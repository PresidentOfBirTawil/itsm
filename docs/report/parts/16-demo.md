# 15. Project demonstration

The recorded video ([group number]`_OOP_S1_2025-2026_Project`) shows the application working, with
every member introducing themselves and explaining their modules. It follows the script in
`docs/video-script.md`. The three workflows the brief asks for, and two more:

| # | Workflow | What the video shows |
|---|---|---|
| 1 | **Employee reports → system prioritises → technician assigned → notification generated** | An employee reports a payment outage by chatting with the assistant (with voice), and a second problem on the form with a screenshot. Priority P1 is calculated (3 × 3 × 3 = 27, shown on the page), deadlines are set, the skilled technician is assigned, and the technician's bell and a desktop alert appear live. |
| 2 | **Technician investigates → SLA monitored → incident resolved → user notified** | The technician acknowledges, sees suggested solutions and the predicted finish time, asks for information (the SLA clock pauses), uses a suggested fix to resolve; the employee is notified, confirms and rates; the fix becomes a knowledge article. The SLA risk list and the 75% warning are shown with the short demo SLA times. |
| 3 | **Multiple incidents → duplicates/related incidents identified → correlated/escalated** | Several people report the same outage: duplicates are linked, others join with "I'm affected too" and the impact rises; failures on two services are traced to the network they share (root cause on the status page and the dependency map); an unacknowledged P1 is escalated to the administrators. |
| 4 | Administration and analytics | Live overview, reports, audit trail, switching the assignment strategy, managing services and their dependencies. |
| 5 | Integrations | A webhook posting to a channel, the REST API used with a key from the interactive documentation, the installed app. |

## 15.1 How to run it

| Who | How |
|---|---|
| **Anyone (no technical knowledge)** | Download the installer for your computer from the releases page (`Quell-Setup-Windows.exe`, `Quell-Mac.dmg` or `Quell-Linux.deb`), install, and open Quell from the Start menu or Applications. Java is included. |
| **A server** | `docker compose up --build` in the repository, then open `http://localhost:8080`. |
| **A developer** | Java 25, then `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`) and open `http://localhost:8080`. Tests: `./mvnw test`. |

Sign in with a demo account (section 12.5), or use the one-click buttons on the sign-in page. To see
everything at once, sign in as the administrator and press *System → Settings → Reset and add sample
incidents*.
