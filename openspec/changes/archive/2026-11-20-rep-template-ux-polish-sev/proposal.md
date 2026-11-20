# rep-template-ux-polish-sev — 测量项 code 放行可选

**Date**: 2026-11-20 · **Status**: implemented-pending-batch-archive · **Stack**: lumen-sev（coordination rep-template-ux-polish）

## Why

用户裁决 2026-11-20（报告模板页收尾批）：项目(code) 输入框下线——打印版面
B 表（名称/单位/测值/参考范围）从不显示项目码，医生自造 LA/EF 纯负担。UI 下线的
契约前提：`RepTempMeasure.code` 的 `@NotBlank` 放行可选。字段保留：存量行 code
不丢、API 直写仍可带、未来报告域统计锚备用。

## What Changes

- `RepTempMeasure.code` 移除 `@NotBlank`（label 保持必填）；javadoc 重写；
- 测试翻转：`RepTempContentApiTests` 两处「blank code 拒绝」→「blank/absent code
  可写通」+ 新增无 code 键用例；label 必填断言不动；
- exam-setting spec delta：MODIFIED Structured Template Content（超集 5 场景，
  新增「Blank or absent measure code writes through」）。

## Scope

- `lumen-sev`: `lumen-system/.../setting/entity/RepTempMeasure.java` +
  `RepTempContentApiTests.java`
- specs delta: `exam-setting` § Structured Template Content

## Impact

- content jsonb 形状零变更（code 本就 string 位，可选化）；无迁移；无 API 路由变更。
- 与 rep-template-ux-polish-web 配对（web 侧去输入 + code passthrough）。
