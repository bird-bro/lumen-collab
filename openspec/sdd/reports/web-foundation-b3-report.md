# Batch 3 报告 — web-foundation-auth-console（task 3.1：dev mock 服务器 + 种子对齐夹具）

> 状态：**DONE_WITH_CONCERNS**（全部验证通过；「问题」一节如实记录 3 项偏差/取舍，
> 其中问题 1 是 brief 内部矛盾，按"并集文档门"解决并全程留证，请复核）。
> 本批只动：`lumen-web/vite.config.ts`（+2/-1）、`lumen-web/mock/**`（新建 9 文件）、本报告。
> 未 commit；dev server 已 kill，端口 5177 已确认释放。

## 1. 接线与插件约定（vite-plugin-mock-dev-server 2.7.1，以本地包为准）

`lumen-web/vite.config.ts`：`import { mockDevServerPlugin } from 'vite-plugin-mock-dev-server'`，
加入 `plugins: [react(), mockDevServerPlugin()]`（`react()` 之后）。`server.proxy`、
`build.rollupOptions`、`test` 块逐字未动（diff +2/-1）。

读本地 README 与 dist 后确认的 **2.x 真实约定**（报告记录，供后续批次复用）：

- **默认目录与文件命名**：`dir = "mock"`，`include = ["**/*.mock.{js,ts,cjs,mjs,json,json5}"]`
  （dist/index.js `resolvePluginOptions` 缺省值）。只有 `*.mock.ts` 被自动加载——
  `mock/_fixtures.ts` 是纯助手文件，不会被当作 mock 项，可安全被 import。
- **导出形状**：`defineMock(item | item[])`（一文件可导多个端点）；**无 default 导出**——
  README 首页的 `import mockDevServerPlugin from '...'` 写法在本版**跑不通**
  （实测 `TypeError: ... is not a function`），必须用具名导出 `mockDevServerPlugin`
  （dist/index.js 导出清单佐证）。
- **URL 匹配**：`path-to-regexp@8.3.0`，只匹配 path 不含 query；路径参数写作 `:id`
  （`request.params.id` 取值）；query 由 `request.query` 解析（自动 JSON 化）。
- **method 缺省 `['POST','GET']`**——PUT/DELETE 必须显式声明，否则不匹配。
- **响应**：`body` 可为函数 `(request) => data`（含 `headers`/`query`/`body`/`params`）；
  缺省 `Content-Type: application/json`、`status: 200`；更复杂场景用 `response` 中间件
  （本批未用到，全部 `body` 函数覆盖）。
- **打包方式（对状态管理是关键事实）**：每个 mock 文件经 esbuild `bundle: true`
  **独立打包**（相对 import 被内联进各自临时文件）→ **模块级可变状态跨文件不共享**。
  因此跨文件会话状态放在 `globalThis` 桶（`_fixtures.ts` 的 `state()`）——仍是纯
  dev 会话内存、不持久，符合 brief「模块级内存即可」的精神（细节见问题 3）。

## 2. 种子对齐表（V2__seed.sql / V3__system_data.sql → mock 夹具）

来源：`lumen-sev/lumen-system/src/main/resources/db/migration/V2__seed.sql`、`V3__system_data.sql`；
落点：`lumen-web/mock/_fixtures.ts`（种子常量）。逐项对照：

