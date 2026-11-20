# Design — rep-temp-ops-chrome-web

## D1 — 单点缺陷定位

`rep-template/index.tsx` 的 `columns` 数组尾部手写 `key:'ops'` 列（width 130）：

- `<ConsoleAction label="删除" danger />` **漏传 `icon`** —— `ConsoleAction` 的
  `icon` 默认 `'edit'`（console-shell.tsx），铅笔字形骑上删除动词；
- 手写列绕过 `ConsoleTable` 的 `actions`/`onRefresh` 壳层 —— 操作表头右缘的刷新
  字形（console-shell.tsx 的 ReloadOutlined 徽标，2026-11-05 用户裁决）静默丢失。

全仓 grep `key: 'ops'` 仅此一处；`onRefresh` 已有 13 页面落——本页是最后一页漏网。

## D2 — 迁移方式（照 exam-method 先例 1:1）

- 删除手写 ops 列；`ConsoleTable` 增加 `actions={{ width: 130, render }}` +
  `onRefresh` 两个 props；
- render 内编辑/删除动词**原样搬运**：`tempPerm` 门控、删除的 Popconfirm 文案、
  `removeNode` 的 node 适配对象（`{id,name,flag,upId,children:[]}`）均不变；
- 删除动词补 `icon="remove"`；编辑走默认 edit 不传（与 exam-method 一致）；
- `onRefresh={() => void loadFiles(scope, modality, selectedNode?.id ?? null,
  nameFilter, pageNum, pageSize)}` —— 与 exam-method 的「重跑当前页查询」同形。

行为面唯一可见变化 = 缺陷修复本身（删除字形 + 刷新 affordance）；其余渲染、
权限、交互零变更。

## D3 — 测试（TDD，照 exam-method-page "rides the shell ops chrome" 惯例）

新增一测：

1. `getByRole('button', { name: '刷新列表' })` 存在且其 `closest('th')` 含
   操作 表头文本；
2. 点击刷新后 `pageRepTemps` 重新发起（mockClear 后 waitFor）；
3. 表格行内 删除 按钮含 `.anticon-delete`（删除字形证据）+ 编辑 按钮含
   `.anticon-edit`。

RED：手写列下无 刷新列表 按钮（getByRole 抛错）→ GREEN：迁壳后通过。

## D4 — spec delta 层叠

MODIFIED 块 = 主规格 Report Template Page 全文（10 个既有场景逐一保留，超集
规则）+ 需求文本补操作列壳层约定句 + 新场景「操作 header carries the refresh
affordance」（措辞对齐 Exam Item Page 既有同名声景，含 编辑/删除 字形断言）。
归档时主规格同步替换 + `<!-- synced from -->` 注释补本 change 一行。

## D5 — 记忆（用户裁决 2026-11-20）

缺陷家族录入会话记忆 `console-table-ops-chrome-invariants`：操作列只走
actions/onRefresh 壳层（禁手写 `key:'ops'`）+ ConsoleAction 非编辑动词必须显式
传 icon。新列表页起手即壳层，不再复制旧页手写模式。
