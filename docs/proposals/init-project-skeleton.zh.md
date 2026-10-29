# init-project-skeleton 提案说明（中文概览）

> **非权威文档声明**：本文为中文导读，仅供快速理解。权威内容以 `openspec/` 下的英文
> proposal / specs / design / tasks / execution-contract 为准；两者冲突时以英文规格为准。
>
> 生成日期：2026-10-29 · 阶段：`proposing`（等待评审批准，尚未进入 apply）

---

## 1. 这个提案在做什么

`lumen-sev/` 和 `lumen-web/` 目前只有角色文件（`CLAUDE.md` / `AGENTS.md`），没有 `pom.xml`、
没有 `package.json`、没有任何可编译或可构建的东西；`docker/` 目录还不存在。所有 v1 功能
（患者、放射科、集成）都需要一个能落脚的工程骨架。

本次变更建立这个骨架，深度由你选定为 **「可编译空壳」**：模块目录、构建文件、应用入口、配置
接缝全部就位，构建全绿；**不含任何业务逻辑、不含任何接口、不含数据库表**。

### 你已拍板的四项决策

| 决策项 | 你的选择 | 影响 |
|---|---|---|
| 骨架深度 | **可编译空壳** | 验收标准 = `mvn compile` / `mvn test` / `pnpm build` 全绿，不做 gateway→auth 参考调用链 |
| OpenSpec 结构 | **跨栈协调文档** | 1 份协调文档 + 2 个 per-stack change（backend / frontend） |
| 后端范围 | **基础子集** | 仅 `lumen-bom` + `lumen-common`(5 个) + `lumen-gateway` + `lumen-auth`；对应交接文档 §8 建议顺序 step 1 |
| docker | **暂不做** | 因此**所有测试必须在无 Nacos / PostgreSQL / Redis / RocketMQ 的环境下通过**——这是本次最硬的约束 |

---

## 2. 文件清单与各文件职责

### 协调层（跨栈父文档）

| 路径 | 职责 |
|---|---|
| `openspec/coordination/init-project-skeleton.md` | **跨栈总入口**。记录两栈共同契约（响应封装、网关路径、雪花 ID 字符串化、每服务一库）、**端口分配表**（gateway 8080 / auth 8081 / web dev 5173）、6 条决策、变更登记表、归档门禁 |

### 后端 change：`init-backend-skeleton`

| 路径 | 职责 |
|---|---|
| `openspec/changes/init-backend-skeleton/proposal.md` | 为什么做、改什么、新增哪两个能力、范围边界、成功标准、风险 |
| `.../specs/service-topology/spec.md` | **服务拓扑能力**：聚合器声明规则、基础模块集、共享库边界（不可执行 jar）、服务身份（名称+端口）、**无基础设施下上下文加载**、敏感配置排除。11 条 WHEN/THEN |
| `.../specs/dependency-governance/spec.md` | **依赖治理能力**：版本单一来源、**OPS 列车钉死 + 与 OPS 版本逐一对齐**、集成库「只托管不引用」。9 条 WHEN/THEN |
| `.../specs/api/spec.md` | **MODIFIED 增量**：响应信封改为 OPS `R` 契约 `{ timestamp, code: string, msg, data }`，成功码 `"200"`/`"204"`（2026-10-29 用户指令：契约向 OPS 对齐） |
| `.../specs/errors/spec.md` | **MODIFIED 增量**：错误码从数字 `0/4xxxx/5xxxx` 改为 OPS `ExceptionEnum` 字母分层（`A` 用户端 / `B` 系统 / `C` 第三方），错误字段 `msg` |
| `.../specs/data/spec.md` | **MODIFIED 增量**：审计字段从 `created_at/updated_at`(UTC) 改为 OPS `EntityBase`——`deleted` 逻辑删除 + `create_by/create_time/update_by/update_time`（GMT+8 序列化） |
| `.../design.md` | 怎么做：D1 钉死的版本列车、D2 dcm4che 独立仓库、D3 测试环境屏蔽 Nacos、D4 模块布局、D5 最小占位源码 |
| `.../tasks.md` | 18 个可勾选任务，分 5 组，每个任务自带验证命令 |
| `.../execution-contract.md` | 执行契约：Intent Lock、20 条已批准行为、设计约束、5 个批次、测试义务、执行模式 **SDD**、评审关卡、升级规则 |

