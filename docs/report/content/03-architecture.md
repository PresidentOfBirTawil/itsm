# Architecture {#architecture .chapter}

One Spring Boot application in five layers, with two doors: web pages for people and a REST API for programs. Everything that happens to an incident is published as an event, and the side effects (audit trail, notifications, live updates, learning, webhooks) are listeners.

```{=html}
<figure class="arch-figure">
<div class="arch">
  <div class="row"><div class="lbl">Clients</div><div class="boxes c4">
    <div class="box soft"><b>Browser</b><span>pages, live updates, chat, voice</span></div>
    <div class="box soft"><b>Installed app</b><span>phone (PWA), desktop installer</span></div>
    <div class="box soft"><b>Other systems</b><span>REST API with personal keys</span></div>
    <div class="box soft"><b>Chat apps</b><span>Slack, Teams, any URL (webhooks)</span></div>
  </div></div>
  <div class="row"><div class="lbl">Security</div><div class="boxes c2">
    <div class="box"><b>Web filter chain</b><span>session, CSRF token, roles, sign-in lockout, CSP</span></div>
    <div class="box"><b>API filter chain</b><span>API key, 120 requests a minute, problem JSON, no cookies</span></div>
  </div></div>
  <div class="row"><div class="lbl">Presentation</div><div class="boxes c4">
    <div class="box"><b>Page controllers</b><span>Thymeleaf templates per role</span></div>
    <div class="box"><b>REST controllers</b><span>/api/v1, OpenAPI documentation</span></div>
    <div class="box"><b>Live stream</b><span>/live, server-sent events</span></div>
    <div class="box"><b>Assistant</b><span>conversation steps</span></div>
  </div></div>
  <div class="row"><div class="lbl">Services</div><div class="boxes c5">
    <div class="box"><b>IncidentService</b><span>report, move, reclassify, join</span></div>
    <div class="box"><b>AssignmentService</b><span>three strategies, one active</span></div>
    <div class="box"><b>SlaService, SlaMonitor</b><span>deadlines; check every minute</span></div>
    <div class="box"><b>Intelligence</b><span>classifier, similarity, forecast, root cause</span></div>
    <div class="box"><b>Knowledge, status, reports</b><span>articles, service map, analytics</span></div>
  </div></div>
  <div class="row"><div class="lbl">Events</div><div class="boxes c6">
    <div class="box dark"><b>Event publisher</b><span>one event per change</span></div>
    <div class="box"><b>Audit trail</b><span>same transaction</span></div>
    <div class="box"><b>Notifications</b><span>in-app, e-mail, live</span></div>
    <div class="box"><b>Live updates</b><span>open screens refresh</span></div>
    <div class="box"><b>Classifier</b><span>learns from changes</span></div>
    <div class="box"><b>Webhooks</b><span>outbox, signed, retried</span></div>
  </div></div>
  <div class="row"><div class="lbl">Domain model</div><div class="boxes c4">
    <div class="box"><b>Incident</b><span>nine state classes guard the lifecycle</span></div>
    <div class="box"><b>Users</b><span>Employee, Technician, Administrator</span></div>
    <div class="box"><b>Catalogue</b><span>services and dependencies, categories, SLA policies</span></div>
    <div class="box"><b>Rules as exceptions</b><span>one class per broken rule</span></div>
  </div></div>
  <div class="row"><div class="lbl">Persistence</div><div class="boxes c3">
    <div class="box"><b>Repositories</b><span>Spring Data JPA, Hibernate</span></div>
    <div class="box soft"><b>H2</b><span>desktop installations, tests</span></div>
    <div class="box soft"><b>PostgreSQL</b><span>servers, Docker</span></div>
  </div></div>
</div>
<figcaption>Layers and components. Requests enter at the top; each layer calls only the layers below it. The component diagram with every class is in Appendix C.</figcaption>
</figure>
```

## Principles

- **Dependencies point downwards.** Controllers call services; services use the domain model and the repositories. The domain model knows nothing about HTTP, pages or the API.
- **The rules live in the domain model.** An `Incident` itself refuses an illegal move, so neither a page, nor the assistant, nor the REST API can bypass a rule: all three call the same services.
- **One transaction per user action.** Reporting saves the incident, its priority, its assignment, its audit entries and its webhook messages together, or nothing at all.
- **Side effects are listeners, and run after the commit.** Nobody is told about a change that was rolled back, and a slow receiver cannot slow down or break the change.
- **Two security chains.** People use pages with a session cookie and CSRF tokens; programs use the API with a key on every request and no session.
- **Pages first, scripts second.** The server renders complete pages; nine small scripts add live updates, the chat, uploads and the command palette, and every page still works without JavaScript.

## Reporting an incident, step by step

Inside one transaction, in `IncidentService.report`; the sequence diagram is in Appendix C.

```{=html}
<div class="steps">
  <div><span class="k">1 · Refuse repeats</span>The same person reporting the same thing again gets <code>DuplicateIncidentException</code>.</div>
  <div><span class="k">2 · Classify</span><code>CategorySuggester</code> (keywords), then <code>CategoryClassifier</code> if no keyword matches.</div>
  <div><span class="k">3 · Create</span><code>IncidentFactory</code> builds the incident in status NEW and gives it its code.</div>
  <div><span class="k">4 · Link duplicates</span><code>DuplicateDetector</code> links it to a similar open incident.</div>
  <div><span class="k">5 · Prioritise</span><code>PriorityCalculator</code> scores it; the SLA policy sets both deadlines.</div>
  <div><span class="k">6 · Assign</span>The active <code>AssignmentStrategy</code> picks a technician who can take more.</div>
  <div><span class="k">7 · Record, commit</span>Each step is an <code>IncidentEvent</code>; audit entries and webhook messages commit with it.</div>
  <div><span class="k">8 · After commit</span>Notifications and webhooks go out; open screens refresh.</div>
</div>
```

## Deployment

::: {.keys}
| Where              | How                                                                                                                                  | Database                      |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|
| Laptop or desktop  | One-click installer (Windows `.exe`, macOS `.dmg`, Linux `.deb`) with Java included. Quell starts in the background, opens the browser and adds a tray icon. | H2 file in the user's data folder |
| Server or cloud    | `docker compose up` starts Quell and PostgreSQL 17, configured with environment variables, ready to sit behind an HTTPS proxy.          | PostgreSQL                    |
| Developer          | `./mvnw spring-boot:run`, with Java 25 installed.                                                                                     | H2 file in `./data`           |
| Phone              | Open the server's address and install Quell as an app (progressive web app).                                                         | The server's                  |
:::

Every push to the repository is built, tested and packaged for the three operating systems by GitHub Actions, and the installers are published as a release.
