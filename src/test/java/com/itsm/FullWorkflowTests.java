package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.InvalidStateTransitionException;
import com.itsm.error.UnauthorizedOperationException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The complete technician and employee workflows: investigation notes,
 * asking for and giving information, recording the resolution, confirming
 * or reopening it, and rating it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FullWorkflowTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentService incidentService;
    @Autowired
    private UserRepository users;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;

    private Employee priya;
    private Employee rahul;
    private Technician sara;
    private Technician ravi;
    private User admin;
    private Incident incident;
    private String code;

    /** Priya reports the payment outage; it is assigned to Sara. */
    @BeforeEach
    void report() {
        priya = (Employee) users.findByEmail("emp1@itsm.local").orElseThrow();
        rahul = (Employee) users.findByEmail("emp2@itsm.local").orElseThrow();
        sara = (Technician) users.findByEmail("tech4@itsm.local").orElseThrow();
        ravi = (Technician) users.findByEmail("tech1@itsm.local").orElseThrow();
        admin = users.findByEmail("admin@itsm.local").orElseThrow();
        incident = incidentService.report(priya, "Payment application is unavailable", "Details.",
                services.findByName("Payment Application").orElseThrow().getId(),
                categories.findByNameIgnoreCase("Application").orElseThrow().getId(), Impact.HIGH, Urgency.HIGH);
        code = incident.getCode();
    }

    private void startWork() {
        incidentService.changeStatus(code, Status.ACKNOWLEDGED, sara, null);
        incidentService.changeStatus(code, Status.IN_PROGRESS, sara, null);
    }

    private void resolveAndConfirm() {
        startWork();
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Renewed the expired certificate.");
        incidentService.changeStatus(code, Status.VERIFIED, priya, null);
    }

    private AuditEntry lastEntry() {
        return incidentService.history(incident).getLast();
    }

    // ---- investigation notes and extra information ----

    @Test
    void theTechnicianAddsInvestigationNotesWithoutChangingTheStatus() {
        startWork();

        incidentService.addNote(code, sara, "The gateway log shows TLS handshake errors since 09:12.");

        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(lastEntry().getAction()).isEqualTo(IncidentAction.NOTE_ADDED);
        assertThat(lastEntry().getActor()).isEqualTo(sara);
        assertThat(lastEntry().getNote()).isEqualTo("The gateway log shows TLS handshake errors since 09:12.");
        assertThat(lastEntry().isStatusChange()).isFalse();
    }

    @Test
    void theReporterCanAddInformationAndAnAdministratorCanAddANote() {
        incidentService.addNote(code, priya, "It started right after the 9 o'clock deployment.");
        assertThat(lastEntry().getActor()).isEqualTo(priya);

        incidentService.addNote(code, admin, "The vendor has been informed.");
        assertThat(lastEntry().getActor()).isEqualTo(admin);
    }

    @Test
    void peopleWhoAreNotInvolvedCannotAddNotes() {
        assertThatThrownBy(() -> incidentService.addNote(code, rahul, "Me too!"))
                .isInstanceOf(UnauthorizedOperationException.class); // another employee cannot even open it
        assertThatThrownBy(() -> incidentService.addNote(code, ravi, "I had a look."))
                .isInstanceOf(UnauthorizedOperationException.class); // another technician can read it, not write
    }

    @Test
    void anEmptyNoteOrANoteOnAClosedIncidentIsRefused() {
        assertThatThrownBy(() -> incidentService.addNote(code, sara, "   "))
                .isInstanceOf(InvalidInputException.class);

        resolveAndConfirm();
        incidentService.changeStatus(code, Status.CLOSED, sara, null);
        int entries = incidentService.history(incident).size();

        assertThatThrownBy(() -> incidentService.addNote(code, sara, "One more thing."))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("closed");
        assertThat(incidentService.history(incident)).hasSize(entries);
    }

    // ---- asking for information and getting it ----

    @Test
    void theTechnicianAsksAndTheReporterAnswers() {
        startWork();

        incidentService.changeStatus(code, Status.AWAITING_INFO, sara, "Does it fail for every customer?");
        assertThat(incident.getStatus()).isEqualTo(Status.AWAITING_INFO);

        incidentService.changeStatus(code, Status.IN_PROGRESS, priya, "Yes, every card payment.");
        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(lastEntry().getActor()).isEqualTo(priya);
        assertThat(lastEntry().getNote()).isEqualTo("Yes, every card payment.");
    }

    // ---- recording the resolution ----

    @Test
    void resolvingRecordsWhatWasDone() {
        startWork();
        assertThat(incident.getResolution()).isNull();

        incidentService.changeStatus(code, Status.RESOLVED, sara, "  Renewed the expired certificate.  ");

        assertThat(incident.getStatus()).isEqualTo(Status.RESOLVED);
        assertThat(incident.getResolution()).isEqualTo("Renewed the expired certificate.");
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    @Test
    void anIncidentCannotBeResolvedWithoutSayingWhatWasDone() {
        startWork();

        assertThatThrownBy(() -> incidentService.changeStatus(code, Status.RESOLVED, sara, ""))
                .isInstanceOf(InvalidInputException.class);
        assertThat(incident.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(incident.getResolution()).isNull();

        // The rule is in the model itself, not only in the service.
        assertThatThrownBy(() -> incident.transitionTo(Status.RESOLVED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("no resolution has been recorded");
    }

    @Test
    void reopeningDropsTheResolutionAndResolvingAgainRecordsTheNewOne() {
        startWork();
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Restarted the gateway.");

        incidentService.changeStatus(code, Status.IN_PROGRESS, priya, "Card payments still fail.");
        assertThat(incident.getResolution()).isNull();
        assertThat(incident.getResolvedAt()).isNull();

        incidentService.changeStatus(code, Status.RESOLVED, sara, "Renewed the expired certificate.");
        assertThat(incident.getResolution()).isEqualTo("Renewed the expired certificate.");
    }

    // ---- rating ----

    @Test
    void theReporterRatesTheResolutionOnceItIsConfirmed() {
        resolveAndConfirm();

        incidentService.rate(code, priya, 4, "Quick, but I had to chase once.");

        assertThat(incident.getRating()).isEqualTo(4);
        assertThat(incident.getRatingComment()).isEqualTo("Quick, but I had to chase once.");
        assertThat(incident.getRatedAt()).isNotNull();
        assertThat(lastEntry().getAction()).isEqualTo(IncidentAction.RATED);
        assertThat(lastEntry().getNote()).isEqualTo("Rated 4 out of 5. Quick, but I had to chase once.");
    }

    @Test
    void aRatingIsPossibleOnlyAfterConfirmingOnlyOnceAndOnlyFromOneToFive() {
        startWork();
        incidentService.changeStatus(code, Status.RESOLVED, sara, "Renewed the expired certificate.");
        assertThatThrownBy(() -> incidentService.rate(code, priya, 5, null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("once you have confirmed");

        incidentService.changeStatus(code, Status.VERIFIED, priya, null);
        assertThatThrownBy(() -> incidentService.rate(code, priya, 0, null)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> incidentService.rate(code, priya, 6, null)).isInstanceOf(InvalidInputException.class);
        assertThat(incident.getRating()).isNull();

        incidentService.rate(code, priya, 5, null);
        assertThatThrownBy(() -> incidentService.rate(code, priya, 1, "Changed my mind"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("already been rated");
        assertThat(incident.getRating()).isEqualTo(5);
    }

    @Test
    void onlyTheReporterCanRate() {
        resolveAndConfirm();

        assertThatThrownBy(() -> incidentService.rate(code, sara, 5, "Great job, me"))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThatThrownBy(() -> incidentService.rate(code, admin, 5, null))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThatThrownBy(() -> incidentService.rate(code, rahul, 1, null))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThat(incident.getRating()).isNull();
    }

    @Test
    void aClosedIncidentCanStillBeRated() {
        resolveAndConfirm();
        incidentService.changeStatus(code, Status.CLOSED, admin, null);

        incidentService.rate(code, priya, 3, null);

        assertThat(incident.getRating()).isEqualTo(3);
    }

    // ---- the same, through the pages ----

    @Test
    void theWholeWorkflowThroughThePages() throws Exception {
        MockHttpSession saraSession = signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD);
        MockHttpSession priyaSession = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        String technicianPage = "/technician/incidents/" + code;
        String employeePage = "/employee/incidents/" + code;

        // Technician: acknowledge, start, note, ask.
        press(saraSession, technicianPage + "/transition", "target", "ACKNOWLEDGED");
        press(saraSession, technicianPage + "/transition", "target", "IN_PROGRESS");
        mvc.perform(get(technicianPage).session(saraSession))
                .andExpect(content().string(containsString("Add an investigation note")));
        press(saraSession, technicianPage + "/notes", "note", "The gateway log shows TLS errors.");
        press(saraSession, technicianPage + "/transition", "target", "AWAITING_INFO", "note", "Does it fail for everyone?");

        // Employee: sees it on the dashboard, answers.
        mvc.perform(get("/employee/dashboard").session(priyaSession))
                .andExpect(content().string(containsString("Waiting for you")))
                .andExpect(content().string(containsString("The technician has asked you a question")));
        press(priyaSession, employeePage + "/transition", "target", "IN_PROGRESS", "note", "Yes, for every card.");

        // Technician: resolve, with the resolution.
        press(saraSession, technicianPage + "/transition", "target", "RESOLVED", "note", "Renewed the certificate.");

        // Employee: sees the resolution, confirms, rates.
        mvc.perform(get(employeePage).session(priyaSession))
                .andExpect(content().string(containsString("Resolution")))
                .andExpect(content().string(containsString("Renewed the certificate.")))
                .andExpect(content().string(containsString(">Confirm it is fixed</button>")))
                .andExpect(content().string(not(containsString("Rate the resolution"))));
        press(priyaSession, employeePage + "/transition", "target", "VERIFIED");
        mvc.perform(get(employeePage).session(priyaSession))
                .andExpect(content().string(containsString("Rate the resolution")));
        press(priyaSession, employeePage + "/rating", "rating", "5", "comment", "Fast and clear.");

        assertThat(incident.getStatus()).isEqualTo(Status.VERIFIED);
        assertThat(incident.getRating()).isEqualTo(5);
        mvc.perform(get(technicianPage).session(saraSession))
                .andExpect(content().string(containsString("5 out of 5")))
                .andExpect(content().string(containsString("Fast and clear.")))
                .andExpect(content().string(containsString("The gateway log shows TLS errors.")))
                .andExpect(content().string(not(containsString("Rate the resolution"))));
        mvc.perform(get(employeePage).session(priyaSession))
                .andExpect(content().string(not(containsString("Rate the resolution")))); // already rated
    }

    @Test
    void aRatingOutsideOneToFiveIsRefusedByTheFormWithAMessage() throws Exception {
        resolveAndConfirm();
        MockHttpSession priyaSession = signIn("emp1@itsm.local", DataSeeder.EMPLOYEE_PASSWORD);
        String page = "/employee/incidents/" + code;

        mvc.perform(post(page + "/rating").session(priyaSession).with(csrf()).header("Referer", page)
                        .param("rating", "9"))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("error", "The rating must be between 1 and 5."));
        mvc.perform(post(page + "/rating").session(priyaSession).with(csrf()).header("Referer", page))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("error", "Please choose a rating from 1 to 5."));

        assertThat(incident.getRating()).isNull();
    }

    @Test
    void aTechnicianCannotPostARating() throws Exception {
        resolveAndConfirm();

        mvc.perform(post("/employee/incidents/" + code + "/rating")
                        .session(signIn("tech4@itsm.local", DataSeeder.TECHNICIAN_PASSWORD)).with(csrf())
                        .param("rating", "5"))
                .andExpect(status().isForbidden());
    }

    private void press(MockHttpSession session, String url, String... params) throws Exception {
        var request = post(url).session(session).with(csrf());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(email).password(password))
                .andReturn().getRequest().getSession(false);
    }
}
