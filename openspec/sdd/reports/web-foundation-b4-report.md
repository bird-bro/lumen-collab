# Batch 4 Report — web-foundation-auth-console(tasks 4.1–4.3:传输客户端 + 会话状态 + typed clients)

> 状态:**DONE**。日期 2026-11-04。范围:`lumen-web/src/api/**`、`lumen-web/src/stores/auth.ts`、
> `lumen-web/src/test/**`(3 个新测试文件)。**未 commit**;`mock/**`、theme、layouts、pages、
> router、package.json、openspec 规格(本报告除外)零触碰(见文末 git status 范围核对)。

## 1. 交付物清单

| 文件 | 角色 |
|:--|:--|
| `lumen-web/src/api/client.ts` | 4.1 传输客户端:`configureApiClient` 注入 + `apiRequest<T>` + `ApiError` |
| `lumen-web/src/api/types.ts` | 4.3 DTO 集中地(全资源共享;选择集中式,见 §4) |
| `lumen-web/src/api/auth.ts` / `users.ts` / `orgs.ts` / `org-deps.ts` / `roles.ts` / `menus.ts` / `dicts.ts` / `logs.ts` | 4.3 typed clients(每资源一文件) |
| `lumen-web/src/stores/auth.ts` | 4.2 auth store(zustand + persist,key `'lumen-auth'`)+ 注入接线 |
| `lumen-web/src/test/api-client.test.ts` | 4.1 契约测试(13 条) |
| `lumen-web/src/test/auth-store.test.ts` | 4.2 契约测试(7 条) |
| `lumen-web/src/test/api-clients.test.ts` | 4.3 契约测试(27 条,每 client ≥1) |

未改动:`src/api/envelope.ts`、`src/api/entity-id.ts`、`src/api/index.ts`(按 brief 要求复用)、
`src/stores/index.ts`(留待 batch 5 决定是否桶导出)。

## 2. TDD 证据(RED → GREEN)

TDD 铁律:每个任务先写测试、跑出 RED、再实现转 GREEN。三个任务的失败模式一致——
模块尚不存在,Vite import-analysis 直接拒绝解析(整文件 RED),截图式证据如下。

### 4.1 传输客户端
- **RED**:`pnpm vitest run src/test/api-client.test.ts`
  `Error: Failed to resolve import "@/api/client" from "src/test/api-client.test.ts"` →
  `Test Files 1 failed (1) / Tests no tests`。
- **GREEN**(实现 `client.ts` 后):`Test Files 1 passed (1) / Tests 13 passed (13)`。

行为↔测试对照(4.1 的 8 条契约,含 2 条补充):

| 契约(brief 条目) | 测试 |
|:--|:--|
| 1. BASE_PATH 拼接 `/system/me` → `/api/v1/system/me` | `1. joins the resource path onto BASE_PATH` |
| 2. 有 token → `Authorization: Bearer t1`;无 token → 不带头 | `2a` / `2b` 两条 |
| 3. body 存在 → `Content-Type: application/json` + JSON.stringify | `3. serializes a present body ...` |
| 4. code "200" → resolve data;"204" → resolve null | `4a` / `4b` |
| 5. code "A0210"(HTTP 仍 200)→ throw `ApiError{code,msg}` | `5. throws ApiError{code,msg} for an A-class code ...` |
| 6. **"203":HTTP 200 也要触发** `onSessionExpired()` **并** throw | `6. fires onSessionExpired AND throws on code "203" even though HTTP is 200` |
| 7. 网络/非 JSON → `ApiError{code:'B0001'}`,msg 含原始原因 | `7a`(网络)/ `7b`(非 JSON)/ `7c`(JSON 但非 envelope) |
| 8. timeout/abort:non-goal(见 §6) | — |
| 补充:query 编码 + null/undefined 跳过(4.3 依赖) | `8. appends encoded query parameters ...` |
| 补充:缺省 GET、无 body | `9. defaults to GET ...` |

关键实现事实:**client 全程不读 `response.status`**(类型上根本没解构 status 字段),
"203 只看 envelope code、绝不看 HTTP 状态"由构造保证 + 专测 6 固定。

### 4.2 auth store
- **RED**:`pnpm vitest run src/test/auth-store.test.ts`
  `Error: Failed to resolve import "@/stores/auth" from "src/test/auth-store.test.ts"` →
  `Test Files 1 failed (1) / Tests no tests`。