| 项 | 种子真值 | mock 值 | 判定 |
|:--|:--|:--|:--|
| 管理员凭据 | `admin` / `admin123`（BCrypt 散列入库） | login 命中判定 `admin`/`admin123`（mock 内存存 raw 比较值，`mock-only`） | 一致 |
| 管理员 uid/name | uid `1`、`系统管理员` | profile.uid `1`、name `系统管理员`；users/list 同 | 一致（JSON number，非字符串） |
| must_change_password | `1`（首登强制改密） | profile.mustChangePassword `1`；users/list 同 | 一致 |
| 医院根节点 | org_info `(1, upId 0, level 1, type 0, '示例医院')` | /orgs/tree 根节点同 id/upId/level/type/name | 一致 |
| 三科室 | org_dep `(1,'radiology','放射科') (2,'us','超声科') (3,'endo','内镜科')`，orgId=1 | /org-deps/list 三行 id/code/name/orgId 逐字一致 | 一致（**code 保持 `us`/`endo` 种子值**） |
| 角色 | role_info `(1,'系统管理员',10,'seed default administrator role')` | roles/list 记录 id/name/type/remark 一致 | 一致 |
| user_role | `(1, 1, 1)` | profile.roles `['系统管理员']`；userRoles 会话态 | 一致 |
| 字典目录 | dict_catal `(1,'common_status','通用状态',enable 1, sort 0)` | /dicts/catal/list 同 | 一致 |
| 字典明细 | detail `(1, ca 1, 'common_status', code 1, '1','启用', sort 1)` `(2, code 0, '0','停用', sort 2)` | /dicts/detail/list?caCode= 两行同，sort 升序 | 一致 |
| 菜单 V2 六行 | id 1-6（系统管理/用户管理/放射科/超声科/内镜科/报告审核按钮，perms 逐字） | mock 菜单 id 1-6 保留（perms/path/component 逐字；**科室占位 path 按 brief 改 `/ultrasound` `/endoscopy`**，见问题 2） | 一致（含已记录偏差） |
| role_menu | role 1 拥有全部六菜单 | role 1 初始 grant = 全部 12 个 mock 菜单（6 种子 + 6 新增） | 扩展（mock-only） |
| 日志 | V3 不种子 log_info | 3 行 mock-only 夹具（不同 type/dateYm） | mock-only（brief 明示） |

**mock-only 字段全清单**（种子/后端无真值）：`token`（`mock-jwt-<random>`——JWT 形状种子不可知）、
固定审计时间戳 `2026-11-04 09:00:00`（种子落 CURRENT_TIMESTAMP 默认）、菜单 id 7-12
（brief 七项控制台的六个新增节点）、log 夹具三行、roles/list 记录的 `menuIds` 回显字段
（缺口端点无实现可对照；前端全量替换对话框「重开可见」场景需要，见问题 3）。

## 3. 未文档化路径门（url → 文档对照表）

**如实记录：brief 的字面门（仅 grep 本变更 api delta）与本 brief 自身的资源清单冲突**——
brief 资源 1/7/8 强制 mock auth/dicts/logs，但 `/login`、`/me`、`/dicts/**`、`/logs` 不在本变更
api delta 里（它们分别由已合并能力规格 `openspec/specs/api/spec.md`（rename-auth-to-system 同步）
与 `openspec/specs/system-data/spec.md` 文档化）。因 brief 同时禁止改 openspec、又强制这些
mock，无法两全；按「凡在 openspec 任何规格文档化即不算未文档化路径」取并集门执行，
**24 个 mock url 全部命中，无一删除项**，逐条 grep 计数如下（`-F` 字面）：

| mock url | api delta（brief 字面门） | 合并/平行规格（实际文档处） |
|:--|:--|:--|
| `/api/v1/system/login` | 0 | `specs/api/spec.md`:2 · web-auth-flow delta:1 |
| `/api/v1/system/me` | 0（delta 仅 `/me` 短语） | `specs/api/spec.md`:2 |
| `/api/v1/system/menus/tree` | 2 | web-system-console delta:1 |
| `/api/v1/system/orgs/tree` | 3 | web-system-console delta:1 |
| `/api/v1/system/orgs` | 5 | web-system-console delta:1 |
| `/api/v1/system/orgs/{id}` | 1（PUT/DELETE） | — |
| `/api/v1/system/org-deps/list` | 2 | web-system-console delta:1 |
| `/api/v1/system/org-deps` | 4 | web-system-console delta:1 |
| `/api/v1/system/org-deps/{id}` | 1 | — |
| `/api/v1/system/users/list` | 2 | web-system-console delta:1 |
| `/api/v1/system/users` | 6 | web-system-console delta:3 |
| `/api/v1/system/users/{id}/password` | 1 | web-system-console delta:1 |
| `/api/v1/system/users/{id}/enable` | 1 | web-system-console delta:1 |
| `/api/v1/system/roles/list` | 2 | web-system-console delta:1 |
| `/api/v1/system/roles` | 4 | web-system-console delta:4 |
| `/api/v1/system/roles/{id}` | 1 | web-system-console delta:2 |
| `/api/v1/system/roles/{id}/menus` | 1 | web-system-console delta:1 |
| `/api/v1/system/dicts/catal/list` | 0 | system-data spec `…/dicts/catal/**`:1 · webconsole:1 |
| `/api/v1/system/dicts/catal` | 0 | 同上 globstar 覆盖（含 POST） |
| `/api/v1/system/dicts/catal/{id}` | 0 | 同上 globstar 覆盖（含 PUT/DELETE） |
| `/api/v1/system/dicts/detail/list` | 0 | system-data spec `…/dicts/detail/**`:1 · webconsole:1 |
| `/api/v1/system/dicts/detail` | 0 | 同上 globstar 覆盖 |
| `/api/v1/system/dicts/detail/{id}` | 0 | 同上 globstar 覆盖 |
| `/api/v1/system/logs` | 0 | system-data spec:1 · webconsole:1 |

