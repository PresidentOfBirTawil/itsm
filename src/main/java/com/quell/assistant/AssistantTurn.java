package com.quell.assistant;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * What the assistant says when a conversation arrives at a step: its
 * messages, the buttons the person can press instead of typing, links to show
 * under the last message (articles, the new incident), and a hint for the text
 * box.
 *
 * @param messages    the assistant's messages, in order
 * @param replies     quick replies: buttons with a label and the value they send
 * @param links       links shown under the last message
 * @param placeholder the hint in the text box, or null for the usual one
 */
public record AssistantTurn(List<String> messages, List<Reply> replies, List<Link> links, String placeholder) {

    /** A button: what it says, and the value sent when it is pressed. */
    public record Reply(String label, String value) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** A link under a message: what it says, where it goes, and a Bootstrap icon. */
    public record Link(String label, String url, String icon) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    static AssistantTurn ask(List<String> messages, List<Reply> replies) {
        return new AssistantTurn(messages, replies, List.of(), null);
    }

    static AssistantTurn askText(List<String> messages, String placeholder) {
        return new AssistantTurn(messages, List.of(), List.of(), placeholder);
    }
}
