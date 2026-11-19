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

- **Change name**: rep-temp-normal-range-web
- **Problem to solve**:
后端 `rep-temp-normal-range-sev` 已将 `content` 升 v2（`measures[].normal`
tagged union），web 编辑器仍是自由文本 Input，无法产出/展示 v2 区间形态。
本 change 让编辑器与真后端契约对齐（无 mock，2026-11-05 裁决）。

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: `RepTempMeasureNormal` union 类型；测量项行「正常范围」Segmented(区间/文本) 行内编辑器（InputNumber×2 / text Input ≤64）；序列化恒写 v2（双侧空→null、单边显式 null 端、文本 trim）；min>max 客户端拦截（无请求）；按存储 kind 回显；夹具与测试升 v2。
- **Out of scope**: v1 兼容分支（迁移后无 v1 行）；填写侧异常提示 UI；模板形态/抽屉宽度等既有裁决改动；后端代码（sev change 负责契约与迁移）。

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
3:### Requirement: Report Template Page
44:#### Scenario: Tab toggle switches scope without cross-leak
50:#### Scenario: Directory node creates a child directory
56:#### Scenario: 公共 option hidden without the public list perm
63:#### Scenario: 公共 scope actions consult the public family
72:#### Scenario: 个人 scope actions keep the base family
79:#### Scenario: 区间 normal serializes the v2 range variant
86:#### Scenario: 文本 normal serializes the v2 text variant
91:#### Scenario: Empty normal serializes null
96:#### Scenario: Inverted range is blocked client-side
102:#### Scenario: Backfill reads the stored kind


## Design Constraints

- **Architecture/Decisions**:
- **D1 类型层**：`types.ts` 新增
  `type RepTempMeasureNormal = { kind: 'range'; min: number|null; max: number|null } | { kind: 'text'; text: string }`，
  `RepTempMeasure.normal: RepTempMeasureNormal | null`；version 注释改 pin 2。
- **D2 行内布局（不换行）**：正常范围列变为紧凑组合——
  `Segmented(区间/文本)`（size small）+ 区间两枚 `InputNumber`（min~max，
  各 ~90px，`step` 默认小数可用，placeholder 最小/最大）或文本一枚 Input
  （≤64）。行仍是单排 flex；960 定宽抽屉下容纳。行级 kind 存进
  `RepTempSection` 编辑状态的 measure 行内（`normal` 为 null 时 Segmented
  默认落 区间、两输入空）。
- **D3 序列化规则（saveTemplate）**：区间两侧全空 → `normal: null`；区间
  至少一端 → `{kind:'range', min: number|null, max: number|null}`（空端
  显式 null，不省键）；文本非空 → `{kind:'text', text}`（trim）；文本空白 →
- **Technical constraints**: React 18 + TS strict + AntD 5（无 mock 层，`VITE_API_ENTRY_URL` 真后端）；固定 960 抽屉 + 行内不换行纪律（压缩单位列宽优先于换行）；测试 vitest（seam mock 仅测试）+ `tsc --noEmit` 干净；kebab-case 文件名；`types.ts` 类型不动服务端契约之外的字段。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
8:- [ ] 1.1 红：types 测试/编译断言——`RepTempMeasureNormal` union 可用，`RepTempMeasure.normal` 收窄为 union|null
9:- [ ] 1.2 绿：`types.ts` 新增 union 类型 + 注释 version pin 2
13:- [ ] 2.1 红：五场景用例——区间双边/单边序列化、文本序列化、双侧空→null、min>max 拦截（无请求）、按 kind 回显 Segmented 与输入
14:- [ ] 2.2 绿：测量项行正常范围列改 Segmented(区间/文本) + InputNumber×2 / text Input（design D2 行内布局）；`saveTemplate` 序列化按 design D3（恒写 version 2）；min>max 预校验按 D4；`openTemplateEdit` 回显按 D5
15:- [ ] 2.3 删除 v1 backfill 残留分支（字符串 normal 判断）——迁移后无 v1 行
19:- [ ] 3.1 `rep-temp-page.test.tsx` 结构化夹具 normal 升 union 形态；受影响既有用例断言更新
20:- [ ] 3.2 全量回归：`npx vitest run`（全套件）+ `npx tsc --noEmit` 干净

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: spec delta 五新场景——区间双边/单边序列化（单边空端显式 null 键）、文本序列化、双侧空→`normal: null`、min>max 拦截且断言零请求、按存储 kind 回显 Segmented 与输入值。
- **Required edge cases**: text 空白→null（不产空 text 变体）；normal===null 回显落区间+空（不造第三态）；`STRUCTURED_TEMP` 夹具多 section 多 measure union 升级；InputNumber 以 aria-label（最小/最大）锚定交互。
- **Regression-sensitive areas**: 既有 30 用例（含 D5/D7 汇总、D6 转换映射、scope 权限族）回归不动；序列化 content 整形与 D1 互斥（structured 写 null prose 列）不变。

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 7 个任务项单模块（lumen-web rep-template 页 + types）、无跨模块依赖；类型→编辑器→夹具回归三批顺次，批内 TDD。

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
