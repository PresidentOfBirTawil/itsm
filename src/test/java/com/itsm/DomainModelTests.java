package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The JPA mapping and the small rules built into the model. Each test is rolled back afterwards. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainModelTests {

    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private TechnicianRepository technicians;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private EntityManager entityManager;

    @Test
    void savedIncidentsGetConsecutiveCodes() {
        Incident first = incidents.save(newIncident("Payment page is down"));
        Incident second = incidents.save(newIncident("Cannot connect to the VPN"));

        assertThat(first.getCode()).matches("INC-\\d{4,}").isEqualTo(Incident.codeFor(first.getId()));
        assertThat(second.getId()).isEqualTo(first.getId() + 1);
        assertThat(second.getCode()).isEqualTo(Incident.codeFor(first.getId() + 1));
        assertThat(Incident.codeFor(1)).isEqualTo("INC-0001");
        assertThat(Incident.codeFor(12345)).isEqualTo("INC-12345");
    }

    @Test
    void incidentIsStoredWithItsLinksAndCanBeFoundByCode() {
        Employee reporter = employees.findAllByOrderByNameAsc().getFirst();
        Technician technician = technicians.findAllByOrderByNameAsc().getFirst();
        ITService service = services.findAllByOrderByCriticalityDescNameAsc().getFirst();
        Category category = categories.findByNameIgnoreCase("Application").orElseThrow();

        Incident incident = new Incident("Payment page is down", "Customers cannot pay.", reporter, service,
                Impact.HIGH, Urgency.HIGH);
        incident.setCategory(category);
        incident.prioritise(new PriorityAssessment(Priority.P1, 27, 3, 3, 3),
                slaPolicies.findByPriority(Priority.P1).orElseThrow());
        incident.assignTo(technician);
        String code = incidents.save(incident).getCode();

        // Write to the database and forget the object, so the next read really comes from the tables.
        entityManager.flush();
        entityManager.clear();

        Incident loaded = incidents.findByCode(code).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Payment page is down");
        assertThat(loaded.getReporter()).isEqualTo(reporter);
        assertThat(loaded.getService().getName()).isEqualTo(service.getName());
        assertThat(loaded.getCategory()).isEqualTo(category);
        assertThat(loaded.getAssignee()).isEqualTo(technician);
        assertThat(loaded.getImpact()).isEqualTo(Impact.HIGH);
        assertThat(loaded.getUrgency()).isEqualTo(Urgency.HIGH);
        assertThat(loaded.getStatus()).isEqualTo(Status.ASSIGNED);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getPriority()).isEqualTo(Priority.P1);
        assertThat(loaded.getAssessment()).isEqualTo(new PriorityAssessment(Priority.P1, 27, 3, 3, 3));
        assertThat(incidents.findByReporterIdOrderByCreatedAtDescIdDesc(reporter.getId())).containsExactly(loaded);
        assertThat(incidents.findByAssigneeIdAndStatusInOrderByResolutionDueAscIdAsc(technician.getId(),
                Status.openStatuses())).containsExactly(loaded);
        assertThat(incidents.countByAssigneeIdAndStatusIn(technician.getId(), Status.openStatuses())).isEqualTo(1);
    }

    @Test
    void newIncidentStartsAsNewWithNoPriorityOrAssignee() {
        Incident incident = incidents.save(newIncident("Printer is jammed"));

        assertThat(incident.getStatus()).isEqualTo(Status.NEW);
        assertThat(incident.getPriority()).isNull();
        assertThat(incident.getAssignee()).isNull();
        assertThat(incident.getResponseDue()).isNull();
        assertThat(incident.getResolutionDue()).isNull();
    }

    @Test
    void settingThePrioritySetsBothDeadlinesFromTheSlaPolicy() {
        Incident incident = newIncident("Payment page is down");
        SlaPolicy p1 = slaPolicies.findByPriority(Priority.P1).orElseThrow();

        incident.prioritise(new PriorityAssessment(Priority.P1, 27, 3, 3, 3), p1);

        assertThat(incident.getPriority()).isEqualTo(Priority.P1);
        assertThat(incident.getResponseDue()).isEqualTo(incident.getCreatedAt().plusMinutes(5));
        assertThat(incident.getResolutionDue()).isEqualTo(incident.getCreatedAt().plusMinutes(30));
    }

    @Test
    void anIncidentWithoutAPriorityIsStoredAndReadBackWithoutOne() {
        String code = incidents.save(newIncident("Printer is jammed")).getCode();
        entityManager.flush();
        entityManager.clear();

        Incident loaded = incidents.findByCode(code).orElseThrow();

        assertThat(loaded.getAssessment()).isNull();
        assertThat(loaded.getPriority()).isNull();
    }

    @Test
    void incidentWithoutATitleIsRejected() {
        assertThatThrownBy(() -> {
            incidents.save(newIncident("  "));
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void serviceCriticalityMustBeBetweenOneAndThree() {
        assertThatThrownBy(() -> new ITService("Too critical", 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ITService("Not critical", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slaResponseTargetCannotBeLaterThanTheResolutionTarget() {
        assertThatThrownBy(() -> new SlaPolicy(Priority.P1, 60, 30)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void techniciansKnowTheirSkills() {
        Technician technician = technicians.findAllByOrderByNameAsc().getFirst(); // Arjun Singh
        Category security = categories.findByNameIgnoreCase("Security").orElseThrow();
        Category database = categories.findByNameIgnoreCase("Database").orElseThrow();

        assertThat(technician.hasSkill(security)).isTrue();
        assertThat(technician.hasSkill(database)).isFalse();
        assertThatThrownBy(() -> technician.getSkills().add(database))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deletingACategoryDeletesItsKeywords() {
        Category category = categories.save(new Category("Telephony", List.of("phone", "voip")));
        entityManager.flush();
        assertThat(keywordRows(category.getId())).isEqualTo(2);

        categories.delete(category);
        entityManager.flush();

        assertThat(keywordRows(category.getId())).isZero();
    }

    private long keywordRows(Long categoryId) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from category_keywords where category_id = ?1")
                .setParameter(1, categoryId)
                .getSingleResult()).longValue();
    }

    private Incident newIncident(String title) {
        Employee reporter = employees.findAllByOrderByNameAsc().getFirst();
        ITService service = services.findAllByOrderByCriticalityDescNameAsc().getFirst();
        return new Incident(title, "Details of the problem.", reporter, service, Impact.MEDIUM, Urgency.MEDIUM);
    }
}
