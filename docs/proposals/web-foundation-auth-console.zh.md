# web-foundation-auth-console 提案说明（中文概览）

> **非权威文档声明**：本文为中文导读，仅供快速理解。权威内容以 `openspec/changes/web-foundation-auth-console/`
> 下的英文 proposal / specs / design / tasks / execution-contract 为准；两者冲突时以英文规格为准。
>
> 生成日期：2026-11-03 · 阶段：`proposing`（等待评审批准，尚未进入 apply）

---

## 1. 这个提案在做什么

后端基座已就绪（`lumen-system` 登录/RBAC + system-data 字典/日志均已实现、验证并归档为规格），
但 `lumen-web/` 还只是一个空壳脚手架：占位路由、写死的导航、临时青色主题。前端开发现在必须启动，
并且按你的要求**以 OPS 参考项目（`ops_web`）的 UI 样式为视觉基准**——MiSans 字体、18px 字号基准、
深蓝配色（`#3178fc` 主色 / `#003676` 导航）、60px 头部 + 160px 侧栏——让产品继承一套经过实战检验
的外观，而不是自行发明。首个与就绪后端配对的功能切片是：**登录认证流程 + 系统管理控制台**
（机构树 / 科室 / 用户 / 角色 / 菜单 / 字典 / 操作日志）。

### 你已拍板的三项决策（2026-11-03 头脑风暴）

| 决策项 | 你的选择 | 影响 |
|---|---|---|
| 首个前端范围 | **主题基座 + 登录 + 系统管理** | 7 个控制台页面，API 全部 mock（对齐规格，为接通 lumen-system 做准备） |
| 主题色 | **完全采用 OPS 配色** | 撤销原规格的青色 `#0E7C86` 决策（D6），改用 OPS 深蓝；避红色原则保留（红色留给临床告警） |
| 字体 | **打包 MiSans 字体文件** | 从 OPS 拷贝 Regular/Medium/Bold（免费可再分发）；**不打包 MSYH.TTF**（微软雅黑有授权限制） |

### 需要注意的一个后端缺口

角色管理页需要 `GET /api/v1/system/roles/list`，但后端 `RoleController` 目前**没有** list 端点
（只有 create/update/delete/菜单分配）。本变更的 API 规格增量已如实声明该契约：前端按规格 mock，
后端以一个小的后续变更实现它（见 §5 下一步建议）。

---

## 2. 规格影响（capabilities）

| 类型 | 能力路径 | 内容 |
|---|---|---|
| 新增 | `specs/web-design-system/spec.md` | MiSans 打包、18px 字号体系（16/18/20）、OPS 配色 tokens、布局度量（60px/160px）、hex 只进 token 模块 |
| 新增 | `specs/web-auth-flow/spec.md` | 登录页行为、JWT 会话、路由守卫（含信封码 `203` 处理）、`/me` 动态菜单 + 按钮级 perms 门控、退出登录 |
| 新增 | `specs/web-system-console/spec.md` | 7 个管理页面的交互契约：orgs / deps / users / roles+菜单分配 / menus / dicts / logs |
| 修改 | `specs/api/spec.md` | 补记 lumen-system **已实现**的管理端点（users/orgs/org-deps/roles/menus）；声明**未实现**的 `roles/list` 缺口 |
| 修改 | `specs/web-app-shell/spec.md` | Design Token Theming 改为 OPS 配色（取代青色 D6）；新增 Dev Mock Server 要求；Module Layout 补充平台级页面位置 |

---

## 3. 文件路径与角色（实施时落点）

```
lumen-web/
├── src/theme/
│   ├── fonts/                  ← MiSans TTF（从 OPS 拷入，MSYH 不拷）
│   ├── fonts.css               ← @font-face 400/500/700 + 回退字体栈
│   └── tokens.ts               ← OPS 配色 + 18px + fontFamily（全项目唯一 hex 所在地）
├── src/api/                    ← 传输客户端（fetch 封装：Bearer 注入、信封解包、203 信号）+ 各资源 typed client
├── src/stores/                 ← zustand auth store（token/profile/菜单树/perms，localStorage 持久化）
├── src/layouts/BasicLayout.tsx ← 60px 头部 + 160px 侧栏 + 动态菜单（NAV_ITEMS 删除）
├── src/router.tsx              ← /login 独立路由 + RequireAuth 守卫 + /system/** 控制台路由
├── src/pages/login/            ← 登录页（OPS 居中卡片式）
├── src/pages/system/{orgs,deps,users,roles,menus,dicts,logs}/  ← 控制台 7 页
├── mock/                       ← vite-plugin-mock-dev-server：按规格逐资源 mock，数据对齐后端种子
└── package.json                ← 精确锁版本：vite-plugin-mock-dev-server / vitest / @testing-library/* / zustand
```

支撑文件（本仓库内）：`openspec/changes/web-foundation-auth-console/`（proposal、5 份规格增量、
design、tasks、execution-contract）· `openspec/loop-state.yaml`（已指向本变更，`execution_mode: SDD`）。

---

## 4. 实施流程计划（SDD，7 个批次 / 19 个任务）

1. **依赖与工具**（1.1）：mock 插件 + Vitest + Testing Library + zustand，`pnpm test` 接线
2. **设计系统基座**（2.1–2.3）：字体拷贝 → tokens 重写（hex 纪律测试先行）→ BasicLayout 度量
3. **Mock 服务器与种子对齐夹具**（3.1）：覆盖全部已文档端点，数据镜像后端种子（admin uid 1、医院根节点、三科室、common_status）
4. **传输客户端与会话状态**（4.1–4.3）：fetch 封装（203 按 HTTP 200 信封码识别）→ auth store → typed clients
5. **UI 原型 + 认证流 UI**（5.1–5.4）：**先跑 `frontend-design` skill 出原型，你确认后**再实现登录页、守卫、动态菜单
6. **控制台 7 页**（6.1–6.7）：每页 client + 页面 + 测试（orgs/deps 用左侧机构树 + 右侧工作区模式）
7. **集成验证**（7.1–7.3）：全套测试 + 无后端 mock 冒烟走查 + 设计系统四场景核对

关键设计决策（详见英文 design.md）：D1 颜色全部走 AntD token 映射表 · D2 字体原样拷 TTF（woff2
子集化为后续优化）· D3 mock 插件选型（Vite 7 兼容性存疑时回退 MSW）· D4 自研轻量 fetch 封装（不引 axios）
· D5 zustand + localStorage · D6 RequireAuth + `/me` 刷新 · D7 登录页去掉 OPS 的隐私协议勾选和忘记密码
（无对应端点/产品需求）· D8 控制台页面结构与 OPS 模式对齐 · D9 Vitest TDD（单元测试 vi.mock 桩掉 api 缝隙）。

---

## 5. 下一步行动建议

1. **评审批准**（DP-3 门）：确认 execution-contract 的意图锁、范围、SDD 模式后，明确批准进入 apply
2. **批准后**：运行 `/opsx:apply web-foundation-auth-console`（guard.sh 通过后 phase 切 `applying`）
3. **apply 内任务 5.1**：`frontend-design` skill 出 UI 原型 → **你确认** → 再写页面代码
4. **并行/后续（后端小变更）**：实现 `GET /api/v1/system/roles/list` 到 api 增量场景（角色页先由 mock 供数，不受阻塞）
5. **收尾**：`/opsx:verify` L2 校验 WHEN/THEN → 全部 PASS 后 `/opsx:archive`
