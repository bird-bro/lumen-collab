# Design

## Context

`rep_temp`(检查设置内核,exam-setting capability)目前 `finding`/`impression` 两 text 列承载全部内容;web 端模板表单是 640 宽抽屉(简单表单)。jsonb 处理的项目先例:patient-domain outbox 的「真实 jsonb 对象 + Types.OTHER handler」路线。迁移账本现状:V38(`exam_method_remark`)为另一在途会话的未跟踪迁移——本 change 的迁移从 **V39** 起,编号协调记入 tasks。EIS 参照系无测量/分节先例(`lumen-reference/NOTES-us-workstation.md` §2.4 净新增候选),模型为自由设计,受架构规则「灵活结构 → jsonb + GIN」约束。

## Goals / Non-Goals

**Goals:**
- 通用分节内容模型(content jsonb),跨模态(放射/超声/内镜)共用,节名自由文本
- 模板侧 CRUD 全链路:迁移 → 实体/DTO 校验 → 契约 → web 双形态编辑器
- 存量 prose 模板零迁移(二选一共存,content 完全接管)

**Non-Goals:**
- 报告实例侧(测量值填写、正常范围校验、分节纸面渲染、打印)——lumen-core 报告域后续提案
- 测量项目词表(dict 体系)/ 器官·部位词表绑定——v1 节名与测量项均自由输入,词表化待统计需求出现
- 分节模板的版本迁移机制(version 字段仅为将来结构演进预留,当前恒为 1)

## Decisions

- **D1 content 完全接管(用户拍板)**:模板二选一——`content IS NULL` = prose(两列);`content IS NOT NULL` = 分节(两列不读、存 NULL)。替代案「列保持全局表现/意见」被否:分节模板没有全局表现,语义不对称。互斥由服务层在写路径强制(写 content 时置空两列),非 DB 约束(表达「读侧不读」无法用 CHECK 完成)。
- **D2 content 形状**:`{version:1, sections:[{name, finding|null, measures:[{code, label, unit|null, normal|null}]}], impression|null}`。`name` 必填自由文本(非「器官」绑定,通用性纪律);measures 任何节可挂(内镜节可为空数组)。code 与 label 并存:code 为报告域渲染/统计的稳定锚(如 AO/EF),label 是显示名(主动脉根部内径)。
- **D3 校验位置:服务层 Bean Validation + 手工断言**:嵌套 DTO `@Valid`/`@NotBlank` 覆盖 version/节名/code/label;sections 非空、目录拒 content 在服务层断言(A0400 域错误)。Gin 校验不做(无嵌套数组条件逻辑需求)。
- **D4 jsonb 落库:沿用 patient-domain 先例**——真实对象 + `@TableField(typeHandler = JacksonTypeHandler.class)` + JDBC Types.OTHER(具体 handler 以 patient-domain outbox 落地代码为准,不新造轮子)。
- **D5 迁移:V39 加列 + GIN**:`ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb` + `CREATE INDEX ... USING gin (content)`(架构规则规定 jsonb+GIN;GIN 服务未来按测量项 containment 找模板的查询,本表写入频度低,索引开销可忽略)。
- **D6 web 双形态编辑器**:添加表单顶部「简单模板/分节模板」Radio 切换;简单=现 640 抽屉不动;分节=960 抽屉平铺节卡片(节名 Input + 测量项行[项目/单位/正常范围+删除] + 节所见 TextArea + 折叠/删除节 + 添加节),全局影像学意见在底部。宽度按模式 `width={structured ? 960 : 640}`。编辑时按行 content 自动落模式。转换:简单→分节映射(表现→首节所见、意见→content.impression);分节→简单拒绝(warning)。表单状态为纯前端结构,保存时序列化 content。
- **D7 右侧表格分节行渲染**:表现列渲染「N 节 · M 项」摘要(token.colorTextTertiary),意见列取 `content.impression`(空显示 —)。分节行的 content 随 page 响应整行返回,无额外请求。
- **D8 目录节点**:flag=0 的 create/update 请求带 content 一律拒绝(A0400),与目录无内容语义一致。

## Risks / Trade-offs

- **V38 编号竞态**:另一会话在途迁移未提交;本 change 定 V39,若 apply 时 V38 已占用 V39,顺延 V40(tasks 含落地前核对步骤)。
- **分节→简单转换被 UI 拒绝但 API 允许**(full-replace null = 破坏性转换,spec 已声明):前端防误操作,API 保持全量替换语义简单;数据丢失风险由 UI 挡住,Power-user 可走 API(接受)。
- **960 抽屉在窄屏(<1280)覆盖右侧表**:抽屉是模态操作流,可接受;不做响应式降级(v1)。
- **content 无 JSON Schema 级 DB 约束**:校验在应用层;绕过 API 直写 DB 不设防(与全库一致的做法)。
