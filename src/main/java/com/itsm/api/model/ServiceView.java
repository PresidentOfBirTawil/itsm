package com.itsm.api.model;

import java.util.List;

import com.itsm.domain.ITService;
import com.itsm.status.ServiceHealth;
import com.itsm.status.ServiceStatus;

/** An IT service and how it is doing right now. */
public record ServiceView(Long id, String name, int criticality, ServiceHealth health, boolean atRisk,
                          List<String> dependsOn, int openIncidents) {

    public static ServiceView of(ServiceStatus status) {
        ITService service = status.service();
        return new ServiceView(service.getId(), service.getName(), service.getCriticality(), status.health(),
                status.isAtRisk(), service.getDependsOn().stream().map(ITService::getName).sorted().toList(),
                status.openIncidents().size());
    }
}
