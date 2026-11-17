# 4. System architecture

Quell is one Spring Boot application organised in layers, with two doors: web pages for people and a
REST API for programs. Everything that happens to an incident is announced as an event, and the side
effects (audit trail, notifications, live updates, learning, webhooks) are listeners.

::: {.wide}
![Architecture: the components and how they interact](../diagrams/architecture.png){width=100%}
:::


### Principles

- **Dependencies point downwards.** Controllers call services; services use the domain model and the
  repositories. The domain model knows nothing about HTTP, pages or the API.
- **The rules live in the domain model.** An `Incident` itself refuses an illegal move, so neither a
  page, nor the assistant, nor the REST API can bypass a rule: all three call the same services.
- **One transaction per user action.** Reporting saves the incident, its priority, its assignment, its
  audit entries and its webhook messages together, or nothing at all.
- **Side effects are decoupled by events** (Observer). A service publishes "this happened"; it does not
  know who listens. Live updates, the learning classifier and webhooks were all added later as new
  listeners without touching the services that change incidents.
- **Nothing leaves before the commit.** Notifications, live messages and webhooks are sent after the
  transaction commits, so nobody is told about a change that was rolled back, and a slow receiver
  cannot slow down or break the change itself.
- **Two security chains.** People use pages with a session cookie and CSRF tokens. Programs use the
  API with a key in every request, no session and no cookies; a separate filter chain applies different,
  appropriate rules to each.
- **Server-side rendering with progressive enhancement.** The server renders complete pages; small
  plain-JavaScript files add live updates, the chat, drag-and-drop and the command palette. Every page
  still works without JavaScript.

### Deployment

| Where | How | Database |
|---|---|---|
| A laptop or desktop | One-click installer (Windows `.exe`, macOS `.dmg`, Linux `.deb`) with Java included, built by `jpackage`; the app starts in the background, opens the browser and adds a tray icon. | H2 file in the user's data folder |
| A server or the cloud | `docker compose up` starts Quell and PostgreSQL 17; configured with environment variables, ready for a reverse proxy that provides HTTPS. | PostgreSQL |
| A developer's machine | `./mvnw spring-boot:run` | H2 file in `./data` |
| Phones | Open the address in the browser and install it as an app (PWA). | (the server's) |

Every change pushed to the repository is built, tested and packaged for all three operating systems by
GitHub Actions, and the installers are published on the releases page.
