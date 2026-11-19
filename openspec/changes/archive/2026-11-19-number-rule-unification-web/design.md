# Design

## Context

The page exists (`src/pages/system/...` or the 患者配置-adjacent location, built by
patient-domain) with tests; the patient-domain archive commit shows the console entry
under 设置 L1's 患者配置 group seeded by V22/V32. The sev change's menu migration moves
the C node into the 系统管理 subtree — this change consumes that seed and must NOT
edit menu seeds (backend owns menu_info).

## Goals / Non-Goals

**Goals:** data source + perms + mount-point repoint with the scenario set preserved;
the patient-side rule client code retired.
**Non-Goals:** backend, visual redesign, new pages (existing-page edit — no prototype
per the house rule), any kernel/patient code.

## Decisions

- **D1 — API client**: a dedicated `src/api/number-rules.ts` typed against the system
  endpoints (the spec's Direct-service surface); the rule section inside
  `src/api/patient.ts` is deleted (no dead code); page imports repoint.
- **D2 — Page location**: the component moves to the 系统管理 page directory
  (route registered against the migrated seed's path — exact path read from the seed
  migration at apply time, the two changes coordinate via the registry); the old
  location's files are removed.
- **D3 — Perms**: one wholesale switch `patient:rule:*` → `system:number:*`
  (list/create/update/delete) in the page gate + toolbar + drawer buttons.
- **D4 — 规则类型**: a select over the platform families with Chinese labels
  (影像号/检查号/预约号); the drawer keeps every other field and validation as-is.
- **D5 — Tests**: the page tests repoint their mocked API module + perms strings; the
  multi-family select gains a scenario; the old-location tests are removed with the
  files.

## Risks / Trade-offs

- **Seed-order coupling**: the page's route depends on the sev change's menu seed
  landing first; both changes verify independently but the browser-level visual check
  waits for both (noted in the coordination registry's archive gate).
