# Demonstration and running {#demo .chapter}

The recorded video, **94_OOP_S1_2025-2026_Project**, shows the running product with its sample data. It demonstrates the three workflows the brief asks for, then administration and the integrations.

## Workflows in the video

::: {.ids .compact}
| #  | Workflow                                                        | What the video shows                                                                                                                                       |
|----|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Employee reports → system prioritises → technician assigned → notification generated | An employee reports a payment outage by chatting with the assistant, and a second problem on the form with a screenshot. P1 is calculated (3 × 3 × 3 = 27, shown on the page), deadlines are set, the skilled technician is assigned, and the technician's bell and a desktop alert appear live. |
| 2  | Technician investigates → SLA monitored → incident resolved → user notified | The technician acknowledges, reads the suggested fixes and the predicted finish time, asks for information (the SLA clock pauses), resolves with a suggested fix; the employee is notified, confirms and rates; the fix becomes a knowledge article. |
| 3  | Multiple incidents → duplicates and related incidents identified → correlated, escalated | Several people report the same outage: duplicates are linked, others join with "I'm affected too" and the impact rises; failures on two services are traced to the network they share; an unacknowledged P1 is escalated to the administrators. |
| 4  | Administration and analytics                                    | The live overview, reports, the audit trail, switching the assignment strategy, services and their dependencies.                                           |
| 5  | Integrations                                                    | A webhook posting to a chat channel, the REST API called with a personal key from its documentation, and the installed app.                               |
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
  <dt>Source code</dt><dd>https://github.com/PresidentOfBirTawil/itsm</dd>
  <dt>Downloads</dt><dd>https://github.com/PresidentOfBirTawil/itsm/releases/latest</dd>
  <dt>Administrator</dt><dd><code>admin@itsm.local</code> / <code>admin12345</code></dd>
  <dt>Technicians</dt><dd><code>tech1@itsm.local</code> … <code>tech4@itsm.local</code> / <code>tech12345</code></dd>
  <dt>Employees</dt><dd><code>emp1@itsm.local</code> … <code>emp4@itsm.local</code> / <code>emp12345</code></dd>
</dl>
```

The sign-in page offers one-click demo sign-in while demo mode is on. To see everything at once, sign in as the administrator and choose System → Settings → Reset and add sample incidents. Short SLA times for a live demonstration are one switch on the same page.
