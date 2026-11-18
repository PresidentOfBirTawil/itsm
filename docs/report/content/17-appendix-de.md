# REST API {#appendix-d .appendix}

Everything under `/api/v1`, with a personal key in every request: `Authorization: Bearer qk_…`. A key acts with exactly its owner's permissions. The interactive documentation is at `/api/docs`, and the OpenAPI 3.1 description at `/api/openapi.json`.

::: {.compact}
| Method | Path                              | What it does                                                                                                      |
|--------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------|
| GET    | `/me`                             | The account the key belongs to                                                                                     |
| GET    | `/incidents`                      | The incidents the owner may see; filters `status`, `priority`, `serviceId`, `categoryId`, `slaStatus`, `q`; paging `page`, `size` (at most 100) |
| POST   | `/incidents`                      | Report an incident (an administrator's key may name the reporter); answers 201 with its location                 |
| GET    | `/incidents/{code}`               | One incident                                                                                                       |
| GET    | `/incidents/{code}/timeline`      | Its history                                                                                                        |
| POST   | `/incidents/{code}/notes`         | Add a note; answers 201                                                                                            |
| POST   | `/incidents/{code}/status`        | Move the incident; only the moves the owner may make                                                               |
| GET    | `/status`                         | Overall service status and the probable root cause                                                                 |
| GET    | `/services`                       | Services with their health and dependencies                                                                        |
| GET    | `/categories`                     | The categories                                                                                                     |
| GET    | `/knowledge`                      | Search the knowledge base (`q`)                                                                                    |
:::

Errors use RFC 9457 problem details: 400 invalid input, 401 no or bad key, 403 not allowed, 404 not found, 409 not possible in the current state, 429 too many requests (120 a minute per key; every answer says how many remain), 500 without internal details.

**Webhooks** send the events an administrator chooses (incident reported, technician assigned, status changed, incident resolved, SLA breached, escalated; optionally only P1 and P2) to Slack, to Microsoft Teams as Adaptive Cards, or to any app as JSON signed in the `X-Quell-Signature` header.

# Repository layout {#appendix-e .appendix}

The complete source code is at **https://github.com/PresidentOfBirTawil/quell**; the installers of every release are at **https://github.com/PresidentOfBirTawil/quell/releases/latest**.

```text
.github/workflows/build.yml     tests, installers for three systems, release
docs/                           report, diagrams, test cases, install guide
packaging/                      installer scripts, icons, release notes
src/main/java/com/quell/
  domain/, domain/state/        entities, enumerations, the nine states
  service/                      use cases: report, move, reclassify, join
  assignment/, sla/, priority/  assignment strategies, SLA rules, priority
  intelligence/                 learning, triage, recommendations, duplicates
  status/                       service graph, health, root cause
  assistant/                    the reporting assistant
  knowledge/, attachment/       knowledge base, files on incidents
  event/, notification/, live/  events and listeners, channels, live updates
  api/, webhook/                REST API and keys, webhooks
  security/, web/               sign-in, rules, page controllers
  desktop/, seed/, report/      launcher, sample data, analytics
  config/, search/, error/      settings, search criteria, exceptions
  repository/                   Spring Data JPA repositories
src/main/resources/
  templates/                    42 Thymeleaf pages and fragments
  static/                       design system, scripts, fonts, offline page
  application*.properties       development, desktop and production settings
src/test/java/com/quell/         41 test classes, 619 tests
Dockerfile, docker-compose.yml  server image with PostgreSQL
Start Quell.bat, .command       one-click start from a source checkout
pom.xml, mvnw, mvnw.cmd         Maven build and wrapper
```
