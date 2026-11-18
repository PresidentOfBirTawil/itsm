package com.quell.status;

import java.util.List;

import com.quell.domain.ITService;
import com.quell.domain.Incident;

/**
 * One service on the status page.
 *
 * @param service       the service
 * @param health        how it is doing, from its own open incidents
 * @param upstreamTrouble services it needs that have an outage now, so it may be affected too
 * @param openIncidents its own open incidents, most serious first
 * @param knownIssues   its open P1 and P2 incidents: what people should know about before reporting
 */
public record ServiceStatus(ITService service, ServiceHealth health, List<ITService> upstreamTrouble,
                            List<Incident> openIncidents, List<Incident> knownIssues) {

    /** True if it is fine itself but may suffer from a service it needs. */
    public boolean isAtRisk() {
        return !health.isProblem() && !upstreamTrouble.isEmpty();
    }

    /** For the badge: its own health, or "At risk" when only a service it needs is in trouble. */
    public String getLabel() {
        return isAtRisk() ? "At risk" : health.getLabel();
    }

    public String getTone() {
        return isAtRisk() ? "warning" : health.getTone();
    }
}
