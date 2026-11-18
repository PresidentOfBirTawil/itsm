package com.quell.seed;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.quell.domain.Administrator;
import com.quell.domain.Category;
import com.quell.domain.Employee;
import com.quell.domain.ITService;
import com.quell.domain.KnowledgeArticle;
import com.quell.domain.Priority;
import com.quell.domain.SlaPolicy;
import com.quell.domain.Technician;
import com.quell.domain.User;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.KnowledgeArticleRepository;
import com.quell.repository.SlaPolicyRepository;
import com.quell.repository.UserRepository;
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
    private final KnowledgeArticleRepository articles;

    public DataSeeder(CategoryRepository categories, SlaPolicyRepository slaPolicies, ITServiceRepository services,
                      UserRepository users, PasswordEncoder passwordEncoder, KnowledgeArticleRepository articles) {
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.services = services;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.articles = articles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCategories();
        seedSlaPolicies();
        seedServices();
        seedUsers();
        alignDemoAccounts();
        seedKnowledgeBase();
        seedServiceDetails();
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

    /** The services of the first versions; {@link #seedServices} adds Workplace Devices to an untouched list of them. */
    private static final Set<String> FIRST_SERVICES = Set.of("Payment Application", "Authentication Service",
            "Database Cluster", "Corporate Network", "Cloud Storage", "Email & Collaboration");

    /** Seven services. Criticality: 3 = the business stops without it, 1 = an inconvenience. */
    private void seedServices() {
        if (services.count() > 0) {
            // Databases made by earlier versions: add the service for laptops and printers,
            // unless an administrator has already changed the list of services.
            Set<String> names = services.findAll().stream().map(ITService::getName).collect(Collectors.toSet());
            if (names.equals(FIRST_SERVICES)) {
                services.save(new ITService("Workplace Devices", 1));
                log.info("Added the Workplace Devices service");
            }
            return;
        }
        services.saveAll(List.of(
                new ITService("Payment Application", 3),
                new ITService("Authentication Service", 3),
                new ITService("Database Cluster", 3),
                new ITService("Corporate Network", 2),
                new ITService("Cloud Storage", 2),
                new ITService("Email & Collaboration", 1),
                new ITService("Workplace Devices", 1)));
        log.info("Seeded {} IT services", services.count());
    }

    /**
     * Which service needs which, and the words people use for each service.
     * Also fills them in on databases made by earlier versions, but never
     * overwrites what an administrator has set.
     */
    private void seedServiceDetails() {
        java.util.Map<String, List<String>> keywords = java.util.Map.of(
                "Payment Application", List.of("payment", "payments", "checkout", "card", "refund", "invoice", "billing", "transaction"),
                "Authentication Service", List.of("login", "log in", "sign in", "password", "sso", "two-factor", "otp", "mfa", "locked"),
                "Database Cluster", List.of("database", "sql", "query", "replication", "standby", "deadlock"),
                "Corporate Network", List.of("vpn", "wifi", "wi-fi", "network", "internet", "dns", "router", "switch", "firewall"),
                "Cloud Storage", List.of("drive", "share", "shared folder", "backup", "sync", "storage", "quota", "file"),
                "Email & Collaboration", List.of("email", "mail", "mailbox", "outlook", "calendar", "teams", "meeting"),
                "Workplace Devices", List.of("laptop", "desktop", "computer", "printer", "monitor", "screen", "keyboard",
                        "mouse", "battery", "headset", "webcam", "docking station", "charger"));
        java.util.Map<String, List<String>> needs = java.util.Map.of(
                "Payment Application", List.of("Database Cluster", "Authentication Service", "Corporate Network"),
                "Authentication Service", List.of("Database Cluster", "Corporate Network"),
                "Database Cluster", List.of("Corporate Network"),
                "Cloud Storage", List.of("Authentication Service", "Corporate Network"),
                "Email & Collaboration", List.of("Authentication Service", "Corporate Network"));

        List<ITService> all = services.findAll();
        boolean anyDependencies = all.stream().anyMatch(service -> !service.getDependsOn().isEmpty());
        for (ITService service : all) {
            if (service.getKeywords().isEmpty() && keywords.containsKey(service.getName())) {
                service.replaceKeywords(keywords.get(service.getName()));
            }
            if (!anyDependencies && needs.containsKey(service.getName())) {
                service.replaceDependencies(needs.get(service.getName()).stream()
                        .map(name -> services.findByName(name).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
            }
        }
    }

    /** One administrator, four technicians with different skills, four employees. */
    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }
        String adminHash = passwordEncoder.encode(ADMIN_PASSWORD);
        String technicianHash = passwordEncoder.encode(TECHNICIAN_PASSWORD);
        String employeeHash = passwordEncoder.encode(EMPLOYEE_PASSWORD);

        users.save(new Administrator("Neha Kapoor", "admin@quell.local", adminHash));

        users.saveAll(List.of(
                new Technician("Ravi Kumar", "tech1@quell.local", technicianHash, 5, skills("Network", "Hardware")),
                new Technician("Meera Nair", "tech2@quell.local", technicianHash, 4, skills("Database", "Storage", "Cloud")),
                new Technician("Arjun Singh", "tech3@quell.local", technicianHash, 3, skills("Security", "Authentication", "Network")),
                new Technician("Sara Thomas", "tech4@quell.local", technicianHash, 5, skills("Software", "Application", "Cloud"))));

        users.saveAll(List.of(
                new Employee("Priya Sharma", "emp1@quell.local", employeeHash),
                new Employee("Rahul Verma", "emp2@quell.local", employeeHash),
                new Employee("Ananya Iyer", "emp3@quell.local", employeeHash),
                new Employee("Karan Mehta", "emp4@quell.local", employeeHash)));

        log.info("Seeded {} users (demo logins are listed in README.md)", users.count());
    }

    /** The demo people and the address each one signs in with (the one-click demo sign-in uses them too). */
    private record DemoLogin(String name, String email) {
    }

    private static final List<DemoLogin> DEMO_LOGINS = List.of(
            new DemoLogin("Neha Kapoor", "admin@quell.local"),
            new DemoLogin("Ravi Kumar", "tech1@quell.local"),
            new DemoLogin("Meera Nair", "tech2@quell.local"),
            new DemoLogin("Arjun Singh", "tech3@quell.local"),
            new DemoLogin("Sara Thomas", "tech4@quell.local"),
            new DemoLogin("Priya Sharma", "emp1@quell.local"),
            new DemoLogin("Rahul Verma", "emp2@quell.local"),
            new DemoLogin("Ananya Iyer", "emp3@quell.local"),
            new DemoLogin("Karan Mehta", "emp4@quell.local"));

    /**
     * Databases made by earlier versions hold the demo people under an older {@code .local}
     * address with the same name before the @. Give them today's address, so that the one-click
     * demo sign-in and the sample incidents find them. Only a demo person is matched: the same
     * name, the same part before the @, a {@code .local} address, and nobody using today's address.
     */
    private void alignDemoAccounts() {
        List<User> everyone = users.findAll();
        for (DemoLogin login : DEMO_LOGINS) {
            if (users.findByEmail(login.email()).isPresent()) {
                continue;
            }
            String before = login.email().substring(0, login.email().indexOf('@') + 1);
            everyone.stream()
                    .filter(user -> login.name().equals(user.getName()))
                    .filter(user -> user.getEmail().startsWith(before) && user.getEmail().endsWith(".local"))
                    .findFirst()
                    .ifPresent(user -> {
                        log.info("Demo account {} now signs in as {}", user.getEmail(), login.email());
                        user.changeEmail(login.email());
                    });
        }
    }

    /** Starter articles for the problems every organisation has, so suggestions work from the first day. */
    private void seedKnowledgeBase() {
        if (articles.count() > 0) {
            return;
        }
        article("tech1", "VPN disconnects every few minutes", "Network", "Corporate Network",
                "Working from home or a hotel, the VPN connects but drops after a few minutes. Video calls freeze and shared drives disconnect.",
                """
                1. Make sure the VPN client is the latest version (Software Center → VPN Client → Update).
                2. Turn off power saving for the wifi adapter: Device Manager → Network adapters → your wifi adapter → Power Management → untick "Allow the computer to turn off this device".
                3. In the VPN client settings, switch the protocol from UDP to TCP. This helps on hotel and guest networks.
                4. Disconnect and connect again. If it still drops more than once an hour, report an incident and mention your location.""");
        article("tech1", "Wifi drops in meeting rooms", "Network", "Corporate Network",
                "Laptops lose the office wifi in meeting rooms or on one floor. Video calls freeze every few minutes.",
                """
                Users: connect to the wifi network called CORP (not CORP-GUEST) and forget the guest network so the laptop stops switching.
                Technicians: check the access point for that room on the controller. A rebooting or overloaded access point is the usual cause; replace it if it shows hardware errors.""");
        article("tech3", "Account locked after changing my password", "Authentication", "Authentication Service",
                "After a password change the account locks again and again, often within minutes, and sign-in says the account is locked.",
                """
                A phone or tablet is still trying the old password.
                1. Wait 15 minutes: the lock clears by itself.
                2. Update the password in the mail app on your phone and tablet, or remove the account there and add it again.
                3. Sign in again on the laptop.
                If it locks again within an hour, report an incident.""");
        article("tech3", "Forgot my password", "Authentication", "Authentication Service",
                "I cannot remember my password and cannot sign in to my laptop or email.",
                """
                Use self-service reset: open the sign-in page on any device, click "Forgot password", confirm with the code sent to your phone and choose a new password.
                The new password must be at least 12 characters. After the reset, update it on your phone too (see "Account locked after changing my password").""");
        article("tech3", "Two-factor sign-in code never arrives", "Authentication", "Authentication Service",
                "The text message or app notification with the sign-in code does not arrive, so I cannot complete the sign-in.",
                """
                1. Check that the phone has signal and that the date and time are set automatically.
                2. Use the authenticator app instead of text messages: it works offline.
                3. Use one of your backup codes if you saved them.
                4. Still stuck: report an incident and a technician will register your phone again after checking your identity.""");
        article("tech3", "Single sign-on keeps going round in a loop", "Authentication", "Authentication Service",
                "Signing in to an internal portal with SSO sends me back to the sign-in page again and again.",
                """
                Users: clear the browser's cookies for the portal, or try a private window.
                Technicians: an expired SSO certificate on the portal is the usual cause. Check the certificate dates in the identity provider and renew the certificate of the affected application.""");
        article("tech4", "Mailbox is full and rejects new mail", "Software", "Email & Collaboration",
                "People who email me get a bounce saying my mailbox is full or over quota.",
                """
                1. Empty "Deleted items" and "Junk".
                2. Search for messages larger than 10 MB and delete or save the attachments to your cloud drive.
                3. Turn on the online archive: File → Tools → Mailbox cleanup → Archive.
                Mail flows again within a few minutes after you are under the limit.""");
        article("tech4", "Mail client crashes when opening attachments", "Software", "Email & Collaboration",
                "The mail program closes or freezes as soon as I open an attachment.",
                """
                1. Start the mail client in safe mode (hold Ctrl while starting it). If it works there, an add-in is the cause: File → Options → Add-ins → turn them off one by one.
                2. Repair Office: Settings → Apps → Microsoft 365 → Modify → Quick repair.
                3. Make sure Office is up to date.""");
        article("tech1", "Printer jams or prints blank pages", "Hardware", null,
                "The floor printer jams on every job, or prints blank or striped pages.",
                """
                Jams: open every tray and door, remove torn pieces of paper, and fan the paper before loading it. Use the paper type set for the tray.
                Blank or striped pages: shake the toner cartridge gently, or replace it if the printer reports it is low.
                If the printer shows an error code, report an incident with the code and the printer's name on its label.""");
        article("tech1", "Laptop will not charge", "Hardware", null,
                "The battery stays at the same level or the charging light does not come on.",
                """
                1. Try another wall socket and another charger of the same type.
                2. Shut the laptop down completely, unplug it, hold the power button for 20 seconds, then connect the charger before starting it.
                3. If two chargers do not work, the battery or the charging port is faulty: report an incident and a technician will replace it, usually the same day.""");
        article("tech2", "Shared folder is read-only or access is denied", "Storage", "Cloud Storage",
                "I can open a shared folder but cannot save into it, or I get \"access denied\".",
                """
                Users: access to shared folders follows your team. Ask your manager to add you to the team's access group; it takes effect after you sign out and back in.
                Technicians: if a whole team lost write access at once, check the folder permissions after the last storage migration and restore the team's group.""");
        article("tech2", "Cloud drive sync is stuck", "Storage", "Cloud Storage",
                "The sync icon keeps spinning, files show a red cross, or changes do not appear on other computers.",
                """
                1. Pause syncing and resume it from the cloud icon in the taskbar.
                2. Check that no file name contains characters like : * ? " < > | and that no path is longer than 250 characters.
                3. Sign out of the sync client and sign in again. Large folders may take an hour to catch up.""");
        article("tech4", "Payment page times out at checkout", "Application", "Payment Application",
                "Customers see a spinner and then an error when they pay. Card payments fail or time out.",
                """
                Technicians:
                1. Check the payment gateway's status page and the error rate on the payment dashboard.
                2. If connections to the gateway are exhausted, raise the connection limit and restart the payment service.
                3. If the problem started after a release, roll the release back.
                Post an update on the status page so that colleagues do not report it again.""");
        article("tech3", "Suspicious email asking for a password", "Security", "Email & Collaboration",
                "I received an email that asks me to sign in or confirm my password. It looks like IT, but something feels wrong.",
                """
                1. Do not click the link and do not open attachments.
                2. Use the "Report phishing" button in the mail client, then delete the message.
                3. If you already entered your password: change it at once and report an incident with priority High. The security team will check your account.""");
        article("tech2", "Reports and queries are suddenly slow", "Database", "Database Cluster",
                "A report or query that used to take a minute now takes much longer, or times out.",
                """
                Technicians:
                1. Compare the query plan with last week's: a missing or unused index is the most common cause.
                2. Update the table statistics.
                3. Check for long-running locks and blocked sessions on the cluster.
                4. If the standby is lagging, free disk space before restarting replication.""");
        log.info("Seeded {} knowledge articles", articles.count());
    }

    private void article(String author, String title, String category, String service, String problem, String solution) {
        KnowledgeArticle article = new KnowledgeArticle(title, problem, solution,
                categories.findByNameIgnoreCase(category).orElse(null),
                service == null ? null : services.findByName(service).orElse(null),
                users.findByEmail(author + "@quell.local").orElse(null));
        articles.save(article);
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
