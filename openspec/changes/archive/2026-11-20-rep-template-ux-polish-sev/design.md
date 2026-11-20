# Design — rep-template-ux-polish-sev

## D1 — 可选而非删除

code 字段保留（string|null）：删字段要迁移存量行且断未来统计锚；可选化是
最小契约变更。`@NotBlank` 移除后 Bean Validation 级联仅校验 label；服务层
guardContent/guardNormalUnion 不涉 code，零改动。

## D2 — 测试翻转（TDD RED 先行）

create 路由：blank code → 200 + 新增「无 code 键」用例 → 200；
update 路由：blank code → 200（原断言拒绝）；label 空白两路由仍 A0400。
RED 证据：翻转后首轮 run 命中「测量项 code 不能为空」A0400（1 failed）。
