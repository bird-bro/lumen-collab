# Batch 6a Brief — web-foundation-auth-console（tasks 6.1–6.3:机构/科室/用户 三页）

> 事实来源:`specs/web-system-console/spec.md`(权威 WHEN/THEN)+ 已确认原型
> `prototype/org-deps.html`、`prototype/console-shell.html`(视觉与交互基准)+
> `specs/api/spec.md` 端点契约。工作目录 REPO。**不要 commit**。TDD:先红后绿。
> 只动:`lumen-web/src/pages/system/{orgs,deps,users}/**`、相关新共享组件
> (`src/components/` 或 `src/pages/system/` 内,报告备案位置)、对应测试文件、本报告。
> **不碰**:壳层/登录/路由/mock/规格。

## 页面公共规范(三页一致,来自原型与规格)
- **紧凑列表页**:无页头标题块;内容区直接「工具行 → 表格 → 分页」;内容区 padding 已是 10px。
- **工具行**:左 = 查询组件 + 查询钮;右 = 主色新增钮(icon+文字,AntD PlusOutlined)。
- **表格**:AntD Table,操作列**纯图标**(tooltip 中文,表头「操作」右侧刷新小图标);
  分页行**居中**(高约 60px)。斑马纹/表头色走既有 Table 组件 token(不动)。
- **错误反馈**:A 类错误 → `message.error(msg)`;表单校验类(重名/重码)→ 表单项内
  红字 + **弹窗不关闭**(spec 明文)。
- **数据流**:页面本地 hooks(mount 加载 + 变更后重查),复用 `src/api/*` typed clients;
  禁止在页面拼 URL、禁止内联 hex(hex 守卫常绿)。
- **权限门控**:页面级用 `usePerm` + 既有 perms 串(如 `system:org:list`)做轻量演示
  (如操作列按 perms 隐藏),不得发明新 perms 字符串(报告记录用法)。
- 弹窗:AntD Modal + Form;成功后关闭并重查列表。

## 任务 6.1 — 机构管理 `src/pages/system/orgs/`(替换占位)
- 布局:树为主体的工作区(原型 org-deps 左树右表中的**树侧**这里即主体——按原型,
  机构页 = 全宽树或树+详情,以原型为准;报告说明取自原型的哪部分)。
- 数据:`orgs.orgTree()` 渲染 `Tree`;种子根「示例医院」可见;**多根**:顶部「新建根机构」
  虚线钮(spec multi-root 场景)。
- 操作:节点悬停/选中工具条 → 新建子机构(Modal:名称等,POST 后重查)、编辑(PUT)、
  删除(DELETE,Popconfirm);后端 A0400 类错误照 `msg` 展示。
- TDD:CRUD round-trip(断言 client 调用与树刷新)、多根创建、删除确认。

## 任务 6.2 — 科室管理 `src/pages/system/deps/`
- 布局:**左机构树 aside(~250px)+ 右列表**(原型 org-deps 模式);树选中承载机构上下文
  (无 badge)。
- 数据:`deps.listDeps(orgId)` 按选中机构过滤;三种子科室默认可见(医院根选中时)。
- 工具行:左 = 科室编码/名称输入 + 查询钮(前端过滤或重查,报告记录);右 = 新建科室
  (Modal:编码必填 + 用途说明文案,按原型;重复 code → A0712 msg 于表单,弹窗不关)。
- 操作:编辑(PUT)/删除(DELETE)图标钮;列表回显 name/code/orgId。
- TDD:按机构过滤、CRUD round-trip、重复 code 反馈不关弹窗。

## 任务 6.3 — 用户管理 `src/pages/system/users/`
- 布局:标准表格页(原型 console-shell 的用户表区):工具行(左 = 科室下拉筛选 + 查询,
  右 = 新建用户)+ 表格 + 居中分页。
- 数据:`users.listUsers()` **裸数组** → 客户端分页(AntD pagination)+ 前端过滤;
  列:用户名/姓名/角色/启用(Switch)/创建时间 + 操作列。
- **操作列(按端点实情,无 delete/edit 端点!)**:重置密码(钥匙图标,Modal 输新密码 →
  `PUT /{id}/password` → mustChangePassword 置 1 回显)、启用/停用(Switch →
  `PUT /{id}/enable`,A0400 越界错误照 msg)。
- 新建用户 Modal:用户名/姓名/初始密码等(按 mock UserItem 形状);重复用户名 →
  A0111「用户名已存在」于表单,**弹窗不关**;成功关闭重查。
- TDD:列表渲染 + 客户端分页、新建(成功/重复)、密码重置回显、enable 切换。

## 验证(留证)
1. `pnpm --dir lumen-web test` 全绿(91 零回归 + 新测试,报总数)。
2. `pnpm --dir lumen-web build` 退出 0;hex 守卫绿。
3. mock 冒烟(5179,测完 kill):三页逐 scenario 走查(orgs 多根、deps 过滤+重码、
   users 重名+密码+enable),截图/断言清单入报告。
4. 范围核对:仅三页目录 + 新共享组件 + 测试 + 报告。

## 完成标准
全部验证过;报告写 `openspec/sdd/reports/web-foundation-b6a-report.md`(中文:RED→GREEN、
布局取自原型哪些区块、权限门控用法、冒烟证据、问题)。失败即停、如实记录。
