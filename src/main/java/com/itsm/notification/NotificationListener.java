package com.itsm.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.itsm.domain.Incident;
import com.itsm.domain.Status;
import com.itsm.domain.User;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventListener;
import com.itsm.repository.AdministratorRepository;
import org.springframework.stereotype.Component;

/**
 * Listens to incident events and tells the right people (an observer, like
 * the audit trail listener). It decides who should hear what; the dispatcher
 * and the channels do the sending.
 *
 * <p>Who is told:</p>
 * <ul>
 *   <li>reported: the reporter (a confirmation)</li>
 *   <li>assigned or reassigned: the technician, and the reporter</li>
 *   <li>nobody could be assigned: the administrators</li>
 *   <li>information requested, resolved: the reporter</li>
 *   <li>the reporter answered, confirmed or reopened: the technician</li>
 *   <li>closed: the reporter and the technician</li>
 *   <li>any other status change: the reporter</li>
 *   <li>SLA at risk or breached: the technician and the administrators</li>
 *   <li>escalated: the administrators</li>
 *   <li>a note: the reporter and the technician</li>
 *   <li>a rating: the technician</li>
 * </ul>
 * Apart from that first confirmation, nobody is notified about something they did themselves.
 */
@Component
public class NotificationListener implements IncidentEventListener {

    private final AdministratorRepository administrators;
    private final NotificationDispatcher dispatcher;

    public NotificationListener(AdministratorRepository administrators, NotificationDispatcher dispatcher) {
        this.administrators = administrators;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        dispatcher.sendAfterCommit(messagesFor(event));
    }

    /** Works out the messages for one event. Public so that tests can check the routing on its own. */
    public List<NotificationMessage> messagesFor(IncidentEvent event) {
        Incident incident = event.incident();
        String code = incident.getCode();
        Outbox outbox = new Outbox(event);

        switch (event.action()) {
            // A confirmation, so it does go to the person who reported it.
            case REPORTED -> outbox.addEvenIfTheyDidIt(NotificationType.INCIDENT_CREATED, incident.getReporter(),
                    code + " received",
                    "We have received your incident \"%s\". You will be told when a technician is assigned."
                            .formatted(incident.getTitle()));

            case ASSIGNED, REASSIGNED -> {
                outbox.add(NotificationType.ASSIGNED, incident.getAssignee(),
                        code + " has been assigned to you",
                        "\"%s\", priority %s, reported by %s.".formatted(incident.getTitle(),
                                incident.getPriority(), incident.getReporter().getName()));
                outbox.add(NotificationType.ASSIGNED, incident.getReporter(),
                        code + ": a technician has been assigned",
                        "%s is now responsible for your incident.".formatted(incident.getAssignee().getName()));
            }

            case ASSIGNMENT_FAILED -> outbox.addAll(NotificationType.ASSIGNMENT_FAILED, administrators.findAll(),
                    code + " needs a technician", event.note());

            case STATUS_CHANGED -> statusChanged(event, outbox);

            case SLA_AT_RISK -> {
                outbox.add(NotificationType.SLA_AT_RISK, incident.getAssignee(), code + ": SLA at risk", event.note());
                outbox.addAll(NotificationType.SLA_AT_RISK, administrators.findAll(),
                        code + ": SLA at risk", event.note());
            }

            case SLA_BREACHED -> {
                outbox.add(NotificationType.SLA_BREACHED, incident.getAssignee(),
                        code + ": SLA breached", event.note());
                outbox.addAll(NotificationType.SLA_BREACHED, administrators.findAll(),
                        code + ": SLA breached", event.note());
            }

            case ESCALATED -> outbox.addAll(NotificationType.ESCALATED, administrators.findAll(),
                    code + " has been escalated to you", event.note());

            // A note goes to the other people on the incident: the reporter and the technician.
            case NOTE_ADDED -> {
                String title = "%s: %s added a note".formatted(code, event.actor() == null ? "someone"
                        : event.actor().getName());
                outbox.add(NotificationType.NOTE_ADDED, incident.getReporter(), title, event.note());
                outbox.add(NotificationType.NOTE_ADDED, incident.getAssignee(), title, event.note());
            }

            case RATED -> outbox.add(NotificationType.RATED, incident.getAssignee(),
                    code + ": the reporter rated your resolution", event.note());

            default -> {
                // Nothing to tell anyone, e.g. when the priority has just been calculated.
            }
        }
        return outbox.messages();
    }

    private void statusChanged(IncidentEvent event, Outbox outbox) {
        Incident incident = event.incident();
        String code = incident.getCode();
        String note = event.note() == null ? "" : event.note();
        Status from = event.oldStatus();
        Status to = event.newStatus();

        if (to == Status.AWAITING_INFO) {
            outbox.add(NotificationType.INFO_REQUESTED, incident.getReporter(),
                    code + ": the technician needs information from you", note);
        } else if (to == Status.RESOLVED) {
            outbox.add(NotificationType.RESOLVED, incident.getReporter(),
                    code + " has been resolved",
                    "Please confirm that it is fixed, or reopen it. What was done: " + note);
        } else if (to == Status.CLOSED) {
            outbox.add(NotificationType.CLOSED, incident.getReporter(), code + " has been closed", note);
            outbox.add(NotificationType.CLOSED, incident.getAssignee(), code + " has been closed", note);
        } else if (from == Status.AWAITING_INFO) {
            outbox.add(NotificationType.STATUS_CHANGED, incident.getAssignee(),
                    code + ": the reporter has answered", note);
        } else if (from == Status.RESOLVED && to == Status.IN_PROGRESS) {
            outbox.add(NotificationType.STATUS_CHANGED, incident.getAssignee(), code + " was reopened", note);
        } else if (to == Status.VERIFIED) {
            outbox.add(NotificationType.STATUS_CHANGED, incident.getAssignee(),
                    code + ": the reporter confirmed the fix", note);
        } else {
            outbox.add(NotificationType.STATUS_CHANGED, incident.getReporter(),
                    "%s is now %s".formatted(code, to.getLabel().toLowerCase()), note);
        }
    }

    /**
     * Collects the messages for one event. It leaves out the person who caused
     * the event, and gives each person at most one message per event.
     */
    private static final class Outbox {

        private final IncidentEvent event;
        private final Map<Long, NotificationMessage> byRecipient = new LinkedHashMap<>();

        private Outbox(IncidentEvent event) {
            this.event = event;
        }

        /** Adds a message, unless the recipient is the person who caused the event. */
        void add(NotificationType type, User recipient, String title, String body) {
            if (recipient != null && !recipient.equals(event.actor())) {
                addEvenIfTheyDidIt(type, recipient, title, body);
            }
        }

        void addEvenIfTheyDidIt(NotificationType type, User recipient, String title, String body) {
            if (recipient == null || byRecipient.containsKey(recipient.getId())) {
                return;
            }
            Incident incident = event.incident();
            byRecipient.put(recipient.getId(), new NotificationMessage(type, recipient.getId(), recipient.getName(),
                    recipient.getEmail(), incident.getId(), incident.getCode(), title,
                    body == null || body.isBlank() ? null : body));
        }

        void addAll(NotificationType type, List<? extends User> recipients, String title, String body) {
            recipients.forEach(recipient -> add(type, recipient, title, body));
        }

        List<NotificationMessage> messages() {
            return new ArrayList<>(byRecipient.values());
        }
    }
}
