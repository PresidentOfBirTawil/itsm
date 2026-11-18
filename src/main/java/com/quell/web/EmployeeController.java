package com.quell.web;

import java.io.IOException;
import java.util.List;

import com.quell.attachment.AttachmentService;
import com.quell.error.InvalidInputException;
import com.quell.error.QuellException;
import com.quell.error.NotFoundException;
import com.quell.domain.Employee;
import com.quell.domain.Impact;
import com.quell.domain.Incident;
import com.quell.domain.Status;
import com.quell.domain.Urgency;
import com.quell.repository.CategoryRepository;
import com.quell.repository.EmployeeRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.IncidentRepository;
import com.quell.security.QuellUserDetails;
import com.quell.intelligence.ReportAdvice;
import com.quell.intelligence.ReportAssistant;
import com.quell.service.IncidentService;
import com.quell.status.StatusService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
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
    private final ReportAssistant reportAssistant;
    private final StatusService statusService;
    private final AttachmentService attachmentService;

    public EmployeeController(EmployeeRepository employees, IncidentRepository incidents,
                              ITServiceRepository services, CategoryRepository categories,
                              IncidentService incidentService, IncidentPages incidentPages,
                              ReportAssistant reportAssistant, StatusService statusService,
                              AttachmentService attachmentService) {
        this.employees = employees;
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.incidentService = incidentService;
        this.incidentPages = incidentPages;
        this.reportAssistant = reportAssistant;
        this.statusService = statusService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public String index() {
        return "redirect:/employee/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal QuellUserDetails user, Model model) {
        List<Incident> mine = incidents.findByReporterIdOrderByCreatedAtDescIdDesc(user.getId());
        long open = incidents.countByReporterIdAndStatusIn(user.getId(), Status.openStatuses());
        model.addAttribute("employee", currentEmployee(user));
        model.addAttribute("incidents", mine);
        model.addAttribute("openCount", open);
        model.addAttribute("doneCount", mine.size() - open);
        // Incidents where the next step is the employee's: a question to answer, or a fix to confirm.
        model.addAttribute("waitingForMe", mine.stream()
                .filter(incident -> incident.getStatus() == Status.AWAITING_INFO
                        || incident.getStatus() == Status.RESOLVED)
                .toList());
        model.addAttribute("alsoAffecting", incidents.findByAffectedUsersIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream().filter(incident -> incident.getStatus().isOpen()).toList());
        model.addAttribute("status", statusService.overview());
        return "employee/dashboard";
    }

    // ---- my incidents ----

    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal QuellUserDetails user, Model model) {
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
    public String report(@AuthenticationPrincipal QuellUserDetails user,
                         @Valid @ModelAttribute("form") IncidentForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        // The screenshot is checked first, so a wrong file never leaves a half-finished report behind.
        byte[] screenshot = null;
        MultipartFile file = form.getScreenshot();
        if (file != null && !file.isEmpty()) {
            try {
                screenshot = file.getBytes();
                attachmentService.check(file.getOriginalFilename(), screenshot);
            } catch (IOException | QuellException e) {
                result.rejectValue("screenshot", "badFile",
                        (e instanceof QuellException ? e.getMessage() : "The file could not be read.") + " Please choose it again.");
            }
        }
        if (result.hasErrors()) {
            addFormChoices(model);
            return "employee/incident-form";
        }
        try {
            Employee employee = currentEmployee(user);
            Incident incident = incidentService.report(employee, form.getTitle(), form.getDescription(),
                    form.getServiceId(), form.getCategoryId(), form.getImpact(), form.getUrgency());
            if (screenshot != null) {
                attachmentService.attach(incident.getCode(), employee, file.getOriginalFilename(), screenshot);
            }
            redirect.addFlashAttribute("success", "Incident %s has been reported.".formatted(incident.getCode()));
            return "redirect:" + INCIDENTS + "/" + incident.getCode();
        } catch (NotFoundException e) {
            throw e; // a service or category that does not exist: the 404 page
        } catch (QuellException e) {
            // Any other broken rule: back to the form, with what was typed still in it.
            if (form.getCategoryId() == null && e instanceof InvalidInputException) {
                result.rejectValue("categoryId", "noSuggestion", e.getMessage());
            } else {
                model.addAttribute("error", e.getMessage());
            }
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

    /** Live hints for the report form, as JSON: see {@link ReportAssistant}. */
    @GetMapping("/incidents/assist")
    @ResponseBody
    public ReportAdvice assist(@RequestParam(defaultValue = "") String title,
                               @RequestParam(defaultValue = "") String description,
                               @RequestParam(required = false) Long serviceId) {
        return reportAssistant.advise(title, description, serviceId);
    }

    /** "I'm affected too": the employee follows a known problem instead of reporting it again. */
    @PostMapping("/incidents/{code}/affected")
    public String affected(@AuthenticationPrincipal QuellUserDetails user, @PathVariable String code,
                           RedirectAttributes redirect) {
        return incidentPages.perform(INCIDENTS + "/" + code, redirect, () -> {
            Incident incident = incidentService.joinAsAffected(code, currentEmployee(user));
            int others = incident.getAffectedCount() - 1;
            return "You are now following %s with %d other %s. You will be told when it is fixed."
                    .formatted(code, others, others == 1 ? "person" : "people");
        });
    }

    @GetMapping("/incidents/{code}")
    public String incident(@AuthenticationPrincipal QuellUserDetails user, @PathVariable String code, Model model) {
        return incidentPages.show(model, code, currentEmployee(user), INCIDENTS);
    }

    @PostMapping("/incidents/{code}/transition")
    public String changeStatus(@AuthenticationPrincipal QuellUserDetails user, @PathVariable String code,
                               @Valid @ModelAttribute TransitionForm form, RedirectAttributes redirect) {
        return incidentPages.changeStatus(code, form, currentEmployee(user), INCIDENTS, redirect);
    }

    @PostMapping("/incidents/{code}/notes")
    public String addNote(@AuthenticationPrincipal QuellUserDetails user, @PathVariable String code,
                          @Valid @ModelAttribute NoteForm form, RedirectAttributes redirect) {
        return incidentPages.addNote(code, form, currentEmployee(user), INCIDENTS, redirect);
    }

    /** The reporter says how good the resolution was, from 1 to 5. */
    @PostMapping("/incidents/{code}/rating")
    public String rate(@AuthenticationPrincipal QuellUserDetails user, @PathVariable String code,
                       @Valid @ModelAttribute RatingForm form, RedirectAttributes redirect) {
        return incidentPages.perform(INCIDENTS + "/" + code, redirect, () -> {
            incidentService.rate(code, currentEmployee(user), form.getRating(), form.getComment());
            return "Thank you. Your rating of %s has been recorded.".formatted(code);
        });
    }

    private Employee currentEmployee(QuellUserDetails user) {
        return employees.findById(user.getId()).orElseThrow();
    }
}