### 前端 change：`init-web-skeleton`

| 路径 | 职责 |
|---|---|
| `openspec/changes/init-web-skeleton/proposal.md` | 同上（前端视角）；明确**不选 mock 工具**、**不引入测试运行器** |
| `.../specs/web-app-shell/spec.md` | **Web 应用外壳能力**：构建完整性（TS strict）、组件风格（函数组件+AntD 优先）、模块布局、**API 接缝不得臆造端点**、仅走网关边界、设计令牌主题（**主色禁用红色**）、占位路由须明示未实现。13 条 WHEN/THEN |
| `.../design.md` | D1 钉死的前端工具链、D2 Node 版本下限、D3 代理目标、D4 信封接缝、D5 mock 工具延后、D6 主题、D7 科室路由模式 |
| `.../tasks.md` | 22 个可勾选任务，分 6 组 |
| `.../debt.md` | **技术债 3 条**：mock 工具未选（中风险）、无测试运行器（中风险）、Vite 落后一个大版本（低风险），各带复查触发条件 |
| `.../execution-contract.md` | 执行契约，执行模式 **SDD** |

---

## 3. 关键技术结论

### 后端版本列车 —— 已改为 **OPS 验证列车**（2026-10-29 用户指令，推翻原 Boot 3.x 方案）

**来源**：`/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/pom.xml`（你的生产验证工程，
groupId `org.birdbro`）。OPS 管理的 jar 一律 **1:1 照搬**，不再自行调研兼容性：

| 组件 | OPS 版本（采纳） | 备注 |
|---|---|---|
| Java | **11** | 推翻交接文档的 Java 17 |
| Spring Boot | **2.5.15** | 推翻"3.x"；OSS 支持已停更，接受此代价换取已验证兼容性（见风险） |
| Spring Framework BOM | 5.3.33 | OPS 显式管理 |
| Spring Cloud | **2020.0.3** | 配 SCA 2021.1 + Boot 2.5.x |
| Spring Cloud Alibaba | **2021.1** | Nacos 注册/配置；Sentinel 仅托管未使用 |
| bootstrap-starter | 3.0.3 | OPS 用 `bootstrap.yml` 方式加载 Nacos 配置 |
| MyBatis-Plus | **3.5.3.1** | `mybatis-plus-boot-starter`（Boot 2 线）——**不是** boot3-starter |
| **Lombok** | **1.18.30** | 与 OPS 一致 |
| **Hutool** | **5.8.20**（hutool-all） | 与 OPS 一致 |
| Druid | 1.2.18 | 取代交接文档的 HikariCP（BOM 层面） |
| PostgreSQL 驱动 | 42.2.18 | ⚠️ 该精确版本有 CVE-2022-21724（42.2.25 修复），见风险 |
| springfox swagger | 3.0.0 | 仅托管；恰好只兼容到 Boot 2.5，与本列车咬合 |
| 另照搬 | jjwt 0.9.1 · fastjson2 2.0.38 · easyexcel 3.3.2 · shiro 1.10.1 · mapstruct 1.5.5.Final · commons-io 2.13.0 · zxing 3.3.3 等 | 全量 |
| 构建机制 | `${revision}`+flatten 1.5.0 · surefire 3.0.0-M5 · compiler 3.8.1（Lombok/mapstruct 注解处理器） | OPS 的构建方式照搬 |

