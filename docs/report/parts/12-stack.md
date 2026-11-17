# 11. Technology stack and selection

## 11.1 Everything used

| Layer | Technology | Version | What for |
|---|---|---|---|
| Language | Java (OpenJDK) | 25 | The whole server: domain model, services, intelligence |
| Framework | Spring Boot | 4.1.2 | Configuration, embedded Tomcat, dependency injection, scheduling |
| Web | Spring MVC, embedded Tomcat | 7.0.10, 11.0 | Controllers for pages and the REST API |
| Security | Spring Security | 7.1.2 | Sign-in, roles, CSRF, sessions, headers, the API filter chain |
| Persistence | Spring Data JPA, Hibernate ORM | 4.1.2, 7.4.13 | Repositories, object-relational mapping, Criteria queries |
| Validation | Jakarta Bean Validation (Hibernate Validator) | 9.1.5 | Form and API input rules |
| Templates | Thymeleaf + Spring Security extras | 3.1.5 | Server-rendered HTML, escaped by default |
| JSON | Jackson | 3.1.7 | API bodies, webhook messages |
| API docs | springdoc-openapi (Swagger UI) | 3.2.0 | OpenAPI 3.1 description and interactive documentation |
| Database | H2 (file and in-memory) | 2.4.240 | Desktop installations, development, tests |
| Database | PostgreSQL + JDBC driver | 16 / 17, 42.7.14 | Server installations |
| Front end | Bootstrap, Bootstrap Icons | 5.3.8, 1.13.2 | Responsive layout and components (served from the application as WebJars) |
| Front end | Inter (variable font) | 5.0.15 | Typography |
| Front end | Plain JavaScript (no framework) | ES2017 | Live updates (EventSource), chat, voice input (Web Speech API), attachments, command palette, theme, service worker |
| Build | Maven (wrapper) | 3.9.11 | Build, dependencies, tests |
| Testing | JUnit Jupiter, AssertJ, Mockito, Spring MockMvc, Awaitility | 6.0.3, 3.27.7, 5.23.0, 7.0, 4.3.0 | 617 automated tests |
| Browser checks | Playwright (Chromium) | | End-to-end checks of real pages during development |
| Packaging | jlink, jpackage, WiX (Windows) | JDK 25 | Native installers with a trimmed Java runtime |
| Containers | Docker, docker-compose | | Server deployment with PostgreSQL |
| CI/CD | GitHub Actions | | Test, build installers for 3 operating systems, publish releases |
| Diagrams | PlantUML | 1.2025 | All UML and ER diagrams |
| Version control | Git, GitHub | | Source code and releases |
