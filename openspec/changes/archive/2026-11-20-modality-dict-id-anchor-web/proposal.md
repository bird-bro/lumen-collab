# Proposal

## Why
4 个 `/setting` 页（检查项目/检查部位/检查方法/报告模板）的列表加载被
`dictItemsByName('检查类型')` 门控——该目录名在 V27 统一后不存在，按名端点契约返回
`[]`（不报错）且空结果进会话缓存，`modality` 门永不打开，页面零 API 调用，与
web-system-console 主规格的页面行为要求相悖。

## What Changes
- `src/utils/dict-items.ts` 新增 `MODALITY_DICT_ID = 1024`（沿用 `NUMBER_TYPE_DICT_ID` 形状）。
- 4 个页面 `dictItemsByName('检查类型')` → `dictItemsById(MODALITY_DICT_ID)`，移除失效的目录名常量。
- 对应单测 mock 从 by-name 改 by-id。
- 页面呈现零变化——恢复主规格本已要求的行为；字典解析从按名端点切到 items-by-id 端点。

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `web-system-console`: 新增「Modality Catalog Resolution by Id」要求——设置页字典解析的持久契约，落地 dict-lookup-by-id 裁决（2026-11-19）的前端半边。

## Impact
- 代码：`src/utils/dict-items.ts` + 4 个 setting 页 + 4 个测试文件。
- rep-template 页叠有未提交 rep-temp-normal-range 改动（同文件叠改，无逻辑交集，coordination doc 已记录）。
- users/room/device 等存量按名消费者不动（coordination debt，另行提案）。
- 无契约/无 schema 变化；会话缓存旧键（by-name）自然失效，无需清缓存逻辑。
