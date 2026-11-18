# UML diagrams {#uml .chapter}

All diagrams are drawn from the real code with PlantUML; their sources are in `docs/diagrams`. This section shows each one at a size that reads on paper. The complete versions, with every use case and class, are in Appendix C, together with the sequence diagram of a report (summarised in 3.2). The design patterns have their own class diagrams in section 5.

## Use case diagram

![Use cases. Three human actors and three system actors. «include» marks a use case that is always part of another: every report is prioritised, classified, checked for duplicates and assigned. «extend» marks an optional one: joining a known problem instead of reporting it again. Complete diagram with all 35 use cases: Appendix C.](../diagrams/overview-use-cases.svg){width=100%}

## Class diagram: the domain model {.landscape}

![The domain model. Inheritance: `Employee`, `Technician` and `Administrator` are kinds of the abstract `User`. Associations with multiplicities: an incident has one reporter, at most one assignee, one service and at most one category. Composition (filled diamond): an incident owns its priority assessment, its history and its attachments. Aggregation (hollow diamond): the users affected by an incident exist on their own. Complete model: Appendix C.](../diagrams/overview-domain-model.svg)

## State machine of an incident

:::: {.split .wide-right}
::: {.col}
The lifecycle of the brief, with two additions: a technician can wait for the reporter (**AWAITING_INFO**, which stops the SLA clock), and a resolved incident can be reopened.

Each status is a class implementing `IncidentState` (State pattern, 5.2). A class lists its legal moves and who may make each one; any other move throws `InvalidStateTransitionException` and changes nothing. All 81 combinations of two statuses are tested: 10 succeed, 71 are refused (TC-05).

::: {.compact}
| Move            | Who              | Effect                            |
|-----------------|------------------|-----------------------------------|
| Acknowledge     | Assigned technician | Response SLA met               |
| Request info    | Assigned technician | Note required; SLA clock stops |
| Reply, resume   | Reporter, technician | Clock restarts; deadline moves back |
| Resolve         | Assigned technician | Resolution note required       |
| Confirm, reopen | Reporter (or administrator) | Verified, or back to work |
| Close           | Technician, administrator | Final; can still be rated |
:::
:::

::: {.col}
![The nine statuses and ten legal moves, with the role that makes each move.](../diagrams/overview-state-machine.svg){width=100%}
:::
::::
