package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itsm.domain.Employee;
import com.itsm.domain.Role;
import com.itsm.domain.User;
import com.itsm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Employee self-registration. Each test runs in a transaction that is rolled back afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationCreatesAnEmployeeWithAHashedPasswordWhoCanSignIn() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Nina Newcomer")
                        .param("email", "Nina@Example.com")
                        .param("password", "s3cret-pass")
                        .param("confirmPassword", "s3cret-pass"))
                .andExpect(redirectedUrl("/login?registered"));

        User created = users.findByEmail("nina@example.com").orElseThrow();
        assertThat(created).isInstanceOf(Employee.class);
        assertThat(created.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(created.getName()).isEqualTo("Nina Newcomer");
        assertThat(created.getPasswordHash()).isNotEqualTo("s3cret-pass").startsWith("$2");
        assertThat(passwordEncoder.matches("s3cret-pass", created.getPasswordHash())).isTrue();

        mvc.perform(formLogin("/login").userParameter("email").user("nina@example.com").password("s3cret-pass"))
                .andExpect(authenticated().withRoles("EMPLOYEE"));
    }

    @Test
    void emailThatAlreadyHasAnAccountIsRejected() throws Exception {
        long before = users.count();

        mvc.perform(post("/register").with(csrf())
                        .param("name", "Someone Else")
                        .param("email", "emp1@itsm.local")
                        .param("password", "s3cret-pass")
                        .param("confirmPassword", "s3cret-pass"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "email"));

        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        long before = users.count();

        mvc.perform(post("/register").with(csrf())
                        .param("name", " ")
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name", "email", "password"));

        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void passwordsThatDoNotMatchAreRejected() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Nina Newcomer")
                        .param("email", "nina@example.com")
                        .param("password", "s3cret-pass")
                        .param("confirmPassword", "different-pass"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));

        assertThat(users.findByEmail("nina@example.com")).isEmpty();
    }

    @Test
    void registrationWithoutACsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/register")
                        .param("name", "Nina Newcomer")
                        .param("email", "nina@example.com")
                        .param("password", "s3cret-pass")
                        .param("confirmPassword", "s3cret-pass"))
                .andExpect(status().isForbidden());
    }
}
