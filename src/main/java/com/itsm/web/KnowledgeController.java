package com.itsm.web;

import java.util.List;
import java.util.Map;

import com.itsm.domain.Incident;
import com.itsm.domain.KnowledgeArticle;
import com.itsm.domain.Role;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.intelligence.ResolutionRecommender;
import com.itsm.knowledge.KnowledgeService;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.UserRepository;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The knowledge base. Everyone who is signed in can search and read it and
 * say whether an article helped; technicians and administrators write and
 * change articles (the addresses under /kb/manage, protected in
 * {@link com.itsm.security.SecurityConfig}).
 */
@Controller
@RequestMapping("/kb")
public class KnowledgeController {

    private static final int SUGGESTIONS = 3;

    private final KnowledgeService knowledge;
    private final ResolutionRecommender recommender;
    private final IncidentService incidentService;
    private final CategoryRepository categories;
    private final ITServiceRepository services;
    private final UserRepository users;

    public KnowledgeController(KnowledgeService knowledge, ResolutionRecommender recommender,
                               IncidentService incidentService, CategoryRepository categories,
                               ITServiceRepository services, UserRepository users) {
        this.knowledge = knowledge;
        this.recommender = recommender;
        this.incidentService = incidentService;
        this.categories = categories;
        this.services = services;
        this.users = users;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal ItsmUserDetails user, @RequestParam(required = false) String q,
                       Model model) {
        model.addAttribute("articles", knowledge.search(q));
        model.addAttribute("q", q);
        model.addAttribute("canEdit", user.getRole() != Role.EMPLOYEE);
        return "kb/list";
    }

    @GetMapping("/{id}")
    public String view(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable Long id, Model model) {
        KnowledgeArticle article = knowledge.view(id);
        model.addAttribute("article", article);
        model.addAttribute("related", knowledge.related(article, 3));
        model.addAttribute("canEdit", user.getRole() != Role.EMPLOYEE);
        model.addAttribute("incidentsPath", RolePaths.incidents(user.getRole()));
        return "kb/view";
    }

    @PostMapping("/{id}/vote")
    public String vote(@PathVariable Long id, @RequestParam boolean helpful, RedirectAttributes redirect) {
        knowledge.vote(id, helpful);
        redirect.addFlashAttribute("success", helpful
                ? "Thanks! Glad it helped."
                : "Thanks for telling us. If the problem is still there, please report an incident.");
        return "redirect:/kb/" + id;
    }

    /** Articles that match what someone is typing, for the report form and the assistant. */
    @GetMapping("/suggest")
    @ResponseBody
    public List<Map<String, Object>> suggest(@RequestParam(defaultValue = "") String q) {
        return recommender.articlesFor(q, SUGGESTIONS).stream()
                .map(match -> Map.<String, Object>of(
                        "id", match.item().getId(),
                        "reference", match.item().getReference(),
                        "title", match.item().getTitle(),
                        "percent", match.percent(),
                        "url", "/kb/" + match.item().getId()))
                .toList();
    }

    // ---- writing (technicians and administrators) ----

    @GetMapping("/manage/new")
    public String newArticle(@AuthenticationPrincipal ItsmUserDetails user,
                             @RequestParam(required = false) String fromIncident, Model model) {
        KnowledgeForm form = new KnowledgeForm();
        if (fromIncident != null) {
            Incident incident = incidentService.getFor(fromIncident, currentUser(user));
            if (incident.getResolution() == null) {
                throw new InvalidInputException(
                        "%s has no resolution yet, so there is nothing to write up.".formatted(incident.getCode()));
            }
            form.setTitle(incident.getTitle());
            form.setProblem(incident.getDescription());
            form.setSolution(incident.getResolution());
            form.setCategoryId(incident.getCategory() == null ? null : incident.getCategory().getId());
            form.setServiceId(incident.getService().getId());
            form.setSourceIncidentCode(incident.getCode());
        }
        model.addAttribute("form", form);
        return form(model, "/kb/manage", "Write a knowledge article");
    }

    @PostMapping("/manage")
    public String create(@AuthenticationPrincipal ItsmUserDetails user, @Valid @ModelAttribute("form") KnowledgeForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return form(model, "/kb/manage", "Write a knowledge article");
        }
        User author = currentUser(user);
        Incident source = form.getSourceIncidentCode() == null || form.getSourceIncidentCode().isBlank()
                ? null : incidentService.getFor(form.getSourceIncidentCode(), author);
        KnowledgeArticle article = knowledge.create(form.getTitle(), form.getProblem(), form.getSolution(),
                form.getCategoryId(), form.getServiceId(), author, source);
        redirect.addFlashAttribute("success", "%s has been published. It will now be suggested for similar problems."
                .formatted(article.getReference()));
        return "redirect:/kb/" + article.getId();
    }

    @GetMapping("/manage/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        KnowledgeArticle article = knowledge.get(id);
        KnowledgeForm form = new KnowledgeForm();
        form.setTitle(article.getTitle());
        form.setProblem(article.getProblem());
        form.setSolution(article.getSolution());
        form.setCategoryId(article.getCategory() == null ? null : article.getCategory().getId());
        form.setServiceId(article.getService() == null ? null : article.getService().getId());
        model.addAttribute("form", form);
        model.addAttribute("article", article);
        return form(model, "/kb/manage/" + id, "Change " + article.getReference());
    }

    @PostMapping("/manage/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") KnowledgeForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("article", knowledge.get(id));
            return form(model, "/kb/manage/" + id, "Change " + knowledge.get(id).getReference());
        }
        KnowledgeArticle article = knowledge.update(id, form.getTitle(), form.getProblem(), form.getSolution(),
                form.getCategoryId(), form.getServiceId());
        redirect.addFlashAttribute("success", "%s has been saved.".formatted(article.getReference()));
        return "redirect:/kb/" + id;
    }

    @PostMapping("/manage/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String reference = knowledge.get(id).getReference();
        knowledge.delete(id);
        redirect.addFlashAttribute("success", "%s has been deleted.".formatted(reference));
        return "redirect:/kb";
    }

    private String form(Model model, String action, String heading) {
        model.addAttribute("action", action);
        model.addAttribute("heading", heading);
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        model.addAttribute("services", services.findAllByOrderByCriticalityDescNameAsc());
        return "kb/form";
    }

    private User currentUser(ItsmUserDetails user) {
        return users.findById(user.getId()).orElseThrow();
    }
}
