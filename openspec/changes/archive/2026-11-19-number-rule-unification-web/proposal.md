# Proposal — number-rule-unification-web

> Cross-stack parent: `openspec/coordination/number-rule-unification.md`. This is the
> console half; the platform service is `number-rule-unification-sev`.

## Why

The 号码规则 console page (patient-domain change built it under 设置 L1's 患者配置
group, `patient:rule:*` perms, patient-service data source) administrates a table that
now lives in the SYSTEM schema behind the system service (`number-rule-unification-sev`).
The page migrates to the 系统管理 IA with `system:number:*` perms and the system
data source — same table, same drawer patterns; the patient-side page entry retires
with the patient routes.

## What Changes

- The page's data source repoints to `/api/v1/system/number-rules[...]+` (new
  `src/api/number-rules.ts`; the patient `patient.ts` rule section retires).
- Perms switch wholesale: `patient:rule:*` → `system:number:*` (page gate +
  toolbar/drawer button gates).
- Mount point moves: the menu seed (system ledger, the sev change) places 号码规则
  under the 系统管理 subtree — the page registers at the matching console route; the
  患者配置 menu entry disappears with the seed migration.
- The 规则类型 field becomes a MULTI-FAMILY select (`pat_id` 影像号 / `accession`
  检查号 / `booking` 预约号) — the v1 fixed-影像号 constraint dissolves with the
  platform families.
- Existing-page edit (no prototype per the house rule); drawer/table conventions
  unchanged (top-right-end close, mask-click-locked, field-level validation).
- Out of scope: backend (the sev change), the kernel, any new page design.

## Capabilities

### Modified Capabilities
- `web-system-console`: Number Rule Page Shell and Number Rule Form and Delete migrate
  to the system IA (data source, perms, mount point, multi-family type select) — the
  scenario set is preserved and extended with the multi-family type selection.
