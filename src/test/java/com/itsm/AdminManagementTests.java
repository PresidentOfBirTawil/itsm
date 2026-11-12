package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.SlaPolicyRepository;
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

/** The administrator's dashboard, the pages that manage services, categories, technicians and SLA policies, and the settings. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminManagementTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private UserRepository users;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private SlaService slaService;

    private MockHttpSession admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
    }

    private Technician technician(String email) {
        return (Technician) users.findByEmail(email).orElseThrow();
    }

    private Incident reportPaymentOutage(String title) {
        return incidentService.report((Employee) users.findByEmail("emp1@itsm.local").orElseThrow(), title,
                "Details.", services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
    }

    // ---- dashboard ----

    @Test
    void theDashboardShowsTheTotalsTheWorkloadAndIncidentsPerService() throws Exception {
        Technician sara = technician("tech4@itsm.local");
        Incident breached = reportPaymentOutage("Payment application is unavailable");     // open, P1
        slaService.refresh(breached, LocalDateTime.now().plusHours(1));                    // its SLA is breached
        Incident resolved = reportPaymentOutage("Payment page shows an error");
        incidentService.changeStatus(resolved.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(resolved.getCode(), Status.IN_PROGRESS, sara, null);
        incidentService.changeStatus(resolved.getCode(), Status.RESOLVED, sara, "Fixed.");
        incidentService.report((Employee) users.findByEmail("emp1@itsm.local").orElseThrow(), "Printer is jammed",
                "Details.", services.findByName("Email & Collaboration").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Hardware").orElseThrow().getId(), Impact.LOW, Urgency.LOW); // open, P4

        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 3L))
                .andExpect(model().attribute("openCount", 2L))
                .andExpect(model().attribute("criticalCount", 1L))   // open P1 only: the resolved P1 does not count
                .andExpect(model().attribute("resolvedCount", 1L))
                .andExpect(model().attribute("breachCount", 1L))
                .andExpect(model().attribute("busiestService", 2L))
                .andExpect(content().string(containsString("Technician workload")))
                .andExpect(content().string(containsString("Incidents per service")))
                .andExpect(content().string(containsString("Payment Application")))
                .andExpect(content().string(containsString("1 of 5")));  // Sara: one open incident
    }

    // ---- services ----

    @Test
    void anAdministratorCanAddAndChangeAService() throws Exception {
        mvc.perform(post("/admin/services").session(admin).with(csrf())
                        .param("name", "Payroll System").param("criticality", "2"))
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attribute("success", containsString("Payroll System")));
        ITService created = services.findByName("Payroll System").orElseThrow();
        assertThat(created.getCriticality()).isEqualTo(2);

        mvc.perform(get("/admin/services/" + created.getId() + "/edit").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payroll System")));

        mvc.perform(post("/admin/services/" + created.getId()).session(admin).with(csrf())
                        .param("name", "Payroll").param("criticality", "3"))
                .andExpect(redirectedUrl("/admin/services"));
        assertThat(services.findById(created.getId()).orElseThrow().getName()).isEqualTo("Payroll");
        assertThat(services.findById(created.getId()).orElseThrow().getCriticality()).isEqualTo(3);

        // Employees can report against the new service straight away.
        mvc.perform(get("/employee/incidents/new").session(signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(content().string(containsString(">Payroll</option>")));
    }

    @Test
    void aServiceNeedsAUniqueNameAndACriticalityFromOneToThree() throws Exception {
        long before = services.count();

        mvc.perform(post("/admin/services").session(admin).with(csrf())
                        .param("name", "payment application").param("criticality", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
        mvc.perform(post("/admin/services").session(admin).with(csrf())
                        .param("name", " ").param("criticality", "4"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "criticality"));

        assertThat(services.count()).isEqualTo(before);
        mvc.perform(get("/admin/services/999999/edit").session(admin)).andExpect(status().isNotFound());
    }

    // ---- categories ----

    @Test
    void anAdministratorCanAddAndChangeACategoryWithItsKeywords() throws Exception {
        mvc.perform(post("/admin/categories").session(admin).with(csrf())
                        .param("name", "Telephony").param("keywords", "Phone, VoIP ,  , headset"))
                .andExpect(redirectedUrl("/admin/categories"));
        Category created = categories.findByNameIgnoreCase("Telephony").orElseThrow();
        assertThat(created.getKeywords()).containsExactlyInAnyOrder("phone", "voip", "headset");

        mvc.perform(post("/admin/categories/" + created.getId()).session(admin).with(csrf())
                        .param("name", "Telephony").param("keywords", "desk phone"))
                .andExpect(redirectedUrl("/admin/categories"));
        assertThat(categories.findById(created.getId()).orElseThrow().getKeywords()).containsExactly("desk phone");

        mvc.perform(post("/admin/categories").session(admin).with(csrf()).param("name", "NETWORK"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }

    // ---- technicians ----

    @Test
    void changingATechniciansSkillsAndAvailabilityChangesWhoGetsIncidents() throws Exception {
        Technician sara = technician("tech4@itsm.local");
        Technician ravi = technician("tech1@itsm.local");
        Long application = categories.findByNameIgnoreCase("Application").orElseThrow().getId();
        Long network = categories.findByNameIgnoreCase("Network").orElseThrow().getId();

        // Sara goes away, and Ravi learns Application (keeping Network).
        mvc.perform(post("/admin/technicians/" + sara.getId()).session(admin).with(csrf())
                        .param("name", "Sara Thomas").param("maxWorkload", "5")
                        .param("skillIds", String.valueOf(application)))    // no "available" box ticked
                .andExpect(redirectedUrl("/admin/technicians"));
        mvc.perform(post("/admin/technicians/" + ravi.getId()).session(admin).with(csrf())
                        .param("name", "Ravi Kumar").param("maxWorkload", "2").param("available", "true")
                        .param("skillIds", String.valueOf(application), String.valueOf(network)))
                .andExpect(redirectedUrl("/admin/technicians"));

        assertThat(sara.isAvailable()).isFalse();
        assertThat(ravi.getMaxWorkload()).isEqualTo(2);
        assertThat(ravi.getSkills()).extracting(Category::getName).containsExactlyInAnyOrder("Application", "Network");

        // Application incidents now go to Ravi, until his new maximum of 2 is reached.
        assertThat(reportPaymentOutage("Payment application is unavailable").getAssignee()).isEqualTo(ravi);
        assertThat(reportPaymentOutage("Payment page shows an error").getAssignee()).isEqualTo(ravi);
        assertThat(reportPaymentOutage("Card payments are declined").getStatus()).isEqualTo(Status.CLASSIFIED);
    }

    @Test
    void anAdministratorCanAddATechnicianWhoCanThenSignIn() throws Exception {
        Long database = categories.findByNameIgnoreCase("Database").orElseThrow().getId();

        mvc.perform(post("/admin/technicians").session(admin).with(csrf())
                        .param("name", "Nina Patel").param("email", "Nina@itsm.local").param("password", "nina12345")
                        .param("maxWorkload", "3").param("available", "true")
                        .param("skillIds", String.valueOf(database)))
                .andExpect(redirectedUrl("/admin/technicians"));

        Technician nina = technician("nina@itsm.local");
        assertThat(nina.getSkills()).extracting(Category::getName).containsExactly("Database");
        assertThat(nina.getPasswordHash()).startsWith("$2");
        mvc.perform(formLogin("/login").userParameter("email").user("nina@itsm.local").password("nina12345"))
                .andExpect(authenticated().withRoles("TECHNICIAN"));
        mvc.perform(get("/admin/technicians").session(admin))
                .andExpect(content().string(containsString("Nina Patel")));
    }

    @Test
    void aTechnicianNeedsValidDetails() throws Exception {
        long before = users.count();

        mvc.perform(post("/admin/technicians").session(admin).with(csrf())
                        .param("name", "").param("email", "tech1@itsm.local").param("password", "short")
                        .param("maxWorkload", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "email", "password", "maxWorkload"));

        assertThat(users.count()).isEqualTo(before);
    }

    // ---- SLA policies ----

    @Test
    void anAdministratorCanChangeAnSlaPolicyAndNewIncidentsUseIt() throws Exception {
        SlaPolicy p1 = slaPolicies.findByPriority(Priority.P1).orElseThrow();

        mvc.perform(post("/admin/sla-policies/" + p1.getId()).session(admin).with(csrf())
                        .param("responseMinutes", "10").param("resolutionMinutes", "60"))
                .andExpect(redirectedUrl("/admin/sla-policies"))
                .andExpect(flash().attributeExists("success"));

        Incident incident = reportPaymentOutage("Payment application is unavailable");
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(10));
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(60));
    }

    @Test
    void anSlaPolicyThatMakesNoSenseIsRefused() throws Exception {
        SlaPolicy p1 = slaPolicies.findByPriority(Priority.P1).orElseThrow();

        mvc.perform(post("/admin/sla-policies/" + p1.getId()).session(admin).with(csrf())
                        .param("responseMinutes", "90").param("resolutionMinutes", "60"))
                .andExpect(flash().attribute("error", containsString("cannot be longer")));
        mvc.perform(post("/admin/sla-policies/" + p1.getId()).session(admin).with(csrf())
                        .param("responseMinutes", "0").param("resolutionMinutes", "60"))
                .andExpect(flash().attribute("error", containsString("at least 1 minute")));

        assertThat(p1.getResponseMinutes()).isEqualTo(5);
        assertThat(p1.getResolutionMinutes()).isEqualTo(30);
    }

    // ---- settings ----

    @Test
    void theSettingsPageSwitchesDemoSlaOnAndOffAndCanRunTheSlaCheck() throws Exception {
        mvc.perform(get("/admin/settings").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Demo SLA")))
                .andExpect(content().string(containsString("Switch demo SLA on")));

        mvc.perform(post("/admin/settings/demo-sla").session(admin).with(csrf()).param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("success", containsString("Demo SLA is on")));
        assertThat(slaService.isDemoMode()).isTrue();

        Incident incident = reportPaymentOutage("Payment application is unavailable");
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(1));

        mvc.perform(post("/admin/settings/check-sla").session(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("success", containsString("1 open incident checked")));

        mvc.perform(post("/admin/settings/demo-sla").session(admin).with(csrf()).param("enabled", "false"))
                .andExpect(flash().attribute("success", containsString("Demo SLA is off")));
        assertThat(slaService.isDemoMode()).isFalse();
    }

    // ---- only administrators ----

    @Test
    void onlyAdministratorsCanManage() throws Exception {
        MockHttpSession technician = signIn("tech1@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);

        for (String page : new String[] {"/admin/services", "/admin/services/new", "/admin/categories",
                "/admin/technicians", "/admin/sla-policies", "/admin/settings", "/admin/emails"}) {
            mvc.perform(get(page).session(admin)).andExpect(status().isOk());
            mvc.perform(get(page).session(technician)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/services").session(technician).with(csrf())
                        .param("name", "Sneaky").param("criticality", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings/demo-sla").session(technician).with(csrf()).param("enabled", "true"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
