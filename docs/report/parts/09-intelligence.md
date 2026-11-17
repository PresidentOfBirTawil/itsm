# 8. Intelligent features and innovations

## 8.1 The innovation challenges of the brief

| Innovation challenge in the brief | In Quell | Where |
|---|---|---|
| AI-assisted incident classification | Keyword rules first; then a **multinomial naive Bayes classifier** trained on the organisation's own incidents and articles, with the deciding words shown. It also questions incidents that seem filed under the wrong category. | `CategoryClassifier`, `NaiveBayesClassifier<L>` |
| AI-based resolution recommendation | For every incident: matching **knowledge articles** and **fixes of similar past incidents**, ranked by TF-IDF cosine similarity, with the shared words, how often a fix worked and how it was rated. One click copies a fix into the resolution. | `ResolutionRecommender`, `SimilarityIndex<T>` |
| Incident similarity detection | Possible duplicates linked automatically (Jaccard similarity of significant words, same service, last 24 hours); the same person reporting the same thing twice is refused. | `DuplicateDetector` |
| Automated root-cause analysis | When several services have serious incidents at the same time, the service they **all depend on** is named as the probable root cause, on the status page and the administrator's overview. | `RootCauseAnalyzer`, `ServiceGraph` |
| Predictive SLA violation detection | A **predicted finish time and breach risk** for each open incident, learned from similar finished incidents and adjusted for the technician's usual speed; plus the 75% warning and the "most likely to breach next" list. | `SlaPredictor`, `SlaRiskRanker` |
| Service dependency graph | Services depend on services; loops are refused; a drawn **dependency map** shows trouble flowing upwards. | `ServiceGraph`, `ServiceMap` |
| Real-time notifications | The bell updates at once in every open tab (server-sent events). | `LiveUpdates`, `LiveChannel` |
| Mobile push notifications | **Desktop and mobile alerts** for new notifications while Quell is in the background (Notification API, through the service worker on phones). | `live.js`, `sw.js` |
| Voice-based incident reporting | A microphone button on the report form and in the assistant (speech recognition built into the browser). | `voice-input.js` |
| Chatbot-based incident reporting | The **reporting assistant**: a conversation that recognises the service, offers a known outage or articles, asks impact and urgency in plain words and files a complete incident. | `assistant/` |
| Cloud deployment | **Container image** and docker-compose with **PostgreSQL**, configured by environment variables; verified on PostgreSQL. | `Dockerfile`, `application-prod.properties` |
| Event-driven architecture | Every change is an event; five listeners react; webhooks use a transactional outbox. | `event/`, `webhook/` |
| Real-time operational dashboard | Dashboards, queues and lists update in place when anything changes. | `LiveUpdateListener`, `live.js` |
| Analytics and visualization | Reports (by status, priority, category, SLA compliance, resolution time, per technician), the health strip, the dependency map. | `ReportService`, `StatusService` |
| Automated incident escalation | A P1 nobody acknowledges in time is escalated to the administrators. | `EscalationPolicy` |
| Microservice architecture | Not used, on purpose (section 11, question 5); the event-driven design keeps the option open. | |

## 8.2 Machine learning without an external service

All learning features are implemented in the project itself, in two small generic classes, and train
on data the installation already has. Nothing is sent to an outside AI service: the features work
offline, answer in milliseconds, and keep incident text private. Each decision is explained on the
page with the words that led to it.

**Text into numbers.** A text is cut into lower-case words; very common words ("the", "is", "please")
are dropped (`Words`).

**TF-IDF cosine similarity** (`SimilarityIndex<T>`). Each document becomes a vector with one weight per
word: term frequency `1 + ln(count)` times inverse document frequency `ln((1 + N) / (1 + df)) + 1`, where
N is the number of documents and df the number that contain the word, so rare, telling words count
more than common ones. Vectors are normalised to length 1, and the similarity of two texts is the dot
product of their vectors (1 = the same words in the same proportions, 0 = nothing in common). Used for
suggested solutions and for the assistant's article suggestions.

