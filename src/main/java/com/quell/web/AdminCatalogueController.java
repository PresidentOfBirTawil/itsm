package com.quell.web;

import com.quell.domain.Category;
import com.quell.domain.ITService;
import com.quell.domain.SlaPolicy;
import com.quell.error.NotFoundException;
import com.quell.error.ServiceNotFoundException;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.SlaPolicyRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrators manage the things incidents refer to: IT services, categories and SLA policies. */
@Controller
@RequestMapping("/admin")
public class AdminCatalogueController {

    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;

    public AdminCatalogueController(ITServiceRepository services, CategoryRepository categories,
                                    SlaPolicyRepository slaPolicies) {
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
    }

    // ---- IT services ----

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        return "admin/services";
    }

    @GetMapping("/services/new")
    public String newService(Model model) {
        model.addAttribute("form", new ServiceForm());
        return serviceForm(model, "/admin/services", "Add an IT service", null);
    }

    private String serviceForm(Model model, String action, String heading, Long editingId) {
        model.addAttribute("action", action);
        model.addAttribute("heading", heading);
        // Every other service can be a dependency; the loop check happens when the form is saved.
        model.addAttribute("otherServices", services.findAllByOrderByCriticalityDescNameAsc().stream()
                .filter(other -> !other.getId().equals(editingId)).toList());
        return "admin/service-form";
    }

    /** The chosen dependencies, or a form error if they would create a loop. */
    private java.util.List<ITService> dependencies(ITService service, ServiceForm form, BindingResult result) {
        java.util.List<ITService> chosen = services.findAllById(form.getDependencyIds());
        if (service != null) {
            com.quell.status.ServiceGraph.loopIfDependsOn(service, chosen).ifPresent(loop -> result.rejectValue(
                    "dependencyIds", "loop", "%s cannot depend on %s: %s already depends on %s, so they would wait for each other."
                            .formatted(service.getName(), loop.getName(), loop.getName(), service.getName())));
        }
        return chosen;
    }

    @PostMapping("/services")
    public String createService(@Valid @ModelAttribute("form") ServiceForm form, BindingResult result, Model model,
                                RedirectAttributes redirect) {
        if (!result.hasFieldErrors("name") && services.existsByNameIgnoreCase(form.getName().trim())) {
            result.rejectValue("name", "duplicate", "There is already a service with this name");
        }
        java.util.List<ITService> needs = dependencies(null, form, result);
        if (result.hasErrors()) {
            return serviceForm(model, "/admin/services", "Add an IT service", null);
        }
        ITService created = new ITService(form.getName(), form.getCriticality());
        created.replaceKeywords(form.keywordList());
        created.replaceDependencies(needs);
        services.save(created);
        redirect.addFlashAttribute("success", "Service \"%s\" has been added.".formatted(form.getName().trim()));
        return "redirect:/admin/services";
    }

    @GetMapping("/services/{id}/edit")
    public String editService(@PathVariable Long id, Model model) {
        ITService service = service(id);
        ServiceForm form = new ServiceForm();
        form.setName(service.getName());
        form.setCriticality(service.getCriticality());
        form.setKeywords(String.join(", ", service.getKeywords()));
        form.setDependencyIds(service.getDependsOn().stream().map(ITService::getId).toList());
        model.addAttribute("form", form);
        return serviceForm(model, "/admin/services/" + id, "Change " + service.getName(), id);
    }

    @PostMapping("/services/{id}")
    public String updateService(@PathVariable Long id, @Valid @ModelAttribute("form") ServiceForm form,
                                BindingResult result, Model model, RedirectAttributes redirect) {
        ITService service = service(id);
        if (!result.hasFieldErrors("name") && services.existsByNameIgnoreCaseAndIdNot(form.getName().trim(), id)) {
            result.rejectValue("name", "duplicate", "There is already a service with this name");
        }
        java.util.List<ITService> needs = dependencies(service, form, result);
        if (result.hasErrors()) {
            return serviceForm(model, "/admin/services/" + id, "Change " + service.getName(), id);
        }
        service.setName(form.getName());
        service.setCriticality(form.getCriticality());
        service.replaceKeywords(form.keywordList());
        service.replaceDependencies(needs);
        services.save(service);
        redirect.addFlashAttribute("success", "Service \"%s\" has been saved.".formatted(service.getName()));
        return "redirect:/admin/services";
    }

    private ITService service(Long id) {
        return services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    // ---- categories ----

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        return "admin/categories";
    }

    @GetMapping("/categories/new")
    public String newCategory(Model model) {
        model.addAttribute("form", new CategoryForm());
        model.addAttribute("action", "/admin/categories");
        model.addAttribute("heading", "Add a category");
        return "admin/category-form";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("form") CategoryForm form, BindingResult result,
                                 Model model, RedirectAttributes redirect) {
        if (!result.hasFieldErrors("name") && categories.existsByNameIgnoreCase(form.getName().trim())) {
            result.rejectValue("name", "duplicate", "There is already a category with this name");
        }
        if (result.hasErrors()) {
            model.addAttribute("action", "/admin/categories");
            model.addAttribute("heading", "Add a category");
            return "admin/category-form";
        }
        categories.save(new Category(form.getName(), form.keywordList()));
        redirect.addFlashAttribute("success", "Category \"%s\" has been added.".formatted(form.getName().trim()));
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable Long id, Model model) {
        Category category = category(id);
        CategoryForm form = new CategoryForm();
        form.setName(category.getName());
        form.setKeywords(String.join(", ", category.getKeywords()));
        model.addAttribute("form", form);
        model.addAttribute("action", "/admin/categories/" + id);
        model.addAttribute("heading", "Change " + category.getName());
        return "admin/category-form";
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable Long id, @Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult result, Model model, RedirectAttributes redirect) {
        Category category = category(id);
        if (!result.hasFieldErrors("name") && categories.existsByNameIgnoreCaseAndIdNot(form.getName().trim(), id)) {
            result.rejectValue("name", "duplicate", "There is already a category with this name");
        }
        if (result.hasErrors()) {
            model.addAttribute("action", "/admin/categories/" + id);
            model.addAttribute("heading", "Change " + category.getName());
            return "admin/category-form";
        }
        category.setName(form.getName());
        category.replaceKeywords(form.keywordList());
        categories.save(category);
        redirect.addFlashAttribute("success", "Category \"%s\" has been saved.".formatted(category.getName()));
        return "redirect:/admin/categories";
    }

    private Category category(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("There is no category with the number " + id + "."));
    }

    // ---- SLA policies ----

    @GetMapping("/sla-policies")
    public String slaPolicies(Model model) {
        model.addAttribute("slaPolicies", slaPolicies.findAllByOrderByPriorityAsc());
        return "admin/sla-policies";
    }

    /** Each row of the SLA table is its own small form. Problems come back as a message above the table. */
    @PostMapping("/sla-policies/{id}")
    public String updateSlaPolicy(@PathVariable Long id, @Valid @ModelAttribute SlaPolicyForm form,
                                  BindingResult result, RedirectAttributes redirect) {
        SlaPolicy policy = slaPolicies.findById(id)
                .orElseThrow(() -> new NotFoundException("There is no SLA policy with the number " + id + "."));
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", result.getAllErrors().getFirst().getDefaultMessage() + ".");
        } else if (form.getResponseMinutes() > form.getResolutionMinutes()) {
            redirect.addFlashAttribute("error", "The response target cannot be longer than the resolution target.");
        } else {
            policy.setTargets(form.getResponseMinutes(), form.getResolutionMinutes());
            slaPolicies.save(policy);
            redirect.addFlashAttribute("success", "The SLA policy for %s has been saved. "
                    .formatted(policy.getPriority()) + "It applies to incidents reported from now on.");
        }
        return "redirect:/admin/sla-policies";
    }
}
