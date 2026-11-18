package com.quell;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.quell.assignment.AssignmentService;
import com.quell.assignment.SkillBasedStrategy;
import com.quell.assignment.TechnicianLoad;
import com.quell.domain.Employee;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.IncidentAction;
import com.quell.domain.Priority;
import com.quell.domain.SlaStatus;
import com.quell.domain.Status;
import com.quell.domain.Urgency;
import com.quell.intelligence.CategoryClassifier;
import com.quell.intelligence.ResolutionRecommender;
import com.quell.repository.AuditEntryRepository;
import com.quell.repository.CategoryRepository;
import com.quell.repository.EmailMessageRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.IncidentRepository;
import com.quell.repository.NotificationRepository;
import com.quell.repository.SlaPolicyRepository;
import com.quell.repository.UserRepository;
import com.quell.seed.DataSeeder;
import com.quell.seed.DemoDataService;
import com.quell.service.IncidentService;
import com.quell.sla.SlaForecast;
import com.quell.sla.SlaPredictor;
import com.quell.sla.SlaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "Reset demo data". The reset restarts the incident numbering, which the
 * database commits on the spot, so these tests are not rolled back like most
 * others. Instead the application (and with it the in-memory database) is
 * thrown away once this class has finished, so nothing leaks into other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoDataResetTests {

    private static final int SAMPLES = DemoDataService.SAMPLE_COUNT;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DemoDataService demoData;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private SlaService slaService;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private AuditEntryRepository auditEntries;
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
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private SlaPredictor slaPredictor;
    @Autowired
    private ResolutionRecommender recommender;
    @Autowired
    private CategoryClassifier classifier;

    private Incident reportPaymentOutage() {
        return report("Payment application is unavailable");
    }

    private Incident report(String title) {
        return incidentService.report((Employee) users.findByEmail("emp1@quell.local").orElseThrow(),
                title, "Details.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
    }

    @Test
    void theSampleSetCoversEveryStageOfTheLifecycle() {
        int added = demoData.reset(true);

        assertThat(added).isEqualTo(SAMPLES);
        transaction.executeWithoutResult(tx -> {
            List<Incident> all = incidents.findAll();
            assertThat(all).hasSize(SAMPLES);
            assertThat(all).extracting(Incident::getCode)
                    .contains("INC-0001", Incident.codeFor(SAMPLES)).doesNotHaveDuplicates();

            Set<Status> statuses = all.stream().map(Incident::getStatus).collect(Collectors.toSet());
            assertThat(statuses).contains(Status.CLASSIFIED, Status.ASSIGNED, Status.ACKNOWLEDGED,
                    Status.IN_PROGRESS, Status.AWAITING_INFO, Status.RESOLVED, Status.VERIFIED, Status.CLOSED);
            assertThat(all).extracting(Incident::getPriority).contains(Priority.values());
            assertThat(all).extracting(Incident::getSlaStatus)
                    .contains(SlaStatus.ON_TRACK, SlaStatus.AT_RISK, SlaStatus.PAUSED, SlaStatus.BREACHED, SlaStatus.MET);

            assertThat(all).filteredOn(Incident::isEscalated).isNotEmpty();
            assertThat(all).filteredOn(incident -> incident.getPossibleDuplicateOf() != null).hasSize(1);
            assertThat(all).filteredOn(incident -> incident.getRating() != null).hasSizeGreaterThanOrEqualTo(8);
            assertThat(all).filteredOn(incident -> incident.getAssignee() == null)
                    .singleElement().satisfies(waiting -> assertThat(waiting.getStatus()).isEqualTo(Status.CLASSIFIED));

            // They are spread over the past: the oldest is days old, and none is in the future.
            LocalDateTime now = LocalDateTime.now();
            assertThat(all).allSatisfy(incident -> assertThat(incident.getCreatedAt()).isBeforeOrEqualTo(now));
            assertThat(all).anySatisfy(incident -> assertThat(incident.getCreatedAt()).isBefore(now.minusDays(7)));
        });
    }

    @Test
    void everySampleIncidentObeysTheRulesARealOneDoes() {
        demoData.reset(true);

        transaction.executeWithoutResult(tx -> {
            for (Incident incident : incidents.findAll()) {
                // Each has a full history that starts with "reported" and ends in its current status.
                var history = incidentService.history(incident);
                assertThat(history).as(incident.getCode()).isNotEmpty();
                assertThat(history.get(0).getAction()).isEqualTo(IncidentAction.REPORTED);
                assertThat(history).isSortedAccordingTo((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()));
                assertThat(history.stream().filter(entry -> entry.isStatusChange()).reduce((a, b) -> b)
                        .orElseThrow().getNewStatus()).as(incident.getCode()).isEqualTo(incident.getStatus());

                // Priority, deadlines and the fields each status requires are all in place.
                assertThat(incident.getPriority()).isNotNull();
                assertThat(incident.getResponseDue()).isAfter(incident.getCreatedAt());
                assertThat(incident.getResolutionDue()).isAfter(incident.getResponseDue());
                if (!incident.getStatus().isOpen()) {
                    assertThat(incident.getResolution()).as(incident.getCode()).isNotBlank();
                    assertThat(incident.getResolvedAt()).isAfterOrEqualTo(incident.getCreatedAt());
                }
                if (incident.getStatus() == Status.CLOSED) {
                    assertThat(incident.getClosedAt()).isAfterOrEqualTo(incident.getResolvedAt());
                }
                if (incident.getRating() != null) {
                    assertThat(incident.getRating()).isBetween(Incident.MIN_RATING, Incident.MAX_RATING);
                }
            }
            // No technician was given more open incidents than their maximum.
            assertThat(assignmentService.technicianLoads())
                    .allSatisfy(load -> assertThat(load.openIncidents()).isLessThanOrEqualTo(load.technician().getMaxWorkload()));
        });
    }

    @Test
    void theResetRemovesWhatWasThereAndKeepsTheMasterData() {
        long userCount = users.count();
        long serviceCount = services.count();
        long categoryCount = categories.count();
        reportPaymentOutage();                                     // committed: an incident, history and notifications
        assignmentService.useStrategy("least-loaded");
        slaService.setDemoMode(true);
        assertThat(notifications.count()).isPositive();
        assertThat(emails.count()).isPositive();

        demoData.reset(true);

        assertThat(incidents.count()).isEqualTo(SAMPLES);
        assertThat(incidents.findAll()).extracting(Incident::getTitle)
                .doesNotContain("Payment application is unavailable");
        assertThat(notifications.count()).isZero();                // sample incidents notify nobody
        assertThat(emails.count()).isZero();
        assertThat(auditEntries.count()).isGreaterThan(SAMPLES * 3L);
        assertThat(assignmentService.getActiveStrategy().getKey()).isEqualTo(SkillBasedStrategy.KEY);
        assertThat(slaService.isDemoMode()).isFalse();
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(services.count()).isEqualTo(serviceCount);
        assertThat(categories.count()).isEqualTo(categoryCount);
        assertThat(slaPolicies.count()).isEqualTo(4);
    }

    @Test
    void resettingToAnEmptyListStartsTheNumberingAgain() {
        report("Payment application is unavailable");
        report("Laptop will not start");

        int added = demoData.reset(false);

        assertThat(added).isZero();
        assertThat(incidents.count()).isZero();
        assertThat(auditEntries.count()).isZero();
        assertThat(reportPaymentOutage().getCode()).isEqualTo("INC-0001");
    }

    @Test
    void resettingTwiceGivesTheSameResultAsResettingOnce() {
        demoData.reset(true);
        demoData.reset(true);

        assertThat(incidents.count()).isEqualTo(SAMPLES);
        assertThat(incidents.findAll()).extracting(Incident::getCode).contains("INC-0001").doesNotContain(Incident.codeFor(SAMPLES + 1));
    }

    @Test
    void theApplicationWorksNormallyOnTopOfTheSampleData() throws Exception {
        demoData.reset(true);
        MockHttpSession admin = signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD);

        slaService.checkOpenIncidents(LocalDateTime.now());        // the every-minute check copes with them
        Incident next = reportPaymentOutage();                     // and new work carries on after them

        assertThat(next.getCode()).isEqualTo(Incident.codeFor(SAMPLES + 1));
        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", SAMPLES + 1L));
        mvc.perform(get("/admin/reports").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/audit").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/incidents").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/technician/dashboard").session(signIn("tech1@quell.local", DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/employee/dashboard").session(signIn("emp1@quell.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void theSampleHistoryGivesTheLearningFeaturesSomethingToLearnFrom() {
        demoData.reset(true);

        transaction.executeWithoutResult(tx -> {
            List<Incident> open = incidents.findByStatusIn(Status.openStatuses());
            Map<String, SlaForecast> forecasts = slaPredictor.forecastAll(open, LocalDateTime.now());
            assertThat(forecasts).hasSizeGreaterThanOrEqualTo(open.size() / 2);   // most open incidents get a forecast
            assertThat(forecasts.values()).allSatisfy(forecast -> assertThat(forecast.breachPercent()).isBetween(0, 100));

            Incident vpn = open.stream().filter(incident -> incident.getTitle().startsWith("VPN connection drops"))
                    .findFirst().orElseThrow();
            assertThat(recommender.recommend(vpn, 3)).isNotEmpty()
                    .first().satisfies(best -> assertThat(best.sharedWords()).contains("vpn"));
            assertThat(classifier.trainingSize()).isGreaterThan(SAMPLES);
        });
    }

    @Test
    void theAdministratorResetsFromTheSettingsPage() throws Exception {
        MockHttpSession admin = signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/settings").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reset and add sample incidents")));

        mvc.perform(post("/admin/settings/reset-demo-data").session(admin).with(csrf()).param("samples", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("success", "The demo data has been reset: " + SAMPLES + " sample incidents were added."));
        assertThat(incidents.count()).isEqualTo(SAMPLES);

        mvc.perform(post("/admin/settings/reset-demo-data").session(admin).with(csrf()).param("samples", "false"))
                .andExpect(flash().attribute("success", containsString("All incidents have been removed")));
        assertThat(incidents.count()).isZero();
    }

    @Test
    void onlyAnAdministratorCanReset() throws Exception {
        demoData.reset(true);

        mvc.perform(post("/admin/settings/reset-demo-data")
                        .session(signIn("emp1@quell.local", DataSeeder.EMPLOYEE_PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings/reset-demo-data")
                        .session(signIn("tech1@quell.local", DataSeeder.TECHNICIAN_PASSWORD)).with(csrf())
                        .param("samples", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings/reset-demo-data")
                        .session(signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD)))     // no CSRF token
                .andExpect(status().isForbidden());

        assertThat(incidents.count()).isEqualTo(SAMPLES);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
