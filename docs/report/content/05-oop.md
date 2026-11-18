# Object-oriented design {#oop .chapter}

The domain is modelled as objects with behaviour, not as tables with procedures around them. An `Incident` guards its own lifecycle and deadlines, each kind of user decides for itself what it may see and do, and every policy that can vary sits behind an interface: the lifecycle step, the assignment rule, the priority rule, the delivery channel and the reaction to a change.

## OOP concepts in the code

::: {.keys}
| Concept                    | Where                                                                                     | How it shows                                                                                                    |
|----------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Encapsulation              | `Incident`                                                                                | Private fields and no `setStatus`: the status changes only through `transitionTo`, `prioritise` and `assignTo`, which check the rules first. |
| Abstraction                | Abstract `User`, `QuellException`; interfaces `IncidentState`, `AssignmentStrategy`, `PriorityCalculator`, `NotificationChannel`, `IncidentEventListener` | Callers depend on "a strategy" or "a channel", never on which one.                     |
| Inheritance                | `Employee`, `Technician`, `Administrator` extend `User`; three strategies extend `AbstractAssignmentStrategy`; eleven exceptions extend `QuellException` | Used only for true "is a" relations. All users share one table (single-table inheritance). |
| Polymorphism               | `user.canWorkOn(incident)`, `state.transitions()`, `strategy.choose(…)`                     | "Is it mine?" for an employee, "is it assigned to me?" for a technician, "yes" for an administrator, with no `if (role == …)` anywhere. |
| Interfaces                 | The five above                                                                            | Each has two or more implementations that can be exchanged.                                                     |
| Association                | Incident → reporter, assignee, service, category; technician ↔ skills                     | Objects that know each other but live independently.                                                           |
| Composition                | An incident owns its `PriorityAssessment`, audit history and attachments                  | The parts have no meaning without the incident.                                                                 |
| Aggregation                | The publisher has its listeners; `AssignmentService` has its strategies                   | The parts exist on their own and are shared.                                                                    |
| Exceptions                 | One class per broken rule, all extending `QuellException` (5.5)                            | Each carries a message written for the user.                                                                    |
| Collections and generics   | `Set<Category>` skills, `List<AssignmentStrategy>`, `Map<Role, String>`, `Optional<…>`; generic `SimilarityIndex<T>`, `NaiveBayesClassifier<L>` | Sets where duplicates make no sense, ordered lists for queues, maps for lookups, `Optional` instead of `null`; the learning algorithms are written once for any item or label. |
| Immutable values           | Records `PriorityAssessment`, `SlaTarget`, `Transition`, `IncidentEvent`, `TechnicianLoad` | Created once and never changed, so they are safe to share.                                                    |
| Enums with behaviour       | `Status.isOpen()`, `Impact.weight()`, the assistant's `Step` with its own `enter` and `handle` | Fixed sets of values that carry their own rules.                                                         |
:::

## State: the incident lifecycle

What an incident may do depends entirely on its status. Without the pattern, every operation would begin with a `switch (status)`. With it, each status is a small class that lists its legal moves, who may make them, and what happens on entering and leaving: `AwaitingInfoState` stops the SLA clock and restarts it on leaving, `ClosedState` allows no move at all. `Incident.transitionTo` asks its current state and refuses anything else. A new status is a new class.

![State pattern. `Incident` delegates each move to the state object for its status; the nine state classes implement one interface.](../diagrams/pattern-state.svg){width=94%}

## Strategy and Template Method: assignment

There are several reasonable ways to choose a technician, and the administrator switches between them on the settings page while the system runs. `AssignmentService` receives every strategy through dependency injection and uses the active one. The abstract parent fixes the shared skeleton in a `final` method, `choose()`: leave out technicians who are disabled, unavailable or full, call the subclass's `pick()`, and throw `TechnicianUnavailableException` if nobody is left. No strategy can forget the workload rule, and a fourth rule is one new class. The priority rule is a second Strategy: `IncidentService` knows only `PriorityCalculator`.

![Strategy and Template Method. Three assignment rules behind one interface; the abstract class fixes the steps they share.](../diagrams/pattern-strategy.svg){width=88%}

## Observer, Factory and Strategy: reacting to a change

A service that changes an incident publishes an `IncidentEvent` and does not know who listens. Five listeners react: the audit trail, notifications, live screen updates, the learning classifier and webhooks. Live updates, learning and webhooks were all added after the core was finished, and not one line of the incident services changed.

