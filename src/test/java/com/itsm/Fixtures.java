package com.itsm;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.PriorityAssessment;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain objects for unit tests that need no database. */
final class Fixtures {

    private Fixtures() {
    }

    static Employee employee() {
        return new Employee("Priya Sharma", "priya@example.com", "hash");
    }

    static Category category(String name) {
        return new Category(name);
    }

    static ITService service(int criticality) {
        return new ITService("Service with criticality " + criticality, criticality);
    }

    /** A technician with a database id, which only the database normally hands out. */
    static Technician technician(long id, String name, int maxWorkload, Category... skills) {
        Technician technician = new Technician(name, name.toLowerCase().replace(' ', '.') + "@example.com", "hash",
                maxWorkload, new LinkedHashSet<>(Arrays.asList(skills)));
        ReflectionTestUtils.setField(technician, "id", id);
        return technician;
    }

    static Incident incident(Impact impact, Urgency urgency, ITService service, Category category) {
        Incident incident = new Incident("Something is broken", "Details.", employee(), service, impact, urgency);
        incident.setCategory(category);
        return incident;
    }

    static Incident incident() {
        return incident(Impact.MEDIUM, Urgency.MEDIUM, service(2), category("Network"));
    }

    /** An incident that was reported at the given moment, so that tests can do exact SLA arithmetic. */
    static Incident incidentReportedAt(LocalDateTime reportedAt) {
        Incident incident = incident();
        ReflectionTestUtils.setField(incident, "createdAt", reportedAt);
        ReflectionTestUtils.setField(incident, "updatedAt", reportedAt);
        return incident;
    }

    static PriorityAssessment assessment(Priority priority) {
        return new PriorityAssessment(priority, 8, 2, 2, 2);
    }

    static SlaPolicy policy(Priority priority) {
        return new SlaPolicy(priority, 60, 480);
    }

    /** The legal moves that take a brand-new incident to each status. */
    private static List<Status> pathTo(Status status) {
        return switch (status) {
            case NEW, CLASSIFIED, ASSIGNED -> List.of();
            case ACKNOWLEDGED -> List.of(Status.ACKNOWLEDGED);
            case IN_PROGRESS -> List.of(Status.ACKNOWLEDGED, Status.IN_PROGRESS);
            case AWAITING_INFO -> List.of(Status.ACKNOWLEDGED, Status.IN_PROGRESS, Status.AWAITING_INFO);
            case RESOLVED -> List.of(Status.ACKNOWLEDGED, Status.IN_PROGRESS, Status.RESOLVED);
            case VERIFIED -> List.of(Status.ACKNOWLEDGED, Status.IN_PROGRESS, Status.RESOLVED, Status.VERIFIED);
            case CLOSED -> List.of(Status.ACKNOWLEDGED, Status.IN_PROGRESS, Status.RESOLVED, Status.VERIFIED,
                    Status.CLOSED);
        };
    }

    /** An incident that has reached the given status the proper way: through legal moves only. */
    static Incident incidentIn(Status status) {
        Incident incident = incident();
        if (status == Status.NEW) {
            return incident;
        }
        incident.prioritise(assessment(Priority.P3), policy(Priority.P3)); // NEW -> CLASSIFIED
        if (status == Status.CLASSIFIED) {
            return incident;
        }
        incident.assignTo(technician(1, "Ravi Kumar", 5));                // CLASSIFIED -> ASSIGNED
        pathTo(status).forEach(incident::transitionTo);
        return incident;
    }
}
