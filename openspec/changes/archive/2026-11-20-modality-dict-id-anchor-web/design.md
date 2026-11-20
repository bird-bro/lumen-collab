# Design

## Context
取数链：页面 mount → `dictItemsByName('检查类型')` → 会话缓存（`utils/dict-items.ts`
ITEMS_CACHE）→ 空 `[]` 作为**成功**结果缓存 → `if (modality)` 门控不开 → 零取数。
事实链见 `openspec/coordination/modality-dict-id-anchor.md` §Shared facts。

## Goals / Non-Goals
**Goals:** 4 页恢复取数；解析锚换 id；单测 mock 同步。
**Non-Goals:** users/room/device 存量按名迁移（debt，另行提案）；空词表页面降级
行为重设计（词表非空为常态，`if (modality)` 门控语义保留）。

## Decisions
- **D1 常量形状**：`MODALITY_DICT_ID = 1024` 放 `dict-items.ts`，镜像
  `NUMBER_TYPE_DICT_ID`；页面不再持有模态目录名字符串（aria-label/文案与目录名解耦，
  「检查类型」作为 UI 标签保留）。
- **D2 mock 缝**：4 个页面测试 mock `@/utils/dict-items` 的 `dictItemsByName` → 改
  `dictItemsById(1024)`，断言同步。users-page 测试 mock 的是 `@/api/dicts`
  （按名「设备类型」），不在本变更面内。
- **D3 门控语义不动**：`if (modality)` 级联门保留——字典加载失败仍不拉列表
  （错误已 toast）；只换解析源。

## Risks / Trade-offs
- [rep-template 同文件叠改 rep-temp-normal-range 未提交工作] → 单点小编辑、无逻辑
  交集；apply 时先读该文件最新盘上状态。
- [会话缓存残留旧空结果] → 旧缓存键是 by-name 键，切 id 键后自然失效，无需清缓存。

## Migration Plan
纯前端，`pnpm dev` 即时生效；回滚 = revert 单 commit。
