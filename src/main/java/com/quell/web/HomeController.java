package com.quell.web;

import com.quell.config.Brand;
import com.quell.security.QuellUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The entry points everyone shares: the product site, the sign-in page and the dashboard redirect. */
@Controller
public class HomeController {

    private final Brand brand;

    public HomeController(Brand brand) {
        this.brand = brand;
    }

    /** The product site for visitors; signed-in users go straight to their dashboard. */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal QuellUserDetails user, Model model) {
        if (user != null) {
            return "redirect:/dashboard";
        }
        boolean downloads = !brand.getDownloadUrl().isBlank();
        model.addAttribute("downloadsPage", downloads ? brand.getDownloadUrl() : "/login");
        model.addAttribute("windowsDownload", downloads ? brand.download("Quell-Setup-Windows.exe") : "/login");
        model.addAttribute("macDownload", downloads ? brand.download("Quell-Mac.dmg") : "/login");
        model.addAttribute("linuxDownload", downloads ? brand.download("Quell-Linux.deb") : "/login");
        return "site/landing";
    }

    /** Sends each signed-in user to the dashboard that belongs to their role. */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal QuellUserDetails user) {
        return "redirect:" + switch (user.getRole()) {
            case EMPLOYEE -> "/employee/dashboard";
            case TECHNICIAN -> "/technician/dashboard";
            case ADMIN -> "/admin/dashboard";
        };
    }

    /** Shows the sign-in form. Spring Security itself handles the POST to /login. */
    @GetMapping("/login")
    public String login(@AuthenticationPrincipal QuellUserDetails user) {
        return user != null ? "redirect:/dashboard" : "auth/login";
    }
}