![Observer. The publisher holds a list of listeners that Spring fills with every class implementing `IncidentEventListener`.](../diagrams/pattern-observer.svg){width=72%}

:::: {.split .wide-left}
::: {.col}
Notifications use two more patterns. A **factory** decides which channels a message goes through (everything goes in-app and to the live bell; only some kinds are e-mailed), so the dispatcher never decides itself. Each **channel** is a strategy for delivering a message; SMS or a chat app would be one more class.

Delivery happens after the commit, each channel in its own transaction and error handling, so a failing mail server never undoes an incident or blocks the in-app message (TC-19).
:::

::: {.col}
![Factory and Strategy for notification channels.](../diagrams/pattern-notification.svg){width=100%}
:::
::::

## Other patterns

::: {.keys}
| Pattern               | Where                                                       | Why                                                                                                      |
|-----------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Factory               | `IncidentFactory.create(…)`                                 | One place that builds a valid incident, in status NEW and with its code; nothing else can create a half-finished one. |
| Repository            | `IncidentRepository`, `UserRepository` and 15 more         | The code asks for objects and never writes SQL; the database can change without touching a service.     |
| Specification         | `IncidentSpecifications.matching(criteria)`                 | Nine optional search criteria combined as small conditions, instead of one query for each of 512 combinations. |
| Decorator             | `LockoutAuthenticationProvider`                             | Adds "pause after five wrong passwords" around Spring's password check without changing it.              |
| Facade                | `ReportingAssistant`, `StatusService`                       | One simple entry point in front of several services (recognition, triage, knowledge, incidents; graph, health, root cause). |
| Transactional outbox  | `WebhookListener`, `WebhookDelivery`, `WebhookDispatcher`   | Webhook messages are saved with the change and sent after the commit: never sent for a rollback, never lost when a receiver is down. |
| State, second use     | The assistant's `Step` enum                                 | Each step of the conversation knows what to ask and how to read the answer.                              |
| Dependency injection  | Every service and controller constructor                    | Classes receive what they need, which is what lets a test hand in a failing channel or a fixed clock.    |
:::

Two patterns were considered and left out. A hand-written **Singleton** would duplicate what Spring already guarantees and make testing harder. A **Chain of Responsibility** for escalation would be a chain of one link, since the brief's escalation has one level in this design.

## Exception handling {#exceptions}

All of the application's own exceptions extend one abstract class, `QuellException`, and carry a message written for the user. One `@ControllerAdvice`, `ErrorHandler`, turns them into pages for every controller, and `ApiExceptionHandler` turns them into RFC 9457 problem JSON for the API, so no controller has its own error code and no stack trace ever reaches a browser.

![The exception hierarchy: one class per kind of broken rule.](../diagrams/pattern-exceptions.svg){width=100%}

::: {.keys .compact}
| Case in the brief         | Handled by                                         | What the user sees                                                                 |
|---------------------------|----------------------------------------------------|------------------------------------------------------------------------------------|
| Invalid login             | Spring Security's `BadCredentialsException`        | "Wrong email or password." on the sign-in page                                      |
| Unauthorized operation    | `UnauthorizedOperationException`, `AccessDeniedException` | The 403 page with the reason; nothing changes                              |
| Invalid incident          | `IncidentNotFoundException`                        | The 404 page                                                                       |
| Invalid state transition  | `InvalidStateTransitionException`                  | "INC-0007 cannot go from New to Closed"; nothing changes                           |
| Technician unavailable    | `TechnicianUnavailableException`                   | A message; automatic assignment leaves the incident waiting and warns administrators |
| Workload exceeded         | `WorkloadExceededException`                        | "… already has 3 of 3 open incidents."                                              |
| Service not found         | `ServiceNotFoundException`                         | The 404 page                                                                       |
| Missing SLA configuration | `MissingSlaConfigurationException`                 | A message naming the priority; nothing is saved                                    |
| Duplicate incident        | `DuplicateIncidentException`                       | A message with the code of the first report                                        |
| Database failure          | Spring's `DataAccessException`                     | A 503 page; the transaction is rolled back                                         |
| Network failure           | Any exception inside a notification channel or webhook | Nothing: the incident is saved, other channels deliver, webhooks are retried    |
| Invalid user input        | Bean Validation, `InvalidInputException`           | The form again, with a message at each wrong field and the typed values kept       |
:::
