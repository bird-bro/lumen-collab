# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: rep-temp-scope-perms
- **Problem to solve**:
报告模板页的公共/个人 scope 目前共用一套按钮权限(`setting:temp:*`)——任何能进页面的用户都能创建/修改/删除**公共**模板(科室共享资产)。用户拍板(2026-11-18):不拆页面,用 **scope 键控按钮权限族**控制——公共模板仅持有公共权限族的用户(如科室主任)可写,个人模板人人可管自己的。

<!-- AI: summarize the above into 1-2 sentences -->
- **Summary**: 报告模板 scope 键控权限族拆分——个人=既有 setting:temp:* 收窄,公共=新增 setting:temp:public:* 四位;endpoint admit-then-assert(静态钉放宽 hasAnyAuthority,服务层按 request/行 type 精确断言,缺族 A0301),V41 种子补 4 F 节点+admin 自动授权,前端 Segmented 显隐/默认翻转/动作键控。
- **In scope**:
  - 后端:V41 迁移、requireScopeFamily 助手 + getTree/getPage/create/update/delete 接线、控制器 hasAnyAuthority 钉、perm 矩阵测试
  - 前端:tempPerm 键控助手、Segmented 过滤+默认惰性化、全部动作键控、fixtures 变体与回归
- **Out of scope**:
  - 科室主任业务角色绑定(部署期角色授权);菜单管理界面调整
  - content 模型/双形态编辑器(rep-template-structured-content 已归档,正交)
  - 其他 setting 域的 scope 拆分(无公共/个人维度);errors spec delta(A0301 复用)

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
5:### Requirement: Report Template Endpoints
21:#### Scenario: Tree returns both scopes independently
28:#### Scenario: Template page under a folder
34:#### Scenario: Template payloads carry the content object
40:#### Scenario: Static pins admit either family
47:### Requirement: Report Template Scope Enforcement
64:#### Scenario: Non-owner private write refused
69:#### Scenario: Private create binds the owner
75:#### Scenario: Public write without the public family refused
81:#### Scenario: Public write with the public family succeeds
87:#### Scenario: Public read without public:list refused
- `specs/system-data/spec.md`
5:### Requirement: Report Template Scope Button Perms
19:#### Scenario: Admin gains the public family without manual grant
- `specs/web-system-console/spec.md`
5:### Requirement: Report Template Page
34:#### Scenario: Tab toggle switches scope without cross-leak
40:#### Scenario: Directory node creates a child directory
46:#### Scenario: Section mode edits sections and measures
53:#### Scenario: Mode switch converts prose to sections
59:#### Scenario: Structured to prose conversion refused
64:#### Scenario: Structured row renders summary cells
70:#### Scenario: 公共 option hidden without the public list perm
77:#### Scenario: 公共 scope actions consult the public family
86:#### Scenario: 个人 scope actions keep the base family


## Design Constraints

