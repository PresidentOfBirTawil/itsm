# Diagrams

Every diagram is written in [PlantUML](https://plantuml.com) and drawn from the real code: each class,
method and relationship exists in `src/main/java/com/quell`. Each `.puml` file is the source; the `.svg`
(vector, used by the report) and `.png` next to it are the rendered pictures. All of them include
[`style.iuml`](style.iuml), so they share one monochrome style set in Geist.

## Report-sized views

Small, focused diagrams that stay readable on an A4 page. The project report uses these in its main sections.

| Diagram | Source |
|---|---|
| Use cases: what each kind of user does | [overview-use-cases.puml](overview-use-cases.puml) |
| Domain model: main classes, attributes, methods and relationships | [overview-domain-model.puml](overview-domain-model.puml) |
| Incident lifecycle: nine statuses, ten moves | [overview-state-machine.puml](overview-state-machine.puml) |
| State pattern | [pattern-state.puml](pattern-state.puml) |
| Strategy and Template Method | [pattern-strategy.puml](pattern-strategy.puml) |
| Observer | [pattern-observer.puml](pattern-observer.puml) |
| Factory and Strategy for notifications | [pattern-notification.puml](pattern-notification.puml) |
| Exception hierarchy | [pattern-exceptions.puml](pattern-exceptions.puml) |
| Database: the central tables | [overview-database.puml](overview-database.puml) |

## Complete diagrams

| Diagram | Source |
|---|---|
| Architecture: components, layers and integrations | [architecture.puml](architecture.puml) |
| Use cases: every actor and use case | [use-case.puml](use-case.puml) |
| Class diagram 1: the domain model (inheritance, associations, composition) | [class-diagram.puml](class-diagram.puml) |
| Class diagram 2: interfaces and design patterns | [class-diagram-patterns.puml](class-diagram-patterns.puml) |
| Class diagram 3: intelligence, service map and assistant | [class-diagram-intelligence.puml](class-diagram-intelligence.puml) |
| Class diagram 4: integrations, security and files | [class-diagram-integrations.puml](class-diagram-integrations.puml) |
| Incident state machine | [state-machine.puml](state-machine.puml) |
| Sequence: an employee reports an incident | [sequence-report-incident.puml](sequence-report-incident.puml) |
| Entity-relationship diagram 1: the core tables | [er-diagram.puml](er-diagram.puml) |
| Entity-relationship diagram 2: knowledge, service map, files, API and webhooks | [er-diagram-extensions.puml](er-diagram-extensions.puml) |

## Rendering them again

After changing a `.puml` file, render the pictures again with the PlantUML jar (it needs Java,
[Graphviz](https://graphviz.org) and the Geist fonts installed), from this folder:

```bash
java -jar plantuml.jar -tsvg *.puml
```

```bash
java -jar plantuml.jar -tpng -Sdpi=150 *.puml
```

Or open a `.puml` file with the PlantUML plugin of IntelliJ IDEA or VS Code.

## How to read the arrows in the class diagrams

| Arrow | Meaning | Example |
|---|---|---|
| Solid line, hollow triangle | Inheritance: "is a" | `Technician` is a `User` |
| Dashed line, hollow triangle | Implements an interface | `SkillBasedStrategy` is an `AssignmentStrategy` |
| Solid line, open arrowhead | Association: "knows a" | an `Incident` knows its reporter |
| Solid line, filled diamond | Composition: "owns a", the part cannot exist alone | an `Incident` owns its `AuditEntry` history |
| Solid line, hollow diamond | Aggregation: "has a", the part exists on its own | the publisher has its listeners |
| Dashed line, open arrowhead | Uses or creates | `IncidentFactory` creates an `Incident` |
