package com.quell.webhook;

/** Where a webhook sends to, which decides the shape of the message. */
public enum WebhookKind {

    /** Any app: a signed JSON document (see {@link WebhookPayloads}). */
    GENERIC("Any app (signed JSON)", "bi-braces"),
    /** A Slack "incoming webhook": a formatted message in a channel. */
    SLACK("Slack", "bi-slack"),
    /** A Microsoft Teams "incoming webhook" (Workflows): an Adaptive Card in a channel. */
    TEAMS("Microsoft Teams", "bi-microsoft-teams");

    private final String label;
    private final String icon;

    WebhookKind(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
