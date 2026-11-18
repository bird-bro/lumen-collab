---
verify:
  change: menu-root-parent-option
  date: 2026-11-18
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "6/6"
      L3: PASS
    backend:
      L1: SKIP
      L2: "-"
      L3: SKIP
---

# Verification — menu-root-parent-option

Single-stack change (lumen-web only; tasks §1 states "no backend change") — backend
records SKIP. All evidence from `lumen-web/src/pages/system/menus/index.tsx` +
`lumen-web/src/api/menus.ts`.

## Stacks

| Stack | Layer | Status | Detail |
|:---|:---|:---|:---|
| lumen-web | L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| lumen-web | L2 spec | 6/6 | all IMPLEMENTED (below) |
| lumen-web | L3 tests | PASS | `pnpm test`; exit=0; logs/test-lumen-web-rerun.log — 33 files / 392 tests green |
| backend | L1 / L3 | SKIP | no backend surface in this change |

## Scenario alignment (delta specs/web-system-console)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Tree renders with types and perms | IMPLEMENTED | index.tsx:289-310 (类型 Tag/TYPE_LABELS), index.tsx:323-331 (权限标识 column), index.tsx:487-496 (Table renders `menuTree()` data) |
| Button node CRUD round-trip | IMPLEMENTED | api/menus.ts:14-31 (GET tree / POST / PUT {id} / DELETE {id} — the documented endpoints), index.tsx:250-257 (create/update then `reloadTree()`), index.tsx:267-277 (delete then `reloadTree()`) |
| Delete rejection surfaces | IMPLEMENTED | index.tsx:419-439 (Popconfirm 删除菜单), index.tsx:272-274 (ApiError `msg` surfaced via messageApi.error; tree unchanged on rejection) |
| Dialog guards the parent choice | IMPLEMENTED | index.tsx:104-117 `collectParentOptions` — skips `type === 'F'` (:107); skips the edited node (`node.mid === excludeId`, :108) so its whole subtree drops out with it |
| Toolbar create defaults to root | IMPLEMENTED | index.tsx:476 (toolbar 添加 → `openCreate(null)`), index.tsx:198-209 (`upId: parent ? parent.mid : 0`), index.tsx:519-529 (上级菜单 Form.Item renders unconditionally), index.tsx:236-239 (submit reads `upId` from the form field — 0 = 根目录) |
| Root stays selectable in edit | IMPLEMENTED | index.tsx:99-101 (`buildParentOptions` always prepends the single 根目录 option, value 0), index.tsx:211-228 (`openEdit` seeds `upId: node.upId` — a top-level node displays 根目录 and it stays selectable) |

## Issues

- L3 first run (logs/test-lumen-web.log): 2 flaky failures in **unrelated** files —
  `device-page.test.tsx` (pointer-events during interaction) and `exam-body-page.test.tsx`
  (duplicate tooltip role). Both pass in isolation (26/26); full-suite rerun exit 0
  (392/392). No menu-page test was affected; no fix required for this change.

Overall: **PASS** — 6/6 scenarios implemented, L1 + L3 green. Next: `/opsx:archive menu-root-parent-option`.
