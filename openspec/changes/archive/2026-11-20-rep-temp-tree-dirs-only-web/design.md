# Design — rep-temp-tree-dirs-only-web

## D1 — 剪枝位置：加载边界，而非渲染时

`loadTree` 拿到 `repTempTree` 响应后立刻 `pruneToDirectories(rows)` 再
`setTree`，**不**在 renderTreeNode 里跳过叶子。理由：

- state 即不变式：re-anchor（同 id 复选 / flat[0] 兜底）、`flatTree`（上级选项
  + 目录回显）、renderTreeNode 三处下游无需各自防叶子；
- 兜底锚点从「第一个节点」变为「第一个目录」——叶子不再可能被选中，右表
  `loadFiles(upId)` 的 upId 恒为目录 id；
- renderTreeNode 的 `flag === 0` 守卫与三元图标随之成为死代码，一并删除
  （图标恒 ▰；删除节点 动词保留在目录上）。

helper（模块级，纯函数）：

```ts
function pruneToDirectories(nodes: RepTempNode[]): RepTempNode[] {
  return nodes
    .filter((node) => node.flag === 0)
    .map((node) => ({ ...node, children: pruneToDirectories(node.children) }))
}
```

## D2 — 两处 onClick 简化

- 新建目录：`openDirectoryAdd(selectedNode?.flag === 0 ? selectedNode.id : null)`
  → `openDirectoryAdd(selectedNode?.id ?? null)`
- 添加模板：`openTemplateAdd(selectedNode?.flag === 0 ? selectedNode : null)`
  → `openTemplateAdd(selectedNode)`（null 仍走 请先选择目录 警告，空树路径不变）

## D3 — 测试

重写原「leaves carry 删除 only」测为 dirs-only 断言（RED 先行）：

1. 树容器（`div[aria-busy]`）内 queryByText 叶子名 = null（RED：叶子在树被
   找到）；
2. 新建子目录/编辑目录/删除节点 三组按钮计数 3（原先 删除节点 为 4——叶子也
   有一颗）；
3. 右表行 `肺结节随访` 仍在（getByText 恰一match——树侧消失后唯一）。

叶子相关既有断言自查：`findAllByText('我的肺结节')`（个人 scope）由 2 match 变
1 match，findAllByText 仍通过；`findByText('肺结节随访')`（mount）唯一 match 落
右表行，轮询等待不受影响。

## D4 — spec delta 层叠

MODIFIED 块 = 主规格现文（含 rep-temp-ops-chrome-web 刚落的壳层句）全 11 场景
逐一保留 + 树句改写 + 新场景「Tree renders directories only」插在 Directory
node 场景之后。归档时主规格替换 + provenance 注释追加本 change。