（mock 文件中另有两处 `url:` 字符串 `/api/v1/system/roles/2/menus`、`/api/v1/system/users/2/password`
是 **log 夹具行的数据字段**（LogInfoEntity.url 列值，镜像真实控制器路径），不是 mock 端点。）

## 4. 后端形状镜像（读控制器/服务后的对齐记录）

- **信封**：`{timestamp, code, msg, data}`；成功有体 `code:"200"` `msg:"OK"`
  （GlobalResponseAdvice→R.ofSuccess）；失败 `data:null`。timestamp 逐字复刻
  R.java 的 `SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")`——**含 12 小时制 `hh` 怪癖**。
- **203**：AuthenticationEntryPointImpl 逐字——HTTP 200，`code:"203"`，
  `msg:"请求访问：<uri>，认证失败，无法访问系统资源"`。**除 POST /login 外全部 24 个端点
  均带 token 门**（镜像 `anyRequest().authenticated()`；任意 Bearer 放行为 mock-only 简化）。
- **mutation 返回**：POST 一律新 id（Integer），PUT/DELETE 一律 `data:true`
  （控制器返回 Boolean.TRUE 的 "200" 契约）。
- **分页形状**：IPage JSON `records/total/size/current/pages`（dicts catal 与 logs 的真实
  返回即 MyBatis-Plus Page；真实 JSON 另有 searchCount 等布尔簿记字段，mock 省略——已记录简化）。
  分页参数名按各自控制器：dicts catal 用 `pageNum/pageSize`（CatalQuery←PageSizeConfig），
  logs 用 `page/pageSize`（LogController RequestParam）。
- **users/list 是裸数组**（`List<UserItemDto>`，无分页包装）——api delta 场景写 "paged user
  payload" 与实现面不符，按 brief「以后端分页返回形状为准」镜像裸数组（问题 1 同源，见下）。
- **错误码逐字**（ExceptionEnum/BusinessException 文案组合 `枚举msg：detail`）：
  登录 A0201「用户账户不存在」/ A0202「用户账户被冻结」/ A0210「用户密码错误」；
  重复用户名 A0111「用户名已存在」；重复角色名 A0714 `数据重复：role name already exists: X`；
  重复字典目录 code A0714 `数据重复：dict catalog code already exists: X`；
  重复 (caCode,value) A0714 `数据重复：dict detail value already exists under caCode X: Y`；
  科室重复 code A0712 `department code already exists: X`；
  缺 caCode 参数 A0410「请求必填参数为空」；未知更新目标 B0031（`role id X does not exist` /
  `user id X does not exist` / `dict catalog id X does not exist` / `dict detail id X does not exist` /
  `department id X does not exist`）；org 目标缺失 A0400（OrgServiceImpl requireLiveOrg 语义）；
  enable 越界 A0400 `enable must be 0 or 1`；roleIds 缺失 A0400 `some role ids do not exist: [..]`；
  menuIds 缺失 A0400 `some menu ids do not exist: [..]`。
- **新建用户缺省**：enable=1、mustChangePassword=0（V1 列缺省，UserInfoServiceImpl 注释佐证）；
  重置密码置 mustChangePassword=1。role 建缺省 type=11。
