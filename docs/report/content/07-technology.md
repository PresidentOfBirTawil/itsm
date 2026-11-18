# Technology {#technology .chapter}

Java 25 and Spring Boot 4 on the server, server-rendered HTML with a little plain JavaScript in the browser, and a relational database reached through JPA. Each choice favours a clear object model and an application that anyone can install and run.

## The stack

::: {.keys .compact}
| Layer             | Technology                                                       | Version              | Used for                                                                  |
|-------------------|------------------------------------------------------------------|----------------------|---------------------------------------------------------------------------|
| Language          | Java (OpenJDK)                                                   | 25                   | The whole server: domain model, services, intelligence                    |
| Framework         | Spring Boot, with embedded Tomcat                                | 4.1.2                | Configuration, dependency injection, scheduling, the web server           |
| Web               | Spring MVC                                                       | 7.0                  | Page controllers and the REST API                                          |
| Security          | Spring Security                                                  | 7.1.2                | Sign-in, roles, CSRF, sessions, headers, the API key filter chain         |
| Persistence       | Spring Data JPA, Hibernate ORM                                   | 4.1, 7.4.13          | Repositories, object-relational mapping, Criteria queries                 |
| Validation        | Jakarta Bean Validation (Hibernate Validator)                    | 9.1                  | Rules on every form and API body                                          |
| Pages             | Thymeleaf with Spring Security extras                            | 3.1.5                | Server-rendered HTML, escaped by default                                  |
| JSON, API docs    | Jackson; springdoc-openapi (Swagger UI)                          | 3.1.7; 3.2.0         | API bodies and webhook messages; OpenAPI 3.1 and interactive documentation |
| Databases         | H2 (file and in-memory); PostgreSQL with its JDBC driver         | 2.4.240; 16–17       | Desktop installations and tests; servers                                  |
| Interface         | Bootstrap and Bootstrap Icons, restyled; Geist and Geist Mono    | 5.3.8, 1.13          | Layout and components under Quell's own design system                     |
| Browser scripts   | Plain JavaScript, no framework                                   | ES2017               | Live updates, chat, voice input, uploads, command palette, themes, offline page |
| Build and tests   | Maven wrapper; JUnit, AssertJ, Mockito, MockMvc, Awaitility       | 3.9; 6.0             | Build and 619 automated tests                                             |
| Delivery          | jlink and jpackage, Docker Compose, GitHub Actions               | —                    | Installers with Java included, the server image, CI and releases          |
| Diagrams          | PlantUML                                                         | 1.2025               | Every UML and ER diagram in this report                                   |
:::

## The nine questions of the brief

::: {.qa}
**1 · Why this programming language?** Java makes the concepts of the course explicit in the language: classes, abstract classes, interfaces, access modifiers and records, so a reader sees `Technician extends User` and `SkillBasedStrategy implements AssignmentStrategy` in the code. Static typing finds whole classes of mistakes before the program runs. Java also has the most mature ecosystem for exactly this kind of enterprise system, so the effort went into business rules rather than infrastructure.

**2 · Why this frontend technology?** The users are staff working with forms, queues and detail pages. Pages rendered on the server are the simplest thing that works: one language, one build, and the server decides what each role is shown, so a button a user may not press is never sent. Nine small scripts add what a page alone cannot do (live updates, chat, voice, drag-and-drop, the command palette, the installable app), and every page still works without them. Programs get the REST API instead.

**3 · Why this backend technology?** Spring Boot supplies, already integrated and proven, every piece the brief asks for: web layer, authentication and roles, persistence, validation, scheduled jobs, transactions and testing. Its dependency injection makes the patterns cheap: `AssignmentService` simply receives every `AssignmentStrategy` that exists, and the publisher every listener.

**4 · Why this database?** The data is relational: an incident references a reporter, a service, a category and a technician, and foreign keys, unique constraints and transactions keep that consistent. H2 runs inside the application, so the installers and an evaluator need nothing else; servers use PostgreSQL with the same code, because all access goes through JPA. Both have been run end to end.

