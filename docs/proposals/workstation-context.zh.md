# workstation-context 提案简报（中文导读）

> **免责声明**：本文是跨栈特性 `workstation-context`（`openspec/coordination/workstation-context.md`
> + `openspec/changes/workstation-context-{api,web}/`）的非权威中文导读；英文 OpenSpec
> 工件才是唯一权威来源。

## 一、提案解释（为什么做）

患者域决策 #8 的**三层正交装配**（deployment × workstation × role_menu）已完成三分之二：
role_menu 随权限地基落地，deployment 是运行事实——唯独 **workstation（科室工作站的会话级
激活台）缺失**。科室页面（放射登记/检查/报告）一开工就会撞上：一个人被授权多个科室时会
同时看到所有科室的页面。用户 2026-11-06 拍板：全栈闭环 + 含授权 UI，一次落完。

## 二、方案一句话（2026-11-06 修订：工作站 = 菜单树 L1 分组）

后端新增 `user_workstation` 表（类型化枚举 admin/radiology/us/endo + 主台偏唯一索引，
V21）+ `PUT /users/{id}/workstations` 全量替换授权端点（钉新按钮权限
`system:user:assign-ws`，F 节点走 V15 内容守卫纪律）+ login/me 载荷扩展
（`workstations` + `profile.primaryWorkstation`，零行用户回落 `["admin"]`）；
**menu_info 加 nullable workstation 列（仅 L1 菜单有值：系统管理=admin，放射/超声/
内镜为 V21 预置占位 L1）——工作站是 L1 菜单的归属字段 + 菜单管理页按台分组渲染（分组
标题行在 L1 之上，非菜单节点），管理员无需切台改菜单**；前端 zone 2 标题左侧切台 chip，
**侧栏为追加语义（用户 2026-11-06 描述）：admin 域 L1 常驻，选中某科室台后该台 L1
（登记…）追加到系统管理之下并列显示，选回控制台即恢复现状**；用户管理页加授权抽屉
（勾台 + 设主台）。

> 修订缘由：初稿用"path 前缀域派生"，用户指出管理员会被迫切台维护菜单——2026-11-06
> 裁决工作站升级为菜单树 L1 分组（推翻 2026-11-05"工作站不进菜单树"的旧读法），原型
> `docs/prototypes/workstation-context.html` 已同步。

## 三、涉及文件与角色

### 后端 change：`workstation-context-api`

| 文件 | 角色 |
|:--|:--|
| `db/migration/V21__user_workstation.sql`（新） | user_workstation 表（FK + is_default 偏唯一）+ assign-ws F 节点（内容守卫 + 超管授权） |
| `entity/UserWorkstation.java`、mapper、DTO（新） | 关联表实体（Lombok 组合 + @TableLogic）、`@Pattern` 枚举校验 |
| `service/WorkstationService.java`（新） | 载荷装配（单查询）+ 全量替换授权（事务 + 双主台 A0400） |
| `UserInfoController` | +1 端点（方法级钉 assign-ws，additive） |
| `AuthServiceImpl` / `MeResponse` / `LoginResponse` / `ProfileDto` | 载荷字段扩展（additive） |
| 测试 | 表/种子/载荷/授权语义全套（含用户删除级联清行） |

### 前端 change：`workstation-context-web`

| 文件 | 角色 |
|:--|:--|
| `src/config/workstations.ts`（新） | 类型化台枚举 + 元数据（label/描述/图标/落地路径）+ 路由域映射（编译期锁死） |
| `src/stores/auth.ts` | + `activeWorkstation`（登录落主台、setter 切换、re-login 重置） |
| `src/layouts/BasicLayout.tsx` | zone 2 标题左切台 chip（Dropdown，有授权台才渲染） |
| 侧栏过滤 | admin 短路=现状不变；科室台按注册域过滤（v1 只有控制台菜单时机制空转） |
| `src/pages/system/users/index.tsx` | 授权抽屉（勾台+主台 radio），`system:user:assign-ws` 门控，双主台报错内联 |

## 四、明确不做

- menu_info 不加工作站字段（过滤在客户端按路由域——既定裁决）
- `remote` 台（v2 随 RCP）、科室页面路由（尚不存在）、按台主题色（v2 可选）
- 迁移回填（零行用户回落 admin，存量账户零影响）

## 五、下一步

1. 审阅两份工件（重点：coordination 的切台语义与 Bootstrap 回落）
2. `/opsx:apply workstation-context-api`——后端先行
3. 后端落地验证后：`/opsx:apply workstation-context-web`
4. 双 verify PASS → 归档闭环；此后放射科业务页面开工时，工作站骨架已经就位

---

## 重大修订（2026-11-06 晚：用户裁决"你完全彻底的做错了"）

**per-user 直授设计（user_workstation 表 + 授权端点 + 抽屉）全部作废**——菜单权限只能
给角色，用户绑角色。工作站改为 **menu_info.type = 'W' 的 L1 菜单节点**：

- 修正 change：`workstation-w-nodes-api`（V22 拆除错误机制 + 种子三个 W 节点） +
  `workstation-context-web` 回退重做（chip 数据源=树上的 W 节点；授权抽屉删除——授权
  走角色管理的菜单授权树）
- 主台概念随之删除（无 per-user 存储）：台选择是会话态，登录默认控制台视图，仅授权
  一个台时自动激活
- 本简报上文关于 user_workstation/assign-ws/主台的描述全部作废，以本节为准
