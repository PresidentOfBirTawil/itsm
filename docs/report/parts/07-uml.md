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

