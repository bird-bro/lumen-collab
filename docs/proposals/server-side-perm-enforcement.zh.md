# server-side-perm-enforcement 提案简报（中文导读）

> **免责声明**：本文是 `openspec/changes/server-side-perm-enforcement/` 的非权威中文
> 导读；英文 OpenSpec 工件（proposal/specs/design/tasks）才是唯一权威来源。

## 一、提案解释（为什么做）

权限的数据模型和数据流已经齐了（menu_info.perms → 角色授权 → /me 下发 → 前端
usePerm 门控），但**后端一个校验都没有**：任何拿到有效 token 的人可以调全部
`/api/v1/system/**` 接口。对多角色的医院部署这是安全空洞（客观查证：lumen-system
0 个 @PreAuthorize；UserLogin.getAuthorities() 返回 null；没有任何地方往会话里
setPermissions）。随访参照系同样没做这层——本提案是刻意超出参照的加固。

## 二、方案一句话

登录时把权限集写进 Redis 会话 → `getAuthorities()` 映射成 Spring Security
authorities → 七个控制器族类级 `@PreAuthorize` 按页面权限串校验 → 无权限统一回
`A0301`（访问未授权，复用既有枚举，HTTP 200 信封对齐 "203" 先例）。

关键约束：**复制的框架文件只动一行**（`UserLogin.getAuthorities()` 方法体），其余
全部是新写的 lumen 自有文件（`MethodSecurityConfig` + `AccessDeniedHandler`），符合
additive-first 演化纪律（记协调文档适配 8）。

## 三、涉及文件与角色

| 文件 | 角色 |
|:--|:--|
| `common/.../UserLogin.java` | 唯一被修改的复制文件：getAuthorities 映射 permissions（适配 8） |
| `lumen-system/.../AuthServiceImpl.java` | 登录时 setPermissions 入会话 |
| `lumen-system/.../config/MethodSecurityConfig.java`（新） | @EnableMethodSecurity |
| `lumen-system/.../security/AccessDeniedHandler`（新） | A0301 统一信封 |
| 七个 Controller 族 | 类级 @PreAuthorize（users/roles/menus/orgs/deps/dicts/logs） |
| 新测试类（权限矩阵） | RED→GREEN：无 token="203"、缺权="A0301"、admin=200、city=只认证 |
| specs delta | auth-rbac（3 个需求增补）+ errors（新增 A0301 响应要求） |

## 四、明确不做

按钮级（F 节点）粒度 —— 等工作站岗位角色落地，路线已记录；科室服务（尚不存在）；
超管通配（维持显式授权裁决）；前端改动（字符串两侧已一致）。

## 五、下一步

DP-3 审阅执行契约 → `/opsx:apply`（TDD 全流程，Batch Inline）→ verify → archive。
