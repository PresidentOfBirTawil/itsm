package com.itsm.assignment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import com.itsm.domain.Technician;

/**
 * The part every strategy shares: technicians who are unavailable or already
 * at their maximum workload are never chosen, and if nobody is left the
 * strategy fails with a {@link TechnicianUnavailableException}.
 *
 * <p>A concrete strategy only has to say how it picks among the technicians
 * who can still take work.</p>
 */
public abstract class AbstractAssignmentStrategy implements AssignmentStrategy {

    /** Fewest open incidents first; equal loads are settled by name so the result is predictable. */
    protected static final Comparator<TechnicianLoad> LEAST_LOADED_FIRST =
            Comparator.comparingLong(TechnicianLoad::openIncidents)
                    .thenComparing(load -> load.technician().getName());

    @Override
    public final Technician choose(Incident incident, List<TechnicianLoad> technicians) {
        List<TechnicianLoad> ableToTakeMore = technicians.stream().filter(TechnicianLoad::canTakeMore).toList();
        if (ableToTakeMore.isEmpty()) {
            throw new TechnicianUnavailableException(
                    "Every technician is unavailable or already at their maximum workload");
        }
        return pick(incident, ableToTakeMore)
                .map(TechnicianLoad::technician)
                .orElseThrow(() -> new TechnicianUnavailableException(nobodySuitableMessage(incident)));
    }

    /**
     * Picks one of the technicians who can still take work.
     *
     * @param ableToTakeMore never empty; contains only available technicians below their maximum workload
     * @return the choice, or empty if none of them is suitable for this incident
     */
    protected abstract Optional<TechnicianLoad> pick(Incident incident, List<TechnicianLoad> ableToTakeMore);

    /** The message used when {@link #pick} finds nobody suitable. */
    protected String nobodySuitableMessage(Incident incident) {
        return "No suitable technician can take this incident";
    }
}
