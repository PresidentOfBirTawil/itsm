package com.itsm.event;

/**
 * Something that wants to be told when an incident changes (the observer in
 * the Observer pattern). To react to incident changes in a new way, write a
 * class that implements this interface and mark it as a Spring component:
 * the publisher finds it by itself, and no existing code has to change.
 */
public interface IncidentEventListener {

    void onIncidentEvent(IncidentEvent event);
}
