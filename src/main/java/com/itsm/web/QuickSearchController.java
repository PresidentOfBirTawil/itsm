package com.itsm.web;

import java.util.ArrayList;
import java.util.List;

import com.itsm.domain.Incident;
import com.itsm.domain.Role;
import com.itsm.knowledge.KnowledgeService;
import com.itsm.repository.IncidentRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search behind the command palette (Ctrl/⌘ K): incidents by code or title,
 * and knowledge articles by meaning. Employees find only their own incidents;
 * technicians and administrators find all of them, as on their search pages.
 */
@RestController
public class QuickSearchController {

    private static final int INCIDENTS = 6;
    private static final int ARTICLES = 4;

    /** One line in the palette. */
    public record Result(String group, String icon, String title, String meta, String url) {
    }

    private final IncidentRepository incidents;
    private final KnowledgeService knowledge;

    public QuickSearchController(IncidentRepository incidents, KnowledgeService knowledge) {
        this.incidents = incidents;
        this.knowledge = knowledge;
    }

    @GetMapping("/search/quick")
    public List<Result> search(@AuthenticationPrincipal ItsmUserDetails user, @RequestParam(defaultValue = "") String q) {
        String text = q.trim();
        if (text.length() < 2) {
            return List.of();
        }
        List<Result> results = new ArrayList<>();
        Long onlyReporter = user.getRole() == Role.EMPLOYEE ? user.getId() : null;
        String pattern = "%" + text.toUpperCase().replace("%", "").replace("_", "") + "%";
        String base = RolePaths.incidents(user.getRole());
        for (Incident incident : incidents.quickSearch(onlyReporter, pattern, Limit.of(INCIDENTS))) {
            results.add(new Result("Incidents", "bi-card-list", incident.getCode() + "  " + incident.getTitle(),
                    incident.getStatus().getLabel()
                            + (incident.getPriority() == null ? "" : " · " + incident.getPriority()),
                    base + "/" + incident.getCode()));
        }
        knowledge.search(text).stream().limit(ARTICLES).forEach(article -> results.add(new Result(
                "Knowledge base", "bi-journal-bookmark", article.getTitle(), article.getReference(),
                "/kb/" + article.getId())));
        return results;
    }
}
