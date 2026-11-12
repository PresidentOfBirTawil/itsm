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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.error.DuplicateIncidentException;
import com.itsm.error.InvalidInputException;
import com.itsm.event.IncidentEvent;
import com.itsm.notification.NotificationListener;
import com.itsm.notification.NotificationMessage;
import com.itsm.notification.NotificationType;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import com.itsm.sla.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The intelligent features inside the running application: duplicate
 * detection, automatic escalation, category suggestion and the SLA risk list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IntelligenceTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private SlaService slaService;
    @Autowired
    private NotificationListener notificationListener;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private Employee priya;
    private Employee rahul;
    private Employee ananya;
    private Technician sara;

    @BeforeEach
    void loadUsers() {
        priya = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        rahul = (Employee) users.findByEmail("emp2@itsm.local").orElseThrow();
        ananya = (Employee) users.findByEmail("emp3@itsm.local").orElseThrow();
        sara = (Technician) users.findByEmail("tech4@itsm.local").orElseThrow();
    }

    private Long serviceId(String name) {
        return services.findByName(name).orElseThrow().getId();
    }

    private Long categoryId(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow().getId();
    }

    /** Reports against the Payment Application with category Application, high impact and urgency (a P1). */
    private Incident reportPayment(Employee reporter, String title) {
        return incidentService.report(reporter, title, "Details.", serviceId("Payment Application"),
                categoryId("Application"), Impact.HIGH, Urgency.HIGH);
    }

    private List<String> notes(Incident incident, IncidentAction action) {
        return incidentService.history(incident).stream()
                .filter(entry -> entry.getAction() == action).map(AuditEntry::getNote).toList();
    }

    // ---- a. duplicate detection ----

    @Test
    void theBriefsThreePaymentReportsAreLinkedAsPossibleDuplicates() {
        Incident first = reportPayment(priya, "Payment application unavailable");
        Incident second = reportPayment(rahul, "Payment transaction failing");
        Incident third = reportPayment(ananya, "Unable to make payment");

        assertThat(first.getPossibleDuplicateOf()).isNull();

        assertThat(second.getPossibleDuplicateOf()).isEqualTo(first);
        assertThat(second.getDuplicateSimilarity()).isEqualTo(0.5);
        assertThat(notes(second, IncidentAction.DUPLICATE_SUSPECTED)).containsExactly(
                "Possible duplicate of %s (similarity 0.50, same service, less than a minute apart)."
                        .formatted(first.getCode()));

        assertThat(third.getPossibleDuplicateOf()).isEqualTo(first); // the most similar of the two earlier ones
        assertThat(third.getDuplicateSimilarity()).isEqualTo(1.0);
        assertThat(incidentService.possibleDuplicatesOf(first)).containsExactly(second, third);

        // A possible duplicate is still a full incident: prioritised and assigned like any other.
        assertThat(third.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    @Test
    void theIncidentPagesAndListsShowThePossibleDuplicateWithItsReason() throws Exception {
        Incident first = reportPayment(priya, "Payment application unavailable");
        Incident second = reportPayment(rahul, "Payment transaction failing");
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/incidents/" + second.getCode()).session(admin))
                .andExpect(content().string(containsString("Possible duplicate of")))
                .andExpect(content().string(containsString("href=\"/admin/incidents/" + first.getCode() + "\"")))
                .andExpect(content().string(containsString("(similarity 0.50, same service, less than a minute apart)")));
        mvc.perform(get("/admin/incidents/" + first.getCode()).session(admin))
                .andExpect(content().string(containsString("Later incidents that look like duplicates of this one")))
                .andExpect(content().string(containsString("href=\"/admin/incidents/" + second.getCode() + "\"")));
        mvc.perform(get("/admin/incidents").session(admin))
                .andExpect(content().string(containsString(">possible duplicate</span>")));

        // The reporter of the second one is told too, but cannot open the other person's incident.
        mvc.perform(get("/employee/incidents/" + second.getCode())
                        .session(signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(content().string(containsString("Possible duplicate of")))
                .andExpect(content().string(not(containsString("/incidents/" + first.getCode() + "\""))));
    }

    @Test
    void incidentsOnAnotherServiceOrAboutSomethingElseAreNotLinked() {
        reportPayment(priya, "Payment application unavailable");

        Incident otherService = incidentService.report(rahul, "Payment application unavailable", "Details.",
                serviceId("Cloud Storage"), categoryId("Application"), Impact.LOW, Urgency.LOW);
        Incident otherProblem = reportPayment(ananya, "Refund page shows the wrong totals");

        assertThat(otherService.getPossibleDuplicateOf()).isNull();
        assertThat(otherProblem.getPossibleDuplicateOf()).isNull();
        assertThat(notes(otherProblem, IncidentAction.DUPLICATE_SUSPECTED)).isEmpty();
    }

    @Test
    void theSamePersonReportingTheSameThingAgainIsRefused() {
        Incident first = reportPayment(priya, "Payment application unavailable");
        long before = incidents.count();

        assertThatThrownBy(() -> reportPayment(priya, "  payment APPLICATION unavailable "))
                .isInstanceOf(DuplicateIncidentException.class)
                .hasMessageContaining(first.getCode())
                .hasMessageContaining("still open");
        assertThat(incidents.count()).isEqualTo(before);

        // Someone else may report it (it is linked instead), and so may the same person in other words.
        assertThat(reportPayment(rahul, "Payment application unavailable").getPossibleDuplicateOf()).isEqualTo(first);
        assertThat(reportPayment(priya, "Card payments are declined").getCode()).isNotNull();
    }

    @Test
    void onceTheFirstReportIsResolvedTheSameThingCanBeReportedAgain() {
        Incident first = reportPayment(priya, "Payment application unavailable");
        incidentService.changeStatus(first.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(first.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(first.getCode(), Status.RESOLVED, sara, "Restarted the gateway.");

        Incident again = reportPayment(priya, "Payment application unavailable");

        assertThat(again.getCode()).isNotEqualTo(first.getCode());
        assertThat(again.getPossibleDuplicateOf()).isNull(); // only open incidents are compared
    }

    // ---- b. automatic escalation ----

    @Test
    void aP1NobodyAcknowledgesInTimeIsEscalatedOnceWithAnAuditEntry() {
        Incident incident = reportPayment(priya, "Payment application unavailable"); // respond within 5 minutes
        LocalDateTime reported = incident.getCreatedAt();

        slaService.checkOpenIncidents(reported.plusMinutes(4));
        assertThat(incident.isEscalated()).isFalse();

        slaService.checkOpenIncidents(reported.plusMinutes(6));
        assertThat(incident.isEscalated()).isTrue();
        assertThat(incident.getEscalatedAt()).isEqualTo(reported.plusMinutes(6));
        assertThat(notes(incident, IncidentAction.ESCALATED)).hasSize(1).first().asString()
                .contains("Escalated to the administrators automatically", "not acknowledged",
                        "response time of 5 min", "assigned to Sara Thomas");

        slaService.checkOpenIncidents(reported.plusMinutes(7));
        slaService.checkOpenIncidents(reported.plusMinutes(8));
        assertThat(notes(incident, IncidentAction.ESCALATED)).hasSize(1);      // not escalated again
        assertThat(incident.getEscalatedAt()).isEqualTo(reported.plusMinutes(6));
    }

    @Test
    void theAdministratorsAreNotifiedOfAnEscalationInTheAppAndByEmail() {
        Incident incident = reportPayment(priya, "Payment application unavailable");
        IncidentEvent escalation = new IncidentEvent(incident, null, IncidentAction.ESCALATED,
                incident.getStatus(), incident.getStatus(), "Escalated to the administrators automatically.");

        List<NotificationMessage> messages = notificationListener.messagesFor(escalation);

        assertThat(messages).extracting(NotificationMessage::recipientEmail).containsExactly("admin@itsm.local");
        assertThat(messages.getFirst().type()).isEqualTo(NotificationType.ESCALATED);
        assertThat(messages.getFirst().type().isEmailed()).isTrue();
        assertThat(messages.getFirst().title()).isEqualTo(incident.getCode() + " has been escalated to you");
    }

    @Test
    void anAcknowledgedP1AndALowerPriorityAreNotEscalated() {
        Incident acknowledged = reportPayment(priya, "Payment application unavailable");
        incidentService.changeStatus(acknowledged.getCode(), Status.ACKNOWLEDGED, sara, null);
        Incident lowPriority = incidentService.report(rahul, "Printer is jammed", "Details.",
                serviceId("Email & Collaboration"), categoryId("Hardware"), Impact.LOW, Urgency.LOW);

        slaService.checkOpenIncidents(LocalDateTime.now().plusDays(3)); // every deadline has long passed

        assertThat(acknowledged.isEscalated()).isFalse();
        assertThat(lowPriority.isEscalated()).isFalse();
        assertThat(lowPriority.isSlaBreached()).isTrue(); // breached, yes, but not a P1
    }

    @Test
    void theDashboardAndTheIncidentPageShowTheEscalation() throws Exception {
        Incident incident = reportPayment(priya, "Payment application unavailable");
        slaService.checkOpenIncidents(incident.getCreatedAt().plusMinutes(6));
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(content().string(containsString("1 escalated incident needs your attention")))
                .andExpect(content().string(containsString(">escalated</span>")));
        mvc.perform(get("/admin/incidents/" + incident.getCode()).session(admin))
                .andExpect(content().string(containsString("Escalated to the administrators automatically")))
                .andExpect(content().string(containsString("Escalated</span>")));
    }

    // ---- c. category suggestion ----

    @Test
    void anIncidentReportedWithoutACategoryGetsOneFromItsWords() {
        Incident incident = incidentService.report(priya, "Cannot connect to the VPN from home",
                "The wifi is fine but the VPN client times out.", serviceId("Corporate Network"), null,
                Impact.MEDIUM, Urgency.MEDIUM);

        assertThat(incident.getCategory().getName()).isEqualTo("Network");
        assertThat(notes(incident, IncidentAction.CATEGORY_SUGGESTED)).containsExactly(
                "Category Network was chosen automatically because the report mentions: vpn, wifi.");
        // The suggested category is used like a chosen one: a technician with the Network skill gets it.
        assertThat(incident.getAssignee().getSkills()).contains(incident.getCategory());
    }

    @Test
    void aChosenCategoryIsNeverOverruled() {
        Incident incident = incidentService.report(priya, "Cannot connect to the VPN from home", "Details.",
                serviceId("Corporate Network"), categoryId("Security"), Impact.MEDIUM, Urgency.MEDIUM);

        assertThat(incident.getCategory().getName()).isEqualTo("Security");
        assertThat(notes(incident, IncidentAction.CATEGORY_SUGGESTED)).isEmpty();
    }

    @Test
    void whenNoCategoryCanBeWorkedOutTheReporterIsAskedToChoose() {
        long before = incidents.count();

        assertThatThrownBy(() -> incidentService.report(priya, "Something odd is happening", "No idea what.",
                serviceId("Corporate Network"), null, Impact.LOW, Urgency.LOW))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Please choose one");

        assertThat(incidents.count()).isEqualTo(before);
    }

    @Test
    void theReportFormLetsTheSystemChooseAndThePageSaysWhy() throws Exception {
        MockHttpSession employee = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/employee/incidents/new").session(employee))
                .andExpect(content().string(containsString("Let the system choose")));

        String page = mvc.perform(post("/employee/incidents").session(employee).with(csrf())
                        .param("title", "Forgot my password and my account is locked")
                        .param("description", "I cannot get in.")
                        .param("serviceId", String.valueOf(serviceId("Authentication Service")))
                        .param("categoryId", "")
                        .param("impact", "LOW").param("urgency", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(page).session(employee))
                .andExpect(content().string(containsString(
                        "Category Authentication was chosen automatically because the report mentions: locked, password.")));
    }

    @Test
    void theReportFormAsksForACategoryWhenItCannotWorkOneOutAndKeepsWhatWasTyped() throws Exception {
        mvc.perform(post("/employee/incidents").session(signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD))
                        .with(csrf())
                        .param("title", "Something odd is happening")
                        .param("description", "No idea what.")
                        .param("serviceId", String.valueOf(serviceId("Corporate Network")))
                        .param("categoryId", "")
                        .param("impact", "LOW").param("urgency", "LOW"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "categoryId"))
                .andExpect(content().string(containsString("We could not work out a category")))
                .andExpect(content().string(containsString("value=\"Something odd is happening\"")));
    }

    // ---- d. SLA risk list ----

    @Test
    void theDashboardListsTheIncidentsMostLikelyToBreachNextWithTheReason() throws Exception {
        incidentService.report(rahul, "Printer is jammed", "Details.", serviceId("Email & Collaboration"),
                categoryId("Hardware"), Impact.LOW, Urgency.LOW);                       // P4: 4 hours to respond
        Incident urgent = reportPayment(priya, "Payment application unavailable");     // P1: 5 minutes to respond

        mvc.perform(get("/admin/dashboard").session(signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Most likely to breach next")))
                .andExpect(content().string(containsString("Response due in")))
                .andExpect(content().string(containsString("not acknowledged yet")))
                .andExpect(model().attribute("slaRisks", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    String list = html.substring(html.indexOf("Most likely to breach next"));
                    assertThat(list.indexOf(urgent.getCode())).isPositive().isLessThan(list.indexOf("Printer is jammed"));
                });
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
