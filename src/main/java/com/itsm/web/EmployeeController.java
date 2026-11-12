package com.itsm.web;

import java.util.List;

import com.itsm.BusinessRuleException;
import com.itsm.domain.Employee;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.Urgency;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.security.ItsmUserDetails;
import com.itsm.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Pages for employees. Everything under /employee needs the EMPLOYEE role (see SecurityConfig). */
@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private static final String INCIDENTS = "/employee/incidents";

    private final EmployeeRepository employees;
    private final IncidentRepository incidents;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final IncidentService incidentService;
    private final IncidentPages incidentPages;

    public EmployeeController(EmployeeRepository employees, IncidentRepository incidents,
                              ITServiceRepository services, CategoryRepository categories,
                              IncidentService incidentService, IncidentPages incidentPages) {
        this.employees = employees;
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.incidentService = incidentService;
        this.incidentPages = incidentPages;
    }

    @GetMapping
    public String index() {
        return "redirect:/employee/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        List<Incident> mine = incidents.findByReporterIdOrderByCreatedAtDescIdDesc(user.getId());
        long open = incidents.countByReporterIdAndStatusIn(user.getId(), Status.openStatuses());
        model.addAttribute("employee", currentEmployee(user));
        model.addAttribute("incidents", mine);
        model.addAttribute("openCount", open);
        model.addAttribute("doneCount", mine.size() - open);
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        return "employee/dashboard";
    }

    // ---- my incidents ----

    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        model.addAttribute("heading", "My incidents");
        model.addAttribute("subheading", "Every incident you have reported, newest first.");
        model.addAttribute("emptyMessage", "You have not reported any incidents yet.");
        model.addAttribute("columns", "service,category,priority,status,sla,assignee,reported");
        model.addAttribute("basePath", INCIDENTS);
        model.addAttribute("canReport", true);
        model.addAttribute("incidents", incidents.findByReporterIdOrderByCreatedAtDescIdDesc(user.getId()));
        return "incidents/list";
    }

    // ---- reporting ----

    @GetMapping("/incidents/new")
    public String reportForm(Model model) {
        model.addAttribute("form", new IncidentForm());
        addFormChoices(model);
        return "employee/incident-form";
    }

    @PostMapping("/incidents")
    public String report(@AuthenticationPrincipal ItsmUserDetails user,
                         @Valid @ModelAttribute("form") IncidentForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            addFormChoices(model);
            return "employee/incident-form";
        }
        try {
            Incident incident = incidentService.report(currentEmployee(user), form.getTitle(), form.getDescription(),
                    form.getServiceId(), form.getCategoryId(), form.getImpact(), form.getUrgency());
            redirect.addFlashAttribute("success", "Incident %s has been reported.".formatted(incident.getCode()));
            return "redirect:" + INCIDENTS + "/" + incident.getCode();
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            addFormChoices(model);
            return "employee/incident-form";
        }
    }

    private void addFormChoices(Model model) {
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        model.addAttribute("impacts", Impact.values());
        model.addAttribute("urgencies", Urgency.values());
    }

    // ---- one incident ----

    @GetMapping("/incidents/{code}")
    public String incident(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code, Model model) {
        return incidentPages.show(model, code, currentEmployee(user), INCIDENTS);
    }

    @PostMapping("/incidents/{code}/transition")
    public String changeStatus(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                               @RequestParam Status target, @RequestParam(required = false) String note,
                               RedirectAttributes redirect) {
        return incidentPages.changeStatus(code, target, note, currentEmployee(user), INCIDENTS, redirect);
    }

    private Employee currentEmployee(ItsmUserDetails user) {
        return employees.findById(user.getId()).orElseThrow();
    }
}
