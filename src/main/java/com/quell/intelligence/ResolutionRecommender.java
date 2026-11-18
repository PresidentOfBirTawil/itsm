package com.quell.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.quell.domain.Incident;
import com.quell.domain.KnowledgeArticle;
import com.quell.domain.Status;
import com.quell.intelligence.text.SimilarityIndex;
import com.quell.repository.IncidentRepository;
import com.quell.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Component;

/**
 * AI-based resolution recommendation: for an incident, finds the knowledge
 * articles and past resolved incidents that look most like it, and offers
 * their solutions to the technician.
 *
 * <p>Similarity is TF-IDF cosine similarity ({@link SimilarityIndex}) on the
 * words that describe the problem (title and description), so a match is
 * about the same symptoms. A candidate on the same service or in the same
 * category gets a small bonus, a past fix the reporter rated poorly a small
 * penalty. Every suggestion comes with the words it matched on, so the
 * technician can judge it at a glance.</p>
 *
 * <p>The same index answers employees while they type a report: articles
 * that may solve the problem before an incident is even created.</p>
 */
@Component
public class ResolutionRecommender {

    /** Suggestions less similar than this are left out. */
    static final double MINIMUM_SCORE = 0.15;
    private static final double SAME_SERVICE_BONUS = 0.08;
    private static final double SAME_CATEGORY_BONUS = 0.08;
    private static final double POOR_RATING_PENALTY = 0.10;

    private final KnowledgeArticleRepository articles;
    private final IncidentRepository incidents;

    public ResolutionRecommender(KnowledgeArticleRepository articles, IncidentRepository incidents) {
        this.articles = articles;
        this.incidents = incidents;
    }

    /** Suggested solutions for an incident, best first. */
    public List<Recommendation> recommend(Incident incident, int limit) {
        String query = incident.getTitle() + " " + incident.getDescription();
        List<KnowledgeArticle> allArticles = articles.findAll();
        Set<Long> coveredByArticles = new HashSet<>();
        allArticles.stream().map(KnowledgeArticle::getSourceIncident).filter(Objects::nonNull)
                .forEach(source -> coveredByArticles.add(source.getId()));

        List<Recommendation> found = new ArrayList<>();
        // Articles are matched on their whole text: the solution often names the cause ("phishing", "index").
        SimilarityIndex.build(allArticles, article -> article.searchText() + " " + article.getSolution())
                .search(query, limit * 3, MINIMUM_SCORE / 2)
                .forEach(match -> {
                    KnowledgeArticle article = match.item();
                    double score = match.score()
                            + bonus(article.getService() != null && article.getService().equals(incident.getService()),
                            article.getCategory() != null && article.getCategory().equals(incident.getCategory()));
                    found.add(new Recommendation(Recommendation.Source.ARTICLE, article.getReference(),
                            article.getId(), null, article.getTitle(), article.getSolution(), percent(score),
                            match.sharedWords(), article.getHelpfulPercent(), 1));
                });

        List<Incident> pastFixes = incidents.findByResolutionIsNotNullAndStatusIn(Status.finishedStatuses()).stream()
                .filter(past -> !past.getId().equals(incident.getId()))
                .filter(past -> !coveredByArticles.contains(past.getId()))
                .toList();
        // A fix that worked several times is one suggestion, with the count: "fixed 4 similar incidents".
        Map<String, Long> timesUsed = pastFixes.stream()
                .collect(Collectors.groupingBy(past -> sameFix(past.getResolution()), Collectors.counting()));
        Set<String> alreadySuggested = new HashSet<>();
        SimilarityIndex.build(pastFixes, past -> past.getTitle() + " " + past.getDescription())
                .search(query, limit * 6, MINIMUM_SCORE / 2)
                .forEach(match -> {
                    Incident past = match.item();
                    // Best match first, so the first time a fix is seen is its best showing.
                    if (!alreadySuggested.add(sameFix(past.getResolution()))) {
                        return;
                    }
                    double score = match.score()
                            + bonus(past.getService().equals(incident.getService()),
                            past.getCategory() != null && past.getCategory().equals(incident.getCategory()))
                            - (past.getRating() != null && past.getRating() <= 2 ? POOR_RATING_PENALTY : 0);
                    found.add(new Recommendation(Recommendation.Source.PAST_INCIDENT, past.getCode(), null,
                            past.getCode(), past.getTitle(), past.getResolution(), percent(score),
                            match.sharedWords(), past.getRating(),
                            timesUsed.get(sameFix(past.getResolution())).intValue()));
                });

        return found.stream()
                .filter(recommendation -> recommendation.percent() >= Math.round(MINIMUM_SCORE * 100))
                .sorted(Comparator.comparingInt(Recommendation::percent).reversed())
                .limit(limit)
                .toList();
    }

    /** Knowledge articles that may answer what someone is typing, best first. */
    public List<SimilarityIndex.Match<KnowledgeArticle>> articlesFor(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return SimilarityIndex.build(articles.findAll(), KnowledgeArticle::searchText)
                .search(text, limit, MINIMUM_SCORE);
    }

    /** Two resolutions are the same fix when their words are the same, whatever the case and spacing. */
    private static String sameFix(String resolution) {
        return resolution.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static double bonus(boolean sameService, boolean sameCategory) {
        return (sameService ? SAME_SERVICE_BONUS : 0) + (sameCategory ? SAME_CATEGORY_BONUS : 0);
    }

    private static int percent(double score) {
        return (int) Math.round(Math.min(score, 0.99) * 100);
    }
}
