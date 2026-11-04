# Batch 6b Brief — web-foundation-auth-console（tasks 6.4–6.7:角色/菜单/字典/日志 四页)

> 事实来源:`specs/web-system-console/spec.md`(权威)+ 已确认原型
> `prototype/role-menus.html`、`prototype/dicts-logs.html` + `src/api/*` clients +
> batch 6a 的共享组件(`src/pages/system/console-shell.tsx`、`org-tree.tsx`)与页面模式。
> **不要 commit**。TDD:先红后绿。只动:`src/pages/system/{roles,menus,dicts,logs}/**`、
> 必要的共享组件扩展、对应测试、本报告。不碰壳层/登录/路由/mock/规格/package.json。

## 页面公共规范(与 6a 完全一致,详见 `reports/web-foundation-b6a-report.md` §4)
紧凑列表页(无页头块)/ 工具行左查询右新增 / 操作列纯图标+tooltip+表头刷新 / 分页居中
~60px / A 类错误 `message.error`、表单类(重名/重码)落表单且弹窗不关 / 本地 hooks +
typed clients / hex 纪律 / `usePerm` 门控(`system:role|menu|dict|log:list`,不发明新串)。

## 任务 6.4 — 角色管理 `src/pages/system/roles/`(替换占位)
- 数据:`roles.listRoles()`(**IPage 形状**,服务端分页,pageNum/pageSize)→ Table 服务端
  分页;记录 `menuIds` 回显字段(mock-only,授权弹窗回显用,注释标注)。
- 工具行:左 = 角色名输入 + 查询(带 pageNum=1 重查);右 = 新建角色。
- CRUD:新建(重名 → **A0714** `数据重复:role name already exists: X` 落表单不关弹窗)、
  编辑(重命名/type/remark)、删除(Popconfirm;级联解绑后端语义,前端只需确认+重查)。
- **菜单授权弹窗**(核心):表格行「授权」图标 → Modal 内**可勾选树**
  (`menus.menuTree()` 全量树,目录/菜单可勾、按钮节点呈叶不可勾选展开按 perms 语义处理,
  报告记录勾选策略)→ 保存 = `assignRoleMenus(id, menuIds)` **全量替换** → 成功后重查
  roles/list 使 `menuIds` 回显新授权;**重开弹窗 = 精确回显已授权集**(spec 场景)。
- TDD:CRUD round-trip + 重名反馈 / 授权全量替换 + 重开回显 + 未知 menuId A0400。

## 任务 6.5 — 菜单管理 `src/pages/system/menus/`(**只读**)
- `menus.menuTree()` 渲染只读树(或树形 Table,取原型/实现最自然者,报告备案):
  目录/菜单/按钮三级结构,**每个节点可见 `perms` 串**(spec 明文);无任何变更控件。
- TDD:树渲染(三分层 + perms 可见)+ 只读断言(无按钮/无请求变更)。

## 任务 6.6 — 字典管理 `src/pages/system/dicts/`
- 布局(按原型 dicts-logs.html 上半):**目录表 + 目录下方明细表**(非抽屉,原型形态)。
- 目录:`dicts.listCatal(pageNum,pageSize)` IPage 服务端分页;新建/编辑/删除
  (重复 code → **A0714** `数据重复:dict catalog code already exists: X` 落表单不关弹窗)。
- 明细:选中目录行 → 下方明细表 `dicts.listDetails(caCode)`(**裸数组,sort 升序**);
  新建/编辑/删除(重复 (caCode,value) → **A0714** `数据重复:dict detail value already
  exists under caCode X: Y`);缺 caCode 参数场景由「未选中目录时禁用明细新增」规避
  (报告记录)。
- TDD:目录分页 + CRUD + 重码 / 明细按目录加载 + sort 序 + CRUD + 重 value 反馈。

## 任务 6.7 — 操作日志 `src/pages/system/logs/`(**只读**)
- `logs.listLogs({page,pageSize,type?,uid?,dateYm?})` IPage;工具行左 = type/uid/dateYm
  筛选 + 查询(带重置);表格 actionTime **倒序**;**零变更控件**(spec: append-only);
  分页走服务端(page/pageSize)。
- TDD:过滤查询(各筛选参数断言)+ 倒序 + 只读断言 + 分页。

## 验证(留证)
1. `pnpm --dir lumen-web test` 全绿(127 零回归 + 新测试,报总数)。
2. `pnpm --dir lumen-web build` 退出 0;hex 守卫绿;无新依赖。
3. mock 冒烟(5179,场景组间重启 dev server,测完 kill+端口确认):roles CRUD+重名+
   授权全量替换与重开回显;menus 只读;dicts 目录/明细 + 两类 A0714;logs 过滤+倒序。
4. 范围核对:仅四页目录 + 共享组件必要扩展 + 测试 + 报告。

## 完成标准
全部验证过;报告写 `openspec/sdd/reports/web-foundation-b6b-report.md`(中文:RED→GREEN、
覆盖矩阵、布局来源、勾选策略、冒烟证据、问题)。失败即停、如实记录。
