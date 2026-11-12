package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.event.IncidentEvent;
import com.itsm.notification.NotificationListener;
import com.itsm.notification.NotificationMessage;
import com.itsm.notification.NotificationType;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updating an incident after it was reported: the technician or an
 * administrator corrects the category, impact or urgency, the priority and
 * the SLA deadlines are worked out again, and a changed priority is announced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReclassificationTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private NotificationListener notificationListener;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Category category(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow();
    }

    /** Payment Application has criticality 3, so medium × medium gives 2 × 2 × 3 = 12: P2. Sara Thomas gets it. */
    private Incident reportMediumPaymentProblem() {
        return incidentService.report((Employee) user("emp1@itsm.local"), "Payment page is slow", "Details.",
                services.findByName("Payment Application").orElseThrow().getId(), category("Application").getId(),
                Impact.MEDIUM, Urgency.MEDIUM);
    }

    private List<IncidentAction> actions(Incident incident) {
        return incidentService.history(incident).stream().map(AuditEntry::getAction).toList();
    }

    // ---- the rule ----

    @Test
    void raisingImpactAndUrgencyRaisesThePriorityAndShortensTheDeadlines() {
        Incident incident = reportMediumPaymentProblem();
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
        User sara = incident.getAssignee();

        incidentService.reclassify(incident.getCode(), sara, category("Application").getId(), Impact.HIGH, Urgency.HIGH);

        assertThat(incident.getImpact()).isEqualTo(Impact.HIGH);
        assertThat(incident.getUrgency()).isEqualTo(Urgency.HIGH);
        assertThat(incident.getPriority()).isEqualTo(Priority.P1);                     // 3 × 3 × 3 = 27
        assertThat(incident.getAssessment().score()).isEqualTo(27);
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(5));    // the P1 policy,
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(30)); // from creation
        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);                   // the status is untouched
        assertThat(incident.getAssignee()).isEqualTo(sara);                            // and so is the assignee
    }

    @Test
    void bothChangesAreWrittenToTheAuditTrail() {
        Incident incident = reportMediumPaymentProblem();

        incidentService.reclassify(incident.getCode(), incident.getAssignee(), category("Database").getId(),
                Impact.LOW, Urgency.MEDIUM);                                           // 1 × 2 × 3 = 6: P3

        List<AuditEntry> history = incidentService.history(incident);
        AuditEntry classification = history.get(history.size() - 2);
        AuditEntry priority = history.get(history.size() - 1);
        assertThat(classification.getAction()).isEqualTo(IncidentAction.CLASSIFICATION_CHANGED);
        assertThat(classification.getActorName()).isEqualTo("Sara Thomas");
        assertThat(classification.getNote())
                .contains("category Application → Database").contains("impact Medium → Low");
        assertThat(classification.isStatusChange()).isFalse();
        assertThat(priority.getAction()).isEqualTo(IncidentAction.PRIORITY_CHANGED);
        assertThat(priority.getNote()).contains("Priority changed from P2 to P3").contains("1 × 2 × 3 = 6");
    }

    @Test
    void aChangeThatLeavesThePriorityAloneRecordsNoPriorityChange() {
        Incident incident = reportMediumPaymentProblem();

        incidentService.reclassify(incident.getCode(), incident.getAssignee(), category("Database").getId(),
                Impact.MEDIUM, Urgency.MEDIUM);                                        // only the category changes

        assertThat(incident.getCategory().getName()).isEqualTo("Database");
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
        assertThat(actions(incident)).contains(IncidentAction.CLASSIFICATION_CHANGED)
                .doesNotContain(IncidentAction.PRIORITY_CHANGED);
    }

    @Test
    void savingWithoutChangingAnythingIsRefused() {
        Incident incident = reportMediumPaymentProblem();

        assertThatThrownBy(() -> incidentService.reclassify(incident.getCode(), incident.getAssignee(),
                category("Application").getId(), Impact.MEDIUM, Urgency.MEDIUM))
                .isInstanceOf(InvalidInputException.class).hasMessage("Nothing was changed.");
        assertThat(actions(incident)).doesNotContain(IncidentAction.CLASSIFICATION_CHANGED);
    }

    @Test
    void anIncidentThatIsNoLongerOpenCannotBeReclassified() {
        Incident incident = reportMediumPaymentProblem();
        User sara = incident.getAssignee();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "Restarted the payment gateway.");

        assertThatThrownBy(() -> incidentService.reclassify(incident.getCode(), sara,
                category("Application").getId(), Impact.HIGH, Urgency.HIGH))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("can no longer be changed");
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
    }

    @Test
    void aCategoryThatDoesNotExistIsRefused() {
        Incident incident = reportMediumPaymentProblem();

        assertThatThrownBy(() -> incidentService.reclassify(incident.getCode(), incident.getAssignee(),
                987_654L, Impact.HIGH, Urgency.HIGH)).isInstanceOf(NotFoundException.class);
    }

    // ---- who may do it ----

    @Test
    void theReporterMayNotReclassifyTheirOwnIncident() {
        Incident incident = reportMediumPaymentProblem();

        assertThatThrownBy(() -> incidentService.reclassify(incident.getCode(), user("emp1@itsm.local"),
                category("Application").getId(), Impact.HIGH, Urgency.HIGH))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
    }

    @Test
    void aTechnicianMayNotReclassifySomeoneElsesIncident() {
        Incident incident = reportMediumPaymentProblem();          // assigned to Sara Thomas
        Technician ravi = (Technician) user("tech1@itsm.local");

        assertThatThrownBy(() -> incidentService.reclassify(incident.getCode(), ravi,
                category("Application").getId(), Impact.HIGH, Urgency.HIGH))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void anAdministratorMayReclassifyAnyOpenIncident() {
        Incident incident = reportMediumPaymentProblem();

        incidentService.reclassify(incident.getCode(), user("admin@itsm.local"), category("Application").getId(),
                Impact.HIGH, Urgency.HIGH);

        assertThat(incident.getPriority()).isEqualTo(Priority.P1);
    }

    // ---- "Priority changed" notification ----

    @Test
    void aPriorityChangeGoesToTheReporterAndTheTechnicianButNotToWhoeverMadeIt() {
        Incident incident = reportMediumPaymentProblem();
        User admin = user("admin@itsm.local");
        IncidentEvent byAdmin = new IncidentEvent(incident, admin, IncidentAction.PRIORITY_CHANGED,
                incident.getStatus(), incident.getStatus(), "Priority changed from P2 to P1.");
        IncidentEvent byTechnician = new IncidentEvent(incident, incident.getAssignee(),
                IncidentAction.PRIORITY_CHANGED, incident.getStatus(), incident.getStatus(), "Priority changed.");

        List<NotificationMessage> fromAdmin = notificationListener.messagesFor(byAdmin);
        List<NotificationMessage> fromTechnician = notificationListener.messagesFor(byTechnician);

        assertThat(fromAdmin).extracting(NotificationMessage::recipientEmail)
                .containsExactlyInAnyOrder("emp1@itsm.local", "tech4@itsm.local");
        assertThat(fromAdmin).allSatisfy(message -> {
            assertThat(message.type()).isEqualTo(NotificationType.PRIORITY_CHANGED);
            assertThat(message.title()).isEqualTo(incident.getCode() + ": the priority is now P2");
        });
        assertThat(NotificationType.PRIORITY_CHANGED.isEmailed()).isTrue();
        assertThat(fromTechnician).extracting(NotificationMessage::recipientEmail)
                .containsExactly("emp1@itsm.local");                       // the technician did it themselves
    }

    @Test
    void aClassificationChangeAloneNotifiesNobody() {
        Incident incident = reportMediumPaymentProblem();
        IncidentEvent event = new IncidentEvent(incident, user("admin@itsm.local"),
                IncidentAction.CLASSIFICATION_CHANGED, incident.getStatus(), incident.getStatus(), "Changed.");

        assertThat(notificationListener.messagesFor(event)).isEmpty();
    }

    // ---- through the browser ----

    @Test
    void theTechnicianChangesTheClassificationOnTheIncidentPage() throws Exception {
        Incident incident = reportMediumPaymentProblem();
        MockHttpSession sara = signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        String page = "/technician/incidents/" + incident.getCode();

        mvc.perform(get(page).session(sara))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Change classification")));

        mvc.perform(post(page + "/classification").session(sara).with(csrf())
                        .param("categoryId", category("Application").getId().toString())
                        .param("impact", "HIGH").param("urgency", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("success", containsString("P1")));

        mvc.perform(get(page).session(sara))
                .andExpect(content().string(containsString("Priority changed")))
                .andExpect(content().string(containsString("Classification changed")));
    }

    @Test
    void theReporterSeesNoClassificationFormAndCannotPostOne() throws Exception {
        Incident incident = reportMediumPaymentProblem();
        MockHttpSession priya = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/employee/incidents/" + incident.getCode()).session(priya))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Change classification"))));

        mvc.perform(post("/technician/incidents/" + incident.getCode() + "/classification").session(priya)
                        .with(csrf()).param("categoryId", category("Application").getId().toString())
                        .param("impact", "HIGH").param("urgency", "HIGH"))
                .andExpect(status().isForbidden());
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
    }

    @Test
    void aFormWithAMissingValueIsSentBackWithAMessage() throws Exception {
        Incident incident = reportMediumPaymentProblem();
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
        String page = "/admin/incidents/" + incident.getCode();

        mvc.perform(post(page + "/classification").session(admin).with(csrf())
                        .header("Referer", "http://localhost" + page)                  // as a browser sends it
                        .param("categoryId", category("Application").getId().toString())
                        .param("impact", "HIGH"))                                      // no urgency
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attributeExists("error"));
        assertThat(incident.getPriority()).isEqualTo(Priority.P2);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
