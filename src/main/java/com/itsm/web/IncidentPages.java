package com.itsm.web;

import java.util.List;
import java.util.stream.Collectors;

import com.itsm.BusinessRuleException;
import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.User;
import com.itsm.domain.state.Transition;
import com.itsm.priority.PriorityCalculator;
import com.itsm.service.IncidentService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The incident page and its status buttons work the same way for all three
 * roles, so the three role controllers share this helper instead of each
 * repeating the code. Only the URL prefix differs (/employee, /technician, /admin).
 */
@Component
public class IncidentPages {

    static final String DETAIL_VIEW = "incidents/detail";

    private final IncidentService incidentService;
    private final PriorityCalculator priorityCalculator;

    public IncidentPages(IncidentService incidentService, PriorityCalculator priorityCalculator) {
        this.incidentService = incidentService;
        this.priorityCalculator = priorityCalculator;
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
        List<Transition> transitions = incident.getState().transitionsFor(viewer.getRole());

        model.addAttribute("incident", incident);
        model.addAttribute("history", incidentService.history(incident));
        model.addAttribute("viewerRole", viewer.getRole());
        model.addAttribute("transitions", transitions);
        // The buttons that need a note, by their text, e.g. "Resolve, Request information". Empty if none does.
        model.addAttribute("noteNeededFor", transitions.stream()
                .filter(Transition::noteRequired)
                .map(transition -> transition.labelFor(viewer.getRole()))
                .collect(Collectors.joining(", ")));
        model.addAttribute("basePath", basePath);
        model.addAttribute("priorityRule", priorityCalculator.describeRule());
        return DETAIL_VIEW;
    }

    /**
     * Handles a click on a status button, then goes back to the incident page.
     * A refused move is shown there as an error message; nothing has changed.
     */
    public String changeStatus(String code, Status target, String note, User actor, String basePath,
                               RedirectAttributes redirect) {
        try {
            Incident incident = incidentService.changeStatus(code, target, actor, note);
            redirect.addFlashAttribute("success",
                    "%s is now %s.".formatted(incident.getCode(), incident.getStatus().getLabel().toLowerCase()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + basePath + "/" + code;
    }
}