**Multinomial naive Bayes** (`NaiveBayesClassifier<L>`). For each category c and word w the classifier
learns P(w | c) from examples, with Laplace smoothing `(count(w, c) + 1) / (words(c) + |vocabulary|)`
so an unseen word never makes a probability zero. For a new text it computes
`log P(c) + Σ log P(w | c)` for every category, turns the scores into probabilities that add up to 1,
and reports the best one with the three words that pushed hardest towards it. Training data: the
categories' own names and keywords (counted twice), every incident with a category, and every
knowledge article. It retrains itself when incidents change, so a technician's correction improves the
next suggestion. A text with no known word gets no guess at all.

**Predicted finish time** (`SlaPredictor`). From finished incidents of the same category and priority
(or, if there are too few, the same priority) it takes the median resolution time, adjusts it by the
assigned technician's speed relative to their colleagues (clamped between 0.6 and 1.6), and computes
the share of similar incidents that took longer than the time this one still has before its deadline:
the breach risk. At least 4 similar incidents are needed; otherwise no forecast is shown.

## 8.3 The service map and root-cause analysis

An administrator records which services each service needs (for example, the Payment Application needs
the Database Cluster, the Authentication Service and the Corporate Network). `ServiceGraph` answers
"what does this service need, directly or indirectly?" (upstream) and "what needs it?" (downstream),
refuses loops, and arranges services in layers for drawing.

`RootCauseAnalyzer` looks at serious incidents (open, P1 or P2, from the last 3 hours, not security
incidents). For every service it asks: how many of the services with serious problems depend on it
(including itself)? A service that two or more depend on is a suspect; the one shared by the most
wins, a suspect with its own serious incident is "confirmed", and among equals the one with the
smallest reach is the most specific answer. The status page then says, for example: *"Corporate
Network: 2 services that depend on it have serious incidents at the same time (Corporate Network,
Payment Application), including Corporate Network itself."*

## 8.4 The reporting assistant

A chat for people who do not know what "impact" or "urgency" means. It is rule-based on purpose:
predictable, instant, testable and private. The conversation is a State machine (`Step`): **describe**
the problem → confirm the **service** recognised from the words (`ServiceDetector`) → if a serious open
incident exists on that service or one it depends on, offer to **join** it → offer **knowledge articles**
with a match of 30% or more → **who** is affected and how much it **stops work**, in plain words, with
the smart-triage guess (`TriageAdvisor`) shown first → the **category** only if nothing could be
worked out → **confirm** → the incident is created by the same `IncidentService` as the form, so every
rule applies. Buttons, typed answers ("it's the vpn", "only me", "I can't work") and speech all work.
The conversation lives in the session, and the page works without JavaScript too.

## 8.5 Integrations: REST API and webhooks

**REST API** (`/api/v1`). Incidents (list with filters, search and paging; read; report; notes; status
changes; timeline), service status, services, categories and the knowledge base. Each request carries
a personal API key (`Authorization: Bearer qk_…`); the key acts with exactly its owner's permissions,
so every rule of the web app applies. Keys are 40 random characters, stored only as a SHA-256 hash,
shown once, revocable and with an expiry. Requests are limited to 120 a minute per key, every answer
says how many are left, and errors use the standard *problem details* format (RFC 9457). Interactive
documentation (OpenAPI 3.1 / Swagger UI) is at `/api/docs`.

**Webhooks.** Administrators send chosen events (reported, assigned, status changed, resolved, SLA
breached, escalated; optionally only P1 and P2) to Slack, Microsoft Teams (Adaptive Cards) or any app
(JSON signed with HMAC-SHA256 so the receiver can check its origin). Messages are queued in the same
transaction as the change (transactional outbox), built and sent right after the commit, retried with
growing pauses, and shown in a delivery log with a test button.

## 8.6 Everything live, everywhere

- **Live updates** (server-sent events): dashboards, queues, lists and the bell change in place when
  anything changes. A message carries only "something changed"; the page fetches its own new content
  through the normal access-checked pages.
- **Desktop and mobile alerts** for new notifications while the app is in the background.
- **Installable app** (manifest and service worker, with an offline page), **native installers** with
  Java included, a **tray icon** and a single-instance check on the desktop.
