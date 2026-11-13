package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Role;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.event.IncidentEvent;
import com.itsm.live.LiveUpdateListener;
import com.itsm.live.LiveUpdates;
import com.itsm.notification.LiveChannel;
import com.itsm.notification.NotificationMessage;
import com.itsm.notification.NotificationType;
import com.itsm.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live updates without a server: who is sent which message, that a broken
 * connection harms nobody else, and that incident news waits for the commit.
 * Plain objects only; messages are sent on the test's own thread.
 */
class LiveUpdatesTests {

    private static final long ADMIN = 1, RAVI = 2, SARA = 3, PRIYA = 4, RAHUL = 5;

    /** A browser tab the test can read: it remembers every message it was sent. */
    private static class Tab extends SseEmitter {
        final List<String> received = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            StringBuilder text = new StringBuilder();
            builder.build().forEach(part -> text.append(part.getData()));
            received.add(text.toString().strip());
        }

        /** The messages of one kind, e.g. "incident", as the data they carried. */
        List<String> messages(String name) {
            return received.stream()
                    .filter(message -> message.startsWith("event:" + name + "\n"))
                    .map(message -> message.substring(message.lastIndexOf("data:") + 5).strip())
                    .toList();
        }
    }

    /** A tab whose browser has gone: every write fails. */
    private static final class ClosedTab extends Tab {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("Broken pipe");
        }
    }

    private final LiveUpdates live = new LiveUpdates(Runnable::run);

    private Tab open(long userId, Role role) {
        return open(userId, role, "session-" + userId);
    }

    private Tab open(long userId, Role role, String sessionId) {
        Tab tab = new Tab();
        live.register(userId, role, sessionId, tab);
        return tab;
    }

    @AfterEach
    void leaveNoTransactionBehind() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
        TransactionSynchronizationManager.unbindResourceIfPossible(listener());
    }

    // ---- who is sent what ----

    @Test
    void aNewConnectionIsGreetedSoTheBrowserKnowsItWorks() {
        Tab tab = open(ADMIN, Role.ADMIN);

        assertThat(tab.messages("connected")).containsExactly("{}");
        assertThat(tab.received.get(0)).contains("retry:5000");   // how long the browser waits before reconnecting
        assertThat(live.connectionCount()).isEqualTo(1);
    }

    @Test
    void anIncidentChangeGoesToAdministratorsTechniciansAndTheReporterOnly() {
        Tab admin = open(ADMIN, Role.ADMIN);
        Tab ravi = open(RAVI, Role.TECHNICIAN);
        Tab sara = open(SARA, Role.TECHNICIAN);
        Tab priya = open(PRIYA, Role.EMPLOYEE);
        Tab rahul = open(RAHUL, Role.EMPLOYEE);

        live.incidentChanged("INC-0007", PRIYA);

        assertThat(admin.messages("incident")).containsExactly("{\"code\":\"INC-0007\"}");
        assertThat(ravi.messages("incident")).hasSize(1);        // every technician: they may see every incident
        assertThat(sara.messages("incident")).hasSize(1);
        assertThat(priya.messages("incident")).hasSize(1);       // she reported it
        assertThat(rahul.messages("incident")).isEmpty();        // another employee learns nothing, not even the code
    }

    @Test
    void theBellCountGoesToEveryTabOfThatUserAndToNobodyElse() {
        Tab laptop = open(SARA, Role.TECHNICIAN, "session-laptop");
        Tab phone = open(SARA, Role.TECHNICIAN, "session-phone");
        Tab ravi = open(RAVI, Role.TECHNICIAN);

        live.unreadCount(SARA, 3);

        assertThat(laptop.messages("bell")).containsExactly("{\"unread\":3}");
        assertThat(phone.messages("bell")).containsExactly("{\"unread\":3}");
        assertThat(ravi.messages("bell")).isEmpty();
    }

    @Test
    void aResetTellsEveryoneToFetchTheirPageAgain() {
        Tab admin = open(ADMIN, Role.ADMIN);
        Tab rahul = open(RAHUL, Role.EMPLOYEE);

        live.everythingChanged();

        assertThat(admin.messages("refresh")).hasSize(1);
        assertThat(rahul.messages("refresh")).hasSize(1);
    }

    @Test
    void withNobodyListeningNothingHappens() {
        live.incidentChanged("INC-0001", PRIYA);
        live.unreadCount(SARA, 1);
        live.everythingChanged();
        live.keepAlive();

        assertThat(live.connectionCount()).isZero();
    }

    // ---- connections that end ----

    @Test
    void aBrokenConnectionIsDroppedAndDoesNotStopTheOthers() {
        Tab before = open(ADMIN, Role.ADMIN);
        ClosedTab gone = new ClosedTab();
        live.register(RAVI, Role.TECHNICIAN, "session-gone", gone);   // fails on the greeting already
        Tab after = open(SARA, Role.TECHNICIAN);

        live.incidentChanged("INC-0007", PRIYA);

        assertThat(before.messages("incident")).hasSize(1);
        assertThat(after.messages("incident")).hasSize(1);
        assertThat(live.connectionCount()).isEqualTo(2);
    }

    @Test
    void theKeepAliveFindsABrowserThatHasGone() {
        Tab tab = open(ADMIN, Role.ADMIN);
        Tab closing = new Tab() {
            private boolean greeted;

            @Override
            public void send(SseEventBuilder builder) throws IOException {
                if (greeted) {
                    throw new IOException("Broken pipe");
                }
                greeted = true;
            }
        };
        live.register(RAVI, Role.TECHNICIAN, "session-closing", closing);
        assertThat(live.connectionCount()).isEqualTo(2);

        live.keepAlive();

        assertThat(live.connectionCount()).isEqualTo(1);
        assertThat(tab.received).hasSize(2);                     // the greeting and one keep-alive comment
        assertThat(tab.received.get(1)).startsWith(":keep-alive");
    }

    @Test
    void disablingAUserClosesTheirConnectionsOnly() {
        Tab karan = open(RAHUL, Role.EMPLOYEE);
        Tab priya = open(PRIYA, Role.EMPLOYEE);

        assertThat(live.disconnect(RAHUL)).isEqualTo(1);
        live.everythingChanged();

        assertThat(karan.messages("refresh")).isEmpty();
        assertThat(priya.messages("refresh")).hasSize(1);
        assertThat(live.connectionCount()).isEqualTo(1);
    }

    @Test
    void signingOutClosesTheConnectionsOfThatSessionOnly() {
        Tab laptop = open(SARA, Role.TECHNICIAN, "session-laptop");
        Tab phone = open(SARA, Role.TECHNICIAN, "session-phone");
        MockHttpSession laptopSession = new MockHttpSession(null, "session-laptop");

        live.onSessionEnded(new HttpSessionDestroyedEvent(laptopSession));
        live.unreadCount(SARA, 2);

        assertThat(laptop.messages("bell")).isEmpty();
        assertThat(phone.messages("bell")).hasSize(1);
    }

    @Test
    void stoppingTheApplicationLetsGoOfEveryConnection() {
        open(ADMIN, Role.ADMIN);
        open(SARA, Role.TECHNICIAN);

        live.onShutdown();

        assertThat(live.connectionCount()).isZero();
    }

    // ---- the listener: after the commit, once per incident ----

    private LiveUpdateListener listener;

    private LiveUpdateListener listener() {
        if (listener == null) {
            listener = new LiveUpdateListener(live);
        }
        return listener;
    }

    private static IncidentEvent event(String code, IncidentAction action) {
        Employee reporter = Fixtures.employee();
        ReflectionTestUtils.setField(reporter, "id", PRIYA);
        Incident incident = new Incident("Printer is jammed", "Details.", reporter, Fixtures.service(1),
                Impact.LOW, Urgency.LOW);
        ReflectionTestUtils.setField(incident, "code", code);
        return new IncidentEvent(incident, reporter, action, null, Status.NEW, null);
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clear();
    }

    private static void rollBack() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clear();
    }

    @Test
    void outsideATransactionAnIncidentEventIsAnnouncedAtOnce() {
        Tab admin = open(ADMIN, Role.ADMIN);

        listener().onIncidentEvent(event("INC-0003", IncidentAction.NOTE_ADDED));

        assertThat(admin.messages("incident")).containsExactly("{\"code\":\"INC-0003\"}");
    }

    @Test
    void insideATransactionTheNewsWaitsForTheCommitAndIsSentOncePerIncident() {
        Tab admin = open(ADMIN, Role.ADMIN);
        Tab priya = open(PRIYA, Role.EMPLOYEE);
        TransactionSynchronizationManager.initSynchronization();

        // Reporting one incident raises three events; a second incident changes in the same transaction.
        listener().onIncidentEvent(event("INC-0007", IncidentAction.REPORTED));
        listener().onIncidentEvent(event("INC-0007", IncidentAction.PRIORITISED));
        listener().onIncidentEvent(event("INC-0007", IncidentAction.ASSIGNED));
        listener().onIncidentEvent(event("INC-0002", IncidentAction.DUPLICATE_SUSPECTED));
        assertThat(admin.messages("incident")).isEmpty();          // not yet: it is not committed

        commit();

        assertThat(admin.messages("incident"))
                .containsExactly("{\"code\":\"INC-0007\"}", "{\"code\":\"INC-0002\"}");
        assertThat(priya.messages("incident")).hasSize(2);         // the reporter of both
    }

    @Test
    void aChangeThatIsRolledBackIsNeverAnnounced() {
        Tab admin = open(ADMIN, Role.ADMIN);
        TransactionSynchronizationManager.initSynchronization();
        listener().onIncidentEvent(event("INC-0007", IncidentAction.REPORTED));

        rollBack();

        assertThat(admin.messages("incident")).isEmpty();

        // And the next transaction starts clean: only its own incident is announced.
        TransactionSynchronizationManager.initSynchronization();
        listener().onIncidentEvent(event("INC-0008", IncidentAction.REPORTED));
        commit();
        assertThat(admin.messages("incident")).containsExactly("{\"code\":\"INC-0008\"}");
    }

    // ---- the third notification channel ----

    @Test
    void theLiveChannelSendsTheRecipientTheirUnreadCount() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        when(notifications.countByRecipientIdAndReadAtIsNull(anyLong())).thenReturn(4L);
        LiveChannel channel = new LiveChannel(notifications, live);
        Tab sara = open(SARA, Role.TECHNICIAN);
        Tab ravi = open(RAVI, Role.TECHNICIAN);

        channel.send(new NotificationMessage(NotificationType.ASSIGNED, SARA, "Sara Thomas", "tech4@itsm.local",
                7L, "INC-0007", "INC-0007 has been assigned to you", null));

        assertThat(channel.getName()).isEqualTo("live");
        assertThat(sara.messages("bell")).containsExactly("{\"unread\":4}");
        assertThat(ravi.messages("bell")).isEmpty();
    }
}