**OPS 没有的 jar（无 OPS 验证背书，仅托管、不进编译路径）**：dcm4che 5.35.2、HAPI HL7v2
2.6.0、rocketmq-spring-boot-starter 2.2.3。Flyway/Redis 不单独钉（`spring-boot-dependencies`
已托管，与 OPS 同）。

### 契约也对齐 OPS（2026-10-29 第二条指令）

不止版本，**契约本身**也改为 OPS 一致——`R.java` 和 `EntityBase` 原样采纳，规格向代码看齐
（原方案是代码迁就规格，已推翻）：

| 契约 | 原 spec | 改为（OPS 实测） |
|---|---|---|
| 响应信封 | `{ code: int, data, message }` | `{ timestamp, code: string, msg, data }`，成功 `"200"`/`"204"`（R.java 实测） |
| 错误码 | 数字 `0`/`4xxxx`/`5xxxx` | OPS `ExceptionEnum` 字母分层：`A`=用户端、`B`=系统、`C`=第三方（即阿里错误码标准） |
| 审计字段 | `created_at/updated_at`（timestamptz, UTC） | `EntityBase`：`deleted` 逻辑删除 + `create_by/create_time/update_by/update_time`，序列化 `yyyy-MM-dd HH:mm:ss` **GMT+8** |

**两个已标注的后果**（写在协调文档决策 #9）：① 时间戳从 UTC 改为 GMT+8；② `create_by`/
`update_by` 是 OPS 的 `Integer`（int32）——若 lumen 用户主键用雪花 bigint，将来 `lumen-auth`
change 必须把这两个列加宽为 bigint，先记不改。

### 后续决策追加（2026-10-29 同日，均已落协调文档与 analysis 文档）

| 决策 | 内容 |
|---|---|
| **删除 `lumen-gateway`** | 医院形态统一入口 = **nginx**（80/443）；鉴权在各服务过滤器（OPS 模式）、限流用 Sentinel、发现是静态的——网关每一项功能都有归属。SCG 留给云端形态的 change |
| **OPS `framework`/`common` 整模块照搬** | 原名/原包（`org.birdbro.framework`/`org.birdbro.common`）原依赖；仅三处适配：Redis 条件化、安全接线边界、操作日志表按服务归属。取代原"五分法 lumen-common"方案 |
| **包名双根** | 照搬件 `org.birdbro.{framework,common}`；新建件 `org.birdbro.lumen.*`；groupId 全 `org.birdbro`；作者署名 birdbro 全保留（头注释/pom/LICENSE/镜像）。规则已写入 `.claude/rules/naming.md` |
| **部署形态** | 医院内网 = 多服务进程（一模块一 jar 一容器）+ 静态发现 + Nacos 仅配置中心（三护栏）+ Docker compose；模块化单体为极小站点可选。全文见 `docs/analysis/hospital-deployment-forms.zh.md` |
| **两个产品** | 云端版业务中只有**放射科**可远程诊断（超声/内镜仅现场）→ 云端版是**独立产品**（放射科远程诊断/转诊/云胶片），不是本地版的部署变体；两产品以版本化库共享内核（bom/framework/common/exam-core/api），非 fork；仓库拓扑由云端产品提案定 |
| **科室命名** | `lumen-radiology`（放射科）、`lumen-us`、`lumen-endo`——统一「模块名=科室英文名」规则（用户 2026-10-29 拍板）；RIS 作为产品类别词留在文档（Business Context），不进模块/包名 |（写在协调文档决策 #9）：① 时间戳从 UTC 改为 GMT+8；② `create_by`/
`update_by` 是 OPS 的 `Integer`（int32）——若 lumen 用户主键用雪花 bigint，将来 `lumen-auth`
change 必须把这两个列加宽为 bigint，先记不改。

**代价（已写进 proposal Risks，用户 2026-10-29 知情接受）**：Boot 2.5.15 OSS 已停更（无安全
补丁）、`javax` 体系；两个 OPS 精确版本带 CVE（PostgreSQL 42.2.18、commons-fileupload 1.3.1）。
同小版本内的补丁升级（42.2.25 / 1.3.3）已列为**待你批准的第一个偏离项**。

