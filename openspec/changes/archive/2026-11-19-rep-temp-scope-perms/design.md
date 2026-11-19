# Design

## Context

RepTempController 现状:类门控 `@PreAuthorize("hasAuthority('setting:temp:list')")` + 四个写端点静态按钮钉;拒绝信封由框架 `AuthDenyAdvice`(adaptation (13))渲染 A0301。服务层无既有 authority 助手(SecurityUtils 只有 UserHeader 通道);身份桥接 = UserHeaderBridgeFilter。种子先例 V28(F 行 NOT EXISTS + role 1 守护授权 + setval 尾);迁移编号 V41(V40 已被 number_rule_platform 占用)。前端 `usePerm()/hasPerm()` 读全局权限集(patient:rule:* 门控先例)。

## Goals / Non-Goals

**Goals:**
- 公共/个人 scope 键控权限族:endpoint admit-then-assert,服务层精确断言
- V41 种子补 4 个 public 按钮位 + admin 自动授权
- 前端 Segmented 显隐 + 默认 scope 翻转 + 动作键控
- 存量行为回归:持双族的用户(=admin)一切如旧

**Non-Goals:**
- 科室主任业务角色的实际绑定(部署期角色授权操作,非种子)
- 菜单管理界面/角色授权树的功能调整(权限位复用既有 F 节点机制)
- content 模型与双形态编辑器(正交,rep-template-structured-content 已归档)
- 其他 setting 域(检查项目/部位/方法)的 scope 拆分(它们无公共/个人维度)

## Decisions

- **D1 admit-then-assert**:三个写端点(POST/PUT/DELETE)的 `@PreAuthorize` 静态钉放宽为 `hasAnyAuthority('setting:temp:<op>', 'setting:temp:public:<op>')`——静态钉保留(防御纵深 + 既有按钮钉先例不破坏),精确族由服务层按 scope 断言。替代案「彻底移除静态钉」被否:丢掉控制器层第一道防线;「SpEL 按参数分支(create 用 #request.type)」被否:update/delete 需行 type,DB 查询先行,与 create 形成两套机制。
- **D2 服务层断言点**:`RepTempServiceImpl` 私有助手 `requireScopeFamily(type, op)`——`SecurityContextHolder.getContext().getAuthentication().getAuthorities()` 直读(项目无现成助手,单服务使用不值得进框架,additive-first 不动 SecurityUtils);create 在入参校验前断言(request.type 分支),update/delete 在 `requireOwnedRow` 之后按行 type 断言(顺序 = 先归属后族:非owner私行的既有 A0400 语义不变;公共行无归属概念,族断言即终态);`getTree`/`getPage` 在 type=1 时断言 `public:list`。拒绝 = `throw new AccessDeniedException("缺少公共模板权限: setting:temp:public:<op>")` → `AuthDenyAdvice` 渲染 A0301 专责信封(与 errors spec「authenticated-but-unauthorized」语义一致;content-free 不漏行)。
- **D3 V42 种子(编号顺延,V41 落地前被并行 number_rule 会话占用——apply 时按 design 顺延规则处理)**:4 个 F 节点(公共模板可见/新建公共模板/编辑公共模板/删除公共模板,`setting:temp:public:{list,create,update,delete}`,sort 4–7,up_id = 报告模板 C 行)NOT EXISTS 守卫 + role 1 守护授权(照抄 V28 内容守卫形态)+ setval 尾。public:list 也做成 F 节点:Segmented 显隐需要它作为可绑定位。
- **D4 前端键控**:`tempPerm(op) = scope === TYPE_PUBLIC ? 'setting:temp:public:' + op : 'setting:temp:' + op` 单点助手;Segmented options = [个人] + (hasPerm('setting:temp:public:list') ? [公共] : []),顺序保持 公共前个人?——现 UI 顺序 公共/个人,保持不变,仅过滤;`useState` 初值改惰性:`useState(() => hasPerm('setting:temp:public:list') ? TYPE_PUBLIC : TYPE_PRIVATE)`;scope 切换到不可用值不可达(options 已过滤)。所有动作的 hasPerm 调用换 tempPerm。编辑/删除对「当前加载行跨 scope」天然键控(行来自当前 scope 的树/表)。
- **D5 测试矩阵**:后端 perm 测试四象限(仅基本族/仅公共族/双族/无)×(公共写/公共读/个人写);复用 AuthRepTempPermPinTests idiom(MockMvc + 会话权限集);前端三场景(options 过滤/默认翻转/键控显隐)+ 存量 26 用例回归(权限集 fixtures 补 public:list 保持旧路径绿)。

## Risks / Trade-offs

- **admin 之外现存量用户**:今天所有持 `setting:temp:*` 的用户失去公共写能力(语义收窄的本意);部署后需给科室主任绑 4 个新权限位(部署手册/交接说明提示)。
- **D2 顺序选择**:先归属后族意味着「无公共族的用户探测公共行 id」得到 A0301(行存在性经由族拒绝不可区分——content-free 达标);非 owner 私行仍 A0400,两码并存语义清晰。
- **SecurityContextHolder 直读**:测试里 MockMvc 的 authorities 装配必须与生产同源(既有 perm 测试已如此),无新框架面。
