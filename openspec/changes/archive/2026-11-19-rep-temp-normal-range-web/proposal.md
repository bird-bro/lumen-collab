# Proposal: rep-temp-normal-range-web

## Why

后端 `rep-temp-normal-range-sev` 已将 `content` 升 v2（`measures[].normal`
tagged union），web 编辑器仍是自由文本 Input，无法产出/展示 v2 区间形态。
本 change 让编辑器与真后端契约对齐（无 mock，2026-11-05 裁决）。

## What Changes

- 测量项行「正常范围」从单一文本 Input 改为 **Segmented(区间/文本)** + 条件输入：
  区间 = min/max 两个 `InputNumber`（小数允许，单边留空即开区间）；文本 =
  ≤64 字符 Input。行内完成不换行（固定 960 抽屉）。
- 序列化恒写 v2 union：区间两侧全空 → `normal: null`；区间至少一端 →
  `{kind:'range', min, max}`；文本非空 → `{kind:'text', text}`。
- 客户端预校验：min > max 提交被拦截并命名提示（不发请求）；text 超长由
  Input maxLength 兜住。
- **BREAKING**（对内）删除 v1 backfill 分支：迁移后存量行已是 v2 union，
  编辑器按 `kind` 回显 Segmented 与输入；`types.ts` 注释 version pin 2。
- 既有结构化夹具 normal 升 union 形态；测试同步。

## Capabilities

### Modified Capabilities

- `web-system-console` — MODIFIED `Report Template Page`（测量项行编辑器
  union 化 + 固定 960 抽屉宽度裁决同步，MODIFIED 块按主规格场景超集纪律
  携带全部 5 个既有场景）。

## Impact

- `lumen-web/src/api/types.ts`（`RepTempMeasureNormal` union + `RepTempMeasure`）
- `lumen-web/src/pages/setting/rep-template/index.tsx`（测量项行编辑器、
  序列化、校验）
- `lumen-web/src/test/rep-temp-page.test.tsx`（夹具升 v2 + 新场景）
- 依赖：`rep-temp-normal-range-sev` verify PASS 后对真后端实施（后端先行）。
