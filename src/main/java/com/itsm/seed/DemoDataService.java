package com.itsm.seed;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.itsm.domain.AuditEntry;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.IncidentAction;
import com.itsm.domain.PriorityAssessment;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.SlaSignal;
import com.itsm.domain.SlaTarget;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import com.itsm.domain.User;
import com.itsm.error.InvalidInputException;
import com.itsm.intelligence.DuplicateDetector;
import com.itsm.intelligence.EscalationPolicy;
import com.itsm.priority.PriorityCalculator;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmailMessageRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.NotificationRepository;
import com.itsm.repository.SettingRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets the demo data: removes every incident with its history,
 * notifications and emails, puts the settings back to their defaults and, if
 * wanted, adds a set of sample incidents spread over the last ten days, so
 * that the dashboards, reports and search have something realistic to show.
 *
 * <p>Users, services, categories and SLA policies are kept as they are.</p>
 *
 * <p>The sample incidents are not written straight into the tables. Each one
 * is taken through its life with the same model methods the application uses
 * (prioritise, assign, transition, SLA check), only with times in the past.
 * That way a sample incident obeys every rule a real one does.</p>
 */
@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final IncidentRepository incidents;
    private final AuditEntryRepository auditEntries;
    private final NotificationRepository notifications;
    private final EmailMessageRepository emails;
    private final SettingRepository settings;
    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final AdministratorRepository administrators;
    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final PriorityCalculator priorityCalculator;
    private final DuplicateDetector duplicateDetector;
    private final EscalationPolicy escalationPolicy;
    private final EntityManager entityManager;

    public DemoDataService(IncidentRepository incidents, AuditEntryRepository auditEntries,
                           NotificationRepository notifications, EmailMessageRepository emails,
                           SettingRepository settings, UserRepository users, TechnicianRepository technicians,
                           AdministratorRepository administrators, ITServiceRepository services,
                           CategoryRepository categories, SlaPolicyRepository slaPolicies,
                           PriorityCalculator priorityCalculator, DuplicateDetector duplicateDetector,
                           EscalationPolicy escalationPolicy, EntityManager entityManager) {
        this.incidents = incidents;
        this.auditEntries = auditEntries;
        this.notifications = notifications;
        this.emails = emails;
        this.settings = settings;
        this.users = users;
        this.technicians = technicians;
        this.administrators = administrators;
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.priorityCalculator = priorityCalculator;
        this.duplicateDetector = duplicateDetector;
        this.escalationPolicy = escalationPolicy;
        this.entityManager = entityManager;
    }

    /**
     * Removes all incident data and resets the settings.
     *
     * @param withSampleIncidents true to add the sample incidents afterwards
     * @return how many sample incidents were added
     */
    @Transactional
    public int reset(boolean withSampleIncidents) {
        notifications.deleteAllInBatch();
        emails.deleteAllInBatch();
        auditEntries.deleteAllInBatch();
        incidents.clearDuplicateLinks();
        incidents.deleteAllInBatch();
        settings.deleteAllInBatch(); // back to the skill-based strategy, demo SLA off
        entityManager.flush();
        entityManager.clear();
        try {
            incidents.restartNumbering(); // so that the next incident is INC-0001 again
        } catch (RuntimeException e) {
            log.warn("Incident numbering could not be restarted; codes will carry on: {}", e.getMessage());
        }
        technicians.findAll().forEach(technician -> technician.setAvailable(true));

        int added = withSampleIncidents ? addSampleIncidents(LocalDateTime.now().withNano(0)) : 0;
        log.info("Demo data reset: {} sample incidents added", added);
        return added;
    }

    // ---- the sample incidents ----

    /** How far a sample incident got. */
    private enum Story { CLOSED, VERIFIED, RESOLVED, IN_PROGRESS, AWAITING_INFO, ACKNOWLEDGED, ASSIGNED, UNASSIGNED }

    /**
     * One sample incident.
     *
     * @param minutesAgo   how long ago it was reported
     * @param ackAfter     minutes from report to acknowledgement
     * @param resolveAfter minutes from report to resolution
     */
    private record Sample(int reporter, String title, String description, String service, String category,
                          Impact impact, Urgency urgency, long minutesAgo, Story story, int technician,
                          long ackAfter, long resolveAfter, String resolution, Integer rating, String comment) {
    }

    private static final int H = 60;

    private static final List<Sample> SAMPLES = List.of(
            // ---- finished, over the last ten days ----
            new Sample(1, "Payment page times out at checkout", "Customers see a spinner and then an error when they pay.",
                    "Payment Application", "Application", Impact.HIGH, Urgency.HIGH, 236 * H, Story.CLOSED, 4, 2, 21,
                    "Raised the connection limit of the payment gateway and restarted the payment service.", 5, "Fixed within minutes."),
            new Sample(2, "Wifi drops in meeting room 3", "Video calls freeze every few minutes in room 3.",
                    "Corporate Network", "Network", Impact.MEDIUM, Urgency.MEDIUM, 220 * H, Story.CLOSED, 1, 25, 190,
                    "Replaced the faulty access point above room 3.", 4, null),
            new Sample(3, "Account locked after password change", "I changed my password and now I cannot get in at all.",
                    "Authentication Service", "Authentication", Impact.LOW, Urgency.HIGH, 200 * H, Story.CLOSED, 3, 6, 45,
                    "Unlocked the account and cleared the cached old password on the phone.", 5, "Very quick."),
            new Sample(4, "Monthly sales report query is very slow", "The report used to take a minute and now takes forty.",
                    "Database Cluster", "Database", Impact.MEDIUM, Urgency.MEDIUM, 190 * H, Story.CLOSED, 2, 9, 170,
                    "Rebuilt a missing index on the orders table.", 2, "It took most of the day."),
            new Sample(1, "Shared finance folder is read-only", "Nobody in finance can save into the shared folder.",
                    "Cloud Storage", "Storage", Impact.MEDIUM, Urgency.MEDIUM, 170 * H, Story.CLOSED, 2, 20, 150,
                    "Restored the write permission that was lost in last night's migration.", 4, null),
            new Sample(3, "Laptop will not charge", "The charging light stays off with two different chargers.",
                    "Email & Collaboration", "Hardware", Impact.LOW, Urgency.MEDIUM, 160 * H, Story.CLOSED, 1, 50, 300,
                    "Replaced the laptop battery.", 5, null),
            new Sample(4, "Suspicious email asking for my password", "It looks like it comes from IT but the address is odd.",
                    "Email & Collaboration", "Security", Impact.MEDIUM, Urgency.HIGH, 140 * H, Story.CLOSED, 3, 22, 240,
                    "Confirmed phishing. Blocked the sender and removed the email from all mailboxes.", 4, "Thanks for the warning to everyone."),
            new Sample(2, "Refund button does nothing", "Support staff cannot issue refunds to customers.",
                    "Payment Application", "Application", Impact.MEDIUM, Urgency.HIGH, 120 * H, Story.CLOSED, 4, 8, 27,
                    "Rolled back yesterday's release of the refund module.", 3, "Slow start, good fix."),
            new Sample(1, "Calendar invitations arrive hours late", "Invitations I send show up for colleagues much later.",
                    "Email & Collaboration", "Software", Impact.MEDIUM, Urgency.LOW, 100 * H, Story.CLOSED, 4, 70, 420,
                    "Cleared the stuck mail queue on the collaboration server.", null, null),
            new Sample(3, "VPN client fails after update", "After this morning's update the VPN client closes at once.",
                    "Corporate Network", "Network", Impact.HIGH, Urgency.MEDIUM, 90 * H, Story.CLOSED, 1, 8, 95,
                    "Published the previous client version and blocked the faulty update.", 4, null),
            new Sample(4, "Database replication lag alarm", "The standby database is forty minutes behind the primary.",
                    "Database Cluster", "Database", Impact.HIGH, Urgency.MEDIUM, 70 * H, Story.VERIFIED, 2, 3, 24,
                    "Restarted replication after freeing disk space on the standby.", 5, "Good communication throughout."),
            new Sample(2, "Cannot sign in to the HR portal with SSO", "Single sign-on sends me round in a loop.",
                    "Authentication Service", "Authentication", Impact.MEDIUM, Urgency.MEDIUM, 50 * H, Story.RESOLVED, 3, 7, 70,
                    "Renewed the expired SSO certificate of the HR portal.", null, null),

            // ---- still open ----
            new Sample(1, "Cloud backup job failed last night", "The nightly backup report shows a failure for the file server.",
                    "Cloud Storage", "Cloud", Impact.MEDIUM, Urgency.MEDIUM, 5 * H, Story.IN_PROGRESS, 2, 30, -1, null, null, null),
            new Sample(2, "Card payments are declined for some customers", "About one in five card payments is refused.",
                    "Payment Application", "Application", Impact.HIGH, Urgency.HIGH, 24, Story.IN_PROGRESS, 4, 3, -1, null, null, null),
            new Sample(3, "Switch in server room B shows an amber light", "One of the switches in rack 4 has an amber status light.",
                    "Corporate Network", "Hardware", Impact.MEDIUM, Urgency.HIGH, 60, Story.AWAITING_INFO, 1, 7, -1, null, null, null),
            new Sample(4, "New starter needs a laptop set up", "A new colleague starts on Monday and needs a laptop.",
                    "Email & Collaboration", "Hardware", Impact.LOW, Urgency.LOW, 2 * H, Story.ASSIGNED, 1, -1, -1, null, null, null),
            new Sample(1, "Phishing wave: several users clicked a link", "At least six people entered their password on a fake page.",
                    "Authentication Service", "Security", Impact.HIGH, Urgency.HIGH, 9, Story.ASSIGNED, 3, -1, -1, null, null, null),
            new Sample(2, "VPN connection drops every few minutes", "Working from home, the VPN disconnects all the time.",
                    "Corporate Network", "Network", Impact.MEDIUM, Urgency.MEDIUM, 3 * H, Story.ACKNOWLEDGED, 1, 40, -1, null, null, null),
            new Sample(3, "VPN connection drops in the afternoon", "Since lunch the VPN keeps cutting out.",
                    "Corporate Network", "Network", Impact.MEDIUM, Urgency.MEDIUM, 150, Story.ASSIGNED, 3, -1, -1, null, null, null),
            new Sample(4, "Mailbox is full and rejects mail", "Senders get a bounce saying my mailbox is over quota.",
                    "Email & Collaboration", "Software", Impact.LOW, Urgency.MEDIUM, 20 * H, Story.IN_PROGRESS, 4, 90, -1, null, null, null),
            new Sample(1, "Storage quota exceeded for the design team", "The design share refuses new files.",
                    "Cloud Storage", "Storage", Impact.HIGH, Urgency.LOW, 30, Story.ASSIGNED, 2, -1, -1, null, null, null),
            new Sample(2, "Two-factor code never arrives", "The text message with the sign-in code does not come.",
                    "Authentication Service", "Authentication", Impact.LOW, Urgency.MEDIUM, 36, Story.ACKNOWLEDGED, 3, 12, -1, null, null, null),
            new Sample(3, "Antivirus reports malware on a shared PC", "The PC at reception shows a red antivirus warning.",
                    "Email & Collaboration", "Security", Impact.MEDIUM, Urgency.MEDIUM, 12, Story.UNASSIGNED, 0, -1, -1, null, null, null));

    private int addSampleIncidents(LocalDateTime now) {
        User administrator = administrators.findAll().stream().findFirst()
                .orElseThrow(() -> new InvalidInputException("The sample incidents need an administrator account."));
        List<Incident> added = new ArrayList<>();
        // Oldest first, so that the codes go up with time.
        SAMPLES.stream()
                .sorted((a, b) -> Long.compare(b.minutesAgo(), a.minutesAgo()))
                .forEach(sample -> added.add(play(sample, now, administrator, added)));
        return added.size();
    }

    /** Takes one sample incident through its life, writing the audit entries as it goes. */
    private Incident play(Sample sample, LocalDateTime now, User administrator, List<Incident> earlier) {
        Employee reporter = (Employee) demoUser("emp" + sample.reporter());
        Technician technician = sample.technician() == 0 ? null : (Technician) demoUser("tech" + sample.technician());
        ITService service = services.findByName(sample.service())
                .orElseThrow(() -> new InvalidInputException("The sample incidents need the service " + sample.service()));
        Category category = categories.findByNameIgnoreCase(sample.category())
                .orElseThrow(() -> new InvalidInputException("The sample incidents need the category " + sample.category()));
        LocalDateTime reportedAt = now.minusMinutes(sample.minutesAgo());

        Incident incident = new Incident(sample.title(), sample.description(), reporter, service,
                sample.impact(), sample.urgency(), reportedAt);
        incident.setCategory(category);
        incidents.save(incident);
        record(incident, reportedAt, reporter, IncidentAction.REPORTED, null, Status.NEW,
                "Reported against %s, category %s.".formatted(service.getName(), category.getName()));

        duplicateDetector.findDuplicate(incident, earlier).ifPresent(match -> {
            incident.linkPossibleDuplicate(match.original(), match.similarity());
            record(incident, reportedAt, null, IncidentAction.DUPLICATE_SUSPECTED, Status.NEW, Status.NEW,
                    match.explain() + ".");
        });

        PriorityAssessment assessment = priorityCalculator.calculate(incident);
        SlaPolicy policy = slaPolicies.findByPriority(assessment.priority()).orElseThrow(
                () -> new InvalidInputException("The sample incidents need an SLA policy for " + assessment.priority()));
        incident.prioritise(assessment, policy);
        record(incident, reportedAt, null, IncidentAction.PRIORITISED, Status.NEW, Status.CLASSIFIED,
                "Priority %s (%s). Score: impact × urgency × service criticality = %s. Respond by %s, resolve by %s."
                        .formatted(assessment.priority(), assessment.priority().getLabel(), assessment.formula(),
                                DEADLINE.format(incident.getResponseDue()), DEADLINE.format(incident.getResolutionDue())));

        LocalDateTime clock = reportedAt;
        if (technician == null) {
            record(incident, reportedAt, null, IncidentAction.ASSIGNMENT_FAILED, Status.CLASSIFIED, Status.CLASSIFIED,
                    "No technician with the skill %s is available and below their maximum workload (Skill-based "
                            .formatted(category.getName()) + "strategy). An administrator needs to assign this incident.");
        } else {
            incident.assignTo(technician);
            record(incident, reportedAt, null, IncidentAction.ASSIGNED, Status.CLASSIFIED, Status.ASSIGNED,
                    "Assigned to %s automatically (Skill-based strategy).".formatted(technician.getName()));

            if (sample.ackAfter() >= 0) {
                clock = move(incident, clock, reportedAt.plusMinutes(sample.ackAfter()), Status.ACKNOWLEDGED, technician, null);
            }
            if (reached(sample.story(), Story.IN_PROGRESS)) {
                clock = move(incident, clock, clock.plusMinutes(3), Status.IN_PROGRESS, technician, null);
            }
            if (sample.story() == Story.AWAITING_INFO) {
                clock = move(incident, clock, clock.plusMinutes(10), Status.AWAITING_INFO, technician,
                        "Which rack is it in, and is anything unreachable because of it?");
            }
            if (sample.resolveAfter() >= 0) {
                clock = move(incident, clock, reportedAt.plusMinutes(sample.resolveAfter()), Status.RESOLVED,
                        technician, sample.resolution());
            }
            if (reached(sample.story(), Story.VERIFIED)) {
                clock = move(incident, clock, clock.plusMinutes(25), Status.VERIFIED, reporter, null);
                if (sample.rating() != null) {
                    clock = clock.plusMinutes(2);
                    incident.rate(sample.rating(), sample.comment(), clock);
                    record(incident, clock, reporter, IncidentAction.RATED, incident.getStatus(), incident.getStatus(),
                            "Rated %d out of %d.%s".formatted(sample.rating(), Incident.MAX_RATING,
                                    sample.comment() == null ? "" : " " + sample.comment()));
                }
            }
            if (sample.story() == Story.CLOSED) {
                clock = move(incident, clock, clock.plusMinutes(60), Status.CLOSED, administrator, null);
            }
        }
        // An incident that is still open has been ticking until now.
        if (incident.getStatus().isOpen()) {
            checkSla(incident, clock, now);
        }
        return incident;
    }

    /** True if an incident with story {@code actual} got at least as far as {@code step}. */
    private static boolean reached(Story actual, Story step) {
        return switch (step) {
            case IN_PROGRESS -> actual == Story.IN_PROGRESS || actual == Story.AWAITING_INFO
                    || actual == Story.RESOLVED || actual == Story.VERIFIED || actual == Story.CLOSED;
            case VERIFIED -> actual == Story.VERIFIED || actual == Story.CLOSED;
            default -> actual == step;
        };
    }

    /** Moves the incident to a status at a past moment, after catching up on SLA checks until then. */
    private LocalDateTime move(Incident incident, LocalDateTime from, LocalDateTime at, Status target, User actor,
                               String note) {
        checkSla(incident, from, at);
        Status before = incident.getStatus();
        if (target == Status.RESOLVED) {
            incident.recordResolution(note);
        }
        incident.transitionTo(target, at);
        record(incident, at, actor, IncidentAction.STATUS_CHANGED, before, target, note);
        recordSignals(incident, at);
        return at;
    }

    /**
     * Runs the SLA check at each moment between {@code from} and {@code until}
     * at which something would have been noticed (75% used, deadline passed),
     * as the minute job would have done, and finally at {@code until}.
     */
    private void checkSla(Incident incident, LocalDateTime from, LocalDateTime until) {
        LocalDateTime clock = from;
        while (true) {
            Optional<LocalDateTime> next = nextCheckpoint(incident, clock, until);
            if (next.isEmpty()) {
                break;
            }
            clock = next.get();
            recordSignals(incident, clock);
            if (escalationPolicy.shouldEscalate(incident, clock)) {
                incident.escalate(clock);
                record(incident, clock, null, IncidentAction.ESCALATED, incident.getStatus(), incident.getStatus(),
                        escalationPolicy.explain(incident));
            }
        }
        recordSignals(incident, until);
    }

    private Optional<LocalDateTime> nextCheckpoint(Incident incident, LocalDateTime after, LocalDateTime until) {
        List<LocalDateTime> moments = new ArrayList<>();
        for (SlaTarget target : List.of(incident.responseTarget(), incident.resolutionTarget())) {
            if (target.isRunning()) {
                long allowed = target.allowed().getSeconds();
                Duration paused = target.paused();
                moments.add(target.start().plus(paused).plusSeconds(Math.round(allowed * Incident.AT_RISK_FRACTION)));
                moments.add(target.due().plusSeconds(1));
            }
        }
        return moments.stream().filter(moment -> moment.isAfter(after) && !moment.isAfter(until)).sorted().findFirst();
    }

    private void recordSignals(Incident incident, LocalDateTime at) {
        for (SlaSignal signal : incident.refreshSla(at)) {
            String target = signal.target().name();
            String due = DEADLINE.format(signal.target().due());
            if (signal.type() == SlaSignal.Type.AT_RISK) {
                record(incident, at, null, IncidentAction.SLA_AT_RISK, incident.getStatus(), incident.getStatus(),
                        "75%% of the %s time has been used. The %s deadline is %s.".formatted(target, target, due));
            } else {
                record(incident, at, null, IncidentAction.SLA_BREACHED, incident.getStatus(), incident.getStatus(),
                        "The %s deadline (%s) was missed.".formatted(target, due));
            }
        }
    }

    private void record(Incident incident, LocalDateTime at, User actor, IncidentAction action, Status before,
                        Status after, String note) {
        auditEntries.save(new AuditEntry(incident, at, actor, action, before, after, note));
    }

    private User demoUser(String name) {
        return users.findByEmail(name + "@itsm.local").orElseThrow(() -> new InvalidInputException(
                "The sample incidents need the demo account %s@itsm.local.".formatted(name)));
    }
}
