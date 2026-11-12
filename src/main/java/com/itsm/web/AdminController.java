package com.itsm.web;

import com.itsm.BusinessRuleException;
import com.itsm.assignment.AssignmentService;
import com.itsm.assignment.AssignmentStrategy;
import com.itsm.domain.Administrator;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Status;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import com.itsm.security.ItsmUserDetails;
import com.itsm.service.IncidentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Pages for administrators. Everything under /admin needs the ADMIN role (see SecurityConfig). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String INCIDENTS = "/admin/incidents";

    private final UserRepository users;
    private final AdministratorRepository administrators;
    private final TechnicianRepository technicians;
    private final EmployeeRepository employees;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final IncidentRepository incidents;
    private final AuditEntryRepository auditEntries;
    private final IncidentService incidentService;
    private final AssignmentService assignmentService;
    private final IncidentPages incidentPages;

    public AdminController(UserRepository users, AdministratorRepository administrators,
                           TechnicianRepository technicians, EmployeeRepository employees,
                           ITServiceRepository services, CategoryRepository categories,
                           SlaPolicyRepository slaPolicies, IncidentRepository incidents,
                           AuditEntryRepository auditEntries, IncidentService incidentService,
                           AssignmentService assignmentService, IncidentPages incidentPages) {
        this.users = users;
        this.administrators = administrators;
        this.technicians = technicians;
        this.employees = employees;
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.incidents = incidents;
        this.auditEntries = auditEntries;
        this.incidentService = incidentService;
        this.assignmentService = assignmentService;
        this.incidentPages = incidentPages;
    }

    /**
     * Added to every admin page: how many incidents are waiting because no
     * technician could be found. The navbar shows it as a warning badge.
     */
    @ModelAttribute("waitingCount")
    public long waitingCount() {
        return assignmentService.countWaitingForTechnician();
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("userCount", users.count());
        model.addAttribute("serviceCount", services.count());
        model.addAttribute("categoryCount", categories.count());
        model.addAttribute("incidentCount", incidents.count());
        model.addAttribute("waiting", assignmentService.waitingForTechnician());
        model.addAttribute("loads", assignmentService.technicianLoads());
        model.addAttribute("activeStrategy", assignmentService.getActiveStrategy());
        model.addAttribute("slaPolicies", slaPolicies.findAllByOrderByPriorityAsc());
        return "admin/dashboard";
    }

    // ---- incidents ----

    @GetMapping("/incidents")
    public String incidents(Model model) {
        model.addAttribute("heading", "All incidents");
        model.addAttribute("subheading", "Every incident in the system, newest first.");
        model.addAttribute("emptyMessage", "No incidents have been reported yet.");
        model.addAttribute("columns", "service,priority,status,reporter,assignee,reported");
        model.addAttribute("basePath", INCIDENTS);
        model.addAttribute("incidents", incidents.findAllByOrderByCreatedAtDescIdDesc());
        return "incidents/list";
    }

    @GetMapping("/incidents/{code}")
    public String incident(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code, Model model) {
        String view = incidentPages.show(model, code, currentAdministrator(user), INCIDENTS);

        // Extra for administrators: the assignment box.
        Incident incident = (Incident) model.getAttribute("incident");
        model.addAttribute("loads", assignmentService.technicianLoads());
        model.addAttribute("activeStrategy", assignmentService.getActiveStrategy());
        if (incident.getAssignee() == null) {
            AuditEntry lastFailure = auditEntries.findFirstByIncidentIdAndActionOrderByOccurredAtDescIdDesc(
                    incident.getId(), IncidentAction.ASSIGNMENT_FAILED);
            model.addAttribute("assignmentWarning", lastFailure == null ? null : lastFailure.getNote());
        }
        return view;
    }

    @PostMapping("/incidents/{code}/transition")
    public String changeStatus(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                               @RequestParam Status target, @RequestParam(required = false) String note,
                               RedirectAttributes redirect) {
        return incidentPages.changeStatus(code, target, note, currentAdministrator(user), INCIDENTS, redirect);
    }

    /** Assign, or reassign, by hand. */
    @PostMapping("/incidents/{code}/assign")
    public String assign(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                         @RequestParam(required = false) Long technicianId, RedirectAttributes redirect) {
        try {
            Incident incident = incidentService.assign(code, technicianId, currentAdministrator(user));
            redirect.addFlashAttribute("success",
                    "%s is now assigned to %s.".formatted(code, incident.getAssignee().getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + INCIDENTS + "/" + code;
    }

    /** Let the active strategy try again for an incident that has no technician. */
    @PostMapping("/incidents/{code}/auto-assign")
    public String autoAssign(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                             RedirectAttributes redirect) {
        try {
            Incident incident = incidentService.autoAssign(code, currentAdministrator(user));
            if (incident.getAssignee() != null) {
                redirect.addFlashAttribute("success",
                        "%s is now assigned to %s.".formatted(code, incident.getAssignee().getName()));
            } else {
                redirect.addFlashAttribute("error",
                        "No technician could be found for %s. The timeline shows why.".formatted(code));
            }
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + INCIDENTS + "/" + code;
    }

    // ---- settings ----

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("strategies", assignmentService.getStrategies());
        model.addAttribute("activeStrategy", assignmentService.getActiveStrategy());
        return "admin/settings";
    }

    /** Switches the assignment strategy. It applies to the very next incident; no restart is needed. */
    @PostMapping("/settings/assignment-strategy")
    public String useStrategy(@RequestParam String key, RedirectAttributes redirect) {
        try {
            AssignmentStrategy strategy = assignmentService.useStrategy(key);
            redirect.addFlashAttribute("success",
                    "New incidents are now assigned with the %s strategy.".formatted(strategy.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    // ---- reference data ----

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("administrators", administrators.findAllByOrderByNameAsc());
        model.addAttribute("technicians", technicians.findAllByOrderByNameAsc());
        model.addAttribute("employees", employees.findAllByOrderByNameAsc());
        return "admin/users";
    }

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        return "admin/services";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        return "admin/categories";
    }

    @GetMapping("/sla-policies")
    public String slaPolicies(Model model) {
        model.addAttribute("slaPolicies", slaPolicies.findAllByOrderByPriorityAsc());
        return "admin/sla-policies";
    }

    private Administrator currentAdministrator(ItsmUserDetails user) {
        return administrators.findById(user.getId()).orElseThrow();
    }
}
