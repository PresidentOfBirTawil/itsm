# Author and conclusion {#conclusion .chapter}

Quell meets every mandatory requirement of the brief and fifteen of its sixteen innovation challenges, and it goes further where a real product must: integrations, installation, security and finish.

## Author

Group 94 has one member, who analysed, designed, built, tested and documented the whole product, and presents every module in the video.

::: {.keys .compact}
| Member                      | Responsibility                                                                                                       | Main packages                                              |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| Yatharth Soni, 2025ADPS1461H | Problem analysis and requirements; domain model and OOP design; services and REST API; interface and design system; intelligent features; database; testing; installers, Docker and CI; report and video. | All of `com.quell`, the templates and scripts, `src/test`, `packaging`, `.github` |
:::

## Limitations and future work

::: {.keys .compact}
| Area              | Today                                               | Next step                                                                                   |
|-------------------|-----------------------------------------------------|---------------------------------------------------------------------------------------------|
| Tenancy           | One organisation per installation                   | A tenant on every row and per-tenant settings, to offer Quell as a hosted service.          |
| Sign-in           | E-mail and password, with lockout                    | Single sign-on (OpenID Connect), two-factor sign-in, password reset by e-mail.              |
| E-mail            | A simulated outbox                                  | A real mail channel (one class implementing `NotificationChannel`); replies added as notes. |
| SLA calendar      | Clock time, 24 hours a day                          | Business hours and holidays per policy.                                                     |
| Scale             | One server; several need shared sessions and a lock for the SLA job | Spring Session, ShedLock and a message broker (7.2, question 9).                |
| Learning          | Naive Bayes and TF-IDF, trained locally             | An optional language model for summaries and draft replies, with the organisation's consent. |
:::

## Conclusion

The central question of the brief was how object-oriented thinking can produce a maintainable, extensible and intelligent system for managing IT incidents. Quell's answer shows in how the product grew. Live updates, webhooks and the reporting assistant were all added after the core was finished, as new classes behind existing interfaces (listeners, a notification channel, a state machine in front of the same services), without changing the incident logic. The rules sit in the objects that own them, so the web pages, the chat and the REST API all obey them without repeating them, and 619 tests check that they do. That is what the patterns were for.
