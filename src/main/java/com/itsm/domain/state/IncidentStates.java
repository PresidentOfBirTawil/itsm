package com.itsm.domain.state;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.itsm.domain.Status;

/**
 * Finds the state object that belongs to a status. The database stores the
 * status as a word (for example IN_PROGRESS); this turns that word back into
 * the object that knows the rules.
 */
public final class IncidentStates {

    private static final Map<Status, IncidentState> STATES = new EnumMap<>(Status.class);

    static {
        List<IncidentState> all = List.of(
                new NewState(), new ClassifiedState(), new AssignedState(), new AcknowledgedState(),
                new InProgressState(), new AwaitingInfoState(), new ResolvedState(), new VerifiedState(),
                new ClosedState());
        all.forEach(state -> STATES.put(state.status(), state));

        // Fail at startup, not halfway through a request, if someone adds a status and forgets its state class.
        for (Status status : Status.values()) {
            if (!STATES.containsKey(status)) {
                throw new IllegalStateException("No state class for status " + status);
            }
        }
    }

    private IncidentStates() {
    }

    public static IncidentState of(Status status) {
        return STATES.get(status);
    }

    public static Collection<IncidentState> all() {
        return Collections.unmodifiableCollection(STATES.values());
    }
}
