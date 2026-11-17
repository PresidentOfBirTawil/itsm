package com.itsm.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.itsm.domain.ITService;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import org.springframework.stereotype.Component;

/**
 * Automated root-cause analysis: when several services fail at about the
 * same time, looks for one service they all depend on.
 *
 * <p>How it decides:</p>
 * <ol>
 *   <li>Only serious incidents count: open, P1 or P2, reported in the last
 *       {@value #WINDOW_HOURS} hours. Security incidents are left out: an
 *       attack is not caused by a failing service.</li>
 *   <li>For every service S, the affected set is the services among S and
 *       everything that depends on S (directly or not) that have such an
 *       incident.</li>
 *   <li>S is a suspect when its affected set has at least two services: one
 *       failure that explains several.</li>
 *   <li>The best suspect explains the most services; when two explain as
 *       many, the more specific one wins (fewer services depend on it), and a
 *       suspect with a serious incident of its own is preferred.</li>
 * </ol>
 */
@Component
public class RootCauseAnalyzer {

    static final int WINDOW_HOURS = 3;
    private static final String SECURITY = "Security";

    public Optional<RootCause> analyse(ServiceGraph graph, List<Incident> openIncidents, LocalDateTime now) {
        LocalDateTime since = now.minus(Duration.ofHours(WINDOW_HOURS));
        List<Incident> serious = openIncidents.stream()
                .filter(incident -> incident.getStatus().isOpen())
                .filter(incident -> incident.getPriority() == Priority.P1 || incident.getPriority() == Priority.P2)
                .filter(incident -> !incident.getCreatedAt().isBefore(since))
                .filter(incident -> incident.getCategory() == null
                        || !SECURITY.equalsIgnoreCase(incident.getCategory().getName()))
                .toList();
        if (serious.size() < 2) {
            return Optional.empty();
        }

        List<Suspect> suspects = new ArrayList<>();
        for (ITService candidate : graph.services()) {
            Set<ITService> reach = new LinkedHashSet<>(graph.downstream(candidate));
            reach.add(candidate);
            List<Incident> explained = serious.stream().filter(incident -> reach.contains(incident.getService())).toList();
            Set<ITService> affected = new LinkedHashSet<>();
            explained.forEach(incident -> affected.add(incident.getService()));
            if (affected.size() >= 2) {
                boolean confirmed = affected.contains(candidate);
                suspects.add(new Suspect(candidate, affected, explained, confirmed, reach.size()));
            }
        }
        return suspects.stream()
                .max(Comparator.<Suspect>comparingInt(suspect -> suspect.affected().size())
                        .thenComparing(Suspect::confirmed)
                        .thenComparing(Comparator.comparingInt(Suspect::reach).reversed())
                        .thenComparing(suspect -> suspect.service().getName(), Comparator.reverseOrder()))
                .map(suspect -> new RootCause(suspect.service(), List.copyOf(suspect.affected()),
                        suspect.incidents(), suspect.confirmed()));
    }

    private record Suspect(ITService service, Set<ITService> affected, List<Incident> incidents, boolean confirmed,
                           int reach) {
    }
}
