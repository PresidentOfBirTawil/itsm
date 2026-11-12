package com.itsm.event;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Tells every listener about an incident event (the subject in the Observer pattern).
 *
 * <p>The code that changes an incident only calls {@link #publish}. It does
 * not know who is listening, or how many listeners there are. Listeners run
 * straight away, inside the same database transaction as the change, so a
 * change and its audit entry are saved together or not at all.</p>
 */
@Component
public class IncidentEventPublisher {

    private final List<IncidentEventListener> listeners;

    /** Spring passes in every bean that implements IncidentEventListener. */
    public IncidentEventPublisher(List<IncidentEventListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void publish(IncidentEvent event) {
        listeners.forEach(listener -> listener.onIncidentEvent(event));
    }
}
