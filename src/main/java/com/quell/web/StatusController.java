package com.quell.web;

import com.quell.domain.Role;
import com.quell.security.QuellUserDetails;
import com.quell.status.StatusService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The status page: how every IT service is doing right now, what is known to
 * be wrong, the probable root cause when several services fail together, and
 * the map of which service depends on which. For everyone who is signed in.
 */
@Controller
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public String status(@AuthenticationPrincipal QuellUserDetails user, Model model) {
        model.addAttribute("overview", statusService.overview());
        model.addAttribute("employee", user.getRole() == Role.EMPLOYEE);
        model.addAttribute("viewerId", user.getId());
        model.addAttribute("incidentsPath", RolePaths.incidents(user.getRole()));
        return "status";
    }
}
