# Proposal: audit-log-unify-api

## Why

The platform's audit trail is split across two tables, two write paths, two endpoints,
two permissions, and two menus in two different console groups (登录日志 under 安全,
日志查询 under 系统配置). The sensitivity rationale for the split collapsed — operation-log
rows already carry ip/os/browser/engine — and the early-stage window means `log_sign`
holds no data worth keeping. Merging now removes a permanent dual write path / dual
partition family / dual permission system in favor of one table, one endpoint, one page
(审计日志), one permission.

Cross-stack feature — coordination doc: `openspec/coordination/audit-log-unify.md`
(decisions D1–D8, supersedes sign-log-query D4/D7). Sibling change: `audit-log-unify-web`.

## What Changes

- **BREAKING** — `GET /api/v1/system/logs/sign` and the permission
  `system:signlog:list` are removed; menu 17 登录日志 retires.
- `public.log_sign` table family is DROPPED (parent + 2026/2027/def partitions +
  parked residue + sequence) via a new roll-forward Flyway migration — no data
  migration (append-only test residue only; user ruling 2026-11-10).
- `log_info.type` becomes Integer: operations 1=新增 2=修改 3=删除 4=授权 5=重置密码
  (new additive `common` map), sign events reuse `Constants.SUCCESS=10 / FAIL=11 /
  SIGN_OUT=12` verbatim. `@Log` annotation / `LogInfo` POJO / `LogAspect` / all 21
  call sites stay String — the single translation point is `LogInfoServiceImpl.insert`.
- Sign events (`AuthServiceImpl` login success/failure, `LogoutSuccessHandlerImpl`
  logout) now land in `public.log_info` through a rewritten
  `LogSignServiceImpl.insert` (`LogSign` → `LogInfoEntity` translation); the session
  token anchor (UUID) moves from the dropped `token` column to `log_info`'s existing
  dead `session` column, which the admin query path select-excludes (supersedes
  sign-log-query D7 with the same guarantee on a new column).
- `GET /api/v1/system/logs` becomes the single audit query: filter set widens
  additively with `account` (sign-page parity); `type` filter becomes Integer.
- Copied log_sign code family (`LogSignEntity`/`LogSignMapper`/`ILogSignService`/
  `LogSignServiceImpl` + 2 mapper XMLs) is deleted and recorded in the adaptation
  ledger — they reference a dropped table.
- Dict seed gains the 「审计日志类型」 catalog (codes 1–5 + 10/11/12) for dict-driven
  label display, mirroring the role-type-dict pattern.
- Menu 12 日志查询 is retitled 审计日志 and re-parented into the 安全 group;
  permission stays `system:log:list` (single permission, supersedes sign-log-query D4).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system-data`: Sign Event Recording retargets to `log_info`; Sign Log Query Endpoint
  is REMOVED; System Log Tables drop the log_sign half; Log Query Endpoint widens
  (`account` filter, Integer type, session-column exclusion); Operation Log Recording
  gains the Integer type code vocabulary and the annotation→code translation point;
  Dict Seed Data gains the 审计日志类型 catalog.
- `data`: the shared-tables list loses `log_sign` (log_info placement rules unchanged).
- `web-system-console`: is updated by the sibling change `audit-log-unify-web` — not
  listed here to avoid double-delta ownership.

## Impact

- **Code**: `framework` (LogInfoEntity/LogInfoServiceImpl + XML, LogSign family
  deletion, AsyncFactory untouched), `common` (new `LogTypes` code map),
  `lumen-system` (LogController endpoint removal + query widening, AuthServiceImpl
  untouched, new V2x migration: drop log_sign family + type re-type + menu re-parent +
  dict seed), 5 auth test classes re-targeted to log_info.
- **API**: `GET /system/logs/sign` removed (breaking, no consumers outside the sibling
  web change); `GET /system/logs` response rows lose nothing but gain nothing either —
  the row shape drops no field operationally in use, `type` serializes as number.
- **Data**: `log_sign` family dropped; `log_info.type` re-typed integer (empty table).
- **Specs**: system-data + data deltas; web-system-console delta owned by the sibling.
- **Security**: credential-anchor isolation preserved via session-column exclusion;
  single permission `system:log:list` (server-side `@PreAuthorize` unchanged).
