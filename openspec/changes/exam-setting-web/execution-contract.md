# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: exam-setting-web
- **Problem to solve**:
The backend change `exam-setting-api` delivers the 检查设置 service
(`/api/v1/setting/**`); the console needs pages to maintain the exam catalog and the
report-template knowledge base. RCP's `/setting/exam/*` + template pages prove the
interaction shapes; lumen re-hosts them under the existing console patterns with the
re-modeled domain (no org filter, group management in-page, one template page with
公/私 tabs). Per the frontend-backend-first ruling, this change starts only after the
backend API is user-verified.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: four console pages under the seeded 检查设置 group (检查项目 / 检查
  部位 with in-page group management / 检查方法 / 报告模板 with 公共-私有 tabs), typed
  API modules under `src/api/setting/`, component registry entries, prototype-first
  visual confirmation, real-backend wiring, `setting:*` perm gating.
- **Out of scope**: mock data layer, new UI primitives outside the console kit,
  项目对照 page, print-template page, workstation-side item pickers, backend code
  (cross-domain ban), menu seed data (backend change owns it).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
5:### Requirement: Exam Item Page
16:#### Scenario: Group selection cascades the body options
22:#### Scenario: Create posts to the real backend
28:### Requirement: Exam Body Page with In-Page Group Management
37:#### Scenario: Left column manages groups without leaving the page
42:#### Scenario: Blocked group delete shows the reason
47:### Requirement: Exam Method Page
54:#### Scenario: Method CRUD round-trips the real API
60:### Requirement: Report Template Page
70:#### Scenario: Tab toggle switches scope without cross-leak
76:#### Scenario: Directory node creates a child template


## Design Constraints

- **Architecture/Decisions**:
### D1: Page skeletons reuse the console list+drawer pattern
Each page = filter bar + table + Drawer form per the existing console pages
(`system/org/**`, `system/logs`): AntD 5 table with fixed 操作 column, Drawer forms
(540-class width), confirm dialogs for deletes, message feedback on success. No new
layout primitives.
### D2: 检查部位 page hosts group management in-page
Left column (检查类型 select above it) lists the modality's groups with inline
新建/编辑/删除; the right table lists bodies filtered by the selected group. One menu
item instead of two — the coordination doc's menu ruling. A refused group delete
surfaces the backend's blocking message verbatim (backend names the reference).
### D3: Item drawer cascades 分组 → 部位, methods multi-select
Drawer holds 部位分组 select (feeds from `exam-body-groups/list`), 检查部位 tree
- **Technical constraints**: React 18 + TS + Vite + AntD 5 (pnpm, single SPA); build
  against the REAL backend via `pnpm dev` proxy (`VITE_API_ENTRY_URL`) — no mock layer
  (2026-11-05 ruling); antd icons hard dependency intact (antd-icons ruling);
  frontend-design skill for the prototype; cross-domain ban — this change touches only
  `lumen-web/`.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
11:- [ ] 1.1 API modules `src/api/setting/`: `exam-body-group.ts`, `exam-body.ts`,
15:- [ ] 1.2 Component registry entries for the four seeded paths
22:- [ ] 2.1 Prototype the four pages' layouts (filter bar + table + Drawer; 检查部位
28:- [ ] 3.1 Wire `setting/exam-method/index`: dict 1021 检查类型 filter, 名称/编码
35:- [ ] 4.1 Wire left group column (list/新建/编辑/删除 on
43:- [ ] 5.1 Wire filters (检查类型/部位分组/常用项目/关键词) + `GET /page` table with
52:- [ ] 6.1 Wire 公共/私有 tabs (type 1/2 threaded into every call), left modality
60:- [ ] 7.1 Perm gating: hide/disable affordances on missing
64:- [ ] 7.2 Run the configured suite: `pnpm build` exit 0 + the page-level checks green

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: group→body cascade clearing the stale
  body value; tab toggle reloading both panes with the right scope; blocked group
  delete surfacing the backend message verbatim; perm-gated affordances absent for a
  stripped role.
- **Required edge cases**: unknown-modality rejection surfaced from the backend;
  server-resolved body/method names render (no client-side join); empty-tree and
  empty-page states.
- **Regression-sensitive areas**: existing console pages untouched (menu-driven
  routing additions only); `pnpm build` exit 0 after each page wiring.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 9 tasks, one stack, sequential page wiring with a shared
  prototype gate — same profile as org-device-room-web (Batch Inline, 11 tasks).

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
