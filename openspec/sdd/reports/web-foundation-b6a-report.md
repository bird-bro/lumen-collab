# Batch 6a 报告 — web-foundation-auth-console(tasks 6.1–6.3:机构/科室/用户)

> **续作说明**:本批由两位执行者接力完成——前任代理完成全部实现后死于 API 配额中断
> (死在冒烟阶段,未写报告);协调者接手完成审计、冒烟与本报告。实现代码未推倒重写。
> 状态:**完成,全部验证通过**。未 commit(由协调者提交)。

## 1. 交付物

| 页面 | 文件 | 内容 |
|:--|:--|:--|
| 机构管理 | `src/pages/system/orgs/index.tsx` + `org-tree.tsx`(共享树组件) | 全宽树工作区;种子根「示例医院」;**多根**「新建根机构」虚线钮;节点新建子机构/编辑/删除(Popconfirm) |
| 科室管理 | `src/pages/system/deps/index.tsx` | **左机构树 aside(~250px)+ 右列表**(原型 org-deps 模式);树选中承载机构上下文(无 badge);工具行左查询右新增 |
| 用户管理 | `src/pages/system/users/index.tsx` + `console-shell.tsx`(共享页头/工具行组件) | 标准表格页;**裸数组客户端分页**;操作列 = 重置密码(钥匙)/启用 Switch(**无 delete/edit——后端无此端点,按端点实情裁剪**) |

测试:`src/test/{orgs-page,deps-page,users-page,console-shell}.test.tsx` + `require-auth.test.tsx` 扩展。
测试数:91 → **127**(+36,基线零回归)。

## 2. 覆盖矩阵(brief 条目 × 测试)

**6.1 orgs**(9 测试):树加载+种子根 ✓ / 嵌套渲染 ✓ / 新建子机构+重查 ✓ / PUT 编辑回显 ✓ /
删除 Popconfirm+取消不发请求 ✓ / **多根创建(upId 0)双根渲染** ✓ / A 类错误表单内不关弹窗 ✓ /
无 `system:org:list` 时全部变更控件隐藏但保留读 ✓

**6.2 deps**(10 测试):自动选中种子根+`listDeps(orgId)` 只列该机构 ✓ / 切换机构重查 ✓ /
编码/名称查询过滤 ✓ / 新建 round-trip ✓ / PUT 编辑 ✓ / 删除 Popconfirm+重查 ✓ / 表头刷新钮 ✓ /
**A0712 落表单 code 字段、弹窗不关** ✓ / 读/删 A 类 `message.error` ✓ / perms 门控 ✓

**6.3 users**(11 测试):裸数组渲染(用户名/姓名/角色/Switch/创建时间)✓ / **客户端分页
(一次请求,翻页切片)** ✓ / 查询框前端过滤 ✓ / 表头刷新重查 ✓ / 新建 round-trip ✓ /
**重名 A0111 落表单、弹窗不关** ✓ / 重置密码 + `mustChangePassword` 回显 ✓ / 重置 A 类浮层 +
弹窗保持 ✓ / enable 切换 + 重查回显 ✓ / **越界 A0400 恢复服务器状态** ✓ / perms 门控
(隐藏新建/重置、Switch 禁用、保留读)✓

**审计结论:原 brief 全部验收条目均有对应测试,无缺口。**

## 3. 冒烟证据(dev-server 5179,API 级;测完端口释放)

> 前任死因之一是"mock 状态跨运行污染 + 临时脚本分页选择器写错"——其遗留仅存在于已被
> 丢弃的临时探针脚本;正式测试代码无此问题(分页测试通过)。本次冒烟按场景组重启 dev server。

- **6.1**:tree 返回种子根;POST 新根(upId 0)→ `data:2`;子机构 POST/PUT/DELETE 全 `200` ✓
- **6.2**:`?orgId=1` 返回三种子科室;新建 `data:4`;重复 code → **A0712 `department code
  already exists: radiology`**(逐字)✓
- **6.3**:list 裸数组;新建 `data:2`;重复用户名 → **A0111 `用户名已存在`** ✓;
  重置密码(`newPassword` 字段)→ `data:true` 且列表 `mustChangePassword` 翻 1 ✓;
  enable=0 → `data:true` 且列表回显 ✓
- 期间一次 `A0400 newPassword must not be blank` 为**冒烟脚本自身字段名笔误**(用了
  `password`),核对前端 client(`src/api/users.ts:23` 发 `newPassword`)与后端
  `ResetPasswordRequest.newPassword` 一致后排除——**非实现 bug**。
- 浏览器级走查留待 Batch 7.2(集成验证批的 mock 冒烟走查),本批以 API 级 + 127 单测覆盖。

## 4. 布局与规范对照

- 三页均无页头标题块(紧凑列表);工具行左查询右新增(PlusOutlined);操作列纯图标
  + tooltip + 表头刷新;分页行居中 ~60px;内容区 10px——全部取自已确认原型。
- 权限门控:`usePerm`/`hasPerm` 消费既有 perms 串(`system:org|dep|user:list`),未发明新串。
- 数据流:本地 hooks + `src/api/*` clients;无 URL 拼接、无 axios、**零 hex 违规**
  (hex 守卫常绿)、零新依赖。

## 5. 遗留问题

1. 浏览器级全 scenario 走查 → Batch 7.2 统一做(本批 API 级 + 单测已覆盖行为)。
2. 用户表无编辑/删除入口是**端点实情**(后端仅 create/list/password/enable)——若产品
   需要编辑/删除用户,属后端能力缺口,需另立后端变更(与 roles/list 同类)。
3. `console-shell.tsx`/`org-tree.tsx` 为三页共享组件,6b 的 roles/dicts/logs 页面
   预期复用(报告备案,供 6b 执行者读取)。
