package com.itsm.intelligence;

import java.util.List;

import com.itsm.repository.CategoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helps an employee while they write a report, before anything is saved:
 * which category it is (keyword rules first, then the learning classifier),
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

    public ReportAssistant(CategorySuggester keywordRules, CategoryClassifier classifier, TriageAdvisor triage,
                           ResolutionRecommender recommender, CategoryRepository categories) {
        this.keywordRules = keywordRules;
        this.classifier = classifier;
        this.triage = triage;
        this.recommender = recommender;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public ReportAdvice advise(String title, String description) {
        String text = (title == null ? "" : title) + " " + (description == null ? "" : description);
        if (text.isBlank()) {
            return new ReportAdvice(null, null, null, List.of());
        }

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
        return new ReportAdvice(category, impact, urgency, articles);
    }
}
