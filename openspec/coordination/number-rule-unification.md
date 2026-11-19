# Coordination: number-rule-unification

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "number-rule-unification"`.

## Why

Number generation is ONE department-neutral mechanism duplicated across two domains:
`patient_number_rule` + `PatientNumberServiceImpl` in lumen-patient (pat_id, 行锁烧号)
and — until the 2026-11-18 rewind — the same shape again in lumen-core for the exam
side (removed; the kernel now carries only the `NumberIssuerPort` seam). The user
ruling 2026-11-18 (three-step convergence: rename patient-number-rules → number-rules →
"why not system" → "统一一张表放 system 模式"): unify ALL number families into ONE
`number_rule` table in the SYSTEM schema behind ONE system issuance API — the same
config-catalog home as dicts and exam-setting, one console admin surface, one
mechanism. The kernel keeps only the port (done in lumen-core-registration-kernel).

## Shared Design

Single source of truth for both stacks.

### Platform model (lumen-system)

- **Table `system.number_rule`** — the generic rule family table (the
  `patient_number_rule` shape 1:1: `rule_type` / `prefix` / `suffix` / `serial_length` /
  `serial_cur` / `is_stamp` / `stamp` / `stamp_value` / `active` + the EntityBase audit
  set; `uk_number_rule_active` partial unique per type). Seeded at migration time with
  the THREE current families' defaults: `pat_id`, `accession`, `booking` — **seed
  defaults, NO cross-schema data migration** (the pat_id row is one config line the
  hospital re-enters; this avoids pg-init cross-schema grants entirely, user-approved).
- **Issuance service** — REQUIRES_NEW row-lock burned-serial (the patient precedent,
  relocated): `SELECT ... FOR UPDATE` on the active rule row, stamp-token day reset,
  `prefix + [stamp] + zero-padded serial + suffix`. The increment commits independently
  of the CALLER's transaction: a caller rollback burns the number (semantics preserved
  remotely — the 2026-11-18 ruling's core correction).
- **API** (HTTP surface owned by lumen-system):
  - `GET/POST/PUT/DELETE /api/v1/system/number-rules[/{id}]` — rule CRUD, gated by
    `system:number:*` (list/create/update/delete).
  - `POST /api/v1/system/number-rules/issue` — the machine endpoint (body
    `{ruleType}`), gated by the internal-service auth (below), fail-fast.
- **Internal-service auth for `/issue`** (design decision, DP-3 flagged): a dedicated
  internal API-key header (`X-Internal-Key`, value from gitignored config on BOTH
  sides, never committed) checked by a system-side guard; a wrong/missing key is a
  401-family rejection. NOT permitAll (the endpoint mints numbers). This is the
  platform's FIRST service-to-service call — the heavyweight alternatives (service
  accounts, mTLS) defer to a future change.
- **First Feign contract** (`lumen-api` module materializes): `NumberApi` —
  `@PostMapping("/api/v1/system/number-rules/issue")` with Sentinel fallback that
  FAILS FAST (throws — a fallback fabricating numbers is forbidden, numbers are never
  reused). No discovery (Nacos removed 2026-11-03): direct-URL config per environment.

### Consumer rewiring (lumen-patient)

- `PatientNumberService` delegates to the `NumberApi` Feign client (the local 行锁
  implementation retires); `patient_number_rule` DROPPED by a new patient-ledger
  migration (data NOT migrated — the pat_id config re-entered on the system console
  page, user-approved).
- The patient number-rule HTTP surface (`/api/v1/patient/patient-number-rules` → the
  in-flight rename to `number-rules`, user session 2026-11-18) is SUPERSEDED and
  RETIRED entirely — rule admin moves to system. `patient:rule:*` perms retire; the
  患者配置 menu seed loses the 号码规则 entry (web-side menu migration).
- The patient 行锁发号 tests relocate to lumen-system's suite (row lock + day reset +
  concurrency proofs against `system.number_rule`); patient-side tests keep the
  burned-semantic proof (a rolled-back archive creation burns the number) via the
  Feign seam (fake server or test instance).

### Console page (lumen-web)

- The 号码规则 console page (patient-domain change built it under 患者配置) MIGRATES to
  the system console IA: 系统管理 subtree, same table/fields (the page is a data-source
  + perms repoint), gated `system:number:*`; the 患者配置 menu entry is removed by the
  menu seed migration. Drawer/table conventions unchanged (existing-page edit, no
  prototype needed).

## Decisions (user rulings 2026-11-18)

1. Unify into the SYSTEM schema (not core, not patient) — config-catalog precedent
   (dicts, exam-setting), one admin surface.
2. Seed defaults instead of cross-schema data migration (pat_id row re-entered).
3. System routes `/api/v1/system/number-rules[...]+/issue`, perms `system:number:*`.
4. Console page migrates to the system console (route/menu/perms all switch).
5. The patient-side in-flight rename STOPS — the end state lands in one go here.
6. Kernel unchanged (port already landed in lumen-core-registration-kernel; its spec
   documents the platform-owned machinery).

## Change Registry

| Change | Stack | Scope | Status |
|:---|:---|:---|:---|
| `number-rule-unification-sev` | lumen-sev (system + patient + lumen-api) | system number_rule table/API/issuer, Feign contract, patient rewire + table drop, test relocation | **archived 2026-11-19** (verify PASS 8/8, reactor 339/0) |
| `number-rule-unification-web` | lumen-web | console page migration to system IA, API client repoint, perms/menu seeds | **archived 2026-11-19** (verify 6/6 test-verified + 412 green, visual confirmed) |

Archive gate: both changes reach verify overall=PASS before either archives.
