# Feign 直连 vs Nacos 注册发现 —— 决策分析与升级路径

> 交接分析 · 2026-11-20 · 结论：现行架构维持直连，升级触发条件见 §4
> 用途：未来 "cloud-form" 改造提案（引入 Nacos / Spring Cloud Gateway）的决策底稿。
> 决策沿革：2026-11-03 design D8 拍板 no-Nacos（随访 fu_launch 模式）；本文档补充对比论证与升级路径。

## 0. 一句话结论

两种模式的机制差异：**直连是"地址写在配置里"，Nacos 是"服务自报地址，调用方按服务名查活实例"**。本项目当前选直连不是因为功能不足，而是 Nacos 的三项核心价值（动态扩缩容、实例级故障转移、配置热更新）在固定拓扑的院内部署里兑现不了，白付运维成本；且升级路径已收窄到纯配置层，契约层零改动。

## 1. 现状盘点（证据锚点）

| 组件 | 状态 | 证据 |
|:--|:--|:--|
| Nacos（注册+配置） | **刻意移除**，design D8（2026-11-03） | `lumen-sev/lumen-patient/pom.xml:56` —— "The OPS-train nacos-discovery/nacos-config/bootstrap starters stay removed"；`lumen-patient/src/main/resources/application.yml:1` —— "design D8: no Nacos anywhere; local files only" |
| 服务寻址 | Feign **直连 URL** | `lumen-api/.../number/NumberApi.java:28` —— `url = "${lumen.api.system.url:}"`；`lumen-api/.../patient/PatientApi.java:41` —— `url = "${lumen.api.patient.url:}"` |
| 配置管理 | 环境变量 + gitignored `application-dev.yml`（模板 `application-dev.yml.example`），无配置中心 | 随访 fu_launch 模式（design D8） |
| Java 网关 | 无，nginx 统一入口 `/api/v1/<service>/**`；Gateway 留作 future cloud-form change | `openspec/specs/service-topology/spec.md:90`（"Unified Entry Without a Java Gateway"） |
| Sentinel | starter 仅用于给 Feign 接口织入 fallback 类（`NumberApiFallback` / `PatientApiFallback`），无 dashboard | 进程内库，无需部署 |
| RocketMQ | 全仓零引用（2026-11-20 核实）；us-station 事件面用 outbox + HTTP pull/ack 替代 | us-station B2（`InternalPatientController` pull/ack） |
| 已需运行时组件 | 仅 PostgreSQL（一服务一 schema、一连接角色） | 容器编排（nginx/Redis/多容器）让给未来 docker change，`docker/` 目前 README-only |

## 2. 机制对比

| 维度 | Feign 直连（现状） | Nacos 注册发现 |
|:--|:--|:--|
| 地址来源 | 配置文件/环境变量里的静态 URL | 服务启动注册 + 心跳续约，注册中心维护活实例列表 |
| 目标实例挂了 | 调用失败，走 Sentinel fallback | 健康检查摘除死实例，自动绕开 |
| 多实例负载均衡 | 无（URL 指单地址；要 LB 则让 URL 指向 nginx upstream） | LoadBalancer 按服务名在实例间轮询 |
| 扩缩容/迁移 | 改配置 + 重启调用方 | 新实例自注册，调用方零感知 |
| 配置热更新 | 无（文件 + 重启） | Nacos 兼做配置中心，热推送 |
| 需安装运维的组件 | **零** | Nacos 集群（生产建议 1~3 节点）+ 每服务接入依赖 |
| 故障排查 | 地址错 → 立刻 connection refused，一眼定位 | 多一层：注册中心不可达/网络分区也要查 |
| 失败模式 | 只有"目标不可达"一种 | 新增"注册中心不可达""注册数据陈旧"等 |

## 3. 本项目裁决直连的理由（5 条）

1. **拓扑是死的**。部署单元封顶 6~7 个（system/patient/radiology/us/endo/integration），院内固定拓扑，无 K8s 弹性伸缩场景 —— Nacos 的核心价值兑现不了。
2. **调用链很短**。east-west 仅两条：patient→system（发号，`NumberApi`）、us→patient（档案解析，`PatientApi`）。为两条调用维护注册中心集群，运维杠杆为负。
3. **流量形态有界平稳**。院内科室级流量，非互联网突发形态；对实例级自动 failover 需求低，多实例时 nginx upstream 兜底。
4. **少一个组件 = 少一种故障模式**。部署目标是低成本落进医院/科室环境；直连少一件必须先于所有服务存活的基础设施，现场实施更简单，排查链路更短。
5. **参照系已验证**。OPS 参照工程同形态跑通，整栈 1:1 拷贝（`lumen-bom` 版本列车），风险已被趟过。

## 4. 何时重新评估（触发条件）

任一成立即应发起新提案重新评估（并同步评估 Spring Cloud Gateway —— north-south 网关与 east-west 注册是一对 cloud-form 变更）：

- [ ] 部署进入容器编排/K8s，单服务需要多实例弹性伸缩
- [ ] 服务数 × 调用链增长到点对点 URL 配置的管理成本超过注册中心运维成本
- [ ] 配置热更新成为硬需求（当前"改配置重启"可接受）
- [ ] 跨机房/多环境寻址，nginx upstream 不再够用
- [ ] 医院方/集成方强制云原生部署形态

**不触发升级的情形**：单纯"用了微服务框架就该配注册中心"的观念性理由 —— 本项目形态是"有微服务边界，无微服务全家桶"，这是设计而非缺漏（见 §1 证据）。

## 5. 升级路径（届时怎么改）

核心论点：**契约层（`lumen-api`）在两种模式下完全不变**，切换点已收窄到纯配置层。

1. **版本**：`nacos-discovery` + `spring-cloud-loadbalancer` starters 加回 `lumen-bom`（版本列车唯一版本源，改版本须走新提案）。
2. **Feign 契约**：删除或置空接口上的 `url` 属性 → `name` 属性（本来就有）改走服务名解析 + LoadBalancer。接口签名零改动，`lumen-api` add-only 纪律不破。
3. **配置**：各服务 `application-dev.yml` / env 加 Nacos server 地址（回归 bootstrap 或 spring.config.import，按列车版本定）。
4. **降级**：Sentinel fallback 类逻辑不变，两种模式下均生效。
5. **入口**：nginx north-south 入口不受影响（Nacos 只管 east-west）。
6. **流程**：OpenSpec 新提案 + `service-topology` spec delta（"Unified Entry Without a Java Gateway" 等场景届时需增补 MODIFIED 块），禁止直改 specs。

## 6. 相关锚点

- `openspec/specs/service-topology/spec.md` —— 服务拓扑与无网关统一入口的权威规格
- `docs/analysis/hospital-deployment-forms.zh.md` —— 部署形态分析（同目录姊妹篇）
- `lumen-sev/CLAUDE.md` —— 架构规则（"no Nacos since 2026-11-03"、nginx 统一入口）
- us-station 提案（outbox + HTTP pull/ack 事件面，MQ 缺位下的替代形态先例）
