package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.itsm.domain.Category;
import com.itsm.domain.ITService;
import com.itsm.domain.Priority;
import com.itsm.domain.Role;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Technician;
import com.itsm.domain.User;
import com.itsm.repository.AdministratorRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.EmployeeRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import com.itsm.seed.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The demo data that is loaded into an empty database. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeedDataTests {

    @Autowired
    private UserRepository users;
    @Autowired
    private AdministratorRepository administrators;
    @Autowired
    private TechnicianRepository technicians;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private ITServiceRepository services;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SlaPolicyRepository slaPolicies;
    @Autowired
    private DataSeeder seeder;

    @Test
    void oneAdminFourTechniciansFourEmployees() {
        assertThat(administrators.count()).isEqualTo(1);
        assertThat(technicians.count()).isEqualTo(4);
        assertThat(employees.count()).isEqualTo(4);
        assertThat(users.count()).isEqualTo(9);
    }

    @Test
    void usersComeBackAsTheRightSubclassWithTheRightRole() {
        // One query on the single users table returns a mix of subclasses.
        List<User> everyone = users.findAll();

        assertThat(everyone).filteredOn(user -> user.getRole() == Role.ADMIN).hasSize(1);
        assertThat(everyone).filteredOn(user -> user.getRole() == Role.TECHNICIAN).hasSize(4)
                .allMatch(user -> user instanceof Technician);
        assertThat(everyone).filteredOn(user -> user.getRole() == Role.EMPLOYEE).hasSize(4);
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        assertThat(users.findAll()).allSatisfy(user -> {
            assertThat(user.getPasswordHash()).startsWith("$2").hasSize(60);
            assertThat(user.getPasswordHash()).doesNotContain(DataSeeder.ADMIN_PASSWORD,
                    DataSeeder.TECHNICIAN_PASSWORD, DataSeeder.EMPLOYEE_PASSWORD);
        });
    }

    @Test
    void techniciansHaveDifferentSkills() {
        List<Set<String>> skillSets = technicians.findAllByOrderByNameAsc().stream()
                .map(technician -> technician.getSkills().stream().map(Category::getName).collect(Collectors.toSet()))
                .toList();

        assertThat(skillSets).hasSize(4).doesNotHaveDuplicates().allMatch(skills -> !skills.isEmpty());

        // Between them the technicians cover every category.
        Set<String> covered = skillSets.stream().flatMap(Set::stream).collect(Collectors.toSet());
        Set<String> allCategories = categories.findAll().stream().map(Category::getName).collect(Collectors.toSet());
        assertThat(covered).isEqualTo(allCategories);
    }

    @Test
    void sixServicesWithCriticalityBetweenOneAndThree() {
        assertThat(services.findAll()).hasSize(6)
                .allMatch(service -> service.getCriticality() >= 1 && service.getCriticality() <= 3);
        assertThat(services.findAll()).extracting(ITService::getCriticality).contains(1, 2, 3);
    }

    @Test
    void theBriefsNineCategoriesEachWithKeywords() {
        assertThat(categories.findAllByOrderByNameAsc()).extracting(Category::getName).containsExactly(
                "Application", "Authentication", "Cloud", "Database", "Hardware",
                "Network", "Security", "Software", "Storage");
        assertThat(categories.findAllByOrderByNameAsc()).allMatch(category -> !category.getKeywords().isEmpty());
    }

    @Test
    void theBriefsSlaPolicies() {
        assertThat(slaPolicies.count()).isEqualTo(4);
        assertSla(Priority.P1, 5, 30);
        assertSla(Priority.P2, 15, 2 * 60);
        assertSla(Priority.P3, 60, 8 * 60);
        assertSla(Priority.P4, 4 * 60, 24 * 60);
    }

    @Test
    void seedingAgainAddsNothing() {
        seeder.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(9);
        assertThat(services.count()).isEqualTo(6);
        assertThat(categories.count()).isEqualTo(9);
        assertThat(slaPolicies.count()).isEqualTo(4);
    }

    private void assertSla(Priority priority, int responseMinutes, int resolutionMinutes) {
        SlaPolicy policy = slaPolicies.findByPriority(priority).orElseThrow();
        assertThat(policy.getResponseMinutes()).isEqualTo(responseMinutes);
        assertThat(policy.getResolutionMinutes()).isEqualTo(resolutionMinutes);
    }
}
