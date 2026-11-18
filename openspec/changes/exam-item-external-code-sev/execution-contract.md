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

- **Change name**: exam-item-external-code-sev
- **Problem to solve**:
检查项目缺少与外部系统对照的编码字段：`exam_item.code` 经 exam-item-polish 协调文档
D4（2026-11-16）确认仅为院内自有业务编码、无第三方消费方。v1 集成路线
（`lumen-integration`，HL7v2 对 HIS）落地前需要目录级对照锚点——用户裁决
2026-11-17：增加**通用对照编码 `external_code`**（HIS 是第一个消费方），可空、
不强制唯一。

<!-- AI: summarize the above into 1-2 sentences -->
- **Intent summary**: `exam_item` 增加通用对照编码列 `external_code`（可空、不唯一）
  并贯通实体/DTO/keyword 检索——外部系统（HIS 首个消费方）对照的目录级锚点。
- **In scope**: V37 additive 迁移（单条 ALTER）；`ExamItem` 实体 +
  `ItemCreateRequest`/`ItemUpdateRequest`/`ItemVO` 的 `externalCode`；
  `ExamItemServiceImpl` keyword LIKE 分支；schema seed + API 测试；exam-setting
  spec delta。
- **Out of scope**: 抽屉/表格 UI（配套 `exam-item-external-code-web`）；独立分页
  过滤参数；唯一索引；映射表；HIS 同步逻辑本身（lumen-integration 未来提案）；
  院内 `code` 的任何行为变化。

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
12:### Requirement: Exam Item Columns
26:#### Scenario: Item stores the catalog facts and the score weights
32:#### Scenario: Item code uniqueness
38:#### Scenario: Item stores the external mapping code
45:#### Scenario: Duplicate external mapping codes are accepted
50:### Requirement: Exam Item Endpoints
58:#### Scenario: Page filter by group
64:#### Scenario: Keyword matches the external mapping code
69:#### Scenario: Perm-gated write


## Design Constraints

- **Architecture/Decisions**:
- **D1 — 通用 `external_code`，非 HIS 专名**（用户裁决 2026-11-17）: HIS 是第一个
  消费方但不是唯一可预期的对照系统；专名列在第二系统出现时要迁移改名，通用列免迁移。
  否决项：`his_code` 专名（第二系统加列/改名成本）；映射表
  `(item_id, system, code)`（v1 只有一个消费方，建表+维护面过重 — HERO）。
  EIS 参照系对照锚点在订单级（`clinicItemId`，字典同步模式），目录级无先例可抄，
  本列是 lumen 的目录级最小落法。
- **D2 — 不强制唯一**（用户裁决 2026-11-17）: 打包计费（一 HIS 收费项 ↔ 多院内
  检查项）是合法映射，硬唯一堵死；集成未落地前重复可经 keyword 检索人工发现。
  与 `exam_item_live_code_uidx`（院内 code 的 partial unique index）形成有意对比：
  那是自有编码空间，本列是外部系统空间。
- **D3 — additive V37**: V35（`exam_body_group_remark`）之后顺延；V36（workstation_department_icons，eis-workstation-icons change）已占用；
  `ALTER TABLE exam_item ADD COLUMN external_code varchar(32);` 一条语句，不触碰
- **Technical constraints**: additive Flyway only（不动已应用迁移；V35 之后顺延 V37（V36 已被 icons change 占用））；
  JDK 11 + aggregator reactor 构建（`./mvnw -pl lumen-system -am test`）；
  共享测试库夹具走基类前缀家族 + hard-delete 清扫（2026-11-07 裁决）；
  `BeanUtil.copyProperties(request, entity, true)` 1:1 拷贝（字段同名自动携带）。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 `AuthExamItemApiTests`: create with `externalCode` echoes in detail;
7:- [ ] 1.2 `AuthExamItemApiTests`: two live items in the same modality with the
10:- [ ] 1.3 `SettingSchemaSeedTests`: V37 applies — `exam_item.external_code`
15:- [ ] 2.1 `V37__exam_item_external_code.sql`: single `ALTER TABLE exam_item ADD
17:- [ ] 2.2 `ExamItem` entity + `ItemCreateRequest` / `ItemUpdateRequest` / `ItemVO`:
19:- [ ] 2.3 `ExamItemServiceImpl`: keyword LIKE branch adds
24:- [ ] 3.1 `./mvnw -pl lumen-system -am test`（aggregator 根，JDK 11）green —
26:- [ ] 3.2 `/opsx:verify` PASS — verify.md written

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: create 携带 `externalCode` 回显 / 不带
  则 NULL；update 替换 mapping code；同模态重复 `externalCode` 放行（打包计费）；
  keyword 命中对照编码；V37 列型 varchar(32) nullable 且无唯一索引。
- **Required edge cases**: 空串/超 32 字符 mapping code 入参（DTO 层校验对齐现有
  code 处理）；keyword 与 名称/code/externalCode 三分支任一命中。
- **Regression-sensitive areas**: 院内 `code` 的模态内唯一性校验不得变化；
  `exam_item_method` 绑定事务重写不得变化；既有 keyword 行为（名称/code）不变。

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 6 实施任务同模块（lumen-system）、无跨模块依赖、单列变更
  无风险指标；TDD 红→绿成对推进。

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 用户审阅 proposal/spec delta（DP-3）；verify L2 对
  WHEN/THEN
- **Blocking categories**: 院内 code 唯一性回归；迁移触碰已应用文件（禁止）

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
