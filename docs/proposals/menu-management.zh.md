# menu-management 提案简报（中文导读）

> **免责声明**：本文是跨栈特性 `menu-management`（`openspec/coordination/menu-management.md`
> + `openspec/changes/menu-management-{api,web}/`）的非权威中文导读；英文 OpenSpec
> 工件才是唯一权威来源。

## 一、提案解释（为什么做）

菜单树是整个权限体系的骨架（role_menu 授权 → /me 下发 perms → 前端 usePerm 门控 +
服务端 @PreAuthorize），但目前它**只能靠 Flyway 种子塑形**：后端只有 `GET /menus/tree`
一个读端点，前端菜单页是只读树。每次 IA 调整都要写迁移；随访配方里 1:1 就绪的
按钮级权限（F 节点 + perms）一直没有落地入口。本次把菜单页升级为完整管理面：
目录/菜单/按钮三类节点的增删改 + 按钮权限串的录入。

四个范围裁决（2026-11-05 拍板，见协调文档 Decisions）：

1. **工作站模式不进本次变更**——工作站是会话级激活台（三层正交装配），不是菜单层级；
   菜单模型不动，阶段目录（登记/检查/诊断）未来通过普通 CRUD 建树即可。
2. **按钮权限串自由文本** + `模块:资源:动作` 约定，服务端维持页面级门控
   （`system:menu:list` 管整个家族，按钮级由各端点渐进采用）。
3. **删除语义**：有存活子节点 → 阻止（A0400）；否则软删节点 + 软清 role_menu 绑定
   （与删角色清绑定同款纪律）。
4. **新建自动授权超管**（role 1）——lumen 用显式授权不用通配，不自动授权超管就
   看不到新节点；UPDATE 不碰授权。

## 二、方案一句话

后端 `MenuController` 增配 POST/PUT/DELETE 三端点 + 新 `MenuManageService`
（内存 guard map 做 F 叶子/环/父存在校验，两处 `@Transactional`：创建=插节点+守卫式
超管授权，删除=软删+清绑定），前端菜单页从只读 `Tree` 换成 `Table` 树表格 +
类型感知的新建/编辑弹窗（按钮型给 perms 输入），走既有 `usePerm` 门控与
`ApiError.msg` 报错通道。

## 三、涉及文件与角色

### 后端 change：`menu-management-api`

| 文件 | 角色 |
|:--|:--|
| `lumen-system/.../dto/CreateMenuRequest.java`、`UpdateMenuRequest.java`（新） | `@Valid` 请求体，Bean 字段 1:1 |
| `lumen-system/.../service/MenuManageService.java`（新） | CRUD + guard map + 自动授权/清绑定，`BusinessException(A0400)` 拒绝 |
| `lumen-system/.../controller/MenuController.java` | 只增方法不改旧方法（类级 @PreAuthorize 已覆盖） |
| 对应测试（新） | TDD 红绿：guard 场景 / 授权语义 / 信封透传 |

### 前端 change：`menu-management-web`

| 文件 | 角色 |
|:--|:--|
| `src/api/types.ts` | + `CreateMenuRequest`/`UpdateMenuRequest` |
| `src/api/menus.ts` | + `createMenu`/`updateMenu`/`removeMenu`（只增不改） |
| `src/pages/system/menus/index.tsx` | 页内重写：Table 树表格 + 弹窗 + Popconfirm 删除 |
| `src/test/menus-page.test.tsx` | 重写：渲染/CRUD 往返/拒绝透传/父选择守卫/无权限隐藏 |

规格 delta：后端改 `api` + `auth-rbac`，前端改 `web-system-console`
（REMOVED 只读需求 + ADDED 管理需求）。无数据库迁移——模型零变更。

## 四、明确不做

- 工作站（类型化枚举、用户主台/多台、/me 载荷、切台重过滤）——独立待落 change
- perms 格式校验/唯一约束、拖拽排序、图标选择器、path/component 路由下拉
- 按钮级服务端强制（F 节点 perms 仍只进权限集，由端点渐进采用）

## 五、下一步

1. 审阅两份工件（`openspec/changes/menu-management-api/`、`.../web/`）
2. `/opsx:apply menu-management-api`——后端先行（TDD，任务 1→4）
3. 后端落地用户验证后：`/opsx:apply menu-management-web`（真后端联调，
   `pnpm dev` 走 vite 代理）
4. 两边 verify PASS → 各自 `/opsx:archive`（规格同步在归档时做），协调文档注册表收口
