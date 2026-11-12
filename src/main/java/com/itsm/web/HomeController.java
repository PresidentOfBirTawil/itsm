package com.itsm.web;

import com.itsm.security.ItsmUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The entry points everyone shares: the home URL, the sign-in page and the dashboard redirect. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /** Sends each signed-in user to the dashboard that belongs to their role. */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal ItsmUserDetails user) {
        return "redirect:" + switch (user.getRole()) {
            case EMPLOYEE -> "/employee/dashboard";
            case TECHNICIAN -> "/technician/dashboard";
            case ADMIN -> "/admin/dashboard";
        };
    }

    /** Shows the sign-in form. Spring Security itself handles the POST to /login. */
    @GetMapping("/login")
    public String login(@AuthenticationPrincipal ItsmUserDetails user) {
        return user != null ? "redirect:/dashboard" : "auth/login";
    }
}
