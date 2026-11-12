package com.itsm.web;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.itsm.error.ItsmException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.intelligence.DuplicateMatch;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.Role;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.domain.state.Transition;
import com.itsm.priority.PriorityCalculator;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.search.IncidentSearchCriteria;
import com.itsm.search.IncidentSpecifications;
import com.itsm.service.IncidentService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The incident page and its status buttons work the same way for all three
 * roles, so the three role controllers share this helper instead of each
 * repeating the code. Only the URL prefix differs (/employee, /technician, /admin).
 */
@Component
public class IncidentPages {

    static final String DETAIL_VIEW = "incidents/detail";
    static final String SEARCH_VIEW = "incidents/search";

    private final IncidentService incidentService;
    private final PriorityCalculator priorityCalculator;
    private final IncidentRepository incidents;
    private final CategoryRepository categories;
    private final ITServiceRepository services;
    private final TechnicianRepository technicians;

    public IncidentPages(IncidentService incidentService, PriorityCalculator priorityCalculator,
                         IncidentRepository incidents, CategoryRepository categories,
                         ITServiceRepository services, TechnicianRepository technicians) {
        this.incidentService = incidentService;
        this.priorityCalculator = priorityCalculator;
        this.incidents = incidents;
        this.categories = categories;
        this.services = services;
        this.technicians = technicians;
    }

    /**
     * Fills the model for the incident page.
     *
     * @param viewer   the signed-in user; decides which status buttons are offered
     * @param basePath where this role's incident pages live, e.g. "/employee/incidents"
     * @return the name of the template to render
     */
    public String show(Model model, String code, User viewer, String basePath) {
        Incident incident = incidentService.getFor(code, viewer);
        // A technician may look at any incident but only gets buttons on their own.
        boolean canWorkOn = viewer.canWorkOn(incident);
        List<Transition> transitions = canWorkOn ? incident.getState().transitionsFor(viewer.getRole()) : List.of();

        model.addAttribute("incident", incident);
        model.addAttribute("viewerRole", viewer.getRole());
        model.addAttribute("canWorkOn", canWorkOn);
        model.addAttribute("transitions", transitions);
        // The buttons that need a note, by their text, e.g. "Resolve, Request information". Empty if none does.
        model.addAttribute("noteNeededFor", transitions.stream()
                .filter(Transition::noteRequired)
                .map(transition -> transition.labelFor(viewer.getRole()))
                .collect(Collectors.joining(", ")));
        model.addAttribute("basePath", basePath);
        model.addAttribute("priorityRule", priorityCalculator.describeRule());

        // What the user may add: a note while the incident is not closed, a rating once the fix is confirmed.
        model.addAttribute("canAddNote", canWorkOn && incident.getState().allowsNotes());
        model.addAttribute("canRate", viewer.getRole() == Role.EMPLOYEE && canWorkOn
                && incident.getState().allowsRating() && incident.getRating() == null);

        // Technicians (on their own incidents) and administrators can correct the classification while it is open.
        boolean canReclassify = canWorkOn && viewer.getRole() != Role.EMPLOYEE && incident.getStatus().isOpen();
        model.addAttribute("canReclassify", canReclassify);
        if (canReclassify) {
            model.addAttribute("categories", categories.findAllByOrderByNameAsc());
            model.addAttribute("impacts", Impact.values());
            model.addAttribute("urgencies", Urgency.values());
        }

        // What the intelligent features found, each with its explanation.
        List<AuditEntry> history = incidentService.history(incident);
        model.addAttribute("history", history);
        model.addAttribute("duplicate", DuplicateMatch.of(incident));
        model.addAttribute("laterDuplicates", viewer.getRole() == Role.EMPLOYEE ? List.of()
                : incidentService.possibleDuplicatesOf(incident));
        model.addAttribute("categoryNote", noteOf(history, IncidentAction.CATEGORY_SUGGESTED));
        model.addAttribute("escalationNote", noteOf(history, IncidentAction.ESCALATED));
        return DETAIL_VIEW;
    }

    private static String noteOf(List<AuditEntry> history, IncidentAction action) {
        return history.stream().filter(entry -> entry.getAction() == action)
                .map(AuditEntry::getNote).findFirst().orElse(null);
    }

    /**
     * Fills the model for the search page: the incidents that match the
     * filters, and the choices for the filter drop-downs.
     *
     * @param basePath   where this role's incident pages live; each result links to basePath/code
     * @param searchPath the URL of the search page itself (where the filter form is sent)
     */
    public String search(Model model, IncidentSearchCriteria criteria, BindingResult result,
                         String basePath, String searchPath) {
        // If a filter could not be understood (for example a date that is not a date), show nothing rather than everything.
        List<Incident> found = result.hasErrors() ? List.of()
                : incidents.findAll(IncidentSpecifications.matching(criteria),
                        Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        model.addAttribute("incidents", found);
        model.addAttribute("filtered", !criteria.isEmpty());
        model.addAttribute("basePath", basePath);
        model.addAttribute("searchPath", searchPath);
        model.addAttribute("statuses", Status.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("slaStatuses", SlaStatus.values());
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        model.addAttribute("technicians", technicians.findAllByOrderByNameAsc());
        return SEARCH_VIEW;
    }

    // ---- buttons on the incident page ----

    /** A status button was pressed. */
    public String changeStatus(String code, TransitionForm form, User actor, String basePath,
                               RedirectAttributes redirect) {
        return perform(basePath + "/" + code, redirect, () -> {
            Incident incident = incidentService.changeStatus(code, form.getTarget(), actor, form.getNote());
            return "%s is now %s.".formatted(incident.getCode(), incident.getStatus().getLabel().toLowerCase());
        });
    }

    /** "Save classification" was pressed. */
    public String reclassify(String code, ClassificationForm form, User actor, String basePath,
                             RedirectAttributes redirect) {
        return perform(basePath + "/" + code, redirect, () -> {
            Incident incident = incidentService.reclassify(code, actor, form.getCategoryId(), form.getImpact(),
                    form.getUrgency());
            return "The classification of %s has been changed. Its priority is %s."
                    .formatted(code, incident.getPriority());
        });
    }

    /** "Add a note" was pressed. */
    public String addNote(String code, NoteForm form, User actor, String basePath, RedirectAttributes redirect) {
        return perform(basePath + "/" + code, redirect, () -> {
            incidentService.addNote(code, actor, form.getNote());
            return "Your note has been added to %s.".formatted(code);
        });
    }

    /**
     * Carries out an action and goes back to the given page. If the action
     * breaks a rule, the rule's message is shown there as an error and nothing
     * has changed. "Not found" and "not allowed" are not messages but whole
     * pages, so those are passed on to the {@link ErrorHandler}.
     *
     * @param action does the work and returns the success message
     */
    public String perform(String backTo, RedirectAttributes redirect, Supplier<String> action) {
        try {
            redirect.addFlashAttribute("success", action.get());
        } catch (NotFoundException | UnauthorizedOperationException e) {
            throw e;
        } catch (ItsmException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + backTo;
    }
}
