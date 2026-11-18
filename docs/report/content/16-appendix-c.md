# Complete diagrams {#appendix-c .appendix}

The ten complete diagrams, each on its own page. They are vector graphics: zoom in to read the smallest labels. Their PlantUML sources, and the report-sized versions used in sections 4, 5 and 8, are in `docs/diagrams`.

::: {.keys .compact}
| Diagram                                   | Shows                                                                                         | Source                            |
|-------------------------------------------|-----------------------------------------------------------------------------------------------|-----------------------------------|
| Architecture                              | Every component, the two security chains, the event listeners and the integrations             | `architecture.puml`               |
| Use cases                                 | All 35 use cases of the four human and three system actors                                    | `use-case.puml`                   |
| Class diagram 1: the domain model         | Every entity with attributes and methods, the enumerations and all relationships              | `class-diagram.puml`              |
| Class diagram 2: interfaces and patterns  | State, Strategy, Template Method, Observer, Factory and the exception hierarchy together       | `class-diagram-patterns.puml`     |
| Class diagram 3: intelligence             | The generic learning classes, the features built on them, the service graph and the assistant | `class-diagram-intelligence.puml` |
| Class diagram 4: integrations             | Webhooks, API keys and their filter, the sign-in lockout and attachments                      | `class-diagram-integrations.puml` |
| State machine                             | Every status, move, guard and effect on the SLA clock                                          | `state-machine.puml`              |
| Sequence: reporting an incident           | Every call, in order, from the form to the notifications after the commit                      | `sequence-report-incident.puml`   |
| Entity-relationship 1: the core           | Users, incidents, catalogue, SLA, audit trail and notifications, every column                  | `er-diagram.puml`                 |
| Entity-relationship 2: the extensions     | Knowledge base, service map, files, API keys and webhooks                                      | `er-diagram-extensions.puml`      |
:::

## Architecture {.landscape}

![Architecture: components, layers and integrations.](../diagrams/architecture.svg)

## Use cases {.fullpage}

![Use cases: every actor and use case.](../diagrams/use-case.svg)

## Class diagram 1: the domain model {.landscape}

![The domain model, complete.](../diagrams/class-diagram.svg)

## Class diagram 2: interfaces and design patterns {.landscape}

![Interfaces and design patterns, complete.](../diagrams/class-diagram-patterns.svg)

## Class diagram 3: intelligence, service map and assistant {.landscape}

![Intelligence, the service map and the reporting assistant.](../diagrams/class-diagram-intelligence.svg)

## Class diagram 4: integrations, security and files {.landscape}

![Integrations, security and files.](../diagrams/class-diagram-integrations.svg)

## State machine {.fullpage}

![The incident state machine, complete.](../diagrams/state-machine.svg)

## Sequence: reporting an incident {.landscape}

![Reporting an incident: every call, in one transaction, and what happens after the commit.](../diagrams/sequence-report-incident.svg)

## Entity-relationship 1: the core tables {.landscape}

![Core tables: users, incidents, catalogue, SLA policies, audit trail and notifications.](../diagrams/er-diagram.svg)

## Entity-relationship 2: the extensions {.landscape}

![Knowledge base, service map, attachments, API keys and webhooks.](../diagrams/er-diagram-extensions.svg)
