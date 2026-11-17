package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.itsm.assistant.ChatMessage;
import com.itsm.assistant.Conversation;
import com.itsm.assistant.ReportingAssistant;
import com.itsm.assistant.Step;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import com.itsm.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The reporting assistant: a conversation that ends in an incident, a joined known problem, or a solved problem. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssistantTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ReportingAssistant assistant;
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

    private Employee employee(int number) {
        return (Employee) users.findByEmail("emp" + number + "@itsm.local").orElseThrow();
    }

    private static String lastMessage(Conversation conversation) {
        return conversation.getTranscript().getLast().text();
    }

    /** Answers until the conversation reaches the step, pressing "I still need help" at the articles. */
    private void answer(Conversation conversation, Employee employee, String text) {
        assistant.answer(conversation, employee, text);
        if (conversation.getStep() == Step.ARTICLES) {
            assistant.answer(conversation, employee, "no");
        }
    }

    @Test
    void aConversationInPlainWordsReportsAnIncident() {
        Employee rahul = employee(2);
        Conversation conversation = new Conversation();
        assistant.start(conversation, rahul);
        assertThat(conversation.getTranscript()).first().extracting(ChatMessage::text).asString().startsWith("Hi Rahul!");
        assertThat(conversation.getStep()).isEqualTo(Step.DESCRIBE);

        assistant.answer(conversation, rahul, "My laptop battery drains within an hour and the laptop gets very hot.");
        assertThat(conversation.getStep()).isEqualTo(Step.SERVICE);
        assertThat(lastMessage(conversation)).contains("Workplace Devices");
        assertThat(conversation.getReplies().getFirst().label()).isEqualTo("Yes, Workplace Devices");

        answer(conversation, rahul, "yes");          // typed instead of pressing the button
        assertThat(conversation.getStep()).isEqualTo(Step.WHO);
        answer(conversation, rahul, "only me");
        assertThat(conversation.getStep()).isEqualTo(Step.URGENCY);
        answer(conversation, rahul, "it can wait until next week");
        assertThat(conversation.getStep()).isEqualTo(Step.CONFIRM);
        assertThat(conversation.getTranscript()).anySatisfy(message ->
                assertThat(message.text()).contains("Service: Workplace Devices · Category: Hardware"));

        assistant.answer(conversation, rahul, "yes");
        assertThat(conversation.getStep()).isEqualTo(Step.DONE);
        Incident incident = incidents.findByCode(conversation.getCreatedCode()).orElseThrow();
        assertThat(incident.getReporter()).isEqualTo(rahul);
        assertThat(incident.getTitle()).isEqualTo("My laptop battery drains within an hour and the laptop gets very hot");
        assertThat(incident.getService().getName()).isEqualTo("Workplace Devices");
        assertThat(incident.getCategory().getName()).isEqualTo("Hardware");
        assertThat(incident.getImpact()).isEqualTo(Impact.LOW);
        assertThat(incident.getUrgency()).isEqualTo(Urgency.LOW);
        assertThat(conversation.getTranscript()).anySatisfy(message ->
                assertThat(message.text()).startsWith("Reported as **" + incident.getCode() + "**"));
        assertThat(conversation.getTranscript().getLast().links()).singleElement()
                .satisfies(link -> assertThat(link.url()).isEqualTo("/employee/incidents/" + incident.getCode()));
    }

    @Test
    void aTooShortDescriptionIsAskedAgain() {
        Employee priya = employee(1);
        Conversation conversation = new Conversation();
        assistant.start(conversation, priya);
        assistant.answer(conversation, priya, "hi");

        assertThat(conversation.getStep()).isEqualTo(Step.DESCRIBE);
        assertThat(lastMessage(conversation)).startsWith("Could you tell me a little more?");
    }

    @Test
    void theButtonsOfferTheSmartTriageGuessFirst() {
        Employee priya = employee(1);
        Conversation conversation = new Conversation();
        assistant.start(conversation, priya);
        assistant.answer(conversation, priya, "Nobody in the whole company can send email and we cannot work at all.");
        answer(conversation, priya, "yes");

        assertThat(conversation.getStep()).isEqualTo(Step.WHO);
        assertThat(conversation.getReplies().getFirst().label()).endsWith("(suggested)");
        assertThat(conversation.getReplies().getFirst().value()).isEqualTo("impact:HIGH");
    }

    @Test
    void aKnownProblemIsJoinedInsteadOfReportedAgain() {
        Incident outage = incidentService.report(employee(1), "Card payments are declined", "Card payments fail.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
        long before = incidents.count();
        Employee ananya = employee(3);
        Conversation conversation = new Conversation();
        assistant.start(conversation, ananya);

        assistant.answer(conversation, ananya, "Card payments fail at checkout for our customers since this morning.");
        assistant.answer(conversation, ananya, "service:" + services.findByName("Payment Application").orElseThrow().getId());
        assertThat(conversation.getStep()).isEqualTo(Step.KNOWN_ISSUE);
        assertThat(conversation.getTranscript()).anySatisfy(message -> assertThat(message.text()).contains(outage.getCode()));

        assistant.answer(conversation, ananya, "yes");
        assertThat(conversation.getStep()).isEqualTo(Step.DONE);
        assertThat(conversation.getCreatedCode()).isEqualTo(outage.getCode());
        assertThat(outage.isAffecting(ananya)).isTrue();
        assertThat(incidents.count()).isEqualTo(before);
    }

    @Test
    void whoReportedTheKnownProblemIsNotOfferedIt() {
        Employee priya = employee(1);
        incidentService.report(priya, "Card payments are declined", "Card payments fail.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
        Conversation conversation = new Conversation();
        assistant.start(conversation, priya);
        assistant.answer(conversation, priya, "Card payments fail at checkout for our customers since this morning.");
        assistant.answer(conversation, priya, "yes");

        assertThat(conversation.getStep()).isNotEqualTo(Step.KNOWN_ISSUE);
    }

    @Test
    void aBrokenRuleIsSaidPolitelyAndTheConversationStaysWhereItIs() {
        Employee karan = employee(4);
        String problem = "The printer on floor two jams on every single print job.";
        Conversation first = new Conversation();
        assistant.start(first, karan);
        for (String text : List.of(problem, "yes", "just me", "it slows me down", "yes")) {
            answer(first, karan, text);
        }
        assertThat(first.getStep()).isEqualTo(Step.DONE);

        Conversation second = new Conversation();
        assistant.start(second, karan);
        for (String text : List.of(problem, "yes", "just me", "it slows me down", "yes")) {
            answer(second, karan, text);
        }
        assertThat(second.getStep()).isEqualTo(Step.CONFIRM);
        assertThat(lastMessage(second)).contains(first.getCreatedCode());
    }

    @Test
    void anAnswerItCannotUseIsAskedAgainWithoutRepeatingTheQuestion() {
        Employee priya = employee(1);
        Conversation conversation = new Conversation();
        assistant.start(conversation, priya);
        assistant.answer(conversation, priya, "The VPN disconnects every few minutes when I work from home.");
        answer(conversation, priya, "yes");
        int messages = conversation.getTranscript().size();

        assistant.answer(conversation, priya, "banana");

        assertThat(conversation.getStep()).isEqualTo(Step.WHO);
        assertThat(conversation.getTranscript()).hasSize(messages + 2);   // what was typed, and one reply
        assertThat(lastMessage(conversation)).startsWith("Sorry, I didn't get that.");
    }

    @Test
    void startingAgainForgetsEverything() {
        Employee priya = employee(1);
        Conversation conversation = new Conversation();
        assistant.start(conversation, priya);
        assistant.answer(conversation, priya, "The VPN disconnects every few minutes when I work from home.");

        assistant.answer(conversation, priya, "restart");

        assertThat(conversation.getStep()).isEqualTo(Step.DESCRIBE);
        assertThat(conversation.getTranscript()).hasSize(2);
        assertThat(conversation.getDescription()).isEmpty();
    }

    @Test
    void wordsBetweenDoubleStarsAreBoldOnlyInTheAssistantsMessages() {
        ChatMessage message = new ChatMessage(true, "Reported as **INC-0001** with priority **P2**.", List.of());
        assertThat(message.parts()).extracting(ChatMessage.Part::text)
                .containsExactly("Reported as ", "INC-0001", " with priority ", "P2", ".");
        assertThat(message.parts()).extracting(ChatMessage.Part::bold).containsExactly(false, true, false, true, false);
        assertThat(new ChatMessage(false, "**not bold**", List.of()).parts()).singleElement()
                .satisfies(part -> assertThat(part.bold()).isFalse());
    }

    // ---- the page ----

    @Test
    void thePageWorksWithAndWithoutJavaScript() throws Exception {
        MockHttpSession session = signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        mvc.perform(get("/employee/assistant").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("I&#39;ll help you report an IT problem")));

        // Without JavaScript: an ordinary form post, then the page again.
        mvc.perform(post("/employee/assistant").session(session).with(csrf())
                        .param("text", "The VPN disconnects every few minutes when I work from home."))
                .andExpect(redirectedUrl("/employee/assistant"));

        // With it: only the chat, rendered by the same template. A button shows as what it says.
        String network = "service:" + services.findByName("Corporate Network").orElseThrow().getId();
        mvc.perform(post("/employee/assistant").session(session).with(csrf()).header("X-Requested-With", "fetch")
                        .param("text", network))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-chat")))
                .andExpect(content().string(containsString("Yes, Corporate Network")))
                .andExpect(content().string(not(containsString("<nav"))));
    }

    @Test
    void whatPeopleTypeIsNeverTreatedAsHtml() throws Exception {
        MockHttpSession session = signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        mvc.perform(get("/employee/assistant").session(session));
        mvc.perform(post("/employee/assistant").session(session).with(csrf()).header("X-Requested-With", "fetch")
                        .param("text", "<script>alert(1)</script> my laptop screen stays black after the update"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void theAssistantIsForEmployees() throws Exception {
        mvc.perform(get("/employee/assistant").session(signIn("tech1@itsm.local", DataSeeder.TECHNICIAN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/employee/assistant")).andExpect(status().is3xxRedirection());
    }

    @Test
    void startOverOnThePageBeginsANewConversation() throws Exception {
        MockHttpSession session = signIn("emp2@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        mvc.perform(get("/employee/assistant").session(session));
        mvc.perform(post("/employee/assistant").session(session).with(csrf())
                .param("text", "The VPN disconnects every few minutes when I work from home."));

        mvc.perform(post("/employee/assistant/restart").session(session).with(csrf()))
                .andExpect(redirectedUrl("/employee/assistant"));

        Conversation conversation = (Conversation) session.getAttribute("quell.assistant");
        assertThat(conversation.getStep()).isEqualTo(Step.DESCRIBE);
        assertThat(conversation.getTranscript()).hasSize(2);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
