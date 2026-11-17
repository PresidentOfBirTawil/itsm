package com.itsm.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.itsm.attachment.AttachmentService;
import com.itsm.error.ItsmException;
import com.itsm.error.NotFoundException;
import com.itsm.error.UnauthorizedOperationException;
import com.itsm.domain.KnowledgeArticle;
import com.itsm.intelligence.CategoryClassifier;
import com.itsm.intelligence.CategoryPrediction;
import com.itsm.intelligence.DuplicateMatch;
import com.itsm.intelligence.ResolutionRecommender;
import com.itsm.knowledge.KnowledgeService;
import com.itsm.sla.SlaPredictor;
import com.itsm.status.RootCause;
import com.itsm.status.StatusService;
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

    /** How many suggested solutions the incident page shows. */
    private static final int SUGGESTED_SOLUTIONS = 3;
    /** The classifier must be at least this sure before it questions the category on the incident page. */
    private static final double SECOND_OPINION_CONFIDENCE = 0.6;

    private final IncidentService incidentService;
    private final PriorityCalculator priorityCalculator;
    private final IncidentRepository incidents;
    private final CategoryRepository categories;
    private final ITServiceRepository services;
    private final TechnicianRepository technicians;
    private final ResolutionRecommender recommender;
    private final CategoryClassifier classifier;
    private final SlaPredictor slaPredictor;
    private final KnowledgeService knowledge;
    private final StatusService statusService;
    private final AttachmentService attachmentService;

    public IncidentPages(IncidentService incidentService, PriorityCalculator priorityCalculator,
                         IncidentRepository incidents, CategoryRepository categories,
                         ITServiceRepository services, TechnicianRepository technicians,
                         ResolutionRecommender recommender, CategoryClassifier classifier, SlaPredictor slaPredictor,
                         KnowledgeService knowledge, StatusService statusService, AttachmentService attachmentService) {
        this.incidentService = incidentService;
        this.priorityCalculator = priorityCalculator;
        this.incidents = incidents;
        this.categories = categories;
        this.services = services;
        this.technicians = technicians;
        this.recommender = recommender;
        this.classifier = classifier;
        this.slaPredictor = slaPredictor;
        this.knowledge = knowledge;
        this.statusService = statusService;
        this.attachmentService = attachmentService;
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

        // Screenshots and other files: everyone who can see the incident sees them; the people on it can add more.
        model.addAttribute("attachments", attachmentService.of(incident));
        model.addAttribute("canAttach", attachmentService.mayAttach(viewer, incident) && incident.getStatus() != Status.CLOSED);
        model.addAttribute("viewerId", viewer.getId());

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
        addInsights(model, incident, viewer, canWorkOn, canReclassify);
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

    /**
     * What the learning features have to say about the incident: when it will
     * probably be resolved (everyone), and for technicians and administrators
     * the suggested solutions, a second opinion on the category, and whether
     * the fix has been written up for the knowledge base.
     */
    private void addInsights(Model model, Incident incident, User viewer, boolean canWorkOn, boolean canReclassify) {
        boolean staff = viewer.getRole() != Role.EMPLOYEE;
        boolean open = incident.getStatus().isOpen();
        model.addAttribute("forecast", open ? slaPredictor.forecast(incident, LocalDateTime.now()).orElse(null) : null);
        model.addAttribute("staff", staff);
        if (!staff) {
            return;
        }
        model.addAttribute("recommendations", open ? recommender.recommend(incident, SUGGESTED_SOLUTIONS) : List.of());
        model.addAttribute("canUseSuggestion", canWorkOn && open);
        CategoryPrediction opinion = canReclassify
                ? classifier.predict(incident.getTitle() + " " + incident.getDescription())
                        .filter(prediction -> prediction.confidence() >= SECOND_OPINION_CONFIDENCE)
                        .filter(prediction -> incident.getCategory() == null
                                || !prediction.categoryId().equals(incident.getCategory().getId()))
                        .orElse(null)
                : null;
        model.addAttribute("classifierOpinion", opinion);
        // Part of a wider problem? When several services fail together, the status page names a probable root cause.
        RootCause rootCause = open ? statusService.overview().rootCause() : null;
        model.addAttribute("rootCause", rootCause != null && rootCause.affected().contains(incident.getService())
                && !rootCause.service().equals(incident.getService()) ? rootCause : null);
        KnowledgeArticle article = knowledge.writtenFrom(incident).orElse(null);
        model.addAttribute("knowledgeArticle", article);
        model.addAttribute("canWriteArticle", article == null && incident.getResolution() != null && !open);
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
