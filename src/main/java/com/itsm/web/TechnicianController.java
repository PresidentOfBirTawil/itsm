package com.itsm.web;

import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Pages for technicians. Everything under /technician needs the TECHNICIAN role (see SecurityConfig). */
@Controller
@RequestMapping("/technician")
public class TechnicianController {

    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;

    public TechnicianController(TechnicianRepository technicians, IncidentRepository incidents) {
        this.technicians = technicians;
        this.incidents = incidents;
    }

    @GetMapping
    public String index() {
        return "redirect:/technician/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        Technician technician = technicians.findWithSkillsById(user.getId()).orElseThrow();
        long activeCount = incidents.countByAssigneeIdAndStatusIn(user.getId(), Status.openStatuses());
        model.addAttribute("technician", technician);
        model.addAttribute("incidents", incidents.findByAssigneeIdOrderByCreatedAtDesc(user.getId()));
        model.addAttribute("activeCount", activeCount);
        // How full the technician's queue is, as a percentage for the progress bar (capped at 100).
        model.addAttribute("workloadPercent", Math.min(100, activeCount * 100 / technician.getMaxWorkload()));
        return "technician/dashboard";
    }

    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        model.addAttribute("heading", "My queue");
        model.addAttribute("subheading", "Every incident assigned to you, newest first.");
        model.addAttribute("emptyMessage", "No incidents are assigned to you.");
        model.addAttribute("incidents", incidents.findByAssigneeIdOrderByCreatedAtDesc(user.getId()));
        return "incidents/list";
    }
}
