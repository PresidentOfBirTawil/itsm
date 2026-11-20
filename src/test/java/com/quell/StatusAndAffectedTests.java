package com.quell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.quell.domain.AuditEntry;
import com.quell.domain.Employee;
import com.quell.domain.ITService;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.IncidentAction;
import com.quell.domain.Priority;
import com.quell.domain.Status;
import com.quell.domain.Urgency;
import com.quell.domain.User;
import com.quell.error.InvalidInputException;
import com.quell.event.IncidentEvent;
import com.quell.notification.NotificationListener;
import com.quell.notification.NotificationMessage;
import com.quell.repository.CategoryRepository;
import com.quell.repository.EmployeeRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.UserRepository;
import com.quell.seed.DataSeeder;
import com.quell.service.IncidentService;
import com.quell.status.ServiceHealth;
import com.quell.status.StatusOverview;
import com.quell.status.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Service status, root cause on screen, known problems while reporting, and "I'm affected too". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatusAndAffectedTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private StatusService statusService;
    @Autowired
    private NotificationListener notificationListener;
    @Autowired
    private UserRepository users;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private Employee employee(int number) {
        return (Employee) users.findByEmail("emp" + number + "@quell.local").orElseThrow();
    }

    private ITService service(String name) {
        return services.findByName(name).orElseThrow();
    }

    private Incident report(Employee reporter, String title, String service, String category, Impact impact,
                            Urgency urgency) {
        return incidentService.report(reporter, title, "Details.", service(service).getId(),
                categories.findByNameIgnoreCase(category).orElseThrow().getId(), impact, urgency);
    }

    // ---- the seeded service map ----

    @Test
    void theServicesComeWithDependenciesAndKeywords() {
        assertThat(service("Payment Application").getDependsOn()).extracting(ITService::getName)
                .containsExactlyInAnyOrder("Database Cluster", "Authentication Service", "Corporate Network");
        assertThat(service("Corporate Network").getDependsOn()).isEmpty();
        assertThat(service("Corporate Network").getKeywords()).contains("vpn", "wifi");
    }

    // ---- status ----

    @Test
    void everythingIsOperationalUntilSomethingBreaks() {
        StatusOverview overview = statusService.overview();
        assertThat(overview.worst()).isEqualTo(ServiceHealth.OPERATIONAL);
        assertThat(overview.headline()).isEqualTo("All systems operational");
        assertThat(overview.rootCause()).isNull();

        report(employee(1), "Database is down", "Database Cluster", "Database", Impact.HIGH, Urgency.HIGH);   // P1

        StatusOverview after = statusService.overview();
        assertThat(after.headline()).isEqualTo("Major outage in progress");
        assertThat(after.services().get(0).service().getName()).isEqualTo("Database Cluster");   // worst first
        assertThat(after.services()).filteredOn(status -> status.service().getName().equals("Payment Application"))
                .singleElement().satisfies(payment -> {
                    assertThat(payment.isAtRisk()).isTrue();
                    assertThat(payment.getLabel()).isEqualTo("At risk");
                    assertThat(payment.upstreamTrouble()).extracting(ITService::getName).containsExactly("Database Cluster");
                });
    }

    @Test
    void severalFailuresAtOnceAreTracedToTheServiceTheyShare() throws Exception {
        report(employee(1), "Card payments are declined", "Payment Application", "Application", Impact.HIGH, Urgency.HIGH);
        report(employee(2), "Switch in the server room is amber", "Corporate Network", "Hardware", Impact.MEDIUM, Urgency.HIGH);

        assertThat(statusService.overview().rootCause().service().getName()).isEqualTo("Corporate Network");

        mvc.perform(get("/status").session(signIn("emp3@quell.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Probable root cause")))
                .andExpect(content().string(containsString("Dependency map")))
                .andExpect(content().string(containsString("I'm affected too")));
        mvc.perform(get("/admin/dashboard").session(signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD)))
                .andExpect(content().string(containsString("Probable root cause")));
    }

    @Test
    void everyRoleCanOpenTheStatusPageButOnlyStaffGetLinksToIncidents() throws Exception {
        Incident outage = report(employee(1), "Database is down", "Database Cluster", "Database", Impact.HIGH, Urgency.HIGH);

        mvc.perform(get("/status").session(signIn("tech2@quell.local", DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/technician/incidents/" + outage.getCode() + "\"")));
        mvc.perform(get("/status").session(signIn("emp2@quell.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/employee/incidents/" + outage.getCode() + "\">" + outage.getCode()))));
        mvc.perform(get("/status")).andExpect(status().is3xxRedirection());
    }

    // ---- "I'm affected too" ----

    @Test
    void joiningLetsAnEmployeeFollowSomeoneElsesIncident() throws Exception {
        Incident outage = report(employee(1), "Payment page is down", "Payment Application", "Application",
                Impact.MEDIUM, Urgency.HIGH);
        MockHttpSession rahul = signIn("emp2@quell.local", DataSeeder.EMPLOYEE_PASSWORD);
        mvc.perform(get("/employee/incidents/" + outage.getCode()).session(rahul)).andExpect(status().isForbidden());

        mvc.perform(post("/employee/incidents/" + outage.getCode() + "/affected").session(rahul).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", containsString("You are now following " + outage.getCode())));

        assertThat(outage.getAffectedCount()).isEqualTo(2);
        mvc.perform(get("/employee/incidents/" + outage.getCode()).session(rahul))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 people")))
                .andExpect(content().string(not(containsString("Confirm it is fixed"))));   // following is not owning
        mvc.perform(get("/employee/dashboard").session(rahul))
                .andExpect(content().string(containsString("Also affecting you")));
        List<AuditEntry> history = incidentService.history(outage);
        assertThat(history).extracting(AuditEntry::getAction).contains(IncidentAction.AFFECTED_USER_ADDED);
    }

    @Test
    void moreAffectedPeopleRaiseTheImpactAndThePriority() {
        // Low impact, high urgency, Payment Application (3): 1 × 3 × 3 = 9, P2.
        Incident outage = report(employee(1), "Refunds fail", "Payment Application", "Application", Impact.LOW, Urgency.HIGH);
        assertThat(outage.getPriority()).isEqualTo(Priority.P2);

        incidentService.joinAsAffected(outage.getCode(), employee(2));                  // 2 people: still low
        assertThat(outage.getImpact()).isEqualTo(Impact.LOW);

        incidentService.joinAsAffected(outage.getCode(), employee(3));                  // 3 people: a team
        assertThat(outage.getImpact()).isEqualTo(Impact.MEDIUM);
        assertThat(outage.getPriority()).isEqualTo(Priority.P1);                        // 2 × 3 × 3 = 18

        incidentService.joinAsAffected(outage.getCode(), employee(4));
        Employee fifth = employees.save(new Employee("Dev Patel", "dev@quell.local", "hash"));
        incidentService.joinAsAffected(outage.getCode(), fifth);                       // 5 people: many
        assertThat(outage.getImpact()).isEqualTo(Impact.HIGH);
        assertThat(incidentService.history(outage)).extracting(AuditEntry::getNote)
                .anySatisfy(note -> assertThat(note).isEqualTo("Impact raised from Low to Medium because 3 people are affected."))
                .anySatisfy(note -> assertThat(note).isEqualTo("Impact raised from Medium to High because 5 people are affected."));
    }

    @Test
    void theReporterCannotJoinTwiceNobodyCanJoinTwiceAndAFinishedIncidentCannotBeJoined() {
        Incident outage = report(employee(1), "Mail is slow", "Email & Collaboration", "Software", Impact.LOW, Urgency.LOW);

        assertThatThrownBy(() -> incidentService.joinAsAffected(outage.getCode(), employee(1)))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("reported");
        incidentService.joinAsAffected(outage.getCode(), employee(2));
        assertThatThrownBy(() -> incidentService.joinAsAffected(outage.getCode(), employee(2)))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("already counted");

        User technician = outage.getAssignee();
        incidentService.changeStatus(outage.getCode(), Status.ACKNOWLEDGED, technician, null);
        incidentService.changeStatus(outage.getCode(), Status.IN_PROGRESS, technician, null);
        incidentService.changeStatus(outage.getCode(), Status.RESOLVED, technician, "Cleared the mail queue.");
        assertThatThrownBy(() -> incidentService.joinAsAffected(outage.getCode(), employee(3)))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("resolved");
    }

    @Test
    void everyoneAffectedIsToldWhenItIsFixed() {
        Incident outage = report(employee(1), "Mail is slow", "Email & Collaboration", "Software", Impact.LOW, Urgency.LOW);
        incidentService.joinAsAffected(outage.getCode(), employee(2));
        incidentService.joinAsAffected(outage.getCode(), employee(3));

        List<NotificationMessage> messages = notificationListener.messagesFor(new IncidentEvent(outage,
                outage.getAssignee(), IncidentAction.STATUS_CHANGED, Status.IN_PROGRESS, Status.RESOLVED, "Fixed."));

        assertThat(messages).extracting(NotificationMessage::recipientEmail)
                .containsExactlyInAnyOrder("emp1@quell.local", "emp2@quell.local", "emp3@quell.local");
    }

    // ---- reporting with known problems in mind ----

    @Test
    void theReportFormPointsToAKnownProblemRecognisesTheServiceAndSaysWhoFollowsIt() throws Exception {
        Incident outage = report(employee(1), "Database is down", "Database Cluster", "Database", Impact.HIGH, Urgency.HIGH);
        MockHttpSession rahul = signIn("emp2@quell.local", DataSeeder.EMPLOYEE_PASSWORD);

        // Payment depends on the database, so its known problem is shown for Payment too.
        mvc.perform(get("/employee/incidents/assist").session(rahul)
                        .param("title", "Checkout fails").param("description", "")
                        .param("serviceId", service("Payment Application").getId().toString()))
                .andExpect(jsonPath("$.knownIssues", hasSize(1)))
                .andExpect(jsonPath("$.knownIssues[0].code").value(outage.getCode()))
                .andExpect(jsonPath("$.knownIssues[0].following").value(false))
                .andExpect(jsonPath("$.service.label").value("Payment Application"));
        mvc.perform(get("/employee/incidents/assist").session(rahul)
                        .param("title", "VPN drops").param("description", "")
                        .param("serviceId", service("Corporate Network").getId().toString()))
                .andExpect(jsonPath("$.knownIssues", hasSize(0)))
                .andExpect(jsonPath("$.service.label").value("Corporate Network"));

        // The reporter and anyone who said "I'm affected too" are shown that they follow it, not offered to join.
        incidentService.joinAsAffected(outage.getCode(), employee(2));
        String payment = service("Payment Application").getId().toString();
        assertFollowing("emp1@quell.local", payment, outage, true);    // reported it
        assertFollowing("emp2@quell.local", payment, outage, true);    // said "I'm affected too"
        assertFollowing("emp3@quell.local", payment, outage, false);   // can still join
    }

    private void assertFollowing(String email, String serviceId, Incident outage, boolean following) throws Exception {
        mvc.perform(get("/employee/incidents/assist").session(signIn(email, DataSeeder.EMPLOYEE_PASSWORD))
                        .param("title", "Checkout fails").param("description", "").param("serviceId", serviceId))
                .andExpect(jsonPath("$.knownIssues", hasSize(1)))
                .andExpect(jsonPath("$.knownIssues[0].code").value(outage.getCode()))
                .andExpect(jsonPath("$.knownIssues[0].following").value(following));
    }

    // ---- managing the map ----

    @Test
    void anAdministratorCannotMakeTwoServicesWaitForEachOther() throws Exception {
        MockHttpSession admin = signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD);
        ITService network = service("Corporate Network");

        mvc.perform(post("/admin/services/" + network.getId()).session(admin).with(csrf())
                        .param("name", "Corporate Network").param("criticality", "2")
                        .param("dependencyIds", service("Payment Application").getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "dependencyIds"))
                .andExpect(content().string(containsString("would wait for each other")));
        assertThat(network.getDependsOn()).isEmpty();

        mvc.perform(post("/admin/services/" + network.getId()).session(admin).with(csrf())
                        .param("name", "Corporate Network").param("criticality", "2")
                        .param("keywords", "vpn, wifi, ethernet"))
                .andExpect(status().is3xxRedirection());
        assertThat(network.getKeywords()).containsExactly("vpn", "wifi", "ethernet");
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
