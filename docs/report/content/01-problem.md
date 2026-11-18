# Problem and solution {#problem .chapter}

When an IT service fails, the people affected need one place to report it, and the IT team needs to know which failure to work on first. Quell is that place: one product that takes an incident from the first report to its closure, with every decision explained and recorded.

## The problem

Without a system, reports arrive by e-mail, chat and phone. The same outage is reported many times, nobody knows who is working on what, a critical outage waits behind a broken mouse, and a missed deadline is noticed only when a user complains.

## Stakeholders

::: {.keys}
| Stakeholder              | What they need from the system                                                                                   |
|--------------------------|------------------------------------------------------------------------------------------------------------------|
| Employee (reporter)      | Report in under a minute; see who has the problem and what is happening; be told when it is fixed; say "it is not fixed". |
| Technician               | One queue ordered by deadline; everything about an incident on one page; no more work than they can handle.      |
| Service desk manager     | The whole desk at a glance: open, critical, late, overloaded. Configure services, SLAs, categories, technicians and the assignment rule without a programmer. |
| IT management, auditors  | Evidence: SLA compliance, resolution times, workload, and a history of every change that cannot be edited.        |
| Service owners           | Critical services, such as payments and sign-in, handled before minor ones.                                      |
| Developers               | A design in which a new category, rule, channel or status needs no change to unrelated code.                     |
:::

## Challenges, and how Quell answers them

::: {.keys}
| Challenge                        | Quell's answer                                                                                                     |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Reports scattered over channels  | One place to report: a form, a chat assistant, voice input, or the REST API for other systems.                      |
| Priority by whoever shouts loudest | A documented formula, impact × urgency × service criticality, shown on every incident (6.1).                       |
| Slow, uninformed assignment      | Automatic assignment by skill, by workload or in turn; the administrator switches the rule while the system runs.   |
| SLAs checked after the fact      | Deadlines set when the incident is reported and checked every minute: a warning at 75%, breaches recorded, unacknowledged P1 incidents escalated. |
| Duplicate reports                | Similar reports linked automatically; "I'm affected too" instead of a second report.                                |
| Incomplete history               | Every action written to an audit trail in the same transaction as the change.                                       |
| Recurring problems               | Fixes suggested from similar past incidents; a knowledge base fed by resolved incidents; root-cause analysis. |
:::

## Proposed solution

A web product with three roles (employee, technician, administrator), used in a browser, as an installed app on a phone or computer, or by other systems through a REST API. Reporting an incident starts one transaction that classifies it, sets its priority and deadlines, links possible duplicates, assigns a technician and records every step; after it commits, people and connected apps are notified and every open screen updates. The brief's fourteen-step scenario maps onto Quell as follows.

```{=html}
<div class="steps">
  <div><span class="k">1 · Create</span>The factory builds a valid incident and gives it a code such as INC-0042.</div>
  <div><span class="k">2 · Classify</span>Category from the reporter, or from keyword rules, then a learning classifier.</div>
  <div><span class="k">3 · Affected service</span>Chosen on the form, or recognised from the words in the chat.</div>
  <div><span class="k">4 · Business impact</span>Impact level, raised when more people say they are affected.</div>
  <div><span class="k">5 · Priority</span>Impact × urgency × service criticality gives P1 to P4.</div>
  <div><span class="k">6 · SLA</span>Response and resolution deadlines from the policy for that priority.</div>
  <div><span class="k">7 · Suitable technicians</span>Enabled, available, below their workload limit, with the skill.</div>
  <div><span class="k">8 · Assign</span>The strategy the administrator chose picks one technician.</div>
  <div><span class="k">9 · Notify</span>In the app, by e-mail, as a desktop alert, live on open screens, by webhook.</div>
  <div><span class="k">10 · Track</span>Nine statuses; only the ten legal moves are possible.</div>
  <div><span class="k">11 · Monitor SLA</span>Checked every minute: at risk, breached, paused, escalated.</div>
  <div><span class="k">12 · Record</span>Every action in the audit trail, with who, when and the status change.</div>
  <div><span class="k">13 · Close</span>The reporter confirms the fix; a technician or administrator closes.</div>
  <div><span class="k">14 · Report</span>Dashboard, reports, audit log, service status and analytics.</div>
</div>
```

## Assumptions

- One organisation uses one installation; there is no separation between tenants.
- Every incident concerns exactly one service from the catalogue. Services may depend on other services.
- Employees register themselves; technicians and administrators are created by an administrator.
- SLA times are clock time, 24 hours a day, without business hours or holidays.
- The impact level stands for the number of people affected (Low: one or two, Medium: a team, High: many people or customers). It rises automatically when three, then five, people say they are affected.
- E-mail is simulated: messages go to an outbox the administrator can read. A real mail server would be one new class.
- All intelligence runs inside the application. No incident text is sent to an outside service.
