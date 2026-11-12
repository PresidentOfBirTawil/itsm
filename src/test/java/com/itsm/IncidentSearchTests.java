package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.search.IncidentSearchCriteria;
import com.itsm.search.IncidentSpecifications;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import com.itsm.sla.SlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search with Spring Data JPA Specifications. Four incidents are set up that
 * differ in the things the filters look at, and the tests check that each
 * filter, and all of them together, find the right ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentSearchTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private SlaService slaService;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private Technician sara;
    private Technician ravi;

    /** P1, Payment Application, Application, Sara, IN_PROGRESS, SLA breached. */
    private Incident paymentOutage;
    /** P1, Payment Application, Application, Sara, ASSIGNED, on track. */
    private Incident paymentError;
    /** P3, Corporate Network, Hardware, Ravi, ASSIGNED, on track. */
    private Incident brokenRouter;
    /** P4, Email & Collaboration, Hardware, Ravi, RESOLVED, SLA met. */
    private Incident jammedPrinter;

    @BeforeEach
    void reportFourIncidents() {
        Employee priya = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        sara = (Technician) users.findByEmail("tech4@itsm.local").orElseThrow();
        ravi = (Technician) users.findByEmail("tech1@itsm.local").orElseThrow();

        paymentOutage = report(priya, "Payment application is unavailable", "Payment Application", "Application",
                Impact.HIGH, Urgency.HIGH);
        incidentService.changeStatus(paymentOutage.getCode(), Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(paymentOutage.getCode(), Status.IN_PROGRESS, sara, null);
        slaService.refresh(paymentOutage, LocalDateTime.now().plusMinutes(31)); // past its 30-minute deadline

        paymentError = report(priya, "Payment page shows an error", "Payment Application", "Application",
                Impact.HIGH, Urgency.HIGH);

        brokenRouter = report(priya, "Router on floor 2 keeps restarting", "Corporate Network", "Hardware",
                Impact.MEDIUM, Urgency.MEDIUM);

        jammedPrinter = report(priya, "Printer is jammed", "Email & Collaboration", "Hardware",
                Impact.LOW, Urgency.LOW);
        incidentService.changeStatus(jammedPrinter.getCode(), Status.ACKNOWLEDGED, ravi, null);
        incidentService.changeStatus(jammedPrinter.getCode(), Status.IN_PROGRESS, ravi, null);
        incidentService.changeStatus(jammedPrinter.getCode(), Status.RESOLVED, ravi, "Removed the paper.");
    }

    private Incident report(Employee reporter, String title, String service, String category,
                            Impact impact, Urgency urgency) {
        return incidentService.report(reporter, title, "Details.", services.findByName(service).orElseThrow().getId(),
                categories.findByNameIgnoreCase(category).orElseThrow().getId(), impact, urgency);
    }

    private List<Incident> search(IncidentSearchCriteria criteria) {
        return incidents.findAll(IncidentSpecifications.matching(criteria), Sort.by("id"));
    }

    // ---- one combined search ----

    @Test
    void allFiltersTogetherFindExactlyTheIncidentThatMatchesEveryOne() {
        IncidentSearchCriteria criteria = new IncidentSearchCriteria();
        criteria.setCode(paymentOutage.getCode().toLowerCase());                 // code, any letter case
        criteria.setStatus(Status.IN_PROGRESS);
        criteria.setPriority(Priority.P1);
        criteria.setCategoryId(categories.findByNameIgnoreCase("Application").orElseThrow().getId());
        criteria.setServiceId(services.findByName("Payment Application").orElseThrow().getId());
        criteria.setTechnicianId(sara.getId());
        criteria.setFrom(LocalDate.now().minusDays(1));                          // date range
        criteria.setTo(LocalDate.now());
        criteria.setSlaStatus(SlaStatus.BREACHED);

        assertThat(search(criteria)).containsExactly(paymentOutage);
    }

    @Test
    void changingAnyOneFilterOfTheCombinedSearchLosesTheMatch() {
        // Same priority, service, category and technician as the outage, but another status and SLA status.
        IncidentSearchCriteria criteria = new IncidentSearchCriteria();
        criteria.setPriority(Priority.P1);
        criteria.setServiceId(services.findByName("Payment Application").orElseThrow().getId());
        criteria.setTechnicianId(sara.getId());
        assertThat(search(criteria)).containsExactly(paymentOutage, paymentError);

        criteria.setSlaStatus(SlaStatus.BREACHED);
        assertThat(search(criteria)).containsExactly(paymentOutage);

        criteria.setStatus(Status.ASSIGNED);                                     // breached AND assigned: nobody
        assertThat(search(criteria)).isEmpty();
    }

    // ---- each filter on its own ----

    @Test
    void noFiltersFindEverything() {
        IncidentSearchCriteria criteria = new IncidentSearchCriteria();

        assertThat(criteria.isEmpty()).isTrue();
        assertThat(search(criteria)).containsExactly(paymentOutage, paymentError, brokenRouter, jammedPrinter);
    }

    @Test
    void eachFilterNarrowsTheListOnItsOwn() {
        IncidentSearchCriteria byStatus = new IncidentSearchCriteria();
        byStatus.setStatus(Status.ASSIGNED);
        assertThat(search(byStatus)).containsExactly(paymentError, brokenRouter);

        IncidentSearchCriteria byPriority = new IncidentSearchCriteria();
        byPriority.setPriority(Priority.P3);
        assertThat(search(byPriority)).containsExactly(brokenRouter);

        IncidentSearchCriteria byCategory = new IncidentSearchCriteria();
        byCategory.setCategoryId(categories.findByNameIgnoreCase("Hardware").orElseThrow().getId());
        assertThat(search(byCategory)).containsExactly(brokenRouter, jammedPrinter);

        IncidentSearchCriteria byService = new IncidentSearchCriteria();
        byService.setServiceId(services.findByName("Corporate Network").orElseThrow().getId());
        assertThat(search(byService)).containsExactly(brokenRouter);

        IncidentSearchCriteria byTechnician = new IncidentSearchCriteria();
        byTechnician.setTechnicianId(ravi.getId());
        assertThat(search(byTechnician)).containsExactly(brokenRouter, jammedPrinter);

        IncidentSearchCriteria bySla = new IncidentSearchCriteria();
        bySla.setSlaStatus(SlaStatus.MET);
        assertThat(search(bySla)).containsExactly(jammedPrinter);
    }

    @Test
    void theCodeFilterFindsPartOfACode() {
        IncidentSearchCriteria criteria = new IncidentSearchCriteria();
        criteria.setCode(String.valueOf(brokenRouter.getId())); // just the number

        assertThat(search(criteria)).contains(brokenRouter);

        criteria.setCode("INC-");
        assertThat(search(criteria)).hasSize(4);

        criteria.setCode("no such code");
        assertThat(search(criteria)).isEmpty();
    }

    @Test
    void theDateRangeIncludesBothEndDays() {
        IncidentSearchCriteria today = new IncidentSearchCriteria();
        today.setFrom(LocalDate.now());
        today.setTo(LocalDate.now());
        assertThat(search(today)).hasSize(4);

        IncidentSearchCriteria beforeToday = new IncidentSearchCriteria();
        beforeToday.setTo(LocalDate.now().minusDays(1));
        assertThat(search(beforeToday)).isEmpty();

        IncidentSearchCriteria afterToday = new IncidentSearchCriteria();
        afterToday.setFrom(LocalDate.now().plusDays(1));
        assertThat(search(afterToday)).isEmpty();
    }

    // ---- the search pages ----

    @Test
    void theAdministratorsIncidentPageFiltersWithTheFormAndShowsSlaBadges() throws Exception {
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/incidents").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(paymentOutage.getCode())))
                .andExpect(content().string(containsString(jammedPrinter.getCode())))
                .andExpect(content().string(containsString(">Breached</span>")))
                .andExpect(content().string(containsString(">Met</span>")))
                .andExpect(content().string(containsString(">On track</span>")));

        mvc.perform(get("/admin/incidents").session(admin)
                        .param("priority", "P1").param("slaStatus", "BREACHED")
                        .param("technicianId", String.valueOf(sara.getId()))
                        .param("from", LocalDate.now().toString()).param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/incidents/" + paymentOutage.getCode() + "\"")))
                .andExpect(content().string(not(containsString(paymentError.getCode()))))
                .andExpect(content().string(not(containsString(brokenRouter.getCode()))));

        mvc.perform(get("/admin/incidents").session(admin).param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No incident matches these filters.")));
    }

    @Test
    void techniciansCanSearchAllIncidentsAndOpenTheResultsReadOnly() throws Exception {
        MockHttpSession saraSession = signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);

        // Sara finds an incident that belongs to Ravi ...
        mvc.perform(get("/technician/search").session(saraSession)
                        .param("technicianId", String.valueOf(ravi.getId())).param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/technician/incidents/" + brokenRouter.getCode() + "\"")))
                .andExpect(content().string(not(containsString(paymentOutage.getCode()))));

        // ... and can read it, but has no button to change it.
        mvc.perform(get("/technician/incidents/" + brokenRouter.getCode()).session(saraSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"target\""))));
    }

    @Test
    void employeesCannotSearch() throws Exception {
        MockHttpSession employee = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/technician/search").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/incidents").session(employee)).andExpect(status().isForbidden());
    }

    @Test
    void aDateThatIsNotADateShowsAMessageInsteadOfAnErrorPage() throws Exception {
        mvc.perform(get("/admin/incidents").session(signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD))
                        .param("from", "yesterday"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please enter a date.")));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
