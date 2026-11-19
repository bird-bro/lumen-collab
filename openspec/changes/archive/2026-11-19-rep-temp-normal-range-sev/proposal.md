# Proposal: rep-temp-normal-range-sev

## Why

测量项「正常范围」是自由文本（archived `rep-template-structured-content` D2），
报告填写侧无法用测值比对自动提示指标异常。用户裁决 2026-11-19：现在就结构化，
且存量 v1 行一次性迁移到位，不做双版本读兼容。

## What Changes

- **BREAKING** `content` 模型升版 v2：`measures[].normal: string|null` →
  `normal: null | {kind:'range', min, max} | {kind:'text', text}`（tagged union，
  用户拍板）。`code/label/unit` 不动。
- 一次性 Flyway **V47** 数据迁移（lumen-system 账本）：仅碰 `version=1` 行
  （幂等守卫）；严格模式集 `19-40`/`<5|≤5`/`>3|≥3` → range（`<5` 与 `≤5`
  同语义 `max=5` 平坦化，用户确认），不可解析（含「阴性」）→ text 原文，
  null → null；新装机自然 no-op。
- **BREAKING** 迁移后服务端只认 version 2：v1 形态写请求拒（A0400 命名字段），
  DTO 单一形态（union 结构由 Jackson 绑定守护）。
- range 校验：min/max 独立可空小数（≥x、≤x 单边开区间）；双填 min ≤ max
  否则 A0400；两端全空拒 A0400；无算子枚举。
- text 校验：非空、≤64。
- `content.version` 服务端钉 2（沿 pin-1 旧纪律）；嵌套 DTO Bean Validation
  按 kind 分组同步。

## Capabilities

### Modified Capabilities

- `exam-setting` — MODIFIED `Structured Template Content`（v2 shape、校验规则、
  单版本写）+ ADDED `Normal-Range v2 One-Shot Migration`（迁移行为契约）。

## Impact

- `lumen-sev/lumen-system`（setting 域：`RepTempMeasure` 嵌套 DTO、服务层校验、
  V47 迁移、既有 content 测试夹具升 v2）
- 无新增端点；create/update 请求体中 `content` 内部字段类型变化（前端由
  `rep-temp-normal-range-web` 跟进，sev verify 后对真后端实施）
- 报告填写侧的异常提示消费不在本期（未来提案）
