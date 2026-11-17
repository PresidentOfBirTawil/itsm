package com.itsm.intelligence;

import java.util.List;

/**
 * A suggested solution for an incident: a knowledge article or the
 * resolution of a similar past incident.
 *
 * @param source       where it comes from
 * @param reference    KB-0012 or INC-0007
 * @param articleId    the article, for {@link Source#ARTICLE}
 * @param incidentCode the past incident, for {@link Source#PAST_INCIDENT}
 * @param title        the article's or past incident's title
 * @param solution     what fixed it
 * @param percent      how similar it is, 0 to 100
 * @param sharedWords  the words the two have in common, most telling first
 * @param feedback     for an article the share of "helpful" votes, for an incident its rating (1 to 5); may be null
 * @param timesUsed    for a past fix, how many finished incidents were resolved with the same words (at least 1)
 */
public record Recommendation(Source source, String reference, Long articleId, String incidentCode, String title,
                             String solution, int percent, List<String> sharedWords, Integer feedback,
                             int timesUsed) {

    public enum Source {
        ARTICLE("Knowledge base"),
        PAST_INCIDENT("Past incident");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** The four most telling shared words, for showing. */
    public List<String> topWords() {
        return sharedWords.stream().limit(4).toList();
    }

    public boolean isArticle() {
        return source == Source.ARTICLE;
    }
}
