# Batch 2 Report: 前端 Segmented 显隐 + 动作键控 (tasks 2.1–2.3)

Change: rep-temp-scope-perms · Stack: lumen-web · Commit: `43ef5f6`

## Implemented

- **2.1** `src/pages/setting/rep-template/index.tsx:207-210` — `tempPerm(op)` single-point
  helper (`useCallback`, `[scope]`; 公共 → `setting:temp:public:*`, 个人 → base), exact
  brief shape. Segmented 公共 option filtered on `hasPerm('setting:temp:public:list')`
  (index.tsx:729-733, order 公共前个人 kept, 个人 unconditional). Default scope lazy
  (index.tsx:199-202): `useState(() => hasPerm('setting:temp:public:list') ? TYPE_PUBLIC : TYPE_PRIVATE)`.
- **2.2** All seven action sites re-keyed: table 编辑/删除 (index.tsx:617,620), tree
  新建子目录/编辑目录/删除节点 (index.tsx:659-665), left 新建目录 (index.tsx:692), right
  添加模板 (index.tsx:729 area). No scattered perm literals left (grep: only lazy-init,
  helper, Segmented filter — the three scope-independent public:list checks per D4).
- **2.3** E2E test: base-only user → single-option Segmented, full personal write path
  (添加模板 POSTs type=2), `repTempTree`/`pageRepTemps` never called with type=1.
- Section editor (dual-form D6) logic untouched; jsdoc records the 2026-11-19 ruling.

## TDD Evidence

- RED: `pnpm vitest run src/test/rep-temp-page.test.tsx` → exit 1, **4 failed | 26 passed**
  (new cases fail against pre-change page: 公共 option always shown, default 公共, base-keyed
  actions). Log: /tmp/rep-temp-red.log
- GREEN: same command → exit 0, **30 passed (30)**. Log: /tmp/rep-temp-green.log
- `pnpm exec tsc --noEmit` → exit 0.

## Files Changed

- `lumen-web/src/pages/setting/rep-template/index.tsx` (helper + filter + lazy init + 7 re-keys + jsdoc)
- `lumen-web/src/test/rep-temp-page.test.tsx` (fixtures +4 public strings; new describe
  `scope-keyed perms` with 4 cases: delta scenarios 1/2/3 1:1 + task 2.3 e2e)

## Regression Strategy (per brief)

- beforeEach fixtures enriched with the 4 public variants → legacy 26 run as dual-family
  users, behavior byte-identical (default still 公共, all actions visible).
- New cases use trimmed sets via `setSession` overrides.

## Self-Review Findings

1. **One legacy case needed a 1-line anchor change** (`perm gating` test): it sets
   base-only perms, so under the lazy default it now lands on 个人 — tree anchor
   呼吸系统 → 我的短语 (test file, perm-gating describe). Its assertion (新建 hidden without
   base create) is unchanged and still passes. Inevitable: that case's subject (base-only
   user) is exactly what the default-flip changes.
2. Delta 3 scenarios covered 1:1; the 6 original scenarios' tests untouched and green.
3. No type=1 request asserted two ways (`every(call[1] !== 1)` + `not.toContain(1)`).
4. No new components/types; parallel session's modified files (exam-item/exam-method/
   types.ts) untouched — commit is pathspec-limited to the two files.

## Concerns

- None blocking. Note: scope default evaluates once at mount (D4); a mid-session perms
  refresh does not re-evaluate it — matches design (switching to an unavailable value is
  unreachable via options) and existing usePerm gating patterns.
