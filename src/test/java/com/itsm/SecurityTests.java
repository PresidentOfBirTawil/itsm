package com.itsm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sign-in and role checks, exercised through the real security filter chain
 * with the seeded demo users (real form login, real BCrypt check).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTests {

    private static final String ADMIN = "admin@itsm.local";
    private static final String TECHNICIAN = "tech1@itsm.local";
    private static final String EMPLOYEE = "emp1@itsm.local";

    @Autowired
    private MockMvc mvc;

    // ---- signing in ----

    @Test
    void wrongPasswordFails() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user(EMPLOYEE).password("not-the-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unknownEmailFails() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("nobody@itsm.local").password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void correctPasswordSignsInWithTheUsersRole() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user(EMPLOYEE).password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(authenticated().withUsername(EMPLOYEE).withRoles("EMPLOYEE"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("EMP1@ITSM.LOCAL").password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(authenticated().withRoles("EMPLOYEE"));
    }

    // ---- role checks on the server ----

    @Test
    void employeeCannotOpenAdmin() throws Exception {
        MockHttpSession employee = signIn(EMPLOYEE, DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/admin").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/dashboard").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(employee)).andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotOpenTechnicianPages() throws Exception {
        MockHttpSession employee = signIn(EMPLOYEE, DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(get("/technician/dashboard").session(employee)).andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotOpenAdminOrEmployeePages() throws Exception {
        MockHttpSession technician = signIn(TECHNICIAN, DataSeeder.TECHNICIAN_PASSWORD);

        mvc.perform(get("/admin/dashboard").session(technician)).andExpect(status().isForbidden());
        mvc.perform(get("/employee/dashboard").session(technician)).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotOpenEmployeeOrTechnicianPages() throws Exception {
        MockHttpSession admin = signIn(ADMIN, DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/employee/dashboard").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/technician/dashboard").session(admin)).andExpect(status().isForbidden());
    }

    @Test
    void visitorIsSentToTheLoginPage() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/employee/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginAndRegisterPagesAreOpenToEveryone() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    // ---- one dashboard and one navbar per role ----

    @Test
    void eachRoleLandsOnItsOwnDashboard() throws Exception {
        mvc.perform(get("/dashboard").session(signIn(EMPLOYEE, DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(redirectedUrl("/employee/dashboard"));
        mvc.perform(get("/dashboard").session(signIn(TECHNICIAN, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(redirectedUrl("/technician/dashboard"));
        mvc.perform(get("/dashboard").session(signIn(ADMIN, DataSeeder.ADMIN_PASSWORD)))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void employeeSeesTheEmployeeDashboardAndNavbar() throws Exception {
        mvc.perform(get("/employee/dashboard").session(signIn(EMPLOYEE, DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, <span>Priya Sharma</span>")))
                .andExpect(content().string(containsString("navbar-employee")))
                .andExpect(content().string(containsString("href=\"/employee/incidents\"")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(not(containsString("href=\"/technician/incidents\""))));
    }

    @Test
    void technicianSeesTheTechnicianDashboardAndNavbar() throws Exception {
        mvc.perform(get("/technician/dashboard").session(signIn(TECHNICIAN, DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, <span>Ravi Kumar</span>")))
                .andExpect(content().string(containsString("navbar-technician")))
                .andExpect(content().string(containsString("href=\"/technician/incidents\"")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(not(containsString("href=\"/employee/incidents\""))));
    }

    @Test
    void adminSeesTheAdminDashboardAndNavbar() throws Exception {
        mvc.perform(get("/admin/dashboard").session(signIn(ADMIN, DataSeeder.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("navbar-admin")))
                .andExpect(content().string(containsString("href=\"/admin/users\"")))
                .andExpect(content().string(not(containsString("href=\"/employee/incidents\""))))
                .andExpect(content().string(not(containsString("href=\"/technician/incidents\""))));
    }

    @Test
    void everyAdminPageOpensForTheAdmin() throws Exception {
        MockHttpSession admin = signIn(ADMIN, DataSeeder.ADMIN_PASSWORD);

        for (String page : new String[] {"/admin/incidents", "/admin/users", "/admin/services",
                "/admin/categories", "/admin/sla-policies"}) {
            mvc.perform(get(page).session(admin)).andExpect(status().isOk());
        }
    }

    // ---- signing out ----

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession employee = signIn(EMPLOYEE, DataSeeder.EMPLOYEE_PASSWORD);

        mvc.perform(post("/logout").session(employee).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
        mvc.perform(get("/employee/dashboard").session(employee))
                .andExpect(redirectedUrl("/login"));
    }

    /** Signs in through the real login form and returns the session that proves it. */
    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }
}
