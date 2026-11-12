package com.itsm.domain.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.itsm.domain.Role;
import com.itsm.domain.Status;

/**
 * One allowed move out of a state: where it leads, which roles may make it
 * (and what the button says for each of them), and whether a note is required.
 *
 * <p>A transition with no roles is automatic: only the application itself makes it.</p>
 */
public record Transition(Status target, Map<Role, String> labels, boolean noteRequired) {

    public Transition {
        labels = Collections.unmodifiableMap(labels.isEmpty() ? new EnumMap<>(Role.class) : new EnumMap<>(labels));
    }

    /** Starts a transition to the given status. Add the roles that may make it with {@link #by}. */
    public static Transition to(Status target) {
        return new Transition(target, Map.of(), false);
    }

    /** Lets the given role make this move; {@code label} is the text on that role's button. */
    public Transition by(Role role, String label) {
        Map<Role, String> extended = new EnumMap<>(Role.class);
        extended.putAll(labels);
        extended.put(role, label);
        return new Transition(target, extended, noteRequired);
    }

    /** The person making this move must explain it in a note. */
    public Transition withNote() {
        return new Transition(target, labels, true);
    }

    public boolean allowedFor(Role role) {
        return labels.containsKey(role);
    }

    /** The button text for the given role, or null if that role may not make this move. */
    public String labelFor(Role role) {
        return labels.get(role);
    }

    /** True when no user may make this move: the application makes it by itself. */
    public boolean isAutomatic() {
        return labels.isEmpty();
    }
}
