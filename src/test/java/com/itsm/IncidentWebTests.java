package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itsm.assignment.AssignmentService;
import com.itsm.assignment.RoundRobinStrategy;
import com.itsm.assignment.SkillBasedStrategy;
import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The incident pages, used the way a browser uses them: signing in through
 * the login form, then GET and POST requests with that session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentWebTests {

    private static final String PRIYA = "emp1@itsm.local";   // employee, reports the incidents
    private static final String RAHUL = "emp2@itsm.local";   // another employee
    private static final String RAVI = "tech1@itsm.local";   // Network, Hardware
    private static final String ARJUN = "tech3@itsm.local";  // Security, Authentication, Network
    private static final String SARA = "tech4@itsm.local";   // Software, Application, Cloud
    private static final String ADMIN = "admin@itsm.local";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private AssignmentService assignmentService;

    // ---- reporting ----

    @Test
    void theReportFormOffersTheServicesCategoriesImpactsAndUrgencies() throws Exception {
        mvc.perform(get("/employee/incidents/new").session(employee()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payment Application")))
                .andExpect(content().string(containsString("Authentication")))
                .andExpect(content().string(containsString("High · many people or customers")))
                .andExpect(content().string(containsString("High · work has stopped")));
    }

    @Test
    void anEmployeeReportsAnIncidentAndLandsOnItsPage() throws Exception {
        MockHttpSession priya = employee();

        String code = reportPaymentOutage(priya);

        mvc.perform(get("/employee/incidents/" + code).session(priya))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payment application is unavailable")))
                // priority and its breakdown
                .andExpect(content().string(containsString("P1 · Critical")))
                .andExpect(content().string(containsString("= 27")))
                .andExpect(content().string(containsString("18–27 is P1")))
                // assigned straight away to the technician with the Application skill
                .andExpect(content().string(containsString("Sara Thomas")))
                // the timeline
                .andExpect(content().string(containsString("Incident reported")))
                .andExpect(content().string(containsString("Priority calculated")))
                .andExpect(content().string(containsString("Technician assigned")))
                .andExpect(content().string(containsString("3 × 3 × 3 = 27")));
    }

    @Test
    void theNewIncidentAppearsUnderMyIncidentsWithALinkToItsPage() throws Exception {
        MockHttpSession priya = employee();
        String code = reportPaymentOutage(priya);

        mvc.perform(get("/employee/incidents").session(priya))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/employee/incidents/" + code + "\"")));
        mvc.perform(get("/employee/dashboard").session(priya))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/employee/incidents/" + code + "\"")));
    }

    @Test
    void aReportWithMissingAnswersShowsTheErrorsAndCreatesNothing() throws Exception {
        long before = incidents.count();

        mvc.perform(post("/employee/incidents").session(employee()).with(csrf())
                        .param("title", " ")
                        .param("description", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form",
                        "title", "description", "serviceId", "categoryId", "impact", "urgency"))
                .andExpect(content().string(containsString("Please choose the affected service")));

        assertThat(incidents.count()).isEqualTo(before);
    }

    @Test
    void onlyEmployeesCanReport() throws Exception {
        mvc.perform(get("/employee/incidents/new").session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/employee/incidents").session(signIn(ADMIN, DataSeeder.ADMIN_PASSWORD)).with(csrf())
                        .param("title", "x"))
                .andExpect(status().isForbidden());
    }

    // ---- who can open an incident ----

    @Test
    void anEmployeeSeesOnlyTheirOwnIncidentsAndOtherTechniciansCanOnlyRead() throws Exception {
        String code = reportPaymentOutage(employee()); // reported by Priya, assigned to Sara

        mvc.perform(get("/employee/incidents/" + code).session(signIn(RAHUL, DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isForbidden());

        // Another technician may read it, but gets no buttons.
        mvc.perform(get("/technician/incidents/" + code).session(signIn(RAVI, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This incident is not assigned to you")))
                .andExpect(content().string(not(containsString("name=\"target\""))));

        mvc.perform(get("/technician/incidents/" + code).session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/incidents/" + code).session(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void anIncidentThatDoesNotExistGivesNotFound() throws Exception {
        mvc.perform(get("/admin/incidents/INC-9999").session(admin())).andExpect(status().isNotFound());
    }

    // ---- only the buttons each role may use ----

    @Test
    void eachRoleIsShownOnlyItsOwnButtons() throws Exception {
        MockHttpSession priya = employee();
        String code = reportPaymentOutage(priya); // status ASSIGNED

        // The technician can acknowledge.
        mvc.perform(get("/technician/incidents/" + code).session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(content().string(containsString("value=\"ACKNOWLEDGED\"")))
                .andExpect(content().string(containsString(">Acknowledge</button>")))
                .andExpect(content().string(not(containsString("Reassign"))));

        // The reporter has nothing to do yet.
        mvc.perform(get("/employee/incidents/" + code).session(priya))
                .andExpect(content().string(not(containsString("name=\"target\""))))
                .andExpect(content().string(containsString("There is no status change for you to make")));

        // The administrator has no status button either, but can reassign.
        mvc.perform(get("/admin/incidents/" + code).session(admin()))
                .andExpect(content().string(not(containsString("name=\"target\""))))
                .andExpect(content().string(containsString(">Reassign</button>")));
    }

    @Test
    void theButtonsChangeAsTheIncidentMovesAlong() throws Exception {
        MockHttpSession priya = employee();
        MockHttpSession sara = signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD);
        String code = reportPaymentOutage(priya);
        move(sara, "/technician", code, Status.ACKNOWLEDGED, null);
        move(sara, "/technician", code, Status.IN_PROGRESS, null);

        mvc.perform(get("/technician/incidents/" + code).session(sara))
                .andExpect(content().string(containsString(">Resolve</button>")))
                .andExpect(content().string(containsString(">Request information</button>")))
                .andExpect(content().string(containsString("Needed for: Resolve, Request information.")))
                .andExpect(content().string(not(containsString(">Acknowledge</button>"))));

        move(sara, "/technician", code, Status.RESOLVED, "Restarted the payment gateway.");

        mvc.perform(get("/employee/incidents/" + code).session(priya))
                .andExpect(content().string(containsString(">Confirm it is fixed</button>")))
                .andExpect(content().string(containsString(">Not fixed, reopen</button>")));
        mvc.perform(get("/technician/incidents/" + code).session(sara))
                .andExpect(content().string(not(containsString("name=\"target\""))));
        mvc.perform(get("/admin/incidents/" + code).session(admin()))
                .andExpect(content().string(containsString(">Reopen</button>")))
                .andExpect(content().string(not(containsString(">Confirm it is fixed</button>"))));
    }

    // ---- the brief's scenario, start to finish ----

    @Test
    void thePaymentOutageScenarioFromReportToClose() throws Exception {
        MockHttpSession priya = employee();
        MockHttpSession sara = signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD);
        MockHttpSession admin = admin();

        String code = reportPaymentOutage(priya);
        assertThat(statusOf(code)).isEqualTo(Status.ASSIGNED);

        move(sara, "/technician", code, Status.ACKNOWLEDGED, null);
        move(sara, "/technician", code, Status.IN_PROGRESS, null);
        move(sara, "/technician", code, Status.AWAITING_INFO, "Does it fail for every customer?");
        move(priya, "/employee", code, Status.IN_PROGRESS, "Yes, every card payment fails.");
        move(sara, "/technician", code, Status.RESOLVED, "Restarted the payment gateway.");
        move(priya, "/employee", code, Status.VERIFIED, null);
        move(admin, "/admin", code, Status.CLOSED, "Thanks everyone.");
        assertThat(statusOf(code)).isEqualTo(Status.CLOSED);

        mvc.perform(get("/admin/incidents/" + code).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Does it fail for every customer?")))
                .andExpect(content().string(containsString("Yes, every card payment fails.")))
                .andExpect(content().string(containsString("Restarted the payment gateway.")))
                .andExpect(content().string(containsString("Thanks everyone.")))
                .andExpect(content().string(containsString("Closed. Nothing more will happen")))
                .andExpect(content().string(not(containsString("name=\"target\""))));
    }

    // ---- refused moves ----

    @Test
    void resolvingWithoutANoteIsRefusedWithAMessage() throws Exception {
        MockHttpSession sara = signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD);
        String code = reportPaymentOutage(employee());
        move(sara, "/technician", code, Status.ACKNOWLEDGED, null);
        move(sara, "/technician", code, Status.IN_PROGRESS, null);

        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf())
                        .param("target", "RESOLVED"))
                .andExpect(redirectedUrl("/technician/incidents/" + code))
                .andExpect(flash().attribute("error", containsString("Please add a note")));

        assertThat(statusOf(code)).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void anIllegalMoveIsRefusedWithAMessageAndChangesNothing() throws Exception {
        String code = reportPaymentOutage(employee()); // ASSIGNED

        mvc.perform(post("/technician/incidents/" + code + "/transition")
                        .session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)).with(csrf())
                        .param("target", "CLOSED"))
                .andExpect(redirectedUrl("/technician/incidents/" + code))
                .andExpect(flash().attribute("error", containsString("cannot go from Assigned to Closed")));

        assertThat(statusOf(code)).isEqualTo(Status.ASSIGNED);
    }

    @Test
    void aUserCannotMakeAnotherRolesMoveEvenByCraftingTheRequest() throws Exception {
        MockHttpSession priya = employee();
        String code = reportPaymentOutage(priya); // ASSIGNED: acknowledging is the technician's move

        mvc.perform(post("/employee/incidents/" + code + "/transition").session(priya).with(csrf())
                        .param("target", "ACKNOWLEDGED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/technician/incidents/" + code + "/transition").session(priya).with(csrf())
                        .param("target", "ACKNOWLEDGED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/technician/incidents/" + code + "/transition")
                        .session(signIn(RAVI, DataSeeder.TECHNICIAN_PASSWORD)).with(csrf())
                        .param("target", "ACKNOWLEDGED"))
                .andExpect(status().isForbidden());

        assertThat(statusOf(code)).isEqualTo(Status.ASSIGNED);
    }

    // ---- the technician's queue ----

    @Test
    void myQueueListsTheMostUrgentDeadlineFirst() throws Exception {
        MockHttpSession priya = employee();
        // Both go to Sara. Reported first but due in 8 hours (P3); reported second and due in 30 minutes (P1).
        String relaxed = report(priya, "A slow report page", "Cloud Storage", "Application", "MEDIUM", "MEDIUM");
        String urgent = reportPaymentOutage(priya);
        MockHttpSession sara = signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD);

        for (String page : new String[] {"/technician/dashboard", "/technician/incidents"}) {
            String html = mvc.perform(get(page).session(sara))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("My queue")))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html.indexOf(urgent)).isPositive().isLessThan(html.indexOf(relaxed));
        }
    }

    // ---- administrators: warning, assignment, settings ----

    @Test
    void administratorsAreWarnedWhenNoTechnicianCouldBeFoundAndCanAssignByHand() throws Exception {
        technician(ARJUN).setAvailable(false); // the only technician with Security
        MockHttpSession priya = employee();
        MockHttpSession admin = admin();

        String code = report(priya, "Suspicious login attempts", "Authentication Service", "Security", "HIGH", "HIGH");
        assertThat(statusOf(code)).isEqualTo(Status.CLASSIFIED);

        // The reporter sees that it is waiting.
        mvc.perform(get("/employee/incidents/" + code).session(priya))
                .andExpect(content().string(containsString("Nobody yet")))
                .andExpect(content().string(containsString("Automatic assignment failed")));

        // The administrator is warned on the dashboard and in the navbar, and on the incident itself.
        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(content().string(containsString("1 incident is waiting for a technician")))
                .andExpect(content().string(containsString("href=\"/admin/incidents/" + code + "\"")))
                .andExpect(content().string(containsString("title=\"Incidents waiting for a technician\"")));
        mvc.perform(get("/admin/incidents/" + code).session(admin))
                .andExpect(content().string(containsString("No technician with the skill Security")))
                .andExpect(content().string(containsString(">Assign</button>")))
                .andExpect(content().string(containsString("Assign automatically")));

        // Assign it by hand.
        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin).with(csrf())
                        .param("technicianId", String.valueOf(technician(RAVI).getId())))
                .andExpect(redirectedUrl("/admin/incidents/" + code))
                .andExpect(flash().attribute("success", containsString("Ravi Kumar")));

        assertThat(statusOf(code)).isEqualTo(Status.ASSIGNED);
        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(content().string(not(containsString("waiting for a technician"))));
    }

    @Test
    void anAdministratorCanReassignFromTheIncidentPage() throws Exception {
        MockHttpSession admin = admin();
        String code = reportPaymentOutage(employee()); // assigned to Sara

        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin).with(csrf())
                        .param("technicianId", String.valueOf(technician(RAVI).getId())))
                .andExpect(flash().attribute("success", containsString("Ravi Kumar")));

        mvc.perform(get("/admin/incidents/" + code).session(admin))
                .andExpect(content().string(containsString("Technician changed")))
                .andExpect(content().string(containsString("Moved from Sara Thomas to Ravi Kumar.")));
        // Ravi now has the button; Sara can still read the incident but can no longer act on it.
        mvc.perform(get("/technician/incidents/" + code).session(signIn(RAVI, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(content().string(containsString(">Acknowledge</button>")));
        mvc.perform(get("/technician/incidents/" + code).session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">Acknowledge</button>"))));
    }

    @Test
    void assigningToATechnicianWhoCannotTakeItIsRefusedWithAMessage() throws Exception {
        String code = reportPaymentOutage(employee());
        technician(RAVI).setAvailable(false);

        mvc.perform(post("/admin/incidents/" + code + "/assign").session(admin()).with(csrf())
                        .param("technicianId", String.valueOf(technician(RAVI).getId())))
                .andExpect(redirectedUrl("/admin/incidents/" + code))
                .andExpect(flash().attribute("error", containsString("Ravi Kumar is not available")));

        assertThat(incidents.findByCode(code).orElseThrow().getAssignee().getEmail()).isEqualTo(SARA);
    }

    @Test
    void onlyAdministratorsCanAssign() throws Exception {
        String code = reportPaymentOutage(employee());

        mvc.perform(post("/admin/incidents/" + code + "/assign")
                        .session(signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD)).with(csrf())
                        .param("technicianId", String.valueOf(technician(RAVI).getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void theSettingsPageSwitchesTheAssignmentStrategy() throws Exception {
        MockHttpSession admin = admin();

        mvc.perform(get("/admin/settings").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Skill-based")))
                .andExpect(content().string(containsString("Least loaded")))
                .andExpect(content().string(containsString("Round robin")));
        assertThat(assignmentService.getActiveStrategy().getKey()).isEqualTo(SkillBasedStrategy.KEY);

        mvc.perform(post("/admin/settings/assignment-strategy").session(admin).with(csrf())
                        .param("key", RoundRobinStrategy.KEY))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("success", containsString("Round robin")));

        assertThat(assignmentService.getActiveStrategy().getKey()).isEqualTo(RoundRobinStrategy.KEY);
        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(content().string(matchesPattern("(?s).*Assignment strategy:\\s*<a[^>]*>Round robin</a>.*")));
    }

    @Test
    void anUnknownStrategyIsRefused() throws Exception {
        mvc.perform(post("/admin/settings/assignment-strategy").session(admin()).with(csrf())
                        .param("key", "coin-toss"))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("error"));

        assertThat(assignmentService.getActiveStrategy().getKey()).isEqualTo(SkillBasedStrategy.KEY);
    }

    @Test
    void onlyAdministratorsCanOpenOrChangeTheSettings() throws Exception {
        MockHttpSession sara = signIn(SARA, DataSeeder.TECHNICIAN_PASSWORD);

        mvc.perform(get("/admin/settings").session(sara)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings/assignment-strategy").session(sara).with(csrf())
                        .param("key", RoundRobinStrategy.KEY))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private MockHttpSession employee() throws Exception {
        return signIn(PRIYA, DataSeeder.EMPLOYEE_PASSWORD);
    }

    private MockHttpSession admin() throws Exception {
        return signIn(ADMIN, DataSeeder.ADMIN_PASSWORD);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }

    private Technician technician(String email) {
        return (Technician) users.findByEmail(email).orElseThrow();
    }

    private Status statusOf(String code) {
        return incidents.findByCode(code).map(Incident::getStatus).orElseThrow();
    }

    /** The brief's example: impact 3 × urgency 3 × criticality 3 = 27, P1, goes to Sara (Application). */
    private String reportPaymentOutage(MockHttpSession reporter) throws Exception {
        return report(reporter, "Payment application is unavailable", "Payment Application", "Application",
                "HIGH", "HIGH");
    }

    /** Fills in and submits the report form; returns the new incident's code, taken from the redirect. */
    private String report(MockHttpSession reporter, String title, String service, String category,
                          String impact, String urgency) throws Exception {
        String redirect = mvc.perform(post("/employee/incidents").session(reporter).with(csrf())
                        .param("title", title)
                        .param("description", "Users are unable to complete transactions.")
                        .param("serviceId", String.valueOf(services.findByName(service).orElseThrow().getId()))
                        .param("categoryId",
                                String.valueOf(categories.findByNameIgnoreCase(category).orElseThrow().getId()))
                        .param("impact", impact)
                        .param("urgency", urgency))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/employee/incidents/INC-*"))
                .andExpect(flash().attribute("success", containsString("has been reported")))
                .andReturn().getResponse().getRedirectedUrl();
        return redirect.substring(redirect.lastIndexOf('/') + 1);
    }

    /** Presses a status button on the incident page of the given role. */
    private void move(MockHttpSession session, String rolePrefix, String code, Status target, String note)
            throws Exception {
        var request = post(rolePrefix + "/incidents/" + code + "/transition").session(session).with(csrf())
                .param("target", target.name());
        if (note != null) {
            request.param("note", note);
        }
        mvc.perform(request)
                .andExpect(redirectedUrl(rolePrefix + "/incidents/" + code))
                .andExpect(flash().attributeExists("success"));
    }
}
