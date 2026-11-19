# Design: rep-temp-normal-range-sev

## Context

`rep_temp.content` 是 jsonb（V39 加列 + GIN），经
`RepTempContentJsonbTypeHandler`（JacksonTypeHandler 系）整体序列化；
嵌套 DTO `RepTempContent → RepTempSection → RepTempMeasure`（均为
`@Data @Builder @AllArgsConstructor @NoArgsConstructor`，`code/label`
`@NotBlank`），服务层断言 version/sections 非空/目录拒 content，版本钉 1
（archived `rep-template-structured-content` D2/D3）。共享裁决与 v2 形态见
`openspec/coordination/rep-temp-normal-range.md`。

## Goals / Non-Goals

- **Goals**: v2 union DTO + Bean Validation；V47 一次性迁移（幂等、确定性
  规则）；服务端单版本（v2）写；既有测试夹具与断言升 v2。
- **Non-Goals**: 报告填写侧异常提示消费（未来提案）；双版本读兼容（迁移后
  无 v1 行，用户拍板放弃）；`code/label/unit` 语义变化；任何新端点。

## Decisions

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
  (LATERAL jsonb 重建)`——`WITH ORDINALITY` 保 sections/measures 顺序，
  measure 级 `CASE` + PG 正则（trim 后）：
  `^(\d+(\.\d+)?)\s*[-~]\s*(\d+(\.\d+)?)$` → `{kind:'range',min,max}`；
  `^[<≤]\s*(\d+(\.\d+)?)$` → `{kind:'range',min:null,max}`；
  `^[>≥]\s*(\d+(\.\d+)?)$` → `{kind:'range',min:null→max 对调}`（即 min 有值
  max 空）；其余非空 → `{kind:'text',text:原文}`；SQL NULL → JSON null。
  数字以 `::numeric` 进 `jsonb_build_object`（jsonb number）。守卫
  `WHERE content->>'version' = '1'`（新装机列空 → 0 行，no-op）。`<` 与 `≤`
  的严格性差异模型不可表达——平坦化为同一区间端点，已在 spec/design 记录，
  用户确认。
- **D4 错误码**：全部域校验走既有 AuthDenyAdvice/服务层断言 → A0400 信封，
  message 命名字段（「正常范围最小值不能大于最大值」「content 版本必须为 2」
  等）；不新增错误码族。
- **D5 测试基座**：沿用 `RepTempContentApiTests` + 套件基座（`@MockBean`
  Feign 缝桩先例）；迁移断言用 fixture SQL 造 v1 行 → 跑迁移逻辑断言
  （V47 以 schema-clean 方式在测试库重放，遵循「共享测试库验证四坑」：
  独占窗口 + 幂等重放断言）。

## Risks / Trade-offs

- 迁移 SQL 较繁（嵌套 jsonb 重建）；换来的是纯账本内可审查、无 Java 迁移
  先例破坏。若实施中发现 PG 正则/重建不可维护，回退方案是 Flyway Java
  migration（Jackson 复用同一规则集）——默认不做。
- `<5`→`max=5` 平坦化：填写侧未来若需严格性，属该提案的模型扩展，本期不预留。

## Open Questions

- 无（用户 2026-11-19 三拍板 + 设计整体确认）。
