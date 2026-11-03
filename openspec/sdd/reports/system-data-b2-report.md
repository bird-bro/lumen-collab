# Batch 2 Report — system-data（tasks 3.1, 3.2, 3.3, 3.4, 4.1–4.5）

Status: **DONE_WITH_CONCERNS**（全部任务完成、全量套件绿；3 条关注项见"关注项"节，其中
登出链路 NPE 修复为新登记的 framework 适配，需协调者确认入协调文档）

## 实现了什么

### 任务 3.1 — 字典端点（lumen-auth）
- `DictCatalController`（`/api/v1/auth/dicts/catal`）：POST 创建、PUT /{id}、DELETE /{id}
  （逻辑删）、GET /list（分页，绑定拷贝件 `CatalQuery`，name/cls 过滤）。
- `DictDetailController`（`/api/v1/auth/dicts/detail`）：POST、PUT /{id}、DELETE /{id}、
  GET /list?caCode=（仅 live，sort 升序、id 升序兜底稳定）。
- 返回值纪律照现有控制器：create 返回 Integer（新 id），update/delete 返回 Boolean.TRUE
  （void 会落 "204" 信封分支）。
- 新增 lumen-auth 管理层：`DictCatalManageService(+Impl)`、`DictDetailManageService(+Impl)`
  （类名带 Manage 后缀，原因见"偏差记录 B"）。查重在服务层（live 行，排除自身）：
  - 目录重复 code → `A0714`（部分唯一索引 `uk_dict_catal_code WHERE deleted=0` 兜底）；
  - 明细重复 (caCode, value) → `A0714`（对应 DDL 的 `uk_dict_detail_ca_code_value`），
    value 为空跳过查重（PG 唯一索引中 NULL 互不相斥，服务层与 DDL 语义对齐）；
  - caId 找不到 live 目录 → `A0400`；更新目标不存在 → `B0031`（照 RoleServiceImpl 先例）。
- **ca_code 冗余锚点由服务端解析**（场景 3 双锚点一致性）：create/update 均以 caId 反查
  live 目录后写 ca_code，请求体里的错误 caCode 永远不会落库（测试有专断言）。
- **Batch 1 复核交接 3（CatalCreate.enable 是 Byte）**：在
  `DictCatalManageServiceImpl.create/update` 中显式 `request.getEnable().intValue()` 转
  Integer 落实体，javadoc 注明；`DetailCreate.enable` 本就是 Integer，无需处理。

### 任务 3.2 — 日志查询端点
- `LogController`：GET `/api/v1/auth/logs`，参数 page/pageSize（默认 1/20）+ 可选
  type/uid/dateYm；MyBatis-Plus `Page<LogInfoEntity>`，actionTime 倒序 + id 倒序兜底同秒
  稳定性；deleted=0 用 `apply("deleted = 0")`（拷贝件实体无 @TableLogic，与 XML 裸 SQL 同款）。
- **framework/log 加法式新方法**（brief 明确属适配范围，已报告）：`ILogInfoService.getPage(
  Integer pageNum, Integer pageSize, String type, Integer uid, Integer dateYm)` +
  `LogInfoServiceImpl` 实现；原 `getLogInfoPage(LogRequest)`（account/时间窗查询）一行未动。

### 任务 3.3 — `@Log` 接线
- 拷贝件注解 `common/annotation/Log`（title/type/desc 三参）标注到全部管理写方法：
  - OrgController：create/update/delete（title 机构管理）
  - OrgDepController：create/update/delete（title 科室管理）
  - UserInfoController：create/resetPassword/setEnable（title 用户管理）
  - RoleController：create/update/delete/assignMenus（title 角色管理）
  - DictCatalController / DictDetailController：各自 create/update/delete（title 字典目录 /
    字典明细）
- type 取值选型（报告记录）：新增/修改/删除/重置密码/授权，中文操作动词，与 @ApiOperation
  中文展示串一致；desc 留空。共 19 处。

### 任务 3.4 — 登录事件接线
- `AuthServiceImpl.login`：成功路径在 `createToken` 之后
  `AsyncManager.me().execute(AsyncFactory.recordForSign(account, SUCCESS, "Sign-IN",
  "登录成功，用户"+account, loginUser))`——loginUser 提供 uid + token（UUID 会话锚点，非 JWT）。
