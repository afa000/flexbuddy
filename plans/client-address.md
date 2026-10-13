# Plan: stop visitors from choosing their own address for the per-connection limits

Planned against `origin/main` at 2b7a47d.

## The problem, reproduced

Every per-connection limit keys on `request.getRemoteAddr()`:
- `login-ip` (20 failed sign-ins per 15 min);
- `register-ip` (10 sign-ups an hour);
- `reset-ip` (password-reset emails);
- `code-ip` (verification, two-step and deletion codes).

`application.properties` line 88 sets
`server.forward-headers-strategy=framework`, so Spring's
`ForwardedHeaderFilter` replaces that address with the **first**
(left-most) entry of `X-Forwarded-For`. A visitor can put anything there.

I ran the app locally on 2b7a47d and posted to `/register`:
- **With no header:** after the hourly limit, every sign-up got
  **429**.
- **With a different made-up address on each request**
  (`X-Forwarded-For: 203.0.113.N`): **12 of 12 got through**, each
  redirecting to `/verify-email`.

**What Render does with the header:** I couldn't reach the live site or
Render's docs from here. Community and feedback threads on Render say its
proxy **appends** to `X-Forwarded-For` rather than replacing it, so a
value the visitor sent stays at the left:
- [Render feedback](https://feedback.render.com/features/p/send-the-correct-xforwardedfor)
- [Render community: client IPs](https://community.render.com/t/accessing-client-ips-in-a-node-express-app/36282)
- [Render community: missing X-Forwarded-For](https://community.render.com/t/missing-x-forwarded-for-header-on-api-rewrites-to-backend/24481)

They also mention Cloudflare headers (`CF-Connecting-IP`,
`True-Client-IP`) on some requests, which isn't confirmed.

**Impact:**
- **Mass emails.** The per-email limits still hold, but someone can
  script sign-ups or reset requests to unlimited addresses. Each one
  sends a verification code from `flexbuddysupport@gmail.com`. Gmail
  would likely suspend the account's sending, and then sign-up
  verification, password reset and two-step email codes stop working
  for everyone.
- **Password guessing.** Guessing across many accounts loses its
  per-connection cap.

## 1. Goal and out-of-scope

**Goal:**
- **The address used** for every limit is the one Render's own proxy
  saw. By default that's the **right-most** `X-Forwarded-For` entry,
  the one Render appends. Anything a visitor sends is ignored.
- **The rule is configurable** through environment variables, to use a
  named header or to skip more proxy hops. A different setting can then
  be applied without a code change if Render turns out to route
  through Cloudflare first.
- **Diagnostics:** a privacy-safe log line, at most once an hour, says
  which forwarding headers arrive and how many entries they have,
  **without any IP addresses**. That's enough to confirm the setting
  from Render's logs.
- **No call-site changes:** every existing `getRemoteAddr()` caller
  gets the corrected address automatically. That includes the limiter
  and Spring Security's `WebAuthenticationDetails`, as used by
  `LoginAttemptEvents`. The `https` scheme and host handling from
  `ForwardedHeaderFilter` stay exactly as they are. Calendar links and
  Google's `redirect_uri` depend on them.

**Out of scope:**
- **New limits** or changed limit numbers.
- **A CAPTCHA.**
- **Logging visitor IPs.** The app doesn't log them today and still
  won't.
- **Cloudflare's own IP ranges.** The plan doesn't check whether an
  address belongs to Cloudflare; the setting covers that case.

## 2. Data model and migration

None.

## 3. Backend files

### `security/ClientAddressProperties.java` (new)

```java
/** Where the visitor's real address comes from behind Render's proxy. */
@ConfigurationProperties("flexbuddy.security.client-address")
public record ClientAddressProperties(
        /** Read this header instead of X-Forwarded-For, e.g. "CF-Connecting-IP"; blank uses X-Forwarded-For. */
        @DefaultValue("") String header,
        /** Proxies we trust that append to X-Forwarded-For; 1 means the right-most entry is the visitor. */
        @DefaultValue("1") int trustedHops) {
}
```

### `security/ClientAddressFilter.java` (new; `OncePerRequestFilter`)

**Finding the real request:** walk to the innermost request with
`while (r instanceof ServletRequestWrapper w) r = w.getRequest();`.
That's where the **original** headers are; `ForwardedHeaderFilter`'s
wrapper hides `X-Forwarded-*` from everything after it.
- `peer` is `raw.getRemoteAddr()`, the address of Render's proxy
  connection.

**Resolving the address,** with pure static helpers:
- **Header mode:** if `header` is set and the raw request has it, use its
  value. If it isn't a valid address, fall back to the forwarded rule.
- **Forwarded mode:** split `X-Forwarded-For` on commas, trim the
  entries and drop empty ones. Take the entry at
  `entries.size() - trustedHops`, which for `trustedHops = 1` is the
  right-most.
  - If the list is shorter than `trustedHops`, or the entry isn't a
    valid address, use `peer`.
- **No header at all** (local runs and tests): use `peer`.
- **What counts as a valid address:** IPv4 dotted quad or IPv6.
  - Strip a `:port` from IPv4 and `[...]:port` brackets from IPv6.
  - Reject anything with letters other than hex digits, or with spaces.
  - Use `java.net.InetAddress` only on literal input: check with a
    regex first, so no hostname is ever looked up.

**Wrapping the request:** pass on an `HttpServletRequestWrapper` whose
`getRemoteAddr()` and `getRemoteHost()` return the resolved address.

**Diagnostics:** at most once an hour (an `AtomicLong` holding the last
log time from the `Clock`), log at INFO:

```
client address: x-forwarded-for entries=2; cf-connecting-ip=present (equals entry 1 of 2); true-client-ip=absent;
x-real-ip=absent; peer private=true; using entry 2 of 2
```

This line never contains an address. It only says which headers exist,
how many entries there are, which entry a Cloudflare header equals, and
whether the peer is a private address (`InetAddress.isSiteLocalAddress`
or loopback).

### Registering the filter (`config/ClientAddressConfig.java`, new)

```java
@Bean
FilterRegistrationBean<ClientAddressFilter> clientAddressFilter(ClientAddressProperties properties, Clock clock) {
    FilterRegistrationBean<ClientAddressFilter> bean = new FilterRegistrationBean<>(new ClientAddressFilter(properties, clock));
    // After Spring's ForwardedHeaderFilter (scheme and host) and before Spring Security (-100).
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return bean;
}
```

Also add `@EnableConfigurationProperties(ClientAddressProperties.class)`.
Boot registers `ForwardedHeaderFilter` at `HIGHEST_PRECEDENCE`, so this
runs inside its wrapper and before the security chain. That means:
- `LoginAttemptFilter`, `LockAwareFailureHandler`,
  `VerifiedLoginSuccessHandler`, every controller, `GoogleAccountService`
  (through `RequestContextHolder`) and `WebAuthenticationDetails` all see
  the corrected `getRemoteAddr()`.
- Leave `server.forward-headers-strategy=framework` unchanged.

### `src/main/resources/application.properties`

After `server.forward-headers-strategy=framework`, add:

```
# The visitor's address for sign-in, sign-up and email limits: the right-most X-Forwarded-For entry, the one Render's
# proxy adds; anything the visitor sends to the left is ignored. Set a header (e.g. CF-Connecting-IP) or more trusted
# hops only after the hourly "client address:" log line on Render shows it is needed.
flexbuddy.security.client-address.header=${FLEXBUDDY_CLIENT_ADDRESS_HEADER:}
flexbuddy.security.client-address.trusted-hops=${FLEXBUDDY_CLIENT_ADDRESS_HOPS:1}
```

### `render.yaml`

No change. The defaults apply, and the two environment variables are only
set if the log line shows they're needed (section 7).

## 4. Frontend files

None.

## 5. Ripple list

- **Limits:** the same numbers, now keyed on an address the visitor can't
  choose. Per-email limits are unchanged.
- **Drivers sharing a connection** (the same home Wi-Fi) share limits, as
  intended before.
- **If Render actually routes through Cloudflare** and appends after it,
  the right-most entry would be a Cloudflare edge address. Many drivers
  would then share a bucket, and busy times could hit `login-ip` (20 per
  15 min) for unrelated people.
  - **How to spot it:** the log line shows two or more entries, with
    `cf-connecting-ip` equal to an entry other than the last.
  - **The fix is a setting, not code:** set
    `FLEXBUDDY_CLIENT_ADDRESS_HOPS=2`, or
    `FLEXBUDDY_CLIENT_ADDRESS_HEADER=CF-Connecting-IP`.
- **Scheme and host:** unchanged. `ForwardedHeaderFilter` still runs
  first. `CalendarController` (`ServletUriComponentsBuilder.fromCurrentContextPath`)
  and Google's `{baseUrl}` redirect stay `https`.
- **Privacy:** no addresses are logged, as before. The policy needs no
  change.
- **Error alerts:** unaffected.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data. `sw.js` is unchanged.
- **Phone layout:** none.

## 6. Tests to add

**`security/ClientAddressFilterTest`** (unit; build `MockHttpServletRequest`s
and wrap them in a `ServletRequestWrapper` to mimic
`ForwardedHeaderFilter`, then run the filter and capture the request
passed on). Each pins one rule:
- **The pass-through rule:** `X-Forwarded-For: 203.0.113.9, 198.51.100.7`
  with peer `10.0.0.5` gives `198.51.100.7`. The spoofed left entry is
  ignored, and the right-most is used.
- **One entry:** `198.51.100.7` gives `198.51.100.7`.
- **No header:** the peer, `10.0.0.5`.
- **Two hops:** `trustedHops = 2` with `a, b, c` gives `b`.
  `trustedHops = 3` with two entries gives the peer.
- **Header mode:** `header = "CF-Connecting-IP"` with
  `CF-Connecting-IP: 198.51.100.7` gives that value, and a spoofed
  `X-Forwarded-For` is ignored. When the header is missing, it falls back
  to the forwarded rule.
- **Invalid values** fall back:
  - a right-most entry of `unknown`, `evil.example` or `1.2.3`;
  - an empty `X-Forwarded-For`;
  - a header with spaces inside an address.
- **Ports and brackets** are stripped: `198.51.100.7:4433` gives
  `198.51.100.7`, and `[2001:db8::1]:443` gives `2001:db8::1`.
- **The original headers are read through the wrapper,** where an inner
  wrapper hides `X-Forwarded-For` from `getHeader`.
- **The diagnostic line** contains `entries=2` and contains no `.` or `:`
  digit sequences that form an address (regex check). It's written once,
  and not again within the hour (using `MutableClock`).

**`ClientAddressIntegrationTest`** (new; `@SpringBootTest`, MockMvc, test
profile):
- **The reproduction:** 11 `POST /register` requests from the same
  `remoteAddr`, each with a different `X-Forwarded-For` left entry and
  the same right-most entry. The 11th returns **429**.
- **Different real clients:** with different right-most entries, the 11th
  still redirects to `/verify-email`.
- **Sign-in tracking:** a failed sign-in with
  `X-Forwarded-For: 203.0.113.1, 198.51.100.7` records `login-ip` under
  `198.51.100.7`. Check with `AttemptLimiter` after 20 failures:
  `isLocked(LOGIN_IP, "198.51.100.7")` is true, and the spoofed
  `203.0.113.1` isn't locked.
- **Scheme is unchanged:** a request to the `CalendarController`
  endpoint that builds an absolute link (`ServletUriComponentsBuilder.fromCurrentContextPath`,
  line 81), sent with `X-Forwarded-Proto: https`, still produces a link
  starting with `https://`.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks (after deploy, on Render)

1. **Read the diagnostic.** In Render → the web service → **Logs**, filter
   for `client address:`. Within the first hour of traffic (open the site
   once), one line appears. Record it.
   - `x-forwarded-for entries=1 … peer private=true; using entry 1 of 1`:
     **the default is right**, and nothing needs setting.
   - `entries=2` with `cf-connecting-ip=present (equals entry 1 of 2)`:
     Cloudflare appended before Render. Set
     `FLEXBUDDY_CLIENT_ADDRESS_HEADER=CF-Connecting-IP` in Render →
     Environment, which redeploys, then repeat this step.
   - **Anything else:** paste the line (it holds no addresses) to the
     planning session.
2. **The spoof test,** from a laptop on Wi-Fi. Use your own Gmail
   plus-addresses, so only you get the codes:

   ```
   for i in $(seq 1 11); do
     curl -s -o /dev/null -w "%{http_code}\n" -c j -b j \
       -H "X-Forwarded-For: 203.0.113.$i" \
       --data-urlencode "_csrf=$(curl -s -c j -b j https://flexbuddy.onrender.com/register | grep -o 'name=\"_csrf\" value=\"[^\"]*' | cut -d'"' -f4)" \
       --data-urlencode "displayName=Limit test" \
       --data-urlencode "email=flexbuddysupport+limit$i@gmail.com" \
       --data-urlencode "password=limit-test-pass-$i" \
       https://flexbuddy.onrender.com/register
   done
   ```

   - The first ten print `302`, and the **11th prints `429`**. It's
     earlier if you already signed up from that connection within the
     hour, since every attempt counts. Before the fix, all 11 printed
     `302`.
   - The ten test accounts stay unverified and are deleted automatically
     after 24 hours.
3. **Different networks don't share limits.** Right after step 2, on a
   phone using **cellular data** (Wi-Fi off), open Create account. It
   loads and a sign-up goes through to the code page.
4. **The normal sign-in still works** on desktop, the Android Play app and
   an iPhone. A wrong password 5 times shows the 15-minute lock message,
   as before.
5. **Calendar links** in Account → Reminders & calendar still start with
   `https://flexbuddy.onrender.com/calendar/`.

## 8. Commit message

```
fix(security): key connection limits on the address render's proxy saw

The sign-in, sign-up, password reset and email code limits per
connection used the first X-Forwarded-For entry, which the visitor
writes. Sending a different made-up address on each request got past
all of them, so a script could send verification and reset emails to
any number of addresses from the support Gmail account until Gmail
stopped it.

A filter now takes the right-most entry, the one Render's proxy adds,
and hands that address to every caller of getRemoteAddr, including
the sign-in limit and Google sign-in, while the scheme and host
handling stays as it was. A named header or more proxy hops can be
set from the environment, and an hourly log line that carries no
addresses shows which forwarding headers arrive so the setting can be
confirmed on Render.
```

## 9. Open questions

None that block shipping. Step 1 of the manual checks decides whether
either environment variable is needed.
