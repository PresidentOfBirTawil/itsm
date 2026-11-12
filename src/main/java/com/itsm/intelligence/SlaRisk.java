package com.itsm.intelligence;

import java.time.Duration;

import com.itsm.domain.Incident;

/**
 * One line of the SLA risk list: an open incident and its next deadline.
 *
 * @param incident     the incident
 * @param target       which deadline comes next: "response" or "resolution"
 * @param fractionUsed the share of that deadline's time already used (0.0 to 1.0)
 * @param remaining    the time left until the deadline
 */
public record SlaRisk(Incident incident, String target, double fractionUsed, Duration remaining) {

    public long percentUsed() {
        return Math.round(fractionUsed * 100);
    }

    /** E.g. "Response due in 3 min · 40% of the time used · nobody has acknowledged it yet". */
    public String explain() {
        String text = "%s due in %s · %d%% of the time used".formatted(
                target.substring(0, 1).toUpperCase() + target.substring(1), describe(remaining), percentUsed());
        if ("response".equals(target)) {
            text += incident.getAssignee() == null
                    ? " · no technician assigned"
                    : " · not acknowledged yet";
        }
        return text;
    }

    private static String describe(Duration time) {
        long seconds = Math.max(0, time.getSeconds());
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        return minutes % 60 == 0 ? minutes / 60 + " h" : minutes / 60 + " h " + minutes % 60 + " min";
    }
}
