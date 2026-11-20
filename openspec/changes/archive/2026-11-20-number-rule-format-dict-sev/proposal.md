# Proposal: number-rule-format-dict-sev

## Why
号码规则目前只默认 `yyMMdd`，无法在页面选择更多日期/时间戳模式；格式词表需要进入数据字典维护。

## What Changes
新增 V43 字典种子（catalog 1029，种子名「号码戳格式」，V45 跟随操作员改名落「编号格式」），预置 `yyMMdd`、`yyyyMMdd`、分钟/秒级时间戳模式；`value` 是写入 `number_rule.stamp` 的 Java 模式。既有号码规则 API/表结构不变。

## Affected Stacks
- single-stack: lumen-system data seed

## Depends-On
- none - standalone data seed; coordinates with `number-rule-format-dict-web`

## Scope
- In scope: deterministic dict catalog/detail seed and sequence resync.
- Out of scope: `number_rule` DDL changes, issuance reset-policy redesign, Java API contract changes.

## Success Criteria
- Fresh/replay-safe migration exposes enabled dict items under catalog 1029 (「编号格式」).
- Dict lookup returns the seeded patterns for the drawer Select.
- V43 re-execution is a no-op.

## Constraints
- Anchor the catalog by name; detail `value` is the machine pattern.
- Preserve V25 deterministic-id and `setval` discipline.

## Risks
- Malformed custom dict pattern → issuance surfaces the existing invalid-stamp-format request error; operators correct the dict item.
