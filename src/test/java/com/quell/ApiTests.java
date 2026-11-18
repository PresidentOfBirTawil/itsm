package com.quell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import com.quell.api.ApiRateLimiter;
import com.quell.api.ApiTokenService;
import com.quell.domain.ApiToken;
import com.quell.domain.Employee;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.Urgency;
import com.quell.domain.User;
import com.quell.repository.ApiTokenRepository;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.IncidentRepository;
import com.quell.repository.UserRepository;
import com.quell.seed.DataSeeder;
import com.quell.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** The REST API: keys, permissions, errors, limits and documentation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApiTokenService tokenService;
    @Autowired
    private ApiTokenRepository tokens;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private UserRepository users;

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private String keyFor(String email) {
        return "Bearer " + tokenService.create(user(email), "Tests", 30).key();
    }

    private Long serviceId(String name) {
        return services.findByName(name).orElseThrow().getId();
    }

    private Incident reportAs(String email, String title) {
        return incidentService.report((Employee) user(email), title, "Details.", serviceId("Workplace Devices"),
                categories.findByNameIgnoreCase("Hardware").orElseThrow().getId(), Impact.LOW, Urgency.MEDIUM);
    }

    private static String newIncident(String title, String extra) {
        return """
                {"title": "%s", "description": "Since 09:40 one in five payments fails: the payment page shows error 502.",
                 "serviceId": %%d, "impact": "HIGH", "urgency": "HIGH"%s}""".formatted(title, extra);
    }

    // ---- signing in with a key ----

    @Test
    void withoutAKeyTheApiAnswers401AsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString("Authorization: Bearer")));
    }

    @Test
    void wrongRevokedAndExpiredKeysAreRefused() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer qk_" + "x".repeat(40)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid API key"));

        ApiTokenService.CreatedToken created = tokenService.create(user("emp1@quell.local"), "Old script", null);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + created.key())).andExpect(status().isOk());
        tokenService.revoke(created.token().getId(), user("emp1@quell.local"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + created.key())).andExpect(status().isUnauthorized());

        ApiToken expired = tokenService.create(user("emp1@quell.local"), "Short", 1).token();
        org.springframework.test.util.ReflectionTestUtils.setField(expired, "expiresAt", java.time.LocalDateTime.now().minusMinutes(1));
        assertThat(expired.isUsable(java.time.LocalDateTime.now())).isFalse();
    }

    @Test
    void keysAreStoredOnlyAsAHashAndShownOnce() throws Exception {
        ApiTokenService.CreatedToken created = tokenService.create(user("emp1@quell.local"), "Monitoring", 90);
        ApiToken stored = tokens.findById(created.token().getId()).orElseThrow();

        assertThat(created.key()).startsWith("qk_").hasSize(43);
        assertThat(stored.getTokenHash()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(created.key().getBytes(StandardCharsets.UTF_8))));
        assertThat(stored.getTokenHash()).doesNotContain(created.key().substring(3));
        assertThat(stored.getPrefix()).isEqualTo(created.key().substring(0, 7));
    }

    @Test
    void theApiKeepsNoSessionAndNeedsNoCsrfToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("emp1@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newIncident("Checkout returns 502", "").formatted(serviceId("Payment Application"))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // ---- incidents ----

    @Test
    void anEmployeeReportsAnIncidentAndItIsPrioritisedAndAssignedAsUsual() throws Exception {
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("emp1@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newIncident("Checkout returns 502", "").formatted(serviceId("Payment Application"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/incidents/INC-")))
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.reporter.email").value("emp1@quell.local"))
                .andExpect(jsonPath("$.assignee").isNotEmpty())
                .andExpect(jsonPath("$.links.web").value(startsWith("/employee/incidents/INC-")));
    }

    @Test
    void anEmployeeKeySeesOnlyThatEmployeesIncidents() throws Exception {
        Incident mine = reportAs("emp1@quell.local", "My keyboard is broken");
        Incident theirs = reportAs("emp2@quell.local", "Their mouse is broken");
        String key = keyFor("emp1@quell.local");

        mvc.perform(get("/api/v1/incidents").header("Authorization", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code").value(hasItem(mine.getCode())))
                .andExpect(jsonPath("$.items[*].reporter.email").value(everyItem(org.hamcrest.Matchers.is("emp1@quell.local"))));
        mvc.perform(get("/api/v1/incidents/" + theirs.getCode()).header("Authorization", key))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/incidents/INC-9999").header("Authorization", key))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsCanBeFilteredSearchedAndPaged() throws Exception {
        reportAs("emp1@quell.local", "Projector in room 4 flickers");
        mvc.perform(get("/api/v1/incidents").param("q", "projector flickers").param("size", "5")
                        .header("Authorization", keyFor("admin@quell.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Projector in room 4 flickers"))
                .andExpect(jsonPath("$.size").value(5));
        mvc.perform(get("/api/v1/incidents").param("size", "500").header("Authorization", keyFor("admin@quell.local")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/incidents").param("status", "NOT_A_STATUS").header("Authorization", keyFor("admin@quell.local")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsAreListedOneByOne() throws Exception {
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("emp1@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("serviceId")));
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("emp1@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void anAdministratorKeyMayReportOnBehalfOfAnEmployeeButAnEmployeeKeyMayNot() throws Exception {
        String body = newIncident("Disk almost full on the file server", ", \"reporterEmail\": \"emp3@quell.local\"")
                .formatted(serviceId("Cloud Storage"));
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("admin@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporter.email").value("emp3@quell.local"));
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("emp1@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/incidents").header("Authorization", keyFor("admin@quell.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newIncident("No reporter", "").formatted(serviceId("Cloud Storage"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTechnicianWorksOnlyOnTheirOwnIncidentsAndIllegalMovesAreConflicts() throws Exception {
        Incident incident = reportAs("emp1@quell.local", "Docking station dead");
        Incident fresh = incidents.findByCode(incident.getCode()).orElseThrow();
        String assigneeEmail = fresh.getAssignee().getEmail();
        String other = assigneeEmail.equals("tech4@quell.local") ? "tech2@quell.local" : "tech4@quell.local";

        mvc.perform(post("/api/v1/incidents/" + incident.getCode() + "/status").header("Authorization", keyFor(assigneeEmail))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mvc.perform(post("/api/v1/incidents/" + incident.getCode() + "/status").header("Authorization", keyFor(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/incidents/" + incident.getCode() + "/status").header("Authorization", keyFor(assigneeEmail))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/incidents/" + incident.getCode() + "/notes").header("Authorization", keyFor(assigneeEmail))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Ordered a new dock.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/incidents/" + incident.getCode() + "/timeline").header("Authorization", keyFor("emp1@quell.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].note").value(hasItem("Ordered a new dock.")));
    }

    // ---- the rest ----

    @Test
    void statusServicesCategoriesAndKnowledge() throws Exception {
        String key = keyFor("emp2@quell.local");
        mvc.perform(get("/api/v1/me").header("Authorization", key))
                .andExpect(jsonPath("$.email").value("emp2@quell.local"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
        mvc.perform(get("/api/v1/status").header("Authorization", key))
                .andExpect(jsonPath("$.headline").value("All systems operational"))
                .andExpect(jsonPath("$.services.length()").value(7));
        mvc.perform(get("/api/v1/services").header("Authorization", key))
                .andExpect(jsonPath("$[?(@.name == 'Payment Application')].dependsOn[*]").value(hasItem("Database Cluster")));
        mvc.perform(get("/api/v1/categories").header("Authorization", key))
                .andExpect(jsonPath("$.length()").value(9));
        mvc.perform(get("/api/v1/knowledge").param("q", "vpn").header("Authorization", key))
                .andExpect(jsonPath("$[0].reference").value(startsWith("KB-")));
    }

    @Test
    void theDocumentationIsPublic() throws Exception {
        mvc.perform(get("/api/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Quell API"))
                .andExpect(jsonPath("$.paths['/api/v1/incidents']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.scheme").value("bearer"));
        mvc.perform(get("/api/docs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/openapi.json/swagger-config")).andExpect(status().isOk());
    }

    @Test
    void eachKeyHasARequestLimitPerMinute() {
        MutableClock clock = new MutableClock();
        ApiRateLimiter limiter = new ApiRateLimiter(3, clock);
        assertThat(limiter.take(1L).allowed()).isTrue();
        assertThat(limiter.take(1L).allowed()).isTrue();
        ApiRateLimiter.Decision third = limiter.take(1L);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isZero();
        assertThat(limiter.take(1L).allowed()).isFalse();
        assertThat(limiter.take(2L).allowed()).isTrue();          // another key has its own count
        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.take(1L).allowed()).isTrue();          // a new minute
    }

    @Test
    void everyAnswerSaysHowManyRequestsAreLeft() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", keyFor("emp1@quell.local")))
                .andExpect(header().string("X-RateLimit-Limit", "120"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    // ---- the web page for keys ----

    @Test
    void keysAreMadeAndRevokedOnTheAccountPage() throws Exception {
        MockHttpSession session = signIn("emp1@quell.local", DataSeeder.EMPLOYEE_PASSWORD);
        MvcResult made = mvc.perform(post("/account/api-keys").session(session).with(csrf())
                        .param("name", "Grafana").param("days", "90"))
                .andExpect(redirectedUrl("/account/api-keys"))
                .andExpect(flash().attribute("newKeyName", "Grafana"))
                .andReturn();
        String key = (String) made.getFlashMap().get("newKey");
        assertThat(key).startsWith("qk_");

        ApiToken token = tokens.findByTokenHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))).orElseThrow();
        // Shown once, right after it was made ...
        mvc.perform(get("/account/api-keys").session(session))
                .andExpect(content().string(containsString(key)));
        // ... and never again: only its first characters identify it.
        mvc.perform(get("/account/api-keys").session(session))
                .andExpect(content().string(containsString("Grafana")))
                .andExpect(content().string(containsString(token.getPrefix())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(key))));
        mvc.perform(post("/account/api-keys/" + token.getId() + "/revoke").session(session).with(csrf()))
                .andExpect(redirectedUrl("/account/api-keys"));
        assertThat(token.isRevoked()).isTrue();

        // Someone else's key cannot be revoked by an employee.
        ApiToken others = tokenService.create(user("emp2@quell.local"), "Theirs", 30).token();
        mvc.perform(post("/account/api-keys/" + others.getId() + "/revoke").session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:05Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
