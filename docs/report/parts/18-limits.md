# 17. Limitations and future work

| Area | Today | Next step |
|---|---|---|
| Tenancy | One organisation per installation. | Tenant id on every row and per-tenant settings, to sell it as a hosted service. |
| Sign-in | E-mail and password, lockout. | Single sign-on (OpenID Connect), two-factor sign-in, password reset by e-mail. |
| E-mail | Simulated outbox. | A real mail channel (one class implementing `NotificationChannel`), replies by e-mail added as notes. |
| SLA calendar | Clock time, 24 hours a day. | Business hours and holidays per policy. |
| Analytics | Tables and the health map. | Charts over time and exports to CSV. |
| Machine learning | Naive Bayes and TF-IDF, trained locally. | Optional use of a large language model for summaries and drafting replies, with the organisation's consent. |
| Scale | One server; several need shared sessions and a lock for the SLA job. | Spring Session, ShedLock, a message broker for live updates and webhooks (section 11, question 9). |

# 18. Conclusion

Quell meets every mandatory requirement of the brief and 15 of its 16 innovation challenges, and goes
further where a real product must: integrations, installation, security and polish. The central idea
of the brief, using object-oriented thinking to build a maintainable, extensible and intelligent
system, shows in how the product grew. The live updates, the learning classifier, the webhooks and the
assistant were all added after the core was finished, and each was a new class behind an existing
interface (a listener, a channel, a state) rather than a change to the incident logic. That is what the
patterns were for.
