# Requirements and business rules {#appendix-a .appendix}

Every functional requirement and business rule, with the class or package that implements it. The fourteen non-functional requirements are complete in 2.3. Class names are in `src/main/java/com/quell`.

## Functional requirements

::: {.ids}
| ID    | Requirement                                                                                 | Implemented in |
|-------|--------------------------------------------------------------------------------------------|------------------------------|
| FR-01 | Users sign in with email and password; employees can register themselves. | `security/`, `RegistrationService` |
| FR-02 | Each role has its own dashboard and menu; every address is protected by role on the server. | `SecurityConfig` |
| FR-03 | An employee reports an incident with title, description, service, optional category, impact and urgency. | `EmployeeController`, `IncidentService.report` |
| FR-04 | Each incident gets a unique readable code (INC-0001, INC-0002, …). | `Incident` (`@PrePersist`) |
| FR-05 | The system calculates the priority (P1–P4) and stores how it was worked out. | `WeightedPriorityCalculator`, `PriorityAssessment` |
| FR-06 | The system sets response and resolution deadlines from the SLA policy of the priority. | `SlaService`, `Incident.prioritise` |
| FR-07 | The system assigns a technician automatically using a strategy the administrator can change at runtime. | `AssignmentService`, `assignment/` |
| FR-08 | An incident moves through nine statuses; only the legal moves are possible and each belongs to a role. | `domain/state/` |
| FR-09 | Technicians add investigation notes, request information, and record a resolution. | `IncidentService.addNote`, `changeStatus` |
| FR-10 | The reporter confirms the fix or reopens, and can rate the resolution from 1 to 5. | `ResolvedState`, `IncidentService.rate` |
| FR-11 | Every action on an incident is recorded in an audit trail that cannot be edited. | `AuditTrailListener`, `AuditEntry` |
| FR-12 | The people concerned are notified in the application and by (simulated) email. | `notification/` |
| FR-13 | SLAs are monitored every minute: a warning at 75% of the time, a breach after the deadline. The clock pauses while waiting for information. | `SlaMonitor`, `SlaService`, `Incident.refreshSla` |
| FR-14 | Possible duplicates are detected and linked. | `DuplicateDetector` |
| FR-15 | A critical incident nobody has acknowledged in time is escalated to the administrators. | `EscalationPolicy`, `AutoEscalation` |
| FR-16 | A category is suggested from the words of the report when the reporter chooses none. | `CategorySuggester` |
| FR-17 | Incidents can be searched by any combination of code, status, priority, service, category, technician, SLA state and date range. | `IncidentSpecifications` |
| FR-18 | The administrator has a dashboard (totals, waiting, escalated, most likely to breach, workload, incidents per service). | `AdminController` |
| FR-19 | The administrator manages users, technicians, services, categories and SLA policies. | `AdminTechnicianController`, `AdminCatalogueController` |
| FR-20 | The administrator can assign and reassign by hand; a technician who is unavailable or full is refused. | `AssignmentService.assignManually` |
| FR-21 | The classification of an open incident can be corrected; priority and deadlines are recalculated and a changed priority is announced. | `IncidentService.reclassify` |
| FR-22 | Reports: incidents by status, priority and category, SLA compliance, resolution time against target, figures per technician. | `ReportService` |
| FR-23 | An audit log across all incidents with filters. | `AdminInsightController` |
| FR-24 | Demo data can be reset and filled with realistic past incidents. | `DemoDataService` |
| FR-25 | Open pages update by themselves: when an incident is created or changes, the administrator's dashboard, the technician's queue and the incident lists are refreshed in place, and the bell count changes at once. | `live/`, `LiveChannel`, `static/js/live.js` |
| FR-26 | Employees can report by chatting with an assistant (typed or spoken), which recognises the service, offers a known outage or knowledge articles, asks impact and urgency in plain words and files the incident by the normal rules. | `assistant/`, `AssistantController` |
| FR-27 | Voice input: a microphone button turns speech into text on the report form and in the assistant. | `static/js/voice-input.js` |
| FR-28 | Screenshots, PDFs and log files can be attached when reporting and on the incident page (drag and drop, paste, choose). | `AttachmentService`, `AttachmentController` |
| FR-29 | A knowledge base of articles that anyone can search and rate; technicians write articles, also straight from a resolved incident. | `knowledge/`, `KnowledgeController` |
| FR-30 | Technicians see suggested solutions for each incident: matching articles and fixes of similar past incidents, ranked by similarity. | `ResolutionRecommender` |
| FR-31 | The category is chosen by a machine-learning classifier when no keyword matches, and the classifier warns when an incident seems filed under the wrong category. | `CategoryClassifier`, `NaiveBayesClassifier` |
| FR-32 | Each open incident has a predicted finish time and a risk of missing its deadline, learned from similar past incidents. | `SlaPredictor` |
| FR-33 | Services can depend on other services; a status page shows the health of every service, a dependency map, known problems and the probable root cause when several fail together. | `status/`, `StatusController` |
| FR-34 | Employees can say "I'm affected too" to follow a known problem instead of reporting it again; the impact rises with the number of people affected and everyone is told when it is fixed. | `IncidentService.joinAsAffected` |
| FR-35 | New notifications appear as desktop alerts while the app is in the background. | `LiveChannel`, `static/js/live.js` |
| FR-36 | The application installs as an app on phones and computers (progressive web app) and shows an offline page when the server cannot be reached. | `AppManifestController`, `static/sw.js` |
| FR-37 | A REST API (`/api/v1`) lets other systems list, read, report and work on incidents and read status, services, categories and the knowledge base, with interactive documentation. | `api/` |
| FR-38 | Users make and revoke personal API keys that act with their own permissions. | `ApiTokenService`, `AccountController` |
| FR-39 | Administrators connect webhooks to Slack, Microsoft Teams or any app, choose the events, see a delivery log and send a test message. | `webhook/`, `AdminWebhookController` |
| FR-40 | A command palette (Ctrl/⌘ K) finds any incident or article and jumps to any page; dark and light themes. | `QuickSearchController`, `command-palette.js` |
| FR-41 | One-click installers for Windows, macOS and Linux with Java included, and a container image with PostgreSQL for servers. | `packaging/`, `desktop/`, `Dockerfile` |
:::

