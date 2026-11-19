# Design

## Context

lumen-system's ledger is at V37 (V38 is taken by the parallel exam-method work in
flight — this change takes the NEXT free version at apply time and must not touch V38).
The patient ledger carries `patient_number_rule` (patient-domain archived) and the
user's in-flight controller rename (PatientNumberController → NumberRuleController,
route → number-rules) which this change SUPERSEDES and reverts. `lumen-api` does not
exist yet; the platform has NO cross-service call anywhere, so this change also
bootstraps the Feign machinery (no discovery since the 2026-11-03 local-config ruling —
direct URLs). The kernel's `NumberIssuerPort` (lumen-core-registration-kernel) is DONE
and unchanged here; radiology/us consumers adapt it to `NumberApi` when they arrive.

## Goals / Non-Goals

**Goals:** platform-owned `number_rule` + issuance API + internal-key guard; the first
Feign contract (`lumen-api`); patient rewired + old table/routes/perms retired;
mechanism tests relocated to lumen-system; menu seed moved (system ledger).
**Non-Goals:** the kernel (zero edits), the console page (web change), service
accounts/mTLS, data migration of the old pat_id config row (seed defaults instead —
user-approved).

## Decisions

- **D1 — Domain placement**: `org.birdbro.lumen.manage` (the 系统管理域 — number rules
  are system-console administration, same home as dicts/users). Controller
  `NumberRuleController`, business services `NumberRuleService`/`NumberIssuerService`
  (interface + impl per the 2026-11-13 ruling), mapper `NumberRuleMapper`, entity
  `NumberRule` (the `PatientNumberRule` shape 1:1). Perms family `system:number:*`
  (list/create/update/delete).
- **D2 — Migration**: one system-ledger migration (next free V) creating
  `system.number_rule` + seeding the three family defaults (pat_id / accession /
  booking) + the menu seed move (号码规则 leaves 设置 L1 患者配置 group; a 系统管理 C
  node with `system:number:list` + the four button perms join the M=1 subtree — the
  web change consumes this seed). Comment lines carry no semicolons (the V36 house
  rule).
- **D3 — Issuer**: the patient `PatientNumberServiceImpl` logic relocated 1:1
  (REQUIRES_NEW row lock, stamp-token day reset, padPre format, B0001 no-rule /
  A0400 bad-stamp mapping) — the mechanism tests relocate with it.
- **D4 — Internal-service auth**: `X-Internal-Key` header checked by a handler
  interceptor scoped to the `/issue` endpoint; the key value rides gitignored config
  (`application-dev.yml` / env var) on BOTH sides; missing/wrong key → auth rejection.
  NOT permitAll. The heavyweight alternatives (service accounts, mTLS) defer.
- **D5 — lumen-api module**: plain jar in the aggregator (after `common`), deps
  minimal (spring-cloud-openfeign for `@FeignClient`, spring-web for mapping
  annotations — both version-managed by the existing BOM train; NO new pins). `NumberApi`
  declares the issue endpoint + `NumberIssueRequest`/`NumberIssueResponse` DTOs; the
  fail-fast fallback class sits beside it and THROWS. Consumers enable Feign clients
  with direct-URL config (`lumen.api.system.url`, gitignored per environment).
- **D6 — Patient rewire**: `PatientNumberService` delegates to `NumberApi` (the local
  row-lock implementation retires); the patient-ledger migration (next free V) DROPS
  `patient_number_rule`; the old controller + its DTOs + `patient:rule:*` perm
  references + the in-flight rename artifacts are removed; patient tests mock
  `NumberApi` (`@MockBean`) keeping the burned-semantic proof patient-side.
- **D7 — Test topology**: the row-lock/day-reset/concurrency tests move to
  lumen-system's suite (against `system.number_rule`, the `k?_`-style fixture-family
  discipline with hard-DELETE sweeps); system's boot test extends to the new table.

## Risks / Trade-offs

- **First Feign call = new failure surface**: patient archive creation now depends on
  system reachability. Accepted (system is already the JWT issuer — the platform dies
  with it); Sentinel fallback fails fast, never degrades to fabricated numbers.
- **Seed-instead-of-migrate**: the live pat_id row's config (prefix/serial length) is
  re-entered manually once per deployment — documented in the coordination doc.
- **V-number coordination with the parallel session**: the migration takes the next
  free system-ledger version at apply time; V38 stays theirs.