- **菜单树**：MenuTree POJO 全字段（`id→mid`、visible 整数→布尔、裁剪列 null、叶 children null、
  sort 排序、孤儿丢弃——TreeUtils 语义）；/me 与 login 返回**用户域树**（按角色 grant 过滤，
  AuthServiceImpl 语义），/menus/tree 返回全量树。

## 5. curl 冒烟证据（dev-server，端口 5177）

启动：`pnpm --dir lumen-web exec vite --port 5177 --strictPort`（后台；等价 brief 的
`pnpm dev -- --port 5177`，pnpm 会把 `--` 字面透传故改用 exec 形态）。全部在测，摘录：

1. **登录好凭据**：`code:"200"`，data 含 `token:"mock-jwt-…"` + profile（uid 1/系统管理员/
   mustChangePassword 1/roles ["系统管理员"]）+ 十二节点菜单树 + perms 集。
2. **坏用户名** → `{"code":"A0201","msg":"用户账户不存在","data":null}`；
   **坏密码** → `{"code":"A0210","msg":"用户密码错误","data":null}`；
   **停用后登录** → `{"code":"A0202","msg":"用户账户被冻结","data":null}`（enable 0 via users 端点后复验）。
3. **/me 无 Authorization / 非 Bearer** → `code:"203"`，msg 逐字（HTTP 200）；**带 Bearer** →
   `code:"200"` profile+menus+perms。其他被门端点（/menus/tree 等）无 token 同样 203。
4. **orgs**：/tree 返回种子根（children null）；POST 新根 `data:2` → 树现双根（多根能力）；
   PUT 改名 `data:true`；DELETE `data:true`。
5. **org-deps**：`?orgId=1` 过滤返回三种子科室；重复 code → A0712 逐字文案；新建 `data:4`。
6. **users**：/list 裸数组（admin 行 mustChangePassword 1）；重复用户名 → A0111；新建 `data:2`
   （mustChangePassword 0）；enable=5 → A0400；enable 0/1 → `data:true`；password 重置 →
   `data:true` 且列表 mustChangePassword 翻 1。
7. **roles（缺口端点）**：/list 返回 IPage 形状（records/total/size/current/pages）+
   `menuIds` 回显；重复名 → A0714 逐字；**全量替换 `PUT /roles/1/menus {menuIds:[1,2,3]}` 后
   GET /me 菜单树即时缩为 [系统管理,放射科]、perms 缩为 [system:user:list]**，再全量还原
   [1..12] 成功；未知 menuId → A0400 `some menu ids do not exist: [99]`。
8. **dicts**：catal/list IPage（common_status）；重复 code → A0714 逐字；
   detail/list?caCode=common_status → [启用(1),停用(0)] sort 升序；重复 (caCode,value) → A0714 逐字。
9. **logs**：`?page=1&pageSize=10` → IPage total 3，actionTime 倒序；
   `?type=%E6%96%B0%E5%A2%9E` → 仅「新增」行；`?dateYm=202611` → 两行 202611。
10. **放行证据（两条）**：未 mock 路径 `/api/v1/system/definitely-not-mocked`——
    (a) 默认 target `http://localhost:80`（本机 OrbStack 恰有真实后端栈）→ 请求落到 proxy、
    **真后端**自答 203 信封（字段序 code,msg,timestamp 与 mock 信封 timestamp,code,msg,data 可辨，
    dev 日志无 mock 命中行）；(b) `VITE_API_ENTRY_URL=http://localhost:59999` 重启 →
    同路径 **HTTP 500**，dev 日志 `http proxy error … AggregateError [ECONNREFUSED]`——
    未命中 mock 的 /api/v1 请求确实放行给 proxy，绝不返回 mock 数据。两 variant 均留证。

**收尾**：dev server 已 kill，`lsof` 确认 **端口 5177 释放**；无残留 vite 进程。

## 6. 其余验证

- `pnpm --dir lumen-web test` → **14/14 通过**（4 文件，退出 0），零回归。
- `pnpm --dir lumen-web build` → 退出 0（tsc 双遍 + vite build）。
- mock 泄漏检查：`rg -l "mock" dist/` 仅命中 `index-CPOWShdR.js` 里的 antd cssinjs `p.mock`
  （依赖内部字段）；`rg "mock-jwt|defineMock|mockDevServerPlugin|_fixtures|mock-dev-server" dist/`
  **零命中** → 构建产物不含 mock 代码（dev-only 成立）。
