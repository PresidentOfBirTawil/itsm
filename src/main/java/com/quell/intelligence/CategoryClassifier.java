package com.quell.intelligence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.quell.domain.Category;
import com.quell.domain.IncidentAction;
import com.quell.domain.KnowledgeArticle;
import com.quell.event.IncidentEvent;
import com.quell.event.IncidentEventListener;
import com.quell.intelligence.text.NaiveBayesClassifier;
import com.quell.intelligence.text.NaiveBayesClassifier.Example;
import com.quell.repository.CategoryRepository;
import com.quell.repository.IncidentRepository;
import com.quell.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Component;

/**
 * AI-assisted classification: a naive Bayes classifier that learns the
 * category of an incident from the words of every incident filed before it.
 *
 * <p>What it learns from:</p>
 * <ul>
 *   <li>the keywords of each category (so it works from day one),</li>
 *   <li>every incident that has a category, with its title and description,</li>
 *   <li>the knowledge-base articles that have a category.</li>
 * </ul>
 *
 * <p>It keeps learning. When a technician corrects the category of an
 * incident, the corrected incident is an example the next time the
 * classifier is built. It is rebuilt when an incident is reported or
 * reclassified (it listens for those events, the Observer pattern again) or
 * when an article changes, but only when it is next asked, so a burst of
 * reports costs one rebuild.</p>
 *
 * <p>The keyword rules ({@link CategorySuggester}) stay the first choice,
 * because they explain themselves in one line. The classifier is used when
 * the rules find nothing, and on the incident page to point out an incident
 * that may have been filed under the wrong category.</p>
 */
@Component
public class CategoryClassifier implements IncidentEventListener {

    /** Below this probability the classifier does not choose a category on its own. */
    public static final double CONFIDENT = 0.5;

    /** A category's keywords count as this many examples each, so that a few incidents do not drown them out. */
    private static final double KEYWORD_WEIGHT = 2.0;

    private final CategoryRepository categories;
    private final IncidentRepository incidents;
    private final KnowledgeArticleRepository articles;

    private NaiveBayesClassifier<Long> model;
    private Map<Long, String> names = Map.of();
    private boolean stale = true;

    public CategoryClassifier(CategoryRepository categories, IncidentRepository incidents,
                              KnowledgeArticleRepository articles) {
        this.categories = categories;
        this.incidents = incidents;
        this.articles = articles;
    }

    /** The most likely category of a text, if the classifier has anything to go on. */
    public Optional<CategoryPrediction> predict(String text) {
        return rank(text, 1).stream().findFirst();
    }

    /** The most likely categories of a text, best first. */
    public synchronized List<CategoryPrediction> rank(String text, int limit) {
        NaiveBayesClassifier<Long> classifier = model();
        int trainedOn = (int) Math.round(classifier.exampleCount());
        return classifier.predict(text).stream()
                .limit(limit)
                .map(prediction -> new CategoryPrediction(prediction.label(), names.get(prediction.label()),
                        prediction.probability(), prediction.evidence(), trainedOn))
                .toList();
    }

    /** How many examples the classifier currently learns from. */
    public synchronized int trainingSize() {
        return (int) Math.round(model().exampleCount());
    }

    /** New examples: rebuild on the next question. */
    public synchronized void markStale() {
        stale = true;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        if (event.action() == IncidentAction.REPORTED || event.action() == IncidentAction.CLASSIFICATION_CHANGED) {
            markStale();
        }
    }

    private NaiveBayesClassifier<Long> model() {
        if (stale || model == null) {
            List<Example<Long>> examples = new ArrayList<>();
            Map<Long, String> categoryNames = new HashMap<>();
            for (Category category : categories.findAll()) {
                categoryNames.put(category.getId(), category.getName());
                examples.add(new Example<>(category.getName(), category.getId(), KEYWORD_WEIGHT));
                category.getKeywords().forEach(keyword ->
                        examples.add(new Example<>(keyword, category.getId(), KEYWORD_WEIGHT)));
            }
            incidents.findByCategoryIsNotNull().forEach(incident -> examples.add(new Example<>(
                    incident.getTitle() + " " + incident.getDescription(), incident.getCategory().getId())));
            for (KnowledgeArticle article : articles.findAll()) {
                if (article.getCategory() != null) {
                    // The solution names the cause in a technician's words ("standby", "index"), which helps too.
                    examples.add(new Example<>(article.searchText() + " " + article.getSolution(),
                            article.getCategory().getId()));
                }
            }
            model = new NaiveBayesClassifier<>(examples);
            names = categoryNames;
            stale = false;
        }
        return model;
    }
}
