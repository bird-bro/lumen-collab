# Tasks: login-lockout

## 1. Lockout service (TDD)

- [x] 1.1 Add the committed config to `lumen-system/application.yml`:
      `lumen.security.login-lockout.{max-attempts: 5, window-minutes: 15,
      lock-minutes: 30}` + comment (non-sensitive defaults, user ruling; env/dev-yml
      override path noted).
- [x] 1.2 Write failing `LoginLockoutServiceTest` (design T5, house integration base —
      real Redis): `recordFailure` INCRs with window EXPIRE on first increment;
      reaching `max-attempts` writes the lock key; `isLocked` gates; `clear` deletes
      both keys; a pre-planted short-TTL lock key expires naturally (T2 semantics at
      the service level).
- [x] 1.3 Implement `org.birdbro.lumen.system.service.support.LoginLockoutService`
      (`@Component`, `@author birdbro`, `StringRedisTemplate`, `@Value` defaults per
      design D1/D2/D5). Green: 1.2 passes.

## 2. Login loop wiring (TDD)

- [x] 2.1 Write failing integration tests in `AuthLoginTests` (design T1-T4):
      threshold lock → next attempt `A0211` even with the correct password;
      unknown-account probes never create keys; plaintext attempts don't count;
      successful login clears (re-threshold needs the full count again); admin reset
      on a locked account unlocks immediately; planted 10s lock key expires and the
      account recovers. Existing suites stay untouched (run-admin logins succeed on
      the first try — no accumulation).
- [x] 2.2 Wire `AuthServiceImpl.login` (additive `@Autowired` field, never-modify
      rule): lock check after the enable branch / before BCrypt (`A0211` via
      `ERROR_A0211` verbatim); `recordFailure` on the `A0210` throw path;
      `clear` on the success path. Green: 2.1 (lock/expiry/non-counting scenarios).
- [x] 2.3 Wire `UserInfoServiceImpl.resetPassword`: `clear(username)` after the
      successful update (additive field, design D6). Green: 2.1 (reset-unlock
      scenario).

## 3. Verification

- [x] 3.1 `export JAVA_HOME=...jdk-11.jdk/.../Home && ./mvnw test -q` from `lumen-sev/`
      — all green (reactor rule; evidence in verify.md).
- [x] 3.2 Confirm frontend zero-change: `A0211` msg renders through the existing
      inline error box (no web change required — the existing `login-page` A-class
      test already pins the rendering mechanism); note the evidence in verify.md.
