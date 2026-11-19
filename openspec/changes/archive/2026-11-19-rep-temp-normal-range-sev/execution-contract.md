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

- **Change name**: rep-temp-normal-range-sev
- **Problem to solve**:
测量项「正常范围」是自由文本（archived `rep-template-structured-content` D2），
报告填写侧无法用测值比对自动提示指标异常。用户裁决 2026-11-19：现在就结构化，
且存量 v1 行一次性迁移到位，不做双版本读兼容。

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: content v2 union DTO（`RepTempMeasureNormal`：range min/max 独立可空、text ≤64）+ 服务端校验（min≤max、两端全空拒、kind 合法、version 钉 2）+ V47 一次性幂等迁移（严格模式集转 range、不可解析降 text 原文）+ 既有 content 夹具升 v2。
- **Out of scope**: 报告填写侧异常提示消费（未来提案）；双版本读兼容（迁移后无 v1 行）；`code/label/unit` 语义变化；任何新端点。

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
3:### Requirement: Structured Template Content
24:#### Scenario: Structured template stores the section model
32:#### Scenario: Invalid content rejected
41:#### Scenario: Full-replace null content converts structured to prose
46:#### Scenario: Single-sided range normal is accepted
54:### Requirement: Normal-Range v2 One-Shot Migration
67:#### Scenario: Parseable v1 normals convert to ranges
74:#### Scenario: Unparseable v1 normals degrade to text verbatim
80:#### Scenario: Migration replay is idempotent
85:#### Scenario: v1-shaped writes are rejected after migration


## Design Constraints

- **Architecture/Decisions**:
- **D1 union 形态与反序列化守护**：`normal` 类型从 `String` 改为
  `RepTempMeasureNormal`（新增嵌套 DTO，`kind: String` + range 字段
  `min/max BigDecimal` + text 字段；`@Data @Builder @AllArgsConstructor
  @NoArgsConstructor` 组合沿 entity 惯例）。Jackson 把 v1 的字符串 normal
  绑定为 union 对象失败 → 请求体绑定错误（400 族，报文含字段路径），这层
  结构守护免费获得；`kind` 取值与分组校验由服务层断言（沿 D3 校验位置先例：
  Bean Validation 覆盖 `code/label`，条件互斥逻辑服务层断言，A0400 命名字段）。
- **D2 服务端校验规则（A0400，报文命名字段）**：`version` 必须为 2（服务端
  写路径钉 2，沿 pin-1 旧纪律——客户端传值被覆写）；range：min/max 均可空
  小数（`BigDecimal`，jsonb 数字），双填则 `min <= max`，两端全空拒；text：
  非空 ≤64；`kind` 非 `range`/`text` 拒；null normal 合法（无范围）。
- **D3 V47 迁移（纯 SQL，幂等）**：单条 `UPDATE system.rep_temp ... FROM
- **Technical constraints**: JDK 11 / Spring Boot 2.5.15 训练（版本 1:1 OPS，不升级）；jsonb 经 JacksonTypeHandler 整体序列化、V39 GIN 不动；A0400 信封命名字段（不新增错误码族）；reactor 构建从 lumen-sev/ 聚合；共享测试库 hazards（独占窗口 + rerunFailingTestsCount）；Lombok 四件套组合沿 entity 惯例。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
8:- [ ] 1.1 红：迁移测试——fixture SQL 造 v1 行（normal 覆盖 `19-40`/`<5`/`≤5`/`>3`/`阴性`/null/带空格变体），断言迁移后 v2 形态逐例正确（含「阴性」text 原文逐字节保留）；断言重放幂等（二次执行 0 行变化、无错）
9:- [ ] 1.2 绿：`V47__rep_temp_normal_range_v2.sql`——单 UPDATE + LATERAL jsonb 重建（WITH ORDINALITY 保序），CASE+正则按 design D3 规则集，守卫 `content->>'version' = '1'`
10:- [ ] 1.3 新装机 no-op 断言（V39 空列表 → 迁移 0 行成功）；重跑 `./mvnw -pl lumen-system -am test -q` 种子/迁移组绿
14:- [ ] 2.1 红：DTO/服务层测试——合法 range（双边/单边/小数）与 text（≤64）存储 verbatim；min>max 拒 A0400 命名字段；range 两端全空拒；text 空白/超 64 拒；kind 非法值拒；version=1 或字符串 normal（v1 形态）拒；version 客户端传 3 被钉成 2 落库
15:- [ ] 2.2 绿：新增 `RepTempMeasureNormal` 嵌套 DTO（kind + min/max BigDecimal + text，Lombok 四件套）；`RepTempMeasure.normal` 类型改 union；服务层校验按 design D2（A0400 命名字段）；写路径钉 version 2
16:- [ ] 2.3 目录拒 content、sections 非空等既有断言回归不动
20:- [ ] 3.1 `RepTempContentApiTests` 等既有 content 夹具 normal 升 union 形态；web 类型注释对齐（version pin 2 由 web change 落）
21:- [ ] 3.2 lumen-system 全量 `./mvnw -pl lumen-system -am test -q` 绿（共享测试库：独占窗口 + rerunFailingTestsCount 容错惯例）

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 迁移转换表（`19-40`/`<5`/`≤5`/`>3`/`阴性`/null/带空格 → 对应 v2 形态）+ 重放幂等 + 新装机 no-op；DTO 合法 range（双边/单边/小数）与 text verbatim 存储；min>max、两端全空、text 空白/超 64、kind 非法、v1 形态（version=1 或字符串 normal）全拒且命名字段；version 传 3 被钉 2 落库。
- **Required edge cases**: 全角 `≤≥` 字符模式；数字两侧空格 trim；「阴性」text 原文逐字节保留；fixture 覆盖多 section 多 measure 保序（WITH ORDINALITY）。
- **Regression-sensitive areas**: `RepTempContentApiTests` 夹具升 v2；目录拒 content / sections 非空 / null content 转 prose 既有断言不动。

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 8 个任务项单模块（lumen-system setting 域）、无跨模块依赖；迁移 + DTO 两批有先后（V47 先行，DTO 依赖 v2 形态定案），批内 TDD 红绿推进。

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
