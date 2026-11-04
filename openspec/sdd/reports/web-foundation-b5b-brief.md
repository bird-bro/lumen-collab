# Batch 5b Brief — web-foundation-auth-console（tasks 5.2–5.4 + mock 菜单夹具重构前置）

> 事实来源:用户已确认的原型 `openspec/changes/web-foundation-auth-console/prototype/*.html`
> (12 轮修订后的终版,**视觉值以原型为准**)+ `specs/web-auth-flow/spec.md`(权威 WHEN/THEN)
> + design.md D5/D6。工作目录 REPO。**不要 commit**。TDD 铁律:先红后绿,证据入报告。
> 只动:`lumen-web/src/**`(api/stores/layouts/pages/router/test)、`lumen-web/mock/**`(仅菜单夹具)、
> `lumen-web/package.json`(仅当需要 @ant-design/icons)、本报告。不碰 openspec/ 其他文件。

## 任务 0(前置)— mock 菜单夹具重构为三级

用户已确认的菜单结构(原型 console-shell 侧栏):系统管理(L1)下三个二级分组,分组下为页面:
- **组织架构**:机构管理、科室管理
- **用户权限**:用户管理、角色管理、菜单管理
- **系统配置**:字典管理、操作日志
- 科室类一级(放射科/超声科/内镜科)保持单页直达;「报告审核」按钮节点原位保留。

改 `mock/_fixtures.ts` 的菜单树:新增 3 个分组目录节点(id 13/14/15,type 目录,带 icon、
path 取分组锚点如 `/system` 或留空——以「侧栏分组不导航」语义为准,报告记录选择),现有
7 个页面页节点**保留原 id/path/perms**,改挂到对应分组下;`role_menu` 初始授权、`/me`、
`/menus/tree`、`roles/list` 的 `menuIds` 全部与新树一致;科室一级 + 各自子页结构不变。
`grep` 验证:夹具中分组名与树结构与本节一致。

## 任务 5.2 — TDD:登录页 `src/pages/login/index.tsx`

按原型 `login.html`(居中卡片、产品名 LumenCollab + 影像协作平台、无隐私勾选/无忘记密码):
1. AntD `Form`:username + password;Enter 与按钮均提交;提交中按钮 loading。
2. 提交 → `authApi.login()` → 成功:`useAuthStore.setSession({token,profile,menus,perms})`
   → navigate 到首个有权菜单路由(5.3 的解析函数);失败:`ApiError` 的 `msg` 用
   `message.error` 顶部浮层展示(A 类文案,如「用户密码错误」),停留在登录页。
3. 路由:`/login` 注册在 shell **之外**。
4. 测试(RED→GREEN):成功登录进 shell;错误密码显示 msg 且不留存会话;Enter 触发提交。

## 任务 5.3 — TDD:`RequireAuth` 守卫 + 路由重构

1. `src/components/RequireAuth.tsx`:读 `useAuthStore`——无 token → `<Navigate to="/login" />`
   且**不发任何请求**;有 token → 渲染 children。
2. **boot 刷新**:shell 挂载时若有 token 则调 `me()` 并 `setSession` 部分更新
   (profile/menus/perms);`ApiError code==='203'` → store 已被 client 的
   `onSessionExpired` 清空 → 守卫重定向登录页(203 处理链路已被 4.1/4.2 测试覆盖,此处
   集成验证一条:me() 返回 203 → 落在登录页)。
3. `src/router.tsx`:`/login`(独立)+ shell 父路由(RequireAuth 包裹)+ `/system/**`
   七个**懒加载占位路由**(orgs/deps/users/roles/menus/dicts/logs——本批渲染显式
   「未实现」占位状态、不发请求,符合 web-app-shell § Placeholder Routes;batch 6 替换)+
   科室三路由原样;`/` 重定向到**首个有权菜单路由**(解析函数放
   `src/routes/menu-paths.ts` 或 stores,报告记录位置:取菜单树第一个叶子 path)。
4. 测试:未认证访问受保护路由 → 登录页且零请求;203 → 登录页;`/` → 首个有权路由。

## 任务 5.4 — TDD:三区壳层 + 动态导航 + 权限门控 + 退出

按**已确认原型 console-shell.html** 重构 `BasicLayout`(视觉值逐项取自原型,报告给对照表):
1. **导轨(68px)**:bg `#0B2540`(tokens siderBg)、hover `#12355C`、选中 `#3178fc`
   (跟随主操作);logo 块与 60px 头部同高;L1 项 = **26px 白色图标 + ~16px 白色短标签**
   (垂直排,数据来自 store 菜单树顶层;点击切换激活 L1,并导航到该 L1 首个叶子)。
2. **头部(60px,白)**:左 = 产品标题「LumenCollab 影像协作平台」(600 字重);右 = 用户区
   ——**双行文字(名/机构)在左 + 50px 圆头像块在右,无 caret**;点击弹 `Popover`
   (基本信息双行 + plain 大按钮「退出登录」);退出 = `store.clear()` + navigate `/login`。
   org 值:profile 暂无该字段,先取菜单树/种子医院常量并在代码注释标注(报告记录)。
3. **二级侧栏(160px 白,可折叠 40px)**:渲染**激活 L1 的 children**——L2 目录 =
   分组标题(16px 字 + 20px 图标 + 组间分隔线),L3/叶 = 平铺**纯文字行**(18px/40px,
   无 icon);选中 = `#EBF2FF` 底 + `#3178fc` 左条;文字 `#6f6f6f`;点击叶子导航。
   折叠控制 = **24px Fold 图标绝对定位侧栏右下角**(right:4px/bottom:0);收起态 =
   40px 竖条(Expand 图标 + 竖排文字 18px/600)。优先用 AntD `Menu` mode="inline" +
   `type:'group'` 项 + Menu 组件 token(light 系:itemColor 等)实现;色彩经组件 token
   或既有 tokens,不写内联 hex(hex 纪律守卫常绿)。
4. **内容区**:padding 10px;Suspense/Outlet 原样。
5. **权限门控助手**:`src/utils/perm.ts`(或 stores 内)导出 `usePerm()` /
   `hasPerm(code)`:perms 集合判断;本批至少一条测试(无 perm 的控件不渲染);
   batch 6 页面消费。
6. `NAV_ITEMS` 常量删除;`layout.test.tsx` 重写为三区断言(导轨 68/头部 60/侧栏 160/
   内容 padding 10px/分组标题与平铺项结构/无 token 重定向)。
7. 图标:优先新增 dev 精确锁 `@ant-design/icons`(版本自查 peer 兼容 React 18.3,报告记录
   选型);若你判断内联 SVG 更贴原型,可不走新依赖,报告记录理由。

## 验证(留证)
1. `pnpm --dir lumen-web test` 全绿(既有 61 零回归 + 新测试,报总数)。
2. `pnpm --dir lumen-web build` 退出 0;hex 守卫绿;`rg axios` = 0。
3. mock 冒烟(dev server,`--port 5179 --strictPort`,测完 kill):admin 登录 → 三区壳
   → 侧栏三分组/平铺项/选中态 → 折叠/展开两态 → 点叶子导航 → 退出回登录页;`/me` 203
   场景(手动删 localStorage token 后带假 token 刷新)→ 回登录页。
4. 范围核对:`git status --porcelain` = src/** + mock/** + package.json(仅图标库时)+ 报告。

## 完成标准
全部验证过;报告写 `openspec/sdd/reports/web-foundation-b5b-report.md`(中文:RED→GREEN、
原型↔实现视觉对照表、菜单夹具重构说明、图标选型、mock 冒烟证据、问题)。失败即停、如实记录。
