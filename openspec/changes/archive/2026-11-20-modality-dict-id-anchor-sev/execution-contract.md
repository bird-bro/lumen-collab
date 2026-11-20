# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: modality-dict-id-anchor-sev
- **Problem to solve**:
模态词表消费者的锚点三态漂移：主规格锚 dict 1021（已退役）、实现锚目录名「检查类型」（V27 统一后**不存在**的目录名）、数据实际是 id 1024「设备类型」。单一瓶颈点 `DictModalityMapper.countLiveModality`（`c.name='检查类型'` 恒空 join）使 `ExamBodyGroupServiceImpl.requireModality` 全拒——ExamItem/ExamMethod/ExamBody/RepTemp 四个 ServiceImpl 共 12 处调用（列表+写入）全数失败，setting 域模态路径整体不可用。

setting 域模态校验的 SQL 锚点由不存在的目录名「检查类型」收敛到确定的目录 id 1024（dict-lookup-by-id 裁决 2026-11-19），恢复全部模态路径并同步两处主规格措辞。
- **In scope**:
- `DictModalityMapper` SQL/javadoc 切换到 id 1024
- mapper 锚点测试（真实种子库）
- exam-setting / system-data 主规格同步
- 受影响套件验证
- **Out of scope**:
- 存量按名消费者迁移（room/device/users/roles/logs——coordination debt）
- 缓存引入、种子/DDL 变更、API 契约变化

## Approved Behavior

<!-- Reviewed 2026-11-20: 场景与 delta 一致;「未知值拒绝」为超集保留,改名韧性为新增场景 -->
- `specs/exam-setting/spec.md`
5:### Requirement: Modality Vocabulary
14:#### Scenario: Unknown modality rejected
20:#### Scenario: Catalog rename does not affect validation
- `specs/system-data/spec.md`
5:### Requirement: Dict Seed Data
22:#### Scenario: Fresh database carries the starter dictionary
28:#### Scenario: Re-run is a no-op
33:#### Scenario: Exam-type vocabulary renders abbreviations
40:#### Scenario: Audit-authority vocabulary carries the three tiers


## Design Constraints

- **Architecture/Decisions**:
- **D1 SQL 直写 id 字面量**：`JOIN dict_catal c ... WHERE c.id = 1024`。备选：
  Java 常量拼进 @Select（注解 SQL 静态字符串，只能 `${}` 拼接，徒增注入面与间接）；
  否决。1024 的稳定性由 `AuthDeviceRoomSeedTests` 守护（断言「检查类型」保持退役、
  1024 唯一模态目录），锚点漂移风险有测试兜底。
- **D2 测试锁锚而非模拟**：现有 api 测试对 `DictModalityMapper` 零引用——新增一个
  真实 DB 断言的 mapper 测试：`countLiveModality('CT') == 1`、未知 token `== 0`。
  改名韧性不模拟改名（改名是操作员行为）——SQL 用 id 本身即结构保证。
- **D3 规格超集纪律**：两处 MODIFIED 均整块搬运、只改锚点措辞（openspec-delta-layering
  先例）；exam-setting 侧新增改名韧性场景属超集扩展。
- **Technical constraints**:
- Java 11 / Spring Boot 2.5.15（OPS 版本线，禁升级）；注解 SQL 静态字符串（禁 `${}` 新拼接面）
- 类头 `@author birdbro`；共享测试库验证需独占窗口（已知四坑）

## Task Batches

<!-- Batch 1 = 任务组 1（锚点切换 + 锚点测试）；Batch 2 = 任务组 2（受影响套件 + 规格核对）。依赖顺序成立 -->
5:- [ ] 1.1 `DictModalityMapper` @Select SQL `c.name = '检查类型'` → `c.id = 1024`，javadoc 改写为 id 锚定理由（引用 2026-11-19 裁决，删 1021/1027 翻动史论据）；verify: `./mvnw compile -q` 通过
6:- [ ] 1.2 新增 mapper 锚点测试（真实种子库：`countLiveModality('CT') == 1`、未知 token `== 0`，SQL 文本无目录名依赖）；verify: 该测试绿
10:- [ ] 2.1 运行 setting 域受影响测试（ExamItem/ExamMethod/ExamBody/RepTemp api tests + AuthDeviceRoomSeedTests），日志重定向文件；verify: exit 0 或失败逐条定性（共享库瞬态参照已知四坑）
11:- [ ] 2.2 主规格同步核对：exam-setting/system-data 两处 MODIFIED 场景为超集且与实现一致；verify: 走查结论记入 verify.md 素材

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 锚点测试先写（RED：当前 SQL 锚名，真实库 `countLiveModality('CT')` 返回 0 断言失败）→ 1.1 切 id 后 GREEN
- **Required edge cases**: 未知 token → 0；停用/软删 detail 不计入（既有 SQL 条件，测试锁定）
- **Regression-sensitive areas**: `AuthDeviceRoomSeedTests` 的「检查类型保持退役」守卫；四 ServiceImpl 既有 api 测试

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 4 个 tracked 任务，同模块（setting 域），无跨模块依赖、无风险指标；>3 → Batch Inline

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