- **GREEN**(实现 `stores/auth.ts` 后):`Test Files 1 passed (1) / Tests 7 passed (7)`。

测试要点:每条用例 `vi.resetModules()` + 动态 import,保证"先 seed localStorage 再创建 store"
的**创建时水合**顺序可复现,且模块级状态互不泄漏。覆盖:水合(test 1)、空初值(2)、
`setSession` 整写 + localStorage 同步(3)、`setSession` 部分写保留旧 token——/me 刷新场景(4)、
`clear` 四字段空 + 持久化清(5)、**接线闭环**:经 store 会话发出请求带 `Bearer t-wired`(6)、
"203" envelope 经接线的 `onSessionExpired` 清空 store 与持久化(7)。

### 4.3 typed clients
- **RED**:`pnpm vitest run src/test/api-clients.test.ts`
  `Error: Failed to resolve import "@/api/auth" from "src/test/api-clients.test.ts"` →
  `Test Files 1 failed (1) / Tests no tests`。
- **GREEN**(实现 8 个 client 后):`Test Files 1 passed (1) / Tests 27 passed (27)`。
- 如实记录:首轮 26/27——`createDetail ... round-trip` 失败,原因是**测试桩缺陷**(单响应桩把
  create 的返回 `3` 也喂给了后续 PUT/DELETE),实现本身正确;改用按调用次序出数据的
  `stubOkSequence` 后 27/27。未因此改动任何实现或配置。

## 3. 全量验证(brief § 验证逐条)

| # | 验证项 | 结果 |
|:--|:--|:--|
| 1 | `pnpm --dir lumen-web test` | 退出 0;**7 文件 / 61 测试全绿**(既有 14 + 本批 47 = 13+7+27),零回归 |
| 2 | `pnpm --dir lumen-web build`(`tsc --noEmit` strict × 2 + vite build) | 退出 0;strict/noUnusedLocals/noUnusedParameters 未放宽(见 §5 问题 1) |
| 3 | hex 守卫 `src/test/hex-scan.test.ts` | 单跑 1/1 绿;全量套件内亦绿;本批新文件零 hex 字面量 |
| 3b | `rg -n "axios" lumen-web/src` | **0 命中**(exit 1 = 无匹配) |
| 4 | 路径纪律 | 见 §4 对照表,9 个文件中每个路径串逐一对应 api delta / 主规格 / console delta |
| 5 | `git status --porcelain` 范围 | 本批新增仅 `lumen-web/src/api/**`(10 文件)+ `src/stores/auth.ts` + `src/test/**`(3 文件)+ 本报告;`lumen-sev/**`、`openspec/loop-state.yaml`、`openspec/sdd/progress.md` 的 M 是批前既有,未触碰 |

接线方向证明:`grep` 证实 `src/api/client.ts` 无任何 `@/` import(仅 `./envelope`、`./index`),
**client 不 import store,依赖单向(store → client)**,无循环依赖;store 模块初始化时执行一次
`configureApiClient({ getToken: () => useAuthStore.getState().token, onSessionExpired: () => useAuthStore.getState().clear() })`,
4.1 的注入点闭环(测试 4.2-6/7 即闭环证明)。

## 4. 类型决策与路径对照(事实来源 = `mock/_fixtures.ts` + api delta)

### 4.1 MenuNode 字段实录(照抄 `MockMenuTree`,MenuTree POJO JSON 形状)

`mid`(**不是 id**)、`title`、`upId`、`icon`、`path`、`query`、`component`、`type`(string,'M'|'C'|'F')、
`sort`、`visible`(**Integer 1/0 → boolean**)、`redirect`、`app`、`breadcrumb`、`action`、
`isCache`、`isFrame`、`perms`、`remark`、`children`(**叶子为 null**,TreeUtils 语义)。
类型侧 `MenuNode.children: MenuNode[] | null` 递归。

### 4.2 其余类型决策

