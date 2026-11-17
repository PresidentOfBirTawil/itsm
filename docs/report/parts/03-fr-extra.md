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
