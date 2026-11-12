package com.itsm.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.itsm.assignment.AssignmentService;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.PriorityAssessment;
import com.itsm.domain.Role;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.domain.state.Transition;
import com.itsm.error.DuplicateIncidentException;
import com.itsm.error.IncidentNotFoundException;
import com.itsm.error.InvalidInputException;
import com.itsm.error.InvalidStateTransitionException;
import com.itsm.error.NotFoundException;
import com.itsm.error.ServiceNotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventPublisher;
import com.itsm.intelligence.CategorySuggester;
import com.itsm.intelligence.CategorySuggestion;
import com.itsm.intelligence.DuplicateDetector;
import com.itsm.priority.PriorityCalculator;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.sla.SlaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What users can do with incidents: report one, open one, move one through
 * its lifecycle, add notes and rate the resolution. Every change made here
 * publishes an {@link IncidentEvent}, which is how the audit trail gets
 * written and how people are notified.
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
    private final CategorySuggester categorySuggester;
    private final DuplicateDetector duplicateDetector;
    private final IncidentEventPublisher events;

    public IncidentService(IncidentRepository incidents, ITServiceRepository services,
                           CategoryRepository categories, SlaService slaService,
                           AuditEntryRepository auditEntries, IncidentFactory incidentFactory,
                           PriorityCalculator priorityCalculator, AssignmentService assignmentService,
                           CategorySuggester categorySuggester, DuplicateDetector duplicateDetector,
                           IncidentEventPublisher events) {
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.slaService = slaService;
        this.auditEntries = auditEntries;
        this.incidentFactory = incidentFactory;
        this.priorityCalculator = priorityCalculator;
        this.assignmentService = assignmentService;
        this.categorySuggester = categorySuggester;
        this.duplicateDetector = duplicateDetector;
        this.events = events;
    }

    // ---- reporting ----

    /**
     * An employee reports an incident. In one go the system:
     * <ol>
     *   <li>chooses a category from the text, if the employee left it empty,</li>
     *   <li>creates the incident with the next code (status NEW),</li>
     *   <li>checks whether it looks like a duplicate of a recent incident,</li>
     *   <li>calculates its priority and sets the SLA deadlines (status CLASSIFIED),</li>
     *   <li>tries to assign a technician with the active strategy (status ASSIGNED).</li>
     * </ol>
     * If no technician can take it, the incident is still saved: it stays
     * CLASSIFIED and the administrators are warned.
     *
     * @param categoryId the chosen category, or null to let the system choose one
     * @throws ServiceNotFoundException   if the service does not exist
     * @throws DuplicateIncidentException if this employee already has the very same report open
     * @throws InvalidInputException      if no category was chosen and none can be worked out
     */
    @Transactional
    public Incident report(Employee reporter, String title, String description, Long serviceId,
                           Long categoryId, Impact impact, Urgency urgency) {
        ITService service = services.findById(serviceId).orElseThrow(() -> new ServiceNotFoundException(serviceId));
        refuseIfAlreadyReported(reporter, service, title);

        CategorySuggestion suggestion = null;
        Category category;
        if (categoryId != null) {
            category = categories.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("The chosen category does not exist."));
        } else {
            suggestion = categorySuggester.suggest(title + " " + description, categories.findAll())
                    .orElseThrow(() -> new InvalidInputException(
                            "We could not work out a category from what you wrote. Please choose one."));
            category = suggestion.category();
        }

        Incident incident = incidentFactory.create(reporter, title, description, service, category, impact, urgency);
        events.publish(new IncidentEvent(incident, reporter, IncidentAction.REPORTED, null, incident.getStatus(),
                "Reported against %s, category %s.".formatted(service.getName(), category.getName())));
        if (suggestion != null) {
            events.publish(new IncidentEvent(incident, null, IncidentAction.CATEGORY_SUGGESTED,
                    incident.getStatus(), incident.getStatus(), suggestion.explain()));
        }

        linkIfPossibleDuplicate(incident);
        prioritise(incident);
        assignmentService.autoAssign(incident, null);
        slaService.refresh(incident, LocalDateTime.now());
        return incident;
    }

    /** The same person, the same service, the same title, and the first report still open: that is a double report. */
    private void refuseIfAlreadyReported(Employee reporter, ITService service, String title) {
        incidents.findFirstByReporterIdAndServiceIdAndTitleIgnoreCaseAndStatusInOrderByIdDesc(
                        reporter.getId(), service.getId(), title == null ? "" : title.trim(), Status.openStatuses())
                .ifPresent(existing -> {
                    throw new DuplicateIncidentException(existing.getCode());
                });
    }

    /** Compares the new incident with recent open ones on the same service and links it to the most similar. */
    private void linkIfPossibleDuplicate(Incident incident) {
        List<Incident> recent = incidents.findByServiceIdAndStatusInAndCreatedAtGreaterThanEqualAndIdNot(
                incident.getService().getId(), Status.openStatuses(),
                incident.getCreatedAt().minus(DuplicateDetector.WINDOW), incident.getId());
        duplicateDetector.findDuplicate(incident, recent).ifPresent(match -> {
            incident.linkPossibleDuplicate(match.original(), match.similarity());
            events.publish(new IncidentEvent(incident, null, IncidentAction.DUPLICATE_SUSPECTED,
                    incident.getStatus(), incident.getStatus(), match.explain() + "."));
        });
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

    // ---- working on an incident ----

    /**
     * A user moves an incident to another status. When the move is to
     * RESOLVED, the note is recorded as the incident's resolution.
     *
     * @throws InvalidStateTransitionException if the lifecycle does not allow the move
     * @throws UnauthorizedOperationException  if this user may not make the move
     * @throws InvalidInputException           if the move needs a note and none was given
     */
    @Transactional
    public Incident changeStatus(String code, Status target, User actor, String note) {
        Incident incident = getFor(code, actor);
        Transition transition = incident.getState().transitionTo(target)
                .orElseThrow(() -> new InvalidStateTransitionException(incident, target));

        if (!transition.allowedFor(actor.getRole()) || !actor.canWorkOn(incident)) {
            throw new UnauthorizedOperationException("You may not move %s to %s."
                    .formatted(code, target.getLabel().toLowerCase()));
        }
        if (transition.noteRequired() && (note == null || note.isBlank())) {
            throw new InvalidInputException("Please add a note: \"%s\" needs an explanation."
                    .formatted(transition.labelFor(actor.getRole())));
        }

        Status before = incident.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (target == Status.RESOLVED) {
            incident.recordResolution(note);
        }
        incident.transitionTo(target, now);
        events.publish(new IncidentEvent(incident, actor, IncidentAction.STATUS_CHANGED, before,
                incident.getStatus(), note));
        // A change of status can stop, start or settle an SLA clock, so bring the SLA status up to date at once.
        slaService.refresh(incident, now);
        return incident;
    }

    /**
     * Someone working on the incident adds a note without changing its status:
     * a technician's investigation note, or extra information from the reporter.
     *
     * @throws UnauthorizedOperationException if this user is not involved in the incident
     * @throws InvalidInputException          if the note is empty or the incident is closed
     */
    @Transactional
    public Incident addNote(String code, User actor, String note) {
        Incident incident = getFor(code, actor);
        if (!actor.canWorkOn(incident)) {
            throw new UnauthorizedOperationException("You may not add notes to %s.".formatted(code));
        }
        if (!incident.getState().allowsNotes()) {
            throw new InvalidInputException("%s is %s. Nothing more can be added to it."
                    .formatted(code, incident.getStatus().getLabel().toLowerCase()));
        }
        if (note == null || note.isBlank()) {
            throw new InvalidInputException("Please write the note first.");
        }
        events.publish(new IncidentEvent(incident, actor, IncidentAction.NOTE_ADDED,
                incident.getStatus(), incident.getStatus(), note));
        return incident;
    }

    /**
     * A technician or an administrator corrects the classification of an open
     * incident: its category, impact or urgency. The priority and the SLA
     * deadlines are worked out again, and if the priority comes out different,
     * the reporter and the technician are told.
     *
     * @throws UnauthorizedOperationException if this user may not change the incident
     * @throws InvalidInputException          if nothing was changed, or the incident is no longer open
     */
    @Transactional
    public Incident reclassify(String code, User actor, Long categoryId, Impact impact, Urgency urgency) {
        Incident incident = getFor(code, actor);
        if (actor.getRole() == Role.EMPLOYEE || !actor.canWorkOn(incident)) {
            throw new UnauthorizedOperationException("You may not change the classification of %s.".formatted(code));
        }
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("The chosen category does not exist."));

        List<String> changes = new ArrayList<>();
        if (!category.equals(incident.getCategory())) {
            changes.add("category %s → %s".formatted(
                    incident.getCategory() == null ? "none" : incident.getCategory().getName(), category.getName()));
        }
        if (impact != incident.getImpact()) {
            changes.add("impact %s → %s".formatted(incident.getImpact().getLabel(), impact.getLabel()));
        }
        if (urgency != incident.getUrgency()) {
            changes.add("urgency %s → %s".formatted(incident.getUrgency().getLabel(), urgency.getLabel()));
        }
        if (changes.isEmpty()) {
            throw new InvalidInputException("Nothing was changed.");
        }

        Priority before = incident.getPriority();
        incident.reclassify(category, impact, urgency);
        events.publish(new IncidentEvent(incident, actor, IncidentAction.CLASSIFICATION_CHANGED,
                incident.getStatus(), incident.getStatus(), "Changed " + String.join(", ", changes) + "."));

        PriorityAssessment assessment = priorityCalculator.calculate(incident);
        incident.prioritise(assessment, slaService.policyFor(assessment.priority()));
        if (assessment.priority() != before) {
            events.publish(new IncidentEvent(incident, actor, IncidentAction.PRIORITY_CHANGED,
                    incident.getStatus(), incident.getStatus(),
                    "Priority changed from %s to %s (score %s). Respond by %s, resolve by %s.".formatted(
                            before, assessment.priority(), assessment.formula(),
                            DEADLINE.format(incident.getResponseDue()), DEADLINE.format(incident.getResolutionDue()))));
        }
        slaService.refresh(incident, LocalDateTime.now());
        return incident;
    }

    /**
     * The reporter rates the resolution from 1 to 5.
     *
     * @throws UnauthorizedOperationException if this user did not report the incident
     * @throws InvalidInputException          if it cannot be rated yet, was rated before, or the rating is not 1 to 5
     */
    @Transactional
    public Incident rate(String code, User actor, int stars, String comment) {
        Incident incident = getFor(code, actor);
        if (actor.getRole() != Role.EMPLOYEE || !actor.canWorkOn(incident)) {
            throw new UnauthorizedOperationException("Only the person who reported %s can rate it.".formatted(code));
        }
        incident.rate(stars, comment, LocalDateTime.now());
        events.publish(new IncidentEvent(incident, actor, IncidentAction.RATED,
                incident.getStatus(), incident.getStatus(),
                "Rated %d out of %d.%s".formatted(stars, Incident.MAX_RATING,
                        incident.getRatingComment() == null ? "" : " " + incident.getRatingComment())));
        return incident;
    }

    // ---- assignment by an administrator ----

    /** An administrator gives the incident to a technician of their choice. */
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

    // ---- reading ----

    /** Finds an incident by its code, e.g. INC-0001. */
    @Transactional(readOnly = true)
    public Incident get(String code) {
        return incidents.findByCode(code).orElseThrow(() -> new IncidentNotFoundException(code));
    }

    /**
     * Finds an incident for a user who wants to look at it.
     *
     * @throws UnauthorizedOperationException if this user may not see the incident
     */
    @Transactional(readOnly = true)
    public Incident getFor(String code, User viewer) {
        Incident incident = get(code);
        if (!viewer.canView(incident)) {
            throw new UnauthorizedOperationException("You may not open %s.".formatted(code));
        }
        return incident;
    }

    /** The history of an incident, oldest entry first. */
    @Transactional(readOnly = true)
    public List<AuditEntry> history(Incident incident) {
        return auditEntries.findByIncidentIdOrderByOccurredAtAscIdAsc(incident.getId());
    }

    /** Later incidents that were flagged as possible duplicates of this one. */
    @Transactional(readOnly = true)
    public List<Incident> possibleDuplicatesOf(Incident incident) {
        return incidents.findByPossibleDuplicateOfIdOrderByIdAsc(incident.getId());
    }
}
