package com.itsm.error;

/** Thrown when an incident is given to a technician who already holds as many open incidents as they may. */
public class WorkloadExceededException extends ItsmException {

    public WorkloadExceededException(String technicianName, long openIncidents, int maxWorkload) {
        super("%s already has %d of %d open incidents.".formatted(technicianName, openIncidents, maxWorkload));
    }
}
