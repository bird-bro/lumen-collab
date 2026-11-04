# Batch 4 Brief — web-foundation-auth-console（tasks 4.1–4.3：传输客户端 + 会话状态 + typed clients）

> 事实来源:brief + `specs/web-auth-flow/spec.md`(权威)+ design.md D4/D5。工作目录 REPO。
> **不要 commit**。本批只动:`lumen-web/src/api/**`、`lumen-web/src/stores/**`、
> `lumen-web/src/test/**`。**不碰**:mock/**、theme、layouts、pages、router、package.json。
> TDD 铁律:每个行为先写失败测试留 RED 证据,再实现转 GREEN。

## 环境铁律
- `pnpm --dir lumen-web`;零新增依赖(zustand 5.0.16 / vitest 5.1.0 已就位)。
- 现有类型基座:`src/api/envelope.ts`(`ApiEnvelope<T>`,code 是 **string**)+ `src/api/index.ts`
  (`BASE_PATH = '/api/v1'`)。复用,不得重复定义。
- **类型真值来源 = mock 形状**(`lumen-web/mock/_fixtures.ts` + 各 `*.mock.ts`)+ api delta。
  先读 `_fixtures.ts` 的 profile / 菜单树节点(注意 MenuTree POJO 字段:`mid`、菜单名等——
  以夹具真实键名为准)/ IPage(`records/total/size/current/pages`)再写 TS 类型。
  ID 一律 JSON **number**(规格 § ID Serialization);mock-only 字段(如 roles/list 的
  `menuIds` 回显)类型照带并在注释标注 `mock-only`。
- hex 纪律守卫在 `src/test/hex-scan.test.ts` 常绿——本批不引入任何 hex。

## 任务 4.1 — TDD:传输客户端 `src/api/client.ts`

设计(D4):小而薄的 fetch 封装,**无 axios**。依赖注入避免与 store 循环依赖:

```ts
configureApiClient({ getToken, onSessionExpired })  // 模块级,默认 no-op
apiRequest<T>(path, opts)  // path 以 '/' 起,拼到 BASE_PATH
class ApiError extends Error { code: string; msg: string }
```

行为契约(逐条对应测试):
1. join:`apiRequest('/system/me')` → fetch 到 `/api/v1/system/me`(BASE_PATH 拼接,断言 URL)。
2. Bearer:配置了 getToken 返回 `'t1'` → 请求头 `Authorization: Bearer t1`;无 token →
   不带该头(断言两种)。
3. JSON 体:`opts.body` 存在 → `Content-Type: application/json` 且序列化。
4. 解包:HTTP 200 + envelope `code:"200"` → resolve `data as T`;`code:"204"` → resolve `null as T`。
5. 错误:`code:"A0210"`(HTTP 仍 200)→ throw `ApiError{code:'A0210', msg}`——**绝不看 HTTP 状态**。
6. **203 语义**:HTTP 200 + `code:"203"` → 调用 `onSessionExpired()` **并** throw
   `ApiError{code:'203'}`(调用方不吞)。专测一条:HTTP 200 也要触发(规格明文)。
7. 非 JSON/网络层失败 → throw `ApiError{code:'B0001'-类传输错误}`(自定义码,B 类语义,
   msg 含原始原因;报告记录选定的码值)。
8. timeout/abort:不要求(报告记录为 non-goal)。

## 任务 4.2 — TDD:auth store `src/stores/auth.ts`(zustand + persist)

设计(D5):`zustand/middleware` 的 `persist`,localStorage key `'lumen-auth'`。

1. State:`{ token: string|null, profile: UserProfile|null, menus: MenuNode[], perms: string[] }`。
2. Actions:`setSession(partial: { token, profile, menus, perms })`(整体写入并持久化)、
   `clear()`(四字段清空并持久化移除)。
3. **接线**:`configureApiClient({ getToken: () => get().token, onSessionExpired: () => get().clear() })`
   在 store 模块初始化时执行一次(4.1 的注入点就此闭环;报告说明无循环依赖:client 不 import store)。
4. 测试:seed localStorage 后创建 store → 水合成功;`setSession` → localStorage 同步;
   `clear` → 字段空 + localStorage 清;`onSessionExpired` 触发 `clear`。

## 任务 4.3 — typed clients `src/api/`(每资源一文件,路径逐字对齐 api delta)

文件与契约(全部走 `apiRequest`,禁止在 client 之外拼 URL):
- `auth.ts`:`login({username, password})` → `POST /system/login`,data
  `{ token, profile, menus, perms }`;`me()` → `GET /system/me`,同形状(menus/perms 也含)。
- `users.ts`:`listUsers()` → `GET /system/users/list`,data = `UserItem[]`(**裸数组**,
  b3 已核实);`createUser(body)` → `POST /system/users`,data = number(新 id);
  `resetPassword(id, password)` → `PUT /system/users/{id}/password`,data = true;
  `enableUser(id, enable: 0|1)` → `PUT /system/users/{id}/enable`,data = true。
- `orgs.ts`:`orgTree()` → `GET /system/orgs/tree`,`OrgNode[]`;`createOrg/updateOrg/deleteOrg`。
- `org-deps.ts`:`listDeps(orgId)` → `GET /system/org-deps/list?orgId=`,`OrgDep[]`;
  `createDep/updateDep/deleteDep`。
- `roles.ts`:`listRoles()` → `GET /system/roles/list`(data = IPage 形状 + `menuIds` 回显,
  标注 mock-only/缺口);`createRole/updateRole/deleteRole`;`assignRoleMenus(id, menuIds)` →
  `PUT /system/roles/{id}/menus`(全量替换,data true)。
- `menus.ts`:`menuTree()` → `GET /system/menus/tree`,`MenuNode[]`(全量树)。
- `dicts.ts`:`listCatal(pageNum,pageSize)` → IPage;`createCatal/updateCatal/deleteCatal`;
  `listDetails(caCode)` → `GET /system/dicts/detail/list?caCode=`,`DictDetail[]`(sort 升序,
  裸数组——以 mock 实测为准,报告确认);detail CRUD 同构。
- `logs.ts`:`listLogs({page,pageSize,type?,uid?,dateYm?})` → IPage,actionTime 倒序。
- 测试:每 client 至少一条——stub fetch 断言 **方法+路径+query/body**,并断言解包类型
  (如 users list 返回数组长、create 返回 number)。DTO 类型集中 `src/api/types.ts`
  (或在各文件内导出,报告说明选择)。

## 验证(留证)
1. `pnpm --dir lumen-web test` 退出 0(既有 14 条零回归 + 本批新测试;报告给出总数)。
2. `pnpm --dir lumen-web build` 退出 0(strict 不放宽)。
3. hex 守卫绿;`rg -n "axios" lumen-web/src` = 0(证明无 axios)。
4. 路径纪律:client 文件中出现的每个端点路径字符串能对上 api delta/主规格
   (报告给对照表;`/system/...` 相对路径 + BASE_PATH 拼接,逐一列出)。
5. `git status --porcelain` 范围 = src/api/** + src/stores/** + src/test/** + 本报告。

## 完成标准
全部验证过;报告写 `openspec/sdd/reports/web-foundation-b4-report.md`(中文:RED→GREEN
证据、类型决策(含 MenuNode 字段名实录)、路径对照表、问题)。失败即停、如实记录。
