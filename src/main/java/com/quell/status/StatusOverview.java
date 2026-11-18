package com.quell.status;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the status page shows.
 *
 * @param services  every service, the ones in trouble first
 * @param rootCause the probable root cause of the current problems, or null
 * @param map       the dependency map, laid out for drawing
 * @param asOf      when this was worked out
 */
public record StatusOverview(List<ServiceStatus> services, RootCause rootCause, ServiceMap map, LocalDateTime asOf) {

    public ServiceHealth worst() {
        return services.stream().map(ServiceStatus::health).reduce(ServiceHealth.OPERATIONAL, ServiceHealth::worst);
    }

    /** The headline: "All systems operational", or what is wrong. */
    public String headline() {
        return switch (worst()) {
            case OPERATIONAL -> "All systems operational";
            case DEGRADED -> "Some services are degraded";
            case PARTIAL_OUTAGE -> "Partial outage";
            case MAJOR_OUTAGE -> "Major outage in progress";
        };
    }

    public long problemCount() {
        return services.stream().filter(status -> status.health().isProblem()).count();
    }
}
