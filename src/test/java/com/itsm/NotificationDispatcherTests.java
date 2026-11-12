package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import com.itsm.notification.EmailChannel;
import com.itsm.notification.InAppChannel;
import com.itsm.notification.NotificationChannel;
import com.itsm.notification.NotificationChannelFactory;
import com.itsm.notification.NotificationDispatcher;
import com.itsm.notification.NotificationMessage;
import com.itsm.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * A failing channel must not block the other one. Tested on the dispatcher
 * alone, with stand-in channels that either record what they are given or throw.
 */
class NotificationDispatcherTests {

    private static final NotificationMessage FIRST = message("INC-0001 has been assigned to you");
    private static final NotificationMessage SECOND = message("INC-0002: SLA breached");

    private static NotificationMessage message(String title) {
        return new NotificationMessage(NotificationType.ASSIGNED, 1L, "Sara Thomas", "tech4@itsm.local",
                1L, "INC-0001", title, "Details.");
    }

    /** A channel that remembers what it was asked to send. */
    private static final class RecordingChannel implements NotificationChannel {
        private final List<NotificationMessage> sent = new ArrayList<>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void send(NotificationMessage message) {
            sent.add(message);
        }
    }

    /** A channel that always fails, like a mail server that is down. */
    private static final class BrokenChannel implements NotificationChannel {
        private int attempts;

        @Override
        public String getName() {
            return "broken";
        }

        @Override
        public void send(NotificationMessage message) {
            attempts++;
            throw new IllegalStateException("The mail server is down");
        }
    }

    /** A factory that hands out the given channels for every kind of news. */
    private static NotificationDispatcher dispatcherWith(NotificationChannel... channels) {
        NotificationChannelFactory factory = new NotificationChannelFactory(null, null) {
            @Override
            public List<NotificationChannel> channelsFor(NotificationType type) {
                return List.of(channels);
            }
        };
        return new NotificationDispatcher(factory, TransactionOperations.withoutTransaction());
    }

    @Test
    void aBrokenChannelDoesNotStopTheChannelAfterIt() {
        BrokenChannel broken = new BrokenChannel();
        RecordingChannel working = new RecordingChannel();
        NotificationDispatcher dispatcher = dispatcherWith(broken, working);

        assertThatCode(() -> dispatcher.send(List.of(FIRST, SECOND))).doesNotThrowAnyException();

        assertThat(working.sent).containsExactly(FIRST, SECOND);
        assertThat(broken.attempts).isEqualTo(2); // it was tried for every message, and failed each time
    }

    @Test
    void aBrokenChannelDoesNotUndoTheChannelBeforeIt() {
        RecordingChannel working = new RecordingChannel();
        BrokenChannel broken = new BrokenChannel();
        NotificationDispatcher dispatcher = dispatcherWith(working, broken);

        assertThatCode(() -> dispatcher.send(List.of(FIRST, SECOND))).doesNotThrowAnyException();

        assertThat(working.sent).containsExactly(FIRST, SECOND);
    }

    @Test
    void whenEveryChannelIsBrokenNothingIsThrownAtTheCaller() {
        NotificationDispatcher dispatcher = dispatcherWith(new BrokenChannel(), new BrokenChannel());

        assertThatCode(() -> dispatcher.send(List.of(FIRST))).doesNotThrowAnyException();
        assertThatCode(() -> dispatcher.sendAfterCommit(List.of(FIRST))).doesNotThrowAnyException();
    }

    @Test
    void withNoTransactionRunningMessagesAreSentStraightAway() {
        RecordingChannel working = new RecordingChannel();

        dispatcherWith(working).sendAfterCommit(List.of(FIRST));

        assertThat(working.sent).containsExactly(FIRST);
    }

    @Test
    void theFactoryDecidesWhichChannelsAKindOfNewsGoesThrough() {
        InAppChannel inApp = new InAppChannel(null, null, null);
        EmailChannel email = new EmailChannel(null);
        NotificationChannelFactory factory = new NotificationChannelFactory(inApp, email);

        assertThat(factory.allChannels()).containsExactly(inApp, email);
        // Important news goes to the app and by email ...
        assertThat(factory.channelsFor(NotificationType.SLA_BREACHED)).containsExactly(inApp, email);
        assertThat(factory.channelsFor(NotificationType.ASSIGNED)).containsExactly(inApp, email);
        // ... an ordinary status change only to the app.
        assertThat(factory.channelsFor(NotificationType.STATUS_CHANGED)).containsExactly(inApp);
    }
}
