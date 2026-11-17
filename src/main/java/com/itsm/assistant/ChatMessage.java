package com.itsm.assistant;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.itsm.assistant.AssistantTurn.Link;

/**
 * One message in the transcript, from the assistant or from the person.
 * The assistant may mark words as **important**; {@link #parts()} splits the
 * text so the page can show them in bold without ever treating the text as
 * HTML.
 */
public record ChatMessage(boolean fromAssistant, String text, List<Link> links) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A piece of a message, in bold or not. */
    public record Part(String text, boolean bold) {
    }

    static ChatMessage assistant(String text, List<Link> links) {
        return new ChatMessage(true, text, List.copyOf(links));
    }

    static ChatMessage person(String text) {
        return new ChatMessage(false, text, List.of());
    }

    /** The text, split at the ** marks of the assistant's messages. A person's words are never bold. */
    public List<Part> parts() {
        if (!fromAssistant) {
            return List.of(new Part(text, false));
        }
        String[] pieces = text.split("\\*\\*", -1);
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < pieces.length; i++) {
            if (!pieces[i].isEmpty()) {
                parts.add(new Part(pieces[i], i % 2 == 1));
            }
        }
        return parts;
    }
}
