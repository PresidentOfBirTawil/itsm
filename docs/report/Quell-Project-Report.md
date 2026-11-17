::: {.cover}
![](../../src/main/resources/static/img/logo-512.png){.cover-logo width=96px}

# Quell {.unnumbered .cover-title}

[Intelligent IT Service Management & Incident Resolution System]{.cover-subtitle}

[Project report · CS F213 / MAC F212 Object-Oriented Programming · Semester I, 2026–2027]{.cover-course}

| | |
|---|---|
| **Group number** | [fill in] |
| **Members** | [Name 1, ID] · [Name 2, ID] · [Name 3, ID] · [Name 4, ID] |
| **Source code** | <https://github.com/PresidentOfBirTawil/itsm> |
| **Downloads** | <https://github.com/PresidentOfBirTawil/itsm/releases/latest> |
| **Version** | Quell 1.2.0 · 22 November 2026 |
:::

# Summary {.unnumbered}

**Quell** is a complete IT service management product: employees report IT problems in seconds (by form,
by chatting with an assistant, or by voice), and the system prioritises, routes, tracks and escalates
them automatically, learns from every resolved incident, and tells everyone what is going on. It was
designed as a real product rather than a classroom exercise: it installs with a one-click installer on
Windows, macOS and Linux, runs as a container with PostgreSQL on a server, installs as an app on a
phone, and connects to other tools through a documented REST API and webhooks for Slack and Microsoft
Teams.

At its core is an object-oriented domain model in Java 25: an `Incident` that enforces its own nine-status
lifecycle through the **State** pattern, three interchangeable assignment rules behind a **Strategy**
interface, an **Observer**-based event system that keeps the audit trail, notifications, live updates,
the learning classifier and webhooks out of the business logic, and a single exception hierarchy that
turns every broken rule into a clear message.

| | |
|---|---|
| **Mandatory modules** | All implemented: three roles with role-specific interfaces, incident reporting, automatic priority, SLA policies and monitoring, automatic and manual assignment, nine-status lifecycle, audit trail, notifications, search, dashboards, reports, exception handling, security. |
| **Innovation challenges of the brief** | 15 of the 16 listed (all except microservices, which was rejected for good reasons; section 11, question 5): machine-learning classification, resolution recommendation, similarity detection, automated root-cause analysis, predictive SLA, service dependency graph, real-time notifications, desktop and mobile alerts, voice reporting, chatbot reporting, cloud deployment, event-driven architecture, real-time dashboard, analytics, automated escalation. |
| **Beyond the brief** | REST API with API keys and OpenAPI documentation, webhooks to Slack, Teams and any app, screenshot attachments, a public status page, a knowledge base, an installable app (PWA), native installers, Docker and PostgreSQL, dark and light themes, a command palette. |
| **Size** | 222 Java classes (16,700 lines) in 23 packages, 41 page templates, 21 database tables. |
| **Quality** | 617 automated tests in 43 test classes, all passing; checked end to end in a real browser and on PostgreSQL; built, tested and packaged for three operating systems by continuous integration on every change. |

# 1. Problem analysis

## The problem

When an IT service fails, the people affected need one place to report it, and the IT team needs to
know which failure to work on first. Without a system, reports arrive by email, chat and phone. The
same outage is reported many times, nobody knows who is working on what, a critical outage waits
behind a broken mouse, and nobody notices that a promised response time has passed until a user
complains.

## Stakeholders

| Stakeholder | What they need from the system |
|---|---|
| Employee (reporter) | Report a problem in under a minute, see who has it and what is happening, be told when it is fixed, and be able to say "it is not fixed". |
| Technician | One queue of the incidents assigned to them, ordered by deadline. Everything about an incident on one page. No more work than they can handle. |
| Administrator / service desk manager | See the whole desk at a glance: what is open, what is critical, what is late, who is overloaded. Configure services, categories, SLA policies, technicians and the assignment rule without a programmer. |
| IT management | Evidence: SLA compliance, resolution times, workload per technician, a complete audit trail. |
| Business owners of the services | Critical services (payments, authentication) are dealt with before unimportant ones. |
| Auditors | Every change to an incident is recorded with who, what and when, and cannot be edited. |
| The development team | A design where a new category, assignment rule, notification channel or lifecycle rule can be added without rewriting unrelated code. |

## Existing challenges

- **No single source of truth.** Reports are scattered across channels and get lost.
- **Priority by whoever shouts loudest.** There is no consistent rule, so two people rank the same
  incident differently.
- **Manual assignment.** A manager reads each report and picks a technician, which is slow and ignores
  skills and current workload.
- **SLAs checked after the fact.** A missed deadline is discovered when it is too late to prevent it.
- **Duplicates.** One outage produces many reports and several technicians start on the same problem.
- **No history.** Nobody can show afterwards what was done, by whom and when.

## Proposed solution

