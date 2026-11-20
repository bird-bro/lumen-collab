# Design

## Context
模态校验是 setting 域唯一的词典式守卫：收敛在 `ExamBodyGroupServiceImpl.requireModality`
（interface contract，四 ServiceImpl 共 12 处调用），底层 SQL 在 `DictModalityMapper`
（public.dict_* 直读，search_path 语义）。锚点漂移事实链见
`openspec/coordination/modality-dict-id-anchor.md` §Shared facts，此处不复述。

## Goals / Non-Goals
**Goals:** 校验锚点收敛到 id 1024；两处主规格措辞同步；一个锁锚测试。
**Non-Goals:** 存量按名消费者全面迁移（room/device/users，coordination debt）；
缓存引入；任何种子/DDL 变更。

## Decisions
- **D1 SQL 直写 id 字面量**：`JOIN dict_catal c ... WHERE c.id = 1024`。备选：
  Java 常量拼进 @Select（注解 SQL 静态字符串，只能 `${}` 拼接，徒增注入面与间接）；
  否决。1024 的稳定性由 `AuthDeviceRoomSeedTests` 守护（断言「检查类型」保持退役、
  1024 唯一模态目录），锚点漂移风险有测试兜底。
- **D2 测试锁锚而非模拟**：现有 api 测试对 `DictModalityMapper` 零引用——新增一个
  真实 DB 断言的 mapper 测试：`countLiveModality('CT') == 1`、未知 token `== 0`。
  改名韧性不模拟改名（改名是操作员行为）——SQL 用 id 本身即结构保证。
- **D3 规格超集纪律**：两处 MODIFIED 均整块搬运、只改锚点措辞（openspec-delta-layering
  先例）；exam-setting 侧新增改名韧性场景属超集扩展。

## Risks / Trade-offs
- [共享测试库态漂移/并行套件] → mapper 测试只依赖种子保证的 1024 词表；verify 需独占
  窗口（shared-test-server-verification-hazards 四坑）。
- [id 未来再翻动] → 属新裁决范畴；届时改一处字面量 + 同步规格，成本常量级。

## Migration Plan
单文件 SQL 字面量替换，无部署顺序约束；回滚 = 恢复原 WHERE 子句（无状态残留）。
