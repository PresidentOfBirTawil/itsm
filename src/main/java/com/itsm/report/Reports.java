package com.itsm.report;

import java.util.List;

import com.itsm.domain.Priority;

/**
 * The figures of the administrator's reports page, worked out by {@link ReportService}.
 *
 * @param total             how many incidents there are
 * @param byStatus          incidents per status, in lifecycle order
 * @param byPriority        incidents per priority
 * @param byCategory        incidents per category, most first
 * @param slaMet            incidents resolved without missing a deadline
 * @param slaBreached       incidents that missed a deadline
 * @param resolutionTimes   average time from report to resolution, per priority
 * @param technicians       one line per technician
 * @param averageRating     the average of all ratings given, or null if there are none
 * @param ratedCount        how many incidents have been rated
 */
public record Reports(long total, List<Count> byStatus, List<Count> byPriority, List<Count> byCategory,
                      long slaMet, long slaBreached, List<ResolutionTime> resolutionTimes,
                      List<TechnicianLine> technicians, Double averageRating, long ratedCount) {

    /** A label with a number, e.g. "Network: 7". */
    public record Count(String label, long count) {
    }

    /** How long incidents of one priority took to resolve, against the target of its SLA policy. */
    public record ResolutionTime(Priority priority, long resolved, long averageMinutes, Integer targetMinutes) {

        public boolean isWithinTarget() {
            return targetMinutes == null || averageMinutes <= targetMinutes;
        }
    }

    /** One technician's record. */
    public record TechnicianLine(String name, long assigned, long open, long resolved, long slaBreached,
                                 Double averageRating) {
    }

    /** The share of settled incidents (met or breached) that met their SLA, as a whole percentage. Null if none. */
    public Long slaCompliancePercent() {
        long settled = slaMet + slaBreached;
        return settled == 0 ? null : Math.round(slaMet * 100.0 / settled);
    }

    /** The largest count in a list, for scaling the bars. */
    public static long largest(List<Count> counts) {
        return counts.stream().mapToLong(Count::count).max().orElse(0);
    }
}
