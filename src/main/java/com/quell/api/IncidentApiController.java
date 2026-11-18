package com.quell.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import com.quell.api.model.IncidentView;
import com.quell.api.model.NewIncident;
import com.quell.api.model.NewNote;
import com.quell.api.model.PageView;
import com.quell.api.model.StatusChange;
import com.quell.api.model.TimelineEntry;
import com.quell.domain.Employee;
import com.quell.domain.Incident;
import com.quell.domain.Priority;
import com.quell.domain.Role;
import com.quell.domain.SlaStatus;
import com.quell.domain.Status;
import com.quell.domain.User;
import com.quell.error.InvalidInputException;
import com.quell.error.UnauthorizedOperationException;
import com.quell.repository.IncidentRepository;
import com.quell.repository.UserRepository;
import com.quell.search.IncidentSearchCriteria;
import com.quell.search.IncidentSpecifications;
import com.quell.security.QuellUserDetails;
import com.quell.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidents over the API: list, read, report, add notes, change status.
 * Every rule of the web app applies, because the same services do the work:
 * an employee's key sees only their own incidents, a technician's key can
 * only act on incidents assigned to them, and so on.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Report, follow and work on incidents")
public class IncidentApiController {

    static final int MAX_PAGE_SIZE = 100;

    private final IncidentService incidentService;
    private final IncidentRepository incidents;
    private final UserRepository users;

    public IncidentApiController(IncidentService incidentService, IncidentRepository incidents, UserRepository users) {
        this.incidentService = incidentService;
        this.incidents = incidents;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List incidents",
            description = "The incidents the key's owner may see, newest first. Employees see the ones they reported or are affected by.")
    public PageView<IncidentView> list(@AuthenticationPrincipal QuellUserDetails principal,
                                       @RequestParam(required = false) Status status,
                                       @RequestParam(required = false) Priority priority,
                                       @RequestParam(required = false) Long serviceId,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) SlaStatus slaStatus,
                                       @Parameter(description = "Words in the code or title") @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        User user = user(principal);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException("page must be 0 or more, and size from 1 to %d.".formatted(MAX_PAGE_SIZE));
        }
        IncidentSearchCriteria criteria = new IncidentSearchCriteria();
        criteria.setStatus(status);
        criteria.setPriority(priority);
        criteria.setServiceId(serviceId);
        criteria.setCategoryId(categoryId);
        criteria.setSlaStatus(slaStatus);
        Specification<Incident> filter = IncidentSpecifications.matching(criteria)
                .and(visibleTo(user))
                .and(containsWords(q));
        Page<Incident> found = incidents.findAll(filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageView<>(found.getContent().stream().map(incident -> IncidentView.of(incident, user.getRole())).toList(),
                page, size, found.getTotalElements(), found.getTotalPages());
    }

    @GetMapping("/{code}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get one incident")
    public IncidentView get(@AuthenticationPrincipal QuellUserDetails principal, @PathVariable String code) {
        User user = user(principal);
        return IncidentView.of(incidentService.getFor(code, user), user.getRole());
    }

    @GetMapping("/{code}/timeline")
    @Transactional(readOnly = true)
    @Operation(summary = "The history of an incident", description = "Everything that happened to it, oldest first.")
    public List<TimelineEntry> timeline(@AuthenticationPrincipal QuellUserDetails principal, @PathVariable String code) {
        Incident incident = incidentService.getFor(code, user(principal));
        return incidentService.history(incident).stream().map(TimelineEntry::of).toList();
    }

    @PostMapping
    @Operation(summary = "Report an incident",
            description = "With an employee's key, reported by that employee. With an administrator's key, give reporterEmail "
                    + "to report on behalf of an employee (for example from a monitoring tool). The priority, SLA and "
                    + "technician are worked out exactly as in the web app.")
    public ResponseEntity<IncidentView> report(@AuthenticationPrincipal QuellUserDetails principal,
                                               @Valid @RequestBody NewIncident body) {
        User user = user(principal);
        Employee reporter = reporterFor(user, body.reporterEmail());
        Incident incident = incidentService.report(reporter, body.title(), body.description(), body.serviceId(),
                body.categoryId(), body.impact(), body.urgency());
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + incident.getCode()))
                .body(IncidentView.of(incident, user.getRole()));
    }

    @PostMapping("/{code}/notes")
    @Operation(summary = "Add a note", description = "Visible in the incident's timeline.")
    public ResponseEntity<IncidentView> addNote(@AuthenticationPrincipal QuellUserDetails principal,
                                                @PathVariable String code, @Valid @RequestBody NewNote body) {
        User user = user(principal);
        Incident incident = incidentService.addNote(code, user, body.text());
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + code + "/timeline"))
                .body(IncidentView.of(incident, user.getRole()));
    }

    @PostMapping("/{code}/status")
    @Operation(summary = "Change the status",
            description = "Only the moves the web app offers this user are allowed, e.g. a technician moves their own incident "
                    + "to IN_PROGRESS or RESOLVED (with a note), the reporter confirms the fix with VERIFIED.")
    public IncidentView changeStatus(@AuthenticationPrincipal QuellUserDetails principal, @PathVariable String code,
                                     @Valid @RequestBody StatusChange body) {
        User user = user(principal);
        return IncidentView.of(incidentService.changeStatus(code, body.status(), user, body.note()), user.getRole());
    }

    private Employee reporterFor(User user, String reporterEmail) {
        if (reporterEmail == null || reporterEmail.isBlank()) {
            if (user instanceof Employee employee) {
                return employee;
            }
            throw new InvalidInputException("Only employees report incidents. With an administrator's key, "
                    + "give reporterEmail to report on behalf of an employee.");
        }
        if (user.getRole() != Role.ADMIN) {
            throw new UnauthorizedOperationException("Only administrators' keys may report on behalf of someone else.");
        }
        return users.findByEmail(reporterEmail.strip().toLowerCase(Locale.ROOT))
                .filter(Employee.class::isInstance)
                .map(Employee.class::cast)
                .orElseThrow(() -> new InvalidInputException("There is no employee with the email %s.".formatted(reporterEmail)));
    }

    /** Employees see what they reported or are affected by; staff see everything. */
    static Specification<Incident> visibleTo(User user) {
        if (user.getRole() != Role.EMPLOYEE) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("reporter").get("id"), user.getId()),
                cb.isMember(user, root.get("affectedUsers")));
    }

    /** Every word must appear in the code or the title. */
    static Specification<Incident> containsWords(String text) {
        if (text == null || text.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> all = new java.util.ArrayList<>();
            for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
                String pattern = "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\')));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private User user(QuellUserDetails principal) {
        return users.findById(principal.getId()).orElseThrow();
    }
}
