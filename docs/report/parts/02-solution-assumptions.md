### Proposed solution

A product with three roles, used from a browser, an installed app or another system through its API.
An employee reports an incident on a form or by chatting with the assistant. The system then, without
anyone doing anything: recognises the affected service from the words used, checks whether the problem
is a known outage (and offers to join it instead), suggests knowledge articles that may solve it at once,
chooses the category (keyword rules first, then a machine-learning classifier trained on the
organisation's own history), calculates a priority from impact, urgency and the criticality of the
service, sets response and resolution deadlines from the SLA policy, links a possible duplicate,
assigns a technician with the rule the administrator has chosen, records each step in an audit trail,
notifies the people concerned in the app, by e-mail, as desktop alerts and through webhooks, and updates
every open screen live.

The incident then follows a fixed lifecycle of nine statuses. The technician sees similar past fixes and
a predicted finish time; the administrator sees which incidents will breach next, and when several
services fail at once the system points to the service they all depend on. A job checks every open
incident once a minute, warns at 75% of the allowed time, records breaches and escalates a critical
incident nobody has acknowledged. A good fix becomes a knowledge article in one click, so the next
person with the same problem may not need a technician at all.

### Assumptions

- One organisation uses one installation; there is no separation between tenants. (The landing page
  shows how it would be sold per technician; multi-tenancy is future work.)
- Users have a modern browser. There is no native mobile app; the web app installs on a phone as a
  progressive web app, and desktop installers exist for Windows, macOS and Linux.
- Every incident is about exactly one service from the catalogue. Services can depend on other services.
- Employees register themselves. Technicians and administrators are created by an administrator.
- SLA times are clock time, 24 hours a day. There are no business hours or holidays.
- Impact is chosen by the reporter (Low: one or two people; Medium: a team; High: many people or
  customers) and rises automatically when more people say "I'm affected too" (3 people: at least
  Medium, 5: High).
- E-mail is simulated: messages are stored in an outbox that the administrator can read. A real mail
  server is one class implementing `NotificationChannel`.
- The machine-learning features learn only from data inside the installation. No text is sent to an
  outside service, so they work offline and keep the organisation's data private.
- The server clock is the reference for all times.