**5 · Why this architecture?** A layered, modular monolith. Reporting an incident must save the incident, its priority, its assignment and its audit trail together: one transaction in one process, but distributed transactions or compensation across microservices, plus service discovery and several deployments, for an application used by one organisation. The parts that could become separate services later (notifications, webhooks, reporting) are already decoupled through events and interfaces.

**6 · What alternatives were considered?**
:::

::: {.keys .compact}
| Area          | Alternative                              | Why not chosen                                                                                           |
|---------------|------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Language      | Python (Django, FastAPI)                 | Fast to write, but dynamic typing hides interface errors until run time, and the OOP structure is less visible. |
| Language      | TypeScript (Node.js), C# (.NET), Kotlin  | Node's classical OOP is weaker; .NET is equivalent but the course is taught in Java; Kotlin adds a second language for no design benefit. |
| Frontend      | React or Angular single-page app         | A second project, build and state, and a token-secured API for every screen, for pages that are mostly forms and tables. |
| Frontend      | Native mobile app                        | Staff work at desks; the responsive web app installs on a phone instead.                                 |
| Backend       | Quarkus, Micronaut, Jakarta EE           | Faster start-up matters for serverless, not here; smaller community, or an application server to install. |
| Database      | MongoDB; SQLite                          | Many relationships need transactions across them; SQLite is weaker under concurrent writes and in Hibernate. |
| Architecture  | Microservices; serverless functions      | See question 5; a job that runs every minute and user sessions do not fit short-lived functions.        |
:::

::: {.qa}
**7 · Advantages and limitations.** *Advantages:* one language and one command to run; strong typing and long-term support; security, transactions, validation and scheduling from a tested framework; OOP concepts mapped directly onto language features; 619 tests that need nothing but Java; the database can be exchanged without code changes. *Limitations:* H2 is meant for one machine, so servers use PostgreSQL; server-rendered pages reload on most actions; Spring Boot needs more memory and a few seconds to start; behaviour hidden behind annotations such as `@Transactional` must be learnt; generated SQL must still be understood when a query is slow; sessions in memory need an extra component for several servers.

**8 · How does the technology support object-oriented design?** Abstract classes and interfaces carry the abstractions (`User`, `IncidentState`, five interfaces in all); private fields without setters protect the rules; `extends` with JPA's single-table inheritance maps the user hierarchy to one table; overriding and interfaces chosen at run time give polymorphism; `@ManyToOne` and `@ManyToMany` map associations; `@Embedded` and `@ElementCollection` map composition; records give immutable values; dependency injection lets every class program to an interface. Above all, JPA stores objects: the domain model is ordinary classes with behaviour, and the framework adapts to it rather than the other way round.

**9 · How would the system evolve with many more users?** In the order the limits would be reached, without changing the domain model, the states, the strategies or the listeners:
:::

::: {.ids .compact}
| Step | Limit reached                                   | Change                                                                                                          |
|------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| 1    | One embedded database                           | PostgreSQL (done: `prod` profile and Docker); versioned migrations with Flyway; a tuned connection pool.         |
| 2    | Lists and reports load whole tables             | Pagination everywhere (the API already pages), indexes on status, assignee, service and deadlines, aggregate queries, caches for the catalogue. |
| 3    | One server                                      | Several instances behind a load balancer: shared sessions (Spring Session), one instance runs the SLA job (ShedLock), the round-robin pointer in the database. |
| 4    | Notifications in the request                    | The webhook outbox (done) extended to all notifications, delivered by a message broker; live updates shared between instances. |
| 5    | Search and reports compete with daily work      | A read replica; full-text search for duplicate candidates.                                                       |
| 6    | Several teams, uneven load                      | Split along the existing packages: notifications and reporting first; incident, assignment and SLA stay together. |
:::
