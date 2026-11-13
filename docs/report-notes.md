# Report notes

Notes for the project report on the Intelligent IT Service Management & Incident Resolution System
(I-ITSM). Each section matches a deliverable of the brief and says where in the code the claim can be
checked. Class names are in `src/main/java/com/itsm/`. Diagrams are in [`docs/diagrams/`](diagrams/),
the documented test cases in [`docs/test-cases.md`](test-cases.md).

Contents

1. [Problem analysis](#1-problem-analysis)
2. [Requirements](#2-requirements)
3. [Business rules](#3-business-rules)
4. [Architecture](#4-architecture)
5. [OOP concepts](#5-oop-concepts)
6. [Design patterns](#6-design-patterns)
7. [The priority algorithm](#7-the-priority-algorithm)
8. [Intelligent features](#8-intelligent-features)
9. [Exception handling](#9-exception-handling)
10. [Security](#10-security)
11. [Technology selection: the nine questions](#11-technology-selection-the-nine-questions)
12. [How the system would scale](#12-how-the-system-would-scale)
13. [What was not implemented](#13-what-was-not-implemented)

---

## 1. Problem analysis

### The problem

When an IT service fails, the people affected need one place to report it, and the IT team needs to
know which failure to work on first. Without a system, reports arrive by email, chat and phone. The
same outage is reported many times, nobody knows who is working on what, a critical outage waits
behind a broken mouse, and nobody notices that a promised response time has passed until a user
complains.

### Stakeholders

| Stakeholder | What they need from the system |
|---|---|
| Employee (reporter) | Report a problem in under a minute, see who has it and what is happening, be told when it is fixed, and be able to say "it is not fixed". |
| Technician | One queue of the incidents assigned to them, ordered by deadline. Everything about an incident on one page. No more work than they can handle. |
| Administrator / service desk manager | See the whole desk at a glance: what is open, what is critical, what is late, who is overloaded. Configure services, categories, SLA policies, technicians and the assignment rule without a programmer. |
| IT management | Evidence: SLA compliance, resolution times, workload per technician, a complete audit trail. |
| Business owners of the services | Critical services (payments, authentication) are dealt with before unimportant ones. |
| Auditors | Every change to an incident is recorded with who, what and when, and cannot be edited. |
| The development team | A design where a new category, assignment rule, notification channel or lifecycle rule can be added without rewriting unrelated code. |

### Existing challenges

- **No single source of truth.** Reports are scattered across channels and get lost.
- **Priority by whoever shouts loudest.** There is no consistent rule, so two people rank the same
  incident differently.
- **Manual assignment.** A manager reads each report and picks a technician, which is slow and ignores
  skills and current workload.
- **SLAs checked after the fact.** A missed deadline is discovered when it is too late to prevent it.
- **Duplicates.** One outage produces many reports and several technicians start on the same problem.
- **No history.** Nobody can show afterwards what was done, by whom and when.

### Proposed solution

A web application with three roles. An employee reports an incident on one form. The system then,
without anyone doing anything: suggests a category if none was chosen, calculates a priority from
impact, urgency and the criticality of the affected service, sets response and resolution deadlines
from the SLA policy for that priority, checks whether it looks like a duplicate of a recent incident,
assigns a technician using the rule the administrator has chosen, records each step in an audit trail
and notifies the people concerned. The incident then follows a fixed lifecycle of nine statuses. A job
checks every open incident once a minute, warns at 75% of the allowed time, records breaches, and
escalates a critical incident nobody has acknowledged.

### Assumptions

- One organisation uses the system; there is no separation between tenants.
- Users have a modern browser. There is no native mobile app, but the pages work on a phone.
- Every incident is about exactly one service from the catalogue.
- Employees register themselves. Technicians and administrators are created by an administrator.
- The times in SLA policies are clock time, 24 hours a day. There are no business hours or holidays.
- "Number of affected users" is captured by the impact level (Low: one or two people; Medium: a team or
  department; High: many people or customers) rather than as a separate number, because the reporter
  rarely knows the exact count.
- Email is simulated. Messages are stored in an outbox that the administrator can read.
- The server clock is the reference for all times.

---

## 2. Requirements

### User roles

| Role | Can do | Cannot do |
|---|---|---|
| **Employee** | Register. Report an incident. See their own incidents and their timelines. Answer an information request. Confirm a fix or reopen. Rate the resolution. Read their notifications. | See other people's incidents. Change a status that belongs to the technician. Open any technician or administrator page. |
| **Technician** | See their queue, ordered by deadline. Acknowledge, start work, add investigation notes, request information, resolve with a resolution note, close a verified incident. Correct the classification of their own incidents. Search all incidents and open them read-only. | Change incidents assigned to someone else. Assign incidents. Open administrator pages. |
| **Administrator** | Dashboard, reports and audit log. Search all incidents. Assign, reassign, reopen, close, reclassify. Choose the assignment strategy. Manage users, technicians (skills, maximum workload, availability), services, categories and SLA policies. Read the email outbox. Switch demo SLA on. Reset demo data. | Report incidents or make the technician's moves (acknowledge, resolve) on someone's behalf. |

### Functional requirements

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

### Non-functional requirements

| ID | Quality | Requirement | How it is met |
|---|---|---|---|
| NFR-01 | Security | Only authenticated users; each role limited to its own functions; passwords never stored in readable form. | Section 10. |
| NFR-02 | Maintainability | A new category, assignment strategy, notification channel, listener or priority rule needs no change to unrelated code. | Categories are data. Strategies, channels and listeners are found by interface. Section 6. |
| NFR-03 | Reliability | A failure in a side task must not undo or block the main task. | Notifications are sent after commit, each channel in its own transaction with its own error handling. |
| NFR-04 | Data integrity | An incident is never left half-updated. | Each user action is one database transaction; any exception rolls all of it back. |
| NFR-05 | Auditability | Who did what and when is always known. | Audit entries are written in the same transaction as the change and have no update or delete function. |
| NFR-06 | Usability | Understandable without training; clear feedback on every action; works on a phone. | Bootstrap 5 responsive layout, a success or error message after every action, only the buttons a user may press are shown. |
| NFR-07 | Persistence | Data survives a restart. | H2 file database in `./data/itsm`. |
| NFR-08 | Performance | Pages respond quickly at the size of one organisation. | Indexed lookups by code and id, lists fetched with their related objects in one query, the SLA check takes milliseconds for hundreds of incidents. |
| NFR-09 | Testability | Rules can be tested without a server or database. | Rules live in plain classes. 498 automated tests. |
| NFR-10 | Portability | Runs on any machine with Java 25, nothing else to install. | Maven wrapper, embedded server, embedded database, Bootstrap served from the application. |
| NFR-11 | Error tolerance | A user never sees a stack trace. | `ErrorHandler` turns every exception into a message or a page. Section 9. |
| NFR-12 | Upgradability | A newer version runs on an existing database. | New columns are nullable; enum values are stored as text. |

---

## 3. Business rules

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

---

## 4. Architecture

One Spring Boot application in layers. Picture: [`diagrams/architecture.png`](diagrams/architecture.png).

```
Browser (HTML, Bootstrap 5)
   │  HTTP: session cookie + CSRF token
Security         com.itsm.security     who are you, may you open this address
Presentation     com.itsm.web          controllers, form objects, Thymeleaf templates, error pages
Services         com.itsm.service      one method per user action, one transaction each
                 .assignment .sla .priority .intelligence .report .seed
Events           com.itsm.event        publisher and listeners (audit trail, notifications, live updates)
                 com.itsm.notification channels, factory, dispatcher
                 com.itsm.live         the open browser connections (server-sent events)
Domain model     com.itsm.domain       entities, enums, the nine state classes: the rules
                 com.itsm.error        the exception hierarchy
Persistence      com.itsm.repository   Spring Data JPA repositories → Hibernate → H2
```

Principles:

- **Dependencies point downwards.** Controllers call services, services use the domain model and the
  repositories. The domain model knows nothing about Spring MVC, HTTP or the pages.
- **The rules are in the domain model, not in the controllers.** An `Incident` itself refuses an illegal
  move. A controller cannot bypass a rule by forgetting a check, because the check is not in the
  controller.
- **One transaction per user action.** `IncidentService.report` saves the incident, the audit entries
  and the assignment together or not at all.
- **Side effects are decoupled by events.** A service publishes "this happened". It does not know that
  an audit entry is written or that notifications go out.
- **Notifications leave after the commit.** If the transaction fails, nobody is told about something
  that did not happen. If a notification fails, the incident is unaffected.
- **Server-side rendering.** The server sends complete pages. There is no separate JavaScript
  application and no public API to secure separately.

Why a layered monolith and not microservices is answered in section 11, question 5.

---

## 5. OOP concepts

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
| **Generics and collections** | `List<AssignmentStrategy>`, `Set<Category>`, `Map<Role, String>`, `Optional<...>` | Type-safe collections; `Optional` instead of `null` for "maybe nothing". |

The class diagrams ([domain model](diagrams/class-diagram.png),
[interfaces and patterns](diagrams/class-diagram-patterns.png)) show all of these.

---

## 6. Design patterns

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

Two patterns that were considered and left out, with the reason:

- **Singleton.** Spring already creates exactly one of each service. Writing the pattern by hand would
  add code and make testing harder.
- **Chain of Responsibility** for escalation. The brief describes one escalation step (to the
  administrators). A chain of one link would be decoration.

---

## 7. The priority algorithm

### The rule

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

### Why multiply and not add

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

### Limits and how it could grow

- The three factors have equal weight. A weighted version would be another class implementing
  `PriorityCalculator`; nothing else would change (Strategy pattern).
- The number of affected users is part of the impact level, not a separate input (see Assumptions).
- The priority is calculated when the incident is reported and again when a technician or
  administrator corrects the classification. It does not rise by itself with age; instead the SLA
  monitor and the escalation rule deal with incidents that wait too long.

---

## 8. Intelligent features

All four are rule-based and explain themselves on the page. None uses machine learning, so every
decision can be traced and tested.

| Feature | What it does | How | Class |
|---|---|---|---|
| **Duplicate detection** | Marks a new incident as a possible duplicate of an earlier one and links the two. | Compares the title with open incidents on the same service from the last 24 hours. Common words ("the", "is", "unable", "application") are removed. Similarity = shared words ÷ all words (Jaccard). 0.4 or more is a match; the most similar wins. The page shows the percentage and the shared words. The incident is linked, never rejected, because two people may be seeing two different problems. | `DuplicateDetector`, `Words`, `DuplicateMatch` |
| **Automatic escalation** | Brings a critical incident nobody is looking at to the administrators' attention. | Every minute: a P1 incident that is open, not acknowledged, past its response deadline and not escalated before is marked escalated, recorded in the audit trail, and all administrators are notified in the app and by email. It appears in a panel at the top of the dashboard. | `EscalationPolicy`, `AutoEscalation` |
| **Category suggestion** | Chooses a category when the reporter leaves it open. | Each category has keywords (data, editable by the administrator). The category with the most keywords in the title and description wins; a tie goes to the longer, more specific keyword. Whole words only. The timeline says which words decided it. If nothing matches, the reporter is asked to choose. | `CategorySuggester`, `CategorySuggestion` |
| **SLA risk list** | Shows the administrator which open incidents will breach next. | For each open incident takes the deadline that currently counts (response until acknowledged, then resolution), leaves out paused and already breached ones, and sorts by time left. Shows the share of time used and the reason in words. | `SlaRiskRanker`, `SlaRisk` |

Also "intelligent" in the brief's sense: the automatic priority (section 7), three assignment
strategies switchable at runtime, and SLA monitoring with a pause while waiting for the reporter.

Thresholds and why: 0.4 similarity is the lowest value at which the brief's three example reports
("Payment application unavailable", "Payment transaction failing", "Unable to make payment") are linked
while unrelated reports on the same service are not. 24 hours keeps yesterday's solved outage from
attracting today's new one.

### Innovation feature: live updates

When an incident is created or changes, the pages that show it update without a refresh: the
administrator's dashboard, the technician's home screen and queue, the employee's home screen, the
incident lists and search results, and the number on the notification bell.

**How it works.** Each signed-in page keeps one HTTP connection open to `/live` (server-sent events,
the browser's built-in `EventSource`). The server pushes one of three small messages down it:
`incident` (with the incident's code), `bell` (with the user's unread count) or `refresh`. On an
`incident` message the page fetches itself again in the background and replaces only the parts marked
`data-live`; on a `bell` message it changes the number directly.

| Part | Class | Role |
|---|---|---|
| The open connections | `live/LiveUpdates` | Remembers who is listening (user, role, session) and sends messages, from one background thread. |
| The address | `live/LiveController` | `GET /live` returns the stream. |
| Incident news | `live/LiveUpdateListener` | A third `IncidentEventListener` (Observer). Waits for the commit; one message per incident per action. |
| Bell count | `notification/LiveChannel` | A third `NotificationChannel` (Strategy), handed out by the factory after the in-app channel. |
| Browser side | `static/js/live.js` | About a hundred lines of plain JavaScript, no library. |

**Why server-sent events** and not WebSockets or polling: the news only flows one way, from server to
browser, which is exactly what SSE is for. It is ordinary HTTP, so the existing session cookie and
security rules apply unchanged, the browser reconnects by itself, and no library is needed on either
side. Polling every few seconds would do the same with constant load and a delay. WebSockets would add
a second protocol and their own security configuration for a two-way channel nobody needs.

**Why the page fetches itself again** instead of receiving the new data in the message: the server
already knows how to render every page and who may see what. Sending data would mean a second, parallel
way of producing the dashboard in JavaScript, with its own access checks to get wrong. This way a
message carries no content at all, only "something changed".

**Design decisions worth mentioning in the report**

- *After the commit.* The listener registers itself with the running transaction and sends when it
  commits. A page that fetches fresh content therefore finds the new state, and a change that is rolled
  back is never announced.
- *Once per action.* Reporting an incident raises three events (reported, prioritised, assigned); the
  pages get one message.
- *Privacy.* Technicians and administrators may see every incident, so they get every message. An
  employee gets only the codes of incidents they reported.
- *Isolation.* Messages are sent from a dedicated thread. A browser on a broken connection cannot slow
  down the request of the person who caused the news, and a failed send only removes that connection.
- *No database connection per tab.* The `/live` request reads nothing from the database. This is
  tested: with three streams open the connection pool has no connection in use.
- *Forms are never replaced.* The incident page shows "This incident has just been changed" with a
  link, because someone may be writing a note. On search pages only the results are replaced.
- *Progressive enhancement.* Without JavaScript every page works exactly as before.

### Innovation challenges of the brief that are covered

| Innovation challenge in the brief | In this project |
|---|---|
| Incident similarity detection | Duplicate detection (`DuplicateDetector`) |
| Predictive SLA violation detection | The 75% warning and the "most likely to breach next" list (`SlaRiskRanker`) |
| Automated incident escalation | `EscalationPolicy`, `AutoEscalation` |
| AI-assisted incident classification | Rule-based category suggestion (`CategorySuggester`); no machine learning |
| Real-time notifications | The bell count pushed to open pages (`LiveChannel`) |
| Real-time operational dashboard | The administrator's dashboard updating live (`LiveUpdateListener`) |
| Event-driven architecture | Inside the application: `IncidentEventPublisher` and its three listeners |
| Analytics and visualization | The Reports page (`ReportService`) |

---

## 9. Exception handling

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

One class, `ErrorHandler` (`@ControllerAdvice`), turns exceptions into pages for every controller, so
no controller has its own error-page code and no stack trace reaches a browser.

---

## 10. Security

### What is implemented

| Requirement in the brief | Measure | Where |
|---|---|---|
| **Authentication** | Form login with email and password. A session cookie identifies the user afterwards. The session ID changes at sign-in (protection against session fixation). Sign-out ends the session. | `SecurityConfig` |
| **Password protection** | Passwords are stored only as BCrypt hashes (salted, deliberately slow). The plain password is never stored or logged. Minimum length 8 characters. | `SecurityConfig.passwordEncoder`, `RegistrationService` |
| **Role-based authorization** | Every address is checked on the server: `/employee/**` needs role EMPLOYEE, `/technician/**` TECHNICIAN, `/admin/**` ADMIN, everything else a signed-in user. Hiding a menu item is never the only protection. | `SecurityConfig` |
| **Protection against unauthorized operations** | A second check at object level: `User.canView(incident)` and `canWorkOn(incident)`. A technician cannot change another technician's incident and an employee cannot open someone else's, even with the right address. Each status move also names the role that may make it. | `User` subclasses, `IncidentService`, `Transition` |
| **Input validation** | Bean Validation on every form (required, length, range, email format). Values are checked again in the domain model. Enum and number parameters that cannot be read give a 400 page. | `web/*Form`, `Incident`, `ErrorHandler` |
| **Secure access to the endpoints** | There is no separate public API; every endpoint is a page or form behind the rules above. Every state-changing request needs a CSRF token, so another website cannot make a signed-in user's browser submit a form. | Spring Security default, `th:action` in templates |
| **Cross-site scripting (XSS)** | Thymeleaf escapes everything a user typed when it is shown. No template uses unescaped output for user text. | Templates |
| **SQL injection** | All queries go through JPA with bound parameters. No SQL is built by joining strings. The search uses the Criteria API. | `repository/`, `IncidentSpecifications` |
| **Account control** | An administrator can disable an account. It can no longer sign in, and sessions that are already open end on the next request. | `SignedInSessions`, `ItsmUserDetails.isEnabled` |
| **No information leaks** | The sign-in page gives the same message for an unknown email, a wrong password and a disabled account. Error pages show a readable sentence, never a stack trace or SQL. | `auth/login.html`, `ErrorHandler` |
| **Least privilege for tools** | The H2 database console exists only in the development profile and only for administrators. | `SecurityConfig`, `application-dev.properties` |
| **Accountability** | Every change to an incident is in the audit trail with the user and the time. | `AuditTrailListener` |
| **Live updates** | The stream at `/live` needs a signed-in session like any other address. A message carries only an incident code or the user's own unread count, never content; employees get only the codes of their own incidents. What a page then shows is fetched through the normal, access-checked pages. Streams are closed at sign-out and when an account is disabled. | `LiveUpdates`, `LiveController` |
| **Browser protections** | Spring Security's default headers: no framing by other sites (clickjacking), no content-type sniffing, no caching of protected pages. | Spring Security default |

### What is not implemented, and what would be needed

| Not implemented | Risk | What a production version would add |
|---|---|---|
| HTTPS | Passwords and session cookies travel unencrypted on the network. | A TLS certificate on the server or a reverse proxy; secure-only cookies; HSTS. |
| Limit on sign-in attempts | Passwords can be guessed by trying many. | Lock the account or slow down after several failures; CAPTCHA on registration. |
| Password reset and email verification | A forgotten password needs an administrator; anyone can register with any address. | A reset link sent by real email; confirm the address before the account is active. |
| Stronger password rules, multi-factor sign-in | Weak but 8-character passwords are accepted. | Check against lists of leaked passwords; a second factor for administrators. |
| Single sign-on | Staff need one more password. | Sign in through the company directory (OAuth 2 / OpenID Connect), which Spring Security supports. |
| Audit of sign-ins and administration | Only incident changes are in the audit trail, not who signed in or who changed a setting. | A second audit log for security events. |
| Database credentials | The development database has the default user and no password. | A real database server with its own account; secrets from the environment, not from a file in the repository. |
| Session timeout tuning, "remember me" | Sessions end after the server's default 30 minutes of inactivity. A page that refreshes itself through live updates counts as activity, so an open dashboard on a busy desk stays signed in. | A timeout chosen for the organisation; an absolute maximum session length for unattended screens. |
| Data protection | No encryption of the database file; no rule for deleting old personal data. | Disk encryption; a retention policy. |

---

## 11. Technology selection: the nine questions

The stack: **Java 25, Spring Boot 4.1 (Spring MVC, Spring Security, Spring Data JPA, Bean Validation),
Thymeleaf, Bootstrap 5, Hibernate, H2, Maven, JUnit 5.**

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
because the server decides what is on the page (a button a user may not press is never sent) and
there is no public API to protect separately. Thymeleaf templates are valid HTML and escape user
text by default. Bootstrap gives a consistent, responsive look without a designer, so the pages work
on a phone. A small amount of plain JavaScript is used only where it helps: about a hundred lines for
live updates (section 8), which replace parts of a page with HTML the server rendered. Pages still work
with JavaScript switched off.

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
and the data still survives a restart because it is stored in a file. Tests use a fresh in-memory H2,
so they are fast and independent. Because all access goes through JPA, moving to PostgreSQL or MySQL
is a change of driver and connection settings, not of code.

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
| Database | PostgreSQL / MySQL | The right choice for production (section 12). Needs an installed server or Docker, which makes the project harder to run for an evaluator. |
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
- Excellent testability: 498 tests run in under a minute with no external services.
- The database can be exchanged without code changes.
- Pages work without JavaScript, which makes them robust and accessible.

**Limitations**

- H2 is not meant for many simultaneous users or large data; a production system needs a database
  server.
- Server-rendered pages reload on each action. Live updates soften this for dashboards and lists, but
  highly interactive features (drag and drop, offline use) would need more client-side code.
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
is the next section.

---

## 12. How the system would scale

Today: one process, one embedded database, comfortable for one organisation (hundreds of users,
tens of thousands of incidents). The steps below are in the order they would become necessary.

| Step | Limit that is reached | Change | Effort |
|---|---|---|---|
| 1 | H2 handles few simultaneous writers and is a single file. | Move to **PostgreSQL**. Only the driver and connection settings change, because all access is through JPA. Replace "Hibernate creates the tables" with versioned migration scripts (Flyway). Tune the connection pool. | Small |
| 2 | Lists load every matching incident. | **Pagination** on the incident lists, search results, audit log and notifications (Spring Data supports it directly). **Indexes** on `status`, `assignee_id`, `reporter_id`, `service_id`, `created_at`, `resolution_due`. | Small |
| 3 | Dashboard and reports count in memory. | Replace with **aggregate queries** in the database (`GROUP BY`), and cache the result for a minute. Cache services and categories, which rarely change. | Small |
| 4 | The SLA check loads every open incident every minute. | Query only incidents whose next deadline is near (`resolution_due < now + x`) and that are not already breached, in batches. | Small |
| 5 | One server is not enough, or must not be a single point of failure. | Run **several instances behind a load balancer**. The application is nearly stateless already; three things are not: (a) sessions in memory → Spring Session with Redis or the database; (b) the SLA job would run on every instance → a shared lock so that one instance runs it (ShedLock); (c) the round-robin strategy remembers the last technician in memory → store it in the database. | Medium |
| 6 | Sending notifications slows down the request and is lost if the server stops at the wrong moment. | Write notifications to an **outbox table** in the same transaction and deliver them from a **message queue** (RabbitMQ or Kafka) with retries. Listeners already receive events and never call each other, so this replaces the dispatcher only. | Medium |
| 6a | Live updates: each server knows only the browsers connected to itself, and every message makes every open dashboard render again. | With several instances, **publish the messages through a shared channel** (Redis publish/subscribe or the message queue of step 6) so that every instance can tell its own browsers. Send at most one message per second per page, and for the busiest pages send the changed row instead of asking for the whole page. | Medium |
| 7 | Duplicate detection compares against all recent incidents of a service in memory. | Use the database's **full-text search**, or a search engine, to fetch only likely candidates. `DuplicateDetector` stays as the final check. | Medium |
| 8 | Reports compete with daily work for the database. | A **read replica** for reports and search. | Medium |
| 9 | Several teams work on the code, or one part needs far more capacity than the rest. | Split along the existing package boundaries into **services**: notifications first (already event-driven), then reporting. Incident, assignment and SLA should stay together because they share one transaction. | Large |
| 10 | Operations. | Container image, health checks and metrics (Spring Boot Actuator), central logging, automatic deployment. | Medium |

What would not change: the domain model, the state classes, the strategies, the listeners and the
exception hierarchy. The design decisions that make this possible were made for OOP reasons, not for
scale: persistence behind repositories, side effects behind events, rules in the model.

---

## 13. What was not implemented

Optional items of the brief that were deliberately left out, so that they are not mistaken for
oversights:

| Item | Status | Reason |
|---|---|---|
| AI-based resolution recommendation, automated root-cause analysis | Not implemented | Would need a knowledge base of past resolutions and a model. The data (resolution notes per category) is stored, so it could be added. |
| Machine-learning classification | Replaced by keyword rules | Explainable, testable, and needs no training data. |
| Service dependency graph | Not implemented | Each incident affects one service; services have no relationships to each other. |
| Multi-level escalation chain | One level only | The brief requires escalation; one level to the administrators covers it. |
| Number of affected users as its own input | Part of the impact level | See Assumptions. |
| Real email, SMS, mobile push | Simulated email outbox; live updates only while a page is open | No mail server in a course project. A real channel is one class implementing `NotificationChannel`. Push to a closed browser or a phone would need a push service. |
| Business hours and holidays in SLA times | Not implemented | SLA times are clock time. |
| File attachments on incidents | Not implemented | Not in the brief. |
| REST API for other systems | Not implemented | The brief allows a web application without one. The services could be exposed through REST controllers without change. |
| Voice or chatbot reporting, cloud deployment, microservices | Not implemented | Outside the scope chosen (section 11, question 5). |
| Password reset, HTTPS, sign-in rate limiting | Not implemented | Documented in section 10. |
