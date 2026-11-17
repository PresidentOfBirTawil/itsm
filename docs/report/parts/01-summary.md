# Summary {.unnumbered}

**Quell** is a complete IT service management product: employees report IT problems in seconds (by form,
by chatting with an assistant, or by voice), and the system prioritises, routes, tracks and escalates
them automatically, learns from every resolved incident, and tells everyone what is going on. It was
designed as a real product rather than a classroom exercise: it installs with a one-click installer on
Windows, macOS and Linux, runs as a container with PostgreSQL on a server, installs as an app on a
phone, and connects to other tools through a documented REST API and webhooks for Slack and Microsoft
Teams.

At its core is an object-oriented domain model in Java 25: an `Incident` that enforces its own nine-status
lifecycle through the **State** pattern, three interchangeable assignment rules behind a **Strategy**
interface, an **Observer**-based event system that keeps the audit trail, notifications, live updates,
the learning classifier and webhooks out of the business logic, and a single exception hierarchy that
turns every broken rule into a clear message.

| | |
|---|---|
| **Mandatory modules** | All implemented: three roles with role-specific interfaces, incident reporting, automatic priority, SLA policies and monitoring, automatic and manual assignment, nine-status lifecycle, audit trail, notifications, search, dashboards, reports, exception handling, security. |
| **Innovation challenges of the brief** | 15 of the 16 listed (all except microservices, which was rejected for good reasons; section 11, question 5): machine-learning classification, resolution recommendation, similarity detection, automated root-cause analysis, predictive SLA, service dependency graph, real-time notifications, desktop and mobile alerts, voice reporting, chatbot reporting, cloud deployment, event-driven architecture, real-time dashboard, analytics, automated escalation. |
| **Beyond the brief** | REST API with API keys and OpenAPI documentation, webhooks to Slack, Teams and any app, screenshot attachments, a public status page, a knowledge base, an installable app (PWA), native installers, Docker and PostgreSQL, dark and light themes, a command palette. |
| **Size** | 222 Java classes (16,700 lines) in 23 packages, 41 page templates, 21 database tables. |
| **Quality** | 617 automated tests in 43 test classes, all passing; checked end to end in a real browser and on PostgreSQL; built, tested and packaged for three operating systems by continuous integration on every change. |
