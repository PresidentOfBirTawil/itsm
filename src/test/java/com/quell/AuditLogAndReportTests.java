package com.quell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.quell.domain.AuditEntry;
import com.quell.domain.Employee;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.IncidentAction;
import com.quell.domain.Priority;
import com.quell.domain.Status;
import com.quell.domain.Urgency;
import com.quell.domain.User;
import com.quell.report.ReportService;
import com.quell.report.Reports;
import com.quell.repository.AuditEntryRepository;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.UserRepository;
import com.quell.seed.DataSeeder;
import com.quell.service.IncidentService;
import com.quell.sla.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Limit;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The administrator's audit log across all incidents, and the reports page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogAndReportTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private SlaService slaService;
    @Autowired
    private AuditEntryRepository auditEntries;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private MockHttpSession admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD);
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Incident report(String title, String service, String category, Impact impact, Urgency urgency) {
        return incidentService.report((Employee) user("emp1@quell.local"), title, "Details.",
                services.findByName(service).orElseThrow().getId(),
                categories.findByNameIgnoreCase(category).orElseThrow().getId(), impact, urgency);
    }

    /** A P1 payment incident, taken to CLOSED by Sara Thomas and rated by the reporter. */
    private Incident closedPaymentIncident(int stars) {
        Incident incident = report("Payment application is unavailable", "Payment Application", "Application",
                Impact.HIGH, Urgency.HIGH);
        User sara = incident.getAssignee();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, sara, "Restarted the gateway.");
        incidentService.changeStatus(incident.getCode(), Status.VERIFIED, user("emp1@quell.local"), null);
        incidentService.rate(incident.getCode(), user("emp1@quell.local"), stars, null);
        incidentService.changeStatus(incident.getCode(), Status.CLOSED, user("admin@quell.local"), null);
        return incident;
    }

    // ---- audit log ----

    @Test
    void theAuditLogShowsEveryActionOnEveryIncidentNewestFirst() throws Exception {
        Incident first = report("Printer is jammed", "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW);
        Incident second = closedPaymentIncident(5);

        List<AuditEntry> log = auditEntries.search(null, null, Limit.of(300));

        assertThat(log).extracting(entry -> entry.getIncident().getCode())
                .contains(first.getCode(), second.getCode());
        assertThat(log).hasSize(incidentService.history(first).size() + incidentService.history(second).size());
        assertThat(log.get(0).getAction()).isEqualTo(IncidentAction.STATUS_CHANGED);   // the last thing done: closed
        assertThat(log.get(0).getNewStatus()).isEqualTo(Status.CLOSED);
        assertThat(log.get(0).getActorName()).isEqualTo("Neha Kapoor");
        assertThat(log.get(log.size() - 1).getAction()).isEqualTo(IncidentAction.REPORTED); // the first thing done
        assertThat(log).isSortedAccordingTo((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));

        mvc.perform(get("/admin/audit").session(admin))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entries", hasSize(log.size())))
                .andExpect(content().string(containsString(first.getCode())))
                .andExpect(content().string(containsString(second.getCode())))
                .andExpect(content().string(containsString("Sara Thomas")))
                .andExpect(content().string(containsString("Restarted the gateway.")));
    }

    @Test
    void theAuditLogCanBeNarrowedToOneIncidentAndOneKindOfAction() throws Exception {
        Incident first = report("Printer is jammed", "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW);
        Incident second = closedPaymentIncident(4);
        String number = String.valueOf(second.getId());                  // "7" finds INC-0007 and nothing else

        mvc.perform(get("/admin/audit").session(admin).param("code", number))
                .andExpect(status().isOk())
                .andExpect(model().attribute("entries", hasSize(incidentService.history(second).size())))
                .andExpect(content().string(not(containsString("Printer is jammed"))))
                .andExpect(content().string(not(containsString("/admin/incidents/" + first.getCode() + "\""))));

        mvc.perform(get("/admin/audit").session(admin).param("code", second.getCode().toLowerCase())
                        .param("action", "RATED"))
                .andExpect(model().attribute("entries", hasSize(1)))
                .andExpect(content().string(containsString("Rated 4 out of 5")));

        mvc.perform(get("/admin/audit").session(admin).param("code", "INC-9999"))
                .andExpect(model().attribute("entries", hasSize(0)))
                .andExpect(content().string(containsString("Nothing has been recorded that matches")));
    }

    @Test
    void anActionThatDoesNotExistIsABadRequestNotACrash() throws Exception {
        mvc.perform(get("/admin/audit").session(admin).param("action", "DELETED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdministratorsCanReadTheAuditLogAndTheReports() throws Exception {
        MockHttpSession employee = signIn("emp1@quell.local", DataSeeder.EMPLOYEE_PASSWORD);
        MockHttpSession technician = signIn("tech1@quell.local", DataSeeder.TECHNICIAN_PASSWORD);

        mvc.perform(get("/admin/audit").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").session(technician)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reports").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reports").session(technician)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit")).andExpect(status().is3xxRedirection());       // not signed in at all
    }

    // ---- reports ----

    @Test
    void theReportsAreEmptyButReadableBeforeAnyIncidentExists() throws Exception {
        Reports reports = reportService.build();

        assertThat(reports.total()).isZero();
        assertThat(reports.slaCompliancePercent()).isNull();   // nothing settled yet: no percentage, not 0% or 100%
        assertThat(reports.averageRating()).isNull();
        mvc.perform(get("/admin/reports").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("There are no incidents to report on yet")));
    }

    @Test
    void theReportsCountIncidentsByStatusPriorityAndCategory() {
        closedPaymentIncident(5);
        closedPaymentIncident(3);
        report("Printer is jammed", "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW);

        Reports reports = reportService.build();

        assertThat(reports.total()).isEqualTo(3);
        assertThat(count(reports.byStatus(), "Closed")).isEqualTo(2);
        assertThat(count(reports.byStatus(), "Assigned")).isEqualTo(1);
        assertThat(count(reports.byStatus(), "In progress")).isZero();        // statuses with nothing are still listed
        assertThat(reports.byStatus()).hasSize(Status.values().length);
        assertThat(reports.byPriority()).extracting(Reports.Count::count).containsExactly(2L, 0L, 0L, 1L); // P1…P4
        assertThat(count(reports.byCategory(), "Application")).isEqualTo(2);
        assertThat(count(reports.byCategory(), "Hardware")).isEqualTo(1);
        assertThat(Reports.largest(reports.byStatus())).isEqualTo(2);
    }

    @Test
    void theReportsWorkOutSlaComplianceRatingsAndTechnicianFigures() {
        closedPaymentIncident(5);                                              // resolved in time
        closedPaymentIncident(3);                                              // resolved in time
        Incident late = report("Payment page shows an error", "Payment Application", "Application",
                Impact.HIGH, Urgency.HIGH);
        slaService.refresh(late, LocalDateTime.now().plusHours(2));            // both P1 deadlines missed

        Reports reports = reportService.build();

        assertThat(reports.slaMet()).isEqualTo(2);
        assertThat(reports.slaBreached()).isEqualTo(1);
        assertThat(reports.slaCompliancePercent()).isEqualTo(67L);             // 2 of 3, rounded
        assertThat(reports.ratedCount()).isEqualTo(2);
        assertThat(reports.averageRating()).isEqualTo(4.0);

        Reports.TechnicianLine sara = reports.technicians().stream()
                .filter(line -> line.name().equals("Sara Thomas")).findFirst().orElseThrow();
        assertThat(sara.assigned()).isEqualTo(3);
        assertThat(sara.open()).isEqualTo(1);
        assertThat(sara.resolved()).isEqualTo(2);
        assertThat(sara.slaBreached()).isEqualTo(1);
        assertThat(sara.averageRating()).isEqualTo(4.0);
        assertThat(reports.technicians()).hasSize(4);                          // technicians with nothing are listed too

        Reports.ResolutionTime p1 = reports.resolutionTimes().get(0);
        assertThat(p1.priority()).isEqualTo(Priority.P1);
        assertThat(p1.resolved()).isEqualTo(2);
        assertThat(p1.targetMinutes()).isEqualTo(30);
        assertThat(p1.isWithinTarget()).isTrue();                              // resolved within the same second
        assertThat(reports.resolutionTimes().get(3).resolved()).isZero();      // no P4 was resolved
    }

    @Test
    void theReportsPageShowsTheFigures() throws Exception {
        closedPaymentIncident(5);

        mvc.perform(get("/admin/reports").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("By status")))
                .andExpect(content().string(containsString("By priority")))
                .andExpect(content().string(containsString("By category")))
                .andExpect(content().string(containsString("Average time to resolve")))
                .andExpect(content().string(containsString("Sara Thomas")))
                .andExpect(content().string(containsString("100%")));
    }

    private static long count(List<Reports.Count> counts, String label) {
        return counts.stream().filter(count -> count.label().equals(label)).findFirst().orElseThrow().count();
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
