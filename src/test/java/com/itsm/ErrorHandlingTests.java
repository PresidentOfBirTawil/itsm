package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.error.DuplicateIncidentException;
import com.itsm.error.IncidentNotFoundException;
import com.itsm.error.InvalidInputException;
import com.itsm.error.InvalidStateTransitionException;
import com.itsm.error.ItsmException;
import com.itsm.error.MissingSlaConfigurationException;
import com.itsm.error.NotFoundException;
import com.itsm.error.ServiceNotFoundException;
import com.itsm.error.TechnicianUnavailableException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.error.WorkloadExceededException;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user sees when something goes wrong: one test for each kind of
 * error in the brief. In every case the user gets a readable message or page,
 * and nothing has been changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorHandlingTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private EntityManager entityManager;

    private MockHttpSession priya;
    private MockHttpSession sara;
    private MockHttpSession admin;
    private Incident incident;
    private String code;

    /** Priya reports the payment outage; it is assigned to Sara. */
    @BeforeEach
    void signInAndReport() throws Exception {
        priya = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        sara = signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
        incident = incidentService.report((Employee) users.findByEmail("emp1@itsm.local").orElseThrow(),
                "Payment application is unavailable", "Details.", serviceId("Payment Application"),
                categoryId("Application"), Impact.HIGH, Urgency.HIGH);
        code = incident.getCode();
    }

    private Long serviceId(String name) {
        return services.findByName(name).orElseThrow().getId();
    }

    private Long categoryId(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow().getId();
    }

    private Technician technician(String email) {
        return (Technician) users.findByEmail(email).orElseThrow();
    }

    private MockHttpServletRequestBuilder reportForm(String title, String serviceId) {
        return post("/employee/incidents").session(priya).with(csrf())
                .param("title", title).param("description", "Details.")
                .param("serviceId", serviceId).param("categoryId", String.valueOf(categoryId("Application")))
                .param("impact", "HIGH").param("urgency", "HIGH");
    }

    // ---- the hierarchy ----

    @Test
    void everyErrorOfTheApplicationIsAnItsmException() {
        List<Class<? extends RuntimeException>> all = List.of(
                InvalidStateTransitionException.class, TechnicianUnavailableException.class,
                WorkloadExceededException.class, MissingSlaConfigurationException.class,
                DuplicateIncidentException.class, UnauthorizedOperationException.class,
                InvalidInputException.class, NotFoundException.class,
                ServiceNotFoundException.class, IncidentNotFoundException.class);

        assertThat(all).allMatch(ItsmException.class::isAssignableFrom);
        assertThat(NotFoundException.class).isAssignableFrom(ServiceNotFoundException.class);
        assertThat(NotFoundException.class).isAssignableFrom(IncidentNotFoundException.class);
    }

    // ---- invalid state transition ----

    @Test
    void anInvalidStateTransitionComesBackAsAMessageOnTheIncidentPage() throws Exception {
        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf())
                        .param("target", "CLOSED"))
                .andExpect(redirectedUrl("/technician/incidents/" + code))
                .andExpect(flash().attribute("error", containsString("cannot go from Assigned to Closed")));

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    // ---- technician unavailable, workload exceeded ----

    @Test
    void assigningToAnUnavailableTechnicianComesBackAsAMessage() throws Exception {
        Technician ravi = technician("tech1@itsm.local");
        ravi.setAvailable(false);

        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin).with(csrf())
                        .param("technicianId", String.valueOf(ravi.getId())))
                .andExpect(redirectedUrl("/admin/incidents/" + code))
                .andExpect(flash().attribute("error", "Ravi Kumar is not available."));

        assertThat(incident.getAssignee().getEmail()).isEqualTo("tech4@itsm.local");
    }

    @Test
    void assigningToATechnicianAtTheirMaximumComesBackAsAMessage() throws Exception {
        Technician arjun = technician("tech3@itsm.local");
        arjun.setMaxWorkload(1);
        incidentService.report((Employee) users.findByEmail("emp2@itsm.local").orElseThrow(),
                "Suspicious sign-in attempts", "Details.", serviceId("Authentication Service"),
                categoryId("Security"), Impact.LOW, Urgency.LOW); // fills Arjun

        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin).with(csrf())
                        .param("technicianId", String.valueOf(arjun.getId())))
                .andExpect(redirectedUrl("/admin/incidents/" + code))
                .andExpect(flash().attribute("error", "Arjun Singh already has 1 of 1 open incidents."));

        assertThat(incident.getAssignee().getEmail()).isEqualTo("tech4@itsm.local");
    }

    @Test
    void assigningWithoutChoosingATechnicianComesBackAsAMessage() throws Exception {
        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/incidents/" + code))
                .andExpect(flash().attribute("error", "Please choose a technician."));
    }

    // ---- service not found, incident not found ----

    @Test
    void reportingAgainstAServiceThatDoesNotExistShowsTheNotFoundPage() throws Exception {
        long before = incidents.count();

        mvc.perform(reportForm("Something else is broken", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(containsString("There is no IT service with the number 999999.")));

        assertThat(incidents.count()).isEqualTo(before);
        mvc.perform(get("/admin/services/999999/edit").session(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("There is no IT service with the number 999999.")));
    }

    @Test
    void anIncidentThatDoesNotExistShowsTheNotFoundPage() throws Exception {
        mvc.perform(get("/admin/incidents/INC-9999").session(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("There is no incident INC-9999.")))
                .andExpect(content().string(containsString("Back to my dashboard")));
    }

    @Test
    void anAddressThatDoesNotExistShowsTheNotFoundPageToo() throws Exception {
        mvc.perform(get("/admin/no-such-page").session(admin)).andExpect(status().isNotFound());
    }

    // ---- missing SLA configuration ----

    @Test
    void aMissingSlaPolicyIsExplainedOnTheReportForm() throws Exception {
        slaPolicies.delete(slaPolicies.findByPriority(Priority.P1).orElseThrow());
        entityManager.flush();

        mvc.perform(reportForm("Card payments are declined", String.valueOf(serviceId("Payment Application"))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", containsString("No SLA policy is set up for priority P1")))
                .andExpect(content().string(containsString("Ask an administrator to add one.")))
                .andExpect(content().string(containsString("value=\"Card payments are declined\""))); // input kept
        // That the half-made incident is really undone is checked in NotificationTests, where changes are committed.
    }

    // ---- duplicate incident ----

    @Test
    void reportingTheSameThingTwiceIsExplainedOnTheReportForm() throws Exception {
        long before = incidents.count();

        mvc.perform(reportForm("Payment application is unavailable", String.valueOf(serviceId("Payment Application"))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", containsString("You have already reported this: " + code)));

        assertThat(incidents.count()).isEqualTo(before);
    }

    // ---- unauthorized operation ----

    @Test
    void openingSomeoneElsesIncidentShowsTheAccessDeniedPageWithTheReason() throws Exception {
        mvc.perform(get("/employee/incidents/" + code).session(signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("You do not have access to this page")))
                .andExpect(content().string(containsString("You may not open " + code + ".")));
    }

    @Test
    void pressingAnotherRolesButtonShowsTheAccessDeniedPageAndChangesNothing() throws Exception {
        mvc.perform(post("/employee/incidents/" + code + "/transition").session(priya).with(csrf())
                        .param("target", "ACKNOWLEDGED"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("You may not move " + code + " to acknowledged.")));

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    @Test
    void aPageForAnotherRoleShowsTheAccessDeniedPage() throws Exception {
        mvc.perform(get("/admin/dashboard").session(priya)).andExpect(status().isForbidden());
    }

    // ---- invalid input ----

    @Test
    void aMissingNoteComesBackAsAMessage() throws Exception {
        incidentService.changeStatus(code, Status.ACKNOWLEDGED, technician("tech4@itsm.local"), null);
        incidentService.changeStatus(code, Status.IN_PROGRESS, technician("tech4@itsm.local"), null);

        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf())
                        .param("target", "RESOLVED"))
                .andExpect(redirectedUrl("/technician/incidents/" + code))
                .andExpect(flash().attribute("error", containsString("Please add a note")));
        mvc.perform(post("/technician/incidents/" + code + "/notes").session(sara).with(csrf())
                        .header("Referer", "http://localhost/technician/incidents/" + code)
                        .param("note", "   "))
                .andExpect(redirectedUrl("/technician/incidents/" + code))
                .andExpect(flash().attribute("error", "Please write the note first."));

        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void aFormThatFailsItsChecksGoesBackToThePageItCameFrom() throws Exception {
        String page = "/technician/incidents/" + code;

        // A status that does not exist.
        mvc.perform(post(page + "/transition").session(sara).with(csrf()).header("Referer", page)
                        .param("target", "TELEPORTED"))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("error", "The value given for \"target\" could not be understood."));
        // A note that is far too long.
        mvc.perform(post(page + "/notes").session(sara).with(csrf()).header("Referer", page)
                        .param("note", "x".repeat(1001)))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("error", "The note can be at most 1000 characters."));

        assertThat(incident.getStatus()).isEqualTo(Status.ASSIGNED);
    }

    @Test
    void withoutAPageToGoBackToTheUserLandsOnTheirDashboardWithTheMessage() throws Exception {
        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf()))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", "Please choose what to do."));
        // A page on another site is never used as the place to go back to.
        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf())
                        .header("Referer", "https://evil.example/steal"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void anAddressWithAValueThatMakesNoSenseShowsAFriendlyPage() throws Exception {
        mvc.perform(get("/notifications/not-a-number").session(priya))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("That did not work")))
                .andExpect(content().string(containsString("Nothing has been changed.")));
    }

    @Test
    void anUnknownAssignmentStrategyComesBackAsAMessage() throws Exception {
        mvc.perform(post("/admin/settings/assignment-strategy").session(admin).with(csrf()).param("key", "coin-toss"))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("error", containsString("There is no assignment strategy called")));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
