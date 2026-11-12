package com.itsm.intelligence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turns a piece of text into the set of words that say what it is about.
 *
 * <p>Common words are thrown away. That means ordinary filler ("the", "to",
 * "is"), but also the words nearly every incident report contains: words that
 * say <em>that</em> something is wrong rather than <em>what</em> ("unable",
 * "failing", "unavailable", "error") and general IT words ("application",
 * "system"). What is left is what tells two reports apart, or shows that
 * they are about the same thing.</p>
 */
public final class Words {

    private static final Set<String> COMMON_WORDS = Set.of(
            // filler
            "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "at", "for", "from", "with", "by", "as",
            "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that", "these", "those",
            "i", "we", "you", "he", "she", "they", "my", "our", "your", "me", "us", "am",
            "do", "does", "did", "doing", "have", "has", "had", "can", "could", "will", "would", "should", "may",
            "when", "while", "after", "before", "again", "still", "very", "some", "any", "all", "every",
            "there", "here", "so", "if", "then", "than", "too", "also", "just", "now", "since", "into", "out",
            "get", "gets", "getting", "got", "make", "makes", "making", "made", "try", "tries", "trying", "tried",
            "keep", "keeps", "please", "help", "urgent", "asap",
            // "something is wrong" words
            "not", "no", "cannot", "cant", "unable", "unavailable", "available", "fail", "fails", "failing",
            "failed", "failure", "down", "broken", "error", "errors", "issue", "issues", "problem", "problems",
            "work", "works", "working", "wrong",
            // general IT words
            "application", "applications", "app", "apps", "system", "systems", "service", "services");

    private Words() {
    }

    /**
     * The significant words of a text: lower case, without punctuation,
     * without common words, and with a plural "s" removed so that "payments"
     * and "payment" count as the same word.
     */
    public static Set<String> significantWords(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text == null) {
            return words;
        }
        Arrays.stream(text.toLowerCase().replace("'", "").split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .filter(word -> !COMMON_WORDS.contains(word))
                .map(Words::singular)
                .filter(word -> !COMMON_WORDS.contains(word))
                .forEach(words::add);
        return words;
    }

    private static String singular(String word) {
        boolean plural = word.length() > 3 && word.endsWith("s") && !word.endsWith("ss");
        return plural ? word.substring(0, word.length() - 1) : word;
    }
}
