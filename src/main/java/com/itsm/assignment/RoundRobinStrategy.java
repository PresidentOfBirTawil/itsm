package com.itsm.assignment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Takes the technicians in turn: each new incident goes to the next
 * technician after the one who got the previous incident, and after the last
 * technician it starts again with the first.
 *
 * <p>The turn order is the order of the technicians' ids. The strategy
 * remembers whose turn it was in memory, so after a restart it begins again
 * with the first technician.</p>
 */
@Component
@Order(3)
public class RoundRobinStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "round-robin";

    private static final Comparator<TechnicianLoad> BY_ID =
            Comparator.comparingLong(load -> load.technician().getId());

    /** The id of the technician who got the previous incident. 0 means nobody has had a turn yet. */
    private long lastChosenId = 0;

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public String getName() {
        return "Round robin";
    }

    @Override
    public String getDescription() {
        return "Gives incidents to the technicians in turn, one after the other, whatever their skills or load. "
                + "A technician who cannot take more work is skipped.";
    }

    /** Synchronized because two incidents may be reported at the same moment and must not get the same turn. */
    @Override
    protected synchronized Optional<TechnicianLoad> pick(Incident incident, List<TechnicianLoad> ableToTakeMore) {
        List<TechnicianLoad> inTurnOrder = ableToTakeMore.stream().sorted(BY_ID).toList();
        TechnicianLoad next = inTurnOrder.stream()
                .filter(load -> load.technician().getId() > lastChosenId)
                .findFirst()
                .orElse(inTurnOrder.getFirst()); // past the end: start again with the first
        lastChosenId = next.technician().getId();
        return Optional.of(next);
    }
}
