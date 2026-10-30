<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: init-backend-skeleton
  date: 2026-10-29
  overall: FAIL
  stacks:
    backend:
      L1: PASS
      L2: "22/37"
      L3: PASS
---

# 验证结果: init-backend-skeleton

> 生成时间: 2026-10-29 · 总体: **FAIL**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0（verify.config 的 `mvn compile -q` 于 `dir: lumen-sev`；JDK 11 + wrapper Maven 3.9.9） |
| L2 规格对齐 | 22/37 已实现 | 13 PARTIAL · 2 NOT_IMPLEMENTED（独立审计 + 协调者复核） |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0；`AuthApplicationTests` 通过；8848/5432/6379/9876 全空闲 |

## 场景对齐详情（按规格文件汇总，逐行明细见 L2 审计记录）

| 规格文件 | ✓ | ⚠ | ✗ | 主要缺口 |
|:---|:--|:--|:--|:---|
| service-topology (18) | 11 | 7 | 0 | 验收命令 cwd 依赖；nginx 配置在树外（延迟）；"模块未声明即不参与构建"无守卫；jar 非可执行仅静态证明 |
| dependency-governance (9) | 6 | 2 | 1 | 未来流程场景无强制物；lumen-integration 延迟未建 |
| api (4) | 3 | 0 | 1 | "Service routing" 端点/路由整体延迟（Intent Lock 排除） |
| errors (5) | 4 | 1 | 0 | `ExceptionEnum.SUCCESS("0000")` 死常量与规格"HTTP 状态码表达成功"矛盾（留档即可） |
| data (1) | 0 | 1 | 0 | 规格说 MetaObjectConfig "fills the fields"——实际只填 create_by/update_by，时间戳无 FieldFill |

## 发现的问题（按严重度）

### 须修复（阻断归档）
1. **规格验收命令可移植性**（service-topology 多处 + 根 CLAUDE.md:69 + verify.config）：`mvn -f lumen-sev/pom.xml` 从仓库根执行会因 `.mvn/maven.config` 的相对路径 `-s .mvn/settings.xml` 解析失败（审计已在 /tmp 与根目录复现 exit 1）。改法二选一：规格命令统一为"cd lumen-sev && ./mvnw ..."，或 maven.config 改绝对无关写法。
2. **Redis "get the beans" 子句的 opt-in 语义**（service-topology "Redis is optional" 第二句）：`@ConditionalOnProperty(..., matchIfMissing=true)` 属性缺省时**仍启用**（审计员的 MAJOR-2 前提有误，已当场以注解源码 + B4 日志的 Spring Data Redis 扫描活动反驳）；但真实缺口是**文档没说清 `lumen.redis.enabled=false` 是显式关闭开关**——补一行设计说明即可。
3. **data delta 措辞过强**：MetaObjectConfig 只填经办人字段；时间戳由 DB 默认值负责（EntityBase 无 fill 属性）。改规格一句。

### 已裁定为合理（不阻断，留档）
4. 2 个 ✗（api "Service routing"、dep-gov "Integration service is added later"）是 **Intent Lock 明确排除的延迟行为**——规格应补"适用于 lumen-integration/端点 change 落地后"限定语，而非当作缺陷。
5. 审计员的计数汇总行（24/10/3）与其逐行表格不符；本凭证采用逐行重数结果 22/13/2。
6. 其余 8 个 ⚠ 为"规则正确但无守卫/未来流程场景"——守卫由 verify.config 与后续 CI 承接，登记不修。

### 顺带（非本 change）
7. `coordination/init-project-skeleton.md` 声称前端 `ApiEnvelope` "已更新"——前端代码本就不存在（init-web-skeleton 还在 proposing），该句指的是前端 change 的规划产物，措辞应改为"规划已更新"。
8. 离线 `package` 需补 pin `maven-jar-plugin`（L1 门禁 compile/test 不受影响）；CI 配网络或 pin 均可。

## 下一步
- FAIL → `/opsx:apply init-backend-skeleton` 修复上述 1/2/3/4（全部是文档级修正，无代码改动）后重新 `/opsx:verify`
