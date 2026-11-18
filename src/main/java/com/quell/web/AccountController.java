package com.quell.web;

import com.quell.api.ApiTokenService;
import com.quell.api.ApiTokenService.CreatedToken;
import com.quell.domain.ApiToken;
import com.quell.domain.User;
import com.quell.error.QuellException;
import com.quell.repository.UserRepository;
import com.quell.security.QuellUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The signed-in user's own settings: API keys for the REST API. */
@Controller
@RequestMapping("/account")
public class AccountController {

    private static final String KEYS = "/account/api-keys";

    private final ApiTokenService tokens;
    private final UserRepository users;

    public AccountController(ApiTokenService tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @GetMapping("/api-keys")
    public String keys(@AuthenticationPrincipal QuellUserDetails principal, Model model,
                       jakarta.servlet.http.HttpServletRequest request) {
        // Where this server is, for the example command: e.g. http://localhost:8080
        model.addAttribute("apiBase", org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromContextPath(request).toUriString());
        model.addAttribute("keys", tokens.of(user(principal)));
        model.addAttribute("now", java.time.LocalDateTime.now());
        model.addAttribute("maxKeys", ApiTokenService.MAX_PER_USER);
        return "account/api-keys";
    }

    /** Makes a key. Its full text is shown once, on the next page, and never again. */
    @PostMapping("/api-keys")
    public String create(@AuthenticationPrincipal QuellUserDetails principal, @RequestParam(defaultValue = "") String name,
                         @RequestParam(required = false) Integer days, RedirectAttributes redirect) {
        try {
            CreatedToken created = tokens.create(user(principal), name, days);
            redirect.addFlashAttribute("newKey", created.key());
            redirect.addFlashAttribute("newKeyName", created.token().getName());
        } catch (QuellException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + KEYS;
    }

    @PostMapping("/api-keys/{id}/revoke")
    public String revoke(@AuthenticationPrincipal QuellUserDetails principal, @PathVariable Long id,
                         RedirectAttributes redirect) {
        ApiToken token = tokens.revoke(id, user(principal));
        redirect.addFlashAttribute("success", "The key \"%s\" has been revoked. It stopped working at once.".formatted(token.getName()));
        return "redirect:" + KEYS;
    }

    private User user(QuellUserDetails principal) {
        return users.findById(principal.getId()).orElseThrow();
    }
}
