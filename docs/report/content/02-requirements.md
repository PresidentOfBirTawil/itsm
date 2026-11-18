# Requirements {#requirements .chapter}

Three roles, 41 functional requirements, 14 quality requirements and 32 business rules. This section summarises them; Appendix A lists every requirement and rule with the class that implements it.

## User roles

::: {.keys}
| Role          | Can do                                                                                                                                                       | Cannot do                                                                      |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| Employee      | Register. Report by form, chat or voice, with screenshots. Join a known problem. Follow their incidents and answer questions. Confirm, reopen and rate. Search the knowledge base, see service status, create API keys. | See other people's incidents. Make a technician's moves. Open staff pages. |
| Technician    | Work their queue: acknowledge, start, request information, resolve, close. Add notes and files, correct the classification, use suggested fixes, write knowledge articles, search all incidents. | Change incidents assigned to someone else. Assign incidents. Open administrator pages. |
| Administrator | Live dashboard, reports and audit log. Assign, reassign, reopen, close, reclassify. Choose the assignment strategy. Manage users, technicians, services and their dependencies, categories, SLA policies and webhooks. | Report incidents, or make a technician's moves on their behalf.               |
:::

## Functional requirements

::: {.keys}
| Area                    | What the system does                                                                                                                            | IDs              |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| Accounts and access     | Sign-in and self-registration; a dashboard and menu per role; every address protected by role on the server.                                     | FR-01, 02        |
| Reporting               | Form with service, optional category, impact and urgency; readable codes (INC-0001); chat assistant; voice input; attachments; "I'm affected too". | FR-03, 04, 26–28, 34 |
| Priority and SLA        | P1–P4 calculated and explained; deadlines from the SLA policy; checked every minute with warning, breach and a pause while waiting; escalation.   | FR-05, 06, 13, 15 |
| Assignment              | Automatic, with a strategy switched at runtime; manual assignment refuses technicians who are unavailable or full.                                | FR-07, 20        |
| Lifecycle               | Nine statuses and ten legal moves, each owned by a role; notes, information requests, resolution, confirmation, rating, reclassification.        | FR-08–10, 21     |
| Audit and notification  | An audit trail that cannot be edited; in-app, e-mail, live and desktop notifications; webhooks to Slack, Microsoft Teams or any app.              | FR-11, 12, 35, 39 |
| Intelligence            | Duplicate detection; keyword and machine-learning classification; suggested fixes; predicted finish time; root cause across dependent services.   | FR-14, 16, 30–33 |
| Search and reporting    | Search by any combination of code, status, priority, category, service, technician, SLA state and date range; dashboard; reports; audit log.     | FR-17, 18, 22, 23 |
| Administration          | Users, technicians, services, categories and SLA policies; reset to realistic sample data.                                                       | FR-19, 24        |
| Product                 | Live updates; knowledge base; installable app; REST API with personal keys; command palette and themes; installers and a server image.          | FR-25, 29, 36–38, 40, 41 |
:::

## Non-functional requirements

::: {.ids .compact}
| ID     | Quality           | How it is met                                                                                                  |
|--------|-------------------|----------------------------------------------------------------------------------------------------------------|
| NFR-01 | Security          | Every address and every object checked on the server; BCrypt; sign-in lockout; strict content security policy (section 10). |
| NFR-02 | Maintainability   | Categories are data; a new strategy, channel or listener is a new class found by its interface.                 |
| NFR-03 | Reliability       | Side effects run after the commit and are isolated from each other; webhooks are retried from an outbox.         |
| NFR-04 | Data integrity    | One transaction per user action; any exception rolls the whole action back.                                    |
| NFR-05 | Auditability      | Audit entries are written in the same transaction as the change and are never updated or deleted.               |
| NFR-06 | Usability         | Only permitted actions are shown; feedback after every action; works on a phone; dark and light themes.         |
| NFR-07 | Persistence       | An H2 file on a desktop, PostgreSQL on a server.                                                                |
| NFR-08 | Performance       | Indexed lookups, related objects fetched in one query, paged API lists; the SLA check takes milliseconds.         |
| NFR-09 | Testability       | Rules live in plain classes; 617 automated tests run in about four minutes.                                     |
| NFR-10 | Portability       | Installers with Java included for Windows, macOS and Linux; a container image for servers.                       |
| NFR-11 | Error tolerance   | No stack trace ever reaches a user; API errors use RFC 9457 problem details.                                     |
| NFR-12 | Upgradability     | New columns are nullable and enums are stored as text, so a new version runs on an existing database.            |
| NFR-13 | Interoperability  | A versioned REST API described with OpenAPI; webhooks in the Slack, Teams and signed-JSON formats.               |
| NFR-14 | Privacy           | No incident text leaves the installation unless an administrator connects a webhook.                             |
:::

## Key business rules

The rules the brief asks for, as Quell enforces them. All 32 are in Appendix A.

::: {.ids .compact}
| ID    | Rule                                                                                                                                                  |
|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| BR-01 | Priority score = impact × urgency × service criticality, each from 1 to 3. 18–27 is P1, 9–17 is P2, 4–8 is P3, 1–3 is P4.                                |
| BR-02 | Respond / resolve within: P1 5 min / 30 min · P2 15 min / 2 h · P3 1 h / 8 h · P4 4 h / 24 h, counted from the report.                                   |
| BR-03 | NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED, plus IN_PROGRESS ↔ AWAITING_INFO and reopening. Nothing else. |
| BR-04 | Each move belongs to a role: only the assigned technician acknowledges, starts and resolves; only the reporter confirms or reopens.                    |
| BR-07 | A technician receives an incident only if enabled, available and below their maximum workload.                                                       |
| BR-11 | At 75% of the allowed time an incident is at risk and one warning is sent; after the deadline it is breached, recorded once.                           |
| BR-12 | While the technician waits for the reporter, the SLA clock stops and the resolution deadline moves back by the time waited.                           |
| BR-14 | A P1 incident not acknowledged by its response deadline is escalated to the administrators, once.                                                     |
| BR-15 | A report at least 40% similar to an open incident on the same service from the last 24 hours is linked to it as a possible duplicate.                  |
| BR-17 | An employee sees only their own incidents; technicians and administrators see all.                                                                    |
| BR-30 | Five wrong passwords for one address within 15 minutes pause sign-in for that address for 15 minutes.                                                |
:::
