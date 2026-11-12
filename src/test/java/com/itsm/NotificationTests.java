package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.itsm.domain.EmailMessage;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Notification;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.notification.EmailChannel;
import com.itsm.notification.InAppChannel;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmailMessageRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.NotificationRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import com.itsm.sla.SlaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notifications from end to end. Unlike most test classes this one is not
 * rolled back after each test: notifications are sent only once an incident
 * change has really been committed, so the changes here are committed. The
 * class has its own application and database, so nothing leaks into other tests.
 *
 * <p>The two channels are "spies": the real channels, which a test can tell to
 * fail in order to see what happens to everything else.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTests {

    @MockitoSpyBean
    private EmailChannel emailChannel;
    @MockitoSpyBean
    private InAppChannel inAppChannel;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private SlaService slaService;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private EmailMessageRepository emails;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private TransactionTemplate transaction;

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Incident report(String reporterEmail, String title, String service, String category) {
        return incidentService.report((Employee) user(reporterEmail), title, "Details.",
                services.findByName(service).orElseThrow().getId(),
                categories.findByNameIgnoreCase(category).orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
    }

    /** The in-app notifications about one incident, as "recipient email: title". */
    private List<String> notificationsAbout(Incident incident) {
        return notifications.findAll().stream()
                .filter(notification -> notification.getIncident().getId().equals(incident.getId()))
                .map(notification -> users.findById(notification.getRecipient().getId()).orElseThrow().getEmail()
                        + ": " + notification.getTitle())
                .toList();
    }

    /** The simulated emails about one incident, as "to address: subject". */
    private List<String> emailsAbout(Incident incident) {
        return emails.findAll().stream()
                .filter(email -> email.getSubject().contains(incident.getCode()))
                .map(email -> email.getToAddress() + ": " + email.getSubject())
                .toList();
    }

    // ---- who is told what, through which channel ----

    @Test
    void reportingTellsTheReporterAndTheTechnicianInTheAppAndByEmail() {
        Incident incident = report("emp1@itsm.local", "Payment application is unavailable",
                "Payment Application", "Application"); // assigned to Sara (tech4)
        String code = incident.getCode();

        assertThat(notificationsAbout(incident)).containsExactlyInAnyOrder(
                "emp1@itsm.local: " + code + " received",
                "emp1@itsm.local: " + code + ": a technician has been assigned",
                "tech4@itsm.local: " + code + " has been assigned to you");
        assertThat(emailsAbout(incident)).containsExactlyInAnyOrder(
                "emp1@itsm.local: " + code + " received",
                "emp1@itsm.local: " + code + ": a technician has been assigned",
                "tech4@itsm.local: " + code + " has been assigned to you");
    }

    @Test
    void theRightPeopleAreToldAsTheIncidentMovesAlongAndNobodyAboutTheirOwnAction() {
        Incident incident = report("emp2@itsm.local", "Cannot open shared folder", "Cloud Storage", "Storage"); // Meera
        String code = incident.getCode();
        User meera = user("tech2@itsm.local");
        User rahul = user("emp2@itsm.local");
        User admin = user("admin@itsm.local");

        incidentService.changeStatus(code, Status.ACKNOWLEDGED, meera, null);
        incidentService.changeStatus(code, Status.IN_PROGRESS, meera, null);
        incidentService.changeStatus(code, Status.AWAITING_INFO, meera, "Which folder is it?");
        incidentService.changeStatus(code, Status.IN_PROGRESS, rahul, "The finance folder.");
        incidentService.changeStatus(code, Status.RESOLVED, meera, "Restored your access.");
        incidentService.changeStatus(code, Status.VERIFIED, rahul, null);
        incidentService.changeStatus(code, Status.CLOSED, admin, null);

        assertThat(notificationsAbout(incident)).containsExactly(
                "emp2@itsm.local: " + code + " received",
                "tech2@itsm.local: " + code + " has been assigned to you",
                "emp2@itsm.local: " + code + ": a technician has been assigned",
                "emp2@itsm.local: " + code + " is now acknowledged",
                "emp2@itsm.local: " + code + " is now in progress",
                "emp2@itsm.local: " + code + ": the technician needs information from you",
                "tech2@itsm.local: " + code + ": the reporter has answered",
                "emp2@itsm.local: " + code + " has been resolved",
                "tech2@itsm.local: " + code + ": the reporter confirmed the fix",
                "emp2@itsm.local: " + code + " has been closed",
                "tech2@itsm.local: " + code + " has been closed");

        // Ordinary status changes are not emailed; the important ones are.
        assertThat(emailsAbout(incident)).containsExactly(
                "emp2@itsm.local: " + code + " received",
                "tech2@itsm.local: " + code + " has been assigned to you",
                "emp2@itsm.local: " + code + ": a technician has been assigned",
                "emp2@itsm.local: " + code + ": the technician needs information from you",
                "emp2@itsm.local: " + code + " has been resolved",
                "emp2@itsm.local: " + code + " has been closed",
                "tech2@itsm.local: " + code + " has been closed");
    }

    @Test
    void anSlaBreachTellsTheTechnicianAndTheAdministrators() {
        Incident incident = report("emp3@itsm.local", "Laptop screen is black", "Email & Collaboration",
                "Hardware"); // assigned to Ravi (tech1)
        String code = incident.getCode();

        slaService.checkOpenIncidents(LocalDateTime.now().plusDays(2)); // as if two days had passed

        assertThat(notificationsAbout(incident)).contains(
                "tech1@itsm.local: " + code + ": SLA breached",
                "admin@itsm.local: " + code + ": SLA breached");
        assertThat(emailsAbout(incident)).contains(
                "tech1@itsm.local: " + code + ": SLA breached",
                "admin@itsm.local: " + code + ": SLA breached");
    }

    // ---- a failing channel ----

    @Test
    void aFailingEmailChannelDoesNotBlockTheAppChannelOrUndoTheIncident() {
        doThrow(new IllegalStateException("The mail server is down")).when(emailChannel).send(any());

        Incident incident = report("emp1@itsm.local", "VPN keeps disconnecting", "Corporate Network", "Network");

        // The incident change was saved ...
        Incident saved = incidents.findByCode(incident.getCode()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Status.ASSIGNED);
        // ... the in-app notifications went out ...
        assertThat(notificationsAbout(incident)).hasSize(3);
        // ... and only the emails are missing.
        assertThat(emailsAbout(incident)).isEmpty();
    }

    @Test
    void aFailingAppChannelDoesNotBlockTheEmailChannelOrUndoTheIncident() {
        doThrow(new IllegalStateException("The notifications table is locked")).when(inAppChannel).send(any());

        Incident incident = report("emp1@itsm.local", "Database query times out", "Database Cluster", "Database");

        assertThat(incidents.findByCode(incident.getCode())).isPresent();
        assertThat(emailsAbout(incident)).hasSize(3);
        assertThat(notificationsAbout(incident)).isEmpty();
    }

    @Test
    void aDatabaseErrorInsideOneChannelIsUndoneOnItsOwn() {
        // The email channel writes something and then hits a database error half-way through.
        doAnswer(invocation -> {
            emails.saveAndFlush(new EmailMessage("someone@itsm.local", "Someone", "half-written email", "Body."));
            return emails.saveAndFlush(new EmailMessage(null, "Nobody", "an email with no address", "Body."));
        }).when(emailChannel).send(any());
        long emailsBefore = emails.count();

        Incident incident = report("emp4@itsm.local", "Cannot sign in to the portal", "Authentication Service",
                "Authentication");

        assertThat(incidents.findByCode(incident.getCode())).isPresent();   // the incident is safe
        assertThat(notificationsAbout(incident)).hasSize(3);                // the other channel delivered
        assertThat(emails.count()).isEqualTo(emailsBefore);                 // the half-written email is gone too
    }

    @Test
    void aChangeThatIsRolledBackSendsNothing() {
        List<Incident> reported = new ArrayList<>();
        long notificationsBefore = notifications.count();
        long emailsBefore = emails.count();

        transaction.executeWithoutResult(status -> {
            reported.add(report("emp1@itsm.local", "This report will be undone", "Cloud Storage", "Cloud"));
            status.setRollbackOnly();
        });

        assertThat(incidents.findByCode(reported.getFirst().getCode())).isEmpty();
        assertThat(notifications.count()).isEqualTo(notificationsBefore);
        assertThat(emails.count()).isEqualTo(emailsBefore);
    }

    // ---- the bell and the outbox ----

    @Test
    void theBellShowsTheUnreadCountAndOpeningANotificationMarksItReadAndGoesToTheIncident() throws Exception {
        Incident incident = report("emp1@itsm.local", "Report page is slow", "Cloud Storage", "Software"); // Sara
        MockHttpSession sara = signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        long unread = notifications.countByRecipientIdAndReadAtIsNull(user("tech4@itsm.local").getId());
        assertThat(unread).isPositive();

        // The bell on any page shows the number.
        mvc.perform(get("/technician/dashboard").session(sara))
                .andExpect(content().string(containsString("navbar-bell-count")))
                .andExpect(content().string(containsString(">" + unread + "</span>")));

        // The list shows the notification, marked as new.
        Notification mine = notifications.findByRecipientIdAndReadAtIsNull(user("tech4@itsm.local").getId()).stream()
                .filter(notification -> notification.getIncident().getId().equals(incident.getId()))
                .findFirst().orElseThrow();
        mvc.perform(get("/notifications").session(sara))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(incident.getCode() + " has been assigned to you")))
                .andExpect(content().string(containsString("href=\"/notifications/" + mine.getId() + "\"")));

        // Opening it goes to the incident and marks it read.
        mvc.perform(get("/notifications/" + mine.getId()).session(sara))
                .andExpect(redirectedUrl("/technician/incidents/" + incident.getCode()));
        assertThat(notifications.findById(mine.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(user("tech4@itsm.local").getId()))
                .isEqualTo(unread - 1);

        // "Mark all as read" empties the bell.
        mvc.perform(post("/notifications/read-all").session(sara).with(csrf()))
                .andExpect(redirectedUrl("/notifications"));
        mvc.perform(get("/technician/dashboard").session(sara))
                .andExpect(content().string(not(containsString("navbar-bell-count"))));
    }

    @Test
    void aUserCannotOpenSomeoneElsesNotification() throws Exception {
        Incident incident = report("emp1@itsm.local", "Wifi is down in room 4", "Corporate Network", "Network");
        Notification forTheReporter = notifications.findAll().stream()
                .filter(notification -> notification.getIncident().getId().equals(incident.getId()))
                .filter(notification -> notification.getRecipient().getId().equals(user("emp1@itsm.local").getId()))
                .findFirst().orElseThrow();

        mvc.perform(get("/notifications/" + forTheReporter.getId())
                        .session(signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isNotFound());
        assertThat(notifications.findById(forTheReporter.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void theAdministratorCanReadTheEmailOutboxAndNobodyElseCan() throws Exception {
        Incident incident = report("emp1@itsm.local", "Mail client crashes", "Email & Collaboration", "Software");

        mvc.perform(get("/admin/emails").session(signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(incident.getCode() + " received")))
                .andExpect(content().string(containsString("emp1@itsm.local")));

        mvc.perform(get("/admin/emails").session(signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
