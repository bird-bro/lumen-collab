# Batch 3 Review: 前端双形态模板表单 (tasks 3.1–3.6)

Change: rep-template-structured-content · Scope: 8e8b222..a45ebdc (4 files, all lumen-web)

## Verdict: **PASS** — all 6 delta scenarios implemented with dedicated cases; 0 Critical / 0 Important / 2 Minor. Commit clean; 26/26 + tsc 0 independently re-verified.

## Findings

### Minor
1. **measures nullability asymmetry** — `lumen-web/src/api/types.ts:890` types
   `RepTempSection.measures` non-null, but the Java contract (`RepTempSection.java` — nullable
   List, "round-trip keeps nulls, never []") can return `measures: null` for a bare section
   written via direct API (path design.md accepts for power users). Two read sites would throw:
   `lumen-web/src/pages/setting/rep-template/index.tsx:462` (backfill `s.measures.map`) and
   `:583` (summary `section.measures.length`). Not reachable through this UI alone. 建议:
   `(s.measures ?? [])` at both read sites, or mirror `| null` in the TS type. Not blocking.
2. **Section cards keyed by index while holding local collapse state** — `index.tsx:886` uses
   `key={si}` while TemplateSectionCard keeps per-card `collapsed` (`:86`); deleting a middle card
   shifts positions so a collapsed state can attach to the following section. Cosmetic,
   self-healing. 建议: acceptable; stable per-section id would fix it.

### No-action notes
- Empty-`sections` save reaches backend `@NotEmpty`, message surfaced verbatim (implementer's own
  concern): delta mandates no client pre-check; backend guard is the correct cheaper layer. Accept.
- `content?:` deviation from the brief's literal "required" is CORRECT — see gate 3.

## Gates (8/8 PASS)
1. **提交卫生 PASS** — a45ebdc = exactly the 4 lumen-web files; foreign staged RM rename back staged-intact;
   exam-method 6-file series + pom.xml + V38 untouched; committed types.ts has no remark lines (post-commit only).
2. **回归底线 PASS** — 15 originals counted in place (2 scope + 7 directory + 4 template + 1
   delete-msg + 1 perm); replaced case「添加模板 opens the template drawer」
   (rep-temp-page.test.tsx:329) keeps every original assertion (:336-340, :347) and ADDS
   radio-checked + 640-width (:344-346) — strengthened, semantics preserved.
3. **content?: PASS(实现更贴 spec)** — exam-setting delta spec.md:39-40: request bodies "SHALL
   carry the **optional** `content` object"; backend DTOs carry nullable `content` (no @NotNull);
   `content?: RepTempContent | null` on bodies + required-nullable on the RepTemp row match both;
   the brief's "required" was the outlier. Keep the implementation.
4. **D6 PASS** — Radio `index.tsx:846-856`; width 960/640 `:807`; 节卡片 `:85-183` (节名/测量项行
   code/label/unit/normal+行删+添加测量项/节所见/折叠/删除节) + 添加节 `:893`; 底部全局意见 `:898`;
   简单→分节 表现→首节节所见 `:434-441`; 分节→简单拒绝 + controlled radio 保持分节 `:443`.
5. **D7 PASS** — 「N 节 · M 项」 token.colorTextTertiary `index.tsx:581-590`; 意见 cell
   content.impression ?? '—' `:600`; prose rows unchanged; test :681-693.
6. **D8 PASS** — directory saves carry no content key (`index.tsx:374-392`); pin test asserts
   `'content' in arg === false` (test :707-713).
7. **TDD 证据 PASS** — /tmp logs match the narrative (red1: 19 tests, 5 failed | 14 passed;
   postcommit 26/26); independent re-run: vitest 26/26 exit 0, tsc exit 0. GREEN-on-arrival
   T11/T12 are legitimate invariant pins of pre-existing behavior, honestly disclosed; cycle-1
   serialization RED is real (helper-bug-masked first RED not re-asserted — disclosed, acceptable).
8. **类型契约 PASS** — RepTempContent/RepTempMeasure 逐字段一致(含 unit/normal/finding null 性);唯一偏差 measures TS 非空 vs Java 可空 → Minor 1。

## Spec compliance & Quality & Tests
Dedicated green cases per scenario: section edits → content round-trip (:507/:544/:626 verbatim
PUT), 简单→分节 mapping (:587), 分节→简单 refused (:606), summary cells (:681); baseline hunks
(Segmented :700-709, 上级+防环 :234-252, rows 10/8, tool-row reorg) verified intact. House idioms
hold: theme tokens, ApiError.msg + errorFields guard (:398/:548), console-shell set, drawer
`closable={{placement:'end'}}` + `maskClosable={false}` (:769/:810), `@/api` seam mock only,
deep-copied backfill prevents row aliasing (:462). Assertions are behavior-real (exact content
shape, key-absence); scoping via `节卡片 N` group avoids the 名称 aria-label collision; no weakened checks.
