package com.itsm.api;

import java.util.List;

import com.itsm.api.model.ArticleView;
import com.itsm.api.model.PersonView;
import com.itsm.api.model.ServiceView;
import com.itsm.api.model.StatusView;
import com.itsm.knowledge.KnowledgeService;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.UserRepository;
import com.itsm.security.ItsmUserDetails;
import com.itsm.status.StatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everything around the incidents: who you are, the services and their health, categories, the knowledge base. */
@RestController
@RequestMapping("/api/v1")
public class CatalogApiController {

    /** A category, by id and name. */
    public record CategoryView(Long id, String name) {
    }

    private final StatusService statusService;
    private final CategoryRepository categories;
    private final KnowledgeService knowledge;
    private final UserRepository users;

    public CatalogApiController(StatusService statusService, CategoryRepository categories, KnowledgeService knowledge,
                                UserRepository users) {
        this.statusService = statusService;
        this.categories = categories;
        this.knowledge = knowledge;
        this.users = users;
    }

    @GetMapping("/me")
    @Tag(name = "Account")
    @Operation(summary = "Who the API key belongs to")
    public PersonView me(@AuthenticationPrincipal ItsmUserDetails principal) {
        return PersonView.of(users.findById(principal.getId()).orElseThrow());
    }

    @GetMapping("/status")
    @Transactional(readOnly = true)
    @Tag(name = "Service status")
    @Operation(summary = "The overall service status", description = "As on the status page, with the probable root cause.")
    public StatusView status() {
        return StatusView.of(statusService.overview());
    }

    @GetMapping("/services")
    @Transactional(readOnly = true)
    @Tag(name = "Service status")
    @Operation(summary = "The IT services and their health", description = "Worst first.")
    public List<ServiceView> services() {
        return statusService.overview().services().stream().map(ServiceView::of).toList();
    }

    @GetMapping("/categories")
    @Tag(name = "Incidents")
    @Operation(summary = "The incident categories")
    public List<CategoryView> categories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryView(category.getId(), category.getName())).toList();
    }

    @GetMapping("/knowledge")
    @Transactional(readOnly = true)
    @Tag(name = "Knowledge base")
    @Operation(summary = "Search the knowledge base", description = "Without q, the most recently updated articles.")
    public List<ArticleView> knowledge(@RequestParam(required = false) String q) {
        return knowledge.search(q).stream().map(ArticleView::of).toList();
    }
}
