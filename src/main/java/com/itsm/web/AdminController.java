package com.itsm.web;

import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Pages for administrators. Everything under /admin needs the ADMIN role (see SecurityConfig). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository users;
    private final AdministratorRepository administrators;
    private final TechnicianRepository technicians;
    private final EmployeeRepository employees;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final IncidentRepository incidents;

    public AdminController(UserRepository users, AdministratorRepository administrators,
                           TechnicianRepository technicians, EmployeeRepository employees,
                           ITServiceRepository services, CategoryRepository categories,
                           SlaPolicyRepository slaPolicies, IncidentRepository incidents) {
        this.users = users;
        this.administrators = administrators;
        this.technicians = technicians;
        this.employees = employees;
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.incidents = incidents;
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
        model.addAttribute("technicians", technicians.findAllByOrderByNameAsc());
        model.addAttribute("slaPolicies", slaPolicies.findAllByOrderByPriorityAsc());
        return "admin/dashboard";
    }

    @GetMapping("/incidents")
    public String incidents(Model model) {
        model.addAttribute("heading", "All incidents");
        model.addAttribute("subheading", "Every incident in the system, newest first.");
        model.addAttribute("emptyMessage", "No incidents have been reported yet.");
        model.addAttribute("incidents", incidents.findAllByOrderByCreatedAtDesc());
        return "incidents/list";
    }

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
}
