package com.itsm.intelligence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.itsm.domain.Incident;
import com.itsm.domain.SlaTarget;
import org.springframework.stereotype.Component;

/**
 * Works out which open incidents are most likely to miss a deadline next.
 *
 * <p>How it decides: for each open incident it takes the next deadline that
 * is still running: the response deadline until a technician acknowledges,
 * the resolution deadline after that. Incidents whose clock is paused, or
 * whose next deadline has already been missed, are left out: there is
 * nothing left to prevent. The rest are sorted by time remaining, least
 * first, because the incident with the least time left is the one that
 * breaches next if nobody acts.</p>
 */
@Component
public class SlaRiskRanker {

    public List<SlaRisk> rank(Collection<Incident> openIncidents, LocalDateTime now, int limit) {
        return openIncidents.stream()
                .map(incident -> nextDeadline(incident, now))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SlaRisk::remaining)
                        .thenComparing(risk -> risk.incident().getCreatedAt()))
                .limit(limit)
                .toList();
    }

    private SlaRisk nextDeadline(Incident incident, LocalDateTime now) {
        if (incident.getPriority() == null || !incident.getStatus().isOpen()) {
            return null; // no deadlines yet, or the work is done
        }
        SlaTarget response = incident.responseTarget();
        SlaTarget resolution = incident.resolutionTarget();
        SlaTarget next = response.isRunning() && !response.isLate(now) ? response
                : resolution.isRunning() && !resolution.isLate(now) ? resolution : null;
        return next == null ? null
                : new SlaRisk(incident, next.name(), next.fractionUsed(now), next.remaining(now));
    }
}
