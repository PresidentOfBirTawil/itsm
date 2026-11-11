Read CLAUDE.md and the brief in docs/brief.pdf. Build today's scope end to end:

1. Project: Spring Boot 4.1 (Java 25, Maven) in this folder with Spring Web, Thymeleaf, Spring Security, Spring Data JPA, Validation and H2 (file mode at ./data/itsm, H2 console on in dev). A Bootstrap 5 layout with a navbar.

2. Domain model (JPA): abstract User (name, email, passwordHash) with Employee, Technician (skills as a set of Category, maxWorkload, available) and Administrator, using single-table inheritance. ITService (name, criticality 1–3), Category (name, keywords), SlaPolicy (priority, responseMinutes, resolutionMinutes), Incident (code INC-0001…, title, description, reporter, service, category, impact, urgency, priority, status, assignee, timestamps, responseDue, resolutionDue). Enums for Priority, Impact, Urgency and Status.

3. Seed data: 1 admin, 4 technicians with different skills, 4 employees, 6 services, the brief's categories, and the brief's SLA policies (P1 5 min / 30 min, P2 15 min / 2 h, P3 1 h / 8 h, P4 4 h / 24 h).

4. Security: form login with BCrypt, employee self-registration, roles EMPLOYEE / TECHNICIAN / ADMIN, a separate dashboard and navbar per role, every URL protected by role on the server, logout.

5. Tests: the app starts; an employee cannot open /admin; a wrong password fails.

Write a README with run steps and the demo logins. Finish by telling me how to run it, and explain the inheritance, association and composition in the model in plain language.
