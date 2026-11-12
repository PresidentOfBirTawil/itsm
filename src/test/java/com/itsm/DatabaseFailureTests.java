package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.itsm.report.ReportService;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The brief's "database failure" case. The two services here are "spies": the
 * real services, which a test can tell to fail the way they would if the
 * database could not be reached. The user must get a readable page with the
 * right status, never a stack trace, and nothing half-saved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseFailureTests {

    @MockitoSpyBean
    private ReportService reportService;
    @MockitoSpyBean
    private IncidentService incidentService;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    @Test
    void aPageThatCannotReachTheDatabaseShowsAReadableMessage() throws Exception {
        doThrow(new DataAccessResourceFailureException("Connection is closed [90067-240]"))
                .when(reportService).build();
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/reports").session(admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("error/problem"))
                .andExpect(content().string(containsString("The database could not be reached just now")))
                .andExpect(content().string(not(containsString("Connection is closed"))))   // no technical detail
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void anIncidentThatCannotBeSavedIsReportedToTheUserAndNothingIsStored() throws Exception {
        doThrow(new DataAccessResourceFailureException("Unable to acquire JDBC Connection"))
                .when(incidentService).report(any(), any(), any(), any(), any(), any(), any());
        MockHttpSession employee = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        long before = incidents.count();

        mvc.perform(post("/employee/incidents").session(employee).with(csrf())
                        .param("title", "Payment application is unavailable")
                        .param("description", "Users are unable to complete transactions.")
                        .param("serviceId", services.findByName("Payment Application").orElseThrow().getId().toString())
                        .param("categoryId", categories.findByNameIgnoreCase("Application").orElseThrow().getId().toString())
                        .param("impact", "HIGH").param("urgency", "HIGH"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("Please try again in a moment")));

        assertThat(incidents.count()).isEqualTo(before);
    }

    @Test
    void twoChangesThatClashGiveAConflictPage() throws Exception {
        doThrow(new DataIntegrityViolationException("Unique index or primary key violation"))
                .when(reportService).build();
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/admin/reports").session(admin))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("clashes with something that was saved a moment ago")))
                .andExpect(content().string(not(containsString("Unique index"))));
    }

    @Test
    void afterTheFailureTheApplicationCarriesOnWorking() throws Exception {
        doThrow(new DataAccessResourceFailureException("Connection is closed")).when(reportService).build();
        MockHttpSession admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
        mvc.perform(get("/admin/reports").session(admin)).andExpect(status().isServiceUnavailable());

        mvc.perform(get("/admin/dashboard").session(admin)).andExpect(status().isOk());   // same session, next page
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
