package com.itsm.assignment;

import java.util.List;
import java.util.Optional;

import com.itsm.domain.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Chooses a technician whose skills include the incident's category. If
 * several qualify, the one with the fewest open incidents gets it.
 */
@Component
@Order(1)
public class SkillBasedStrategy extends AbstractAssignmentStrategy {

    public static final String KEY = "skill-based";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public String getName() {
        return "Skill-based";
    }

    @Override
    public String getDescription() {
        return "Gives the incident to a technician whose skills include its category. "
                + "If several qualify, the one with the fewest open incidents gets it.";
    }

    @Override
    protected Optional<TechnicianLoad> pick(Incident incident, List<TechnicianLoad> ableToTakeMore) {
        return ableToTakeMore.stream()
                .filter(load -> load.technician().hasSkill(incident.getCategory()))
                .min(LEAST_LOADED_FIRST);
    }

    @Override
    protected String nobodySuitableMessage(Incident incident) {
        return incident.getCategory() == null
                ? "The incident has no category, so no technician can be matched by skill"
                : "No technician with the skill " + incident.getCategory().getName()
                        + " is available and below their maximum workload";
    }
}