- 失败路径：login 整体 try/catch (BusinessException) → `recordFailedSign` 记录后
  **rethrow 原异常**（信封行为不变，A0201/A0202/A0210 测试全部回归通过）。
  - **SIGN 常量选型（报告记录）**：grep 全库确认 Constants 仅有 `SIGN_OUT=12`，无 SIGN_IN。
    选既有通用对 **`SUCCESS=10`（登录成功）/ `FAIL=11`（登录失败）**，登出保持 12；不修改
    common，不与 12 冲突。测试断言 type=10/11/12 与之锁定。
  - **recordForSign 对 null userLogin 的容忍度（先读源码后处理）**：args 为空时
    `userLogin = new UserLogin()`（token/uid 为 null），插入列均可空——容忍成立。
    处理：**禁用/密码错**分支构造仅含 userId 的最小 UserLogin（失败行仍带 uid）；
    **账号不存在**分支不传 varargs（uid 落 null，account 仍可查）。

### 任务 4.1–4.5 — 测试（先红后绿）
- 4.1 `AuthDataLayerTests` 扩充 `dictCatalEntityRoundTripResolvesIntoPublicSchema`：
  IDictCatalService save→getById 往返 + `public.dict_catal` 定点查到该行（未限定名经
  currentSchema=auth,public 落 public 的运行时实证）+ 断言 auth schema 无 dict_catal 影子表
  + id 越过种子 1/2（setval）。information_schema 四表断言 Batch 1 已建
  （AuthSystemDataMigrationTests），本批不重复。
- 4.2 `AuthDictCrudTests`（3 测试）：建目录（create_by=1 审计实证 + enable 默认 1）→ 重复
  code A0714（data null）→ 建明细（错误 caCode 被服务端纠正 + 双锚点落库）→ 按 caCode 列表
  （sort 排序精确断言）→ 重复 (caCode,value) A0714 → 孤儿明细 A0400 → 删明细后列表收缩 →
  删目录后 code 可复用（部分唯一索引纪律）。
- 4.3 `AuthLogQueryTests`：经 ILogInfoService.insert 播种 3 条受控行（独立 uid/类型/秒级
  actionTime）→ 分页 records/total → uid 过滤精确 2 条且严格倒序 → type 过滤 → dateYm（当月）
  过滤 → 组合无结果空页边界（records 空 + total 0）。
- 4.4 `AuthOperationLogTests`：带 Chrome UA 调 POST /api/v1/auth/orgs → **轮询**
  public.log_info（uid=1、url、method、title='机构管理'、param 含机构名，20×250ms）→
  record=0 实证。UA 说明：LogAspect/AsyncFactory 解析浏览器引擎，TestRestTemplate 默认
  `Java/xx` UA 无可解析版本，故 sign/@Log 相关请求显式携带真实浏览器 UA（测试侧处理，
  生产浏览器流量天然满足）。
- 4.5 `AuthSignLogTests`（2 测试）：新建专用用户（隔离共享库噪声）→ 登录成功轮询
  type=10+uid 行（msg='Sign-IN'、token 非空、descr 含"登录成功"）→ 密码错误轮询
  type=11+uid 行 → POST /api/sign/out 登出轮询 type=12 行（msg='Sign-OUT'、descr 含
  "退出成功"）——登出断言验证适配 (7) 实效。
- **轮询纪律**：共享 `AuthAsyncPoll`（20×250ms），全程无裸 sleep 断言。

## Batch 1 复核交接（4 项全部处理）

1. **LogSignMapper.xml:40 `#{req}` 缺陷 — 已修**：`AND account = #{req}` → `#{req.account}`
   （与 LogInfoMapper.xml 正确写法对齐；先读 XML 与 mapper 接口 `@Param("req") LogRequest`
   确认参数名）。属适配 (7) 范围，本报告登记。
2. **list-by-caCode 新方法 — 已加**：`IDictDetailService.listByCaCode(String caCode)` +
   impl（enable=1、sort 升序、id 兜底；@TableLogic 自动滤已删）。加法式，既有 getPage
   （按 caId）未动。
3. **CatalCreate.enable Byte — 已适配**：见任务 3.1 节，lumen-auth 管理层显式转换并报告。
4. **~/.m2 陷阱 — 已遵守**：每次改 framework 后先
   `./mvnw -pl lumen-bom,framework,common install -DskipTests`（MVN_EXIT=0）再跑
   `-pl lumen-auth` 测试，全程未踩坑。

