package com.itsm.web;

import java.util.LinkedHashSet;
import java.util.List;

import com.itsm.assignment.AssignmentService;
import com.itsm.domain.Category;
import com.itsm.domain.Technician;
import com.itsm.domain.User;
import com.itsm.error.NotFoundException;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public AdminTechnicianController(UserRepository users, AdministratorRepository administrators,
                                     TechnicianRepository technicians, EmployeeRepository employees,
                                     CategoryRepository categories, AssignmentService assignmentService,
                                     PasswordEncoder passwordEncoder) {
        this.users = users;
        this.administrators = administrators;
        this.technicians = technicians;
        this.employees = employees;
        this.categories = categories;
        this.assignmentService = assignmentService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("administrators", administrators.findAllByOrderByNameAsc());
        model.addAttribute("technicians", technicians.findAllByOrderByNameAsc());
        model.addAttribute("employees", employees.findAllByOrderByNameAsc());
        return "admin/users";
    }

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
