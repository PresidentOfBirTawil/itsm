package com.itsm.web;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Pages for technicians. Everything under /technician needs the TECHNICIAN role (see SecurityConfig). */
@Controller
@RequestMapping("/technician")
public class TechnicianController {

    private static final String INCIDENTS = "/technician/incidents";

    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;
    private final IncidentPages incidentPages;

    public TechnicianController(TechnicianRepository technicians, IncidentRepository incidents,
                                IncidentPages incidentPages) {
        this.technicians = technicians;
        this.incidents = incidents;
        this.incidentPages = incidentPages;
    }

    @GetMapping
    public String index() {
        return "redirect:/technician/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        Technician technician = currentTechnician(user);
        List<Incident> queue = queueOf(technician);
        model.addAttribute("technician", technician);
        model.addAttribute("queue", queue);
        model.addAttribute("activeCount", queue.size());
        // How full the technician's queue is, as a percentage for the progress bar (capped at 100).
        model.addAttribute("workloadPercent", Math.min(100, queue.size() * 100 / technician.getMaxWorkload()));
        return "technician/dashboard";
    }

    /** The queue (open incidents, soonest deadline first) and below it the incidents that are finished. */
    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        Technician technician = currentTechnician(user);
        model.addAttribute("queue", queueOf(technician));
        model.addAttribute("finished", incidents.findByAssigneeIdAndStatusInOrderByUpdatedAtDescIdDesc(
                technician.getId(), Status.finishedStatuses()));
        return "technician/incidents";
    }

    /** "My queue": the technician's open incidents, the one that must be resolved soonest at the top. */
    private List<Incident> queueOf(Technician technician) {
        return incidents.findByAssigneeIdAndStatusInOrderByResolutionDueAscIdAsc(
                technician.getId(), Status.openStatuses());
    }

    // ---- one incident ----

    @GetMapping("/incidents/{code}")
    public String incident(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code, Model model) {
        return incidentPages.show(model, code, currentTechnician(user), INCIDENTS);
    }

    @PostMapping("/incidents/{code}/transition")
    public String changeStatus(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                               @RequestParam Status target, @RequestParam(required = false) String note,
                               RedirectAttributes redirect) {
        return incidentPages.changeStatus(code, target, note, currentTechnician(user), INCIDENTS, redirect);
    }

    private Technician currentTechnician(ItsmUserDetails user) {
        return technicians.findWithSkillsById(user.getId()).orElseThrow();
    }
}