## RED + GREEN 证据

- **RED**（仅新增测试文件，实现未动，全量 `./mvnw test -q`）：**50 tests, 8 failures**
  —— 全部落在新测试类，既有 42 全绿：
  - AuthDictCrudTests ×3：`expected: <200> but was: <404>`（端点不存在）
  - AuthLogQueryTests ×1：`expected: <200> but was: <404>`
  - AuthOperationLogTests ×1：`async write did not appear within the 5000 ms poll window`
    （无 @Log 行）
  - AuthSignLogTests.loginSuccessAndWrongPassword ×1：`async write did not appear ... type 10`
    （无登录 sign 接线）
  - AuthSignLogTests.signOut ×1：登出 **HTTP 500**（发现拷贝件登出链路潜在缺陷，见"重要发现"）
  - AuthDataLayerTests.dictCatalEntityRoundTrip ×1（预期外，见"偏差记录 A"）
- **GREEN**（全部落位后）：
  - scoped：`./mvnw -pl lumen-auth test -q` → **51 tests / 0 failures / 0 errors**（含当时
    未删的探针测试 1 个；探针已删，正式数 50）
  - **全量**：`./mvnw test -q` → **MVN_EXIT=0，lumen-auth surefire 50 tests / 0 failures /
    0 errors / 0 skipped**（既有 42 + 新增 8）

## 重要发现与修复：登出链路 NPE（超出 brief 预期，需协调者确认）

RED 阶段发现：**POST /api/sign/out 即使在 V3 落表后也 100% 失败（HTTP 500 → /error → 203
信封）**，与 brief"V3 后应落行"的预期不符。逐层排查（surefire 堆栈 + 过滤器探针）：

- 根因：拷贝件 `AsyncFactory.recordForSign` 在**调用线程**同步执行
  `UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"))`，而
  `LogoutSuccessHandlerImpl` 由 Security 链的 `LogoutFilter` 调用——此阶段
  `RequestContextHolder` 为空（本服务**没有任何** RequestContextFilter 注册，过滤器探针
  实证：仅 jwtAuthenticationTokenFilter / userHeaderBridgeFilter / corsFilter /
  springSecurityFilterChain / characterEncodingFilter），`ServletUtils.getRequest():50` NPE。
- 影响：登出接口完全不可用（不只是 sign 行不落），且 4.5 登出断言不可能达成。
- **修复（framework 新增文件，零拷贝件改动）**：`framework/config/RequestContextConfig.java`
  ——注册 Boot `OrderedRequestContextFilter`（order = SecurityProperties.DEFAULT_FILTER_ORDER-5
  = -105，先于 Security 链 -100，REQUEST dispatch），条件与 Boot 自己的
  `WebMvcAutoConfiguration.requestContextFilter` 完全一致（@ConditionalOnMissingBean +
  @ConditionalOnMissingFilterBean），保证任何环境只会有一个。该过滤器仅绑定/复位线程状态、
  不包装请求，除"让拷贝件假设成立"外无行为变化。GREEN 后登出行正常落库。
- 性质：与 UserHeaderBridgeFilter（适配 (6)）同类的 framework 属主适配——**建议登记为
  适配 (8)**，由协调者确认。

## 偏差记录

- **A. 4.1 数据层往返测试无 RED**：batch 1 已交付 V3 与 framework/dict 实体，roundtrip
  测试首跑即绿（RED 阶段它在 50 全量跑中通过）。brief 允许"若 Batch 1 已建
  AuthSystemDataMigrationTests 则扩充之"，information_schema 部分由此豁免重复。
- **B. lumen-auth 管理服务命名 DictCatal/DetailManageService(+Impl)**：原拟
  `DictDetailServiceImpl` 与 framework 拷贝件 `org.birdbro.framework.dict.service.impl.
  DictDetailServiceImpl` 默认 bean 名冲突（ConflictingBeanDefinitionException，scanBasePackages
  = org.birdbro 全根扫描所致）。按"管理控制器归 lumen-auth（对应 OPS manage 角色）"语义
  加 Manage 后缀重命名，无显式 bean name hack。
