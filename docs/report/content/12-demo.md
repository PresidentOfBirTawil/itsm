# Demonstration and running {#demo .chapter}

The recorded video, **94_OOP_S1_2025-2026_Project**, demonstrates the running product with its sample data: the three workflows the brief asks for, then a tour of the remaining features.

## Workflows in the video

::: {.ids .compact}
| #  | Workflow                                                        | What the video shows                                                                                                                                       |
|----|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Employee reports → system prioritises → technician assigned → notification generated | An employee reports the brief's payment scenario on the form, which shows the known problems with that service as she types. The category is chosen from her words, P1 is calculated (3 × 3 × 3 = 27, shown on the page), the five- and thirty-minute deadlines are set, a finish time is predicted, and the technician with the right skill is assigned; her screen updates live. |
| 2  | Technician investigates → SLA monitored → incident resolved → user notified | The technician acknowledges, which meets the response target, and asks the employee a question, which pauses the SLA clock. The employee answers; the technician applies a fix suggested from similar past incidents and resolves within the SLA. The employee confirms and rates the fix, and the technician publishes it as a knowledge article. |
| 3  | Multiple incidents → duplicates and related incidents identified → correlated, escalated | A second employee describes the problem to the chat assistant, which recognises the service and adds her to the known outage instead of creating a duplicate. The status page traces the failures of two services to the network they share, and the administrator's live overview shows the root cause, an unacknowledged P1 escalated automatically, and the incidents most likely to breach next. |
| 4  | Feature tour                                                    | Reports and analytics, the audit log, switching the assignment strategy, services and their dependencies, the knowledge base, webhooks to Slack, Microsoft Teams or any app, the REST API documentation, the command palette, and the dark and light themes. |
:::

## Running Quell

::: {.keys .compact}
| Who                     | How                                                                                                                                                       |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Anyone                  | Download the installer for the computer from the releases page (`Quell-Setup-Windows.exe`, `Quell-Mac.dmg` or `Quell-Linux.deb`), install it and open Quell. Java is included. |
| A server                | In the repository: `docker compose up --build`, then open `http://localhost:8080`.                                                                         |
| A developer             | With Java 25: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`), then open `http://localhost:8080`. Tests: `./mvnw test`.                      |
:::

```{=html}
<dl class="facts wide">
  <dt>Source code</dt><dd>https://github.com/PresidentOfBirTawil/quell</dd>
  <dt>Downloads</dt><dd>https://github.com/PresidentOfBirTawil/quell/releases/latest</dd>
  <dt>Administrator</dt><dd><code>admin@quell.local</code> / <code>admin12345</code></dd>
  <dt>Technicians</dt><dd><code>tech1@quell.local</code> … <code>tech4@quell.local</code> / <code>tech12345</code></dd>
  <dt>Employees</dt><dd><code>emp1@quell.local</code> … <code>emp4@quell.local</code> / <code>emp12345</code></dd>
</dl>
```

The sign-in page offers one-click demo sign-in while demo mode is on. To see everything at once, sign in as the administrator and choose System → Settings → Reset and add sample incidents. Short SLA times for a live demonstration are one switch on the same page.