- **Architecture/Decisions**:
- **D1 admit-then-assert**:四个写端点的 `@PreAuthorize` 静态钉放宽为 `hasAnyAuthority('setting:temp:<op>', 'setting:temp:public:<op>')`——静态钉保留(防御纵深 + 既有按钮钉先例不破坏),精确族由服务层按 scope 断言。替代案「彻底移除静态钉」被否:丢掉控制器层第一道防线;「SpEL 按参数分支(create 用 #request.type)」被否:update/delete 需行 type,DB 查询先行,与 create 形成两套机制。
- **D2 服务层断言点**:`RepTempServiceImpl` 私有助手 `requireScopeFamily(type, op)`——`SecurityContextHolder.getContext().getAuthentication().getAuthorities()` 直读(项目无现成助手,单服务使用不值得进框架,additive-first 不动 SecurityUtils);create 在入参校验前断言(request.type 分支),update/delete 在 `requireOwnedRow` 之后按行 type 断言(顺序 = 先归属后族:非owner私行的既有 A0400 语义不变;公共行无归属概念,族断言即终态);`getTree`/`getPage` 在 type=1 时断言 `public:list`。拒绝 = `throw new AccessDeniedException("缺少公共模板权限: setting:temp:public:<op>")` → `AuthDenyAdvice` 渲染 A0301 专责信封(与 errors spec「authenticated-but-unauthorized」语义一致;content-free 不漏行)。
- **D3 V41 种子**:4 个 F 节点(公共模板可见/新建公共模板/编辑公共模板/删除公共模板,`setting:temp:public:{list,create,update,delete}`,sort 4–7,up_id = 报告模板 C 行)NOT EXISTS 守卫 + role 1 守护授权(照抄 V28 内容守卫形态)+ setval 尾。public:list 也做成 F 节点:Segmented 显隐需要它作为可绑定位。
- **D4 前端键控**:`tempPerm(op) = scope === TYPE_PUBLIC ? 'setting:temp:public:' + op : 'setting:temp:' + op` 单点助手;Segmented options = [个人] + (hasPerm('setting:temp:public:list') ? [公共] : []),顺序保持 公共前个人?——现 UI 顺序 公共/个人,保持不变,仅过滤;`useState` 初值改惰性:`useState(() => hasPerm('setting:temp:public:list') ? TYPE_PUBLIC : TYPE_PRIVATE)`;scope 切换到不可用值不可达(options 已过滤)。所有动作的 hasPerm 调用换 tempPerm。编辑/删除对「当前加载行跨 scope」天然键控(行来自当前 scope 的树/表)。
- **D5 测试矩阵**:后端 perm 测试四象限(仅基本族/仅公共族/双族/无)×(公共写/公共读/个人写);复用 AuthRepTempPermPinTests idiom(MockMvc + 会话权限集);前端三场景(options 过滤/默认翻转/键控显隐)+ 存量 26 用例回归(权限集 fixtures 补 public:list 保持旧路径绿)。
- **Technical constraints**:
  - 跨域禁令:任务 1.x 只动 lumen-sev,任务 2.x 只动 lumen-web
  - 拒绝信封:服务层抛 AccessDeniedException → AuthDenyAdvice A0301(content-free);不得新造错误码
  - 种子纪律:V41 照抄 V28 形态(NOT EXISTS/role 1 守护/setval 尾);迁移编号 V41(V40 已占)
  - SettingSchemaSeedTests 可能仍在他会话脏集(上 change M2 先例)——冲突时最小行数原则
  - 共享测试库窗口纪律;前端 26 存量用例为回归底线(双族用户行为不变)

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 `V41__rep_temp_public_perms.sql`(V28 纪律):4 个 F 节点(公共模板可见/新建公共模板/编辑公共模板/删除公共模板,`setting:temp:public:{list,create,update,delete}`,sort 4–7,up_id=报告模板 C 行)NOT EXISTS 守卫 + role 1 守护授权 + setval 尾
6:- [ ] 1.2 `RepTempServiceImpl.requireScopeFamily(type, op)` 助手(SecurityContextHolder authorities 直读,缺族抛 AccessDeniedException 命名缺失权限串)+ 接线:getTree/getPage type=1 断言 public:list、create 按 request.type、update/delete 按 requireOwnedRow 行 type;TDD 先行(perm 矩阵:仅基本族公共写/读 → A0301,仅公共族公共写成功且 owner_uid NULL,双族全通,非 owner 私行仍 A0400)
7:- [ ] 1.3 `RepTempController` 四个写端点静态钉放宽 `hasAnyAuthority(基本族, 公共族)`(类门控不动);TDD:仅公共族用户过静态钉
8:- [ ] 1.4 seed 测试:SettingSchemaSeedTests 补菜单断言(若其结构覆盖 F 行计数/权限位——注意该文件可能仍在他会话脏集,冲突时最小行数原则)或独立 V41 断言测试
12:- [ ] 2.1 `tempPerm(op)` 键控助手 + Segmented options 按 `hasPerm('setting:temp:public:list')` 过滤 + 初始 scope 惰性化(无公共权限默认个人);TDD 先行(三场景:隐藏/默认翻转/键控显隐)
13:- [ ] 2.2 全部动作 hasPerm 换 tempPerm(新建目录/新建子目录/编辑目录/删除节点/添加模板/表格编辑与删除);权限 fixtures 补 `setting:temp:public:*` 变体,存量 26 用例回归绿(双族用户行为不变)
14:- [ ] 2.3 个人 scope 无公共权限用户的端到端形态:Segmented 单选项、无公共树/表请求、动作键控基本族(对照 delta 场景「公共 option hidden」)
18:- [ ] 3.1 后端 reactor 全量(共享测试库窗口纪律)+ 前端 vitest 全量
19:- [ ] 3.2 WHEN/THEN 对照:exam-setting delta 8 场景 + web-system-console delta 9 场景 + system-data delta 1 场景逐条核验,verify.md 落 L1/L2/L3

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**:
  - 后端 perm 矩阵:仅基本族 → 公共写/公共读 A0301;仅公共族 → 公共写成功 owner_uid NULL + 过静态钉;双族全通;非 owner 私行仍 A0400(exam-setting delta 5 场景)
  - 前端:Segmented 过滤/默认惰性翻转/动作键控显隐(web-system-console delta 3 新场景)
  - 种子幂等:V41 重放不重复插(system-data delta 场景)
- **Required edge cases**:
  - 仅公共族用户 create type=2(个人写需基本族 → 拒);public:list 持有但无 public:create(公共 scope 只读)
  - SettingSchemaSeedTests 脏集冲突的最小合流
- **Regression-sensitive areas**:
  - 双族(=admin)用户端到端行为不变;存量 26 前端用例;既有 AuthRepTemp 三套件;归属校验语义(非 owner A0400)不变

## Execution Mode

- **Mode**: `SDD`
- **Selection rationale**: 8 任务跨栈(backend+web)+ 迁移/权限面;并行会话在场(脏集/共享测试库风险指标)——沿用上一 change 的 SDD 批次节奏(实现→review→下一批),批次 1 后端 / 批次 2 前端 / 批次 3 验证。

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
