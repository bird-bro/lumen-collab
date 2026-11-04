# Batch 3 Brief — web-foundation-auth-console（task 3.1：mock 服务器 + 种子对齐夹具）

> 事实来源:brief + `specs/web-app-shell/spec.md` § Dev Mock Server(权威)+ 本变更 api delta
> (端点清单)。工作目录 REPO。**不要 commit**。本批只动:`lumen-web/vite.config.ts`、
> `lumen-web/mock/**`、本报告。**不碰** src/ 业务与主题文件、package.json、openspec/ 规格。

## 环境铁律
- `pnpm --dir lumen-web`;vite-plugin-mock-dev-server 2.7.1 已装(b1)。
- **插件用法以本地 README 为准**:读
  `lumen-web/node_modules/vite-plugin-mock-dev-server/README.md` 确认 mock 目录约定、
  文件命名、`defineMock` API(版本 2.x 的真实约定优先于任何记忆);报告记录你读到的约定。
- 端点前缀一律 `/api/v1/system/**`(与 api delta、后端现状一致)。
- 信封:`{ timestamp: 'yyyy-MM-dd hh:mm:ss.SSS'(真实当前时间), code, msg, data }`;
  成功 `code:"200"`(mutation 返回值存在时)或 `"204"`(无 body);**203 = 未认证**
  (HTTP 200,body.code "203",data null)。

## 接线

`vite.config.ts`:import mock 插件并加入 `plugins`(`react()` 之后);`server.proxy` 与
`build.rollupOptions` 不动;test 块不动。插件拦截未命中 mock 的 `/api/v1` 请求时应放行给
proxy——在报告里用一条 curl 证明(如登录 mock 命中、随便一个未 mock 路径落到 proxy 连接
拒绝而非返回 mock 数据)。

## 夹具(种子对齐,§ Dev Mock Server 的 Seed parity 场景)

**先读后端种子取真值**:`lumen-sev/lumen-system/src/main/resources/db/migration/` 的
seed 迁移(V2/V3)——默认管理员用户名/初始密码/uid、医院根节点(id/名称)、三科室及其
unit 节点、`common_status` 字典目录与 details(含 label/value/sort)。把这些真值写进
mock 夹具,报告里列表对照(种子值 → mock 值)。

**seed 里查不到的字段**(如 JWT 形状)用显式假值并注明 `mock-only`。

### 各资源(每个 mock 文件对齐 api delta 的 requirement)
1. **auth**:`POST /login` — 命中种子管理员凭据 → `code:"200"`,data 含 token
   (`mock-jwt-<random>`)+ profile;错误凭据 → `A`-class 码(照抄种子规格里的
   A0210 语义,`data:null`)。`GET /me` — **校验 Authorization 头**:缺失/非 Bearer →
   `code:"203"`;携带 → profile + 菜单树 + perms 集。
2. **menus**:`GET /menus/tree` — 目录/菜单/按钮三层树,含 `perms` 字符串;内容 =
   系统管理目录(orgs/deps/users/roles/menus/dicts/logs 七项)+ 三个科室占位
   (radiology/ultrasound/endoscopy)。/me 的菜单树返回同一份。
3. **orgs**:`GET /tree`(种子树,多根能力=支持 POST 新根)、`POST`、`PUT/{id}`、
   `DELETE/{id}`(POST/PUT/DELETE 回 `data:true`,POST 建节点回新 id 亦可——以后端
   controller 实际返回为准,读 OrgController 后对齐并报告)。
4. **org-deps**:`GET /list?orgId=`(按 org 过滤)、`POST`、`PUT/{id}`、`DELETE/{id}`。
5. **users**:`GET /list`(分页结构**以后端分页返回形状为准**——读 UserInfoController
   与其 service 的分页包装,镜像字段名)、`POST`(重复用户名 → 后端的 `A`-class 码 +
   msg 文案照抄后端 ExceptionEnum/业务异常文案)、`PUT /{id}/password`、
   `PUT /{id}/enable`。
6. **roles**:`GET /list`(**缺口端点**,按 api delta 场景 mock 分页角色列表)、
   `POST`(重名 → `A`-class)、`PUT/{id}`、`DELETE/{id}`、`PUT /{id}/menus`
   (**全量替换语义**:模块级内存状态保存每个角色的菜单 id 集,保存后 GET /me 与
   roles/list 反映新授权——dev 会话内存即可,不要求持久)。
7. **dicts**:`GET /dicts/catal/list`、catal `POST`/`PUT/{id}`/`DELETE/{id}`
   (重复 code → `A`-class);`GET /dicts/detail/list?caCode=`(按 sort 升序)、
   detail `POST`/`PUT/{id}`/`DELETE/{id}`。
8. **logs**:`GET /logs?type=&uid=&dateYm=`(分页、按 actionTime 倒序、无写操作)。
   夹具数行含不同 type/dateYm 便于过滤演示。

CRUD 的内存状态:模块级变量即可(dev 会话内一致);无需 mock 间共享状态管理框架。

## 验证(全部留证)
1. **未文档化路径门**:从 mock 文件提取全部 `url` 字符串,逐条
   `grep -F "<url>" openspec/changes/web-foundation-auth-console/specs/api/spec.md`
   → 全部命中(delta 即权威);报告给对照表。若有 mock url 不在 delta 里 → 删掉,不许留。
2. **dev-server 冒烟**:`pnpm --dir lumen-web dev -- --port 5177 --strictPort` 后台起,
   curl 逐条(带 Authorization 与不带的 /me 都要;login 好坏凭据都要;分页/过滤参数各
   一条),贴响应 JSON(截断即可);**测完 kill 该 dev server**(确认端口释放)。
3. `pnpm --dir lumen-web test` 退出 0(14/14 不回归)。
4. `pnpm --dir lumen-web build` 退出 0;`rg -l "mock" lumen-web/dist` 为空或确认构建
   产物不含 mock 代码(dev-only)。
5. `git status --porcelain` 显示本批只动了 vite.config.ts + mock/** + 报告。

## 完成标准
全部验证过;报告写 `openspec/sdd/reports/web-foundation-b3-report.md`(中文:插件约定
摘要、种子对照表、url→delta 对照表、curl 证据、问题)。失败即停、如实记录。