- **C. 提交范围**：工作区存在**本任务开始前**的未提交改动（lumen-sev/CLAUDE.md、8 个 auth
  实体 Lombok 组合、MenuTreeService、Org/OrgDep/Role/UserInfoServiceImpl 的 BeanUtil 重构、
  application.yml 的 flyway schemas 固定、spec-hygiene/loop-state/progress、未跟踪的
  openspec 提案目录与 system-data.zh.md）——均非本批内容，**未纳入本 commit**。其中
  AuthServiceImpl 与本批改动同文件：已将其中先前遗留的 toProfile BeanUtil 重构 hunk
  从本 commit 中剔除（提交版保留 HEAD 原 setter 写法，工作区已还原遗留改动）。
  ⚠️ 由此，commit 版与本次测试所跑工作区在 toProfile 内部实现上有功能等价的微差（字段
  逐一 setter vs BeanUtil 拷贝，字段集相同、ProfileDto 未变），风险极低；协调者合入遗留
  WIP 时套件会再次全量回归。
- **D. gitignored `application-dev.yml` 未触碰、未暂存**（本机已是 currentSchema=auth,public）。
- **E. 未动项确认**：未动 dict_city/DictMenu/DictCity；未动 openspec/specs/ 与 V1/V2/V3
  迁移；未加登录/登出以外新端点；openspec/changes/system-data/tasks.md 未勾选（该目录整体
  尚未入库，属协调者 propose 工件提交范围，避免单拎一个文件破坏提案完整性）。

## 文件清单（commit `33167c3`，24 files, +1556/−26）

新增（14）：
- `lumen-sev/framework/src/main/java/org/birdbro/framework/config/RequestContextConfig.java`
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/controller/{DictCatalController,
  DictDetailController,LogController}.java`
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/service/{DictCatalManageService,
  DictDetailManageService}.java`
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/service/impl/
  {DictCatalManageServiceImpl,DictDetailManageServiceImpl}.java`
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/{AuthAsyncPoll,AuthDictCrudTests,
  AuthLogQueryTests,AuthOperationLogTests,AuthSignLogTests}.java`

修改（10，均只含本批任务行）：
- `lumen-sev/framework/src/main/java/org/birdbro/framework/dict/service/IDictDetailService.java`
  （+listByCaCode）、`.../dict/service/impl/DictDetailServiceImpl.java`（实现）
- `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/ILogInfoService.java`
  （+getPage）、`.../log/service/impl/LogInfoServiceImpl.java`（实现）
- `lumen-sev/framework/src/main/resources/mapper/LogSignMapper.xml`（#{req}→#{req.account}）
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/controller/{OrgController,
  OrgDepController,RoleController,UserInfoController}.java`（@Log）
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/service/impl/AuthServiceImpl.java`
  （sign 接线；提交版剔除遗留 hunk，见偏差记录 C）
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthDataLayerTests.java`（4.1 扩充）

## 自审发现

- LogController 的 page/pageSize 为裸 @RequestParam（无 @Max(100)），与 PageSizeConfig 的
  约束体系不同——已用 Math.max(1,·) 下限兜底，上限未设（日志表只读、风险低），如需统一
  可后续对齐 PageSizeConfig。
- 更新接口对空白 code/label 的防御与 RoleServiceImpl 同款（!= null 判重），create 侧有
  isBlank 前置 A0400；与现有代码风格一致。
- LogInfoEntity 无 @TableLogic，framework 新增 getPage 用 `apply("deleted = 0")` 与拷贝
  XML 语义逐字对齐（Boolean 字段不会以 boolean 参数绑定 integer 列，规避 batch 1 偏差 C
  同款隐患）。
- 全量回归后 surefire 计数：50/0/0；探针测试与其报告已清理。

## 关注项（需协调者/后续裁定）

1. **RequestContextConfig 需登记为 framework 适配（建议 (8)）**：修复拷贝件登出链路 NPE，
   属"使拷贝件运行假设成立"的 framework 属主修复；文件与理由见上文。
2. **遗留 WIP 待归属**：工作区的前批未提交改动（实体 Lombok/BeanUtil 重构、application.yml
   flyway 固定等）仍留在树上，建议协调者确认归属后单独提交；本批 commit 已刻意隔离。
3. **测试 UA 处理**：sign/@Log 异步行依赖可解析的浏览器 UA（引擎列），测试请求已显式携带
   Chrome UA；若未来前端以非浏览器 UA 调用（如脚本），LogAspect 会在 try 内吞 NPE 导致该
   请求不落 log_info 行（不影响业务响应），属拷贝件既有行为，可在后续 batch 中加法式加固。