## Business rules

::: {.ids}
| ID    | Rule                                                                                        | Enforced in |
|-------|--------------------------------------------------------------------------------------------|------------------------------|
| BR-01 | Priority score = impact (1–3) × urgency (1–3) × service criticality (1–3). 18–27 is P1, 9–17 is P2, 4–8 is P3, 1–3 is P4. | `WeightedPriorityCalculator` |
| BR-02 | SLA times: P1 respond in 5 min, resolve in 30 min. P2: 15 min, 2 h. P3: 1 h, 8 h. P4: 4 h, 24 h. Both are counted from the moment of the report. | `SlaPolicy`, `Incident.prioritise` |
| BR-03 | The lifecycle is NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED, with two side steps: IN_PROGRESS ⇄ AWAITING_INFO, and RESOLVED → IN_PROGRESS (reopen). No other move is possible. | `domain/state/` |
| BR-04 | Each move belongs to a role. Only the assigned technician acknowledges, starts, requests information and resolves. Only the reporter confirms or reopens. A technician or administrator closes. | `Transition`, `User.canWorkOn`, `IncidentService.changeStatus` |
| BR-05 | Requesting information, resolving and an administrator's reopen need a note. Resolving records the note as the resolution. | `Transition.noteRequired`, `Incident.recordResolution` |
| BR-06 | A CLOSED incident never changes again (it can still be rated). | `ClosedState` |
| BR-07 | A technician is given an incident only if their account is enabled, they are marked available, and they have fewer open incidents than their maximum workload. | `TechnicianLoad.canTakeMore` |
| BR-08 | Open means any status before RESOLVED. Resolved incidents no longer count towards workload. | `Status.isOpen` |
| BR-09 | Skill-based assignment never falls back to a technician without the skill. If nobody fits, the incident stays CLASSIFIED and the administrators are warned. | `SkillBasedStrategy`, `AssignmentService.autoAssign` |
| BR-10 | The response SLA is met by acknowledging; the resolution SLA by resolving. | `Incident.responseTarget`, `resolutionTarget` |
| BR-11 | At 75% of the allowed time an incident is AT_RISK and a warning is sent once. After the deadline it is BREACHED, recorded once. Exactly at the deadline is not yet a breach. | `Incident.refreshSla` |
| BR-12 | While an incident is AWAITING_INFO its SLA clock stands still, and the resolution deadline moves back by the time waited. | `AwaitingInfoState`, `Incident.pauseSlaClock/resumeSlaClock` |
| BR-13 | A breach is never forgotten, even if the incident is resolved afterwards. | `Incident.isSlaBreached` |
| BR-14 | A P1 incident that is not acknowledged by its response deadline is escalated to the administrators, once. | `EscalationPolicy` |
| BR-15 | A new incident is a possible duplicate of an open incident on the same service from the last 24 hours if their titles are at least 40% similar. It is linked, not rejected. | `DuplicateDetector` |
| BR-16 | The same person cannot report the same title for the same service while their first report is open. | `IncidentService.refuseIfAlreadyReported` |
| BR-17 | An employee sees only their own incidents. Technicians and administrators can see all. | `User.canView` |
| BR-18 | A resolution can be rated once, from 1 to 5, by the reporter, after they have confirmed the fix. | `Incident.rate`, `IncidentState.allowsRating` |
| BR-19 | Nobody is notified about their own action (except the confirmation that a report was received). | `NotificationListener` |
| BR-20 | Changing the classification is allowed only while the incident is open, by its technician or an administrator. | `Incident.reclassify`, `IncidentService.reclassify` |
| BR-21 | A disabled account cannot sign in and its open sessions end at once. An administrator cannot disable their own account. | `QuellUserDetails`, `SignedInSessions`, `AdminTechnicianController` |
| BR-22 | An email address belongs to at most one account, whatever the letter case. | `User.normaliseEmail`, unique column |
| BR-23 | When 3 people are affected (the reporter plus those who said "I'm affected too") the impact is at least Medium, at 5 it is High; the priority and deadlines are recalculated. The reporter cannot join their own incident, nobody can join twice, and a finished incident cannot be joined. | `IncidentService.joinAsAffected`, `Incident.addAffectedUser` |
| BR-24 | A probable root cause is a service that at least two services with serious problems (open P1 or P2 incidents from the last 3 hours) depend on, directly or indirectly. Security incidents are left out. Most affected services first, then a service with its own serious incident, then the smallest reach. | `RootCauseAnalyzer` |
| BR-25 | A service may not depend on itself or on a service that already depends on it (no loops). | `ServiceGraph.loopIfDependsOn`, `AdminCatalogueController` |
| BR-26 | The learning classifier chooses a category only when it is at least 50% sure and questions an incident's category only when it is at least 60% sure of another one. | `CategoryClassifier`, `IncidentPages` |
| BR-27 | A predicted finish time needs at least 4 similar finished incidents; otherwise none is shown. | `SlaPredictor` |
| BR-28 | Attachments: PNG, JPEG, GIF, WebP, PDF or plain text, recognised by their content; at most 5 MB each and 10 per incident; added by the reporter, people affected, the assigned technician or an administrator until the incident is closed; removed by the person who added them or an administrator. | `AttachmentService`, `FileKind` |
| BR-29 | An API key works only until it is revoked or expires and only while its owner's account is enabled; it can do exactly what its owner can do; at most 120 requests a minute per key; at most 10 working keys per user. | `ApiTokenService`, `ApiRateLimiter` |
| BR-30 | After 5 wrong passwords for one e-mail address within 15 minutes, sign-in for that address is paused for 15 minutes, even with the right password. | `LoginAttempts`, `LockoutAuthenticationProvider` |
| BR-31 | A webhook message is retried after 30 seconds, 2 minutes, 10 minutes and 1 hour, then given up; a message is never sent for a change that was rolled back. | `WebhookDelivery`, `WebhookListener` |
| BR-32 | The assistant offers knowledge articles only with a match of at least 30%, and offers a known outage only to people who have not reported or joined it. | `assistant/Step` |
:::

