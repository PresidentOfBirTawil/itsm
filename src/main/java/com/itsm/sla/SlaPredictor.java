package com.itsm.sla;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaTarget;
import com.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Component;

/**
 * Predictive SLA violation detection. Learns from how long past incidents
 * took to resolve and predicts, for an open incident, when it will probably
 * be resolved and the chance that it misses its resolution deadline.
 *
 * <p>How it works, step by step:</p>
 * <ol>
 *   <li><b>Similar past incidents.</b> Resolved incidents with the same
 *       priority and category; if there are fewer than {@value #MINIMUM_SAMPLE},
 *       the same priority only; if still too few, no prediction.</li>
 *   <li><b>The technician.</b> Some technicians are quicker than others. Each
 *       past duration is multiplied by how fast the assigned technician
 *       usually is compared with everyone (their typical duration divided by
 *       the typical duration for the same priority, kept between 0.6 and 1.6).</li>
 *   <li><b>What has already happened.</b> The incident has been open for a
 *       while, so only past incidents that took longer than that are
 *       relevant (a conditional probability). The expected resolution is the
 *       median of those; the chance of a breach is the share of them that
 *       took longer than the deadline allows.</li>
 * </ol>
 *
 * <p>Times are working times: periods when the SLA clock was paused are left
 * out, as the deadline does.</p>
 */
@Component
public class SlaPredictor {

    static final int MINIMUM_SAMPLE = 4;
    private static final int MINIMUM_TECHNICIAN_SAMPLE = 3;
    private static final double FASTEST_FACTOR = 0.6;
    private static final double SLOWEST_FACTOR = 1.6;

    private final IncidentRepository incidents;

    public SlaPredictor(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    /** The forecast for one incident, if there is enough history to make one. */
    public Optional<SlaForecast> forecast(Incident incident, LocalDateTime now) {
        return forecast(incident, incidents.findByResolvedAtIsNotNull(), now);
    }

    /** Forecasts for several incidents, reading the history once. Incidents without a forecast are left out. */
    public Map<String, SlaForecast> forecastAll(List<Incident> open, LocalDateTime now) {
        List<Incident> history = incidents.findByResolvedAtIsNotNull();
        Map<String, SlaForecast> forecasts = new HashMap<>();
        open.forEach(incident -> forecast(incident, history, now)
                .ifPresent(forecast -> forecasts.put(incident.getCode(), forecast)));
        return forecasts;
    }

    /** The forecast against a given history. Separate so that it can be tested without a database. */
    public Optional<SlaForecast> forecast(Incident incident, List<Incident> history, LocalDateTime now) {
        Priority priority = incident.getPriority();
        if (priority == null || !incident.getStatus().isOpen() || incident.getResolutionDue() == null) {
            return Optional.empty();
        }
        List<Incident> resolved = history.stream()
                .filter(past -> past.getResolvedAt() != null && past.getPriority() != null)
                .filter(past -> !past.getId().equals(incident.getId()))
                .toList();

        List<Incident> samePriority = resolved.stream().filter(past -> past.getPriority() == priority).toList();
        List<Incident> sameCategory = samePriority.stream()
                .filter(past -> incident.getCategory() != null && incident.getCategory().equals(past.getCategory()))
                .toList();
        List<Incident> sample;
        String basis;
        if (sameCategory.size() >= MINIMUM_SAMPLE) {
            sample = sameCategory;
            basis = "%d resolved %s %s incidents".formatted(sample.size(), priority, incident.getCategory().getName());
        } else if (samePriority.size() >= MINIMUM_SAMPLE) {
            sample = samePriority;
            basis = "%d resolved %s incidents".formatted(sample.size(), priority);
        } else {
            return Optional.empty();
        }

        double factor = technicianFactor(incident, resolved);
        if (factor != 1.0) {
            basis += ", adjusted for %s's usual speed".formatted(incident.getAssignee().getName());
        }

        SlaTarget target = incident.resolutionTarget();
        double elapsed = target.used(now).toSeconds() / 60.0;
        double allowed = target.allowed().toSeconds() / 60.0;

        List<Double> stillGoing = new ArrayList<>();
        for (Incident past : sample) {
            double minutes = workingMinutes(past) * factor;
            if (minutes > elapsed) {
                stillGoing.add(minutes);
            }
        }

        double expectedTotal;
        int breachPercent;
        if (stillGoing.isEmpty()) {
            // It has already taken longer than every similar incident: it is late, or about to be.
            expectedTotal = elapsed + 15;
            breachPercent = elapsed >= allowed ? 100 : 75;
        } else {
            expectedTotal = median(stillGoing);
            long late = stillGoing.stream().filter(minutes -> minutes > allowed).count();
            breachPercent = (int) Math.round(late * 100.0 / stillGoing.size());
        }
        if (incident.getResolutionBreachedAt() != null) {
            breachPercent = 100;
        }
        LocalDateTime expected = now.plusSeconds(Math.round((expectedTotal - elapsed) * 60));
        return Optional.of(new SlaForecast(expected, breachPercent, sample.size(), basis));
    }

    /**
     * How fast the assigned technician usually is: the median, over their
     * own resolved incidents, of their duration divided by the median for the
     * same priority. 1.0 if there is too little to tell.
     */
    private double technicianFactor(Incident incident, List<Incident> resolved) {
        if (incident.getAssignee() == null) {
            return 1.0;
        }
        Map<Priority, Double> typical = new HashMap<>();
        for (Priority priority : Priority.values()) {
            List<Double> minutes = resolved.stream().filter(past -> past.getPriority() == priority)
                    .map(SlaPredictor::workingMinutes).toList();
            if (!minutes.isEmpty()) {
                typical.put(priority, Math.max(median(minutes), 1.0));
            }
        }
        List<Double> ratios = resolved.stream()
                .filter(past -> incident.getAssignee().equals(past.getAssignee()))
                .map(past -> workingMinutes(past) / typical.get(past.getPriority()))
                .toList();
        if (ratios.size() < MINIMUM_TECHNICIAN_SAMPLE) {
            return 1.0;
        }
        double factor = Math.max(FASTEST_FACTOR, Math.min(SLOWEST_FACTOR, median(ratios)));
        return Math.abs(factor - 1.0) < 0.05 ? 1.0 : factor;
    }

    /** From report to resolution, without the time the clock was paused. */
    static double workingMinutes(Incident past) {
        double minutes = Duration.between(past.getCreatedAt(), past.getResolvedAt()).toSeconds() / 60.0
                - past.getSlaPausedSeconds() / 60.0;
        return Math.max(minutes, 0);
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
