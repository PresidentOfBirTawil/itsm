# 10. Security

| Threat or requirement | Measure | Where |
|---|---|---|
| **Authentication** | Form login with e-mail and password; the session ID changes at sign-in (no session fixation); sign-out ends the session. | `SecurityConfig` |
| **Password guessing** | After 5 wrong passwords for one address in 15 minutes, sign-in for it is paused for 15 minutes, even with the right password. Unknown addresses are paused the same way, so the answer never reveals which accounts exist. | `LoginAttempts`, `LockoutAuthenticationProvider` |
| **Password storage** | BCrypt hashes only (salted, deliberately slow); minimum length 8. | `PasswordEncoder` |
| **Role-based authorisation** | Every address checked on the server by role (`/employee/**`, `/technician/**`, `/admin/**`), and every object checked again (`canView`, `canWorkOn`): a technician cannot change another technician's incident, an employee cannot open someone else's, even with the right address or through the API. | `SecurityConfig`, `User` subclasses |
| **API access** | Personal API keys, 238 bits of randomness, stored only as SHA-256 hashes, shown once, revocable, expiring, and dead once the owner is disabled. Stateless: no cookies, so no CSRF risk. 120 requests a minute per key. | `api/` |
| **Cross-site request forgery** | Every state-changing form carries a CSRF token. | Spring Security |
| **Cross-site scripting** | All user text is escaped by Thymeleaf; the chat renders bold text by splitting, never as HTML. A strict **Content Security Policy** allows only the site's own scripts (there is no inline script or event handler anywhere), so even injected markup could not run code. | Templates, `SecurityConfig` |
| **Malicious files** | Attachments are recognised by their first bytes, not by name or browser claim; SVG and HTML are refused; files are served with `nosniff`, a sandboxing CSP, and as downloads unless they are images; names are cleaned. | `FileKind`, `AttachmentController` |
| **SQL injection** | All queries through JPA with bound parameters; searches with the Criteria API. | `repository/` |
| **Clickjacking, sniffing, leaks** | `X-Frame-Options: DENY` and `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, a `Permissions-Policy` that switches off camera, location and payment. | `SecurityConfig` |
| **Webhook authenticity** | Messages to apps are signed (HMAC-SHA256 of timestamp and body) so receivers can reject forgeries and replays; addresses must be http(s) without credentials. | `WebhookSender`, `WebhookService` |
| **Account control** | A disabled account cannot sign in, its open sessions end at once, and its API keys stop working. | `SignedInSessions` |
| **No information leaks** | One sign-in error message for every failure; error pages and API errors never show stack traces or SQL. | `ErrorHandler`, `ApiExceptionHandler` |
| **Accountability** | Every change to an incident is in an audit trail that cannot be edited. | `AuditTrailListener` |
| **Production settings** | `prod` profile: secrets from environment variables, PostgreSQL with its own account, trusted proxy headers for HTTPS, no database console. | `application-prod.properties` |

Not implemented, and what a production version would add: HTTPS inside the application (provided by
the reverse proxy in a server installation), password reset by e-mail and address verification,
multi-factor sign-in, single sign-on (OpenID Connect, which Spring Security supports), an audit log of
sign-ins and setting changes, and a data retention policy.
