package com.itsm.intelligence.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itsm.intelligence.Words;

/**
 * A multinomial naive Bayes text classifier: the classic machine-learning
 * method behind spam filters. It learns from labelled examples (texts whose
 * label is known) how likely each word is under each label, and labels a new
 * text with the label that best explains its words.
 *
 * <pre>
 * P(label | words)  ∝  P(label) × Π P(word | label)
 *
 * P(label)          how common the label is among the examples
 * P(word | label)   (times the word occurs under the label + 1) / (words under the label + vocabulary size)
 * </pre>
 *
 * <p>The "+ 1" (Laplace smoothing) keeps a word that never occurred under a
 * label from ruling that label out completely. The sums are done with
 * logarithms so that many small probabilities do not round to zero, and the
 * scores are turned back into probabilities that add up to 1.</p>
 *
 * <p>Immutable: build a new classifier to learn from new examples.</p>
 *
 * @param <L> the label, e.g. a category id
 */
public final class NaiveBayesClassifier<L> {

    /** One labelled example. A weight above 1 counts the example several times. */
    public record Example<L>(String text, L label, double weight) {

        public Example(String text, L label) {
            this(text, label, 1.0);
        }
    }

    /**
     * A label with its probability, and the words of the text that pointed to
     * it most strongly (the evidence), strongest first.
     */
    public record Prediction<L>(L label, double probability, List<String> evidence) {

        public int percent() {
            return (int) Math.round(probability * 100);
        }
    }

    private static final double SMOOTHING = 1.0;
    private static final int EVIDENCE_WORDS = 3;

    private final Map<L, Double> examplesPerLabel = new LinkedHashMap<>();
    private final Map<L, Map<String, Double>> wordCounts = new LinkedHashMap<>();
    private final Map<L, Double> wordTotals = new LinkedHashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private double exampleTotal;

    /** Learns from the examples. Examples without words are ignored. */
    public NaiveBayesClassifier(List<Example<L>> examples) {
        for (Example<L> example : examples) {
            List<String> words = Words.tokens(example.text());
            if (words.isEmpty() || example.label() == null || example.weight() <= 0) {
                continue;
            }
            examplesPerLabel.merge(example.label(), example.weight(), Double::sum);
            exampleTotal += example.weight();
            Map<String, Double> counts = wordCounts.computeIfAbsent(example.label(), label -> new HashMap<>());
            for (String word : words) {
                counts.merge(word, example.weight(), Double::sum);
                wordTotals.merge(example.label(), example.weight(), Double::sum);
                vocabulary.add(word);
            }
        }
    }

    /** How many examples (counted with their weights) it learned from. */
    public double exampleCount() {
        return exampleTotal;
    }

    /** The labels it knows. */
    public Set<L> labels() {
        return examplesPerLabel.keySet();
    }

    /**
     * The labels for a text, most probable first. Empty if none of the text's
     * words occurred in any example: then the classifier has nothing to go
     * on, and saying so is better than guessing.
     */
    public List<Prediction<L>> predict(String text) {
        List<String> known = Words.tokens(text).stream().filter(vocabulary::contains).toList();
        if (known.isEmpty() || examplesPerLabel.isEmpty()) {
            return List.of();
        }
        Map<L, Double> logScores = new LinkedHashMap<>();
        int labelCount = examplesPerLabel.size();
        for (L label : examplesPerLabel.keySet()) {
            double score = Math.log((examplesPerLabel.get(label) + SMOOTHING) / (exampleTotal + SMOOTHING * labelCount));
            for (String word : known) {
                score += Math.log(wordProbability(word, label));
            }
            logScores.put(label, score);
        }
        // Back from logarithms to probabilities that add up to 1 (subtracting the maximum avoids overflow).
        double max = logScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<L, Double> probabilities = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<L, Double> entry : logScores.entrySet()) {
            double value = Math.exp(entry.getValue() - max);
            probabilities.put(entry.getKey(), value);
            sum += value;
        }
        List<Prediction<L>> predictions = new ArrayList<>();
        for (Map.Entry<L, Double> entry : probabilities.entrySet()) {
            predictions.add(new Prediction<>(entry.getKey(), entry.getValue() / sum, evidence(known, entry.getKey())));
        }
        predictions.sort(Comparator.comparingDouble((Prediction<L> prediction) -> prediction.probability()).reversed());
        return predictions;
    }

    /** The probability of a word under a label, smoothed. */
    private double wordProbability(String word, L label) {
        double count = wordCounts.getOrDefault(label, Map.of()).getOrDefault(word, 0.0);
        return (count + SMOOTHING) / (wordTotals.getOrDefault(label, 0.0) + SMOOTHING * vocabulary.size());
    }

    /** The words that favour this label most compared with the average of the others. */
    private List<String> evidence(List<String> words, L label) {
        Map<String, Double> strength = new HashMap<>();
        for (String word : new HashSet<>(words)) {
            double here = wordProbability(word, label);
            double elsewhere = examplesPerLabel.keySet().stream().filter(other -> !other.equals(label))
                    .mapToDouble(other -> wordProbability(word, other)).average().orElse(here);
            double ratio = Math.log(here / elsewhere);
            if (ratio > 0) {
                strength.put(word, ratio);
            }
        }
        return strength.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(EVIDENCE_WORDS)
                .map(Map.Entry::getKey)
                .toList();
    }
}
