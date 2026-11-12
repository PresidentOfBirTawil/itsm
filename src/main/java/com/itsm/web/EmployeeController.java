package com.itsm.web;

import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Pages for employees. Everything under /employee needs the EMPLOYEE role (see SecurityConfig). */
@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final EmployeeRepository employees;
    private final IncidentRepository incidents;
    private final ITServiceRepository services;

    public EmployeeController(EmployeeRepository employees, IncidentRepository incidents,
                              ITServiceRepository services) {
        this.employees = employees;
        this.incidents = incidents;
        this.services = services;
    }

    @GetMapping
    public String index() {
        return "redirect:/employee/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        List<Incident> mine = incidents.findByReporterIdOrderByCreatedAtDesc(user.getId());
        long open = incidents.countByReporterIdAndStatusIn(user.getId(), Status.openStatuses());
        model.addAttribute("employee", employees.findById(user.getId()).orElseThrow());
        model.addAttribute("incidents", mine);
        model.addAttribute("openCount", open);
        model.addAttribute("doneCount", mine.size() - open);
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        return "employee/dashboard";
    }

    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        model.addAttribute("heading", "My incidents");
        model.addAttribute("subheading", "Every incident you have reported, newest first.");
        model.addAttribute("emptyMessage", "You have not reported any incidents yet.");
        model.addAttribute("incidents", incidents.findByReporterIdOrderByCreatedAtDesc(user.getId()));
        return "incidents/list";
    }
}