A product with three roles, used from a browser, an installed app or another system through its API.
An employee reports an incident on a form or by chatting with the assistant. The system then, without
anyone doing anything: recognises the affected service from the words used, checks whether the problem
is a known outage (and offers to join it instead), suggests knowledge articles that may solve it at once,
chooses the category (keyword rules first, then a machine-learning classifier trained on the
organisation's own history), calculates a priority from impact, urgency and the criticality of the
service, sets response and resolution deadlines from the SLA policy, links a possible duplicate,
assigns a technician with the rule the administrator has chosen, records each step in an audit trail,
notifies the people concerned in the app, by e-mail, as desktop alerts and through webhooks, and updates
every open screen live.

The incident then follows a fixed lifecycle of nine statuses. The technician sees similar past fixes and
a predicted finish time; the administrator sees which incidents will breach next, and when several
services fail at once the system points to the service they all depend on. A job checks every open
incident once a minute, warns at 75% of the allowed time, records breaches and escalates a critical
incident nobody has acknowledged. A good fix becomes a knowledge article in one click, so the next
person with the same problem may not need a technician at all.

## Assumptions

- One organisation uses one installation; there is no separation between tenants. (The landing page
  shows how it would be sold per technician; multi-tenancy is future work.)
- Users have a modern browser. There is no native mobile app; the web app installs on a phone as a
  progressive web app, and desktop installers exist for Windows, macOS and Linux.
- Every incident is about exactly one service from the catalogue. Services can depend on other services.
- Employees register themselves. Technicians and administrators are created by an administrator.
- SLA times are clock time, 24 hours a day. There are no business hours or holidays.
- Impact is chosen by the reporter (Low: one or two people; Medium: a team; High: many people or
  customers) and rises automatically when more people say "I'm affected too" (3 people: at least
  Medium, 5: High).
- E-mail is simulated: messages are stored in an outbox that the administrator can read. A real mail
  server is one class implementing `NotificationChannel`.
- The machine-learning features learn only from data inside the installation. No text is sent to an
  outside service, so they work offline and keep the organisation's data private.
- The server clock is the reference for all times.

# 2. Requirements specification

## 2.1 User roles

| Role | Can do | Cannot do |
|---|---|---|
| **Employee** | Register. Report an incident on the form or by chatting with the assistant (also by voice), with screenshots. Follow a known problem ("I'm affected too"). Check the service status page. Search and rate knowledge articles. See their own incidents and their timelines. Answer an information request. Confirm a fix or reopen. Rate the resolution. Read their notifications and desktop alerts. Make API keys. | See other people's incidents. Change a status that belongs to the technician. Open any technician or administrator page. |
| **Technician** | See their queue, ordered by deadline. Acknowledge, start work, add investigation notes, request information, resolve with a resolution note, close a verified incident. Correct the classification of their own incidents. Use suggested solutions; write knowledge articles. Attach files. Search all incidents and open them read-only. | Change incidents assigned to someone else. Assign incidents. Open administrator pages. |
| **Administrator** | Dashboard, reports and audit log. Search all incidents. Assign, reassign, reopen, close, reclassify. Choose the assignment strategy. Manage users, technicians (skills, maximum workload, availability), services and their dependencies, categories and SLA policies. Connect webhooks. Read the email outbox. Switch demo SLA on. Reset demo data. | Report incidents or make the technician's moves (acknowledge, resolve) on someone's behalf. |

## 2.2 Functional requirements

| ID | Requirement | Where |
|---|---|---|
| FR-01 | Users sign in with email and password; employees can register themselves. | `security/`, `RegistrationService` |
| FR-02 | Each role has its own dashboard and menu; every address is protected by role on the server. | `SecurityConfig` |
| FR-03 | An employee reports an incident with title, description, service, optional category, impact and urgency. | `EmployeeController`, `IncidentService.report` |
| FR-04 | Each incident gets a unique readable code (INC-0001, INC-0002, …). | `Incident` (`@PrePersist`) |
| FR-05 | The system calculates the priority (P1–P4) and stores how it was worked out. | `WeightedPriorityCalculator`, `PriorityAssessment` |
| FR-06 | The system sets response and resolution deadlines from the SLA policy of the priority. | `SlaService`, `Incident.prioritise` |
| FR-07 | The system assigns a technician automatically using a strategy the administrator can change at runtime. | `AssignmentService`, `assignment/` |
| FR-08 | An incident moves through nine statuses; only the legal moves are possible and each belongs to a role. | `domain/state/` |
| FR-09 | Technicians add investigation notes, request information, and record a resolution. | `IncidentService.addNote`, `changeStatus` |
| FR-10 | The reporter confirms the fix or reopens, and can rate the resolution from 1 to 5. | `ResolvedState`, `IncidentService.rate` |
| FR-11 | Every action on an incident is recorded in an audit trail that cannot be edited. | `AuditTrailListener`, `AuditEntry` |
| FR-12 | The people concerned are notified in the application and by (simulated) email. | `notification/` |
| FR-13 | SLAs are monitored every minute: a warning at 75% of the time, a breach after the deadline. The clock pauses while waiting for information. | `SlaMonitor`, `SlaService`, `Incident.refreshSla` |
| FR-14 | Possible duplicates are detected and linked. | `DuplicateDetector` |
| FR-15 | A critical incident nobody has acknowledged in time is escalated to the administrators. | `EscalationPolicy`, `AutoEscalation` |
| FR-16 | A category is suggested from the words of the report when the reporter chooses none. | `CategorySuggester` |
| FR-17 | Incidents can be searched by any combination of code, status, priority, service, category, technician, SLA state and date range. | `IncidentSpecifications` |
| FR-18 | The administrator has a dashboard (totals, waiting, escalated, most likely to breach, workload, incidents per service). | `AdminController` |
| FR-19 | The administrator manages users, technicians, services, categories and SLA policies. | `AdminTechnicianController`, `AdminCatalogueController` |
| FR-20 | The administrator can assign and reassign by hand; a technician who is unavailable or full is refused. | `AssignmentService.assignManually` |
| FR-21 | The classification of an open incident can be corrected; priority and deadlines are recalculated and a changed priority is announced. | `IncidentService.reclassify` |
| FR-22 | Reports: incidents by status, priority and category, SLA compliance, resolution time against target, figures per technician. | `ReportService` |
| FR-23 | An audit log across all incidents with filters. | `AdminInsightController` |
| FR-24 | Demo data can be reset and filled with realistic past incidents. | `DemoDataService` |
| FR-25 | Open pages update by themselves: when an incident is created or changes, the administrator's dashboard, the technician's queue and the incident lists are refreshed in place, and the bell count changes at once. | `live/`, `LiveChannel`, `static/js/live.js` |
| FR-26 | Employees can report by chatting with an assistant (typed or spoken), which recognises the service, offers a known outage or knowledge articles, asks impact and urgency in plain words and files the incident by the normal rules. | `assistant/`, `AssistantController` |
| FR-27 | Voice input: a microphone button turns speech into text on the report form and in the assistant. | `static/js/voice-input.js` |
| FR-28 | Screenshots, PDFs and log files can be attached when reporting and on the incident page (drag and drop, paste, choose). | `AttachmentService`, `AttachmentController` |
| FR-29 | A knowledge base of articles that anyone can search and rate; technicians write articles, also straight from a resolved incident. | `knowledge/`, `KnowledgeController` |
| FR-30 | Technicians see suggested solutions for each incident: matching articles and fixes of similar past incidents, ranked by similarity. | `ResolutionRecommender` |
| FR-31 | The category is chosen by a machine-learning classifier when no keyword matches, and the classifier warns when an incident seems filed under the wrong category. | `CategoryClassifier`, `NaiveBayesClassifier` |
| FR-32 | Each open incident has a predicted finish time and a risk of missing its deadline, learned from similar past incidents. | `SlaPredictor` |
| FR-33 | Services can depend on other services; a status page shows the health of every service, a dependency map, known problems and the probable root cause when several fail together. | `status/`, `StatusController` |
| FR-34 | Employees can say "I'm affected too" to follow a known problem instead of reporting it again; the impact rises with the number of people affected and everyone is told when it is fixed. | `IncidentService.joinAsAffected` |
| FR-35 | New notifications appear as desktop alerts while the app is in the background. | `LiveChannel`, `static/js/live.js` |
| FR-36 | The application installs as an app on phones and computers (progressive web app) and shows an offline page when the server cannot be reached. | `AppManifestController`, `static/sw.js` |
| FR-37 | A REST API (`/api/v1`) lets other systems list, read, report and work on incidents and read status, services, categories and the knowledge base, with interactive documentation. | `api/` |
| FR-38 | Users make and revoke personal API keys that act with their own permissions. | `ApiTokenService`, `AccountController` |
| FR-39 | Administrators connect webhooks to Slack, Microsoft Teams or any app, choose the events, see a delivery log and send a test message. | `webhook/`, `AdminWebhookController` |
| FR-40 | A command palette (Ctrl/⌘ K) finds any incident or article and jumps to any page; dark and light themes. | `QuickSearchController`, `command-palette.js` |
| FR-41 | One-click installers for Windows, macOS and Linux with Java included, and a container image with PostgreSQL for servers. | `packaging/`, `desktop/`, `Dockerfile` |

## 2.3 Non-functional requirements

| ID | Quality | Requirement | How it is met |
|---|---|---|---|
| NFR-01 | Security | Only authenticated users; each role limited to its own functions; passwords and keys never stored in readable form; resistant to the common web attacks. | Section 10. |
| NFR-02 | Maintainability | A new category, assignment strategy, notification channel, event reaction or priority rule needs no change to unrelated code. | Categories are data. Strategies, channels and listeners are found by interface (section 6). The learning classifier and webhooks were added as new listeners without changing the incident services. |
| NFR-03 | Reliability | A failure in a side task must not undo or block the main task. | Notifications are sent after commit, each channel in its own transaction; webhooks go through a transactional outbox and are retried; a broken receiver never affects the incident. |
| NFR-04 | Data integrity | An incident is never left half-updated. | Each user action is one database transaction; any exception rolls all of it back. |
| NFR-05 | Auditability | Who did what and when is always known. | Audit entries are written in the same transaction as the change and have no update or delete function. |
| NFR-06 | Usability | Understandable without training; clear feedback on every action; works on a phone. | Responsive layout, a message after every action, only the buttons a user may press are shown, plain-language assistant, command palette, dark and light themes. |
| NFR-07 | Persistence | Data survives a restart. | H2 file database on a desktop; PostgreSQL on a server (`prod` profile). |
| NFR-08 | Performance | Pages respond quickly at the size of one organisation. | Indexed lookups, related objects fetched in one query, paged API lists, the SLA check takes milliseconds for hundreds of incidents, the classifier is trained once and updated incrementally. |
| NFR-09 | Testability | Rules can be tested without a server or database. | Rules live in plain classes. 617 automated tests. |
| NFR-10 | Portability | Runs on Windows, macOS and Linux with nothing else to install. | Native installers with a bundled Java runtime; Maven wrapper; container image. |
| NFR-11 | Error tolerance | A user never sees a stack trace; an API client gets a machine-readable error. | `ErrorHandler` for pages, `ApiExceptionHandler` with RFC 9457 problem details for the API. |
| NFR-12 | Upgradability | A newer version runs on an existing database. | New columns are nullable, enum values are stored as text, new seed data is added only where it is missing. |
| NFR-13 | Interoperability | Other systems can read and change incidents and be told about changes. | Versioned REST API with an OpenAPI description; webhooks in the formats Slack and Teams expect, and signed JSON. |
| NFR-14 | Privacy | No incident text leaves the organisation unless an administrator connects a webhook. | All intelligence runs inside the application; no external AI service. |

# 3. Business rules

| ID | Rule | Enforced in |
|---|---|---|
| BR-01 | Priority score = impact (1–3) × urgency (1–3) × service criticality (1–3). 18–27 is P1, 9–17 is P2, 4–8 is P3, 1–3 is P4. | `WeightedPriorityCalculator` |
| BR-02 | SLA times: P1 respond in 5 min, resolve in 30 min. P2: 15 min, 2 h. P3: 1 h, 8 h. P4: 4 h, 24 h. Both are counted from the moment of the report. | `SlaPolicy`, `Incident.prioritise` |
| BR-03 | The lifecycle is NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED, with two side steps: IN_PROGRESS ⇄ AWAITING_INFO, and RESOLVED → IN_PROGRESS (reopen). No other move is possible. | `domain/state/` |
| BR-04 | Each move belongs to a role. Only the assigned technician acknowledges, starts, requests information and resolves. Only the reporter confirms or reopens. A technician or administrator closes. | `Transition`, `User.canWorkOn`, `IncidentService.changeStatus` |
| BR-05 | Requesting information, resolving and an administrator's reopen need a note. Resolving records the note as the resolution. | `Transition.noteRequired`, `Incident.recordResolution` |
| BR-06 | A CLOSED incident never changes again (it can still be rated). | `ClosedState` |
| BR-07 | A technician is given an incident only if their account is enabled, they are marked available, and they have fewer open incidents than their maximum workload. | `TechnicianLoad.canTakeMore` |
| BR-08 | Open means any status before RESOLVED. Resolved incidents no longer count towards workload. | `Status.isOpen` |
| BR-09 | Skill-based assignment never falls back to a technician without the skill. If nobody fits, the incident stays CLASSIFIED and the administrators are warned. | `SkillBasedStrategy`, `AssignmentService.autoAssign` |
| BR-10 | The response SLA is met by acknowledging; the resolution SLA by resolving. | `Incident.responseTarget`, `resolutionTarget` |
| BR-11 | At 75% of the allowed time an incident is AT_RISK and a warning is sent once. After the deadline it is BREACHED, recorded once. Exactly at the deadline is not yet a breach. | `Incident.refreshSla` |
| BR-12 | While an incident is AWAITING_INFO its SLA clock stands still, and the resolution deadline moves back by the time waited. | `AwaitingInfoState`, `Incident.pauseSlaClock/resumeSlaClock` |
| BR-13 | A breach is never forgotten, even if the incident is resolved afterwards. | `Incident.isSlaBreached` |
| BR-14 | A P1 incident that is not acknowledged by its response deadline is escalated to the administrators, once. | `EscalationPolicy` |
| BR-15 | A new incident is a possible duplicate of an open incident on the same service from the last 24 hours if their titles are at least 40% similar. It is linked, not rejected. | `DuplicateDetector` |
| BR-16 | The same person cannot report the same title for the same service while their first report is open. | `IncidentService.refuseIfAlreadyReported` |
| BR-17 | An employee sees only their own incidents. Technicians and administrators can see all. | `User.canView` |
| BR-18 | A resolution can be rated once, from 1 to 5, by the reporter, after they have confirmed the fix. | `Incident.rate`, `IncidentState.allowsRating` |
| BR-19 | Nobody is notified about their own action (except the confirmation that a report was received). | `NotificationListener` |
| BR-20 | Changing the classification is allowed only while the incident is open, by its technician or an administrator. | `Incident.reclassify`, `IncidentService.reclassify` |
| BR-21 | A disabled account cannot sign in and its open sessions end at once. An administrator cannot disable their own account. | `ItsmUserDetails`, `SignedInSessions`, `AdminTechnicianController` |
| BR-22 | An email address belongs to at most one account, whatever the letter case. | `User.normaliseEmail`, unique column |
| BR-23 | When 3 people are affected (the reporter plus those who said "I'm affected too") the impact is at least Medium, at 5 it is High; the priority and deadlines are recalculated. The reporter cannot join their own incident, nobody can join twice, and a finished incident cannot be joined. | `IncidentService.joinAsAffected`, `Incident.addAffectedUser` |
| BR-24 | A probable root cause is a service that at least two services with serious problems (open P1 or P2 incidents from the last 3 hours) depend on, directly or indirectly. Security incidents are left out. Most affected services first, then a service with its own serious incident, then the smallest reach. | `RootCauseAnalyzer` |
| BR-25 | A service may not depend on itself or on a service that already depends on it (no loops). | `ServiceGraph.loopIfDependsOn`, `AdminCatalogueController` |
| BR-26 | The learning classifier chooses a category only when it is at least 50% sure and questions an incident's category only when it is at least 60% sure of another one. | `CategoryClassifier`, `IncidentPages` |
| BR-27 | A predicted finish time needs at least 4 similar finished incidents; otherwise none is shown. | `SlaPredictor` |
| BR-28 | Attachments: PNG, JPEG, GIF, WebP, PDF or plain text, recognised by their content; at most 5 MB each and 10 per incident; added by the reporter, people affected, the assigned technician or an administrator until the incident is closed; removed by the person who added them or an administrator. | `AttachmentService`, `FileKind` |
| BR-29 | An API key works only until it is revoked or expires and only while its owner's account is enabled; it can do exactly what its owner can do; at most 120 requests a minute per key; at most 10 working keys per user. | `ApiTokenService`, `ApiRateLimiter` |
| BR-30 | After 5 wrong passwords for one e-mail address within 15 minutes, sign-in for that address is paused for 15 minutes, even with the right password. | `LoginAttempts`, `LockoutAuthenticationProvider` |
| BR-31 | A webhook message is retried after 30 seconds, 2 minutes, 10 minutes and 1 hour, then given up; a message is never sent for a change that was rolled back. | `WebhookDelivery`, `WebhookListener` |
| BR-32 | The assistant offers knowledge articles only with a match of at least 30%, and offers a known outage only to people who have not reported or joined it. | `assistant/Step` |

# 4. System architecture

Quell is one Spring Boot application organised in layers, with two doors: web pages for people and a
REST API for programs. Everything that happens to an incident is announced as an event, and the side
effects (audit trail, notifications, live updates, learning, webhooks) are listeners.

::: {.wide}
![Architecture: the components and how they interact](../diagrams/architecture.png){width=100%}
:::


### Principles

- **Dependencies point downwards.** Controllers call services; services use the domain model and the
  repositories. The domain model knows nothing about HTTP, pages or the API.
- **The rules live in the domain model.** An `Incident` itself refuses an illegal move, so neither a
  page, nor the assistant, nor the REST API can bypass a rule: all three call the same services.
- **One transaction per user action.** Reporting saves the incident, its priority, its assignment, its
  audit entries and its webhook messages together, or nothing at all.
- **Side effects are decoupled by events** (Observer). A service publishes "this happened"; it does not
  know who listens. Live updates, the learning classifier and webhooks were all added later as new
  listeners without touching the services that change incidents.
- **Nothing leaves before the commit.** Notifications, live messages and webhooks are sent after the
  transaction commits, so nobody is told about a change that was rolled back, and a slow receiver
  cannot slow down or break the change itself.
- **Two security chains.** People use pages with a session cookie and CSRF tokens. Programs use the
  API with a key in every request, no session and no cookies; a separate filter chain applies different,
  appropriate rules to each.
- **Server-side rendering with progressive enhancement.** The server renders complete pages; small
  plain-JavaScript files add live updates, the chat, drag-and-drop and the command palette. Every page
  still works without JavaScript.

### Deployment

| Where | How | Database |
|---|---|---|
| A laptop or desktop | One-click installer (Windows `.exe`, macOS `.dmg`, Linux `.deb`) with Java included, built by `jpackage`; the app starts in the background, opens the browser and adds a tray icon. | H2 file in the user's data folder |
| A server or the cloud | `docker compose up` starts Quell and PostgreSQL 17; configured with environment variables, ready for a reverse proxy that provides HTTPS. | PostgreSQL |
| A developer's machine | `./mvnw spring-boot:run` | H2 file in `./data` |
| Phones | Open the address in the browser and install it as an app (PWA). | (the server's) |

Every change pushed to the repository is built, tested and packaged for all three operating systems by
GitHub Actions, and the installers are published on the releases page.

# 5. UML diagrams

All diagrams are drawn from the real code with PlantUML; their sources are in `docs/diagrams/`.

## 5.1 Use case diagram

Four human actors (visitor, employee, technician, administrator) and three system actors (the
scheduler that checks SLAs every minute, external tools using the REST API, and webhook receivers).

![Use cases](../diagrams/use-case.png){width=92%}

## 5.2 Class diagram 1: the domain model

Classes with their attributes and methods, inheritance (`Employee`, `Technician` and `Administrator`
extend the abstract `User`), associations (an incident has a reporter, an assignee, a service and a
category; technicians have skills), composition (an incident owns its priority assessment and its audit
history) and the enumerations.

![Domain model](../diagrams/class-diagram.png){width=100%}

::: {.wide}
## 5.3 Class diagram 2: interfaces and design patterns

The interfaces and their implementations: `IncidentState` with nine state classes (State),
`AssignmentStrategy` with three strategies and a template method (Strategy, Template Method),
`NotificationChannel` with three channels and their factory (Strategy, Factory), `IncidentEventListener`
with its listeners (Observer), `PriorityCalculator`, and the exception hierarchy.

![Interfaces and design patterns](../diagrams/class-diagram-patterns.png){width=100%}
:::

::: {.wide}
## 5.4 Class diagram 3: intelligence, service map and assistant

The two generic machine-learning classes (`SimilarityIndex<T>` for TF-IDF similarity,
`NaiveBayesClassifier<L>`), the features built on them, the service graph and root-cause analysis, and
the reporting assistant, whose conversation steps form a second use of the State pattern.

![Intelligence and assistant](../diagrams/class-diagram-intelligence.png){width=100%}
:::

::: {.wide}
## 5.5 Class diagram 4: integrations, security and files

Webhooks (a listener that writes to an outbox, a dispatcher, a sender that signs messages, and the
payload formats), API keys and the filter that checks them (inheriting from Spring's
`OncePerRequestFilter`), the sign-in lockout (a Decorator around Spring's password check) and
attachments (composition: an attachment owns its content).

![Integrations, security and files](../diagrams/class-diagram-integrations.png){width=100%}
:::

## 5.6 State machine of an incident

The nine statuses and every legal move, with the role that may make it.

![Incident lifecycle](../diagrams/state-machine.png){width=80%}

::: {.wide}
## 5.7 Sequence diagram: reporting an incident

What happens, in order and in one transaction, when an employee reports an incident.

![Reporting an incident](../diagrams/sequence-report-incident.png){width=100%}
:::

# 6. Object-oriented design

## 6.1 OOP concepts

| Concept | Where | In plain words |
|---|---|---|
| **Abstraction** | `abstract class User`; interfaces `IncidentState`, `AssignmentStrategy`, `PriorityCalculator`, `NotificationChannel`, `IncidentEventListener`; `abstract class ItsmException` | Code talks to "a user", "a strategy", "a channel" without knowing which one. `AssignmentService` calls `strategy.choose(...)` and never mentions skill-based or round robin. |
| **Encapsulation** | `Incident` | The fields are private and there is no `setStatus`. The status changes only through `transitionTo`, `prioritise` and `assignTo`, which check the rules first. The SLA arithmetic is inside `refreshSla`. |
| **Inheritance** | `Employee`, `Technician`, `Administrator` extend `User`; three strategies extend `AbstractAssignmentStrategy`; eleven exceptions extend `ItsmException` | A technician *is a* user with skills, a maximum workload and an availability flag. In the database all users share one table (`users`) with a `role` column: single-table inheritance. |
| **Polymorphism** | `User.getRole()`, `canView()`, `canWorkOn()`; `IncidentState` methods; `AssignmentStrategy.choose()` | The same call gives a different result depending on the object. `user.canWorkOn(incident)` is "is it mine?" for an employee, "is it assigned to me?" for a technician and "yes" for an administrator. There is no `if (role == ...)` chain. |
| **Association** | `Incident` → `Employee` (reporter), `Technician` (assignee), `ITService`, `Category`; `Technician` ↔ `Category` (skills, many-to-many); `Notification` → `User` | Objects that know each other but live independently. Deleting an incident does not delete the service or the technician. |
| **Composition** | `Incident` owns `PriorityAssessment` (embedded in its row) and its `AuditEntry` history; `Category` owns its keywords | The part has no meaning without the whole. An audit entry without its incident is nothing. |
| **Aggregation** | `IncidentEventPublisher` has its listeners; `AssignmentService` has the strategies | The whole uses parts that exist on their own. |
| **Interfaces** | The five listed under Abstraction | Each is a contract with several implementations that can be exchanged. |
| **Enums with behaviour** | `Status` (`isOpen`, `openStatuses`), `Impact`/`Urgency` (weight, label), `Role` (authority), `NotificationType` (`isEmailed`) | Fixed sets of values that carry their own data and rules instead of being bare strings. |
| **Immutable value objects** | Records `PriorityAssessment`, `SlaTarget`, `SlaSignal`, `Transition`, `IncidentEvent`, `NotificationMessage`, `TechnicianLoad`, `DuplicateMatch`, `Reports` | Small objects that are created once and never change, so they are safe to pass around. |
| **Custom exceptions** | `com.itsm.error` | One class per kind of broken rule (section 9). |
| **Generics** | `SimilarityIndex<T>`, `NaiveBayesClassifier<L>`, `PageView<T>`, `Match<T>`, `Prediction<L>` | The machine-learning algorithms are written once for any kind of item or label: the same `SimilarityIndex` ranks knowledge articles and past incidents; the classifier would label anything, here categories. |
| **Enums with abstract methods** | `Step` (the assistant's conversation steps), `FileKind`, `WebhookEvent` | Each constant of `Step` implements its own `enter` and `handle`: a compact, type-safe State pattern. |
| **Inheritance from a framework class** | `ApiTokenFilter extends OncePerRequestFilter` | The framework guarantees the filter runs exactly once per request; the subclass adds only the key check. |
| **Wrapping instead of inheriting** | `LockoutAuthenticationProvider` holds an `AuthenticationProvider` | Adds the lockout rule to Spring's password check without changing or subclassing it (Decorator). |

The class diagrams in section 5 show all of these.

## 6.2 Design patterns

| Pattern | Classes | Why here |
|---|---|---|
| **State** | Interface `IncidentState`; one class per status: `NewState`, `ClassifiedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `AwaitingInfoState`, `ResolvedState`, `VerifiedState`, `ClosedState`; registry `IncidentStates`; `Incident.transitionTo` | What an incident may do depends entirely on its status. Without the pattern every method would start with a long `switch (status)`. With it, each status is a small class that lists its own legal moves, who may make them, and what happens on entering and leaving (for example `AwaitingInfoState` pauses the SLA clock). Adding a status means adding a class. |
| **Strategy** (assignment) | Interface `AssignmentStrategy`; `SkillBasedStrategy`, `LeastLoadedStrategy`, `RoundRobinStrategy`; context `AssignmentService` | There are several reasonable ways to choose a technician and the administrator must be able to switch at runtime. Each rule is one class behind one interface. `AssignmentService` holds a list of all strategies and uses the one named in the settings. A fourth rule is a new class and nothing else. |
| **Strategy** (priority) | Interface `PriorityCalculator`; `WeightedPriorityCalculator` | The brief leaves the priority rule to the team, so it is likely to change. `IncidentService` knows only the interface. |
| **Strategy** (delivery) | Interface `NotificationChannel`; `InAppChannel`, `LiveChannel`, `EmailChannel` | "Deliver this message" has several implementations. SMS or chat would be another class. `LiveChannel` (the bell count pushed to open pages) was added later as exactly that: one new class. |
| **Template Method** | `AbstractAssignmentStrategy.choose()` (final) calls the abstract `pick()` | All three strategies share the same first step (leave out technicians who are disabled, unavailable or full) and the same failure (throw `TechnicianUnavailableException`). The parent class fixes that skeleton once; each subclass fills in only the choice itself. No strategy can forget the workload rule. |
| **Observer** | `IncidentEventPublisher` (subject); interface `IncidentEventListener`; `AuditTrailListener`, `NotificationListener`, `LiveUpdateListener`; `IncidentEvent` (the message) | Many things must happen when an incident changes: write the audit trail, notify people. The service that changes the incident should not know about them. It publishes an event; every listener reacts. A new reaction is a new listener class. Live updates proved it: `LiveUpdateListener` was added after version 1.0 and not one line of `IncidentService`, `AssignmentService` or `SlaService` changed. |
| **Factory** | `IncidentFactory.create(...)` | Creating a valid incident takes several steps: check that nothing is missing, build it in status NEW, save it so that it gets its number and code. Putting this in one place means an incident cannot be created half-finished anywhere else. |
| **Factory** | `NotificationChannelFactory.channelsFor(type)` | Which channels a message goes through depends on its type (everything goes in-app; only some types are emailed). The dispatcher asks the factory and does not decide itself. |
| **Repository** | `IncidentRepository`, `UserRepository`, … (Spring Data JPA) | The rest of the code asks for objects ("open incidents of this technician") and never writes SQL. The database could be replaced without touching the services. |
| **Specification** | `IncidentSpecifications.matching(criteria)` | The search has nine optional fields (code, status, priority, category, service, technician, SLA state, from date, to date). Each is a small condition, and the ones that are filled in are combined, instead of writing a query for each of the 512 combinations. |
| **Dependency Injection** | Every `@Service`, `@Component`, `@Controller` constructor | Classes receive what they need through their constructor instead of creating it. This is what lets a test hand `NotificationDispatcher` a fake transaction, or make a channel fail on purpose. |
| **MVC** | Controllers, Thymeleaf templates, domain model and services | Separates what the user sees from what the system does. |
| **State** (conversation) | Enum `Step` with nine constants, each implementing `enter` and `handle`; context `Conversation`; driver `ReportingAssistant` | A chat is a sequence of steps whose meaning depends on where the conversation is ("yes" means a different thing after "Is it about the Payment Application?" than after "Shall I report it?"). Each step knows what to ask and what to do with the answer. |
| **Observer** (more listeners) | `CategoryClassifier` and `WebhookListener` also implement `IncidentEventListener` | The classifier marks itself out of date when incidents change; webhooks queue messages. Neither needed a change to the incident services. |
| **Decorator** | `LockoutAuthenticationProvider` wraps Spring's `DaoAuthenticationProvider` | Adds "pause after five wrong passwords" around the existing password check, which stays untouched. |
| **Transactional outbox** | `WebhookListener` writes `WebhookDelivery` rows in the change's transaction; `WebhookDispatcher` sends after the commit and retries | A message is never sent for a change that was rolled back and never lost if a receiver is down. A standard pattern of event-driven systems. |
| **Chain of filters** | Two `SecurityFilterChain`s; `ApiTokenFilter` | Each request passes a chain of small checks; the API chain and the web chain differ only in their links. |
| **Facade** | `ReportingAssistant`, `StatusService` | One simple entry point in front of several services (detection, triage, classification, knowledge, incidents; graph, health, root cause). |

Two patterns that were considered and left out, with the reason:

- **Singleton.** Spring already creates exactly one of each service. Writing the pattern by hand would
  add code and make testing harder.
- **Chain of Responsibility** for escalation. The brief describes one escalation step (to the
  administrators). A chain of one link would be decoration.

# 7. The priority algorithm

## The rule

```
score    = impact weight × urgency weight × service criticality      (each from 1 to 3)

18 – 27  →  P1  Critical
 9 – 17  →  P2  High
 4 –  8  →  P3  Medium
 1 –  3  →  P4  Low
```

| Input | 1 | 2 | 3 | Who sets it |
|---|---|---|---|---|
| Impact | Low: one or two people | Medium: a team or a department | High: many people or customers | The reporter |
| Urgency | Low: it can wait | Medium: work is slowed down | High: work has stopped | The reporter |
| Service criticality | Low | Medium | High | The administrator, once per service |

Example from the brief: the payment application (criticality 3) is unavailable (impact High = 3) and
transactions have stopped (urgency High = 3). Score 3 × 3 × 3 = 27, priority P1, respond within
5 minutes, resolve within 30.

The calculation is stored with the incident (`PriorityAssessment`: the three numbers, the score and
the priority) and shown on the incident page under "How the priority was worked out", so nobody has
to trust a black box.

## Why multiply and not add

With three factors from 1 to 3, only ten scores are possible: 1, 2, 3, 4, 6, 8, 9, 12, 18 and 27.

| Priority | Scores | What it takes | Combinations (of 27) |
|---|---|---|---:|
| P1 | 18, 27 | Two factors at the maximum and the third at least Medium | 4 |
| P2 | 9, 12 | Two at the maximum and one Low, or one at the maximum and two Medium | 6 |
| P3 | 4, 6, 8 | Mostly Medium, or one High pulled down by a Low | 10 |
| P4 | 1, 2, 3 | At least two factors Low | 7 |

- **A low factor pulls the result down.** "Work has stopped for many people" on a test system
  (3 × 3 × 1 = 9) is P2, not P1. With addition (3 + 3 + 1 = 7 of 9) it would rank almost as high as
  a real emergency. Multiplying says: all three must be serious for the incident to be critical.
- **Critical stays rare.** Only 4 of the 27 combinations give P1. If a third of all incidents were
  "critical", the word would mean nothing and the 5-minute response could not be kept.
- **The reporter cannot decide the priority alone.** Impact and urgency come from the reporter, who
  tends to overstate. The service criticality comes from the administrator. Claiming High and High
  for a low-criticality service gives 9: P2.
- **The gaps make the bands robust.** No score lies between 12 and 18. The boundary between P2 and P1
  sits in that gap, so no single-step change of one input lands just across it by accident. The
  boundaries (17 is P2, 18 is P1) are tested directly: TC-02 and TC-03.
- **Simple enough to explain to a user.** Three numbers and one multiplication, shown on the page.

## Limits and how it could grow

- The three factors have equal weight. A weighted version would be another class implementing
  `PriorityCalculator`; nothing else would change (Strategy pattern).
- The number of people affected raises the impact automatically: 3 people make it at least Medium, 5 make it High (BR-23).
- The priority is calculated when the incident is reported and again when a technician or
  administrator corrects the classification. It does not rise by itself with age; instead the SLA
  monitor and the escalation rule deal with incidents that wait too long.

# 8. Intelligent features and innovations

## 8.1 The innovation challenges of the brief

| Innovation challenge in the brief | In Quell | Where |
|---|---|---|
| AI-assisted incident classification | Keyword rules first; then a **multinomial naive Bayes classifier** trained on the organisation's own incidents and articles, with the deciding words shown. It also questions incidents that seem filed under the wrong category. | `CategoryClassifier`, `NaiveBayesClassifier<L>` |
| AI-based resolution recommendation | For every incident: matching **knowledge articles** and **fixes of similar past incidents**, ranked by TF-IDF cosine similarity, with the shared words, how often a fix worked and how it was rated. One click copies a fix into the resolution. | `ResolutionRecommender`, `SimilarityIndex<T>` |
| Incident similarity detection | Possible duplicates linked automatically (Jaccard similarity of significant words, same service, last 24 hours); the same person reporting the same thing twice is refused. | `DuplicateDetector` |
| Automated root-cause analysis | When several services have serious incidents at the same time, the service they **all depend on** is named as the probable root cause, on the status page and the administrator's overview. | `RootCauseAnalyzer`, `ServiceGraph` |
| Predictive SLA violation detection | A **predicted finish time and breach risk** for each open incident, learned from similar finished incidents and adjusted for the technician's usual speed; plus the 75% warning and the "most likely to breach next" list. | `SlaPredictor`, `SlaRiskRanker` |
| Service dependency graph | Services depend on services; loops are refused; a drawn **dependency map** shows trouble flowing upwards. | `ServiceGraph`, `ServiceMap` |
| Real-time notifications | The bell updates at once in every open tab (server-sent events). | `LiveUpdates`, `LiveChannel` |
| Mobile push notifications | **Desktop and mobile alerts** for new notifications while Quell is in the background (Notification API, through the service worker on phones). | `live.js`, `sw.js` |
| Voice-based incident reporting | A microphone button on the report form and in the assistant (speech recognition built into the browser). | `voice-input.js` |
| Chatbot-based incident reporting | The **reporting assistant**: a conversation that recognises the service, offers a known outage or articles, asks impact and urgency in plain words and files a complete incident. | `assistant/` |
| Cloud deployment | **Container image** and docker-compose with **PostgreSQL**, configured by environment variables; verified on PostgreSQL. | `Dockerfile`, `application-prod.properties` |
| Event-driven architecture | Every change is an event; five listeners react; webhooks use a transactional outbox. | `event/`, `webhook/` |
| Real-time operational dashboard | Dashboards, queues and lists update in place when anything changes. | `LiveUpdateListener`, `live.js` |
| Analytics and visualization | Reports (by status, priority, category, SLA compliance, resolution time, per technician), the health strip, the dependency map. | `ReportService`, `StatusService` |
| Automated incident escalation | A P1 nobody acknowledges in time is escalated to the administrators. | `EscalationPolicy` |
| Microservice architecture | Not used, on purpose (section 11, question 5); the event-driven design keeps the option open. | |

## 8.2 Machine learning without an external service

All learning features are implemented in the project itself, in two small generic classes, and train
on data the installation already has. Nothing is sent to an outside AI service: the features work
offline, answer in milliseconds, and keep incident text private. Each decision is explained on the
page with the words that led to it.

**Text into numbers.** A text is cut into lower-case words; very common words ("the", "is", "please")
are dropped (`Words`).

**TF-IDF cosine similarity** (`SimilarityIndex<T>`). Each document becomes a vector with one weight per
word: term frequency `1 + ln(count)` times inverse document frequency `ln((1 + N) / (1 + df)) + 1`, where
N is the number of documents and df the number that contain the word, so rare, telling words count
more than common ones. Vectors are normalised to length 1, and the similarity of two texts is the dot
product of their vectors (1 = the same words in the same proportions, 0 = nothing in common). Used for
suggested solutions and for the assistant's article suggestions.

**Multinomial naive Bayes** (`NaiveBayesClassifier<L>`). For each category c and word w the classifier
learns P(w | c) from examples, with Laplace smoothing `(count(w, c) + 1) / (words(c) + |vocabulary|)`
so an unseen word never makes a probability zero. For a new text it computes
`log P(c) + Σ log P(w | c)` for every category, turns the scores into probabilities that add up to 1,
and reports the best one with the three words that pushed hardest towards it. Training data: the
categories' own names and keywords (counted twice), every incident with a category, and every
knowledge article. It retrains itself when incidents change, so a technician's correction improves the
next suggestion. A text with no known word gets no guess at all.

**Predicted finish time** (`SlaPredictor`). From finished incidents of the same category and priority
(or, if there are too few, the same priority) it takes the median resolution time, adjusts it by the
assigned technician's speed relative to their colleagues (clamped between 0.6 and 1.6), and computes
the share of similar incidents that took longer than the time this one still has before its deadline:
the breach risk. At least 4 similar incidents are needed; otherwise no forecast is shown.

## 8.3 The service map and root-cause analysis

An administrator records which services each service needs (for example, the Payment Application needs
the Database Cluster, the Authentication Service and the Corporate Network). `ServiceGraph` answers
"what does this service need, directly or indirectly?" (upstream) and "what needs it?" (downstream),
refuses loops, and arranges services in layers for drawing.

`RootCauseAnalyzer` looks at serious incidents (open, P1 or P2, from the last 3 hours, not security
incidents). For every service it asks: how many of the services with serious problems depend on it
(including itself)? A service that two or more depend on is a suspect; the one shared by the most
wins, a suspect with its own serious incident is "confirmed", and among equals the one with the
smallest reach is the most specific answer. The status page then says, for example: *"Corporate
Network: 2 services that depend on it have serious incidents at the same time (Corporate Network,
Payment Application), including Corporate Network itself."*

## 8.4 The reporting assistant

A chat for people who do not know what "impact" or "urgency" means. It is rule-based on purpose:
predictable, instant, testable and private. The conversation is a State machine (`Step`): **describe**
the problem → confirm the **service** recognised from the words (`ServiceDetector`) → if a serious open
incident exists on that service or one it depends on, offer to **join** it → offer **knowledge articles**
with a match of 30% or more → **who** is affected and how much it **stops work**, in plain words, with
the smart-triage guess (`TriageAdvisor`) shown first → the **category** only if nothing could be
worked out → **confirm** → the incident is created by the same `IncidentService` as the form, so every
rule applies. Buttons, typed answers ("it's the vpn", "only me", "I can't work") and speech all work.
The conversation lives in the session, and the page works without JavaScript too.

## 8.5 Integrations: REST API and webhooks

**REST API** (`/api/v1`). Incidents (list with filters, search and paging; read; report; notes; status
changes; timeline), service status, services, categories and the knowledge base. Each request carries
a personal API key (`Authorization: Bearer qk_…`); the key acts with exactly its owner's permissions,
so every rule of the web app applies. Keys are 40 random characters, stored only as a SHA-256 hash,
shown once, revocable and with an expiry. Requests are limited to 120 a minute per key, every answer
says how many are left, and errors use the standard *problem details* format (RFC 9457). Interactive
documentation (OpenAPI 3.1 / Swagger UI) is at `/api/docs`.

**Webhooks.** Administrators send chosen events (reported, assigned, status changed, resolved, SLA
breached, escalated; optionally only P1 and P2) to Slack, Microsoft Teams (Adaptive Cards) or any app
(JSON signed with HMAC-SHA256 so the receiver can check its origin). Messages are queued in the same
transaction as the change (transactional outbox), built and sent right after the commit, retried with
growing pauses, and shown in a delivery log with a test button.

## 8.6 Everything live, everywhere

- **Live updates** (server-sent events): dashboards, queues, lists and the bell change in place when
  anything changes. A message carries only "something changed"; the page fetches its own new content
  through the normal access-checked pages.
- **Desktop and mobile alerts** for new notifications while the app is in the background.
- **Installable app** (manifest and service worker, with an offline page), **native installers** with
  Java included, a **tray icon** and a single-instance check on the desktop.

# 9. Exception handling

All the application's own exceptions extend one abstract class, `ItsmException`, and each carries a
message written for the user.

| Case in the brief | Exception | What the user sees |
|---|---|---|
| Invalid login | Spring Security's `BadCredentialsException` | Back on the sign-in page: "Wrong email or password." |
| Unauthorized operation | `UnauthorizedOperationException`; Spring's `AccessDeniedException` for a forbidden address | The 403 page with the reason. |
| Invalid incident / not found | `IncidentNotFoundException` (extends `NotFoundException`) | The 404 page. |
| Invalid state transition | `InvalidStateTransitionException` | Back on the incident page with "INC-0007 cannot go from New to Closed". Nothing changed. |
| Technician unavailable | `TechnicianUnavailableException` | Message on the incident page. During automatic assignment: the incident waits and administrators are warned. |
| Technician workload exceeded | `WorkloadExceededException` | "… already has 3 of 3 open incidents." |
| Service not found | `ServiceNotFoundException` (extends `NotFoundException`) | The 404 page. |
| Missing SLA configuration | `MissingSlaConfigurationException` | Message on the report form naming the priority; nothing saved. |
| Duplicate incident | `DuplicateIncidentException` | Message on the report form with the code of the first report. |
| Database failure | Spring's `DataAccessException` | A page with status 503: "The database could not be reached just now." The transaction is rolled back. |
| Network failure | Any exception inside a notification channel | Nothing: the incident is saved, the other channel still delivers, the failure is logged. |
| Invalid user input | Bean Validation on the form objects; `InvalidInputException`; `EmailAlreadyRegisteredException` | The form again with a message at each wrong field, what was typed still there. |
| A file that may not be attached | `InvalidInputException` from `AttachmentService.check` (empty, too big, wrong kind) | Message on the incident page or at the screenshot field of the report form; nothing saved. |
| An upload bigger than the server limit | Spring's `MaxUploadSizeExceededException` | Back on the page: "That file is too big. Files can be at most 5.0 MB." |
| Any error in the REST API | `ApiExceptionHandler` | JSON *problem details* with the right status: 400 invalid, 401 no or bad key, 403 not allowed, 404 not found, 409 not possible in the current state, 429 too many requests, 500 with no internal details. |
| A webhook receiver that is down | Caught in `WebhookSender` | Nothing for the user; the delivery is retried and its error shown in the delivery log. |
| A broken rule inside the assistant | Any `ItsmException` | Said politely in the chat ("You have already reported this: INC-0012 is still open…"); the conversation stays at the same step. |

One class, `ErrorHandler` (`@ControllerAdvice`), turns exceptions into pages for every controller, so
no controller has its own error-page code and no stack trace reaches a browser. The REST API has its own handler, `ApiExceptionHandler`, which answers in JSON.

# 10. Security

| Threat or requirement | Measure | Where |
|---|---|---|
| **Authentication** | Form login with e-mail and password; the session ID changes at sign-in (no session fixation); sign-out ends the session. | `SecurityConfig` |
| **Password guessing** | After 5 wrong passwords for one address in 15 minutes, sign-in for it is paused for 15 minutes, even with the right password. Unknown addresses are paused the same way, so the answer never reveals which accounts exist. | `LoginAttempts`, `LockoutAuthenticationProvider` |
| **Password storage** | BCrypt hashes only (salted, deliberately slow); minimum length 8. | `PasswordEncoder` |
| **Role-based authorisation** | Every address checked on the server by role (`/employee/**`, `/technician/**`, `/admin/**`), and every object checked again (`canView`, `canWorkOn`): a technician cannot change another technician's incident, an employee cannot open someone else's, even with the right address or through the API. | `SecurityConfig`, `User` subclasses |
| **API access** | Personal API keys, 238 bits of randomness, stored only as SHA-256 hashes, shown once, revocable, expiring, and dead once the owner is disabled. Stateless: no cookies, so no CSRF risk. 120 requests a minute per key. | `api/` |
| **Cross-site request forgery** | Every state-changing form carries a CSRF token. | Spring Security |
| **Cross-site scripting** | All user text is escaped by Thymeleaf; the chat renders bold text by splitting, never as HTML. A strict **Content Security Policy** allows only the site's own scripts (there is no inline script or event handler anywhere), so even injected markup could not run code. | Templates, `SecurityConfig` |
| **Malicious files** | Attachments are recognised by their first bytes, not by name or browser claim; SVG and HTML are refused; files are served with `nosniff`, a sandboxing CSP, and as downloads unless they are images; names are cleaned. | `FileKind`, `AttachmentController` |
| **SQL injection** | All queries through JPA with bound parameters; searches with the Criteria API. | `repository/` |
| **Clickjacking, sniffing, leaks** | `X-Frame-Options: DENY` and `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, a `Permissions-Policy` that switches off camera, location and payment. | `SecurityConfig` |
| **Webhook authenticity** | Messages to apps are signed (HMAC-SHA256 of timestamp and body) so receivers can reject forgeries and replays; addresses must be http(s) without credentials. | `WebhookSender`, `WebhookService` |
| **Account control** | A disabled account cannot sign in, its open sessions end at once, and its API keys stop working. | `SignedInSessions` |
| **No information leaks** | One sign-in error message for every failure; error pages and API errors never show stack traces or SQL. | `ErrorHandler`, `ApiExceptionHandler` |
| **Accountability** | Every change to an incident is in an audit trail that cannot be edited. | `AuditTrailListener` |
| **Production settings** | `prod` profile: secrets from environment variables, PostgreSQL with its own account, trusted proxy headers for HTTPS, no database console. | `application-prod.properties` |

Not implemented, and what a production version would add: HTTPS inside the application (provided by
the reverse proxy in a server installation), password reset by e-mail and address verification,
multi-factor sign-in, single sign-on (OpenID Connect, which Spring Security supports), an audit log of
sign-ins and setting changes, and a data retention policy.

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

## 11.2 The nine questions of the brief

### 1. Why did you choose this programming language?

**Java 25.** The course is about object-oriented programming, and Java makes the OOP concepts explicit
in the language: classes, abstract classes, interfaces, access modifiers, checked types. A reader can
see in the source that `Technician extends User` and that `SkillBasedStrategy implements
AssignmentStrategy`; nothing is implied by convention. Static typing means the compiler finds a
whole class of mistakes before the program runs, which matters in a team where one person changes a
class another person uses. Modern Java also removed much of the old verbosity: records for small
immutable values, `switch` expressions, pattern matching, text blocks. Finally, Java has the most
mature ecosystem for exactly this kind of enterprise system (security, persistence, validation,
scheduling, testing), so we wrote business rules instead of infrastructure.

### 2. Why did you choose this frontend technology?

**Server-rendered HTML with Thymeleaf and Bootstrap 5.** The application is forms, tables and detail
pages for staff inside one organisation. For that, a page rendered on the server is the simplest
thing that works: one language, one project, one build, one deployment. Security is simpler too,
because the server decides what is on the page (a button a user may not press is never sent);
programs get their own REST API with its own rules (section 8.5). Thymeleaf templates are valid HTML and escape user
text by default. Bootstrap gives a consistent, responsive look without a designer, so the pages work
on a phone. Plain JavaScript, without a framework, is used only where it helps: nine small files add live updates,
the chat, voice input, drag-and-drop uploads, the command palette and the installable app. Pages still
work with JavaScript switched off.

### 3. Why did you choose this backend technology?

**Spring Boot 4.1.** It provides, already tested and working together, every piece the brief asks
for: web layer, authentication and role-based authorization (Spring Security), persistence (Spring
Data JPA), input validation, scheduled jobs (the SLA monitor), transactions and a test framework. Its
dependency injection is what makes the design patterns practical: `AssignmentService` receives a
`List<AssignmentStrategy>` containing every strategy that exists, so adding one needs no
registration code. Transactions are declared with one annotation, which gives the "all or nothing"
behaviour of each user action. It starts with one command and needs no application server.

### 4. Why did you select this database?

**H2, a relational database, in file mode.** The data is relational by nature: an incident belongs to
a reporter, a service, a category and a technician; a technician has many skills; an audit entry
belongs to an incident. Foreign keys, unique constraints and transactions keep that consistent, which
a document store would leave to the application. H2 in particular because it runs inside the
application: an evaluator can clone the repository and run it with nothing to install or configure,
and the data still survives a restart because it is stored in a file; the desktop installers use it
too. Tests use a fresh in-memory H2, so they are fast and independent. Because all access goes through
JPA, server installations run the same code on **PostgreSQL** (the `prod` profile and docker-compose),
which we verified end to end: only the driver and connection settings differ.

### 5. Why did you choose this architecture?

**A layered monolith (Option C of the brief: a full-stack application in one deployable unit).** The
layers (security, presentation, services, domain, persistence) separate concerns so that each can be
understood, tested and changed on its own. One process fits the problem: the central operation,
reporting an incident, must save the incident, its priority, its assignment and its audit entries
*together*. In one process with one database that is a single transaction. Spread over
microservices it would need distributed transactions or compensation logic, plus service discovery,
network failure handling and several deployments, for an application used by one organisation. That
complexity would have bought nothing and would have taken time from the OOP design, which is what the
project is assessed on. The parts that would make sense as separate services later (notifications,
reporting) are already decoupled through events and interfaces, so they could be moved out without
redesign.

### 6. What alternatives did you consider?

| Area | Alternative | Why not chosen |
|---|---|---|
| Language | Python (Django / FastAPI) | Quick to write, but dynamic typing hides interface and type errors until runtime, and the OOP concepts are less visible in the code. |
| Language | JavaScript / TypeScript (Node.js) | One language for browser and server, but the ecosystem is assembled from many small libraries, and classical OOP modelling is less natural than in Java. |
| Language | C# / .NET | Comparable to Java in every respect that matters here. Java was chosen because the course is taught in it and the team can review each other's code. |
| Language | Kotlin | Runs on the same platform with shorter syntax, but adds a second language to learn for no benefit to the design. |
| Frontend | React or Angular single-page application with a REST API | Better for highly interactive screens. Here it would mean a second project, a second build, an API to design and secure with tokens, and state kept in two places, for pages that are mostly forms and tables. |
| Frontend | Mobile app (Flutter, Android) | Technicians at a desk and administrators with dashboards are better served by a browser; responsive pages cover the phone. |
| Backend | Jakarta EE on an application server | Same concepts, but needs a server to install and has slower tooling. |
| Backend | Quarkus or Micronaut | Faster start-up, which matters for serverless, not here. Smaller community and less documentation. |
| Database | PostgreSQL / MySQL | PostgreSQL is used for server installations (prod profile, Docker). H2 stays the default so the desktop installer and an evaluator need nothing installed. |
| Database | MongoDB | The data has many relationships and needs transactions across them. |
| Database | SQLite | Embedded like H2, but weaker support in Hibernate and for concurrent writes. |
| Architecture | Microservices | See question 5. |
| Architecture | Serverless functions | The SLA monitor is a job that runs every minute and the application keeps sessions; neither fits short-lived functions well. |
| Persistence | Plain JDBC with hand-written SQL | Full control, but the mapping between rows and objects (inheritance, associations) would be written by hand, and that is exactly what JPA does. |

### 7. What are the advantages and limitations of your technology stack?

**Advantages**

- One language, one project, one command to run. Nothing to install except Java.
- Strong typing and a mature, well-documented ecosystem with long-term support.
- Security, transactions, validation and scheduling come from the framework, already tested.
- OOP concepts map directly onto language features (question 8).
- Excellent testability: 617 tests run in about four minutes with no external services.
- The database can be exchanged without code changes.
- Pages work without JavaScript, which makes them robust and accessible.

**Limitations**

- H2 is not meant for many simultaneous users or large data; server installations use PostgreSQL.
- Server-rendered pages reload on each action. Live updates, the chat and drag-and-drop uploads soften
  this with small scripts, but a rich editor or offline editing would need more client-side code.
- Spring Boot uses more memory and starts more slowly (a few seconds) than lightweight frameworks.
- The framework does much "by convention". That saves code but means a newcomer has to learn what
  happens behind annotations such as `@Transactional`.
- JPA hides SQL, which is convenient until a query is slow; then the generated SQL has to be
  understood anyway.
- Sessions live in the memory of one server, so running several servers needs an extra component
  (section 12).
- Java is more verbose than Python or Kotlin for the same logic.

### 8. How does your technology support object-oriented design?

| OOP idea | Language or framework feature | In this project |
|---|---|---|
| Abstraction | `abstract class`, `interface` | `User`, `ItsmException`; `IncidentState`, `AssignmentStrategy`, `PriorityCalculator`, `NotificationChannel`, `IncidentEventListener` |
| Encapsulation | `private` fields, no setters where a rule applies | `Incident` exposes `transitionTo`, not `setStatus` |
| Inheritance | `extends`; JPA `@Inheritance(SINGLE_TABLE)` maps a class hierarchy to one table | `Employee`, `Technician`, `Administrator` |
| Polymorphism | Method overriding, interface implementations chosen at runtime | `user.canWorkOn(incident)`, `strategy.choose(...)`, `state.transitions()` |
| Association | Object references; JPA `@ManyToOne`, `@ManyToMany` | `Incident` → reporter, service, category, assignee; technician skills |
| Composition | JPA `@Embedded`, `@ElementCollection` | `PriorityAssessment` inside `Incident`; keywords inside `Category` |
| Immutability | `record` | Value objects such as `SlaTarget`, `IncidentEvent` |
| Programming to interfaces | Spring dependency injection supplies the implementation | A service asks for `PriorityCalculator` and receives `WeightedPriorityCalculator`; asks for `List<IncidentEventListener>` and receives all of them |
| Exceptions as a class hierarchy | `extends RuntimeException`, `catch` by type | `ItsmException` and its eleven subclasses |

The important point is JPA: it stores *objects*. The domain model is written as ordinary classes with
behaviour (an `Incident` that refuses illegal moves), not as rows with getters and a separate layer of
procedures. The framework adapts to the object model instead of the other way round.

### 9. How would your system evolve if the number of users increased significantly?

Short answer: the database first, then pagination and queries, then several servers, then
asynchronous notifications. No change to the domain model or the patterns is needed. The full answer
is section 11.3.


## 11.3 How the system would scale

Today: one process, one embedded database, comfortable for one organisation (hundreds of users,
tens of thousands of incidents). The steps below are in the order they would become necessary.

| Step | Limit that is reached | Change | Effort |
|---|---|---|---|
| 1 | H2 handles few simultaneous writers and is a single file. | **Done:** the `prod` profile runs on **PostgreSQL**; only the driver and connection settings change, because all access is through JPA. Next: Replace "Hibernate creates the tables" with versioned migration scripts (Flyway). Tune the connection pool. | Small |
| 2 | Lists load every matching incident. | **Pagination** on the incident lists, search results, audit log and notifications (Spring Data supports it directly). **Indexes** on `status`, `assignee_id`, `reporter_id`, `service_id`, `created_at`, `resolution_due`. | Small |
| 3 | Dashboard and reports count in memory. | Replace with **aggregate queries** in the database (`GROUP BY`), and cache the result for a minute. Cache services and categories, which rarely change. | Small |
| 4 | The SLA check loads every open incident every minute. | Query only incidents whose next deadline is near (`resolution_due < now + x`) and that are not already breached, in batches. | Small |
| 5 | One server is not enough, or must not be a single point of failure. | Run **several instances behind a load balancer**. The application is nearly stateless already; three things are not: (a) sessions in memory → Spring Session with Redis or the database; (b) the SLA job would run on every instance → a shared lock so that one instance runs it (ShedLock); (c) the round-robin strategy remembers the last technician in memory → store it in the database. | Medium |
| 6 | Sending notifications slows down the request and is lost if the server stops at the wrong moment. | **Done for webhooks:** they use an **outbox table** written in the same transaction and are delivered with retries. Next: the same for notifications, delivered from a **message queue** (RabbitMQ or Kafka). Listeners already receive events and never call each other, so this replaces the dispatcher only. | Medium |
| 6a | Live updates: each server knows only the browsers connected to itself, and every message makes every open dashboard render again. | With several instances, **publish the messages through a shared channel** (Redis publish/subscribe or the message queue of step 6) so that every instance can tell its own browsers. Send at most one message per second per page, and for the busiest pages send the changed row instead of asking for the whole page. | Medium |
| 7 | Duplicate detection compares against all recent incidents of a service in memory. | Use the database's **full-text search**, or a search engine, to fetch only likely candidates. `DuplicateDetector` stays as the final check. | Medium |
| 8 | Reports compete with daily work for the database. | A **read replica** for reports and search. | Medium |
| 9 | Several teams work on the code, or one part needs far more capacity than the rest. | Split along the existing package boundaries into **services**: notifications first (already event-driven), then reporting. Incident, assignment and SLA should stay together because they share one transaction. | Large |
| 10 | Operations. | **Done:** container image, docker-compose, CI building and testing every change. Next: health checks and metrics (Spring Boot Actuator), central logging, automatic deployment. | Medium |

What would not change: the domain model, the state classes, the strategies, the listeners and the
exception hierarchy. The design decisions that make this possible were made for OOP reasons, not for
scale: persistence behind repositories, side effects behind events, rules in the model.

# 12. Database design

The schema is generated by Hibernate from the entity classes, so the code and the tables cannot drift
apart. The same schema runs on H2 (desktop, tests) and PostgreSQL (servers). There are 21 tables.

## 12.1 Entity-relationship diagrams

::: {.wide}
![Core tables: users, incidents, catalogue, SLA, audit and notifications](../diagrams/er-diagram.png){width=100%}
:::

::: {.wide}
![Tables for the knowledge base, service map, files, API keys and webhooks](../diagrams/er-diagram-extensions.png){width=100%}
:::

## 12.2 The tables

| Table | Holds | Key columns and constraints |
|---|---|---|
| `users` | Every account; one table for the class hierarchy (single-table inheritance). | `role` discriminator (EMPLOYEE, TECHNICIAN, ADMIN); unique `email`; BCrypt `password_hash`; technicians' `max_workload`, `available` |
| `technician_skills` | Which categories each technician can handle (many-to-many). | (`technician_id`, `category_id`) |
| `categories`, `category_keywords` | The nine categories and the words that hint at each. | unique `name` |
| `it_services`, `service_keywords`, `service_dependencies` | The service catalogue, the words people use for each service, and which service needs which. | unique `name`; `criticality` 1–3; (`service_id`, `depends_on_id`) |
| `sla_policies` | Response and resolution minutes per priority. | unique `priority` |
| `incidents` | The incidents with their classification, priority assessment, deadlines, SLA state, resolution and rating. | unique `code` (INC-0001…, from the sequence `incident_seq`); FKs to reporter, assignee, service, category, possible duplicate |
| `incident_affected_users` | Who said "I'm affected too" (many-to-many). | (`incident_id`, `user_id`) |
| `audit_entries` | The audit trail: every action with actor, time, old and new status and a note. Never updated or deleted. | FK `incident_id`, `actor_id`; `action` |
| `notifications`, `email_outbox` | In-app notifications and the simulated e-mails. | FK `recipient_id`, `incident_id`; `read_at` |
| `knowledge_articles` | Knowledge base articles with votes and views, optionally written from an incident. | FKs to category, service, author, source incident |
| `attachments`, `attachment_contents` | Files on incidents; the bytes are in their own table so lists stay fast. | FK `incident_id`, `uploaded_by_id`; unique `content_id`; `kind`, `size` |
| `api_tokens` | API keys: only the SHA-256 hash, the visible prefix, expiry, last use, revocation. | unique `token_hash`; FK `owner_id` |
| `webhooks`, `webhook_events`, `webhook_deliveries` | Webhooks, the events each wants, and the outbox / delivery log. | FK `webhook_id`; `state`, `next_attempt_at` (indexed together) |
| `settings` | Key-value settings changed at runtime (assignment strategy, demo SLA). | primary key `name` |

## 12.3 Important relationships

- An **incident** has exactly one reporter (an employee), one service, at most one category and at most one
  assignee (a technician); it may point to an earlier incident as a possible duplicate.
- An incident **owns** its audit entries, its attachments and its priority assessment (embedded columns
  `priority_*`): they have no meaning without it (composition).
- **Users** are one table with a `role` column; the JPA mapping turns rows back into `Employee`,
  `Technician` or `Administrator` objects (inheritance).
- **Services** form a directed graph through `service_dependencies`; the application refuses loops.
- **SLA policies** are looked up by priority; the deadlines are copied onto the incident when it is
  prioritised, so changing a policy later does not silently move the deadlines of open incidents.
- **Webhook deliveries** refer to incidents by code, not by foreign key, so the delivery log survives
  a demo reset.

## 12.4 Schema of the central table

```sql
create table incidents (
    duplicate_similarity float(53),
    priority_criticality integer,
    priority_impact_weight integer,
    priority_score integer,
    priority_urgency_weight integer,
    rating integer,
    acknowledged_at timestamp(6),
    assignee_id bigint,
    category_id bigint,
    closed_at timestamp(6),
    created_at timestamp(6) not null,
    escalated_at timestamp(6),
    id bigint not null,
    possible_duplicate_of_id bigint,
    rated_at timestamp(6),
    reporter_id bigint not null,
    resolution_breached_at timestamp(6),
    resolution_due timestamp(6),
    resolved_at timestamp(6),
    response_breached_at timestamp(6),
    response_due timestamp(6),
    service_id bigint not null,
    sla_paused_at timestamp(6),
    sla_paused_seconds bigint,
    updated_at timestamp(6) not null,
    code varchar(12) not null unique,
    title varchar(150) not null,
    rating_comment varchar(1000),
    resolution varchar(1000),
    description varchar(4000) not null,
    impact varchar(10) not null,
    priority varchar(5),
    sla_status varchar(10),
    status varchar(20) not null,
    urgency varchar(10) not null,
    primary key (id)
);
```

## 12.5 Sample data

On first start the application fills in the reference data, and an administrator can add realistic
sample incidents with one button (*System → Settings → Reset and add sample incidents*).

| Data | Contents |
|---|---|
| Users | 1 administrator (Neha Kapoor), 4 technicians with different skills (Ravi Kumar: Network, Hardware; Meera Nair: Database, Storage, Cloud; Arjun Singh: Security, Authentication, Network; Sara Thomas: Software, Application, Cloud), 4 employees |
| Services | 7, with criticality and dependencies: Payment Application (3), Authentication Service (3), Database Cluster (3), Corporate Network (2), Cloud Storage (2), Email & Collaboration (1), Workplace Devices (1) |
| Categories | The brief's 9: Hardware, Software, Network, Database, Cloud, Security, Authentication, Application, Storage, each with keywords |
| SLA policies | The brief's 4: P1 5 min / 30 min, P2 15 min / 2 h, P3 1 h / 8 h, P4 4 h / 24 h |
| Knowledge base | 15 articles for common problems |
| Sample incidents | 83: 23 hand-written stories in every status (including a payment outage with a network root cause, an escalated phishing wave, a waiting incident nobody could take) and 60 resolved incidents over the last two months for the learning features and reports, with 779 audit entries |

Demo accounts: `admin@itsm.local` / `admin12345`, `tech1@itsm.local` … `tech4@itsm.local` /
`tech12345`, `emp1@itsm.local` … `emp4@itsm.local` / `emp12345`. The sign-in page offers one-click demo
sign-in while demo mode is on.

# 13. Testing

## 13.1 Approach

| Level | What it checks | Tools |
|---|---|---|
| Unit tests | One class on its own, without Spring or a database: the priority formula, every lifecycle move, each assignment strategy, the classifier and similarity mathematics, the dependency graph, file type detection, the lockout clock, the rate limiter. | JUnit, AssertJ, Mockito |
| Integration tests | Services with a real (in-memory) database and transactions: reporting, assignment, SLA monitoring, notifications, escalation, webhooks with a real HTTP receiver. | Spring Boot test, H2 |
| Web tests | Real HTTP requests through the security filters, signed in through the real login form: every page, form, API endpoint and error page, every role's access. | MockMvc |
| Browser checks | The running application in Chromium during development: chat, uploads, live updates, alerts, offline page, API documentation, on H2 and on PostgreSQL. | Playwright |
| Continuous integration | All tests on every push, then installers for Windows, macOS and Linux. | GitHub Actions |

**617 automated tests in 43 test classes, all passing**, run with `./mvnw test` in about four minutes and
needing nothing but Java. Each test runs in a transaction that is rolled back, so tests cannot affect
each other; time-dependent rules (SLA, lockout, rate limits) are tested with a clock the test controls.

## 13.2 Documented test cases

36 cases are documented in full below (the brief asks for at least 15), chosen so that every area and
every kind of test is represented. Each names the automated test that carries it out.

| Type | Cases |
|---|---|
| Normal | TC-01, TC-20, TC-21, TC-28, TC-35 |
| Boundary | TC-02, TC-03, TC-10, TC-11, TC-22, TC-24 |
| Invalid input | TC-04, TC-15, TC-17, TC-26, TC-30, TC-31 |
| Exception | TC-06, TC-08, TC-09, TC-13, TC-18, TC-19 |
| Authorization | TC-14, TC-16, TC-32, TC-33, TC-36 |
| Business rule | TC-05, TC-07, TC-12, TC-23, TC-25, TC-27, TC-29, TC-34 |

::: {.wide}
### Mandatory modules

| ID | Area | Scenario | Input | Expected result | Type | Test method |
|---|---|---|---|---|---|---|
| TC-01 | Incident reporting | An employee reports an outage of a critical service. | Reporter Priya Sharma; service Payment Application (criticality 3); category Application; impact High; urgency High. | An incident with a code `INC-nnnn` is stored. Score 3 × 3 × 3 = 27, priority P1. Respond-by is creation + 5 min, resolve-by is creation + 30 min. Status is ASSIGNED and the technician is Sara Thomas (the one with the Application skill). | Normal | `IncidentWorkflowTests.reportingCreatesPrioritisesAndAssignsInOneGo` |
| TC-02 | Priority | The highest score that is not yet critical. | Score 17. | Priority P2. | Boundary | `WeightedPriorityCalculatorTests.seventeenIsStillP2` |
| TC-03 | Priority | The lowest score that is critical. | Score 18. | Priority P1. | Boundary | `WeightedPriorityCalculatorTests.eighteenIsP1` |
| TC-04 | Priority | A score that the formula can never produce. | Scores −1, 0, 28 and 100. | `IllegalArgumentException` each time; no priority is returned. | Invalid input | `WeightedPriorityCalculatorTests.scoresOutsideOneToTwentySevenAreRejected` |
| TC-05 | Lifecycle | Every possible move between two statuses is tried. | All 9 × 9 = 81 pairs of (from, to) status. | The 10 legal moves succeed and the status changes. The other 71 raise `InvalidStateTransitionException` naming both statuses, and the status is unchanged. | Business rule | `IncidentLifecycleTests.aMoveSucceedsOnlyIfTheLifecycleAllowsIt` |
| TC-06 | Lifecycle | Someone tries to close an incident that was only just reported. | A NEW incident; target status CLOSED. | `InvalidStateTransitionException` with the message "… cannot go from New to Closed"; the status is still NEW. | Exception | `IncidentLifecycleTests.newCannotJumpStraightToClosed` |
| TC-07 | Assignment | Skill-based assignment with two technicians who have the skill. | A Network incident. Ravi Kumar (Network, 2 open), Arjun Singh (Network, 1 open), Meera Nair and Sara Thomas (no Network skill, 0 open). | Arjun Singh is chosen: of those with the skill, he has the fewest open incidents. The idle technicians without the skill are not considered. | Business rule | `AssignmentStrategyTests.SkillBased.amongSkilledTechniciansTheLeastLoadedWins` |
| TC-08 | Assignment | An administrator assigns an incident to a technician who is full. | Arjun Singh already has 3 open incidents (his maximum is 3). | `WorkloadExceededException` with "3 of 3" in the message; the incident keeps its technician (Sara Thomas). | Exception | `IncidentWorkflowTests.anIncidentCannotBeGivenToATechnicianAtMaximumWorkload` |
| TC-09 | Assignment | An administrator assigns an incident to a technician who is marked unavailable. | Ravi Kumar, available = false. | `TechnicianUnavailableException` with "Ravi Kumar is not available"; the incident keeps its technician. | Exception | `IncidentWorkflowTests.anIncidentCannotBeGivenToAnUnavailableTechnician` |
| TC-10 | SLA | The SLA check runs at the exact moment of the response deadline. | P3 incident reported 09:00, response time 60 min, not acknowledged; check at 10:00:00. | Not breached. The status is AT_RISK. (A breach begins only after the deadline has passed.) | Boundary | `SlaTests.exactlyAtTheDeadlineIsNotYetABreach` |
| TC-11 | SLA | The SLA check runs when exactly 75% of the response time has been used. | Same incident; checks at +45 min and again at +50 min. (`SlaTests.justUnderSeventyFivePercentIsStillOnTrack` covers +44 min.) | At +45 min the status becomes AT_RISK and one warning is raised for the response deadline. At +50 min the status is still AT_RISK and no second warning is raised. | Boundary | `SlaTests.atSeventyFivePercentOfTheResponseTimeTheIncidentIsAtRiskAndAWarningIsRaisedOnce` |
| TC-12 | SLA | The technician asks the reporter for information and waits two days. | Incident IN_PROGRESS; moved to AWAITING_INFO at +100 min; checks at +100 min and +2880 min. | The status is PAUSED both times, nothing is breached, and the time used stays at 100 minutes. | Business rule | `SlaTests.waitingForInformationPausesTheResolutionClock` |
| TC-13 | SLA configuration | An incident is reported while its priority has no SLA policy. | The P1 policy has been deleted; a P1 incident is reported. | `MissingSlaConfigurationException` that names P1. No incident is stored. | Exception | `IncidentWorkflowTests.reportingFailsCleanlyWhenThePriorityHasNoSlaPolicy` |
| TC-14 | Access by role | An employee types the address of an administrator page. | Signed in as `emp1@itsm.local`; GET `/admin`, `/admin/dashboard`, `/admin/users`. | HTTP 403 Forbidden for all three. | Authorization | `SecurityTests.employeeCannotOpenAdmin` |
| TC-15 | Sign-in | Someone signs in with the wrong password. | Email `emp1@itsm.local`, password `not-the-password`. | Not signed in; sent back to `/login?error`. | Invalid input | `SecurityTests.wrongPasswordFails` |
| TC-16 | Access to actions | A user sends a request by hand for a move that belongs to another role or another technician. | An ASSIGNED incident. (a) The reporter posts "ACKNOWLEDGED" to the employee address; (b) the reporter posts it to the technician address; (c) a technician who is not the assignee posts it. | HTTP 403 for all three; the status is still ASSIGNED. | Authorization | `IncidentWebTests.aUserCannotMakeAnotherRolesMoveEvenByCraftingTheRequest` |
| TC-17 | Incident reporting | The report form is sent almost empty. | Title one space, description empty, no service, impact or urgency. | The form is shown again with an error on each of the five fields ("Please choose the affected service", …). The number of incidents is unchanged. | Invalid input | `IncidentWebTests.aReportWithMissingAnswersShowsTheErrorsAndCreatesNothing` |
| TC-18 | Duplicate detection | The same person reports the same thing twice. | Priya reports "Payment application unavailable", then "  payment APPLICATION unavailable " for the same service while the first is open. | `DuplicateIncidentException` naming the first incident's code and "still open"; nothing is stored. A different person reporting the same title is accepted and linked to the first as a possible duplicate. | Exception | `IntelligenceTests.theSamePersonReportingTheSameThingAgainIsRefused` |
| TC-19 | Notifications | The mail server is down while an incident is reported (the brief's "network failure"). | The email channel is made to throw `IllegalStateException("The mail server is down")`; an incident is reported. | The incident is saved and ASSIGNED. The three in-app notifications are delivered. Only the emails are missing. | Exception | `NotificationTests.aFailingEmailChannelDoesNotBlockTheAppChannelOrUndoTheIncident` |
| TC-20 | Whole workflow | The brief's payment-outage scenario, through the web pages, by three signed-in users. | Employee reports; technician acknowledges, starts work, asks for information; employee answers; technician resolves with a note; employee confirms; administrator closes. | Each step is accepted in that order and the status ends as CLOSED. The incident page shows every note in its timeline and offers no further status buttons. | Normal | `IncidentWebTests.thePaymentOutageScenarioFromReportToClose` |
:::

::: {.wide}
### Intelligent features, integrations and security

| ID | Area | Scenario | Input | Expected result | Type | Test method |
|---|---|---|---|---|---|---|
| TC-21 | Machine learning | No category keyword matches, so the learning classifier decides. | A report whose words appear only in past incidents and articles of one category. | The naive Bayes classifier picks that category, the incident is stored with it, and the timeline explains why ("… was suggested by the learning classifier (82% sure …) because of the words: …"). | Normal | `KnowledgeAndAiTests.whenNoKeywordMatchesTheClassifierChoosesTheCategoryAndSaysWhy` |
| TC-22 | Machine learning | The classifier is asked about text with no word it has ever seen. | "zzz qqq". | No prediction at all (empty), rather than a guess. | Boundary | `TextIntelligenceTests$NaiveBayes.withNoKnownWordItDoesNotGuess` |
| TC-23 | Suggested solutions | A fix that worked several times for the same problem. | Three resolved incidents with the same resolution text; a new similar incident. | The fix is suggested once, with "used 3 times", ranked by TF-IDF cosine similarity. | Business rule | `KnowledgeAndAiTests.aFixThatWorkedSeveralTimesIsSuggestedOnceWithItsCount` |
| TC-24 | Predictive SLA | Too few similar incidents to learn from. | Fewer than 4 finished incidents of the same category or priority. | No forecast is shown (instead of an unreliable one). | Boundary | `SlaPredictorTests.withTooLittleHistoryThereIsNoForecast` |
| TC-25 | Root cause | Two services fail that share a dependency. | Open P1 on Payment Application and P2 on Corporate Network within 3 hours. | Corporate Network is named the probable root cause (Payment Application needs it); the status page and the administrator's overview show it. | Business rule | `ServiceIntelligenceTests.twoFailingServicesThatShareADependencyPointToIt` |
| TC-26 | Service map | An administrator makes two services depend on each other. | Database Cluster already needs Corporate Network; the administrator makes Corporate Network depend on Database Cluster. | Refused with "… cannot depend on … they would wait for each other"; nothing is saved. | Invalid input | `StatusAndAffectedTests.anAdministratorCannotMakeTwoServicesWaitForEachOther` |
| TC-27 | Affected users | More people say "I'm affected too". | A Low-impact incident; 2 more employees join, then 2 more. | At 3 people the impact rises to Medium, at 5 to High, and the priority is recalculated each time; everyone affected is told when it is fixed. | Business rule | `StatusAndAffectedTests.moreAffectedPeopleRaiseTheImpactAndThePriority` |
| TC-28 | Assistant | An employee reports a problem in a conversation. | "My laptop battery drains within an hour …", then "yes", "only me", "it can wait until next week", "yes". | The service (Workplace Devices) and category (Hardware) are recognised, impact Low and urgency Low are read from plain words, and an incident is created by the normal rules with a link to it. | Normal | `AssistantTests.aConversationInPlainWordsReportsAnIncident` |
| TC-29 | Assistant | The problem is a known outage. | An open P1 on Payment Application; another employee describes failing card payments and answers "yes". | The employee is added to the existing incident ("I'm affected too"); no new incident is created. | Business rule | `AssistantTests.aKnownProblemIsJoinedInsteadOfReportedAgain` |
| TC-30 | Assistant | Typed HTML in the chat. | `<script>alert(1)</script> my laptop screen …` | Shown as text (`&lt;script&gt;`), never run. | Invalid input | `AssistantTests.whatPeopleTypeIsNeverTreatedAsHtml` |
| TC-31 | Attachments | Files that must not be attached. | An SVG with a script, a 5 MB + 1 byte PNG, an empty file, HTML renamed to `.png`. | Each is refused with a message; the kind is decided by the file's first bytes, not its name; nothing is stored. | Invalid input | `AttachmentTests.scriptsTooBigFilesAndEmptyFilesAreRefused` |
| TC-32 | Attachments | Another employee asks for a file. | A screenshot on Priya's incident; Rahul requests `/attachments/{id}`. | HTTP 404 (the file does not exist for him); the reporter and staff get it with `nosniff` and a sandboxing content security policy. | Authorization | `AttachmentTests.uploadingFromTheIncidentPageAndViewingTheImage` |
| TC-33 | REST API | Requests without a key, with a wrong key and with a revoked key. | GET `/api/v1/incidents` and `/api/v1/me`. | HTTP 401 as RFC 9457 problem JSON with `WWW-Authenticate: Bearer`; a revoked key stops working at once. | Authorization | `ApiTests.wrongRevokedAndExpiredKeysAreRefused` |
| TC-34 | REST API | A technician's key moves someone else's incident, and an impossible move. | POST `/api/v1/incidents/{code}/status` with IN_PROGRESS (not the assignee) and CLOSED (from ACKNOWLEDGED). | HTTP 403 and HTTP 409; the status is unchanged. The assignee's ACKNOWLEDGED succeeds with 200. | Business rule | `ApiTests.aTechnicianWorksOnlyOnTheirOwnIncidentsAndIllegalMovesAreConflicts` |
| TC-35 | Webhooks | An incident is reported while a webhook listens. | A signed-JSON webhook to a local receiver; a P1 is reported. | One delivery is queued in the same transaction (outbox) and delivered with `X-Quell-Signature = HMAC-SHA256(secret, timestamp.body)`; the message carries the priority and the assigned technician. | Normal | `WebhookTests.anEventIsQueuedWithTheChangeAndDeliveredSigned` |
| TC-36 | Security | Password guessing. | Five wrong passwords for one account, then the right one; then an address without an account. | The fifth failure pauses sign-in (`/login?locked`) even for the right password; other accounts are unaffected; unknown addresses are paused the same way, so nothing reveals which accounts exist. | Authorization | `SecurityHardeningTests.fiveWrongPasswordsPauseSignInEvenWithTheRightPassword` |
:::

::: {.wide}
## 13.3 The brief's exception cases and where each is tested

| Exception case in the brief | Exception class | Test method |
|---|---|---|
| Invalid state transition | `InvalidStateTransitionException` | `IncidentLifecycleTests.aMoveSucceedsOnlyIfTheLifecycleAllowsIt` (TC-05), `ErrorHandlingTests.anInvalidStateTransitionComesBackAsAMessageOnTheIncidentPage` |
| Technician unavailable | `TechnicianUnavailableException` | `IncidentWorkflowTests.anIncidentCannotBeGivenToAnUnavailableTechnician` (TC-09), `ErrorHandlingTests.assigningToAnUnavailableTechnicianComesBackAsAMessage` |
| Workload exceeded | `WorkloadExceededException` | `IncidentWorkflowTests.anIncidentCannotBeGivenToATechnicianAtMaximumWorkload` (TC-08), `ErrorHandlingTests.assigningToATechnicianAtTheirMaximumComesBackAsAMessage` |
| Missing SLA configuration | `MissingSlaConfigurationException` | `IncidentWorkflowTests.reportingFailsCleanlyWhenThePriorityHasNoSlaPolicy` (TC-13), `ErrorHandlingTests.aMissingSlaPolicyIsExplainedOnTheReportForm` |
| Duplicate incident | `DuplicateIncidentException` | `IntelligenceTests.theSamePersonReportingTheSameThingAgainIsRefused` (TC-18), `ErrorHandlingTests.reportingTheSameThingTwiceIsExplainedOnTheReportForm` |
| Unauthorized operation | `UnauthorizedOperationException` | `ErrorHandlingTests.pressingAnotherRolesButtonShowsTheAccessDeniedPageAndChangesNothing`, `ErrorHandlingTests.openingSomeoneElsesIncidentShowsTheAccessDeniedPageWithTheReason` |
| Invalid input | `InvalidInputException` | `ErrorHandlingTests.aMissingNoteComesBackAsAMessage`, `FullWorkflowTests.aRatingIsPossibleOnlyAfterConfirmingOnlyOnceAndOnlyFromOneToFive` |
| Incident not found | `IncidentNotFoundException` | `ErrorHandlingTests.anIncidentThatDoesNotExistShowsTheNotFoundPage` |
| Service not found | `ServiceNotFoundException` | `ErrorHandlingTests.reportingAgainstAServiceThatDoesNotExistShowsTheNotFoundPage` |
| Database failure | Spring's `DataAccessException` | `DatabaseFailureTests.aPageThatCannotReachTheDatabaseShowsAReadableMessage`, `DatabaseFailureTests.anIncidentThatCannotBeSavedIsReportedToTheUserAndNothingIsStored` |
| Network failure | any exception inside a notification channel | `NotificationTests.aFailingEmailChannelDoesNotBlockTheAppChannelOrUndoTheIncident` (TC-19), `NotificationTests.aFailingAppChannelDoesNotBlockTheEmailChannelOrUndoTheIncident` |
:::

The complete list of test classes, with what each covers, is in `docs/test-cases.md` in the repository.

# 14. The user interface

Each role has its own navigation, colour line and home page (the brief asks for a different interface
per user). Dark is the default theme; light is one click away and remembered.

![The product site, with downloads for every operating system](screenshots/01-landing.jpg){width=100%}

![Sign-in, with one-click demo accounts](screenshots/02-login.jpg){width=100%}

![Employee home: own incidents, what waits for them, problems they follow, live service health](screenshots/03-employee-home.jpg){width=100%}

![The assistant recognises the service and offers a known outage instead of a duplicate report](screenshots/04-assistant-known-outage.jpg){width=100%}

![The assistant suggests a knowledge article before reporting, then asks in plain words](screenshots/05-assistant-articles.jpg){width=100%}

![The report form: smart suggestions while typing (impact, urgency, category) and a screenshot drop zone](screenshots/07-report-form-suggestions.jpg){width=100%}

![Technician queue, ordered by deadline](screenshots/08-technician-queue.jpg){width=100%}

![Incident page for the technician: details, predicted finish time, suggested solutions with match scores, attachments](screenshots/09-incident-page.jpg){width=100%}

![Status page: overall health, probable root cause, known problems with "I'm affected too", dependency map](screenshots/10-status-page.jpg){width=100%}

![Administrator overview: health strip, root cause, totals, waiting and escalated incidents, SLA risk with predictions](screenshots/11-admin-overview.jpg){width=100%}

![Reports](screenshots/12-reports.jpg){width=100%}

![Audit log across all incidents](screenshots/13-audit-log.jpg){width=100%}

![Integrations: webhooks to Slack, Teams or any app, with the delivery log](screenshots/14-integrations.jpg){width=100%}

![Knowledge base](screenshots/15-knowledge-base.jpg){width=100%}

![API keys: shown once, revocable, expiring](screenshots/16-api-keys.jpg){width=100%}

![Interactive REST API documentation (OpenAPI)](screenshots/17-api-docs.jpg){width=100%}

![Command palette (Ctrl K): any page, incident or article](screenshots/18-command-palette.jpg){width=100%}

![Light theme](screenshots/19-light-theme.jpg){width=100%}

![On a phone](screenshots/20-phone-assistant.jpg){width=40%}

# 15. Project demonstration

The recorded video ([group number]`_OOP_S1_2025-2026_Project`) shows the application working, with
every member introducing themselves and explaining their modules. It follows the script in
`docs/video-script.md`. The three workflows the brief asks for, and two more:

| # | Workflow | What the video shows |
|---|---|---|
| 1 | **Employee reports → system prioritises → technician assigned → notification generated** | An employee reports a payment outage by chatting with the assistant (with voice), and a second problem on the form with a screenshot. Priority P1 is calculated (3 × 3 × 3 = 27, shown on the page), deadlines are set, the skilled technician is assigned, and the technician's bell and a desktop alert appear live. |
| 2 | **Technician investigates → SLA monitored → incident resolved → user notified** | The technician acknowledges, sees suggested solutions and the predicted finish time, asks for information (the SLA clock pauses), uses a suggested fix to resolve; the employee is notified, confirms and rates; the fix becomes a knowledge article. The SLA risk list and the 75% warning are shown with the short demo SLA times. |
| 3 | **Multiple incidents → duplicates/related incidents identified → correlated/escalated** | Several people report the same outage: duplicates are linked, others join with "I'm affected too" and the impact rises; failures on two services are traced to the network they share (root cause on the status page and the dependency map); an unacknowledged P1 is escalated to the administrators. |
| 4 | Administration and analytics | Live overview, reports, audit trail, switching the assignment strategy, managing services and their dependencies. |
| 5 | Integrations | A webhook posting to a channel, the REST API used with a key from the interactive documentation, the installed app. |

## 15.1 How to run it

| Who | How |
|---|---|
| **Anyone (no technical knowledge)** | Download the installer for your computer from the releases page (`Quell-Setup-Windows.exe`, `Quell-Mac.dmg` or `Quell-Linux.deb`), install, and open Quell from the Start menu or Applications. Java is included. |
| **A server** | `docker compose up --build` in the repository, then open `http://localhost:8080`. |
| **A developer** | Java 25, then `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`) and open `http://localhost:8080`. Tests: `./mvnw test`. |

Sign in with a demo account (section 12.5), or use the one-click buttons on the sign-in page. To see
everything at once, sign in as the administrator and press *System → Settings → Reset and add sample
incidents*.

# 16. Team and responsibilities

Every member understands the whole system; each was responsible for the areas below and presents
them in the video.

| Member | Responsibility | Main packages and files |
|---|---|---|
| [Name 1, ID] | Domain model and OOP design: incidents, users, the State pattern, priority, SLA, exceptions | `domain/`, `domain/state/`, `priority/`, `sla/`, `error/` |
| [Name 2, ID] | Backend services and APIs: reporting, assignment strategies, notifications, events, REST API, webhooks | `service/`, `assignment/`, `notification/`, `event/`, `api/`, `webhook/` |
| [Name 3, ID] | User interface and experience: pages for every role, assistant, live updates, installable app, design system | `web/`, `templates/`, `static/`, `assistant/`, `live/` |
| [Name 4, ID] | Intelligent features, database, testing and DevOps: machine learning, root cause, persistence, tests, installers, Docker, CI | `intelligence/`, `status/`, `repository/`, `src/test/`, `packaging/`, `.github/` |

(With five members, the fifth takes testing and DevOps from member 4.)

# 17. Limitations and future work

| Area | Today | Next step |
|---|---|---|
| Tenancy | One organisation per installation. | Tenant id on every row and per-tenant settings, to sell it as a hosted service. |
| Sign-in | E-mail and password, lockout. | Single sign-on (OpenID Connect), two-factor sign-in, password reset by e-mail. |
| E-mail | Simulated outbox. | A real mail channel (one class implementing `NotificationChannel`), replies by e-mail added as notes. |
| SLA calendar | Clock time, 24 hours a day. | Business hours and holidays per policy. |
| Analytics | Tables and the health map. | Charts over time and exports to CSV. |
| Machine learning | Naive Bayes and TF-IDF, trained locally. | Optional use of a large language model for summaries and drafting replies, with the organisation's consent. |
| Scale | One server; several need shared sessions and a lock for the SLA job. | Spring Session, ShedLock, a message broker for live updates and webhooks (section 11, question 9). |

# 18. Conclusion

Quell meets every mandatory requirement of the brief and 15 of its 16 innovation challenges, and goes
further where a real product must: integrations, installation, security and polish. The central idea
of the brief, using object-oriented thinking to build a maintainable, extensible and intelligent
system, shows in how the product grew. The live updates, the learning classifier, the webhooks and the
assistant were all added after the core was finished, and each was a new class behind an existing
interface (a listener, a channel, a state) rather than a change to the incident logic. That is what the
patterns were for.

# Appendix A. REST API endpoints

All under `/api/v1`, with `Authorization: Bearer qk_…`. Full description: `/api/docs` (interactive) and
`/api/openapi.json` (OpenAPI 3.1).

| Method | Path | What it does |
|---|---|---|
| GET | `/me` | Who the key belongs to |
| GET | `/incidents` | List the incidents the owner may see; filters `status`, `priority`, `serviceId`, `categoryId`, `slaStatus`, `q`; paging `page`, `size` (≤ 100) |
| POST | `/incidents` | Report an incident (administrators' keys may give `reporterEmail`) → 201 with `Location` |
| GET | `/incidents/{code}` | One incident |
| GET | `/incidents/{code}/timeline` | Its history |
| POST | `/incidents/{code}/notes` | Add a note → 201 |
| POST | `/incidents/{code}/status` | Change the status (only the moves the owner may make) |
| GET | `/status` | Overall service status and the probable root cause |
| GET | `/services` | Services with health and dependencies |
| GET | `/categories` | The categories |
| GET | `/knowledge` | Search the knowledge base (`q`) |

# Appendix B. Repository layout

```
.github/workflows/build.yml     CI: tests, installers for 3 operating systems, release
docs/                           this report, diagrams (PlantUML), test cases, video script, install guide
packaging/                      installer scripts and icons
src/main/java/com/itsm/
  domain/  domain/state/        entities, enums, the nine incident states
  service/ assignment/ sla/ priority/        core use cases and rules
  intelligence/ intelligence/text/           machine learning, triage, recommendations, duplicates
  status/                       service graph, health, root cause
  assistant/                    the reporting assistant (chat)
  knowledge/ attachment/        knowledge base, file attachments
  event/ notification/ live/    events, notifications, live updates
  api/ webhook/                 REST API and API keys, webhooks
  security/ web/                sign-in, rules, page controllers
  desktop/ config/ seed/ report/ search/ repository/ error/
src/main/resources/templates/   41 Thymeleaf pages
src/main/resources/static/      CSS design system, JavaScript, service worker, images
src/test/java/com/itsm/         43 test classes, 617 tests
Dockerfile, docker-compose.yml  server deployment with PostgreSQL
```
