# rep-temp-ops-chrome-web — 报告模板页操作列迁入壳层 chrome：删除换 delete 字形 · 操作表头右缘挂刷新

**Date**: 2026-11-20 · **Status**: proposed · **Stack**: lumen-web（单栈轻量提案）

## Why

用户报告 2026-11-20：`/setting/rep-templates` 右表操作列图标错误、操作列右侧缺少
刷新图标。复核确认两处缺陷，且均为**三度复发**的同一家族（用户点名要求记入前端
记忆，不得再犯）：

1. **删除骑 edit 字形**：手写操作列里的 `<ConsoleAction label="删除" danger />`
   未传 `icon` —— `ConsoleAction` 默认 `icon='edit'`，铅笔字形骑上删除动词
   （exam-item 评审第二轮 2026-11-17 修过一模一样的错）。
2. **操作表头无刷新 affordance**：`columns` 里手写 `key:'ops'` 列绕过了
   `ConsoleTable` 的 `actions`/`onRefresh` 壳层 —— 表头右缘的刷新字形（2026-11-05
   用户裁决，13 个页面已落）静默丢失。

家族史：exam-item + exam-method 2026-11-17 由 exam-item-table-polish-web 第二轮
评审修复；rep-template 页面 2026-11-18（修复之后）新建时又手写操作列，缺陷复制。
rep-template 是最后一个手写操作列的页面（全仓 `key: 'ops'` 仅此一处）。

## What Changes

报告模板页（`/setting/rep-templates`）右表，仅操作列迁壳，其余零变更：

- 手写 `key:'ops'` 列删除；编辑/删除动词原样迁入 `ConsoleTable` 的
  `actions={{ width: 130, render }}`（权限门控 `tempPerm('update'/'delete')`、
  删除的 Popconfirm 包裹与 `removeNode` 适配对象均保持不变）；
- 删除动词显式 `icon="remove"`（delete 字形；编辑走 edit 默认不动）；
- `onRefresh` 重跑当前查询：当前 scope/目录/名称过滤/页码下的 `pageRepTemps`；
- 页顶 JSDoc 补一句壳层迁移说明。

**spec delta**：`web-system-console` § Report Template Page（MODIFIED，全场景
超集）——需求文本补操作列壳层约定 + 新场景「操作 header carries the refresh
affordance」（参照 Exam Item/Body/Method Page 三处既有同名声景）。

**记忆**：缺陷家族已录入会话记忆（console-table-ops-chrome-invariants）——操作列
只走 actions/onRefresh 壳层、ConsoleAction 非编辑动词必须显式传 icon。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx` +
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` § Report Template Page

## Impact

- 无 API 契约变更，无库变更，无后端文件，无 coordination doc（单栈轻量）。
- 归档无顺序依赖（本 delta 为 Report Template Page 终态的唯一未同步块）。
