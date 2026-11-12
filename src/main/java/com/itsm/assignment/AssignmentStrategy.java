package com.itsm.assignment;

import com.itsm.error.TechnicianUnavailableException;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Technician;

/**
 * A way of choosing the technician for an incident (the Strategy pattern).
 *
 * <p>The application has several of these and an administrator picks which one
 * is active. The code that assigns incidents only talks to this interface, so
 * swapping the strategy, or adding a new one, changes nothing else.</p>
 */
public interface AssignmentStrategy {

    /** A short fixed name used to remember which strategy is active, e.g. "skill-based". */
    String getKey();

    /** The name shown to administrators. */
    String getName();

    /** One or two sentences explaining how this strategy chooses. */
    String getDescription();

    /**
     * Chooses a technician for the incident.
     *
     * @param technicians every technician with their current number of open incidents
     * @throws TechnicianUnavailableException if no technician can take the incident
     */
    Technician choose(Incident incident, List<TechnicianLoad> technicians);
}
