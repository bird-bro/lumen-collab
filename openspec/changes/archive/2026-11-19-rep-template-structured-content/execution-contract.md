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

- **Change name**: rep-template-structured-content
- **Problem to solve**:
报告模板目前只有 `finding`/`impression` 两个纯文字列——超声(心脏几十项测量值)、内镜(分部位所见)这类结构化报告内容无处安放,医生只能把测量值写成伪表格文字。EIS 参照系确认测量/脏器分区是净新增(旧版不存在),无先例可抄;架构规则已定调「report templates, structured findings → PostgreSQL jsonb + GIN」。富文本(HTML)已评估并否决:HTML 表格无法变成报告编辑器的可填写输入表格,服务端无法做正常范围校验,未来还要迁移。本裁决(用户拍板路线 B,2026-11-18):模板内容**通用分节模型**——不是彩超专属,放射(CT 分区测量)、内镜(分部位所见)、超声(单节/多节)共用同一模型。

<!-- AI: summarize the above into 1-2 sentences -->
- **Summary**: rep_temp 新增通用分节内容模型 `content jsonb`(version + sections[name/finding/measures[code,label,unit,normal]] + impression),与 prose 两列二选一(content 完全接管);后端迁移+校验+契约,前端双形态编辑器(简单 640 / 分节 960 抽屉)——跨科室通用,非彩超专属。
- **In scope**:
  - 后端:V39 迁移(加列+GIN)、RepTemp 实体 jsonb handler、嵌套 DTO 校验、服务层互斥与断言、契约对象/请求体扩展、service+api 测试
  - 前端:模式 Radio、960 分节抽屉(节卡片)、简单↔分节转换映射与拒绝、右侧表格摘要渲染、类型契约
- **Out of scope**:
  - 报告实例侧(测量值填写/正常范围校验/分节纸面渲染/打印)——lumen-core 报告域后续提案
  - 测量项目/器官·部位词表绑定(v1 自由输入);content 版本迁移机制(version 恒 1,仅预留)
  - 分节→简单转换的 API 级禁止(UI 拒绝,API 保持全量替换语义)

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
5:### Requirement: Report Template Columns
18:#### Scenario: Public and private templates share one tree model
26:#### Scenario: Content and prose columns are exclusive
33:### Requirement: Report Template Endpoints
43:#### Scenario: Tree returns both scopes independently
50:#### Scenario: Template page under a folder
56:#### Scenario: Template payloads carry the content object
64:### Requirement: Structured Template Content
79:#### Scenario: Structured template stores the section model
86:#### Scenario: Invalid content rejected
93:#### Scenario: Full-replace null content converts structured to prose
- `specs/web-system-console/spec.md`
5:### Requirement: Report Template Page
25:#### Scenario: Tab toggle switches scope without cross-leak
31:#### Scenario: Directory node creates a child directory
37:#### Scenario: Section mode edits sections and measures
44:#### Scenario: Mode switch converts prose to sections
50:#### Scenario: Structured to prose conversion refused
55:#### Scenario: Structured row renders summary cells


## Design Constraints

