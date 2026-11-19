# Proposal: rep-template-structured-content

## Why

报告模板目前只有 `finding`/`impression` 两个纯文字列——超声(心脏几十项测量值)、内镜(分部位所见)这类结构化报告内容无处安放,医生只能把测量值写成伪表格文字。EIS 参照系确认测量/脏器分区是净新增(旧版不存在),无先例可抄;架构规则已定调「report templates, structured findings → PostgreSQL jsonb + GIN」。富文本(HTML)已评估并否决:HTML 表格无法变成报告编辑器的可填写输入表格,服务端无法做正常范围校验,未来还要迁移。本裁决(用户拍板路线 B,2026-11-18):模板内容**通用分节模型**——不是彩超专属,放射(CT 分区测量)、内镜(分部位所见)、超声(单节/多节)共用同一模型。

## What Changes

- **后端(lumen-system setting 域)**:`rep_temp` 新增 `content jsonb NULL` 列 + GIN 索引(架构规则合规);`RepTemp` 实体、`TempCreateRequest`/`TempUpdateRequest`、`RepTemp` 契约对象扩展 `content` 字段(嵌套 DTO:version / sections[name, finding, measures[code, label, unit, normal]] / impression);服务层校验(content 非空时 version=1、节名必填、测量项 code/label 必填、目录节点拒绝 content);CRUD 全量替换语义不变。
- **共存模型(用户拍板:content 完全接管)**:模板二选一——`content = NULL` 走现有 `finding`/`impression` 两列(prose 模板,存量零迁移);`content` 非空 = 分节模板,节内所见(`sections[].finding`)+ 节内测量(`sections[].measures`)+ 全局结论(`content.impression`),两列不再读取(存 NULL)。
- **前端(lumen-web)**:模板表单升级双形态——prose 模式维持现 640 抽屉;新增「分节模式」开关,分节模式用 **960 宽抽屉平铺**(节卡片:节名 + 测量项行编辑[项目/单位/正常范围] + 节所见 textarea;节可增删折叠;全局影像学意见在底部)。prose → 分节可转换(表现→首节所见、意见→content.impression);分节 → prose 禁止(防丢内容)。右侧表格对分节模板行:表现列渲染「N 节 · M 项」摘要,意见列取 `content.impression`。
- **通用性纪律**:模型统一称「节(section)」——节名自由文本(不绑器官/部位词表,放射可填「右肺上叶」、内镜填「食道」、超声填「心脏」);测量项任何模态任何节可挂;报告纸面渲染规则按模态无关定义(节标题 → 节测量表 → 节所见 → 全局结论)。
- **不在本次范围(报告域后续)**:报告实例侧的测量值填写/正常范围校验/分节纸面渲染属于 lumen-core 报告域,本次只定模板侧契约与数据模型。

## Capabilities

### New Capabilities

(无——结构化内容是 exam-setting 内核能力的扩展,不新建 capability)

### Modified Capabilities

- `exam-setting`:`rep_temp` 列模型加 `content jsonb`;新增「结构化模板内容」需求(content 形状/校验/列互斥/全量替换语义);端点需求的请求/响应体扩展 `content`。
- `web-system-console`:「报告模板页面」需求升级——分节模式开关、960 分节编辑抽屉、节卡片编辑、转换映射、右侧表格分节行的摘要渲染。
