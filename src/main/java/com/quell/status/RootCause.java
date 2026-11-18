package com.quell.status;

import java.util.List;

import com.quell.domain.ITService;
import com.quell.domain.Incident;

/**
 * A service that probably causes several current problems at once.
 *
 * @param service    the suspected service
 * @param affected   the services with recent serious incidents that need it (it may be one of them)
 * @param incidents  those incidents
 * @param confirmed  true if the suspected service has a serious incident of its own
 */
public record RootCause(ITService service, List<ITService> affected, List<Incident> incidents, boolean confirmed) {

    /** E.g. "Corporate Network: 2 services that need it have serious incidents (Payment Application, Corporate Network)." */
    public String explain() {
        String names = String.join(", ", affected.stream().map(ITService::getName).toList());
        return "%d services that depend on %s have serious incidents at the same time (%s)%s."
                .formatted(affected.size(), service.getName(), names,
                        confirmed ? ", including " + service.getName() + " itself" : "");
    }
}
