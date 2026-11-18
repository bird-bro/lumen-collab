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

- **Change name**: exam-item-external-code-web
- **Problem to solve**:
后端配套 change `exam-item-external-code-sev` 给 `exam_item` 增加了通用对照编码
`external_code`（外部系统对照锚点，HIS 首个消费方，可空、不唯一）。检查项目页需要
能查看和维护它——否则对照维护无法进行（编码成对录入、成对核对）。

<!-- AI: summarize the above into 1-2 sentences -->
- **Intent summary**: 检查项目页接入后端新增的 `externalCode`——抽屉 对照编码 字段
  （项目编码 之后）+ 表格列 + 保存载荷与编辑回显，api seam 页测先行。
- **In scope**: `ExamItem`/`CreateExamItemBody` 类型 + `externalCode`；抽屉字段；
  表格列（— for null）；create/update 载荷；页测 + api 测；web-system-console
  spec delta。
- **Out of scope**: 筛选栏改动 / 独立过滤参数；api client 新端点；抽屉既有布局
  （polish 已定稿的选项行与 4-up 数字带不动）；后端任何文件。

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
12:### Requirement: Exam Item Page
29:#### Scenario: Group selection cascades the body options
35:#### Scenario: Create posts to the real backend
41:#### Scenario: Filter bar omits the 常用项目 toggle
48:#### Scenario: Table renders the remark column
54:#### Scenario: Drawer tail lays out the option line and banded numbers
63:#### Scenario: External mapping code round-trips


## Design Constraints

- **Architecture/Decisions**:
- **D1 — 字段落位**: 抽屉里 对照编码 紧跟 项目编码 —— 两个编码是同一实体的两个
  编码空间（院内自有 / 外部对照），成对相邻便于录入与核对；表格列同样紧跟
  项目编码。
- **D2 — 渲染样式与 项目编码 一致**: 列空值渲染 —；对照编码不参与 ellipsis 特判
  （短编码串，项目编码 同款处理）；不做着色/弱化差异，保持列语义对称。
- **D3 — 保存载荷**: create/update 载荷都带 `externalCode: values.externalCode ??
  null`（对齐现有 `code ?? null` 写法）；编辑回显走 `getExamItem` detail 的
  `externalCode`。
- **D4 — 不动筛选栏**: 不为对照编码加独立过滤参数（后端 D6 同款裁决）；keyword
  后端已覆盖对照编码检索，前端关键词占位文案不必变（名称/编码 已含语义）。
- **D5 — 类型与 api client**: `ExamItem` + `CreateExamItemBody` 各加一行
  `externalCode: string | null`；`UpdateExamItemBody` 是 alias 自动继承；
- **Technical constraints**: React 18 + TS strict + AntD 5；无 mock 层（api seam
  测试 stub 仅限测试）；design tokens 而非散落 hex；依赖 sev change 先落库
  （后端先行惯例）。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 `exam-item-page.test.tsx`: drawer exposes labeled 对照编码 field right
8:- [ ] 1.2 `exam-item-page.test.tsx`: table renders 对照编码 column (— for null)
10:- [ ] 1.3 `setting-api.test.ts`: `CreateExamItemBody`/`ExamItem` types carry
15:- [ ] 2.1 `api/types.ts`: `ExamItem.externalCode` + `CreateExamItemBody.externalCode`
16:- [ ] 2.2 Page: drawer 对照编码 field (Input, maxLength 32, after 项目编码);
22:- [ ] 3.1 `pnpm test` green (full suite) — ledger in the apply log
23:- [ ] 3.2 Visual check against the REAL backend (`pnpm dev`, local lumen-system
25:- [ ] 3.3 `/opsx:verify` PASS — verify.md written

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 抽屉 对照编码 labeled 字段落位与次序
  （项目编码 之后）；create/update 载荷携带 `externalCode`；编辑回显从 detail 填充；
  表格 对照编码 列（— for null）紧跟 项目编码。
- **Required edge cases**: null mapping code 的表格渲染；载荷空值 `?? null`。
- **Regression-sensitive areas**: 既有抽屉字段/次序不变（polish 定稿的选项行、
  4-up 数字带）；既有列序与 备注 列渲染不变。

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 5 实施任务同模块（lumen-web）、无跨模块依赖、既有页面
  叠加小字段；TDD 红→绿成对推进。

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 用户审阅 proposal/spec delta（DP-3）；真后端视觉
  确认（UI gate，任务 3.2）；verify L2 对 WHEN/THEN
- **Blocking categories**: 真后端不可达（禁止 mock 数据展示）；既有布局回归

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