- `git status --porcelain`：本批改动 = `lumen-web/vite.config.ts`（M，+2/-1）+
  `lumen-web/mock/`（新增）+ 本报告；其余脏文件（`lumen-sev/.../SystemApplication.java`、
  `openspec/loop-state.yaml`、`openspec/sdd/progress.md`、brief）为并行会话/父进程既有，
  未触碰。

## 7. 问题（如实记录）

1. **brief 内部门冲突（已按并集解决，请复核）**：字面门只 grep 本变更 api delta，但同 brief
   强制 auth/dicts/logs mock，而这些端点文档化在已合并规格（api/system-data）。若严格字面门
   则必须删除 brief 自身要求的 8 个端点 mock。取舍：凡 openspec 任何规格文档化即放行
   （问题 3 表逐条留证），未放松任何「不得发明路径」语义；若需严格字面门，应先补 delta
   （另行走 openspec 变更，本批无权改）。**users/list 同源偏差**：delta 场景写 "paged user
   payload"，实现面（UserInfoController.list→`List<UserItemDto>`）是裸数组；按 brief「以后端
   实际形状为准」镜像裸数组。前端分页消费方（b4+ 的用户页）落地时需知悉此差异（或后端补
   分页 + delta 修正）。
2. **科室菜单占位 path 偏差（brief 指令 > V2 种子）**：V2 菜单种子里超声科/内镜科 path 为
   `/us`、`/endo`，前端路由表实际注册 `/ultrasound`、`/endoscopy`（src/router.tsx）；brief 明示
   占位为 radiology/ultrasound/endoscopy，故 mock 菜单树取后者。**org_dep 的 code 仍逐字保持
   种子值 `us`/`endo`**（DICOM/HL7 路由键，种子对照表已注明）；后续若后端菜单种子与前端路由
   对齐（另一变更），mock 树应同步改回。
3. **mock-only 取舍三则**：(a) 跨文件会话状态走 `globalThis`（插件逐文件 esbuild bundle 的
   事实约束，见 §1）——仍是会话内存，dev server 重启即复位，mock 文件编辑不丢；roles 全量
   替换 → /me 即时反映依赖此机制，已 curl 验证。(b) roles/list 记录附 `menuIds` 回显字段
   （缺口端点无实现面可照抄；web-system-console「重开对话框显示已授权集」场景需要），
   后端实现 list 时应给出等价通道或前端改从别处取。(c) /me 对「任意未注册 Bearer」回落到
   种子 admin 会话（brief 字面「携带→返回 profile」；token 注册表仅记录 login 签发的
   token→uid）。
4. **环境备注**：本机 80 端口有 OrbStack 真实后端栈在跑，proxy 放行证据因此出现「真后端
   203」variant——非本批引入，反而强化了放行证明；b4+ 联调时留意 mock 与真后端同时在环。

## 8. 交付物清单

- `lumen-web/vite.config.ts` — 插件接线（仅 plugins 数组 +2/-1）
- `lumen-web/mock/_fixtures.ts` — 种子常量 + 信封/门禁/分页/菜单树助手 + globalThis 状态
- `lumen-web/mock/auth.mock.ts` — POST /login、GET /me（203 门）
- `lumen-web/mock/menus.mock.ts` — GET /menus/tree
- `lumen-web/mock/orgs.mock.ts` — /orgs/tree + POST/PUT/DELETE
- `lumen-web/mock/org-deps.mock.ts` — /org-deps/list + POST/PUT/DELETE
- `lumen-web/mock/users.mock.ts` — /users/list + POST + /{id}/password + /{id}/enable
- `lumen-web/mock/roles.mock.ts` — /roles/list（缺口）+ POST/PUT/DELETE + /{id}/menus 全量替换
- `lumen-web/mock/dicts.mock.ts` — dicts/catal + dicts/detail 全套
- `lumen-web/mock/logs.mock.ts` — GET /logs 分页过滤
- 本报告 — `openspec/sdd/reports/web-foundation-b3-report.md`
