package com.itsm.web;

import java.time.LocalDateTime;
import java.util.List;

import com.itsm.error.TechnicianUnavailableException;
import com.itsm.intelligence.SlaRiskRanker;
import com.itsm.assignment.AssignmentService;
import com.itsm.assignment.AssignmentStrategy;
import com.itsm.domain.Administrator;
import com.itsm.domain.AuditEntry;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.EmailMessageRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.ServiceIncidentCount;
import com.itsm.search.IncidentSearchCriteria;
import com.itsm.security.ItsmUserDetails;
import com.itsm.service.IncidentService;
import com.itsm.sla.SlaService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The administrator's dashboard, incidents, settings and email outbox.
 * Everything under /admin needs the ADMIN role (see SecurityConfig).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String INCIDENTS = "/admin/incidents";

    private final AdministratorRepository administrators;
    private final IncidentRepository incidents;
    private final AuditEntryRepository auditEntries;
    private final EmailMessageRepository emails;
    private final IncidentService incidentService;
    private final AssignmentService assignmentService;
    private final SlaService slaService;
    private final IncidentPages incidentPages;
    private final SlaRiskRanker slaRiskRanker;

    public AdminController(AdministratorRepository administrators, IncidentRepository incidents,
                           AuditEntryRepository auditEntries, EmailMessageRepository emails,
                           IncidentService incidentService, AssignmentService assignmentService,
                           SlaService slaService, IncidentPages incidentPages, SlaRiskRanker slaRiskRanker) {
        this.administrators = administrators;
        this.incidents = incidents;
        this.auditEntries = auditEntries;
        this.emails = emails;
        this.incidentService = incidentService;
        this.assignmentService = assignmentService;
        this.slaService = slaService;
        this.incidentPages = incidentPages;
        this.slaRiskRanker = slaRiskRanker;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/dashboard";
    }

    // ---- dashboard ----

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // The totals at the top.
        model.addAttribute("totalCount", incidents.count());
        model.addAttribute("openCount", incidents.countByStatusIn(Status.openStatuses()));
        model.addAttribute("criticalCount", incidents.countByPriorityAndStatusIn(Priority.P1, Status.openStatuses()));
        model.addAttribute("resolvedCount", incidents.countByStatusIn(Status.finishedStatuses()));
        model.addAttribute("breachCount", incidents.countBySlaStatus(SlaStatus.BREACHED));

        // Technician workload and incidents per service.
        model.addAttribute("loads", assignmentService.technicianLoads());
        List<ServiceIncidentCount> perService = incidents.countIncidentsPerService();
        model.addAttribute("perService", perService);
        model.addAttribute("busiestService",
                perService.stream().mapToLong(ServiceIncidentCount::incidents).max().orElse(0));

        model.addAttribute("waiting", assignmentService.waitingForTechnician());
        model.addAttribute("activeStrategy", assignmentService.getActiveStrategy());

        // Intelligent features: who is likely to miss a deadline next, and what has been escalated.
        model.addAttribute("slaRisks", slaRiskRanker.rank(incidents.findByStatusIn(Status.openStatuses()),
                LocalDateTime.now(), 5));
        model.addAttribute("escalated", incidents.findByEscalatedAtIsNotNullAndStatusInOrderByEscalatedAtDescIdDesc(
                Status.openStatuses()));
        return "admin/dashboard";
    }

    // ---- incidents ----

    /** The incident list. With no filters it shows everything; the form above the list narrows it down. */
    @GetMapping("/incidents")
    public String incidents(@Valid @ModelAttribute("criteria") IncidentSearchCriteria criteria,
                            BindingResult result, Model model) {
        model.addAttribute("heading", "Incidents");
        return incidentPages.search(model, criteria, result, INCIDENTS, INCIDENTS);
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
                               @Valid @ModelAttribute TransitionForm form, RedirectAttributes redirect) {
        return incidentPages.changeStatus(code, form, currentAdministrator(user), INCIDENTS, redirect);
    }

    @PostMapping("/incidents/{code}/notes")
    public String addNote(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                          @Valid @ModelAttribute NoteForm form, RedirectAttributes redirect) {
        return incidentPages.addNote(code, form, currentAdministrator(user), INCIDENTS, redirect);
    }

    /** Assign, or reassign, by hand. */
    @PostMapping("/incidents/{code}/assign")
    public String assign(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                         @Valid @ModelAttribute AssignForm form, BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", "Please choose a technician.");
            return "redirect:" + INCIDENTS + "/" + code;
        }
        return incidentPages.perform(INCIDENTS + "/" + code, redirect, () -> {
            Incident incident = incidentService.assign(code, form.getTechnicianId(), currentAdministrator(user));
            return "%s is now assigned to %s.".formatted(code, incident.getAssignee().getName());
        });
    }

    /** Let the active strategy try again for an incident that has no technician. */
    @PostMapping("/incidents/{code}/auto-assign")
    public String autoAssign(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable String code,
                             RedirectAttributes redirect) {
        return incidentPages.perform(INCIDENTS + "/" + code, redirect, () -> {
            Incident incident = incidentService.autoAssign(code, currentAdministrator(user));
            if (incident.getAssignee() == null) {
                throw new TechnicianUnavailableException(
                        "No technician could be found for %s. The timeline shows why.".formatted(code));
            }
            return "%s is now assigned to %s.".formatted(code, incident.getAssignee().getName());
        });
    }

    // ---- settings ----

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("strategies", assignmentService.getStrategies());
        model.addAttribute("activeStrategy", assignmentService.getActiveStrategy());
        model.addAttribute("demoSla", slaService.isDemoMode());
        model.addAttribute("demoPolicies", slaService.demoPolicies());
        return "admin/settings";
    }

    /** Switches the assignment strategy. It applies to the very next incident; no restart is needed. */
    @PostMapping("/settings/assignment-strategy")
    public String useStrategy(@Valid @ModelAttribute SettingsForms.Strategy form, RedirectAttributes redirect) {
        return incidentPages.perform("/admin/settings", redirect, () -> {
            AssignmentStrategy strategy = assignmentService.useStrategy(form.getKey());
            return "New incidents are now assigned with the %s strategy.".formatted(strategy.getName());
        });
    }

    /** Switches the short demo SLA deadlines on or off. */
    @PostMapping("/settings/demo-sla")
    public String demoSla(@Valid @ModelAttribute SettingsForms.DemoSla form, RedirectAttributes redirect) {
        slaService.setDemoMode(form.getEnabled());
        redirect.addFlashAttribute("success", form.getEnabled()
                ? "Demo SLA is on. Incidents reported from now on get the short demo deadlines."
                : "Demo SLA is off. Incidents reported from now on get the normal SLA policies.");
        return "redirect:/admin/settings";
    }

    /** Runs the SLA check now instead of waiting for the next minute. Handy while recording a demo. */
    @PostMapping("/settings/check-sla")
    public String checkSlaNow(RedirectAttributes redirect) {
        int checked = slaService.checkOpenIncidents(LocalDateTime.now());
        redirect.addFlashAttribute("success", checked == 1
                ? "The SLA check has run: 1 open incident checked."
                : "The SLA check has run: %d open incidents checked.".formatted(checked));
        return "redirect:/admin/settings";
    }

    // ---- email outbox ----

    /** The emails the system would have sent. They are simulated, so this page is where they can be read. */
    @GetMapping("/emails")
    public String emails(Model model) {
        model.addAttribute("emails", emails.findTop200ByOrderByCreatedAtDescIdDesc());
        return "admin/emails";
    }

    private Administrator currentAdministrator(ItsmUserDetails user) {
        return administrators.findById(user.getId()).orElseThrow();
    }
}
