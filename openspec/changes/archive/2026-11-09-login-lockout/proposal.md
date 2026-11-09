# Proposal: login-lockout

## Why

The same 2026-11-09 security review that produced `password-transport-encryption`
confirmed `POST /api/v1/system/login` has ZERO brute-force protection: no failure
counting, no account lockout, no rate limiting (`AuthServiceImpl.login` is a bare
lookup + enable + BCrypt chain). Any reachable client can try unlimited passwords per
second against the seeded `admin` (whose documented `admin123` credential is in every
wordlist, and whose `must_change_password` first-login flow has no endpoint yet, so the
weak seed password has no forced retirement path). Each attempt also burns one slow
BCrypt round — an auth-endpoint DoS vector. The OPS lineage already ships the dormant
parts: the `A0211 用户输入密码错误次数超限` error code (`ExceptionEnum.java:115`,
verbatim, unused), the `UserLogin.isAccountNonLocked()` Spring Security hook
(hardcoded `true`), and a Redis rate-limit script bean (`framework/RedisConfig
.limitScript()`) — this change is the WIRING, strictly inside lumen-system.

## What Changes

- **NEW** `org.birdbro.lumen.system.service.support.LoginLockoutService` — a two-key
  Redis model per username: `login:fail:<username>` (INCR counter with a fixed-window
  EXPIRE on first increment) and `login:lock:<username>` (set with a TTL when the
  threshold is reached). `checkLocked` / `recordFailure` / `clear` surface the three
  moments of the login loop. Parameters are committed config (NON-sensitive, unlike the
  password-crypto key): `lumen.security.login-lockout.{max-attempts:5,
  window-minutes:15, lock-minutes:30}` (user ruling 2026-11-09).
- **MODIFIED** `AuthServiceImpl.login` sequencing: decrypt (unchanged `A0400`) →
  account lookup (`A0201`) → enable (`A0202`) → **lock check (`A0211`)** → BCrypt
  (mismatch → `A0210` + `recordFailure`; threshold reached → lock key set) → success →
  `clear`. Consequences (all user-ruled): only `A0210` counts toward lockout;
  `A0201` (unknown account) and `A0400` (transport format failure) NEVER count; a
  locked account rejects even the CORRECT password with `A0211` until the lock
  expires (the lock check precedes BCrypt, saving the slow round while locked).
- **MODIFIED** `UserInfoServiceImpl.resetPassword`: admin password reset also CLEARS
  the target account's counter and lock keys — the zero-new-endpoint emergency unlock
  channel (user ruling; automatic expiry is the normal path).
- **NEW** config in the committed `lumen-system` `application.yml`: the three
  login-lockout properties with their defaults + comment (safe to commit — no
  credentials).
- **NEW** integration tests: threshold triggers lock, locked rejects correct password
  with `A0211`, successful login clears, `A0400`/`A0201` do not count, lock expires
  (short-TTL key), admin reset clears.
- **Frontend: ZERO changes** — `A0211` is an A-class envelope whose `msg` (用户输入密码
  错误次数超限) already surfaces through the existing inline error boxes (login page
  `.error-info`, users-page message path). The existing `login-page` A-class tests
  cover the rendering mechanism.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `auth-rbac`: the Login and Token Issuance requirement gains the failure-counting /
  lockout loop — the counting semantics (only wrong-password counts), the check
  ordering (`A0211` before BCrypt), the A0211 envelope on locked accounts (correct
  password included), auto-expiry, success-clears, and the admin-reset-clears channel.

## Impact

- `lumen-sev/lumen-system`: new `service/support/LoginLockoutService`; sequencing edits
  in `AuthServiceImpl` (additive `@Autowired` field, never-modify rule) and a
  `clear` call in `UserInfoServiceImpl.resetPassword`; committed `application.yml`
  three properties; integration tests in `AuthLoginTests` (+ test-scenario TTL keys
  manipulated through the real test Redis).
- `lumen-sev/common` / `framework`: **untouched** — `A0211` reused verbatim; the
  `limitScript` bean stays unconsumed (the two-key lockout model needs plain
  `StringRedisTemplate` INCR/SET with per-key TTL semantics; recorded in design as the
  reason the script bean is not the right tool here).
- API surface: no new endpoints; one reused error code. Consumers = lumen-web only,
  which needs no change.
- Out of scope (recorded, not built): IP-level rate limiting (hospital NAT shared
  egress makes it a future nginx-layer concern, user ruling), lock alerting/threshold
  notifications, the change-own-password endpoint (`must_change_password` closure —
  separate future proposal), captcha.
