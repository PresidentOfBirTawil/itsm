# Security {#security .chapter}

Every address and every object is checked on the server, for people and for programs alike, and the common web attacks are closed by default rather than by care in each page.

::: {.keys .compact}
| Concern                        | Measure                                                                                                                                                         |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Authentication                 | Sign-in with e-mail and password; the session ID changes at sign-in (no session fixation); sign-out ends the session.                                          |
| Password storage               | BCrypt hashes only (salted, deliberately slow); at least 8 characters; the plain password is never stored or logged.                                           |
| Password guessing              | Five wrong passwords for one address within 15 minutes pause sign-in for that address for 15 minutes, even with the right password. Unknown addresses are paused the same way, so the answer never reveals which accounts exist. |
| Role-based authorization       | Every address checked by role on the server (`/employee/**`, `/technician/**`, `/admin/**`), and every object checked again with `canView` and `canWorkOn`: an employee cannot open someone else's incident, nor a technician change another's, even with the right address or through the API. |
| Secure API access              | Personal API keys (`qk_` and 40 random characters), shown once and stored only as SHA-256 hashes, revocable, expiring, dead as soon as the owner is disabled; at most 120 requests a minute; no cookies, so no CSRF risk. |
| Input validation               | Bean Validation on every form and API body; values checked again in the domain model; unreadable parameters give a 400 page.                                   |
| Cross-site request forgery     | Every form that changes something carries a CSRF token.                                                                                                        |
| Cross-site scripting           | Thymeleaf escapes all user text; a strict Content Security Policy allows only the site's own scripts, and no page has an inline script or event handler.         |
| SQL injection                  | All queries go through JPA with bound parameters; searches use the Criteria API; no SQL is built from strings.                                                 |
| Malicious files                | Attachments recognised by their first bytes, not their name; SVG and HTML refused; files served with `nosniff`, a sandboxing CSP, and as downloads unless images. |
| Clickjacking and leaks         | `frame-ancestors 'none'`, `X-Content-Type-Options`, a strict referrer policy, a permissions policy that switches off camera, location and payment.               |
| Webhook authenticity           | Messages to apps are signed with HMAC-SHA256 of the timestamp and body, so receivers can reject forgeries and replays.                                           |
| Accountability                 | Every change to an incident is in an audit trail that cannot be edited; disabled accounts lose their sessions and keys at once.                                 |
| Production settings            | The `prod` profile reads secrets from environment variables, uses PostgreSQL with its own account, trusts the HTTPS proxy's headers, and has no database console. |
:::

**Not implemented, and what a production installation would add.** HTTPS inside the application (a reverse proxy provides it on a server), password reset and address verification by e-mail, two-factor sign-in, single sign-on through OpenID Connect (which Spring Security supports), an audit log of sign-ins and setting changes, and a data retention policy.
