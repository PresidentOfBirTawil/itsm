package com.itsm.webhook;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.itsm.config.Brand;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * What is sent for an event, in the shape each kind of receiver expects:
 * a JSON document for apps, a formatted message for Slack, an Adaptive Card
 * for Microsoft Teams. The message is built when the event happens and
 * stored with the delivery, so a retry sends exactly the same thing.
 */
@Component
public class WebhookPayloads {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Brand brand;

    public WebhookPayloads(Brand brand) {
        this.brand = brand;
    }

    /** Everything a message is built from: plain values, taken from the incident when the event happened. */
    public record Facts(String code, String title, String status, Priority priority, String service, String category,
                        String reporter, String assignee, String actor, String note, LocalDateTime occurredAt) {

        public static Facts of(Incident incident, String actor, String note, LocalDateTime occurredAt) {
            return new Facts(incident.getCode(), incident.getTitle(), incident.getStatus().getLabel(),
                    incident.getPriority(), incident.getService().getName(),
                    incident.getCategory() == null ? null : incident.getCategory().getName(),
                    incident.getReporter().getName(), incident.getAssignee() == null ? null : incident.getAssignee().getName(),
                    actor, note, occurredAt);
        }
    }

    public String build(WebhookKind kind, WebhookEvent event, Facts facts) {
        Object body = switch (kind) {
            case GENERIC -> generic(event, facts);
            case SLACK -> slack(event, facts);
            case TEAMS -> teams(event, facts);
        };
        return JSON.writeValueAsString(body);
    }

    /** A sample incident for the "Send test" button. */
    public Facts testFacts() {
        return new Facts("INC-TEST", "This is a test message from " + brand.getName(), "New", Priority.P3,
                "Corporate Network", "Network", "Test", null, null,
                "If you can read this, the webhook works.", LocalDateTime.now());
    }

    private Map<String, Object> generic(WebhookEvent event, Facts facts) {
        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("code", facts.code());
        incident.put("title", facts.title());
        incident.put("status", facts.status());
        incident.put("priority", facts.priority() == null ? null : facts.priority().name());
        incident.put("service", facts.service());
        incident.put("category", facts.category());
        incident.put("reporter", facts.reporter());
        incident.put("assignee", facts.assignee());
        incident.put("url", link(facts));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event.getName());
        body.put("occurredAt", facts.occurredAt().toString());
        body.put("actor", facts.actor());
        body.put("note", facts.note());
        body.put("incident", incident);
        body.put("source", brand.getName());
        return body;
    }

    private Map<String, Object> slack(WebhookEvent event, Facts facts) {
        String headline = "%s %s *<%s|%s>* %s".formatted(dot(facts.priority()), facts.priority() == null ? "" : facts.priority().name(),
                link(facts), facts.code(), escapeSlack(facts.title()));
        String details = "%s · %s · %s%s".formatted(event.getLabel(), escapeSlack(facts.service()), facts.status(),
                facts.assignee() == null ? "" : " · " + escapeSlack(facts.assignee()));
        List<Object> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", headline)));
        List<Object> context = new ArrayList<>();
        context.add(Map.of("type", "mrkdwn", "text", details));
        blocks.add(Map.of("type", "context", "elements", context));
        if (facts.note() != null && !facts.note().isBlank()) {
            blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "> " + escapeSlack(shorten(facts.note())))));
        }
        return Map.of("text", "%s %s: %s (%s)".formatted(event.getLabel(), facts.code(), facts.title(), facts.service()),
                "blocks", blocks);
    }

    private Map<String, Object> teams(WebhookEvent event, Facts facts) {
        List<Object> factSet = new ArrayList<>();
        factSet.add(Map.of("title", "Priority", "value", facts.priority() == null ? "—" : facts.priority().name()));
        factSet.add(Map.of("title", "Service", "value", facts.service()));
        factSet.add(Map.of("title", "Status", "value", facts.status()));
        factSet.add(Map.of("title", "Assignee", "value", facts.assignee() == null ? "Not assigned yet" : facts.assignee()));
        List<Object> body = new ArrayList<>();
        body.add(Map.of("type", "TextBlock", "size", "Small", "weight", "Bolder", "color", "Accent",
                "text", event.getLabel().toUpperCase()));
        body.add(Map.of("type", "TextBlock", "size", "Medium", "weight", "Bolder", "wrap", true,
                "text", facts.code() + " · " + facts.title()));
        body.add(Map.of("type", "FactSet", "facts", factSet));
        if (facts.note() != null && !facts.note().isBlank()) {
            body.add(Map.of("type", "TextBlock", "wrap", true, "isSubtle", true, "text", shorten(facts.note())));
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);
        card.put("actions", List.of(Map.of("type", "Action.OpenUrl", "title", "Open in " + brand.getName(), "url", link(facts))));
        return Map.of("type", "message", "attachments",
                List.of(Map.of("contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
    }

    private String link(Facts facts) {
        return brand.getPublicUrl() + "/incidents/" + facts.code();
    }

    private static String dot(Priority priority) {
        if (priority == null) {
            return "⚪";
        }
        return switch (priority) {
            case P1 -> "🔴";
            case P2 -> "🟠";
            case P3 -> "🟡";
            case P4 -> "🟢";
        };
    }

    /** Slack treats &, < and > specially. */
    private static String escapeSlack(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shorten(String text) {
        return text.length() <= 300 ? text : text.substring(0, 297) + "…";
    }
}
