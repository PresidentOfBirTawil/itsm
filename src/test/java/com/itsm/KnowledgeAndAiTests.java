package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.KnowledgeArticle;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.intelligence.CategoryClassifier;
import com.itsm.intelligence.Recommendation;
import com.itsm.intelligence.ResolutionRecommender;
import com.itsm.knowledge.KnowledgeService;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.KnowledgeArticleRepository;
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

/**
 * The knowledge base and the learning features inside the running
 * application: suggested solutions, self-help while typing, the classifier
 * choosing a category, and writing up a fix as an article.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KnowledgeAndAiTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private KnowledgeService knowledge;
    @Autowired
    private ResolutionRecommender recommender;
    @Autowired
    private CategoryClassifier classifier;
    @Autowired
    private KnowledgeArticleRepository articles;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Incident report(String title, String description, String service, String category) {
        return incidentService.report((Employee) user("emp1@itsm.local"), title, description,
                services.findByName(service).orElseThrow().getId(),
                category == null ? null : categories.findByNameIgnoreCase(category).orElseThrow().getId(),
                Impact.MEDIUM, Urgency.MEDIUM);
    }

    private Incident resolve(Incident incident, String resolution) {
        User technician = incident.getAssignee();
        incidentService.changeStatus(incident.getCode(), Status.ACKNOWLEDGED, technician, null);
        incidentService.changeStatus(incident.getCode(), Status.IN_PROGRESS, technician, null);
        incidentService.changeStatus(incident.getCode(), Status.RESOLVED, technician, resolution);
        return incident;
    }

    // ---- the knowledge base ----

    @Test
    void theKnowledgeBaseStartsWithArticlesForCommonProblems() {
        assertThat(articles.count()).isGreaterThanOrEqualTo(15);
        assertThat(articles.findAll()).extracting(KnowledgeArticle::getTitle)
                .contains("VPN disconnects every few minutes", "Forgot my password");
    }

    @Test
    void searchingFindsArticlesByMeaningNotExactWording() throws Exception {
        assertThat(knowledge.search("vpn keeps dropping when I work from home").get(0).getTitle())
                .isEqualTo("VPN disconnects every few minutes");
        assertThat(knowledge.search("KB-0001")).hasSize(1);
        assertThat(knowledge.search(null)).hasSize((int) articles.count());

        mvc.perform(get("/kb").param("q", "mailbox full").session(employee()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mailbox is full and rejects new mail")));
    }

    @Test
    void readingAnArticleCountsTheViewAndAnyoneCanSayIfItHelped() throws Exception {
        KnowledgeArticle article = knowledge.search("forgot password").get(0);
        MockHttpSession employee = employee();

        mvc.perform(get("/kb/" + article.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Did this solve your problem?")))
                .andExpect(content().string(not(containsString("/kb/manage/" + article.getId() + "/edit"))));
        mvc.perform(post("/kb/" + article.getId() + "/vote").param("helpful", "true").session(employee).with(csrf()))
                .andExpect(flash().attribute("success", "Thanks! Glad it helped."));

        assertThat(article.getViews()).isEqualTo(1);
        assertThat(article.getHelpfulPercent()).isEqualTo(100);
    }

    @Test
    void onlyTechniciansAndAdministratorsWriteArticles() throws Exception {
        mvc.perform(get("/kb/manage/new").session(employee())).andExpect(status().isForbidden());
        mvc.perform(post("/kb/manage").session(employee()).with(csrf())
                        .param("title", "Sneaky").param("problem", "p").param("solution", "s"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/kb/manage").session(technician()).with(csrf())
                        .param("title", "Headset microphone is muted").param("problem", "Nobody hears me in calls.")
                        .param("solution", "Press the mute button on the headset cable.")
                        .param("categoryId", categories.findByNameIgnoreCase("Hardware").orElseThrow().getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", containsString("has been published")));
        assertThat(knowledge.search("headset microphone muted").get(0).getAuthor().getName()).isEqualTo("Ravi Kumar");
    }

    @Test
    void anArticleWithoutASolutionIsSentBack() throws Exception {
        mvc.perform(post("/kb/manage").session(technician()).with(csrf())
                        .param("title", "Half an article").param("problem", "Something").param("solution", " "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "solution"));
    }

    // ---- suggested solutions ----

    @Test
    void anIncidentGetsTheMatchingArticleAsASuggestedSolution() {
        Incident incident = report("VPN disconnects all the time", "Working from home the VPN drops every few minutes.",
                "Corporate Network", "Network");

        List<Recommendation> suggestions = recommender.recommend(incident, 3);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).title()).isEqualTo("VPN disconnects every few minutes");
        assertThat(suggestions.get(0).isArticle()).isTrue();
        assertThat(suggestions.get(0).percent()).isGreaterThanOrEqualTo(40);
        assertThat(suggestions.get(0).sharedWords()).contains("vpn");
    }

    @Test
    void aResolvedIncidentIsSuggestedForTheNextSimilarOne() {
        Incident first = resolve(report("Projector in room 5 shows no picture", "The projector lamp blinks orange.",
                "Email & Collaboration", "Hardware"), "Replaced the projector lamp.");
        Incident second = report("Projector shows no picture in room 2", "Lamp blinking orange again.",
                "Email & Collaboration", "Hardware");

        List<Recommendation> suggestions = recommender.recommend(second, 3);

        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.source()).isEqualTo(Recommendation.Source.PAST_INCIDENT);
            assertThat(suggestion.incidentCode()).isEqualTo(first.getCode());
            assertThat(suggestion.solution()).isEqualTo("Replaced the projector lamp.");
        });
    }

    @Test
    void aFixThatWorkedSeveralTimesIsSuggestedOnceWithItsCount() {
        for (String room : new String[] {"room 1", "room 4", "room 7"}) {
            resolve(report("Projector shows no picture in " + room, "The projector lamp blinks orange.",
                    "Email & Collaboration", "Hardware"), "Replaced the projector lamp.");
        }
        Incident next = report("Projector in room 9 shows no picture", "Lamp blinking orange.",
                "Email & Collaboration", "Hardware");

        List<Recommendation> pastFixes = recommender.recommend(next, 5).stream()
                .filter(suggestion -> !suggestion.isArticle()).toList();

        assertThat(pastFixes).hasSize(1);
        assertThat(pastFixes.get(0).timesUsed()).isEqualTo(3);
    }

    @Test
    void theTechnicianSeesTheSuggestionsOnTheIncidentPageAndTheReporterDoesNot() throws Exception {
        Incident incident = report("Account locked after password change", "It locks again a few minutes later.",
                "Authentication Service", "Authentication");

        mvc.perform(get("/technician/incidents/" + incident.getCode()).session(technician("tech3")))
                .andExpect(content().string(containsString("Suggested solutions")))
                .andExpect(content().string(containsString("Account locked after changing my password")))
                .andExpect(content().string(containsString("Use this text")));
        mvc.perform(get("/employee/incidents/" + incident.getCode()).session(employee()))
                .andExpect(content().string(not(containsString("Suggested solutions"))));
    }

    // ---- writing up a fix ----

    @Test
    void aResolvedIncidentCanBeWrittenUpAsAnArticleOnce() throws Exception {
        Incident incident = resolve(report("Projector shows no picture", "The projector lamp blinks orange.",
                "Email & Collaboration", "Hardware"), "Replaced the projector lamp.");
        MockHttpSession ravi = technician();

        mvc.perform(get("/technician/incidents/" + incident.getCode()).session(ravi))
                .andExpect(content().string(containsString("Save as a knowledge article")));
        mvc.perform(get("/kb/manage/new").param("fromIncident", incident.getCode()).session(ravi))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Replaced the projector lamp.")))
                .andExpect(content().string(containsString("Written from " + incident.getCode())));
        mvc.perform(post("/kb/manage").session(ravi).with(csrf())
                        .param("title", "Projector shows no picture").param("problem", "The lamp blinks orange.")
                        .param("solution", "Replace the lamp.").param("sourceIncidentCode", incident.getCode()))
                .andExpect(status().is3xxRedirection());

        assertThat(knowledge.writtenFrom(incident)).isPresent();
        mvc.perform(get("/technician/incidents/" + incident.getCode()).session(ravi))
                .andExpect(content().string(containsString("Written up as")))
                .andExpect(content().string(not(containsString("Save as a knowledge article"))));
        assertThatThrownBy(() -> knowledge.create("Again", "p", "s", null, null, user("tech1@itsm.local"), incident))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("already been written up");
    }

    // ---- the learning classifier and smart triage ----

    @Test
    void whenNoKeywordMatchesTheClassifierChoosesTheCategoryAndSaysWhy() {
        // No category keyword in this text, but the knowledge base has taught the classifier these words.
        Incident incident = report("Standby lagging behind the primary", "Statistics and locks look odd, reports crawl.",
                "Database Cluster", null);

        assertThat(incident.getCategory().getName()).isEqualTo("Database");
        AuditEntry suggested = incidentService.history(incident).stream()
                .filter(entry -> entry.getAction() == IncidentAction.CATEGORY_SUGGESTED).findFirst().orElseThrow();
        assertThat(suggested.getNote()).contains("learning classifier").contains("% sure");
        assertThat(classifier.trainingSize()).isGreaterThan(50);
    }

    @Test
    void theClassifierQuestionsAnIncidentFiledUnderTheWrongCategory() throws Exception {
        Incident incident = report("VPN drops every few minutes", "The VPN client disconnects at home.",
                "Corporate Network", "Hardware");   // filed wrongly; Ravi has Hardware and gets it

        mvc.perform(get("/technician/incidents/" + incident.getCode()).session(technician()))
                .andExpect(content().string(containsString("The classifier thinks this is")))
                .andExpect(content().string(containsString("Change to Network")));
    }

    @Test
    void theReportFormGetsSuggestionsWhileTheEmployeeTypes() throws Exception {
        mvc.perform(get("/employee/incidents/assist").session(employee())
                        .param("title", "VPN keeps disconnecting")
                        .param("description", "Everyone in the sales team cannot work from home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.label").value("Network"))
                .andExpect(jsonPath("$.impact.value").value("HIGH"))
                .andExpect(jsonPath("$.urgency.value").value("HIGH"))
                .andExpect(jsonPath("$.articles[0].title").value("VPN disconnects every few minutes"));

        mvc.perform(get("/employee/incidents/assist").session(employee()).param("title", "").param("description", ""))
                .andExpect(jsonPath("$.articles", hasSize(0)));
        mvc.perform(get("/employee/incidents/assist").session(technician())).andExpect(status().isForbidden());
    }

    @Test
    void theKnowledgeSuggestionsAreAvailableAsJson() throws Exception {
        mvc.perform(get("/kb/suggest").param("q", "printer prints blank pages").session(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Printer jams or prints blank pages"))
                .andExpect(jsonPath("$[0].url").value(containsString("/kb/")));
    }

    private MockHttpSession employee() throws Exception {
        return signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
    }

    private MockHttpSession technician() throws Exception {
        return technician("tech1");
    }

    private MockHttpSession technician(String name) throws Exception {
        return signIn(name + "@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
