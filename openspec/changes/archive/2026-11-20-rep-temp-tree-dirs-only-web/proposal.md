# rep-temp-tree-dirs-only-web — 报告模板页左侧树只渲染目录：模板叶子不上树

**Date**: 2026-11-20 · **Status**: proposed · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-20：`/setting/rep-templates` 左侧树不用显示模板，只需要目录——
模板是叶子节点（flag=1），不会有下一级，上树没有层级语义。

叶子此前在树上的唯一动词是 删除节点，而右表操作列已有模板的 编辑/删除（上一
change rep-temp-ops-chrome-web 刚落），能力零损失；树上目录节点仍保留
新建子目录/编辑目录/删除节点 全套。

## What Changes

报告模板页（`/setting/rep-templates`）左树，仅树数据收敛，其余零变更：

- `loadTree` 在加载边界递归剪枝（新 helper `pruneToDirectories`：filter
  `flag===0` + children 递归）——树 state 从此只含目录，下游全部受益：
  - 选中锚点（re-anchor / flat[0]）必为目录，**添加模板 首选即可用**
    （原先选中叶子时点 添加模板 会警告 请先选择目录）；
  - 上级选项、模板抽屉的目录回显沿用现有 flatTree，行为不变；
- `renderTreeNode` 简化：图标恒 ▰、`node.flag === 0` 守卫删除（不变式已由剪枝
  保证）、「新建目录/添加模板」两处 onClick 的 flag 三元随之简化；
- 页顶 JSDoc 与测试头注释同步改写。

**不变式**：`selectedNode` 只可能是目录或 null——右表 `loadFiles(upId)`、
删除节点（目录）行为不变；空树仍渲染 暂无节点、添加模板 警告不变。

**spec delta**：`web-system-console` § Report Template Page（MODIFIED，全场景
超集）——需求文本的 "template leaves carry 删除 only" 改为 "the left tree
renders DIRECTORIES ONLY"，新增场景「Tree renders directories only」。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx` +
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` § Report Template Page

## Impact

- 无 API 契约变更（树接口返回不变，剪枝纯前端）、无库变更、无后端文件、
  无 coordination doc（单栈轻量）。
- 归档无顺序依赖（本 delta 为 Report Template Page 终态的唯一未同步块）。
