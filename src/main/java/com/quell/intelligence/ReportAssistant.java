package com.quell.intelligence;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.status.StatusService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helps an employee while they write a report, before anything is saved:
 * which service it is about, whether there is already a known problem with
 * it, which category it is (keyword rules first, then the learning classifier),
 * how many people it affects and how urgent it sounds (smart triage), and
 * which knowledge articles may solve it without an incident at all.
 */
@Component
public class ReportAssistant {

    private static final int ARTICLES = 3;

    private final CategorySuggester keywordRules;
    private final CategoryClassifier classifier;
    private final TriageAdvisor triage;
    private final ResolutionRecommender recommender;
    private final CategoryRepository categories;
    private final ITServiceRepository services;
    private final ServiceDetector serviceDetector;
    private final StatusService statusService;

    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    public ReportAssistant(CategorySuggester keywordRules, CategoryClassifier classifier, TriageAdvisor triage,
                           ResolutionRecommender recommender, CategoryRepository categories,
                           ITServiceRepository services, ServiceDetector serviceDetector, StatusService statusService) {
        this.keywordRules = keywordRules;
        this.classifier = classifier;
        this.triage = triage;
        this.recommender = recommender;
        this.categories = categories;
        this.services = services;
        this.serviceDetector = serviceDetector;
        this.statusService = statusService;
    }

    @Transactional(readOnly = true)
    public ReportAdvice advise(String title, String description, Long serviceId) {
        String text = (title == null ? "" : title) + " " + (description == null ? "" : description);
        List<ReportAdvice.KnownIssue> knownIssues = statusService.knownIssuesFor(serviceId).stream()
                .map(incident -> new ReportAdvice.KnownIssue(incident.getCode(), incident.getTitle(),
                        incident.getService().getName(), SINCE.format(incident.getCreatedAt()),
                        incident.getAffectedCount(), String.valueOf(incident.getPriority())))
                .toList();
        if (text.isBlank()) {
            return new ReportAdvice(null, null, null, null, List.of(), knownIssues);
        }

        ReportAdvice.Suggestion service = serviceDetector.detect(text, services.findAll())
                .map(detection -> new ReportAdvice.Suggestion(String.valueOf(detection.service().getId()),
                        detection.service().getName(), detection.explain(), null))
                .orElse(null);

        ReportAdvice.Suggestion category = keywordRules.suggest(text, categories.findAll())
                .map(suggestion -> new ReportAdvice.Suggestion(String.valueOf(suggestion.category().getId()),
                        suggestion.category().getName(),
                        "the report mentions: " + String.join(", ", suggestion.matchedKeywords()), null))
                .orElseGet(() -> classifier.predict(text)
                        .filter(prediction -> prediction.confidence() >= CategoryClassifier.CONFIDENT)
                        .map(prediction -> new ReportAdvice.Suggestion(String.valueOf(prediction.categoryId()),
                                prediction.categoryName(),
                                "learned from similar past incidents"
                                        + (prediction.evidence().isEmpty() ? "" : " (" + String.join(", ", prediction.evidence()) + ")"),
                                prediction.percent()))
                        .orElse(null));

        TriageAdvice advice = triage.advise(text);
        ReportAdvice.Suggestion impact = advice.impact() == null ? null : new ReportAdvice.Suggestion(
                advice.impact().name(), advice.impact().getLabel(), "you wrote “" + advice.impactReason() + "”", null);
        ReportAdvice.Suggestion urgency = advice.urgency() == null ? null : new ReportAdvice.Suggestion(
                advice.urgency().name(), advice.urgency().getLabel(), "you wrote “" + advice.urgencyReason() + "”", null);

        List<ReportAdvice.Article> articles = recommender.articlesFor(text, ARTICLES).stream()
                .map(match -> new ReportAdvice.Article(match.item().getId(), match.item().getReference(),
                        match.item().getTitle(), match.percent(), "/kb/" + match.item().getId()))
                .toList();
        return new ReportAdvice(service, category, impact, urgency, articles, knownIssues);
    }
}
