# Design: login-lockout

## Reference Lineage Map

| lumen artifact | reference source |
|:--|:--|
| `A0211 用户输入密码错误次数超限` | copied `common/ExceptionEnum.java:115` — REUSED verbatim (house precedent: A0400/A0301 reuse; zero OPS enum modification) |
| `UserLogin.isAccountNonLocked()` | copied `common/.../UserLogin.java:114` — stays hardcoded `true` (the JWT-session `UserLogin` is built AFTER authentication succeeds; the lock gates the login endpoint itself, not the session objects) |
| Redis counting | plain `StringRedisTemplate` INCR/SET with per-key TTL; `framework/RedisConfig.limitScript()` bean deliberately NOT consumed (see D3) |
| placement | `service/support/` stateless collaborator ruling (service-package-tidy) — `PasswordCipherHelper` precedent |

OPS has no wired lockout in its copied login path; this is lumen-new, additive-first
(no OPS file modified).

## Decisions

- **D1 parameters as committed config (user ruling)**:
  `lumen.security.login-lockout.max-attempts: 5`, `window-minutes: 15`,
  `lock-minutes: 30` — NON-sensitive (no credentials), committed to
  `application.yml` with defaults + comment; overridable per environment via the
  gitignored `application-dev.yml`. Zero startup fail-fast needed: defaults make the
  bean work on a fresh clone (contrast: password-crypto key, which is secret).
- **D2 two-key model per username**: `login:fail:<username>` = failure counter
  (`INCR`; `EXPIRE window` set only when the INCR returns 1 — the fixed-window
  semantic, same shape as the copied limitScript); `login:lock:<username>` = lock
  marker (`SET "1" EX lock-minutes`) written when the counter reaches
  `max-attempts`. Both keys use `StringRedisTemplate` (clean string serialization,
  matching TokenService's Redis usage in the same service).
- **D3 why not the copied `limitScript` bean**: the script is a single-key
  fixed-window RATE counter ("current > count → refuse") shaped for request
  throttling. Lockout needs a two-phase semantic — count now, lock LATER with its own
  TTL, then gate on the lock key — plus `clear` on success/reset. Forcing it through
  the script means a second script call anyway; plain INCR/SET/DELETE with explicit
  TTLs is more legible and testable. The bean stays for its future intended consumer.
- **D4 check ordering in `AuthServiceImpl.login`** (decrypt → lookup → enable →
  **lock** → BCrypt → success):
  - `A0400` (decrypt) stays FIRST — format failures never reach counting (and never
    the account query: no enumeration signal; unchanged from password-transport).
  - Lock check sits AFTER the account lookup → an unknown username hits `A0201` and
    no lock key can ever exist for it (user ruling: unknown accounts cannot be
    locked, no counter is created).
  - Lock check sits BEFORE BCrypt → a locked account costs one Redis GET instead of
    one slow BCrypt round (DoS surface shrinks while locked).
  - `recordFailure` fires ONLY on the `A0210` path (BCrypt mismatch — user ruling);
    when INCR reaches `max-attempts` the lock key is written in the same step.
  - `clear` fires on the success path (after the BCrypt match, before token
    issuance side effects need no ordering — clear is idempotent).
  - Classic tradeoff recorded: an attacker CAN lock a KNOWN victim's username by
    spamming wrong passwords (denial-of-service on that account) — accepted
    industry-wide for account lockout, mitigated by auto-expiry + the admin-reset
    channel, and far cheaper for the hospital than unlimited brute force.
- **D5 `LoginLockoutService` API**: `boolean isLocked(String username)` /
  `void recordFailure(String username)` / `void clear(String username)`. Config
  injected via `@Value` with the committed defaults; a Redis outage surfaces as the
  same B-class failure the login loop already produces for `TokenService` Redis
  misses (fail-closed by dependency, no swallow-and-fake-open).
- **D6 admin-reset clear (user ruling)**: `UserInfoServiceImpl.resetPassword` calls
  `clear(username)` after the successful update — the emergency unlock channel with
  zero new endpoints/permissions; additive `@Autowired` field (never-modify rule,
  `LogController` precedent).
- **D7 error surface**: `A0211` verbatim (msg unchanged — the copied text IS the
  contract); login responses already render any A-class through the existing
  envelope machinery. Frontend zero-change (the inline `.error-info` box shows the
  msg; `users.ts` A0400-only interception untouched — A0211 on login is not an
  ApiError path the web change special-cases).
- **D8 no concurrency ceremony**: INCR is atomic; the check-then-act between the
  lock GET and BCrypt has a benign race window (two simultaneous 5th failures both
  set the lock key idempotently — same outcome). No distributed locks.

## Error Handling

| case | code | notes |
|:--|:--|:--|
| BCrypt mismatch under threshold | `A0210` | unchanged + counter INCR |
| counter reaches `max-attempts` | `A0210` on THAT attempt, lock set | the NEXT attempt answers `A0211` (the 5th wrong try is still a wrong-try answer, not a lock answer) |
| locked account, any password | `A0211` | before BCrypt; msg verbatim |
| unknown account | `A0201` | before lock check; no keys touched |
| transport failure | `A0400` | before everything; no keys touched |
| Redis down | B-class (existing machinery) | login already depends on Redis (token state); no special casing |

## Testing (house integration harness, TDD)

The team test server's REAL Redis backs the lock semantics (same base as token state).

- **T1 lockout loop (integration, `AuthLoginTests`)**: with a test-profile override of
  `max-attempts` small enough to script (e.g. property-overridden 3) — 3 wrong
  logins → 4th attempt (EVEN with the correct password) → `A0211`; unknown-account
  probe in between → `A0201` and no lock created for it.
- **T2 expiry (integration)**: pre-plant the lock key with a ~10s TTL via
  `StringRedisTemplate` (the lock-minutes config governs the TTL VALUE the service
  writes; the test plants its own short key to prove the gate reads the key rather
  than the config) → `A0211` → after expiry → correct-password login succeeds.
- **T3 clear paths (integration)**: failures below threshold → successful login →
  counter gone (subsequent failures start from zero — verified by re-reaching the
  threshold needing the full count again); admin reset on a locked account →
  immediate successful login.
- **T4 non-counting (integration)**: plaintext (`A0400`) and unknown-account
  (`A0201`) attempts interleaved with real failures — the threshold still lands on
  exactly the Nth REAL wrong-password attempt.
- **T5 service unit (integration-lite)**: `LoginLockoutService` against the real
  Redis — INCR window semantics (first increment sets the TTL), lock written at the
  threshold, `clear` deletes both keys.
- Full suite stays green (existing suites' run-admin logins never accumulate 5
  failures, and sweeps are unaffected); `./mvnw test -q` from `lumen-sev/`.
