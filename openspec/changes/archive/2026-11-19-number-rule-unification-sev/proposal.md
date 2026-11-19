# Proposal — number-rule-unification-sev

> Cross-stack parent: `openspec/coordination/number-rule-unification.md` (user rulings
> 2026-11-18 recorded there). This is the backend half; the console-page migration is
> `number-rule-unification-web`.

## Why

Number generation is ONE department-neutral mechanism duplicated across domains:
`patient_number_rule` + the local 行锁发号器 in lumen-patient (pat_id), and — until the
2026-11-18 rewind — the same shape again in lumen-core for the exam side (removed; the
kernel keeps only `NumberIssuerPort`). User ruling: unify ALL families into ONE
`number_rule` table in the SYSTEM schema behind ONE system issuance API — the
config-catalog home (dicts, exam-setting), one admin surface, one mechanism. The
burned-serial semantic survives remotely (the platform commits the increment; a caller
rollback burns the number — the 2026-11-18 ruling's core correction).

## What Changes

- **lumen-system gains the platform number-rule domain** (package
  `org.birdbro.lumen.manage`, the 系统管理域): `number_rule` table (generic rule
  families, `uk_number_rule_active` per type) + REQUIRES_NEW row-lock burned-serial
  issuer (the patient precedent relocated: FOR UPDATE, stamp-token day reset,
  `prefix+[stamp]+补零流水+suffix`) + rule CRUD + the machine issuance endpoint
  (`/api/v1/system/number-rules[/...]+/issue`), gated `system:number:*`.
- **`lumen-api` module materializes** (the planned inter-service contract home):
  `NumberApi` Feign contract with a FAIL-FAST Sentinel fallback (a fallback fabricating
  numbers is forbidden — numbers are never reused); no discovery (Nacos removed
  2026-11-03), direct-URL config per environment.
- **Internal-service auth on `/issue`**: `X-Internal-Key` header guard (gitignored
  config on both sides); NOT permitAll — the endpoint mints numbers.
- **lumen-patient rewires**: `PatientNumberService` delegates to `NumberApi`;
  `patient_number_rule` DROPPED by a patient-ledger migration (data NOT migrated — the
  pat_id config row re-entered on the system console page, user-approved); the patient
  number-rule HTTP surface RETIRES entirely (the in-flight `patient-number-rules` →
  `number-rules` rename is superseded, user ruling); `patient:rule:*` perms retire.
- **Menu seed migration** (system ledger): the 号码规则 console entry leaves 设置 L1's
  患者配置 group and joins the 系统管理 subtree with the `system:number:*` button
  perms (the web change builds the page against this seed).
- **Tests relocate**: the row-lock/day-reset/concurrency mechanism proofs move to the
  lumen-system suite; lumen-patient keeps the burned-semantic proof (a rolled-back
  archive creation burns the number) through the Feign seam (mocked contract bean).
- System-ledger migration number: next free version at apply time (V38 is taken by the
  parallel exam-method work in flight).
- Out of scope: the kernel (its port landed in lumen-core-registration-kernel, zero
  edits here), the console page (web change), service-account/mTLS auth (future).

## Capabilities

### New Capabilities
- `number-rule`: the platform number-rule service — rule directory (CRUD + seeds +
  perms), platform issuance (row-lock burned-serial + machine endpoint), and the
  internal-service Feign contract (fail-fast, internal-key auth).

### Modified Capabilities
- `patient-domain`: Image Number Issuance becomes delegation to the platform service
  (the observable semantics hold; the burned-rollback proof stays patient-side); the
  patient-side Number Rule Management API requirement is REMOVED (routes/perms retire).
