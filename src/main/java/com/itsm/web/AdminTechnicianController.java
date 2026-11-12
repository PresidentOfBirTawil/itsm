package com.itsm.web;

import java.util.LinkedHashSet;
import java.util.List;

import com.itsm.assignment.AssignmentService;
import com.itsm.domain.Administrator;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.Role;
import com.itsm.domain.Technician;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.error.NotFoundException;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import com.itsm.security.ItsmUserDetails;
import com.itsm.security.SignedInSessions;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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

/** Administrators manage the people: a list of all users, and the technicians' skills, availability and workload. */
@Controller
@RequestMapping("/admin")
public class AdminTechnicianController {

    private final UserRepository users;
    private final AdministratorRepository administrators;
    private final TechnicianRepository technicians;
    private final EmployeeRepository employees;
    private final CategoryRepository categories;
    private final AssignmentService assignmentService;
    private final PasswordEncoder passwordEncoder;
    private final SignedInSessions signedInSessions;

    public AdminTechnicianController(UserRepository users, AdministratorRepository administrators,
                                     TechnicianRepository technicians, EmployeeRepository employees,
                                     CategoryRepository categories, AssignmentService assignmentService,
                                     PasswordEncoder passwordEncoder, SignedInSessions signedInSessions) {
        this.users = users;
        this.administrators = administrators;
        this.technicians = technicians;
        this.employees = employees;
        this.categories = categories;
        this.assignmentService = assignmentService;
        this.passwordEncoder = passwordEncoder;
        this.signedInSessions = signedInSessions;
    }

    // ---- all users ----

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("administrators", administrators.findAllByOrderByNameAsc());
        model.addAttribute("technicians", technicians.findAllByOrderByNameAsc());
        model.addAttribute("employees", employees.findAllByOrderByNameAsc());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        UserForm form = new UserForm();
        form.setRole(Role.EMPLOYEE);
        model.addAttribute("form", form);
        return "admin/user-form";
    }

    /** Adds an employee or an administrator. Technicians are added on their own page, with their skills. */
    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute("form") UserForm form, BindingResult result,
                             RedirectAttributes redirect) {
        if (form.getRole() == Role.TECHNICIAN) {
            result.rejectValue("role", "technician", "Add technicians on the Technicians page, where their skills are set");
        }
        if (!result.hasFieldErrors("email") && users.existsByEmail(User.normaliseEmail(form.getEmail()))) {
            result.rejectValue("email", "duplicate", "An account with this email address already exists");
        }
        if (result.hasErrors()) {
            return "admin/user-form";
        }
        String hash = passwordEncoder.encode(form.getPassword());
        User user = form.getRole() == Role.ADMIN
                ? new Administrator(form.getName(), form.getEmail(), hash)
                : new Employee(form.getName(), form.getEmail(), hash);
        users.save(user);
        redirect.addFlashAttribute("success", "%s %s has been added."
                .formatted(user.getRole().getLabel(), user.getName()));
        return "redirect:/admin/users";
    }

    /** Disables an account (the person can no longer sign in) or enables it again. */
    @PostMapping("/users/{id}/enabled")
    public String setEnabled(@AuthenticationPrincipal ItsmUserDetails current, @PathVariable Long id,
                             @RequestParam boolean enabled, RedirectAttributes redirect) {
        User user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("There is no user with the number " + id + "."));
        if (!enabled && user.getId().equals(current.getId())) {
            throw new InvalidInputException("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        users.save(user);
        if (!enabled) {
            signedInSessions.signOut(user); // a disabled account must not keep working in an open browser
        }
        redirect.addFlashAttribute("success", enabled
                ? "%s can sign in again.".formatted(user.getName())
                : "%s has been disabled and can no longer sign in.".formatted(user.getName()));
        return "redirect:/admin/users";
    }

    // ---- technicians ----

    @GetMapping("/technicians")
    public String technicians(Model model) {
        model.addAttribute("loads", assignmentService.technicianLoads());
        return "admin/technicians";
    }

    @GetMapping("/technicians/new")
    public String newTechnician(Model model) {
        NewTechnicianForm form = new NewTechnicianForm();
        form.setAvailable(true);
        model.addAttribute("form", form);
        return technicianForm(model, "/admin/technicians", "Add a technician", true);
    }

    @PostMapping("/technicians")
    public String createTechnician(@Valid @ModelAttribute("form") NewTechnicianForm form, BindingResult result,
                                   Model model, RedirectAttributes redirect) {
        if (!result.hasFieldErrors("email") && users.existsByEmail(User.normaliseEmail(form.getEmail()))) {
            result.rejectValue("email", "duplicate", "An account with this email address already exists");
        }
        if (result.hasErrors()) {
            return technicianForm(model, "/admin/technicians", "Add a technician", true);
        }
        Technician technician = new Technician(form.getName(), form.getEmail(),
                passwordEncoder.encode(form.getPassword()), form.getMaxWorkload(),
                new LinkedHashSet<>(skills(form)));
        technician.setAvailable(form.isAvailable());
        technicians.save(technician);
        redirect.addFlashAttribute("success", "Technician %s has been added.".formatted(technician.getName()));
        return "redirect:/admin/technicians";
    }

    @GetMapping("/technicians/{id}/edit")
    public String editTechnician(@PathVariable Long id, Model model) {
        Technician technician = technician(id);
        TechnicianForm form = new TechnicianForm();
        form.setName(technician.getName());
        form.setMaxWorkload(technician.getMaxWorkload());
        form.setAvailable(technician.isAvailable());
        form.setSkillIds(technician.getSkills().stream().map(Category::getId).toList());
        model.addAttribute("form", form);
        return technicianForm(model, "/admin/technicians/" + id, "Change " + technician.getName(), false);
    }

    @PostMapping("/technicians/{id}")
    public String updateTechnician(@PathVariable Long id, @Valid @ModelAttribute("form") TechnicianForm form,
                                   BindingResult result, Model model, RedirectAttributes redirect) {
        Technician technician = technician(id);
        if (result.hasErrors()) {
            return technicianForm(model, "/admin/technicians/" + id, "Change " + technician.getName(), false);
        }
        technician.setName(form.getName());
        technician.setMaxWorkload(form.getMaxWorkload());
        technician.setAvailable(form.isAvailable());
        technician.replaceSkills(skills(form));
        technicians.save(technician);
        redirect.addFlashAttribute("success", "%s has been saved.".formatted(technician.getName()));
        return "redirect:/admin/technicians";
    }

    private String technicianForm(Model model, String action, String heading, boolean isNew) {
        model.addAttribute("action", action);
        model.addAttribute("heading", heading);
        model.addAttribute("isNew", isNew);
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        return "admin/technician-form";
    }

    private List<Category> skills(TechnicianForm form) {
        return categories.findAllById(form.getSkillIds());
    }

    private Technician technician(Long id) {
        return technicians.findWithSkillsById(id)
                .orElseThrow(() -> new NotFoundException("There is no technician with the number " + id + "."));
    }
}
