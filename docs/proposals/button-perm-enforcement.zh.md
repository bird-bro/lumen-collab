# button-perm-enforcement 提案简报（中文导读）

> **免责声明**：本文是跨栈特性 `button-perm-enforcement`（`openspec/coordination/button-perm-enforcement.md`
> + `openspec/changes/button-perm-enforcement-{api,web}/`）的非权威中文导读；英文 OpenSpec
> 工件才是唯一权威来源。

## 一、提案解释（为什么做）

menu-management 特性（2026-11-06 归档）交付了按钮权限的**管理面**——菜单页可建/改/删
F 节点及权限串、角色可勾、登录/me 下发——但实测共享库 **F 节点为 0**（树里只有
5 目录 + 8 菜单），且所有控制台写端点仍只挂页面级 `system:*:list` 类注解，前端所有
按钮也整体挂在 `hasPerm('system:*:list')` 一个开关上。按钮权限是"空跑道"。

本提案把按钮级权限**端到端落地**：种子（22 个 F 节点）→ 服务端方法级钉 → 前端按
钮级门控，一次闭环。

## 二、方案一句话

`V15` 迁移按 coordination 清单插入 22 个按钮 F 节点（用户 3 / 角色 4 / 菜单 3 /
机构 3 / 科室 3 / 字典 6，超管自动授权 + 幂等守卫），7 个控制器的 21 个写端点
钉方法级 `@PreAuthorize`（**方法注解取代该方法上的类级页面权限**——按钮权限即动作
权限，页面权限管可见性），前端六个管理页的每个按钮改按各自 perms 的 `usePerm`
渲染（操作列仍按页面权限）。

## 三、涉及文件与角色

### 后端 change：`button-perm-enforcement-api`

| 文件 | 角色 |
|:--|:--|
| `db/migration/V15__button_perm_nodes.sql`（新） | 22 个 F 节点 + 超管授权，V5 幂等纪律（显式 id + ON CONFLICT + NOT-EXISTS + setval） |
| 7 个 Controller（UserInfo/Role/Menu/Org/OrgDep/DictCatal/DictDetail） | 21 个写端点加方法级 `@PreAuthorize`（只加注解不改签名，sign-log b2 先例） |
| `AuthPermEnforcementTests` | 按钮级权限矩阵：仅页面权限→写拒绝 A0301；仅按钮权限→写放行、读仍拒；管理员全放行 |

### 前端 change：`button-perm-enforcement-web`

| 文件 | 角色 |
|:--|:--|
| 6 个管理页（users/roles/menus/orgs/deps/dicts） | 每个按钮从整体 `canManage` 改为按各自 perms 的 `hasPerm`（操作列保持页面权限门控） |
| 6+ 个测试文件 | 既有用例的权限集补齐按钮串 + 新增"未授权隐藏/授权显示"用例 |

关键语义（design D2）：`新建机构`/`新建根机构` 共用 `system:org:create`，`新建根节点`/
`新增下级` 共用 `system:menu:create`；字典双面板拆 `dict-catal`/`dict-detail` 两个
资源；日志页只读无按钮。

## 四、明确不做

- 菜单模型/端点契约变更（零）· 工作站耦合 · 日志页按钮
- `hasAnyAuthority` 组合表达式（否决：会退化回页面级粒度）

## 五、下一步

1. 审阅两份工件（`openspec/changes/button-perm-enforcement-api/`、`.../web/`）——
   **重点看 coordination 清单表**（perms 命名与动作的 1:1 映射是两栈的单一事实源）
2. `/opsx:apply button-perm-enforcement-api`——后端先行（V15 + 21 个钉 + 矩阵测试）
3. 后端落地验证后：`/opsx:apply button-perm-enforcement-web`（真后端联调）
4. 双 verify PASS → 各自 `/opsx:archive`，跨栈闭环

**运维提示**：上线后非超管角色将**看不到未授权的按钮**（这正是目的）；管理员由种子
自动授权，其余角色需在角色管理里按需勾选 F 节点。
