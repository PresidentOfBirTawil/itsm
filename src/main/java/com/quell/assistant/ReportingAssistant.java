package com.quell.assistant;

import java.util.ArrayList;
import java.util.List;

import com.quell.assistant.AssistantTurn.Link;
import com.quell.assistant.AssistantTurn.Reply;
import com.quell.domain.Employee;
import com.quell.error.QuellException;
import com.quell.intelligence.CategoryClassifier;
import com.quell.intelligence.CategorySuggester;
import com.quell.intelligence.ResolutionRecommender;
import com.quell.intelligence.ServiceDetector;
import com.quell.intelligence.TriageAdvisor;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.IncidentRepository;
import com.quell.service.IncidentService;
import com.quell.status.StatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reporting assistant: a chatbot that helps an employee report a problem
 * in a conversation instead of a form. It recognises the service from the
 * words used, checks for a known outage first, offers knowledge articles,
 * asks about impact and urgency in plain words (with smart-triage
 * suggestions), and creates the incident through the same
 * {@link IncidentService} as the form, so every rule (priority, SLA,
 * assignment, notifications) applies.
 *
 * <p>Rule-based on purpose: it works offline, answers instantly, and every
 * step is predictable and testable. The steps are in {@link Step}.</p>
 */
@Service
public class ReportingAssistant {

    /** Longer answers are cut here; a description is at most 4000 characters anyway. */
    static final int MAX_ANSWER = 2000;

    private final IncidentService incidentService;
    private final IncidentRepository incidents;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final ServiceDetector serviceDetector;
    private final TriageAdvisor triage;
    private final CategorySuggester categorySuggester;
    private final CategoryClassifier classifier;
    private final ResolutionRecommender recommender;
    private final StatusService statusService;
    private final TransactionTemplate writing;
    private final TransactionTemplate reading;

    public ReportingAssistant(IncidentService incidentService, IncidentRepository incidents,
                              ITServiceRepository services, CategoryRepository categories,
                              ServiceDetector serviceDetector, TriageAdvisor triage,
                              CategorySuggester categorySuggester, CategoryClassifier classifier,
                              ResolutionRecommender recommender, StatusService statusService,
                              PlatformTransactionManager transactionManager) {
        this.incidentService = incidentService;
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.serviceDetector = serviceDetector;
        this.triage = triage;
        this.categorySuggester = categorySuggester;
        this.classifier = classifier;
        this.recommender = recommender;
        this.statusService = statusService;
        this.writing = new TransactionTemplate(transactionManager);
        this.reading = new TransactionTemplate(transactionManager);
        this.reading.setReadOnly(true);
    }

    /** Opens the conversation with a greeting, unless it has already started. */
    public void start(Conversation conversation, Employee employee) {
        synchronized (conversation) {
            if (conversation.transcript.isEmpty()) {
                show(conversation, List.of(), enter(conversation, employee));
            }
        }
    }

    /** Forgets the conversation and starts a new one. */
    public void restart(Conversation conversation, Employee employee) {
        synchronized (conversation) {
            conversation.reset();
            show(conversation, List.of(), enter(conversation, employee));
        }
    }

    /**
     * Takes the person's answer (typed, spoken, or the value of a button),
     * moves the conversation on and adds both sides to the transcript. A
     * broken rule (for example a duplicate report) is said politely and the
     * conversation stays where it is.
     */
    public void answer(Conversation conversation, Employee employee, String text) {
        String answer = text == null ? "" : text.strip();
        if (answer.isEmpty()) {
            return;
        }
        if (answer.length() > MAX_ANSWER) {
            answer = answer.substring(0, MAX_ANSWER);
        }
        if ("restart".equals(answer)) {
            restart(conversation, employee);
            return;
        }
        synchronized (conversation) {
            conversation.add(ChatMessage.person(labelFor(conversation, answer)));
            Step before = conversation.step;
            List<String> said = new ArrayList<>();
            String given = answer;
            try {
                // Its own transaction, so a broken rule rolls back everything the step did.
                Step next = writing.execute(status -> before.handle(conversation, given, context(employee), said));
                conversation.step = next;
            } catch (QuellException e) {
                said.add(e.getMessage());
            }
            AssistantTurn turn = enter(conversation, employee);
            if (conversation.step == before && !said.isEmpty()) {
                // Same step again: say what went wrong, without repeating the question.
                show(conversation, said, new AssistantTurn(List.of(), turn.replies(), List.of(), turn.placeholder()));
            } else {
                show(conversation, said, turn);
            }
        }
    }

    private AssistantTurn enter(Conversation conversation, Employee employee) {
        return reading.execute(status -> conversation.step.enter(conversation, context(employee)));
    }

    /** Adds what the assistant says to the transcript, with the links under its last message. */
    private static void show(Conversation conversation, List<String> said, AssistantTurn turn) {
        List<String> messages = new ArrayList<>(said);
        messages.addAll(turn.messages());
        for (int i = 0; i < messages.size(); i++) {
            List<Link> links = i == messages.size() - 1 ? turn.links() : List.of();
            conversation.add(ChatMessage.assistant(messages.get(i), links));
        }
        conversation.replies = List.copyOf(turn.replies());
        conversation.placeholder = turn.placeholder();
    }

    /** What the person "said": the label of the button they pressed, or their own words. */
    private static String labelFor(Conversation conversation, String answer) {
        return conversation.replies.stream()
                .filter(reply -> reply.value().equals(answer))
                .map(Reply::label)
                .map(label -> label.replace(" (suggested)", ""))
                .findFirst()
                .orElse(answer);
    }

    private AssistantContext context(Employee employee) {
        return new AssistantContext(employee, incidentService, incidents, services, categories, serviceDetector,
                triage, categorySuggester, classifier, recommender, statusService);
    }
}
