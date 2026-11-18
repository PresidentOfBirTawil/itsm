# Intelligence and innovation {#intelligence .chapter}

Every automatic decision is explained where it is shown. Priority is a documented formula; classification, similarity and forecasts are small machine-learning models written in the project itself and trained on the installation's own data, so they work offline, answer in milliseconds and keep incident text private.

## The priority algorithm

```{=html}
<div class="formula">
  <p>score = impact × urgency × service criticality <span class="muted">&nbsp;&nbsp;each from 1 to 3</span></p>
  <p><span class="dot red"></span>P1 Critical 18–27 &nbsp;&nbsp; <span class="dot amber"></span>P2 High 9–17 &nbsp;&nbsp; <span class="dot blue"></span>P3 Medium 4–8 &nbsp;&nbsp; <span class="dot gray"></span>P4 Low 1–3</p>
</div>
```

::: {.keys .compact}
| Factor              | 1                        | 2                          | 3                              | Set by                         |
|---------------------|--------------------------|----------------------------|--------------------------------|--------------------------------|
| Impact              | One or two people        | A team or a department     | Many people or customers       | The reporter; raised by "I'm affected too" |
| Urgency             | It can wait              | Work is slowed down        | Work has stopped               | The reporter                   |
| Service criticality | Low                      | Medium                     | High                           | The administrator, per service |
:::

The brief's example: the payment application (criticality 3) is unavailable for many users (impact 3) and transactions have stopped (urgency 3). The score is 3 × 3 × 3 = 27, so the incident is P1: respond within 5 minutes, resolve within 30. The three numbers, the score and the priority are stored with the incident (`PriorityAssessment`) and shown on its page, so nobody has to trust a black box.

**Why multiply rather than add.** A low factor pulls the result down: work stopped for many people on a minor test system gives 3 × 3 × 1 = 9, which is P2, whereas adding (3 + 3 + 1) would rank it almost as high as a real emergency. Critical stays rare, at 4 of the 27 combinations, which keeps the 5-minute target achievable. The reporter cannot set the priority alone, because criticality comes from the administrator. And no score falls between 12 and 18, so the boundary of P1 lies in a gap that a one-step change cannot cross by accident; the edges themselves (17 is P2, 18 is P1) are tested by TC-02 and TC-03. A different rule would be one more class implementing `PriorityCalculator`.

## Intelligent features

The brief asks for two; Quell has all seven of its suggestions (A to G), and a reporting assistant.

::: {.keys .compact}
| Feature                       | How it works                                                                                                                                         | Brief |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| Duplicate detection           | Jaccard similarity of the significant words of two titles, on the same service within 24 hours; 40% or more links them. The shared words are shown; nothing is rejected. | A |
| Correlation and root cause    | Services form a dependency graph. When two or more services with serious incidents (open P1 or P2 in the last 3 hours) depend on one service, it is named the probable root cause. | B |
| Automatic escalation          | Every minute, a P1 incident not acknowledged by its response deadline is escalated to the administrators, once.                                         | C |
| Technician recommendation     | Skill-based (fewest open incidents among those with the skill), least loaded, or round robin; availability and workload limits always apply.             | D |
| SLA risk detection            | A warning at 75% of the allowed time; a "most likely to breach next" list; a predicted finish time and breach risk learned from similar past incidents. | E |
| Categorisation                | Keyword rules first; otherwise a naive Bayes classifier trained on past incidents and articles decides when at least 50% sure, and shows the deciding words. It also flags a category it is 60% sure is wrong. | F |
| Suggested resolution          | Knowledge articles and fixes of similar resolved incidents, ranked by TF-IDF cosine similarity, with how often each fix worked; one click copies a fix. | G |
| Reporting assistant           | A chat, typed or spoken, that recognises the service, offers a known outage or an article first, asks impact and urgency in plain words, then files the incident through the same service as the form. | — |
:::

## How the learning works

Two small generic classes, with no external service and no library:

- **`SimilarityIndex<T>`** turns each text into a TF-IDF vector: term frequency 1 + ln(count) times inverse document frequency ln((1 + N) / (1 + df)) + 1, so rare, telling words weigh more than common ones. Vectors are normalised, and the similarity of two texts is their dot product, from 0 (nothing in common) to 1. It ranks suggested fixes and the assistant's articles.
- **`NaiveBayesClassifier<L>`** learns P(word | category) with Laplace smoothing, scores a text by log P(category) + Σ log P(word | category), and turns the scores into probabilities. Its training data is the categories' keywords, every categorised incident and every article; it retrains when incidents change, so a technician's correction improves the next guess. A text with no known word gets no guess at all.
- **`SlaPredictor`** takes the median resolution time of similar finished incidents (at least 4 are needed), adjusts it by the assigned technician's usual speed (0.6 to 1.6 times), and gives the share of similar incidents that took longer than the time still left: the risk of breaching.

## The innovation challenges

Fifteen of the sixteen are built in. Microservices were left out on purpose (7.2, question 5).

::: {.keys .compact .pairs}
| Challenge                          | In Quell                                                    | Challenge                         | In Quell                                                  |
|------------------------------------|-------------------------------------------------------------|-----------------------------------|-----------------------------------------------------------|
| AI-assisted classification         | <span class="check">✓</span> Naive Bayes after keyword rules | Mobile push notifications        | <span class="check">✓</span> Phone and desktop alerts via the service worker |
| AI-based resolution recommendation | <span class="check">✓</span> TF-IDF suggested fixes          | Voice-based reporting            | <span class="check">✓</span> Microphone on the form and in the chat |
| Incident similarity detection      | <span class="check">✓</span> Duplicate detection             | Chatbot-based reporting          | <span class="check">✓</span> The reporting assistant      |
| Automated root-cause analysis      | <span class="check">✓</span> Root cause on the service graph | Cloud deployment                 | <span class="check">✓</span> Container image, PostgreSQL  |
| Predictive SLA violation detection | <span class="check">✓</span> Forecast, 75% warning, risk list | Microservice architecture       | <span class="cross">—</span> Not used, on purpose          |
| Service dependency graph           | <span class="check">✓</span> Service map; loops refused      | Event-driven architecture        | <span class="check">✓</span> Events, five listeners, outbox |
| Real-time notifications            | <span class="check">✓</span> Live bell (server-sent events)  | Real-time operational dashboard  | <span class="check">✓</span> Dashboards and queues update live |
| Analytics and visualization        | <span class="check">✓</span> Reports, health strip, service map | Automated escalation          | <span class="check">✓</span> Unacknowledged P1 to administrators |
:::

**Beyond the brief:** a REST API with personal keys and interactive documentation; webhooks to Slack, Microsoft Teams or any app; screenshots and logs attached to incidents; a public service status page; a knowledge base fed by resolved incidents; an installable app; one-click installers for three operating systems; dark and light themes; a command palette.
