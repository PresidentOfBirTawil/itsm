package com.itsm.assignment;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Chooses the technician with the fewest open incidents, whatever their skills. */
@Component
@Order(2)
public class LeastLoadedStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "least-loaded";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public String getName() {
        return "Least loaded";
    }

    @Override
    public String getDescription() {
        return "Gives the incident to the technician with the fewest open incidents, whatever their skills. "
                + "Spreads the work evenly.";
    }

    @Override
    protected Optional<TechnicianLoad> pick(Incident incident, List<TechnicianLoad> ableToTakeMore) {
        return ableToTakeMore.stream().min(LEAST_LOADED_FIRST);
    }
}
