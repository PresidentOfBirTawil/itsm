package com.itsm.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One of an incident's two SLA deadlines (response or resolution), with the
 * arithmetic that goes with it. It is a snapshot built from the incident's
 * fields; it stores nothing itself.
 *
 * @param name        "response" or "resolution"
 * @param start       when the clock started (the incident was reported)
 * @param due         the deadline. For a clock that has been paused and resumed it already
 *                    includes the time added for those pauses.
 * @param metAt       when the target was met (acknowledged or resolved), or null if not yet
 * @param paused      how long the clock has stood still in pauses that are over
 * @param pausedSince when the current pause began, or null if the clock is not paused now
 */
public record SlaTarget(String name, LocalDateTime start, LocalDateTime due, LocalDateTime metAt,
                        Duration paused, LocalDateTime pausedSince) {

    /**
     * What the clock shows at {@code now}. A clock that has been met stopped
     * at that moment; a paused clock stands still at the moment it was paused.
     */
    public LocalDateTime clockTime(LocalDateTime now) {
        if (metAt != null) {
            return metAt;
        }
        return pausedSince != null ? pausedSince : now;
    }

    public boolean isMet() {
        return metAt != null;
    }

    /** True while the clock is ticking: not met yet and not paused. */
    public boolean isRunning() {
        return metAt == null && pausedSince == null;
    }

    /** The working time the SLA allows: from start to deadline, not counting pauses. */
    public Duration allowed() {
        return Duration.between(start, due).minus(paused);
    }

    /** The working time used so far, not counting pauses. */
    public Duration used(LocalDateTime now) {
        Duration used = Duration.between(start, clockTime(now)).minus(paused);
        return used.isNegative() ? Duration.ZERO : used;
    }

    /** The share of the allowed time that has been used: 0.0 at the start, 1.0 at the deadline, more after it. */
    public double fractionUsed(LocalDateTime now) {
        long allowedSeconds = allowed().getSeconds();
        if (allowedSeconds <= 0) {
            return 1.0;
        }
        return (double) used(now).getSeconds() / allowedSeconds;
    }

    /** The time left until the deadline. Negative once the deadline has passed. */
    public Duration remaining(LocalDateTime now) {
        return Duration.between(clockTime(now), due);
    }

    /** True if the deadline was missed: it passed before the target was met, or it was met too late. */
    public boolean isLate(LocalDateTime now) {
        return clockTime(now).isAfter(due);
    }
}
