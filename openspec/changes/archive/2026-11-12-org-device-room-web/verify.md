<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: org-device-room-web
  date: 2026-11-12
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# Verification: org-device-room-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build`, exit 0) | 10/10 | PASS (`pnpm test` — 317/317 across 26 files, exit 0) | logs/build-lumen-web.log, logs/test-lumen-web.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Org selection filters the table | IMPLEMENTED | device/index.tsx (tree onSelect → campus orgId / root omits orgId); room/index.tsx:397-406; tests assert both semantics |
| Type and status filters | IMPLEMENTED | device/index.tsx:461 (type select binds the dict VALUE token), :455-458 (page-1 reset); device-page.test.tsx asserts `type: 'CT'` wire shape |
| Create device through the drawer | IMPLEMENTED | device/index.tsx:258 (handleSubmit → createDevice + refresh) |
| Edit echoes and full-updates | IMPLEMENTED | device/index.tsx:222 (fresh getDevice echo, Dayjs conversion); D9 full-replace submit |
| Delete guard surfaces the room count | IMPLEMENTED | device/index.tsx:316 (backend A0400 msg → error notification, row stays) |
| Room list shows the bound device by reference | IMPLEMENTED | room/index.tsx columns (devName/devAe from the server-resolved summary; 未绑定 empty state) |
| Department filter narrows the list | IMPLEMENTED | room/index.tsx dep/name filters with page-1 reset |
| Status toggle row action | IMPLEMENTED | room/index.tsx:297-308 (endpoint call, revert-on-failure re-render); perm-gated :96 |
| Create room with device binding | IMPLEMENTED | room/index.tsx:226-257 (org/dep-scoped picker, read-only AE/IP/Port echo) + :259 (binding.devId into body) |
| Rebind or clear the device | IMPLEMENTED | room/index.tsx 清除绑定 → NO_BINDING; update body carries devId/null |

## Test Evidence (2026-11-12)

- Full Vitest suite 317/317 (26 files), exit 0 — includes device-page.test.tsx,
  room-page.test.tsx, users-page.test.tsx (modalitys CSV re-keyed to the unified
  设备类型 dict), dicts-page.test.tsx.
- `pnpm exec tsc --noEmit` clean.

## Issues

- None attributable to this change. (Backend-side notes live in org-device-room-api/verify.md.)

## Next

- PASS -> archive org-device-room-web (batch with org-device-room-api per the coordination gate).
