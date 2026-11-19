# Batch 2 Review: rep-temp-scope-perms (tasks 2.1-2.3, commit 43ef5f6)

**Verdict: PASS** — 0 blocker / 0 important / 2 minor. Spec delta scenarios 1:1, D4 honored, no test weakening.

## Findings

**Minor 1 — RED evidence log no longer verifiable** (`/tmp/rep-temp-red.log`)
The file now contains Maven reactor output (lumen-system errors, mtime 10:30 — two minutes
after the commit; shared-/tmp collision with a parallel process), so the reported
"4 failed | 26 passed" cannot be re-read. The count is internally coherent (new tests +
fixture enrichment written against the OLD perm-gating anchor = 4 failures; the anchor
flip accompanied GREEN), and all 4 new tests structurally fail pre-change code (公共
option unconditional, default 公共, base-keyed actions), but keep future logs outside
shared /tmp. GREEN 30/30 + `tsc --noEmit` 0 re-run by reviewer: exit 0 both.

**Minor 2 — potential vacuous half-assertion in delta case 1** (`src/test/rep-temp-page.test.tsx:768-769`)
`pageCalls.every((call) => call[0].type !== 1)` runs right after `findByText('我的短语')`,
which resolves on the tree render; the `loadFiles` effect (page/index.tsx:350-355) fires
post-render, so `pageCalls` could theoretically still be empty → vacuous pass. Detection
power is preserved (a real default-公共 bug fails the tree anchor and the guarded
`treeCalls.length > 0` check first), and in the e2e case the write path guarantees a page
call before the `not.toContain(1)` assertion. Optional hardening: await a personal-table
row or `waitFor(() => expect(pageCalls.length).toBeGreaterThan(0))` before the every.

## Gates

1. **提交卫生 PASS** — commit diff = exactly 2 lumen-web files. The gate's "two staged renames" were already committed by the parallel session (388b3a1, 10:13, "页面 git mv 保历史") before this commit (10:28); reflog shows no reset/stash, index holds no foreign entries, dirty set (exam-item/exam-method/types.ts + lumen-sev) untouched.
2. **tempPerm 单点 PASS** — all 7 action sites via `tempPerm(op)` (index.tsx:628,631 table 编辑/删除; :670,:673,:676 tree 新建子目录/编辑目录/删除节点; :703 新建目录; :745 添加模板). Grep: only scope-independent literals left are the lazy init (:201) and Segmented filter (:730), both `public:list` per D4. Columns built inline (no memo) → no stale-perm risk; mutations carry `type: scope` (:403,:555) unchanged.
3. **Segmented 过滤 + 惰性默认 PASS** — options order 公共前个人 kept, 公共 gated on `hasPerm('setting:temp:public:list')`, 个人 unconditional (:726-731); lazy `useState(() => hasPerm(...) ? TYPE_PUBLIC : TYPE_PRIVATE)` (:200-202); case 1 asserts `repTempTree` never called with type=1 (guarded by `length > 0`) — see Minor 2 for the page half.
4. **存量 26 用例语义保全 PASS** — beforeEach perms enriched 4 → 8 strings (dual-family, commented; enhancement not weakening). Sole legacy edit = perm-gating anchor 呼吸系统→我的短语 (:447): its assertions (新建子目录/新建目录 null without base create) intact — anchor flip is the inevitable base-only-default-个人 consequence, not semantic swap.
5. **新 3 场景 1:1 PASS** — case 1 ↔ scenario 公共 option hidden (option existence + no type=1); case 2 ↔ 公共 scope consults public family (read fine, 新建目录/新建子目录/添加模板/编辑 hidden, reappear on 个人); case 3 ↔ 个人 scope base family (all five affordances + directory POST `type:2, flag:0`); case 4 = task 2.3 e2e (single option, 添加模板 POST `type:2, flag:1`, zero type=1 tree/page calls). Real assertions throughout; no constant-true checks.
6. **TDD 证据 PASS(带 Minor 1 保留)** — reviewer re-ran: vitest 30/30 exit 0, `tsc --noEmit` exit 0.

## Notes

- Mid-session perms refresh does not re-evaluate the lazy default (mount-once) — matches design D4 (unreachable via filtered options); implementer concern note is accurate.
- Full-file grep confirms no other test file renders this page; affected-suite scope is correct.
