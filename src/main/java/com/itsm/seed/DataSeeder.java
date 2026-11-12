package com.itsm.seed;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.itsm.domain.Administrator;
import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.Technician;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with demo data when the application starts.
 * Each block runs only if its table is still empty, so restarting the
 * application never creates duplicates.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    public static final String ADMIN_PASSWORD = "admin12345";
    public static final String TECHNICIAN_PASSWORD = "tech12345";
    public static final String EMPLOYEE_PASSWORD = "emp12345";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final ITServiceRepository services;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(CategoryRepository categories, SlaPolicyRepository slaPolicies, ITServiceRepository services,
                      UserRepository users, PasswordEncoder passwordEncoder) {
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.services = services;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCategories();
        seedSlaPolicies();
        seedServices();
        seedUsers();
    }

    /** The nine categories from the project brief, each with keywords that hint at it. */
    private void seedCategories() {
        if (categories.count() > 0) {
            return;
        }
        categories.saveAll(List.of(
                new Category("Hardware", List.of("laptop", "desktop", "printer", "monitor", "keyboard", "mouse", "battery", "device")),
                new Category("Software", List.of("software", "install", "installation", "update", "license", "crash", "version")),
                new Category("Network", List.of("network", "wifi", "vpn", "internet", "connectivity", "dns", "router", "lan")),
                new Category("Database", List.of("database", "sql", "query", "table", "deadlock", "replication")),
                new Category("Cloud", List.of("cloud", "virtual machine", "container", "kubernetes", "deployment", "region")),
                new Category("Security", List.of("security", "virus", "malware", "phishing", "breach", "ransomware", "firewall")),
                new Category("Authentication", List.of("login", "password", "sso", "otp", "locked", "credentials", "sign in")),
                new Category("Application", List.of("application", "app", "portal", "payment", "transaction", "page", "unavailable")),
                new Category("Storage", List.of("storage", "disk", "drive", "backup", "quota", "space", "file share"))));
        log.info("Seeded {} categories", categories.count());
    }

    /** The SLA targets from the project brief. */
    private void seedSlaPolicies() {
        if (slaPolicies.count() > 0) {
            return;
        }
        slaPolicies.saveAll(List.of(
                new SlaPolicy(Priority.P1, 5, 30),          // 5 min / 30 min
                new SlaPolicy(Priority.P2, 15, 2 * 60),     // 15 min / 2 h
                new SlaPolicy(Priority.P3, 60, 8 * 60),     // 1 h / 8 h
                new SlaPolicy(Priority.P4, 4 * 60, 24 * 60) // 4 h / 24 h
        ));
        log.info("Seeded {} SLA policies", slaPolicies.count());
    }

    /** Six services. Criticality: 3 = the business stops without it, 1 = an inconvenience. */
    private void seedServices() {
        if (services.count() > 0) {
            return;
        }
        services.saveAll(List.of(
                new ITService("Payment Application", 3),
                new ITService("Authentication Service", 3),
                new ITService("Database Cluster", 3),
                new ITService("Corporate Network", 2),
                new ITService("Cloud Storage", 2),
                new ITService("Email & Collaboration", 1)));
        log.info("Seeded {} IT services", services.count());
    }

    /** One administrator, four technicians with different skills, four employees. */
    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }
        String adminHash = passwordEncoder.encode(ADMIN_PASSWORD);
        String technicianHash = passwordEncoder.encode(TECHNICIAN_PASSWORD);
        String employeeHash = passwordEncoder.encode(EMPLOYEE_PASSWORD);

        users.save(new Administrator("Neha Kapoor", "admin@itsm.local", adminHash));

        users.saveAll(List.of(
                new Technician("Ravi Kumar", "tech1@itsm.local", technicianHash, 5, skills("Network", "Hardware")),
                new Technician("Meera Nair", "tech2@itsm.local", technicianHash, 4, skills("Database", "Storage", "Cloud")),
                new Technician("Arjun Singh", "tech3@itsm.local", technicianHash, 3, skills("Security", "Authentication", "Network")),
                new Technician("Sara Thomas", "tech4@itsm.local", technicianHash, 5, skills("Software", "Application", "Cloud"))));

        users.saveAll(List.of(
                new Employee("Priya Sharma", "emp1@itsm.local", employeeHash),
                new Employee("Rahul Verma", "emp2@itsm.local", employeeHash),
                new Employee("Ananya Iyer", "emp3@itsm.local", employeeHash),
                new Employee("Karan Mehta", "emp4@itsm.local", employeeHash)));

        log.info("Seeded {} users (demo logins are listed in README.md)", users.count());
    }

    private Set<Category> skills(String... categoryNames) {
        Set<Category> skills = new LinkedHashSet<>();
        Arrays.stream(categoryNames)
                .map(name -> categories.findByNameIgnoreCase(name)
                        .orElseThrow(() -> new IllegalStateException("Category not found: " + name)))
                .forEach(skills::add);
        return skills;
    }
}
