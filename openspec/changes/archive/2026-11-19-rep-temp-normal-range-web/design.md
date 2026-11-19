# Design: rep-temp-normal-range-web

## Context

模板抽屉（固定 960，2026-11-19 裁决）分节卡片的测量项行现为
`项目/名称/单位/正常范围(text Input ≤64)/删除` 五列；`TplFormValues` +
`tplSections` 纯前端状态，保存时 `saveTemplate` 序列化 content。服务端
`rep-temp-normal-range-sev` 已升 v2（normal tagged union，version 钉 2）。
共享裁决见 `openspec/coordination/rep-temp-normal-range.md`。

## Goals / Non-Goals

- **Goals**: 行内 union 编辑器（Segmented 区间/文本）、v2 序列化、客户端
  min>max 预校验、按 kind 回显；夹具与测试升 v2。
- **Non-Goals**: v1 兼容分支（迁移后不存在 v1 行）；填写侧异常提示 UI；
  模板形态/抽屉宽度等既有裁决改动。

## Decisions

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
  `normal: null`。恒写 `version: 2`。
- **D4 客户端预校验**：区间 min>max → `messageApi.error('正常范围最小值不能大于最大值')`
  并 return（不发请求、字段级定位到该行）；与既有 节名/测量项 校验同层。
- **D5 回显（openTemplateEdit）**：`kind==='text'` → Segmented 文本 + 输入
  回填；`kind==='range'` → Segmented 区间 + min/max 回填（null 端留空）；
  `normal===null` → 区间 + 空（不造第三态）。
- **D6 测试**：夹具 `STRUCTURED_TEMP` normal 升 union；既有 30 用例回归 +
  新增 spec delta 五场景用例（区间双边/单边、文本、空→null、min>max 拦截、
  kind 回显）；Segmented 交互沿 antd label 点击惯例，InputNumber 以
  aria-label（最小/最大）锚定。

## Risks / Trade-offs

- 行宽密度上升；960 定宽下五列 + Segmented + 双数字输入可达上限——若实施
  时溢出，优先压缩单位列宽而非换行（换行违背行内纪律）。

## Open Questions

- 无（设计已随 sev 侧整体确认）。
