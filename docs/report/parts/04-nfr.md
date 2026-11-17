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