| 决策 | 依据 |
|:--|:--|
| `UserProfile = {uid, username, name, enable, mustChangePassword, roles: string[]}` | mock `profileOf()` 实测形状 |
| `LoginData {token, profile, menus, perms}`;`MeData {profile, menus, perms}`(**无 token**) | mock `/me` 不重发票据;brief "同形状(menus/perms 也含)"按夹具实况收窄 |
| `Page<T> = {records, total, size, current, pages}`(命名 Page,注释注明镜像 IPage) | mock `pageOf()`;roles/list、dicts/catal/list、logs 三处使用 |
| `UserItem` **无 password 字段**(裸数组,BCrypt hash 永不序列化) | mock list rows + api delta "plain List" |
| `RoleItem.menuIds?: number[]` + JSDoc 标注 **mock-only** | roles/list 是 delta 声明的缺口端点,后端 follow-up 不承诺该字段,故 optional |
| IDs 全部 JSON **number**(uid/id/upid/caId/dateYm…) | 主规格 § ID Serialization(2026-11-02 Integer 自增决策);`entity-id.ts` 的 snowflake-string 说明是旧决议残留,未引用、未改动(见 §6 问题 3) |
| `ListLogsQuery` 用 **type alias** 而非 interface | interface 无隐式索引签名,过不了 build 的 tsc 对 `Record` 形参的赋值检查(alias 有);行为不变 |
| 传输错误码选定 **`B0001`**(未导出常量 `TRANSPORT_ERROR_CODE`) | errors 规格 B0001 = 系统错误语义,是最近的 B 类码;brief "B0001-类(自定义码,报告记录码值)" |
| DTO 集中于 `src/api/types.ts`(brief 给的两个选项之一) | 八个 client 共享 UserProfile/MenuNode/Page;单点维护优于八处散落 |

### 4.3 路径对照表(client 文件内每个端点路径串)

统一经 `apiRequest` 走 `BASE_PATH` 拼接,资源文件只传字面量相对路径 + `query` 对象,
**URL 组装只发生在 client.ts**(brief "禁止在 client 之外拼 URL")。

| client 函数 | 代码内路径串 | 实际请求 | 规格出处 |
|:--|:--|:--|:--|
| auth.login | `'/system/login'` | POST /api/v1/system/login | `openspec/specs/api/spec.md` § Authentication Endpoints |
| auth.me | `'/system/me'` | GET /api/v1/system/me | 同上 |
| users.listUsers | `'/system/users/list'` | GET /api/v1/system/users/list | change `specs/api/spec.md` § User Management Endpoints |
| users.createUser | `'/system/users'` | POST /api/v1/system/users | 同上 |
| users.resetPassword | `` `/system/users/${id}/password` `` | PUT /api/v1/system/users/{id}/password | 同上 |
| users.enableUser | `` `/system/users/${id}/enable` `` | PUT /api/v1/system/users/{id}/enable | 同上 |
| orgs.orgTree | `'/system/orgs/tree'` | GET /api/v1/system/orgs/tree | change `specs/api/spec.md` § Org Tree Endpoints |
| orgs.createOrg / updateOrg / deleteOrg | `'/system/orgs'`、`` `/system/orgs/${id}` `` ×2 | POST / PUT / DELETE | 同上 |
| org-deps.listDeps | `'/system/org-deps/list'` + `query:{orgId}` | GET /api/v1/system/org-deps/list?orgId= | change `specs/api/spec.md` § Department Endpoints |
| org-deps.createDep / updateDep / deleteDep | `'/system/org-deps'`、`` `/system/org-deps/${id}` `` ×2 | POST / PUT / DELETE | 同上 |
| roles.listRoles | `'/system/roles/list'` + `query:{pageNum,pageSize}` | GET /api/v1/system/roles/list | change `specs/api/spec.md` § Role Management Endpoints(**缺口端点,mock 供数**) |
| roles.createRole / updateRole / deleteRole | `'/system/roles'`、`` `/system/roles/${id}` `` ×2 | POST / PUT / DELETE | 同上 |
| roles.assignRoleMenus | `` `/system/roles/${id}/menus` `` + body `{menuIds}`(全量替换) | PUT /api/v1/system/roles/{id}/menus | 同上 § Role-menu full-replace assignment |
| menus.menuTree | `'/system/menus/tree'` | GET /api/v1/system/menus/tree | change `specs/api/spec.md` § Menu Tree Endpoint |
| dicts.listCatal | `'/system/dicts/catal/list'` + `query:{pageNum,pageSize}` | GET /api/v1/system/dicts/catal/list | change `specs/web-system-console/spec.md` § Dictionary Management |
| dicts.createCatal / updateCatal / deleteCatal | `'/system/dicts/catal'`、`` `/system/dicts/catal/${id}` `` ×2 | POST / PUT / DELETE | 同上 |
| dicts.listDetails | `'/system/dicts/detail/list'` + `query:{caCode}` | GET /api/v1/system/dicts/detail/list?caCode= | 同上;**裸数组、sort 升序,已按 mock 实测确认** |
| dicts.createDetail / updateDetail / deleteDetail | `'/system/dicts/detail'`、`` `/system/dicts/detail/${id}` `` ×2 | POST / PUT / DELETE | 同上 |
| logs.listLogs | `'/system/logs'` + `query:{page,pageSize,type?,uid?,dateYm?}` | GET /api/v1/system/logs | change `specs/web-system-console/spec.md` § Operation Log Query |