### ⚠️ 重要发现 1：dcm4che 不在 Maven Central

实测 `org.dcm4che:dcm4che-core` 在 `repo1.maven.org` 返回 **404**，只在
`https://www.dcm4che.org/maven2` 有（最新 5.35.2）。所以 `lumen-bom` 必须**额外声明这个
仓库**。本次变更**不让任何模块引用它**（只托管版本），因此仓库不可达也不会导致构建失败——
但将来做 `lumen-integration` 时必须注意这一点。

### ⚠️ 重要发现 2：前端 registry 的 `latest` 已全面超出项目约束

| 包 | registry `latest` | 项目约束 | **本次钉的版本** |
|---|---|---|---|
| react | 19.3.1 | React 18 | **18.3.1** |
| antd | 6.6.8 | AntD 5 | **5.29.3** |
| vite | 8.3.5 | — | **7.3.7** |
| typescript | 7.0.2 | — | **5.9.3** |

**为什么不用 Vite 8**：`@vitejs/plugin-react` 6.x 的 peer 只接受 `vite ^8`，而 5.2.0 的 peer
接受 `^4.2 || ^5 || ^6 || ^7 || ^8`。选 Vite 7.3.7 + plugin-react 5.2.0 是稳妥组合。
Vite 7.3.7 的 Node 引擎要求为 `^20.19.0 || >=22.12.0`（本机 Node v22.16.0 / pnpm 10.32.1，满足）。

→ 结论：**六个依赖必须写死精确版本（不带 `^` / `~`）并提交 `pnpm-lock.yaml`**，否则一次
`pnpm install` 就会漂移到 React 19 + AntD 6，直接违反 `lumen-web/CLAUDE.md` 的「AntD 5 优先」。

### ⚠️ 重要发现 3：前端 L3 验证是 SKIP，不是 PASS

`openspec/verify.config.yaml` 中 `lumen-web.test: null`，所以 `/opsx:verify` 的 L3（测试）会报
**SKIP**。这意味着前端**唯一自动化关卡是 TS strict 构建**。像「占位路由不发请求」「主色不是
红色」「路由确实是懒加载分包」这类行为断言**没有任何测试覆盖**，只能靠 `tasks.md` 里的
grep/构建产物检查命令 + 人工评审。这正是前端契约选 **SDD** 模式（而非 Batch Inline）的原因——
SDD 的独立评审维度是这 13 条行为唯一的结构化关卡。此项已记入 `debt.md`。

---

## 4. 下一步行动建议

### 立即要做（按顺序）

1. **评审本文与两份契约** —— 重点看第 3 节三条发现和「执行模式」判断
2. **批准执行契约（DP-3 审批门）** —— 回复批准后端契约，我才会：
   - 更新 `openspec/loop-state.yaml`：`phase: applying`、`change: init-backend-skeleton`、
     `execution_mode: SDD`
   - 通过 `guard.sh proposing applying`（已试跑，**PASS**）
   - 开始 `/opsx:apply` 实现
3. **后端先做、前端后做，不要并行** —— `loop-state.yaml` 只有**一个** `artifacts_hash` 字段，
   两个 change 无法同时「新鲜」。协调文档已记录此决策。

### 需要你留意的判断点

