# Testing {#testing .chapter}

619 automated tests in 41 test classes, all passing. They run with one command (`./mvnw test`) in about four minutes, need nothing but Java, and run again on every push. Thirty-six of them are documented as test cases; the sixteen below cover every kind the brief asks for, and Appendix B lists all thirty-six with their inputs and test methods.

## Approach

::: {.keys .compact}
| Level              | What it checks                                                                                                                 | Tools                    |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------|--------------------------|
| Unit               | One class on its own, without Spring or a database: the priority formula, every lifecycle move, each strategy, the classifier and similarity mathematics, the dependency graph, file type detection, the lockout clock. | JUnit, AssertJ, Mockito  |
| Integration        | Services with a real in-memory database and transactions: reporting, assignment, SLA monitoring, notifications, escalation, webhooks with a real HTTP receiver. | Spring Boot Test, H2     |
| Web                | Real HTTP requests through the security filters, signed in through the real login form: every page, form, API endpoint, error page and role. | MockMvc                  |
| End to end         | The running application in Chromium: chat, uploads, live updates, alerts, offline page, API documentation, on H2 and on PostgreSQL. | Playwright               |
| Continuous         | All tests on every push, then the installers for three operating systems.                                                     | GitHub Actions           |
:::

Each integration test runs in a transaction that is rolled back, so tests cannot affect each other. Rules that depend on time (SLA, escalation, lockout, rate limits) are tested with a clock the test controls, never by waiting.

```{=html}
<div class="stats kinds">
  <div><div class="value">5</div><div class="label">normal</div></div>
  <div><div class="value">6</div><div class="label">boundary</div></div>
  <div><div class="value">6</div><div class="label">invalid input</div></div>
  <div><div class="value">6</div><div class="label">exception</div></div>
  <div><div class="value">5</div><div class="label">authorization</div></div>
  <div><div class="value">8</div><div class="label">business rule</div></div>
</div>
```

## Key test cases

::: {.ids .compact}
| ID    | Kind           | Scenario                                                                 | Expected result                                                                                             |
|-------|----------------|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| TC-01 | Normal         | An employee reports an outage of the payment application, impact and urgency High. | Stored with a code; 3 × 3 × 3 = 27, P1; due in 5 and 30 minutes; ASSIGNED to the technician with the Application skill. |
| TC-02 | Boundary       | The highest score that is not critical: 17.                              | P2.                                                                                                         |
| TC-03 | Boundary       | The lowest critical score: 18.                                           | P1.                                                                                                         |
| TC-05 | Business rule  | Every pair of statuses: 9 × 9 = 81 attempted moves.                      | The 10 legal moves succeed; the other 71 throw `InvalidStateTransitionException` and change nothing.        |
| TC-07 | Business rule  | Skill-based assignment of a Network incident.                            | Of the two technicians with the skill, the one with fewer open incidents; idle technicians without it are ignored. |
| TC-08 | Exception      | An administrator assigns an incident to a technician who is full.       | `WorkloadExceededException` ("3 of 3"); the incident keeps its technician.                                   |
| TC-10 | Boundary       | The SLA check runs exactly at the response deadline.                     | At risk, not yet breached: a breach starts only after the deadline.                                          |
| TC-12 | Business rule  | The technician waits two days for the reporter's answer.                 | SLA paused throughout; nothing breached; the time used stays at 100 minutes.                                 |
| TC-13 | Exception      | A P1 incident is reported after the P1 policy was deleted.               | `MissingSlaConfigurationException` naming P1; nothing is stored.                                             |
| TC-14 | Authorization  | An employee opens administrator addresses.                               | HTTP 403 for each.                                                                                          |
| TC-17 | Invalid input  | The report form is sent almost empty.                                    | The form again, with an error at each of the five fields; nothing is stored.                                 |
| TC-18 | Exception      | The same person reports the same title for the same service again.      | `DuplicateIncidentException` naming the first incident; another person's report is accepted and linked.      |
| TC-19 | Exception      | The mail server is down when an incident is reported.                    | The incident is saved and assigned; the in-app notifications arrive; only the e-mails are missing.           |
| TC-25 | Business rule  | Payment Application and Corporate Network fail within three hours.       | Corporate Network, which the payment application needs, is named the probable root cause.                   |
| TC-33 | Authorization  | API requests with no key, a wrong key and a revoked key.                 | HTTP 401 as problem JSON; a revoked key stops working at once.                                               |
| TC-36 | Authorization  | Five wrong passwords, then the right one.                                | Sign-in is paused even with the right password; other accounts are unaffected.                               |
:::

Every exception case of the brief has at least one test that raises it and one that checks what the user sees; the mapping is in Appendix B.
