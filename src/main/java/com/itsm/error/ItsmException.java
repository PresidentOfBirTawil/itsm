package com.itsm.error;

/**
 * The parent of every error this application raises on purpose: a request
 * that breaks one of its rules. There is one subclass for each kind of rule
 * that can be broken, so code that catches errors can tell them apart.
 *
 * <p>Every exception of this family carries a message written for the person
 * using the application, so a page can show it as it is.</p>
 *
 * <pre>
 * ItsmException
 * ├── InvalidStateTransitionException     an incident was asked to make a move its lifecycle forbids
 * ├── TechnicianUnavailableException      nobody, or the chosen technician, can take an incident
 * ├── WorkloadExceededException           the chosen technician is already at their maximum
 * ├── MissingSlaConfigurationException    a priority has no SLA policy
 * ├── DuplicateIncidentException          the same person reported the same thing twice
 * ├── UnauthorizedOperationException      the user may not do this (shown as the 403 page)
 * ├── InvalidInputException               what was typed or sent makes no sense
 * └── NotFoundException                   the thing asked for does not exist (shown as the 404 page)
 *     ├── IncidentNotFoundException
 *     └── ServiceNotFoundException
 * </pre>
 */
public abstract class ItsmException extends RuntimeException {

    protected ItsmException(String message) {
        super(message);
    }
}