- **Architecture/Decisions**:
- **D1 content 完全接管(用户拍板)**:模板二选一——`content IS NULL` = prose(两列);`content IS NOT NULL` = 分节(两列不读、存 NULL)。替代案「列保持全局表现/意见」被否:分节模板没有全局表现,语义不对称。互斥由服务层在写路径强制(写 content 时置空两列),非 DB 约束(表达「读侧不读」无法用 CHECK 完成)。
- **D2 content 形状**:`{version:1, sections:[{name, finding|null, measures:[{code, label, unit|null, normal|null}]}], impression|null}`。`name` 必填自由文本(非「器官」绑定,通用性纪律);measures 任何节可挂(内镜节可为空数组)。code 与 label 并存:code 为报告域渲染/统计的稳定锚(如 AO/EF),label 是显示名(主动脉根部内径)。
- **D3 校验位置:服务层 Bean Validation + 手工断言**:嵌套 DTO `@Valid`/`@NotBlank` 覆盖 version/节名/code/label;sections 非空、目录拒 content 在服务层断言(A0400 域错误)。Gin 校验不做(无嵌套数组条件逻辑需求)。
- **D4 jsonb 落库:沿用 patient-domain 先例**——真实对象 + `@TableField(typeHandler = JacksonTypeHandler.class)` + JDBC Types.OTHER(具体 handler 以 patient-domain outbox 落地代码为准,不新造轮子)。
- **D5 迁移:V39 加列 + GIN**:`ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb` + `CREATE INDEX ... USING gin (content)`(架构规则规定 jsonb+GIN;GIN 服务未来按测量项 containment 找模板的查询,本表写入频度低,索引开销可忽略)。
- **D6 web 双形态编辑器**:添加表单顶部「简单模板/分节模板」Radio 切换;简单=现 640 抽屉不动;分节=960 抽屉平铺节卡片(节名 Input + 测量项行[项目/单位/正常范围+删除] + 节所见 TextArea + 折叠/删除节 + 添加节),全局影像学意见在底部。宽度按模式 `width={structured ? 960 : 640}`。编辑时按行 content 自动落模式。转换:简单→分节映射(表现→首节所见、意见→content.impression);分节→简单拒绝(warning)。表单状态为纯前端结构,保存时序列化 content。
- **D7 右侧表格分节行渲染**:表现列渲染「N 节 · M 项」摘要(token.colorTextTertiary),意见列取 `content.impression`(空显示 —)。分节行的 content 随 page 响应整行返回,无额外请求。
- **D8 目录节点**:flag=0 的 create/update 请求带 content 一律拒绝(A0400),与目录无内容语义一致。
- **Technical constraints**:
  - 跨域禁令:任务 1-2 只动 lumen-sev,任务 3.x 只动 lumen-web;单 change 跨栈(patient-domain 先例)
  - never-modify-existing-methods:扩展走重载/新增,不改既有公共方法签名
  - jsonb handler 沿用 patient-domain outbox 先例(D4),不新造轮子;Hutool 默认工具箱
  - 迁移编号与在途 V38 协调(落地前核对,顺延规则见 tasks 1.1);Flyway 已应用迁移不可改写
  - 前端无 mock 层(真后端);共享测试库 verify 需独占窗口 + rerunFailingTestsCount 容错

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 核对迁移编号(在途 V38 是否落库;确定本 change 用 V39 或顺延),写 `V39__rep_temp_content.sql`:`ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb` + `CREATE INDEX IF NOT EXISTS rep_temp_content_gin ON rep_temp USING gin (content)`
6:- [ ] 1.2 `RepTemp` 实体加 `content` 字段,沿用 patient-domain jsonb handler 先例(typeHandler + Types.OTHER),补 `@Builder` 组合纪律
7:- [ ] 1.3 新增嵌套 DTO:`TempContentRequest`(version/sections/impression)→ `TempSectionRequest`(name/finding/measures)→ `TempMeasureRequest`(code/label/unit/normal),`@Valid` + `@NotBlank` 覆盖 version=1、节名、code、label;`TempCreateRequest`/`TempUpdateRequest` 加 `@Valid content`
8:- [ ] 1.4 `RepTempServiceImpl.create/update`:content 校验(version=1、sections 非空、目录拒 content、写 content 时置空 finding/impression 两列)+ 契约对象 `RepTemp`(page 响应)带 content;TDD 先行(service 测试:分节存储 verbatim / 无效 content 拒绝 / 目录拒 content / content 非空时两列 NULL / null content 全量替换转 prose)
12:- [ ] 2.1 api 层测试:分节模板 create/update/page 往返 content;tree 不含 content(树节点仍是 id/name/flag/upId/children)
13:- [ ] 2.2 校验失败错误信息命名到字段(节名/测量项 code/label/version),errors spec 若需新增码位则在 delta 登记
17:- [ ] 3.1 类型契约:`RepTempContent/RepTempSection/RepTempMeasure` 类型 + `CreateRepTempBody/UpdateRepTempBody/RepTemp` 扩展 content(types.ts,从 exam-setting delta 派生)
18:- [ ] 3.2 模板表单加「简单模板/分节模板」模式 Radio:简单模式维持 640 抽屉现表单;分节模式 `width={structured ? 960 : 640}` 渲染节卡片列表(节名 Input + 测量项行[项目/单位/正常范围+行删+行增] + 节所见 TextArea + 节折叠/删除 + 添加节)+ 底部全局影像学意见;TDD 先行(模式切换/节编辑序列化 content/校验节名与测量项必填)
19:- [ ] 3.3 转换语义:简单→分节映射(表现→首节所见、意见→content.impression);分节→简单拒绝(warning 提示,模式保持分节)
20:- [ ] 3.4 编辑回填:structured 行(content 非空)直接落分节模式并回填节列表;prose 行落简单模式
21:- [ ] 3.5 右侧表格:分节行表现列渲染「N 节 · M 项」摘要(token.colorTextTertiary)、意见列取 content.impression(空 —);prose 行不变;TDD 先行(摘要/意见回显/prose 不受影响)
22:- [ ] 3.6 保存链路:create/update 序列化 content,目录节点(上级)不携带 content;错误信息透传 ApiError.msg
26:- [ ] 4.1 后端 reactor 全量测试(共享测试库独占窗口纪律)+ 前端 vitest 全量
27:- [ ] 4.2 WHEN/THEN 对照:exam-setting delta 5 场景 + web-system-console delta 6 场景逐条核验,verify.md 落 L1/L2/L3

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**:
  - 服务层:分节 content verbatim 存储 + 两列互斥置 NULL;无效 content 拒绝(空节名/空 code/label/空 sections/version≠1/目录带 content);null content 全量替换转 prose(exam-setting delta 3 场景)
  - api 层:create/update/page content 往返;tree 不含 content(2 场景)
  - web:模式切换与转换映射(表现→首节所见/意见→content.impression);分节→简单拒绝;节编辑序列化 content;分节行摘要「N 节 · M 项」+ 意见回显(4 场景)
- **Required edge cases**:
  - measures 空数组(内镜节);content.impression null;unit/normal null
  - 迁移编号 V38 竞态核对;目录节点 content 拒绝
- **Regression-sensitive areas**:
  - 存量 prose 模板端到端不变(现有 rep-temp-page 测试 15 用例全绿);树/分页/权限门控行为不变;简单模式表单渲染不变

## Execution Mode

- **Mode**: `SDD`
- **Selection rationale**: 12 任务 >3,跨模块(backend lumen-sev + frontend lumen-web)+ 新数据模型与迁移;另有并行会话占用共享测试库与迁移编号(风险指标)——不满足 Batch Inline 条件,SDD 执行,进度走 loop-state/progress 共享台账。

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
