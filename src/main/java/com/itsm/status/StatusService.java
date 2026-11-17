package com.itsm.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itsm.domain.ITService;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.Status;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service health for the status page, the dashboard and the report form:
 * how each service is doing, what people should know before reporting, and
 * the probable root cause when several services fail together.
 */
@Service
@Transactional(readOnly = true)
public class StatusService {

    static final int MAP_WIDTH = 720;

    private final ITServiceRepository services;
    private final IncidentRepository incidents;
    private final RootCauseAnalyzer rootCauseAnalyzer;

    public StatusService(ITServiceRepository services, IncidentRepository incidents, RootCauseAnalyzer rootCauseAnalyzer) {
        this.services = services;
        this.incidents = incidents;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
    }

    /** The whole picture, as of now. */
    public StatusOverview overview() {
        LocalDateTime now = LocalDateTime.now();
        ServiceGraph graph = new ServiceGraph(services.findAll());
        List<Incident> open = incidents.findByStatusIn(Status.openStatuses());

        Map<ITService, ServiceHealth> ownHealth = new LinkedHashMap<>();
        graph.services().forEach(service -> ownHealth.put(service, ServiceHealth.OPERATIONAL));
        open.forEach(incident -> ownHealth.computeIfPresent(incident.getService(),
                (service, health) -> health.worst(ServiceHealth.of(incident))));

        Map<ITService, ServiceStatus> statuses = new LinkedHashMap<>();
        for (ITService service : graph.services()) {
            List<ITService> troubled = graph.upstream(service).stream()
                    .filter(upstream -> ownHealth.get(upstream).isOutage())
                    .toList();
            List<Incident> own = open.stream().filter(incident -> incident.getService().equals(service))
                    .sorted(MOST_SERIOUS_FIRST).toList();
            List<Incident> known = own.stream()
                    .filter(incident -> incident.getPriority() == Priority.P1 || incident.getPriority() == Priority.P2)
                    .toList();
            statuses.put(service, new ServiceStatus(service, ownHealth.get(service), troubled, own, known));
        }
        Optional<RootCause> rootCause = rootCauseAnalyzer.analyse(graph, open, now);
        List<ServiceStatus> ordered = new ArrayList<>(statuses.values());
        ordered.sort(Comparator.comparing((ServiceStatus status) -> status.health().ordinal()).reversed()
                .thenComparing(status -> !status.isAtRisk())
                .thenComparing(status -> status.service().getName()));
        return new StatusOverview(ordered, rootCause.orElse(null), ServiceMap.of(graph, statuses, MAP_WIDTH), now);
    }

    /**
     * What someone reporting a problem with this service should know first:
     * serious open incidents on it and on the services it needs.
     */
    public List<Incident> knownIssuesFor(Long serviceId) {
        if (serviceId == null) {
            return List.of();
        }
        return services.findById(serviceId).map(service -> {
            ServiceGraph graph = new ServiceGraph(services.findAll());
            List<ITService> relevant = new ArrayList<>(graph.upstream(service));
            relevant.add(0, service);
            return incidents.findByStatusIn(Status.openStatuses()).stream()
                    .filter(incident -> relevant.contains(incident.getService()))
                    .filter(incident -> incident.getPriority() == Priority.P1 || incident.getPriority() == Priority.P2)
                    // A security incident elsewhere (an attack) does not explain this service's trouble.
                    .filter(incident -> incident.getService().equals(service) || !isSecurity(incident))
                    // This service's own problems first, then those of the services it needs.
                    .sorted(Comparator.comparing((Incident incident) -> !incident.getService().equals(service))
                            .thenComparing(MOST_SERIOUS_FIRST))
                    .limit(3)
                    .toList();
        }).orElse(List.of());
    }

    private static boolean isSecurity(Incident incident) {
        return incident.getCategory() != null && "Security".equalsIgnoreCase(incident.getCategory().getName());
    }

    static final Comparator<Incident> MOST_SERIOUS_FIRST = Comparator
            .comparing((Incident incident) -> incident.getPriority() == null ? 9 : incident.getPriority().ordinal())
            .thenComparing(Incident::getCreatedAt, Comparator.reverseOrder());
}
