package com.quell.api.model;

import java.time.LocalDateTime;
import java.util.List;

import com.quell.domain.ITService;
import com.quell.status.RootCause;
import com.quell.status.ServiceHealth;
import com.quell.status.StatusOverview;

/** The overall service status, as on the status page. */
public record StatusView(String headline, ServiceHealth worst, List<ServiceView> services, RootCauseView rootCause,
                         LocalDateTime asOf) {

    /** When several services fail together: the one they all need. */
    public record RootCauseView(String service, List<String> affected, boolean confirmed, String explanation) {

        static RootCauseView of(RootCause cause) {
            return cause == null ? null : new RootCauseView(cause.service().getName(),
                    cause.affected().stream().map(ITService::getName).toList(), cause.confirmed(), cause.explain());
        }
    }

    public static StatusView of(StatusOverview overview) {
        return new StatusView(overview.headline(), overview.worst(),
                overview.services().stream().map(ServiceView::of).toList(),
                RootCauseView.of(overview.rootCause()), overview.asOf());
    }
}
