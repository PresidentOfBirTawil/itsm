package com.itsm.assignment;

import com.itsm.domain.Technician;

/**
 * A technician together with the number of open incidents they hold right now.
 * This is what an assignment strategy looks at when it chooses.
 */
public record TechnicianLoad(Technician technician, long openIncidents) {

    /** True if the technician is available and still below their maximum workload. */
    public boolean canTakeMore() {
        return technician.isAvailable() && openIncidents < technician.getMaxWorkload();
    }
}
