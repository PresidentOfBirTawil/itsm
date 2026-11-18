# Test cases

The project has **617 automated tests** in 41 test classes (JUnit 6, AssertJ, Mockito, Spring's MockMvc,
Awaitility). They run with one command and need nothing installed except Java 25:

```bash
./mvnw test
```

This document describes **36 of them in full**, chosen so that every area of the brief and every kind
of test is represented: TC-01 to TC-20 cover the mandatory modules, TC-21 to TC-36 the intelligent
features, the assistant, attachments, the REST API, webhooks and security hardening. Each case names the test method that carries it out, so the document can be
checked against the code. All test classes are in `src/test/java/com/quell/`.

The six kinds of test:

| Type | What it shows |
|---|---|
| Normal | The system does the right thing with ordinary, correct input. |
| Boundary | The system behaves correctly exactly at the edge of a rule, and one step either side. |
| Invalid input | Input that makes no sense is refused with a message and nothing is saved. |
| Exception | Something goes wrong inside the system and the right exception is raised and handled. |
| Authorization | A user is stopped from doing something their role does not allow. |
| Business rule | A rule from the brief is enforced. |

## The 20 documented cases

| ID | Area | Scenario | Input | Expected result | Type | Test method |
|---|---|---|---|---|---|---|
| TC-01 | Incident reporting | An employee reports an outage of a critical service. | Reporter Priya Sharma; service Payment Application (criticality 3); category Application; impact High; urgency High. | An incident with a code `INC-nnnn` is stored. Score 3 × 3 × 3 = 27, priority P1. Respond-by is creation + 5 min, resolve-by is creation + 30 min. Status is ASSIGNED and the technician is Sara Thomas (the one with the Application skill). | Normal | `IncidentWorkflowTests.reportingCreatesPrioritisesAndAssignsInOneGo` |
| TC-02 | Priority | The highest score that is not yet critical. | Score 17. | Priority P2. | Boundary | `WeightedPriorityCalculatorTests.seventeenIsStillP2` |
| TC-03 | Priority | The lowest score that is critical. | Score 18. | Priority P1. | Boundary | `WeightedPriorityCalculatorTests.eighteenIsP1` |
| TC-04 | Priority | A score that the formula can never produce. | Scores −1, 0, 28 and 100. | `IllegalArgumentException` each time; no priority is returned. | Invalid input | `WeightedPriorityCalculatorTests.scoresOutsideOneToTwentySevenAreRejected` |
| TC-05 | Lifecycle | Every possible move between two statuses is tried. | All 9 × 9 = 81 pairs of (from, to) status. | The 10 legal moves succeed and the status changes. The other 71 raise `InvalidStateTransitionException` naming both statuses, and the status is unchanged. | Business rule | `IncidentLifecycleTests.aMoveSucceedsOnlyIfTheLifecycleAllowsIt` |
| TC-06 | Lifecycle | Someone tries to close an incident that was only just reported. | A NEW incident; target status CLOSED. | `InvalidStateTransitionException` with the message "… cannot go from New to Closed"; the status is still NEW. | Exception | `IncidentLifecycleTests.newCannotJumpStraightToClosed` |
| TC-07 | Assignment | Skill-based assignment with two technicians who have the skill. | A Network incident. Ravi Kumar (Network, 2 open), Arjun Singh (Network, 1 open), Meera Nair and Sara Thomas (no Network skill, 0 open). | Arjun Singh is chosen: of those with the skill, he has the fewest open incidents. The idle technicians without the skill are not considered. | Business rule | `AssignmentStrategyTests.SkillBased.amongSkilledTechniciansTheLeastLoadedWins` |
| TC-08 | Assignment | An administrator assigns an incident to a technician who is full. | Arjun Singh already has 3 open incidents (his maximum is 3). | `WorkloadExceededException` with "3 of 3" in the message; the incident keeps its technician (Sara Thomas). | Exception | `IncidentWorkflowTests.anIncidentCannotBeGivenToATechnicianAtMaximumWorkload` |
| TC-09 | Assignment | An administrator assigns an incident to a technician who is marked unavailable. | Ravi Kumar, available = false. | `TechnicianUnavailableException` with "Ravi Kumar is not available"; the incident keeps its technician. | Exception | `IncidentWorkflowTests.anIncidentCannotBeGivenToAnUnavailableTechnician` |
| TC-10 | SLA | The SLA check runs at the exact moment of the response deadline. | P3 incident reported 09:00, response time 60 min, not acknowledged; check at 10:00:00. | Not breached. The status is AT_RISK. (A breach begins only after the deadline has passed.) | Boundary | `SlaTests.exactlyAtTheDeadlineIsNotYetABreach` |
| TC-11 | SLA | The SLA check runs when exactly 75% of the response time has been used. | Same incident; checks at +45 min and again at +50 min. (`SlaTests.justUnderSeventyFivePercentIsStillOnTrack` covers +44 min.) | At +45 min the status becomes AT_RISK and one warning is raised for the response deadline. At +50 min the status is still AT_RISK and no second warning is raised. | Boundary | `SlaTests.atSeventyFivePercentOfTheResponseTimeTheIncidentIsAtRiskAndAWarningIsRaisedOnce` |
| TC-12 | SLA | The technician asks the reporter for information and waits two days. | Incident IN_PROGRESS; moved to AWAITING_INFO at +100 min; checks at +100 min and +2880 min. | The status is PAUSED both times, nothing is breached, and the time used stays at 100 minutes. | Business rule | `SlaTests.waitingForInformationPausesTheResolutionClock` |
| TC-13 | SLA configuration | An incident is reported while its priority has no SLA policy. | The P1 policy has been deleted; a P1 incident is reported. | `MissingSlaConfigurationException` that names P1. No incident is stored. | Exception | `IncidentWorkflowTests.reportingFailsCleanlyWhenThePriorityHasNoSlaPolicy` |
| TC-14 | Access by role | An employee types the address of an administrator page. | Signed in as `emp1@quell.local`; GET `/admin`, `/admin/dashboard`, `/admin/users`. | HTTP 403 Forbidden for all three. | Authorization | `SecurityTests.employeeCannotOpenAdmin` |
| TC-15 | Sign-in | Someone signs in with the wrong password. | Email `emp1@quell.local`, password `not-the-password`. | Not signed in; sent back to `/login?error`. | Invalid input | `SecurityTests.wrongPasswordFails` |
| TC-16 | Access to actions | A user sends a request by hand for a move that belongs to another role or another technician. | An ASSIGNED incident. (a) The reporter posts "ACKNOWLEDGED" to the employee address; (b) the reporter posts it to the technician address; (c) a technician who is not the assignee posts it. | HTTP 403 for all three; the status is still ASSIGNED. | Authorization | `IncidentWebTests.aUserCannotMakeAnotherRolesMoveEvenByCraftingTheRequest` |
| TC-17 | Incident reporting | The report form is sent almost empty. | Title one space, description empty, no service, impact or urgency. | The form is shown again with an error on each of the five fields ("Please choose the affected service", …). The number of incidents is unchanged. | Invalid input | `IncidentWebTests.aReportWithMissingAnswersShowsTheErrorsAndCreatesNothing` |
| TC-18 | Duplicate detection | The same person reports the same thing twice. | Priya reports "Payment application unavailable", then "  payment APPLICATION unavailable " for the same service while the first is open. | `DuplicateIncidentException` naming the first incident's code and "still open"; nothing is stored. A different person reporting the same title is accepted and linked to the first as a possible duplicate. | Exception | `IntelligenceTests.theSamePersonReportingTheSameThingAgainIsRefused` |
| TC-19 | Notifications | The mail server is down while an incident is reported (the brief's "network failure"). | The email channel is made to throw `IllegalStateException("The mail server is down")`; an incident is reported. | The incident is saved and ASSIGNED. The three in-app notifications are delivered. Only the emails are missing. | Exception | `NotificationTests.aFailingEmailChannelDoesNotBlockTheAppChannelOrUndoTheIncident` |
| TC-20 | Whole workflow | The brief's payment-outage scenario, through the web pages, by three signed-in users. | Employee reports; technician acknowledges, starts work, asks for information; employee answers; technician resolves with a note; employee confirms; administrator closes. | Each step is accepted in that order and the status ends as CLOSED. The incident page shows every note in its timeline and offers no further status buttons. | Normal | `IncidentWebTests.thePaymentOutageScenarioFromReportToClose` |

## Cases for the intelligent features and integrations

| ID | Area | Scenario | Input | Expected result | Type | Test method |
|---|---|---|---|---|---|---|
| TC-21 | Machine learning | No category keyword matches, so the learning classifier decides. | A report whose words appear only in past incidents and articles of one category. | The naive Bayes classifier picks that category, the incident is stored with it, and the timeline explains why ("… was suggested by the learning classifier (82% sure …) because of the words: …"). | Normal | `KnowledgeAndAiTests.whenNoKeywordMatchesTheClassifierChoosesTheCategoryAndSaysWhy` |
| TC-22 | Machine learning | The classifier is asked about text with no word it has ever seen. | "zzz qqq". | No prediction at all (empty), rather than a guess. | Boundary | `TextIntelligenceTests$NaiveBayes.withNoKnownWordItDoesNotGuess` |
| TC-23 | Suggested solutions | A fix that worked several times for the same problem. | Three resolved incidents with the same resolution text; a new similar incident. | The fix is suggested once, with "used 3 times", ranked by TF-IDF cosine similarity. | Business rule | `KnowledgeAndAiTests.aFixThatWorkedSeveralTimesIsSuggestedOnceWithItsCount` |
| TC-24 | Predictive SLA | Too few similar incidents to learn from. | Fewer than 4 finished incidents of the same category or priority. | No forecast is shown (instead of an unreliable one). | Boundary | `SlaPredictorTests.withTooLittleHistoryThereIsNoForecast` |
| TC-25 | Root cause | Two services fail that share a dependency. | Open P1 on Payment Application and P2 on Corporate Network within 3 hours. | Corporate Network is named the probable root cause (Payment Application needs it); the status page and the administrator's overview show it. | Business rule | `ServiceIntelligenceTests.twoFailingServicesThatShareADependencyPointToIt` |
| TC-26 | Service map | An administrator makes two services depend on each other. | Database Cluster already needs Corporate Network; the administrator makes Corporate Network depend on Database Cluster. | Refused with "… cannot depend on … they would wait for each other"; nothing is saved. | Invalid input | `StatusAndAffectedTests.anAdministratorCannotMakeTwoServicesWaitForEachOther` |
| TC-27 | Affected users | More people say "I'm affected too". | A Low-impact incident; 2 more employees join, then 2 more. | At 3 people the impact rises to Medium, at 5 to High, and the priority is recalculated each time; everyone affected is told when it is fixed. | Business rule | `StatusAndAffectedTests.moreAffectedPeopleRaiseTheImpactAndThePriority` |
| TC-28 | Assistant | An employee reports a problem in a conversation. | "My laptop battery drains within an hour …", then "yes", "only me", "it can wait until next week", "yes". | The service (Workplace Devices) and category (Hardware) are recognised, impact Low and urgency Low are read from plain words, and an incident is created by the normal rules with a link to it. | Normal | `AssistantTests.aConversationInPlainWordsReportsAnIncident` |
| TC-29 | Assistant | The problem is a known outage. | An open P1 on Payment Application; another employee describes failing card payments and answers "yes". | The employee is added to the existing incident ("I'm affected too"); no new incident is created. | Business rule | `AssistantTests.aKnownProblemIsJoinedInsteadOfReportedAgain` |
| TC-30 | Assistant | Typed HTML in the chat. | `<script>alert(1)</script> my laptop screen …` | Shown as text (`&lt;script&gt;`), never run. | Invalid input | `AssistantTests.whatPeopleTypeIsNeverTreatedAsHtml` |
| TC-31 | Attachments | Files that must not be attached. | An SVG with a script, a 5 MB + 1 byte PNG, an empty file, HTML renamed to `.png`. | Each is refused with a message; the kind is decided by the file's first bytes, not its name; nothing is stored. | Invalid input | `AttachmentTests.scriptsTooBigFilesAndEmptyFilesAreRefused` |
| TC-32 | Attachments | Another employee asks for a file. | A screenshot on Priya's incident; Rahul requests `/attachments/{id}`. | HTTP 404 (the file does not exist for him); the reporter and staff get it with `nosniff` and a sandboxing content security policy. | Authorization | `AttachmentTests.uploadingFromTheIncidentPageAndViewingTheImage` |
| TC-33 | REST API | Requests without a key, with a wrong key and with a revoked key. | GET `/api/v1/incidents` and `/api/v1/me`. | HTTP 401 as RFC 9457 problem JSON with `WWW-Authenticate: Bearer`; a revoked key stops working at once. | Authorization | `ApiTests.wrongRevokedAndExpiredKeysAreRefused` |
| TC-34 | REST API | A technician's key moves someone else's incident, and an impossible move. | POST `/api/v1/incidents/{code}/status` with IN_PROGRESS (not the assignee) and CLOSED (from ACKNOWLEDGED). | HTTP 403 and HTTP 409; the status is unchanged. The assignee's ACKNOWLEDGED succeeds with 200. | Business rule | `ApiTests.aTechnicianWorksOnlyOnTheirOwnIncidentsAndIllegalMovesAreConflicts` |
| TC-35 | Webhooks | An incident is reported while a webhook listens. | A signed-JSON webhook to a local receiver; a P1 is reported. | One delivery is queued in the same transaction (outbox) and delivered with `X-Quell-Signature = HMAC-SHA256(secret, timestamp.body)`; the message carries the priority and the assigned technician. | Normal | `WebhookTests.anEventIsQueuedWithTheChangeAndDeliveredSigned` |
| TC-36 | Security | Password guessing. | Five wrong passwords for one account, then the right one; then an address without an account. | The fifth failure pauses sign-in (`/login?locked`) even for the right password; other accounts are unaffected; unknown addresses are paused the same way, so nothing reveals which accounts exist. | Authorization | `SecurityHardeningTests.fiveWrongPasswordsPauseSignInEvenWithTheRightPassword` |

### How the 36 cases are spread

| Type | Cases |
|---|---|
| Normal | TC-01, TC-20, TC-21, TC-28, TC-35 |
| Boundary | TC-02, TC-03, TC-10, TC-11, TC-22, TC-24 |
| Invalid input | TC-04, TC-15, TC-17, TC-26, TC-30, TC-31 |
| Exception | TC-06, TC-08, TC-09, TC-13, TC-18, TC-19 |
| Authorization | TC-14, TC-16, TC-32, TC-33, TC-36 |
| Business rule | TC-05, TC-07, TC-12, TC-23, TC-25, TC-27, TC-29, TC-34 |

## The brief's exception cases and where each is tested

| Exception case in the brief | Exception class | Test method |
|---|---|---|
| Invalid state transition | `InvalidStateTransitionException` | `IncidentLifecycleTests.aMoveSucceedsOnlyIfTheLifecycleAllowsIt` (TC-05), `ErrorHandlingTests.anInvalidStateTransitionComesBackAsAMessageOnTheIncidentPage` |
| Technician unavailable | `TechnicianUnavailableException` | `IncidentWorkflowTests.anIncidentCannotBeGivenToAnUnavailableTechnician` (TC-09), `ErrorHandlingTests.assigningToAnUnavailableTechnicianComesBackAsAMessage` |
| Workload exceeded | `WorkloadExceededException` | `IncidentWorkflowTests.anIncidentCannotBeGivenToATechnicianAtMaximumWorkload` (TC-08), `ErrorHandlingTests.assigningToATechnicianAtTheirMaximumComesBackAsAMessage` |
| Missing SLA configuration | `MissingSlaConfigurationException` | `IncidentWorkflowTests.reportingFailsCleanlyWhenThePriorityHasNoSlaPolicy` (TC-13), `ErrorHandlingTests.aMissingSlaPolicyIsExplainedOnTheReportForm` |
| Duplicate incident | `DuplicateIncidentException` | `IntelligenceTests.theSamePersonReportingTheSameThingAgainIsRefused` (TC-18), `ErrorHandlingTests.reportingTheSameThingTwiceIsExplainedOnTheReportForm` |
| Unauthorized operation | `UnauthorizedOperationException` | `ErrorHandlingTests.pressingAnotherRolesButtonShowsTheAccessDeniedPageAndChangesNothing`, `ErrorHandlingTests.openingSomeoneElsesIncidentShowsTheAccessDeniedPageWithTheReason` |
| Invalid input | `InvalidInputException` | `ErrorHandlingTests.aMissingNoteComesBackAsAMessage`, `FullWorkflowTests.aRatingIsPossibleOnlyAfterConfirmingOnlyOnceAndOnlyFromOneToFive` |
| Incident not found | `IncidentNotFoundException` | `ErrorHandlingTests.anIncidentThatDoesNotExistShowsTheNotFoundPage` |
| Service not found | `ServiceNotFoundException` | `ErrorHandlingTests.reportingAgainstAServiceThatDoesNotExistShowsTheNotFoundPage` |
| Database failure | Spring's `DataAccessException` | `DatabaseFailureTests.aPageThatCannotReachTheDatabaseShowsAReadableMessage`, `DatabaseFailureTests.anIncidentThatCannotBeSavedIsReportedToTheUserAndNothingIsStored` |
| Network failure | any exception inside a notification channel | `NotificationTests.aFailingEmailChannelDoesNotBlockTheAppChannelOrUndoTheIncident` (TC-19), `NotificationTests.aFailingAppChannelDoesNotBlockTheEmailChannelOrUndoTheIncident` |

`ErrorHandlingTests.everyErrorOfTheApplicationIsAQuellException` checks that all the application's own
exception classes extend the one abstract `QuellException`.

## All test classes

| Test class | Tests | What it covers |
|---|---:|---|
| `QuellApplicationTests` | 1 | The application starts. |
| `SecurityTests` | 16 | Sign-in, wrong password, each role's pages and navbar, every URL protected by role, sign-out. |
| `RegistrationTests` | 5 | Employee self-registration, BCrypt hashing, duplicate email, bad input, CSRF. |
| `SeedDataTests` | 8 | The seed data: users, skills, services, categories, the brief's four SLA policies. |
| `DomainModelTests` | 10 | Inheritance, associations and the incident code in the database. |
| `WeightedPriorityCalculatorTests` | 32 | The priority formula, every band edge, all 27 input combinations. |
| `IncidentLifecycleTests` | 116 | The State pattern: all 81 moves, one class per status, what each role may do. |
| `AssignmentStrategyTests` | 30 | The three assignment strategies and the rules they share. |
| `IncidentWorkflowTests` | 28 | Reporting, the factory, the audit trail, assignment and reassignment through the service layer. |
| `IncidentWebTests` | 21 | The same through the web pages, including the payment-outage scenario. |
| `SlaTests` | 21 | SLA arithmetic: 75% warning, breach, pause and resume, reopening. |
| `SlaMonitoringTests` | 11 | The every-minute SLA check and the demo SLA setting. |
| `NotificationDispatcherTests` | 5 | Channels are chosen by the factory and one failing channel does not stop the others. |
| `NotificationTests` | 11 | Who is told what, after commit only, failure isolation, the bell, the email outbox. |
| `IncidentSearchTests` | 10 | Search by any combination of filters (JPA Specifications). |
| `AdminManagementTests` | 11 | Dashboard figures; managing services, categories, technicians and SLA policies. |
| `DuplicateDetectorTests` | 13 | Title similarity, the 0.4 threshold, the 24-hour window, same service only. |
| `CategorySuggesterTests` | 17 | Category suggestion from keywords. |
| `EscalationAndRiskTests` | 11 | Automatic escalation of unacknowledged P1 incidents; the "most likely to breach" list. |
| `IntelligenceTests` | 15 | The four intelligent features from end to end. |
| `FullWorkflowTests` | 15 | Investigation notes, information requests, resolution, reopening, rating. |
| `ErrorHandlingTests` | 18 | Every exception of the brief turned into a readable message or page. |
| `ReclassificationTests` | 14 | Changing an incident's category, impact and urgency; priority recalculated and announced. |
| `UserManagementTests` | 12 | Adding users, disabling and enabling accounts, ending a disabled user's session. |
| `AuditLogAndReportTests` | 8 | The administrator's audit log with its filters, and the report figures. |
| `DatabaseFailureTests` | 4 | A database failure gives a readable 503 page and leaves nothing half-saved. |
| `DemoDataResetTests` | 9 | "Reset demo data": what is removed, what is kept, and that the sample incidents obey the rules. |
| `LiveUpdatesTests` | 15 | Live updates as plain objects: who is sent which message, broken connections, waiting for the commit. |
| `LiveUpdatesWebTests` | 13 | The live stream in the running application: sign-in required, the right messages arrive, streams close when they should. |
| `KnowledgeAndAiTests` | 14 | Knowledge base search, voting, permissions, suggested solutions, write-ups, the learning classifier and its second opinion, live form suggestions. |
| `TextIntelligenceTests` | 12 | TF-IDF similarity, multinomial naive Bayes (probabilities, weights, no guessing) and smart triage of plain words. |
| `SlaPredictorTests` | 7 | The predicted finish time and breach risk from similar past incidents, per technician speed. |
| `ServiceIntelligenceTests` | 11 | The dependency graph (upstream, downstream, loops, layers), service health, root-cause analysis, service recognition. |
| `StatusAndAffectedTests` | 10 | The status page, known problems while reporting, "I'm affected too" and the impact it raises. |
| `AssistantTests` | 13 | The reporting assistant: a whole conversation, typed answers, known outages, broken rules, HTML safety, the page with and without JavaScript. |
| `AttachmentTests` | 12 | File type by content, size and count limits, safe names, who may attach, see and remove, safe download headers. |
| `ApiTests` | 15 | The REST API: keys (hashed, revoked, expired), permissions, validation errors as problem JSON, filters and paging, rate limit, documentation, the keys page. |
| `WebhookTests` | 7 | Webhook outbox, HMAC signature, Slack and Teams formats, event and priority filters, retries, unreachable receivers, the admin pages. |
| `SecurityHardeningTests` | 6 | Content security policy and other headers, no inline scripts, sign-in lockout and its expiry. |
| `AppShellTests` | 3 | The installable app: manifest, service worker, offline page, desktop alert controls. |
| `DesktopAndBrandTests` | 7 | The desktop launcher's data folder, single instance and port, and the product name everywhere. |
| **Total** | **617** | |

## How the tests are built

- **Unit tests** (`WeightedPriorityCalculatorTests`, `IncidentLifecycleTests`, `AssignmentStrategyTests`,
  `SlaTests`, `DuplicateDetectorTests`, `CategorySuggesterTests`, `EscalationAndRiskTests`,
  `NotificationDispatcherTests`, `LiveUpdatesTests`) create plain Java objects. They use no database and no Spring, so a
  failure points straight at the rule that broke.
- **Integration tests** start the whole application against an empty in-memory H2 database with the
  seed data. Each test runs in a transaction that is rolled back afterwards, so tests cannot affect
  each other.
- **Web tests** send real HTTP requests through the security filters with MockMvc. They sign in through
  the real login form, so roles, CSRF protection and sessions are tested as they are in production.
- `NotificationTests`, `DatabaseFailureTests`, `DemoDataResetTests` and `LiveUpdatesWebTests` need real
  commits or deliberate failures, so each has its own copy of the application and database.
- The every-minute SLA job is switched off in tests. Tests call the same check directly and pass in the
  time, so nothing depends on the clock of the machine.
