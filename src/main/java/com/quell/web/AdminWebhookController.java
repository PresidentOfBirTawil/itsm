package com.quell.web;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.quell.domain.Webhook;
import com.quell.domain.WebhookDelivery;
import com.quell.error.InvalidInputException;
import com.quell.webhook.WebhookEvent;
import com.quell.webhook.WebhookKind;
import com.quell.webhook.WebhookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrators connect Quell to Slack, Microsoft Teams and other apps. */
@Controller
@RequestMapping("/admin/webhooks")
public class AdminWebhookController {

    private static final String LIST = "/admin/webhooks";

    private final WebhookService webhookService;

    public AdminWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("webhooks", webhookService.all());
        model.addAttribute("deliveries", webhookService.log(null));
        return "admin/webhooks";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        return form(model, null, "", WebhookKind.SLACK, "", EnumSet.of(WebhookEvent.REPORTED, WebhookEvent.RESOLVED,
                WebhookEvent.SLA_BREACHED, WebhookEvent.ESCALATED), false, true);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Webhook webhook = webhookService.get(id);
        model.addAttribute("webhook", webhook);
        model.addAttribute("deliveries", webhookService.log(id));
        return form(model, id, webhook.getName(), webhook.getKind(), webhook.getUrl(), webhook.getEvents(),
                webhook.isMajorOnly(), webhook.isEnabled());
    }

    @PostMapping
    public String create(@RequestParam(defaultValue = "") String name, @RequestParam(required = false) WebhookKind kind,
                         @RequestParam(defaultValue = "") String url,
                         @RequestParam(name = "events", required = false) List<WebhookEvent> events,
                         @RequestParam(defaultValue = "false") boolean majorOnly, Model model, RedirectAttributes redirect) {
        Set<WebhookEvent> chosen = events == null ? Set.of() : Set.copyOf(events);
        try {
            Webhook webhook = webhookService.create(name, kind, url, chosen, majorOnly);
            redirect.addFlashAttribute("success", "The webhook \"%s\" is on. Send a test message to check it.".formatted(webhook.getName()));
            return "redirect:" + LIST + "/" + webhook.getId() + "/edit";
        } catch (InvalidInputException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, null, name, kind, url, chosen, majorOnly, true);
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam(defaultValue = "") String name,
                         @RequestParam(required = false) WebhookKind kind, @RequestParam(defaultValue = "") String url,
                         @RequestParam(name = "events", required = false) List<WebhookEvent> events,
                         @RequestParam(defaultValue = "false") boolean majorOnly,
                         @RequestParam(defaultValue = "false") boolean enabled, Model model, RedirectAttributes redirect) {
        Set<WebhookEvent> chosen = events == null ? Set.of() : Set.copyOf(events);
        try {
            Webhook webhook = webhookService.update(id, name, kind, url, chosen, majorOnly, enabled);
            redirect.addFlashAttribute("success", "The webhook \"%s\" has been saved.".formatted(webhook.getName()));
            return "redirect:" + LIST;
        } catch (InvalidInputException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("webhook", webhookService.get(id));
            model.addAttribute("deliveries", webhookService.log(id));
            return form(model, id, name, kind, url, chosen, majorOnly, enabled);
        }
    }

    @PostMapping("/{id}/test")
    public String test(@PathVariable Long id, RedirectAttributes redirect) {
        WebhookDelivery delivery = webhookService.test(id);
        if (delivery.getState() == WebhookDelivery.State.DELIVERED) {
            redirect.addFlashAttribute("success", "The test message was delivered (the receiver answered %d)."
                    .formatted(delivery.getResponseStatus()));
        } else {
            redirect.addFlashAttribute("error", "The test message was not delivered. " + delivery.getError());
        }
        return "redirect:" + LIST + "/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        String name = webhookService.get(id).getName();
        webhookService.delete(id);
        redirect.addFlashAttribute("success", "The webhook \"%s\" has been removed.".formatted(name));
        return "redirect:" + LIST;
    }

    @PostMapping("/deliveries/{id}/retry")
    public String retry(@PathVariable Long id, RedirectAttributes redirect) {
        webhookService.retry(id);
        redirect.addFlashAttribute("success", "The message is being sent again.");
        return "redirect:" + LIST;
    }

    private static String form(Model model, Long id, String name, WebhookKind kind, String url, Set<WebhookEvent> events,
                               boolean majorOnly, boolean enabled) {
        model.addAttribute("editingId", id);
        model.addAttribute("name", name);
        model.addAttribute("kind", kind == null ? WebhookKind.SLACK : kind);
        model.addAttribute("url", url);
        model.addAttribute("events", events);
        model.addAttribute("majorOnly", majorOnly);
        model.addAttribute("enabled", enabled);
        model.addAttribute("kinds", WebhookKind.values());
        model.addAttribute("choosableEvents", WebhookEvent.choosable());
        return "admin/webhook-form";
    }
}
