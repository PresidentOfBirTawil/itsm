# Diagrams

The diagrams are written in [PlantUML](https://plantuml.com). Each `.puml` file is the source and the
`.png` next to it is the rendered picture, ready to paste into the report.

| Diagram | Source | Picture |
|---|---|---|
| Use cases: who can do what | [use-case.puml](use-case.puml) | [use-case.png](use-case.png) |
| Class diagram 1: the domain model (inheritance, associations, composition) | [class-diagram.puml](class-diagram.puml) | [class-diagram.png](class-diagram.png) |
| Class diagram 2: interfaces and design patterns | [class-diagram-patterns.puml](class-diagram-patterns.puml) | [class-diagram-patterns.png](class-diagram-patterns.png) |
| Incident state machine | [state-machine.puml](state-machine.puml) | [state-machine.png](state-machine.png) |
| Sequence: an employee reports an incident | [sequence-report-incident.puml](sequence-report-incident.puml) | [sequence-report-incident.png](sequence-report-incident.png) |
| Entity-relationship diagram (the database tables) | [er-diagram.puml](er-diagram.puml) | [er-diagram.png](er-diagram.png) |
| Architecture: layers and packages | [architecture.puml](architecture.puml) | [architecture.png](architecture.png) |

The class diagram is split in two so that each half stays readable on a page. Both are drawn from the
real classes: every class, method and relationship in them exists in `src/main/java/com/itsm`.

## Rendering them again

After changing a `.puml` file, render the pictures again with the PlantUML jar (it needs Java and
[Graphviz](https://graphviz.org)):

```bash
java -jar plantuml.jar -tpng docs/diagrams/*.puml
```

Or paste the text of a `.puml` file into the online editor at plantuml.com, or open it with the
PlantUML plugin of IntelliJ IDEA or VS Code.

## How to read the arrows in the class diagrams

| Arrow | Meaning | Example |
|---|---|---|
| Solid line, hollow triangle | Inheritance: "is a" | `Technician` is a `User` |
| Dashed line, hollow triangle | Implements an interface | `SkillBasedStrategy` is an `AssignmentStrategy` |
| Solid line, open arrowhead | Association: "knows a" | an `Incident` knows its reporter |
| Solid line, filled diamond | Composition: "owns a", the part cannot exist alone | an `Incident` owns its `AuditEntry` history |
| Solid line, hollow diamond | Aggregation: "has a", the part exists on its own | the publisher has its listeners |
| Dashed line, open arrowhead | Uses or creates | `IncidentFactory` creates an `Incident` |
