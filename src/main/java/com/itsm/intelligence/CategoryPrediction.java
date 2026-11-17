package com.itsm.intelligence;

import java.util.List;

/**
 * What the learning classifier thinks the category of a text is.
 *
 * @param categoryId   the predicted category
 * @param categoryName its name
 * @param confidence   the probability, 0 to 1
 * @param evidence     the words that pointed to it most strongly
 * @param trainedOn    how many examples the classifier learned from
 */
public record CategoryPrediction(Long categoryId, String categoryName, double confidence, List<String> evidence,
                                 int trainedOn) {

    public int percent() {
        return (int) Math.round(confidence * 100);
    }

    /** E.g. "Category Network was suggested by the classifier (82% sure, learned from 120 examples) because of: vpn, wifi." */
    public String explain() {
        return "Category %s was suggested by the learning classifier (%d%% sure, learned from %d examples)%s."
                .formatted(categoryName, percent(), trainedOn,
                        evidence.isEmpty() ? "" : " because of the words: " + String.join(", ", evidence));
    }
}
