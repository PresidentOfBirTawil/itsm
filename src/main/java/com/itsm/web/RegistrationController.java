package com.itsm.web;

import com.itsm.security.ItsmUserDetails;
import com.itsm.service.EmailAlreadyRegisteredException;
import com.itsm.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Employee self-registration. */
@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register")
    public String form(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        if (user != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result) {
        if (!result.hasFieldErrors("password") && !result.hasFieldErrors("confirmPassword")
                && !form.passwordsMatch()) {
            result.rejectValue("confirmPassword", "mismatch", "The two passwords do not match");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            registrationService.registerEmployee(form.getName(), form.getEmail(), form.getPassword());
        } catch (EmailAlreadyRegisteredException e) {
            result.rejectValue("email", "duplicate", "An account with this email address already exists");
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
