# Batch 6b 报告 — web-foundation-auth-console(tasks 6.4–6.7:角色/菜单/字典/日志)

> 执行说明:6b 由协调者**在主会话直接实现**(6a 的两次子代理故障后,子代理模型解析
> 不可用;主会话有全量上下文,实现+测试+修错一体完成)。TDD:28 个新用例先写(模块缺失
> 全量 RED)→ 实现转 GREEN。状态:**完成,155/155 全绿,build/hex 全过**。未 commit。

## 1. 交付物

| 页面 | 文件 | 要点 |
|:--|:--|:--|
| 角色管理 | `src/pages/system/roles/index.tsx` | IPage 服务端分页;CRUD(重名 A0714 落表单不关弹窗);**菜单授权弹窗**(勾选集即全量授权,保存=全量替换 PUT,重开按 roles/list 的 menuIds 回显;保存时 withAncestors 并祖先保证授权树完整) |
| 菜单管理 | `src/pages/system/menus/index.tsx` | **只读**三级树,目录/菜单/按钮 Tag 标注,每个节点 perms 串可见(monospace);零变更控件 |
| 字典管理 | `src/pages/system/dicts/index.tsx` | 目录表(服务端分页)+ **下方明细表**(原型形态);行选中加载明细(sort 升序);两类 A0714 落表单;未选目录时新建明细禁用(规避 A0410) |
| 操作日志 | `src/pages/system/logs/index.tsx` | **只读**;type/uid/dateYm 过滤 + 重置;actionTime 倒序;服务端分页(page/pageSize);零变更控件 |

测试:`{roles,menus,dicts,logs}-page.test.tsx` 28 条。总数 127 → **155**。

## 2. TDD 与过程中抓到的真实问题

1. **ConsoleTable 不转发 `onRow`**(共享组件缺口)——字典页"行选中加载明细"因此失效;
   扩展 ConsoleTable 透传 onRow(6b 唯一共享组件改动,报告备案)。
2. **roles/dicts 挂载双取**——`[query]` effect 挂载即触发,首屏 listRoles/listCatal 发两次;
   加 `useRef` skip-first 守卫。
3. **logs 查询/重置不触发请求**——page 已是 1 时 setState 不翻转,页内 effect 不跑;
   runQuery/reset 显式调用 reload。
4. **AntD Tree 勾选机制与全量替换契约冲突**(本批最大决策):`onCheck` 收到**字符串化 key**
   (数字 key 内部失配 → `[]`),级联勾选又会剔除半选父级——两者都破坏"勾选集=完整授权"
   语义。**换实现**:授权树改为 AntD `Checkbox` 嵌套列表(每节点独立勾选,checkStrict 语义
   显式化),保存时 `withAncestors` 并祖先。最小复现(scratch)证实 Tree 行为后替换,已删探针。
5. **AntD 两字按钮自动插空格**(重 置/删 除)——测试断言用 `\s*` 容错(与 6a「保 存」同理)。
6. 测试夹具类型对齐(MenuNode.isCache/isFrame 为 boolean、LogItem 全字段)由 strict tsc 抓出。

## 3. 覆盖矩阵(brief × 测试)

**6.4 roles**(10):IPage 渲染 / 翻页 pageNum 重查 / 新建 round-trip / 重名 A0714 不关弹窗 /
删除 Popconfirm(「删 除」)+重查 / **授权弹窗按 menuIds 回显(3 节点全勾)** / 保存=全量替换
([1,7,13]) / **取消勾选→重开回显新授权([1,13])** / 未知 menuId A0400 浮层 / perms 门控

**6.5 menus**(4):三级树渲染 / perms 可见(system:user:list、system:report:audit)/ 只读断言
(无新建/删除/checkbox) / 节点类型标注

**6.6 dicts**(8):目录 IPage 渲染 / 新建 round-trip / 重码 A0714 不关弹窗 / 行选中加载明细 /
明细 round-trip / 重 value A0714 不关弹窗 / 未选目录禁用新建明细 / perms 门控

**6.7 logs**(6):倒序渲染 / 三过滤器参数断言 / 重置清空重查 / 服务端分页(page 键)/
只读断言(无操作列) / 无 perm 可读

## 4. 冒烟证据(5179,API 级;测完端口释放)

- roles:list(records 含 menuIds:15)/ 新建 `data:2` / 重名 A0714 逐字 / 全量替换 [1,13] →
  echo [1,13] / 未知 [99] → A0400 逐字;还原全量后继续
- menus:三级树 + 全部 perms 逐字(system:org:list … radiology:report:audit)✓
- dicts:catal IPage(common_status)/ details sort 升序 [启用1,停用2] / 重 value A0714 逐字 ✓
- logs:`type=新增` 过滤 total 1 ✓
- 浏览器级走查归 Batch 7.2 统一执行。

## 5. 遗留 / 备注

- 角色"类型"列显示种子原值 10(原型确认清单问题 4,用户整体确认时未单独改)——备案。
- roles/list 仍为 mock 供数(缺口端点);后端跟进变更实现后,roles 页无需改动(消费 typed client)。
- AntD Tree → Checkbox 列表的决策已写进组件注释;若未来升级 AntD 且 Tree 行为修复,可评估换回。
