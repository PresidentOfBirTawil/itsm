package com.itsm.intelligence;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.itsm.domain.Incident;
import org.springframework.stereotype.Component;

/**
 * Spots a new incident that is probably about the same problem as an earlier one.
 *
 * <p>How it decides: it compares the new incident with incidents that are
 * still open, affect the <b>same service</b> and were reported in the
 * <b>last 24 hours</b>. For each pair it takes the significant words of the
 * two titles (see {@link Words}) and calculates the Jaccard similarity:</p>
 *
 * <pre>
 *                 words the two titles share
 *   similarity = ----------------------------
 *                 words in either title
 * </pre>
 *
 * <p>At {@value #THRESHOLD} or more the earlier incident counts as a possible
 * duplicate. If several qualify, the most similar one is chosen.</p>
 */
@Component
public class DuplicateDetector {

    /** Two titles are "possibly the same" from this similarity upwards. */
    public static final double THRESHOLD = 0.4;

    /** Only incidents reported this recently are compared. */
    public static final Duration WINDOW = Duration.ofHours(24);

    /** The Jaccard similarity of two texts, from 0.0 to 1.0. Texts with no significant words are never similar. */
    public double similarity(String first, String second) {
        Set<String> a = Words.significantWords(first);
        Set<String> b = Words.significantWords(second);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        Set<String> either = new HashSet<>(a);
        either.addAll(b);
        return (double) shared.size() / either.size();
    }

    /**
     * The earlier incident the new one most probably duplicates, if any.
     *
     * @param candidates the incidents to compare with; ones that do not qualify are ignored
     */
    public Optional<DuplicateMatch> findDuplicate(Incident fresh, Collection<Incident> candidates) {
        return candidates.stream()
                .filter(candidate -> qualifies(fresh, candidate))
                .map(candidate -> new DuplicateMatch(candidate,
                        similarity(fresh.getTitle(), candidate.getTitle()),
                        Duration.between(candidate.getCreatedAt(), fresh.getCreatedAt())))
                .filter(match -> match.similarity() >= THRESHOLD)
                .max(Comparator.comparingDouble(DuplicateMatch::similarity)
                        // equally similar: prefer the more recent one
                        .thenComparing(match -> match.original().getCreatedAt()));
    }

    /** Still open, on the same service, reported earlier and within the last 24 hours. */
    private boolean qualifies(Incident fresh, Incident candidate) {
        if (candidate == fresh || !candidate.getStatus().isOpen()) {
            return false;
        }
        if (!candidate.getService().getName().equals(fresh.getService().getName())) {
            return false;
        }
        Duration age = Duration.between(candidate.getCreatedAt(), fresh.getCreatedAt());
        return !age.isNegative() && age.compareTo(WINDOW) <= 0;
    }
}
