package com.itsm.intelligence;

import java.time.Duration;
import java.util.Locale;

import com.itsm.domain.Incident;

/**
 * An earlier incident that a new one looks like, with the evidence.
 *
 * @param original   the earlier incident
 * @param similarity how alike the two titles are: 0.0 (nothing in common) to 1.0 (the same words)
 * @param apart      the time between the two reports
 */
public record DuplicateMatch(Incident original, double similarity, Duration apart) {

    /** Rebuilds the match from the link stored on an incident, or returns null if it has none. */
    public static DuplicateMatch of(Incident duplicate) {
        Incident original = duplicate.getPossibleDuplicateOf();
        if (original == null) {
            return null;
        }
        return new DuplicateMatch(original, duplicate.getDuplicateSimilarity(),
                Duration.between(original.getCreatedAt(), duplicate.getCreatedAt()).abs());
    }

    /** The evidence in brackets, e.g. "(similarity 0.62, same service, 6 min apart)". */
    public String evidence() {
        return "(similarity %s, same service, %s apart)".formatted(
                String.format(Locale.ROOT, "%.2f", similarity), describe(apart));
    }

    /** The whole sentence, e.g. "Possible duplicate of INC-0007 (similarity 0.62, same service, 6 min apart)". */
    public String explain() {
        return "Possible duplicate of %s %s".formatted(original.getCode(), evidence());
    }

    private static String describe(Duration time) {
        long minutes = time.toMinutes();
        if (minutes < 1) {
            return "less than a minute";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        return minutes % 60 == 0 ? hours + " h" : hours + " h " + minutes % 60 + " min";
    }
}
