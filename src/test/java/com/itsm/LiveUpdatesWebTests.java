package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.itsm.domain.Notification;
import com.itsm.live.LiveUpdates;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.NotificationRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Live updates through the running application: a signed-in user opens the
 * stream at /live, something happens, and the right message arrives on it.
 *
 * <p>The messages go out only after a change has really been committed, so
 * these tests are not rolled back. The application and its in-memory
 * database are thrown away after this class.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiveUpdatesWebTests {

    private static final Duration SOON = Duration.ofSeconds(5);
    private static int reports;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LiveUpdates liveUpdates;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserRepository users;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    /** An open stream: what one browser tab has been sent so far. */
    private record Stream(MockHttpServletResponse response) {

        String text() throws Exception {
            return response.getContentAsString();
        }

        /** The data of every message of one kind, e.g. "incident". */
        List<String> messages(String name) throws Exception {
            return Pattern.compile("event:" + name + "\\n(?:retry:\\d+\\n)?data:(.*)\\n").matcher(text())
                    .results().map(match -> match.group(1)).toList();
        }
    }

    private Stream listen(MockHttpSession session) throws Exception {
        return new Stream(mvc.perform(get("/live").session(session))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse());
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }

    private MockHttpSession admin() throws Exception {
        return signIn("admin@itsm.local", DataSeeder.ADMIN_PASSWORD);
    }

    private MockHttpSession employee(int number) throws Exception {
        return signIn("emp" + number + "@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
    }

    private MockHttpSession technician(int number) throws Exception {
        return signIn("tech" + number + "@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
    }

    /** Reports a P1 payment incident through the form (assigned to Sara Thomas, tech4) and returns its code. */
    private String reportPaymentIncident(MockHttpSession employee) throws Exception {
        String location = mvc.perform(post("/employee/incidents").session(employee).with(csrf())
                        .param("title", "Payment problem number " + ++reports)
                        .param("description", "Users are unable to complete transactions.")
                        .param("serviceId", services.findByName("Payment Application").orElseThrow().getId().toString())
                        .param("categoryId", categories.findByNameIgnoreCase("Application").orElseThrow().getId().toString())
                        .param("impact", "HIGH").param("urgency", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @AfterEach
    void closeTheStreams() {
        liveUpdates.onShutdown();
    }

    // ---- opening the stream ----

    @Test
    void theStreamIsOnlyForSignedInUsers() throws Exception {
        mvc.perform(get("/live"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertThat(liveUpdates.connectionCount()).isZero();
    }

    @Test
    void aSignedInUserGetsAnEventStreamThatStaysOpen() throws Exception {
        mvc.perform(get("/live").session(employee(1)))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andExpect(content().string(containsString("event:connected")));

        assertThat(liveUpdates.connectionCount()).isEqualTo(1);
    }

    @Test
    void anOpenStreamKeepsNoDatabaseConnectionForItself() throws Exception {
        listen(admin());
        listen(technician(1));
        listen(employee(1));

        // Three streams are open. If each held a connection, a few dozen open tabs would use up the pool.
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        await().atMost(SOON).untilAsserted(() ->
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero());
        assertThat(liveUpdates.connectionCount()).isEqualTo(3);
    }

    // ---- incidents ----

    @Test
    void reportingAnIncidentReachesTheDashboardsOnceAndNotOtherEmployees() throws Exception {
        MockHttpSession priya = employee(1);
        Stream adminTab = listen(admin());
        Stream technicianTab = listen(technician(1));           // not the technician who gets it
        Stream reporterTab = listen(priya);
        Stream otherEmployeeTab = listen(employee(2));

        String code = reportPaymentIncident(priya);
        String message = "{\"code\":\"" + code + "\"}";

        // Reported, prioritised and assigned are three events, but one message per incident.
        await().atMost(SOON).untilAsserted(() -> {
            assertThat(adminTab.messages("incident")).containsExactly(message);
            assertThat(technicianTab.messages("incident")).containsExactly(message);
            assertThat(reporterTab.messages("incident")).containsExactly(message);
        });
        assertThat(otherEmployeeTab.messages("incident")).isEmpty();
        assertThat(otherEmployeeTab.text()).doesNotContain(code);
    }

    @Test
    void aStatusChangeReachesTheOpenPagesToo() throws Exception {
        MockHttpSession sara = technician(4);
        String code = reportPaymentIncident(employee(1));
        Stream adminTab = listen(admin());

        mvc.perform(post("/technician/incidents/" + code + "/transition").session(sara).with(csrf())
                        .param("target", "ACKNOWLEDGED"))
                .andExpect(status().is3xxRedirection());

        await().atMost(SOON).untilAsserted(() ->
                assertThat(adminTab.messages("incident")).containsExactly("{\"code\":\"" + code + "\"}"));
    }

    @Test
    void aRefusedChangeIsNotAnnounced() throws Exception {
        String code = reportPaymentIncident(employee(1));
        Stream adminTab = listen(admin());

        mvc.perform(post("/technician/incidents/" + code + "/transition").session(technician(4)).with(csrf())
                        .param("target", "CLOSED").header("Referer", "http://localhost/technician/incidents/" + code))
                .andExpect(status().is3xxRedirection());             // back to the page with an error message
        liveUpdates.everythingChanged();                             // a marker: whatever was sent came before this

        await().atMost(SOON).untilAsserted(() -> assertThat(adminTab.messages("refresh")).hasSize(1));
        assertThat(adminTab.messages("incident")).isEmpty();
    }

    // ---- the bell ----

    @Test
    void theBellOfTheAssignedTechnicianGoesUpWithoutARefresh() throws Exception {
        Long saraId = users.findByEmail("tech4@itsm.local").orElseThrow().getId();
        long before = notifications.countByRecipientIdAndReadAtIsNull(saraId);
        Stream saraTab = listen(technician(4));
        Stream raviTab = listen(technician(1));

        reportPaymentIncident(employee(1));                          // assigned to Sara: she is notified

        await().atMost(SOON).untilAsserted(() ->
                assertThat(saraTab.messages("bell")).contains("{\"unread\":" + (before + 1) + "}"));
        assertThat(raviTab.messages("bell")).isEmpty();              // nobody else's bell moves
    }

    @Test
    void readingANotificationLowersTheBellInTheUsersOtherTabs() throws Exception {
        MockHttpSession sara = technician(4);
        Long saraId = users.findByEmail("tech4@itsm.local").orElseThrow().getId();
        reportPaymentIncident(employee(1));
        await().atMost(SOON).until(() -> notifications.countByRecipientIdAndReadAtIsNull(saraId) > 0);
        List<Notification> unread = notifications.findByRecipientIdAndReadAtIsNull(saraId);
        Stream otherTab = listen(sara);

        mvc.perform(get("/notifications/" + unread.get(0).getId()).session(sara))
                .andExpect(status().is3xxRedirection());
        await().atMost(SOON).untilAsserted(() ->
                assertThat(otherTab.messages("bell")).containsExactly("{\"unread\":" + (unread.size() - 1) + "}"));

        mvc.perform(post("/notifications/read-all").session(sara).with(csrf()))
                .andExpect(status().is3xxRedirection());
        await().atMost(SOON).untilAsserted(() ->
                assertThat(otherTab.messages("bell")).last().isEqualTo("{\"unread\":0}"));
    }

    // ---- everything at once, and connections that end ----

    @Test
    void resettingTheDemoDataRefreshesEveryOpenPage() throws Exception {
        MockHttpSession admin = admin();
        Stream adminTab = listen(admin);
        Stream employeeTab = listen(employee(3));

        mvc.perform(post("/admin/settings/reset-demo-data").session(admin).with(csrf()).param("samples", "false"))
                .andExpect(status().is3xxRedirection());

        await().atMost(SOON).untilAsserted(() -> {
            assertThat(adminTab.messages("refresh")).hasSize(1);
            assertThat(employeeTab.messages("refresh")).hasSize(1);
        });
    }

    @Test
    void disablingAnAccountClosesItsStream() throws Exception {
        MockHttpSession admin = admin();
        Long karanId = users.findByEmail("emp4@itsm.local").orElseThrow().getId();
        Stream karanTab = listen(employee(4));
        Stream adminTab = listen(admin);
        assertThat(liveUpdates.connectionCount()).isEqualTo(2);

        mvc.perform(post("/admin/users/" + karanId + "/enabled").session(admin).with(csrf()).param("enabled", "false"));
        liveUpdates.everythingChanged();

        await().atMost(SOON).untilAsserted(() -> assertThat(adminTab.messages("refresh")).hasSize(1));
        assertThat(karanTab.messages("refresh")).isEmpty();
        assertThat(liveUpdates.connectionCount()).isEqualTo(1);

        mvc.perform(post("/admin/users/" + karanId + "/enabled").session(admin).with(csrf()).param("enabled", "true"));
    }

    @Test
    void signingOutClosesTheStreamOfThatSession() throws Exception {
        MockHttpSession priya = employee(1);
        listen(priya);
        assertThat(liveUpdates.connectionCount()).isEqualTo(1);

        mvc.perform(post("/logout").session(priya).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(priya.isInvalid()).isTrue();
        // A real server announces the end of a session itself. The test server does not, so the test does it.
        events.publishEvent(new HttpSessionDestroyedEvent(priya));

        assertThat(liveUpdates.connectionCount()).isZero();
    }

    // ---- the pages ----

    @Test
    void thePagesCarryTheScriptAndMarkTheirLiveParts() throws Exception {
        MockHttpSession admin = admin();
        MockHttpSession priya = employee(1);
        String code = reportPaymentIncident(priya);

        mvc.perform(get("/admin/dashboard").session(admin))
                .andExpect(content().string(containsString("<script src=\"/js/live.js\"")))
                .andExpect(content().string(containsString("data-live=\"page\"")))
                .andExpect(content().string(containsString("data-live=\"nav-waiting\"")))
                .andExpect(content().string(containsString("navbar-bell-count")));
        mvc.perform(get("/technician/dashboard").session(technician(4)))
                .andExpect(content().string(containsString("data-live=\"page\"")));
        mvc.perform(get("/technician/incidents").session(technician(4)))
                .andExpect(content().string(containsString("data-live=\"page\"")));
        mvc.perform(get("/employee/dashboard").session(priya))
                .andExpect(content().string(containsString("data-live=\"page\"")));
        mvc.perform(get("/admin/incidents").session(admin))
                .andExpect(content().string(containsString("data-live=\"incident-list\"")));
        mvc.perform(get("/notifications").session(priya))
                .andExpect(content().string(containsString("data-live=\"notifications\"")));
        // The incident page has forms: it is not replaced, it shows a notice instead.
        mvc.perform(get("/employee/incidents/" + code).session(priya))
                .andExpect(content().string(containsString("data-live-incident=\"" + code + "\"")))
                .andExpect(content().string(containsString("This incident has just been changed.")))
                .andExpect(content().string(not(containsString("data-live=\"page\""))));
    }

    @Test
    void theSignInPageHasNoLiveScriptButTheScriptItselfIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script src=\"/js/live.js\""))));
        mvc.perform(get("/js/live.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("new EventSource('/live')")));
    }
}