| 事项 | 我的处理 | 你可能想改 |
|---|---|---|
| **OPS 列车的两个 CVE 版本** | PostgreSQL 42.2.18、commons-fileupload 1.3.1 **原样照搬**（遵守"和 ops 一样"）；补丁版 42.2.25 / 1.3.3 已列为待批偏离项 | 医疗影像平台存患者数据，建议批这两个补丁位（同小版本，风险极低） |
| OPS 列车 vs 旧决策的冲突 | 已按你的指令**推翻**交接文档 §2 的 Java 17 + Boot 3.x + HikariCP；CLAUDE.md/AGENTS.md/lumen-sev/CLAUDE.md 的技术栈行由任务 5.4 同步（防止未来会话改回去） | 交接文档 §2 是历史记录未改；如希望也加修正注记，说一声 |
| 端口分配 | gateway 8080 / auth 8081 / web dev 5173，写入协调文档 | 若与你本地习惯冲突，现在改最省事 |
| 前端 mock 工具 | **本次不选**，记入 debt，触发条件 = API spec 出现第一个具体端点时 | 若你想现在就定（MSW / vite-plugin-mock-dev-server / mockjs），需另开 change |
| 前端测试运行器 | **本次不加** Vitest，L3 保持 SKIP | 若你希望骨架阶段就有测试，需扩大本 change 范围并改 `verify.config.yaml` |
| 执行模式 | 两个 change 均判为 **SDD**（>3 任务 + 跨模块依赖 + 风险指标），你已确认保持 SDD | — |

### 骨架做完之后（后续独立 change，勿混入本次）

- 后端：`lumen-exam-core` + `lumen-api/*` + `lumen-patient` + `lumen-radiology`（跑通预约→登记→报告）
- 后端：`lumen-integration`（MWL/MPPS 对接 dcm4chee，项目核心价值点）
- 独立：`docker/` compose（含 dcm4chee-arc-light）—— 一旦有了它，「无基础设施测试」约束即可放宽
- 前端：API spec 出现第一个端点后，选 mock 工具并写第一个 typed client
- 工程：GitHub 组织注册、CI（Maven + pnpm 两个 job）、main 分支保护

---

## 5. 流程图

```
你在这里
  │
  ▼
/proposing ──────────────────────────────────────────────┐
  │  ✅ 协调文档已建                                        │
  │  ✅ backend: proposal/specs×2/design/tasks/contract    │
  │  ✅ frontend: proposal/specs/design/tasks/debt/contract│
  │  ✅ openspec validate --strict 两个 change 均通过        │
  │  ✅ artifacts_hash 已存（backend）                      │
  │  ✅ guard.sh proposing→applying 试跑 PASS              │
  │                                                       │
  │  ⏸  DP-3 审批门：等待你批准执行契约                      │
  ▼                                                       │
/applying  (backend 先)                                    │
  │  Batch 1 聚合器+BOM  ──▶ 🔒 强制评审（版本列车是地基）     │
  │  Batch 2 五个共享库                                     │
  │  Batch 3 gateway/auth 外壳 ──▶ 🔒 评审（测试 profile）    │
  │  Batch 4 零基础设施验证 ──▶ 🔒 强制评审（风险最高批次）    │
  │  Batch 5 文档与规格卫生                                  │
  ▼                                                       │
/verifying  L1 mvn compile · L2 20 条 WHEN/THEN · L3 mvn test
  │                                                       │
  ▼                                                       │
/applying  (frontend 后，重设 artifacts_hash)                │
  │  Batch 1 脚手架+钉版本 ──▶ 🔒 强制评审                    │
  │  Batch 2 模块布局                                       │
  │  Batch 3 API 接缝 ──▶ 🔒 强制评审（防止臆造端点）          │
  │  Batch 4 网关代理（跨栈端口须与协调文档一致）              │
  │  Batch 5 外壳/主题/路由 ──▶ 🔒 强制评审（L3 SKIP，唯一人工关卡）
  │  Batch 6 债务与规格卫生                                  │
  ▼                                                       │
/verifying  L1 pnpm build · L2 13 条 WHEN/THEN · L3 SKIP    │
  │                                                       │
  ▼                                                       │
门禁：两个 change 全部 verify PASS ──▶ 各自 archive ──▶ 协调文档标记 archived
                                                          │
       任一环节发现新范围/设计假设错误 ──▶ rewind ───────────┘
```
