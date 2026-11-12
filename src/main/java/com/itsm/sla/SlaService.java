package com.itsm.sla;

import com.itsm.error.MissingSlaConfigurationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.Priority;
import com.itsm.domain.Setting;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.SlaSignal;
import com.itsm.domain.Status;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventPublisher;
import com.itsm.intelligence.AutoEscalation;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SettingRepository;
import com.itsm.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything to do with SLA deadlines: which policy applies to a priority,
 * and checking incidents against their deadlines.
 */
@Service
public class SlaService {

    /** The setting that switches the short demo deadlines on ("true") or off. */
    public static final String DEMO_MODE_SETTING = "sla.demo-mode";

    /**
     * Demo deadlines in minutes (response, resolution). Short enough to show an
     * "at risk" warning and a breach while recording a video.
     */
    private static final Map<Priority, int[]> DEMO_TARGETS = Map.of(
            Priority.P1, new int[] {1, 3},
            Priority.P2, new int[] {2, 5},
            Priority.P3, new int[] {3, 8},
            Priority.P4, new int[] {5, 10});

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    private final SlaPolicyRepository slaPolicies;
    private final SettingRepository settings;
    private final IncidentRepository incidents;
    private final IncidentEventPublisher events;
    private final AutoEscalation autoEscalation;

    public SlaService(SlaPolicyRepository slaPolicies, SettingRepository settings, IncidentRepository incidents,
                      IncidentEventPublisher events, AutoEscalation autoEscalation) {
        this.slaPolicies = slaPolicies;
        this.settings = settings;
        this.incidents = incidents;
        this.events = events;
        this.autoEscalation = autoEscalation;
    }

    // ---- which policy applies ----

    /**
     * The SLA policy for a priority. Normally the one an administrator
     * configured; in demo mode a short stand-in that is not stored.
     *
     * @throws MissingSlaConfigurationException if no policy is configured for the priority
     */
    @Transactional(readOnly = true)
    public SlaPolicy policyFor(Priority priority) {
        if (isDemoMode()) {
            return demoPolicy(priority);
        }
        return slaPolicies.findByPriority(priority).orElseThrow(() -> new MissingSlaConfigurationException(priority));
    }

    @Transactional(readOnly = true)
    public boolean isDemoMode() {
        return settings.findById(DEMO_MODE_SETTING).map(setting -> "true".equals(setting.getValue())).orElse(false);
    }

    /** Switches the demo deadlines on or off. Only incidents reported from now on are affected. */
    @Transactional
    public void setDemoMode(boolean on) {
        String value = String.valueOf(on);
        settings.findById(DEMO_MODE_SETTING).ifPresentOrElse(
                setting -> setting.setValue(value),
                () -> settings.save(new Setting(DEMO_MODE_SETTING, value)));
    }

    /** The demo deadlines, for display on the settings page. */
    public List<SlaPolicy> demoPolicies() {
        return List.of(Priority.values()).stream().map(SlaService::demoPolicy).toList();
    }

    private static SlaPolicy demoPolicy(Priority priority) {
        int[] minutes = DEMO_TARGETS.get(priority);
        return new SlaPolicy(priority, minutes[0], minutes[1]);
    }

    // ---- checking incidents against their deadlines ----

    /**
     * Checks every open incident. Run once a minute by {@link SlaMonitor},
     * and on demand from the settings page.
     *
     * @return how many incidents were checked
     */
    @Transactional
    public int checkOpenIncidents(LocalDateTime now) {
        List<Incident> open = incidents.findByStatusIn(Status.openStatuses());
        for (Incident incident : open) {
            refresh(incident, now);
            autoEscalation.escalateIfDue(incident, now); // a P1 nobody has acknowledged in time
        }
        return open.size();
    }

    /**
     * Brings one incident's SLA status up to date and publishes an event for
     * anything new: a deadline getting close, or a deadline missed. Called by
     * the minute job and straight after every change to an incident.
     */
    @Transactional
    public void refresh(Incident incident, LocalDateTime now) {
        for (SlaSignal signal : incident.refreshSla(now)) {
            String target = signal.target().name();
            String due = DEADLINE.format(signal.target().due());
            if (signal.type() == SlaSignal.Type.AT_RISK) {
                events.publish(new IncidentEvent(incident, null, IncidentAction.SLA_AT_RISK,
                        incident.getStatus(), incident.getStatus(),
                        "%d%% of the %s time has been used. The %s deadline is %s."
                                .formatted(Math.round(Incident.AT_RISK_FRACTION * 100), target, target, due)));
            } else {
                events.publish(new IncidentEvent(incident, null, IncidentAction.SLA_BREACHED,
                        incident.getStatus(), incident.getStatus(),
                        "The %s deadline (%s) was missed.".formatted(target, due)));
            }
        }
    }
}
