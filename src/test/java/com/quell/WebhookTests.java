package com.quell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.quell.domain.Employee;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.Urgency;
import com.quell.domain.Webhook;
import com.quell.domain.WebhookDelivery;
import com.quell.error.InvalidInputException;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.UserRepository;
import com.quell.repository.WebhookDeliveryRepository;
import com.quell.seed.DataSeeder;
import com.quell.service.IncidentService;
import com.quell.webhook.WebhookDispatcher;
import com.quell.webhook.WebhookEvent;
import com.quell.webhook.WebhookKind;
import com.quell.webhook.WebhookPayloads;
import com.quell.webhook.WebhookSender;
import com.quell.webhook.WebhookService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Webhooks: what is queued when, how it is signed and shaped, retries, and the admin pages. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebhookTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One request the test receiver got. */
    record Received(String body, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WebhookService webhookService;
    @Autowired
    private WebhookDispatcher dispatcher;
    @Autowired
    private WebhookDeliveryRepository deliveries;
    @Autowired
    private WebhookPayloads payloads;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private UserRepository users;

    private HttpServer receiver;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile int answer = 200;

    @BeforeEach
    void startReceiver() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hook", exchange -> {
            received.add(new Received(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    Map.copyOf(exchange.getRequestHeaders())));
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop(0);
    }

    private String hookUrl() {
        return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
    }

    private int reported;

    private Incident report(Impact impact, Urgency urgency) {
        return incidentService.report((Employee) users.findByEmail("emp1@quell.local").orElseThrow(),
                "Card payments fail at checkout " + (++reported), "The payment page shows an error.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), impact, urgency);
    }

    @Test
    void anEventIsQueuedWithTheChangeAndDeliveredSigned() throws Exception {
        Webhook webhook = webhookService.create("Pager", WebhookKind.GENERIC, hookUrl(), EnumSet.of(WebhookEvent.REPORTED), false);
        Incident incident = report(Impact.HIGH, Urgency.HIGH);

        List<WebhookDelivery> queued = webhookService.log(webhook.getId());
        assertThat(queued).singleElement().satisfies(delivery -> {
            assertThat(delivery.getEvent()).isEqualTo(WebhookEvent.REPORTED);
            assertThat(delivery.getIncidentCode()).isEqualTo(incident.getCode());
            assertThat(delivery.getState()).isEqualTo(WebhookDelivery.State.PENDING);
        });

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.header("X-Quell-Event")).isEqualTo("incident.reported");
            String timestamp = request.header("X-Quell-Timestamp");
            assertThat(request.header("X-Quell-Signature"))
                    .isEqualTo("sha256=" + WebhookSender.sign(webhook.getSecret(), timestamp + "." + request.body()));
            JsonNode body = JSON.readTree(request.body());
            assertThat(body.get("event").asString()).isEqualTo("incident.reported");
            assertThat(body.get("incident").get("code").asString()).isEqualTo(incident.getCode());
            assertThat(body.get("incident").get("priority").asString()).isEqualTo("P1");
            assertThat(body.get("incident").get("assignee").asString()).isNotBlank();
            assertThat(body.get("incident").get("url").asString()).endsWith("/incidents/" + incident.getCode());
        });
        assertThat(deliveries.findById(queued.getFirst().getId()).orElseThrow().getState())
                .isEqualTo(WebhookDelivery.State.DELIVERED);
    }

    @Test
    void onlyTheChosenEventsAndPrioritiesAreSent() {
        Webhook major = webhookService.create("Majors", WebhookKind.SLACK, hookUrl(), EnumSet.of(WebhookEvent.REPORTED), true);
        Webhook resolved = webhookService.create("Fixes", WebhookKind.SLACK, hookUrl(), EnumSet.of(WebhookEvent.RESOLVED), false);

        report(Impact.LOW, Urgency.LOW);                 // P4

        assertThat(webhookService.log(major.getId())).isEmpty();
        assertThat(webhookService.log(resolved.getId())).isEmpty();
        report(Impact.HIGH, Urgency.HIGH);               // P1
        assertThat(webhookService.log(major.getId())).hasSize(1);
    }

    @Test
    void slackAndTeamsGetMessagesInTheirOwnFormat() throws Exception {
        WebhookPayloads.Facts facts = payloads.testFacts();

        JsonNode slack = JSON.readTree(payloads.build(WebhookKind.SLACK, WebhookEvent.REPORTED, facts));
        assertThat(slack.get("text").asString()).contains("INC-TEST");
        assertThat(slack.get("blocks").get(0).get("type").asString()).isEqualTo("section");

        JsonNode teams = JSON.readTree(payloads.build(WebhookKind.TEAMS, WebhookEvent.REPORTED, facts));
        JsonNode attachment = teams.get("attachments").get(0);
        assertThat(attachment.get("contentType").asString()).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachment.get("content").get("type").asString()).isEqualTo("AdaptiveCard");
        assertThat(attachment.get("content").get("actions").get(0).get("type").asString()).isEqualTo("Action.OpenUrl");
    }

    @Test
    void aFailedDeliveryIsRetriedWithGrowingPausesAndThenGivenUp() {
        answer = 500;
        Webhook webhook = webhookService.create("Flaky", WebhookKind.GENERIC, hookUrl(), EnumSet.of(WebhookEvent.REPORTED), false);
        report(Impact.HIGH, Urgency.HIGH);
        dispatcher.dispatchDue();

        WebhookDelivery delivery = webhookService.log(webhook.getId()).getFirst();
        assertThat(delivery.getState()).isEqualTo(WebhookDelivery.State.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getResponseStatus()).isEqualTo(500);
        assertThat(delivery.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(20));
        assertThat(dispatcher.dispatchDue()).isZero();    // not due yet

        for (int i = 0; i < WebhookDelivery.RETRY_PAUSES.size(); i++) {
            delivery.failed(500, "still down", LocalDateTime.now());
        }
        assertThat(delivery.getState()).isEqualTo(WebhookDelivery.State.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(WebhookDelivery.RETRY_PAUSES.size() + 1);
    }

    @Test
    void anUnreachableReceiverIsReportedNotThrown() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        WebhookSender.Result result = new WebhookSender(new com.quell.config.Brand("Quell", "", "1", true, "", ""))
                .send("http://127.0.0.1:" + closedPort + "/nobody", "{}", "secret", "webhook.test", 1L);
        assertThat(result.ok()).isFalse();
        assertThat(result.status()).isNull();
        assertThat(result.error()).startsWith("Could not reach the receiver");
    }

    @Test
    void addressesAndChoicesAreChecked() {
        assertThatThrownBy(() -> webhookService.create("x", WebhookKind.GENERIC, "ftp://example.com/x", EnumSet.of(WebhookEvent.REPORTED), false))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> webhookService.create("x", WebhookKind.GENERIC, "javascript:alert(1)", EnumSet.of(WebhookEvent.REPORTED), false))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> webhookService.create("x", WebhookKind.GENERIC, "https://user:pw@example.com/x", EnumSet.of(WebhookEvent.REPORTED), false))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> webhookService.create("x", WebhookKind.GENERIC, "https://example.com/x", EnumSet.noneOf(WebhookEvent.class), false))
                .isInstanceOf(InvalidInputException.class);
        Webhook slack = webhookService.create("Ops", WebhookKind.SLACK, "https://hooks.slack.com/services/T000/B000/abcdefgh1234",
                EnumSet.of(WebhookEvent.REPORTED), false);
        assertThat(slack.getMaskedUrl()).isEqualTo("https://hooks.slack.com/…1234");
        assertThat(slack.getSecret()).startsWith("whsec_").hasSize(54);
    }

    @Test
    void administratorsManageWebhooksAndCanSendATest() throws Exception {
        MockHttpSession admin = signIn("admin@quell.local", DataSeeder.ADMIN_PASSWORD);
        mvc.perform(get("/admin/webhooks").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/webhooks").session(signIn("emp1@quell.local", DataSeeder.EMPLOYEE_PASSWORD)))
                .andExpect(status().isForbidden());

        MvcResult created = mvc.perform(post("/admin/webhooks").session(admin).with(csrf())
                        .param("name", "Ops channel").param("kind", "TEAMS").param("url", hookUrl())
                        .param("events", "REPORTED", "ESCALATED"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String editPage = created.getResponse().getRedirectedUrl();
        Long id = Long.valueOf(editPage.replaceAll("\\D+", " ").strip().split(" ")[0]);

        mvc.perform(post("/admin/webhooks/" + id + "/test").session(admin).with(csrf()))
                .andExpect(flash().attribute("success", "The test message was delivered (the receiver answered 200)."));
        assertThat(received).singleElement().satisfies(request ->
                assertThat(request.header("X-Quell-Event")).isEqualTo("webhook.test"));

        mvc.perform(post("/admin/webhooks").session(admin).with(csrf()).param("name", "Bad").param("kind", "GENERIC")
                        .param("url", "not a url").param("events", "REPORTED"))
                .andExpect(status().isOk());              // the form again, with the message
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
