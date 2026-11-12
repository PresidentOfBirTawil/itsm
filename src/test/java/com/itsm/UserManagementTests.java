package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.itsm.domain.Administrator;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** "Manage users": the administrator adds employees and administrators and disables or enables accounts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserManagementTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private MockHttpSession admin;

    @BeforeEach
    void signInAsAdministrator() throws Exception {
        admin = signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    // ---- adding ----

    @Test
    void theUsersPageListsEveryoneByRole() throws Exception {
        mvc.perform(get("/admin/users").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin@itsm.local")))
                .andExpect(content().string(containsString("tech1@itsm.local")))
                .andExpect(content().string(containsString("emp4@itsm.local")))
                .andExpect(content().string(containsString("Add user")));
    }

    @Test
    void theAdministratorAddsAnEmployeeWhoCanThenSignIn() throws Exception {
        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("role", "EMPLOYEE").param("name", "Dana Whitfield")
                        .param("email", "Dana@Itsm.Local").param("password", "dana12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("success", "Employee Dana Whitfield has been added."));

        User dana = user("dana@itsm.local");                                   // stored in lower case
        assertThat(dana).isInstanceOf(Employee.class);
        assertThat(dana.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(dana.isEnabled()).isTrue();
        assertThat(dana.getPasswordHash()).isNotEqualTo("dana12345");          // a BCrypt hash, not the password
        assertThat(passwordEncoder.matches("dana12345", dana.getPasswordHash())).isTrue();
        mvc.perform(formLogin("/login").userParameter("email").user("dana@itsm.local").password("dana12345"))
                .andExpect(authenticated().withRoles("EMPLOYEE"));
    }

    @Test
    void theAdministratorAddsAnotherAdministrator() throws Exception {
        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("role", "ADMIN").param("name", "Omar Haddad")
                        .param("email", "omar@itsm.local").param("password", "omar12345"))
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(user("omar@itsm.local")).isInstanceOf(Administrator.class);
        mvc.perform(formLogin("/login").userParameter("email").user("omar@itsm.local").password("omar12345"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void anEmailAddressThatIsAlreadyInUseIsRefused() throws Exception {
        long before = users.count();

        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("role", "EMPLOYEE").param("name", "Second Priya")
                        .param("email", "EMP1@itsm.local").param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().attributeHasFieldErrors("form", "email"))
                .andExpect(content().string(containsString("already exists")));
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void aShortPasswordABadEmailAndAnEmptyNameAreRefused() throws Exception {
        long before = users.count();

        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("role", "EMPLOYEE").param("name", " ")
                        .param("email", "not-an-email").param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "email", "password"));
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void techniciansAreNotAddedThroughThisForm() throws Exception {
        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("role", "TECHNICIAN").param("name", "Tara Tech")
                        .param("email", "tara@itsm.local").param("password", "tara12345"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "role"));
        assertThat(users.existsByEmail("tara@itsm.local")).isFalse();
    }

    // ---- disabling ----

    @Test
    void aDisabledAccountCannotSignInAndAnEnabledOneCanAgain() throws Exception {
        User karan = user("emp4@itsm.local");

        mvc.perform(post("/admin/users/" + karan.getId() + "/enabled").session(admin).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("success", containsString("can no longer sign in")));
        assertThat(karan.isEnabled()).isFalse();
        mvc.perform(formLogin("/login").userParameter("email").user("emp4@itsm.local")
                        .password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(post("/admin/users/" + karan.getId() + "/enabled").session(admin).with(csrf())
                        .param("enabled", "true"))
                .andExpect(flash().attribute("success", containsString("can sign in again")));
        mvc.perform(formLogin("/login").userParameter("email").user("emp4@itsm.local")
                        .password(DataSeeder.EMPLOYEE_PASSWORD))
                .andExpect(authenticated());
    }

    @Test
    void disablingAnAccountEndsItsOpenSessionAtOnce() throws Exception {
        MockHttpSession karan = signIn("emp4@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        mvc.perform(get("/employee/dashboard").session(karan)).andExpect(status().isOk());

        mvc.perform(post("/admin/users/" + user("emp4@itsm.local").getId() + "/enabled").session(admin)
                .with(csrf()).param("enabled", "false"));

        mvc.perform(get("/employee/dashboard").session(karan))      // the same browser, a moment later
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/admin/users").session(admin)).andExpect(status().isOk()); // the administrator is unaffected
    }

    @Test
    void anAdministratorCannotDisableTheirOwnAccount() throws Exception {
        User neha = user("admin@itsm.local");

        mvc.perform(post("/admin/users/" + neha.getId() + "/enabled").session(admin).with(csrf())
                        .param("enabled", "false").header("Referer", "http://localhost/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "You cannot disable your own account."));
        assertThat(neha.isEnabled()).isTrue();
        mvc.perform(get("/admin/users").session(admin)).andExpect(status().isOk());
    }

    @Test
    void aUserNumberThatDoesNotExistGivesTheNotFoundPage() throws Exception {
        mvc.perform(post("/admin/users/987654/enabled").session(admin).with(csrf()).param("enabled", "false"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDisabledTechnicianIsNotGivenNewIncidents() throws Exception {
        User sara = user("tech4@itsm.local");                        // the only technician with the Application skill
        mvc.perform(post("/admin/users/" + sara.getId() + "/enabled").session(admin).with(csrf())
                .param("enabled", "false"));

        Incident incident = incidentService.report((Employee) user("emp1@itsm.local"),
                "Payment application is unavailable", "Details.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);

        assertThat(incident.getAssignee()).isNotEqualTo(sara);
        if (incident.getAssignee() == null) {
            assertThat(incident.getStatus()).isEqualTo(Status.CLASSIFIED); // waits for an administrator
        }
    }

    // ---- who may manage users ----

    @Test
    void onlyAdministratorsCanManageUsers() throws Exception {
        MockHttpSession employee = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        MockHttpSession technician = signIn("tech1@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        Long victim = user("emp2@itsm.local").getId();

        mvc.perform(get("/admin/users").session(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users/new").session(technician)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users").session(employee).with(csrf())
                        .param("role", "ADMIN").param("name", "Sneaky").param("email", "sneaky@itsm.local")
                        .param("password", "sneaky123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + victim + "/enabled").session(technician).with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertThat(users.existsByEmail("sneaky@itsm.local")).isFalse();
        assertThat(user("emp2@itsm.local").isEnabled()).isTrue();
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
