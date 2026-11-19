# Proposal: rep-temp-scope-perms

## Why

报告模板页的公共/个人 scope 目前共用一套按钮权限(`setting:temp:*`)——任何能进页面的用户都能创建/修改/删除**公共**模板(科室共享资产)。用户拍板(2026-11-18):不拆页面,用 **scope 键控按钮权限族**控制——公共模板仅持有公共权限族的用户(如科室主任)可写,个人模板人人可管自己的。

## What Changes

- **权限族拆分**:现有 `setting:temp:create/update/delete` 语义收窄为「个人」scope;新增 `setting:temp:public:list/create/update/delete` 管「公共」scope;`setting:temp:list` 保持页面级类门控不变。
- **后端(lumen-system setting 域)**:`RepTempServiceImpl` 服务层 scope 键控守卫——create 按请求 `type` 分支、update/delete 按 `requireOwnedRow` 行 type 分支断言对应权限族(个人写仍叠加既有归属校验,顺序 = 先族后归属);`getTree`/`getPage` 在 `type=1`(公共读)时要求 `public:list`(type=2 只需页面级 list)。控制器变更:三个写端点(POST/PUT/DELETE)的静态钉放宽为 `hasAnyAuthority(基本族, 公共族)`( admit-then-assert——静态钉保留防御纵深,精确族由服务层按 scope 断言),类门控不动。
- **种子(V42,V28 纪律;V41 被并行 number_rule 会话占用顺延)**:「报告模板」菜单下补 4 个 F 按钮(公共模板可见/新建公共模板/编辑公共模板/删除公共模板,sort 4–7,NOT EXISTS 守卫)+ 系统管理员(role 1)守护式自动授权 + setval 尾;科室主任等业务角色 = 部署期角色绑定,不落种子。
- **前端(lumen-web)**:Segmented 公共选项按 `hasPerm('setting:temp:public:list')` 显隐(无公共权限者只见个人模板库,默认 scope 随之翻转为个人);全部模板动作(新建目录/新建子目录/编辑目录/删除节点/添加模板/表格编辑删除)按当前 scope 取对应族字符串。
- **错误面**:缺公共权限的拒绝复用既有 **A0301**(authenticated-but-unauthorized),服务层抛 `AccessDeniedException` 走框架 `AuthDenyAdvice` 专责信封——errors spec 无需 delta。
- **不在范围**:与 rep-template-structured-content(已归档)正交——不动 content 模型/双形态编辑器;科室主任角色的实际绑定(部署期操作);菜单管理界面的调整。

## Capabilities

### New Capabilities

(无——权限族是 exam-setting 端点执法与 web 页面门控的扩展,种子落 system-data)

### Modified Capabilities

- `exam-setting`:MODIFIED「Report Template Endpoints」(写端点 admit-then-assert 的族门控)与「Report Template Scope Enforcement」(公共写需公共族、公共读需 public:list)。
- `web-system-console`:MODIFIED「Report Template Page」(Segmented 显隐/默认 scope/动作键控)。
- `system-data`:ADDED「Report Template Scope Button Perms」(V41 种子 + admin 自动授权)。
