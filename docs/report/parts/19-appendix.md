# Appendix A. REST API endpoints

All under `/api/v1`, with `Authorization: Bearer qk_…`. Full description: `/api/docs` (interactive) and
`/api/openapi.json` (OpenAPI 3.1).

| Method | Path | What it does |
|---|---|---|
| GET | `/me` | Who the key belongs to |
| GET | `/incidents` | List the incidents the owner may see; filters `status`, `priority`, `serviceId`, `categoryId`, `slaStatus`, `q`; paging `page`, `size` (≤ 100) |
| POST | `/incidents` | Report an incident (administrators' keys may give `reporterEmail`) → 201 with `Location` |
| GET | `/incidents/{code}` | One incident |
| GET | `/incidents/{code}/timeline` | Its history |
| POST | `/incidents/{code}/notes` | Add a note → 201 |
| POST | `/incidents/{code}/status` | Change the status (only the moves the owner may make) |
| GET | `/status` | Overall service status and the probable root cause |
| GET | `/services` | Services with health and dependencies |
| GET | `/categories` | The categories |
| GET | `/knowledge` | Search the knowledge base (`q`) |

# Appendix B. Repository layout

```
.github/workflows/build.yml     CI: tests, installers for 3 operating systems, release
docs/                           this report, diagrams (PlantUML), test cases, video script, install guide
packaging/                      installer scripts and icons
src/main/java/com/itsm/
  domain/  domain/state/        entities, enums, the nine incident states
  service/ assignment/ sla/ priority/        core use cases and rules
  intelligence/ intelligence/text/           machine learning, triage, recommendations, duplicates
  status/                       service graph, health, root cause
  assistant/                    the reporting assistant (chat)
  knowledge/ attachment/        knowledge base, file attachments
  event/ notification/ live/    events, notifications, live updates
  api/ webhook/                 REST API and API keys, webhooks
  security/ web/                sign-in, rules, page controllers
  desktop/ config/ seed/ report/ search/ repository/ error/
src/main/resources/templates/   41 Thymeleaf pages
src/main/resources/static/      CSS design system, JavaScript, service worker, images
src/test/java/com/itsm/         43 test classes, 617 tests
Dockerfile, docker-compose.yml  server deployment with PostgreSQL
```
