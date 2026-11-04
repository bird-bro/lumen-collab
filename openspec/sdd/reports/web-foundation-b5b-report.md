# Batch 5b 报告 — web-foundation-auth-console(任务 5.2–5.4 + mock 菜单夹具重构)

> 执行者:Implementer Agent · 日期:2026-11-04
> 事实来源:用户已确认原型 `prototype/login.html`、`prototype/console-shell.html`(12 轮修订终版)
> + `specs/web-auth-flow/spec.md` + design.md D5/D6/D7。
> TDD 铁律全程执行:每个行为先红后绿,证据见下。**未 commit**(按 brief 要求)。

---

## 1. 结果总览

| 验证项 | 结果 |
|:--|:--|
| `pnpm --dir lumen-web test` | **91/91 全绿**(11 个文件;既有 61 零回归) |
| `pnpm --dir lumen-web build` | **退出 0**(tsc strict ×2 + vite build) |
| hex 纪律守卫(`hex-scan.test.ts`) | **绿**(src 下 theme/test 之外零 hex,注释也算——途中被它抓出 2 处注释违规并已改写) |
| `rg axios`(src/ + mock/) | **0 条** |
| mock 冒烟(端口 5179,`--strictPort`,测完 kill) | **浏览器级 33/33 通过**(Playwright + 系统 Chrome,见 §6) |
| 范围核对 `git status --porcelain` | 仅 `lumen-web/src/**`、`lumen-web/mock/_fixtures.ts`、`lumen-web/package.json`(+锁文件,图标库所致)+ 本报告;并行会话文件(lumen-sev/**、openspec/loop-state.yaml、progress.md、dict-city-reference/**)未触碰 |

测试数变化:61(基线)− 2(旧 layout.test 重写)+ 9(新 layout)+ 10(menu-paths)+ 4(login)+ 6(require-auth)+ 3(perm)= **91**。

---

## 2. 任务 0 — mock 菜单夹具重构(三级树)

改 `lumen-web/mock/_fixtures.ts`,其余 mock 文件经共享夹具自动跟随(`/me`、`/menus/tree` 用 `buildMenuTree`;`roles/list` 的 `menuIds` 用 `seedRoleMenus`)。

**新树结构**(grep 证据见下):

```
系统管理 (1, M, path=/system, icon=setting)
├─ 组织架构 (13, M, path=null, icon=bank)     ← 新增分组目录
│   ├─ 机构管理 (7,  C, path=org,  perms=system:org:list)
│   └─ 科室管理 (8,  C, path=dep,  perms=system:dep:list)
├─ 用户权限 (14, M, path=null, icon=safety)   ← 新增分组目录
│   ├─ 用户管理 (2,  C, path=user,  perms=system:user:list)
│   ├─ 角色管理 (9,  C, path=role,  perms=system:role:list)
│   └─ 菜单管理 (10, C, path=menu,  perms=system:menu:list)
└─ 系统配置 (15, M, path=null, icon=sliders)  ← 新增分组目录
    ├─ 字典管理 (11, C, path=dict,  perms=system:dict:list)
    └─ 日志查询 (12, C, path=log,   perms=system:log:list)
放射科 (3, C, /radiology) ── 报告审核 (6, F) 原位保留
超声科 (4, C, /ultrasound)、内镜科 (5, C) 单页直达不变
```

**决策与记录**:

- **分组 path 取 `null`**(非 `/system` 锚点):以「侧栏分组不导航」语义为准——目录节点(type M)在解析器(`src/routes/menu-paths.ts`)中永不解析为路由,只有叶子(type C)可导航;分组标题纯展示。
- **7 个页面节点保留原 id/path/perms/component**,只改 upId;组内 sort 重编为 1..n(顺序语义不变,报告备案)。
- **分组 icon**:bank(组织架构,原型房形)、safety(用户权限,原型盾形)、sliders(系统配置,原型三滑杆)——AntD 图标名,映射表在 BasicLayout。
- **「操作日志」vs「日志查询」差异**:brief 任务 0 文案写「操作日志」,但已确认原型侧栏与页面标题均为「日志查询」(`console-shell.html:378`、`dicts-logs.html:17`),且 brief 要求「保留原页面节点」,故**保留「日志查询」**。
- `seedRoleMenus`:`{ 1: [1..15] }`(role_menu 初始授权含三个新分组)。
- `permsOf` 自动跟随,登录/me 的 perms 集合不变。

**grep 验证**(夹具中分组名与树结构):

```
$ grep -n "upId: 1[345]|title: '组织架构'|..." mock/_fixtures.ts
  218: { id: 13, upId: 1, type: 'M', title: '组织架构', ... icon: 'bank', sort: 1 }
  219: { id: 7,  upId: 13, ... 机构管理 ...
  220: { id: 8,  upId: 13, ... 科室管理 ...
  221: { id: 14, upId: 1, type: 'M', title: '用户权限', ... icon: 'safety', sort: 2 }
  222-223: 用户管理(upId 14)/ 角色管理(upId 14)/ 菜单管理(upId 14)
  224: { id: 15, upId: 1, type: 'M', title: '系统配置', ... icon: 'sliders', sort: 3 }
  225-226: 字典管理(upId 15)/ 日志查询(upId 15)
```

dev-server 实测(login 响应的树):`[1]系统管理 → [13]组织架构 → [7]机构管理/[8]科室管理;[14]用户权限 → [2][9][10];[15]系统配置 → [11][12];[3]放射科→[6]报告审核(F);[4]超声科;[5]内镜科` —— 与本节一致。

---

## 3. RED→GREEN 证据

每个测试文件先跑 RED(实现缺失/旧行为)再 GREEN。

| 任务 | 测试文件 | RED 证据 | GREEN |
|:--|:--|:--|:--|
| 5.3 前置:菜单→路由解析 | `src/test/menu-paths.test.ts` | `1 failed (Test Files), no tests`(模块 `@/routes/menu-paths` 不存在,transform 失败) | **10/10** |
| 5.2 登录页 | `src/test/login-page.test.tsx` | `1 failed, no tests`(`@/pages/login` 不存在) | **4/4**(首次运行还暴露 jsdom 缺 `matchMedia`,在 `setup.ts` 补标准 stub 后全绿) |
| 5.3 守卫+路由 | `src/test/require-auth.test.tsx` | `6 failed (6)`(`appRoutes` 不存在) | **6/6**(中途修一个真实 bug:`Result` 误从 react-router-dom 导入) |
| 5.4 权限门控 | `src/test/perm.test.tsx` | 与 layout 同批 `2 failed (files), 8 failed` | **3/3** |
| 5.4 三区壳层 | `src/test/layout.test.tsx`(重写) | 同上,9 个用例中 8 失败 | **9/9** |

中途被测试抓出并修复的真实问题(非测试问题):

1. `FoldOutlined`/`ExpandOutlined` 在 @ant-design/icons 5.6.1 **不存在**(运行时 undefined → React "Element type is invalid"),换成 `MenuFoldOutlined`/`MenuUnfoldOutlined`(语义与 OPS 折叠一致)。
2. CSS 变量只注入了 4 个、CSS 文件却消费 17 个(头像渐变、悬停底色等全部失效)——冒烟截图目检发现,补齐 13 个 var 注入(全部取自 token 管线)。
3. hex 守卫扫注释:两处文档注释含 `#xxxxxx` 字面量被守卫拦下,改写为不含 hex 的表述(守卫的严格性是设计行为)。

---

## 4. 任务 5.2 — 登录页(`src/pages/login/index.tsx`)

按 `prototype/login.html` 实现:深蓝舞台(径向光斑 + 网格 + 底部压暗)、居中 400px 白卡、品牌块(44px 渐变 mark + PlusOutlined + LumenCollab/影像协作平台)、AntD Form(username+password,size large 前缀图标)、右侧 Enter 提示(kbd 样式)、字距 8px 的「登 录」主按钮。

行为(TDD 覆盖):

1. 成功:`authApi.login()` → `setSession({token,profile,menus,perms})` → `navigate(firstGrantedRoute(menus), {replace:true})`(admin 夹具落在 `/system/orgs`,与 D6 一致);断言了请求 URL/方法/JSON 体。
2. 失败:`ApiError.msg` 经 `message.error`(顶部浮层,A 类文案原文「用户密码错误」),**停留登录页、session 不落库**(断言 store token 与 localStorage 持久层均为 null)。
3. Enter:密码框内 `{enter}` 触发提交(jsdom 隐式表单提交),与按钮等价。
4. loading:请求悬置时按钮文案切「登录中」+ spinner(用未 resolve 的 fetch Promise 驱动)。
5. 原型无隐私勾选/无忘记密码 —— 按原型省略(D7)。
6. 种子账号提示与页脚仅 `import.meta.env.DEV` 渲染(生产构建不出现明文种子口令,报告备案)。

路由注册:`/login` 在 `src/router.tsx` 中独立于 shell 父路由(测试断言登录页无 `.ant-layout-header/.ant-layout-sider`)。

## 5. 任务 5.3 — 守卫 + boot 刷新 + 路由重构

- **`src/components/RequireAuth.tsx`**:只读 store token;无 token → `<Navigate to="/login" replace />`。**零请求**由两层测试证明:单元(require-auth #1,fetch 计数 0)与浏览器冒烟(#1/#7,network 面板 0 条 `/api/v1/*`)。
- **`src/components/SessionBoot.tsx`**(boot 刷新,渲染 null):shell 挂载且有 token 时调 `me()` → `setSession({profile,menus,perms})` 部分更新(token 保留);`code 203` → client 的 `onSessionExpired`(4.1/4.2 已接线)清空 store → 守卫重定向。集成用例:stub `/api/v1/system/me` 返回 203 → **落在登录页且 store 已清**(require-auth #2)。非 203 错误保留缓存会话(注释备案,离线容忍)。
- **`src/router.tsx` 重构**:导出 `appRoutes`(供测试以 MemoryRouter+useRoutes 隔离驱动)与 `router`(createBrowserRouter,App.tsx 消费)。
  - `/login` 独立;
  - `/` 父路由 = `RequireAuth > SessionBoot > BasicLayout`,index 子路由 = `IndexRedirect`(读 store menus → `firstGrantedRoute` → `<Navigate replace>`;**无可导航菜单时渲染显式「No granted menu」Result**,不重定向不空屏,备案);
  - `/system/**` 七个懒加载占位路由(orgs/deps/users/roles/menus/dicts/logs,复数,与 D6 一致),每个渲染显式「is not yet implemented」Result、零请求(测试 + 冒烟双重证明),batch 6 原位替换 `src/pages/system/<resource>/index.tsx`;
  - 科室三路由懒加载原样。
- **解析函数位置:`src/routes/menu-paths.ts`**(brief 给出的首选位置):`resolveMenuRoute`(目录/按钮不可导航;console 页按 component 列映射到复数路由;科室绝对路径直通)、`firstGrantedRoute`(深度优先第一叶子)、`firstLeafRouteOf`(导轨 L1 点击目标)、`activeTopLevelNode`(当前激活 L1)。10 个单测覆盖。

## 6. mock 冒烟(端口 5179)证据

dev server:`pnpm dev --port 5179 --strictPort`(测完 kill)。两层证据:

**(a) HTTP 层(curl)**:login 200 → 树结构 = §2 所列;`/me` 200(top=系统管理+三科室);`/menus/tree` 200(系统管理 children=[组织架构,用户权限,系统配置]);`roles/list` → `menuIds: [1..15]`;无 Authorization 的 `/me` → `{"code":"203","msg":"请求访问:/api/v1/system/me,认证失败,无法访问系统资源"}`(与 AuthenticationEntryPointImpl 文案逐字一致)。

**(b) 浏览器层(Playwright,系统 Chrome,33/33)**——脚本 `/tmp/b5b-smoke.cjs`,截图 `/tmp/b5b-01-login.png`、`b5b-02-shell.png`、`b5b-03-folded.png`:

| 场景 | 结果 |
|:--|:--|
| 1 未登录访问 `/` → 落 `/login`,**0 条 /api/v1 请求** | PASS |
| 2 admin 登录 → 落 `/system/orgs`;导轨 68px;头部 60px;导轨四 L1(来自 /me 树);产品标题;名/机构双行 | PASS |
| 3 侧栏 160px;三分组标题;七个平铺项;选中=机构管理;选中底色 `rgb(240,248,255)`(colorPrimaryBg);**3px 左条**(inset shadow `rgb(49,120,252) 3px 0px`,色值来自注入的 `--lumen-menu-active-bar`) | PASS |
| 4 点 Fold → 40px 竖条 + 竖排「系统管理」;点 Expand → 回 160px | PASS |
| 5 点「角色管理」→ `/system/roles`,选中态跟随;点「日志查询」→ `/system/logs` | PASS |
| 6 头部用户区 → Popover → 「退出登录」→ 回 `/login`,localStorage `lumen-auth` token=null | PASS |
| 7 删除 `lumen-auth` → reload → 落 `/login`,**0 条 /api/v1 请求** | PASS |

**203 浏览器场景的如实说明**:brief 要求「手动删 localStorage token 后带假 token 刷新 → 回登录页」。实测:带**假 Bearer token** 刷新时,mock 的 `/me` 会兜底回种子 admin 返回 **200**(`mock/auth.mock.ts:92` 的 mock-only 便利兜底:未注册 token → uid 1),因此「假 token → 203」在本 mock 上**不可能出现**;mock 的 203 只在**无 Authorization 头**时发生,而该路径被零请求守卫提前拦截(冒烟 #1/#7)。故 203 → 清会话 → 回登录链路的证据为:① 单元级(require-auth #2:me() 返回 203 → store 清空 → 登录页);② 存量 4.1/4.2 client/store 单测;③ curl 层 203 报文。若确需浏览器级 203,需给 mock 加「假 token 拒绝」开关——超出本批夹具授权范围,未做,备案。

---

## 7. 任务 5.4 — 三区壳层:原型↔实现视觉对照表

数据源:`prototype/console-shell.html`(rev.11/12)+ `prototype/login.html`。色彩全部经 token 管线(tokens.ts → 组件 token / `theme.getDesignToken` / `useToken`),hex 仅存在于 `src/theme/`。

| 区域 | 原型(确认版) | 实现 | 一致? |
|:--|:--|:--|:--|
| 导轨宽 | 68px(`--mainnav-width`) | `RAIL_WIDTH=68`,Sider inline width,**冒烟实测 68** | ✅ |
| 导轨底色 | `#0B2540` | tokens.ts `Layout.siderBg`(组件 token 自动应用) | ✅(值源自 tokens.ts,未改动) |
| 导轨 hover | `#12355C` | tokens.ts `Menu.darkItemHoverBg`,经 `--lumen-rail-hover` 注入(layout 测试断言 `#12355C`) | ✅ |
| 导轨选中 | `#3178fc` | tokens.ts `Menu.darkItemSelectedBg`,经 `--lumen-rail-active` 注入(测试断言) | ✅ |
| logo 块 | 与 60px 头部同高,34px 渐变 mark | 高 60,`linear-gradient(135deg, darkItemSelectedBg, siderBg)`,PlusOutlined 18px 白 | ✅ |
| 导轨项 | 26px 白图标 + ~16px 白短标签,垂直排 | 26px 图标 + 16px 标签(css),白色=`colorTextLightSolid` | ✅ |
| 导轨数据 | L1 项 | **store 菜单树顶层**(测试断言四项来自 menus,且无旧 NAV_ITEMS 的 "Radiology" 英文项) | ✅ |
| 头部高 | 60px 白,1px 下边框 | `HEADER_HEIGHT=60` + `colorBorder` 下边框(测试断言 60px) | ✅ |
| 头部标题 | 26px/600「LumenCollab 影像协作平台」 | `.lumen-header-title` 26px/600 | ✅ |
| 环境章 | 24px 高、primaryBg 底、primary 字 | `.lumen-header-env`,DEV 显示「开发环境」 | ✅ |
| 用户区 | 双行(名 16px/500、机构 12px)在左 + **50px 圆头像**在右,**无 caret** | `.lumen-header-user`/`.lumen-header-avatar`(inline 50×50),无 caret | ✅ |
| 头像字 | 「管」(静态演示值) | 取 `profile.name` 首字「系」(通用实现,测试断言 + 备案差异) | ⚠️ 有意偏差 |
| Popover | 基本信息双行 + plain 40px「退出登录」按钮,悬停 primary | Popover(trigger click, bottomRight)+ `Button block size=large`,点击 → `store.clear()` + navigate /login(测试+冒烟) | ✅ |
| org 值 | 「示例医院」 | 常量 `SEED_ORG_LABEL='示例医院'`(profile 无 org 字段,代码注释 + 本表备案;待 /me 增加 org 后替换) | ✅(顶位) |
| 侧栏宽 | 160px 白,可折叠 40px | `SIDER_WIDTH=160` / `SIDER_COLLAPSED_WIDTH=40`(测试+冒烟双态实测) | ✅ |
| 侧栏内容 | **激活 L1 的 children**:L2=分组标题(16px 字+20px 图标),L3=纯文字平铺行,组间 1px 分隔线 | AntD `Menu mode="inline"` + `type:'group'`(分组标题内嵌 20px 图标 span)+ 叶子纯文字项 + 组间 `{type:'divider'}`;数据=activeTopLevelNode 的 children | ✅ |
| 行规格 | 40px 行高、左右 8px 边距、圆角 6 | Menu 组件 token `itemHeight:40 / itemMarginInline:8 / itemMarginBlock:2 / itemBorderRadius:6` | ✅ |
| 侧栏文字 | `#6f6f6f`(OPS `--menu-text-color`) | `colorTextSecondary #666`(**#6f6f6f 不在已同步 tokens.ts 中,按 hex 纪律取最近 token 档位**) | ⚠️ 记录偏差 |
| 选中态 | `#EBF2FF` 底 + `#3178fc` **左**条 + 500 字重 | 底 = 派生 `colorPrimaryBg`(实测 `rgb(240,248,255)`;AntD 对 #3178fc 的自动浅 tint,非手写 EBF2FF);**左条** = AntD 内联选中条在 inline-END,故 `activeBarWidth:0` 关掉后用 token 注入的 CSS 变量覆盖 `box-shadow: inset 3px 0 0`(冒烟实测 inset 3px `rgb(49,120,252)`) | ⚠️ 底色记录偏差;左条 ✅ |
| hover | `#f0f3fa` 底 | tokens.ts `Table.headerBg`(组件 token 直读)注入 `itemHoverBg` | ✅ |
| 分组标题色 | `#999` / 500 字重 | `colorTextTertiary`(#999)+ groupTitleFontSize 16 | ✅ |
| 折叠钮 | 24px Fold 图标,绝对定位右下(right:4px/bottom:0,padding 5px) | `.lumen-side-fold`(24px MenuFoldOutlined,同位) | ✅ |
| 收起态 | 40px 竖条:Expand 图标 + 竖排文字 18px/600(激活 L1 名) | `.lumen-side-expand`(MenuUnfoldOutlined 24 + 18px/600 竖排「系统管理」,冒烟实测) | ✅ |
| 内容区 | padding 10px(rev.8 用户设定) | `CONTENT_PADDING=10`(测试断言 inline padding 10px) | ✅ |
| 登录页舞台 | 深蓝渐变(旧值 `#0f356a→#003676`)+ primary 系径向光斑 + 网格 + 底部压暗 | 基色 = **已同步 tokens** `Layout.siderBg`(#0B2540)+ 径向光斑改用**运行时 token 主色加 alpha**(8 位 hex 由 `token.colorPrimary` 拼接,无字面量)+ 黑色中性压暗 + 96px 网格(带径向 mask)+ 两个加号饰形 | ⚠️ 原型内嵌旧 token 值,实现跟随 rev.12 tokens(备案) |
| 登录卡片 | 400px、圆角 8、padding 42/38/34、深投影 | 同值(投影 `rgba(2,18,46,.42)` 为原型阴影色,非 hex 形式) | ✅ |
| 登录页加载时演示 toast | 静态演示(position 示意) | 不实现——产品行为是失败时 `message.error`(位置相同:顶部居中) | ⚠️ 演示性元素,备案 |

**NAV_ITEMS 常量已删除**(`layout.test.tsx` 重写后断言旧英文导航不存在);色彩零内联 hex(hex 守卫常绿)。

## 8. 图标选型决策

**选择:新增 `@ant-design/icons` 精确锁 `5.6.1`,置于 dependencies。**

- 版本自查:antd 5.29.3 自身依赖 `@ant-design/icons: ^5.6.1` → 锁内已解析 5.6.1(同一份,无重复拷贝);其 peer 为 `react >=16.0.0 / react-dom >=16.0.0`,**兼容 React 18.3.1**。6.x 虽存在(6.3.4)但属 antd 6 世代,不采用。
- **放 dependencies 而非 devDependencies 的理由**(偏离 brief 字面「dev 精确锁」,备案):图标被 `src/**` 生产源码导入,且 pnpm 严格 node_modules 不提升传递依赖——不放 dependencies 的生产安装会解析失败。
- 原型图标→AntD 映射:系统管理=setting;分组=bank/safety/sliders;科室(icon=null)=fallback `RadarChartOutlined`(原型科室为圆形射线系图形,取最接近者,备案);折叠=MenuFoldOutlined/MenuUnfoldOutlined(FoldOutlined/ExpandOutlined **在 5.6.1 不存在**,运行时 undefined 被测试抓出后替换);登录=UserOutlined/LockOutlined;logo=PlusOutlined。
- CSS 文件 `src/layouts/basic-layout.css` 零色值:全部 17 个颜色经 `--lumen-*` CSS 变量由组件从 token 管线注入。

## 9. 文件清单

新增:`src/pages/login/index.tsx`、`src/components/RequireAuth.tsx`、`src/components/SessionBoot.tsx`、`src/routes/menu-paths.ts`、`src/pages/system/console-placeholder.tsx` + 7 个占位页(`orgs/deps/users/roles/menus/dicts/logs/index.tsx`)、`src/utils/perm.ts`、`src/layouts/basic-layout.css`、4 个测试文件(menu-paths/login-page/require-auth/perm)。
修改:`mock/_fixtures.ts`、`src/router.tsx`、`src/layouts/BasicLayout.tsx`、`src/components/index.ts`、`src/test/layout.test.tsx`(重写)、`src/test/setup.ts`(matchMedia stub)、`package.json`+`pnpm-lock.yaml`(图标库)。
解析函数位置(按 brief 要求备案):**`src/routes/menu-paths.ts`**。

## 10. 遗留问题 / 建议

1. **backend follow-up(原型已标注)**:console-shell.html 注释要求后端 V2 seed 同步三级菜单(分组 13/14/15)——属后端会话,未动。
2. mock `/me` 的假 token 兜底(`auth.mock.ts:92`)使浏览器级 203 演练不可构造(见 §6 说明);可选后续:mock 加开关。
3. `SessionBoot` 对非 203 错误静默保留缓存会话(注释备案);batch 6 页面实现时可按需补全局错误提示。
4. 主 bundle 716KB(chunk 警告)为既有状态(antd 整包),非本批引入;manualChunks 优化留待后续。
5. 用户区 org 值目前为种子医院常量,待 `/me` profile 增加 org 字段后替换(代码注释已标注)。
