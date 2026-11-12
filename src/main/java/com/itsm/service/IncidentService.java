package com.itsm.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.itsm.BusinessRuleException;
import com.itsm.assignment.AssignmentService;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.PriorityAssessment;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.domain.state.InvalidStateTransitionException;
import com.itsm.domain.state.Transition;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventPublisher;
import com.itsm.priority.PriorityCalculator;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.sla.SlaService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What users can do with incidents: report one, open one, and move one
 * through its lifecycle. Every change made here publishes an
 * {@link IncidentEvent}, which is how the audit trail gets written.
 */
@Service
public class IncidentService {

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final IncidentRepository incidents;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SlaService slaService;
    private final AuditEntryRepository auditEntries;
    private final IncidentFactory incidentFactory;
    private final PriorityCalculator priorityCalculator;
    private final AssignmentService assignmentService;
    private final IncidentEventPublisher events;

    public IncidentService(IncidentRepository incidents, ITServiceRepository services,
                           CategoryRepository categories, SlaService slaService,
                           AuditEntryRepository auditEntries, IncidentFactory incidentFactory,
                           PriorityCalculator priorityCalculator, AssignmentService assignmentService,
                           IncidentEventPublisher events) {
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.slaService = slaService;
        this.auditEntries = auditEntries;
        this.incidentFactory = incidentFactory;
        this.priorityCalculator = priorityCalculator;
        this.assignmentService = assignmentService;
        this.events = events;
    }

    /**
     * An employee reports an incident. In one go the system:
     * <ol>
     *   <li>creates the incident with the next code (status NEW),</li>
     *   <li>calculates its priority and sets the SLA deadlines (status CLASSIFIED),</li>
     *   <li>tries to assign a technician with the active strategy (status ASSIGNED).</li>
     * </ol>
     * If no technician can take it, the incident is still saved: it stays
     * CLASSIFIED and the administrators are warned.
     */
    @Transactional
    public Incident report(Employee reporter, String title, String description, Long serviceId,
                           Long categoryId, Impact impact, Urgency urgency) {
        ITService service = services.findById(serviceId)
                .orElseThrow(() -> new BusinessRuleException("The chosen service does not exist."));
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("The chosen category does not exist."));

        Incident incident = incidentFactory.create(reporter, title, description, service, category, impact, urgency);
        events.publish(new IncidentEvent(incident, reporter, IncidentAction.REPORTED, null, incident.getStatus(),
                "Reported against %s, category %s.".formatted(service.getName(), category.getName())));

        prioritise(incident);
        assignmentService.autoAssign(incident, null);
        slaService.refresh(incident, LocalDateTime.now());
        return incident;
    }

    /** Calculates the priority, looks up the SLA policy for it and sets both on the incident. */
    private void prioritise(Incident incident) {
        PriorityAssessment assessment = priorityCalculator.calculate(incident);
        SlaPolicy policy = slaService.policyFor(assessment.priority());

        Status before = incident.getStatus();
        incident.prioritise(assessment, policy);
        events.publish(new IncidentEvent(incident, null, IncidentAction.PRIORITISED, before, incident.getStatus(),
                "Priority %s (%s). Score: impact × urgency × service criticality = %s. Respond by %s, resolve by %s."
                        .formatted(assessment.priority(), assessment.priority().getLabel(), assessment.formula(),
                                DEADLINE.format(incident.getResponseDue()),
                                DEADLINE.format(incident.getResolutionDue()))));
    }

    /**
     * A user moves an incident to another status.
     *
     * @throws InvalidStateTransitionException if the lifecycle does not allow the move
     * @throws AccessDeniedException           if this user may not make the move
     * @throws BusinessRuleException           if the move needs a note and none was given
     */
    @Transactional
    public Incident changeStatus(String code, Status target, User actor, String note) {
        Incident incident = getFor(code, actor);
        Transition transition = incident.getState().transitionTo(target)
                .orElseThrow(() -> new InvalidStateTransitionException(incident, target));

        if (!transition.allowedFor(actor.getRole()) || !actor.canWorkOn(incident)) {
            throw new AccessDeniedException("%s may not move %s to %s".formatted(actor, code, target));
        }
        if (transition.noteRequired() && (note == null || note.isBlank())) {
            throw new BusinessRuleException("Please add a note: \"%s\" needs an explanation."
                    .formatted(transition.labelFor(actor.getRole())));
        }

        Status before = incident.getStatus();
        LocalDateTime now = LocalDateTime.now();
        incident.transitionTo(target, now);
        events.publish(new IncidentEvent(incident, actor, IncidentAction.STATUS_CHANGED, before,
                incident.getStatus(), note));
        // A change of status can stop, start or settle an SLA clock, so bring the SLA status up to date at once.
        slaService.refresh(incident, now);
        return incident;
    }

    /**
     * An administrator gives the incident to a technician of their choice.
     *
     * @throws BusinessRuleException if that technician cannot take it, or the incident cannot be assigned now
     */
    @Transactional
    public Incident assign(String code, Long technicianId, User administrator) {
        Incident incident = get(code);
        assignmentService.assignManually(incident, technicianId, administrator);
        return incident;
    }

    /**
     * An administrator asks the active strategy to try again for an incident
     * that has no technician. Check {@link Incident#getAssignee()} afterwards
     * to see whether it found one.
     */
    @Transactional
    public Incident autoAssign(String code, User administrator) {
        Incident incident = get(code);
        assignmentService.autoAssign(incident, administrator);
        return incident;
    }

    /** Finds an incident by its code, e.g. INC-0001. */
    @Transactional(readOnly = true)
    public Incident get(String code) {
        return incidents.findByCode(code).orElseThrow(() -> new IncidentNotFoundException(code));
    }

    /**
     * Finds an incident for a user who wants to look at it.
     *
     * @throws AccessDeniedException if this user may not see the incident
     */
    @Transactional(readOnly = true)
    public Incident getFor(String code, User viewer) {
        Incident incident = get(code);
        if (!viewer.canView(incident)) {
            throw new AccessDeniedException("%s may not open %s".formatted(viewer, code));
        }
        return incident;
    }

    /** The history of an incident, oldest entry first. */
    @Transactional(readOnly = true)
    public List<AuditEntry> history(Incident incident) {
        return auditEntries.findByIncidentIdOrderByOccurredAtAscIdAsc(incident.getId());
    }
}