分页键差异已在类型/JSDoc 固化:roles 与 dicts 用 `pageNum/pageSize`(PageSizeConfig 惯例),
logs 用 `page/pageSize`(logs 端点自身惯例),mock 实测一致。

## 5. 过程问题(已解决,如实记录)

1. **vitest 绿 ≠ tsc 绿**:全量 vitest 61/61 后,`pnpm build` 的 strict tsc 抓出 3 处——
   (a) `ListLogsQuery` interface 缺隐式索引签名,赋给 `Record` 形参报 TS2322(改为 type alias);
   (b) store 测试残留未用助手 TS6133(删除);(c) 无参 mock 的 `calls[0][1]` 不可索引
   TS2352/TS2493(改用 `(...args: unknown[])` 签名的桩)。三处均为收紧而非放宽配置,复跑 build 退出 0。
2. **4.3 首轮 26/27**:单响应桩导致 `updateDetail` 断言拿到 create 的 `3`(测试侧缺陷),
   换 `stubOkSequence` 后全绿;实现零改动。
3. 每个 client 的 stub 均为只暴露 `json()` 的朴素对象——因 client 按规格**根本不读 HTTP 状态**,
   status 字段在桩上属于死代码,这是"203 语义"的构造性证明的一部分。

## 6. 遗留观察(未动、不在本批范围)

1. **timeout/abort = non-goal**(brief 4.1 条目 8):`apiRequest` 未做超时与 AbortSignal;
   若后续需要,以新增可选 opts 字段的重载/扩展方式加,不动现有签名。
2. `src/api/index.ts` 头注"seam is deliberately unpopulated until the API spec gains its first
   concrete endpoint"已过时(seam 现已填充 9 个 client 文件);该文件本批只复用未改,建议
   batch 5/收尾时顺手更新注释。
3. `src/api/entity-id.ts`(`EntityId = string`,snowflake 论据)与主规格现行
   "Integer 自增 → JSON number"决议冲突;本批 DTO 全部用 `number` 且不引用该类型,文件本身
   按复用纪律原样保留,建议后续变更删除或改写(需按"新增优先"的演化风格处理)。
4. 主规格 § Authentication Endpoints 文字为 login 返回 "JWT + profile",而 mock/brief 让 login
   同样携带 menus/perms(超集);类型按 brief + mock 收紧,规格文字差异记录在案。
5. 命名口径:tasks.md 4.2 写 "login/clear actions",brief(更细)指定 `setSession/clear`;
   按 brief 实现——store 不发 HTTP,登录页(batch 5)组合 `auth.login()` + `setSession`。
   `clear()` 的"持久化移除"实现为 persist 写入清空后的四字段(storage 里是无会话状态,
   再水合结果与删 key 等价),测试 5/7 断言该行为。
6. `setSession` 签名取 `Partial<AuthSession>`:登录写全四字段、/me 刷新只写 profile/menus/perms
   两种调用都成立(brief 参数名 "partial" 即此意);测试 4 专测部分写保留旧 token。

## 7. 结论

三任务全部 TDD 完成,五项验证全过(61 测试 / build strict 绿 / hex 绿 / 无 axios / 范围干净),
无失败搁置项。batch 5(login 页、RequireAuth、动态导航)可直接消费:
`login()`、`me()`、`useAuthStore`(state + setSession/clear)、`ApiError`、`MenuNode`/`UserProfile`。
