package com.itsm.assignment;

import com.itsm.error.TechnicianUnavailableException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.itsm.error.InvalidInputException;
import com.itsm.error.TechnicianUnavailableException;
import com.itsm.error.WorkloadExceededException;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Setting;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.User;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventPublisher;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SettingRepository;
import com.itsm.repository.TechnicianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives incidents to technicians: automatically with whichever
 * {@link AssignmentStrategy} is active, or by hand when an administrator chooses.
 *
 * <p>This class never mentions a concrete strategy. It holds all of them by
 * their key and uses the active one through the interface, which is why an
 * administrator can switch strategy while the application is running.</p>
 */
@Service
public class AssignmentService {

    /** The name under which the active strategy's key is stored in the settings table. */
    public static final String ACTIVE_STRATEGY_SETTING = "assignment.strategy";

    /** Used until an administrator chooses a strategy. */
    public static final String DEFAULT_STRATEGY = SkillBasedStrategy.KEY;

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final Map<String, AssignmentStrategy> strategies = new LinkedHashMap<>();
    private final SettingRepository settings;
    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;
    private final IncidentEventPublisher events;

    /** Spring passes in every bean that implements AssignmentStrategy. */
    public AssignmentService(List<AssignmentStrategy> strategies, SettingRepository settings,
                             TechnicianRepository technicians, IncidentRepository incidents,
                             IncidentEventPublisher events) {
        strategies.forEach(strategy -> this.strategies.put(strategy.getKey(), strategy));
        if (!this.strategies.containsKey(DEFAULT_STRATEGY)) {
            throw new IllegalStateException("The default assignment strategy is missing: " + DEFAULT_STRATEGY);
        }
        this.settings = settings;
        this.technicians = technicians;
        this.incidents = incidents;
        this.events = events;
    }

    // ---- which strategy is active ----

    /** Every strategy an administrator can choose from. */
    public List<AssignmentStrategy> getStrategies() {
        return List.copyOf(strategies.values());
    }

    /** The strategy in use right now. Read from the database each time, so a switch takes effect at once. */
    @Transactional(readOnly = true)
    public AssignmentStrategy getActiveStrategy() {
        return settings.findById(ACTIVE_STRATEGY_SETTING)
                .map(Setting::getValue)
                .map(strategies::get)
                .orElse(strategies.get(DEFAULT_STRATEGY));
    }

    /** Switches to another strategy. Incidents assigned from now on use it. */
    @Transactional
    public AssignmentStrategy useStrategy(String key) {
        AssignmentStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new InvalidInputException("There is no assignment strategy called \"" + key + "\".");
        }
        settings.findById(ACTIVE_STRATEGY_SETTING).ifPresentOrElse(
                setting -> setting.setValue(key),
                () -> settings.save(new Setting(ACTIVE_STRATEGY_SETTING, key)));
        log.info("Assignment strategy switched to {}", key);
        return strategy;
    }

    // ---- assigning ----

    /** Every technician with the number of open incidents they hold right now, in id order. */
    @Transactional(readOnly = true)
    public List<TechnicianLoad> technicianLoads() {
        return technicians.findAllByOrderByIdAsc().stream().map(this::loadOf).toList();
    }

    private TechnicianLoad loadOf(Technician technician) {
        return new TechnicianLoad(technician,
                incidents.countByAssigneeIdAndStatusIn(technician.getId(), Status.openStatuses()));
    }

    /**
     * Lets the active strategy choose a technician for an unassigned incident.
     *
     * <p>If the strategy finds nobody it throws a {@link TechnicianUnavailableException}.
     * That is caught here: the incident keeps its status (CLASSIFIED), the
     * failure is recorded in its history, and it shows up as a warning for
     * the administrators, who can then assign it by hand.</p>
     *
     * @param actor the administrator who asked for it, or null when the system does it by itself
     * @return true if a technician was assigned
     */
    @Transactional
    public boolean autoAssign(Incident incident, User actor) {
        if (incident.getAssignee() != null) {
            throw new InvalidInputException("%s is already assigned to %s."
                    .formatted(incident.getCode(), incident.getAssignee().getName()));
        }
        AssignmentStrategy strategy = getActiveStrategy();
        Status before = incident.getStatus();
        try {
            Technician technician = strategy.choose(incident, technicianLoads());
            incident.assignTo(technician);
            events.publish(new IncidentEvent(incident, actor, IncidentAction.ASSIGNED, before, incident.getStatus(),
                    "Assigned to %s automatically (%s strategy).".formatted(technician.getName(), strategy.getName())));
            return true;
        } catch (TechnicianUnavailableException e) {
            log.warn("{} could not be assigned automatically ({}): {}", incident.getCode(), strategy.getKey(),
                    e.getMessage());
            events.publish(new IncidentEvent(incident, actor, IncidentAction.ASSIGNMENT_FAILED, before, before,
                    "%s (%s strategy). An administrator needs to assign this incident."
                            .formatted(e.getMessage(), strategy.getName())));
            return false;
        }
    }

    /**
     * An administrator gives the incident to a technician of their choice,
     * whether or not it already had one.
     *
     * @throws TechnicianUnavailableException if that technician is not available
     * @throws WorkloadExceededException      if that technician is already at their maximum workload
     */
    @Transactional
    public void assignManually(Incident incident, Long technicianId, User administrator) {
        Technician technician = technicianId == null ? null : technicians.findById(technicianId).orElse(null);
        if (technician == null) {
            throw new InvalidInputException("Please choose a technician.");
        }
        Technician previous = incident.getAssignee();
        if (technician.equals(previous)) {
            throw new InvalidInputException("%s is already assigned to %s."
                    .formatted(incident.getCode(), technician.getName()));
        }
        TechnicianLoad load = loadOf(technician);
        if (!technician.isAvailable()) {
            throw new TechnicianUnavailableException(technician.getName() + " is not available.");
        }
        if (load.openIncidents() >= technician.getMaxWorkload()) {
            throw new WorkloadExceededException(technician.getName(), load.openIncidents(),
                    technician.getMaxWorkload());
        }

        Status before = incident.getStatus();
        incident.assignTo(technician);
        if (previous == null) {
            events.publish(new IncidentEvent(incident, administrator, IncidentAction.ASSIGNED, before,
                    incident.getStatus(), "Assigned to %s by hand.".formatted(technician.getName())));
        } else {
            events.publish(new IncidentEvent(incident, administrator, IncidentAction.REASSIGNED, before,
                    incident.getStatus(), "Moved from %s to %s.".formatted(previous.getName(), technician.getName())));
        }
    }

    // ---- incidents nobody could be found for ----

    /** Incidents that are classified but have no technician: the ones administrators are warned about. */
    @Transactional(readOnly = true)
    public List<Incident> waitingForTechnician() {
        return incidents.findByStatusAndAssigneeIsNullOrderByCreatedAtAscIdAsc(Status.CLASSIFIED);
    }

    @Transactional(readOnly = true)
    public long countWaitingForTechnician() {
        return incidents.countByStatusAndAssigneeIsNull(Status.CLASSIFIED);
    }
}
