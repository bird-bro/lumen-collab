# Tasks

## 1. 后端:迁移与数据模型(lumen-sev / lumen-system setting 域)

- [x] 1.1 核对迁移编号(在途 V38 是否落库;确定本 change 用 V39 或顺延),写 `V39__rep_temp_content.sql`:`ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb` + `CREATE INDEX IF NOT EXISTS rep_temp_content_gin ON rep_temp USING gin (content)`
- [x] 1.2 `RepTemp` 实体加 `content` 字段,沿用 patient-domain jsonb handler 先例(typeHandler + Types.OTHER),补 `@Builder` 组合纪律
- [x] 1.3 新增嵌套 DTO:`TempContentRequest`(version/sections/impression)→ `TempSectionRequest`(name/finding/measures)→ `TempMeasureRequest`(code/label/unit/normal),`@Valid` + `@NotBlank` 覆盖 version=1、节名、code、label;`TempCreateRequest`/`TempUpdateRequest` 加 `@Valid content`
- [x] 1.4 `RepTempServiceImpl.create/update`:content 校验(version=1、sections 非空、目录拒 content、写 content 时置空 finding/impression 两列)+ 契约对象 `RepTemp`(page 响应)带 content;TDD 先行(service 测试:分节存储 verbatim / 无效 content 拒绝 / 目录拒 content / content 非空时两列 NULL / null content 全量替换转 prose)

## 2. 后端:API 契约面(auth 测试与错误码)

- [x] 2.1 api 层测试:分节模板 create/update/page 往返 content;tree 不含 content(树节点仍是 id/name/flag/upId/children)
- [x] 2.2 校验失败错误信息命名到字段(节名/测量项 code/label/version),errors spec 若需新增码位则在 delta 登记

## 3. 前端:双形态模板表单(lumen-web)

- [x] 3.1 类型契约:`RepTempContent/RepTempSection/RepTempMeasure` 类型 + `CreateRepTempBody/UpdateRepTempBody/RepTemp` 扩展 content(types.ts,从 exam-setting delta 派生)
- [x] 3.2 模板表单加「简单模板/分节模板」模式 Radio:简单模式维持 640 抽屉现表单;分节模式 `width={structured ? 960 : 640}` 渲染节卡片列表(节名 Input + 测量项行[项目/单位/正常范围+行删+行增] + 节所见 TextArea + 节折叠/删除 + 添加节)+ 底部全局影像学意见;TDD 先行(模式切换/节编辑序列化 content/校验节名与测量项必填)
- [x] 3.3 转换语义:简单→分节映射(表现→首节所见、意见→content.impression);分节→简单拒绝(warning 提示,模式保持分节)
- [x] 3.4 编辑回填:structured 行(content 非空)直接落分节模式并回填节列表;prose 行落简单模式
- [x] 3.5 右侧表格:分节行表现列渲染「N 节 · M 项」摘要(token.colorTextTertiary)、意见列取 content.impression(空 —);prose 行不变;TDD 先行(摘要/意见回显/prose 不受影响)
- [x] 3.6 保存链路:create/update 序列化 content,目录节点(上级)不携带 content;错误信息透传 ApiError.msg

## 4. 验证与收尾

- [x] 4.1 后端 reactor 全量测试(共享测试库独占窗口纪律)+ 前端 vitest 全量
- [x] 4.2 WHEN/THEN 对照:exam-setting delta 5 场景 + web-system-console delta 6 场景逐条核验,verify.md 落 L1/L2/L3
