package com.quell.intelligence.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.quell.intelligence.Words;

/**
 * Finds the items whose text is most like a given text, the way a search
 * engine does: TF-IDF weighting and cosine similarity.
 *
 * <ul>
 *   <li><b>TF</b> (term frequency): a word that occurs often in a text says
 *       more about it, but with diminishing returns: weight 1 + ln(count).</li>
 *   <li><b>IDF</b> (inverse document frequency): a word found in nearly every
 *       text says little ("login" in a list of login problems); a rare word
 *       says a lot ("replication"). Weight ln((1 + N) / (1 + df)) + 1.</li>
 *   <li><b>Cosine similarity</b>: each text becomes a vector of word weights,
 *       and two texts are as similar as the angle between their vectors is
 *       small: 1 for the same words in the same proportions, 0 for no word in
 *       common. Long and short texts are compared fairly.</li>
 * </ul>
 *
 * <p>Generic, so the same index serves knowledge articles and past
 * incidents alike. It is built in memory, which is fast for the thousands of
 * texts a single organisation produces.</p>
 *
 * @param <T> what is being searched, e.g. a knowledge article
 */
public final class SimilarityIndex<T> {

    /** One search result: the item, how similar it is (0 to 1), and the words it shares with the query. */
    public record Match<T>(T item, double score, List<String> sharedWords) {

        /** The score as a percentage, for showing to people. */
        public int percent() {
            return (int) Math.round(score * 100);
        }
    }

    private final List<T> items;
    private final List<Map<String, Double>> vectors;
    private final Map<String, Double> inverseDocumentFrequency;

    private SimilarityIndex(List<T> items, List<Map<String, Double>> vectors, Map<String, Double> idf) {
        this.items = items;
        this.vectors = vectors;
        this.inverseDocumentFrequency = idf;
    }

    /**
     * Builds an index.
     *
     * @param items the items to search
     * @param text  how to get the text of an item
     */
    public static <T> SimilarityIndex<T> build(Collection<T> items, Function<T, String> text) {
        List<T> list = new ArrayList<>(items);
        List<Map<String, Integer>> counts = list.stream().map(item -> countWords(text.apply(item))).toList();

        Map<String, Integer> documentFrequency = new HashMap<>();
        counts.forEach(count -> count.keySet().forEach(word -> documentFrequency.merge(word, 1, Integer::sum)));
        int n = list.size();
        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((word, df) -> idf.put(word, Math.log((1.0 + n) / (1.0 + df)) + 1.0));

        List<Map<String, Double>> vectors = counts.stream().map(count -> vector(count, idf)).toList();
        return new SimilarityIndex<>(list, vectors, idf);
    }

    /**
     * The items most similar to a text, best first.
     *
     * @param minimumScore leave out items less similar than this (0 to 1)
     */
    public List<Match<T>> search(String query, int limit, double minimumScore) {
        Map<String, Double> queryVector = vector(countWords(query), inverseDocumentFrequency);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        List<Match<T>> matches = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Double> candidate = vectors.get(i);
            double score = 0;
            Map<String, Double> contributions = new HashMap<>();
            for (Map.Entry<String, Double> entry : queryVector.entrySet()) {
                Double weight = candidate.get(entry.getKey());
                if (weight != null) {
                    double contribution = weight * entry.getValue();
                    score += contribution;
                    contributions.put(entry.getKey(), contribution);
                }
            }
            if (score >= minimumScore && score > 0) {
                List<String> shared = contributions.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .toList();
                matches.add(new Match<>(items.get(i), Math.min(score, 1.0), shared));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble((Match<T> match) -> match.score()).reversed())
                .limit(limit)
                .toList();
    }

    /** How many items are in the index. */
    public int size() {
        return items.size();
    }

    private static Map<String, Integer> countWords(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Words.tokens(text).forEach(word -> counts.merge(word, 1, Integer::sum));
        return counts;
    }

    /** TF-IDF weights scaled to length 1, so that the dot product of two vectors is their cosine. */
    private static Map<String, Double> vector(Map<String, Integer> counts, Map<String, Double> idf) {
        Map<String, Double> vector = new HashMap<>();
        counts.forEach((word, count) -> {
            Double inverse = idf.get(word);
            if (inverse != null) {
                vector.put(word, (1 + Math.log(count)) * inverse);
            }
        });
        double length = Math.sqrt(vector.values().stream().mapToDouble(weight -> weight * weight).sum());
        if (length > 0) {
            vector.replaceAll((word, weight) -> weight / length);
        }
        return vector;
    }
}
