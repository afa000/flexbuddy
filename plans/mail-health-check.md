# Plan: keep the health check independent of the mail server

Planned against `origin/main` at 89bde5d (password reset shipped).

## Why this comes first

Adding `spring-boot-starter-mail` for password reset also turned on Spring
Boot's mail health indicator. It folds into `/actuator/health`, which is
the `healthCheckPath` Render polls in `render.yaml`. I ran the current
jar locally against Postgres and got these results:

| `FLEXBUDDY_MAIL_HOST` | `/actuator/health` |
| --- | --- |
| empty (no mail configured) | **503**, `"mail":{"status":"DOWN"}`, overall `DOWN` |
| set, SMTP server unreachable | **503**, overall `DOWN` |
| set, with `management.health.mail.enabled=false` | **200**, overall `UP` |

This creates three problems in production:

- Every Render health check opens an SMTP connection and signs in to
  Gmail. Render polls often, so the app signs in to Gmail all day. Gmail
  can rate-limit or temporarily block that, and then the health check
  goes DOWN.
- A Gmail outage, or a revoked app password, makes Render treat the whole
  web service as unhealthy. A deploy fails its health check and is rolled
  back, even though every page would have worked.
- If the `FLEXBUDDY_MAIL_*` variables are ever cleared, every deploy fails.

Mail is optional by design: `MailConfig` falls back to the logging mailer
when no host is set. The health check should follow the same rule.

## 1. Goal and out-of-scope

**Goal:** `/actuator/health` reports on the database and the app itself,
never on the mail server.

**Out of scope:**
- Any other actuator change: exposure, details or extra endpoints.
- Mail retries.
- Alerting. That is planned separately in `plan/error-alerts`.

## 2. Data model and migration

None.

## 3. Backend files

- `src/main/resources/application.properties`: in the mail block, after
  `flexbuddy.public-url`, add:

  ```
  # Mail is optional, so the health check Render polls never depends on the SMTP server.
  management.health.mail.enabled=false
  ```

Nothing else changes. `MailConfig`, the mailers and `render.yaml` stay
as they are.

## 4. Frontend files

None.

## 5. Ripple list

- Render's health check stops signing in to Gmail on every poll.
- A broken mail setup is no longer visible in the health check. It shows
  up as the WARN line `password reset email could not be sent (...)` from
  `SmtpPasswordResetMailer`. `plan/error-alerts` makes failures like this
  reach you by email; until then, the Render logs are where to look.
- The `db` indicator still counts, so the health check still goes DOWN
  when Postgres is unreachable. That is the behaviour Render needs.

## 6. Tests to add

New `src/test/java/com/angel/flexbuddy/HealthCheckIntegrationTest.java`:

- Set up with `@SpringBootTest`, `@AutoConfigureMockMvc`,
  `@ActiveProfiles("test")` and
  `properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1"}`.
  Port 1 refuses connections immediately, so the test stays fast.
- `healthIsUpWhenTheMailServerIsUnreachable`: `GET /actuator/health`
  without signing in returns 200 and the body contains `"status":"UP"`.
  This pins that the mail indicator is off. Removing the property line
  makes it fail with 503. I confirmed that through the jar, with the mail
  host pointing at a closed local port.
- `healthNeedsNoSignIn`: the same request is not redirected to `/login`.
  This pins the existing `permitAll` on `/actuator/health`.

Run `mvn test` (441 tests passed, 1 skipped, on 89bde5d) and
`node --test "src/test/js/*.test.js"` (45 passed).

## 7. Manual checks

There's no UI, so there is nothing to check at 375–430px. Desktop
checks:

1. Locally, start the app with `FLEXBUDDY_MAIL_HOST=127.0.0.1` and
   `FLEXBUDDY_MAIL_PORT=1`. `curl -i localhost:8080/actuator/health`
   should return `HTTP/1.1 200` and `{"status":"UP",...}`.
2. Do the same with `FLEXBUDDY_MAIL_HOST` empty. It should also return 200.
3. After the deploy, open **Events** on the Render web service. The
   deploy should say "Deploy live" with no health-check failure.
4. Request a password reset for your own account on the live site. The
   email should still arrive, which shows mail itself is unaffected.

## 8. Commit message

```
fix(health): keep the mail server out of the health check

Adding mail for password reset also turned on Spring Boot's mail
health indicator, so every health check Render runs signed in to
Gmail's SMTP server. When the server could not be reached, or no mail
host was set, /actuator/health answered 503 and Render treated the
whole service as down, which can fail a deploy or roll it back.

Mail is optional: with no host the app logs instead of sending. The
health check now follows the same rule and reports on the database
and the app only. A test pins a 200 from the health check while the
mail server refuses connections.
```

## 9. Open questions

- None that block shipping. If Render has shown failed or rolled-back
  deploys since password reset shipped, this is the likely cause.
