# Batch 2 Brief — system-data（tasks 3.1, 3.2, 3.3, 3.4, 4.1–4.5）

## 环境铁律（与 Batch 1 相同）
- 每条 mvn 命令前：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
- 显式 `cd /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev`
- 测试 `./mvnw test -q`（团队测试服务器，`.env-test.local` 已配置）；显式回显 MVN_EXIT
- **异步写入轮询**：`AsyncManager` 异步落库——测试断言 log 行前用轮询循环
  （如 20 次 × 250ms 查库直至出现/超时），不要裸 sleep 一次就断言

## 前置状态（Batch 1 已交付，先读再写）
- `framework/dict/` 已有：DictCatalEntity/DictDetailEntity（继承 EntityBase）、
  DictCatalMapper/DictDetailMapper、IDictCatalService/IDictDetailService + 实现、
  pojo 子集、DictLabelHelper/DictReverseHelper
- V3 已建 `public.dict_catal/dict_detail/log_info/log_sign`；连接串已
  `currentSchema=auth,public`；种子 `common_status`（启用/停用）已在
- 写代码前**先读**这些文件的签名（service 方法名、pojo 字段、mapper 方法），
  控制器/测试严格按现有签名调用

## 任务 3.1 — 字典端点（lumen-auth/controller）
- `DictCatalController`：`/api/v1/auth/dicts/catal` — POST 创建、PUT /{id}、
  DELETE /{id}（逻辑删）、GET /list（分页或全量，按现有 service 签名取齐）
- `DictDetailController`：`/api/v1/auth/dicts/detail` — POST、PUT /{id}、DELETE /{id}、
  GET /list?caCode=（仅 live，按 sort）
- 返回值纪律照现有控制器（Integer/Boolean.TRUE——void 会落 "204" 信封分支）
- 重复 code 拒绝：服务层查重抛 A 类（从 `common/enums/ExceptionEnum` 选最贴合的——
  参考 A0714 数据重复的用法），部分唯一索引兜底
- 风格照 `OrgController`（javadoc 英文、@author birdbro、@Api/@ApiOperation 中文展示串
  ——否则 swagger 规格又空了）

## 任务 3.2 — 日志查询端点
- `LogController`：GET `/api/v1/auth/logs` — 参数 page/pageSize/type/uid/dateYm
  （后三可选），MyBatis-Plus `Page<LogInfoEntity>`，actionTime 倒序；走
  `ILogInfoService`/LogInfoMapper（如需新增分页查询方法，加在 framework log service
  属适配范围，报告记录）

## 任务 3.3 — `@Log` 接线
- 拷贝件注解 `common/annotation/Log.java`（先读其参数——title 等）
- 标注：OrgController/OrgDepController/UserInfoController/RoleController/Dict 两控制器的
  全部写方法（create/update/delete）；title 用中文展示串（如 "机构管理"）

## 任务 3.4 — 登录事件接线
- `AuthServiceImpl.login`：成功路径 + 失败路径各接
  `AsyncManager.me().execute(AsyncFactory.recordForSign(...))`（签名照
  `LogoutSuccessHandlerImpl` 的调用：account、Constants 里的事件常量（先 grep
  SIGN_IN/SIGN_OUT——失败用登出以外的恰当常量或数值，报告记录）、msg、descr、userLogin）
- 失败路径：login 的三个失败分支都 throw BusinessException——用 try/catch
  (BusinessException) 记录后 **rethrow**（信封行为不变）；userLogin 为 null 的失败场景
  （密码错/账号禁用）注意 recordForSign 对 null userLogin 的容忍度——先读
  AsyncFactory.recordForSign 源码，必要时构造仅含 account 的最小 UserLogin 并报告处理

## 任务 4.1–4.5 — 测试（先红后绿；红 = 端点/接线不存在时）
- 4.1 数据层往返：dict 实体 insert/select + information_schema 断言四表在 public
  （若 Batch 1 已建 AuthSystemDataMigrationTests 则扩充之）
- 4.2 字典端到端：登录 → 建目录 → 建明细 → 按 caCode 列表 → 重复 code A 类拒绝
- 4.3 日志查询：分页 + type/uid/dateYm 过滤 + 倒序
- 4.4 `@Log` 实测：调用一个带注解写端点 → 轮询 `public.log_info` 出现行（uid=操作者）
- 4.5 sign 实测：登录成功 + 密码错误各一条 `public.log_sign`（type/uid/account）；
  POST `/api/sign/out`（带 token）后登出行出现——**该端点当前会因 log_sign 不存在而
  异步失败，V3 后应落行**（此断言同时验证适配 (7) 的实效）
- 回归：既有 34 测试全绿（连接串已改，等价语义）

## Batch 1 复核交接（必须处理）

1. **LogSignMapper.xml:40 既有 `#{req}` 缺陷**：登录事件接线后该写路径变活——修掉
   （适配 (7) 范围，报告记录）；先读该 XML 与对应 mapper 接口的参数名再改
2. **list-by-caCode 需新方法**：DictDetailMapper/Service 的 getPage 按 caId 过滤——
   按 caCode 的明细列表需在 framework/dict 新增方法（加法式，不改既有方法——"Never
   modify existing public methods, use overloading"）
3. **CatalCreate.enable 是 Byte**（OPS 血统）：lumen 全线 Integer——控制器/DTO 适配时
   处理并报告
4. **环境陷阱**：改过 framework 后必须先
   `./mvnw -pl lumen-bom,framework,common install -DskipTests` 再 `-pl lumen-auth`
   跑测试——否则 ~/.m2 旧 jar 导致 test-compile 找不到 framework/dict

## 完成标准与提交
1. 全部红绿证据 + `./mvnw test -q` 全绿（MVN_EXIT=0，surefire 计数入报告）
2. 单条 commit：`✨ feat(sev): system-data batch 2 - dict/log endpoints + @Log + sign wiring (system-data 3.1-4.5)`
3. 报告：`openspec/sdd/reports/system-data-b2-report.md`（RED/GREEN 证据、文件清单、
   ExceptionEnum 选码与 SIGN 常量选型记录、偏差）

## 不要做
- 不动 `dict_city`/DictMenu/DictCity；不动 openspec/specs/；不加登录/登出以外的新端点；
  不改 V1/V2/V3 迁移（发现 DDL 与实体不匹配 → BLOCKED 上报，不要私自改迁移）
