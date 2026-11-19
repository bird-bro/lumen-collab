# Tasks: rep-temp-normal-range-web

> TDD：先红后绿。前置：`rep-temp-normal-range-sev` verify PASS（真后端联调）。
> 构建一律 `cd lumen-web && pnpm ...`；单测 `npx vitest run src/test/rep-temp-page.test.tsx`。

## 1. 类型层

- [x] 1.1 红：types 测试/编译断言——`RepTempMeasureNormal` union 可用，`RepTempMeasure.normal` 收窄为 union|null
- [x] 1.2 绿：`types.ts` 新增 union 类型 + 注释 version pin 2

## 2. 编辑器 + 序列化

- [x] 2.1 红：五场景用例——区间双边/单边序列化、文本序列化、双侧空→null、min>max 拦截（无请求）、按 kind 回显 Segmented 与输入
- [x] 2.2 绿：测量项行正常范围列改 Segmented(区间/文本) + InputNumber×2 / text Input（design D2 行内布局）；`saveTemplate` 序列化按 design D3（恒写 version 2）；min>max 预校验按 D4；`openTemplateEdit` 回显按 D5
- [x] 2.3 删除 v1 backfill 残留分支（字符串 normal 判断）——迁移后无 v1 行

## 3. 夹具与回归

- [x] 3.1 `rep-temp-page.test.tsx` 结构化夹具 normal 升 union 形态；受影响既有用例断言更新
- [x] 3.2 全量回归：`npx vitest run`（全套件）+ `npx tsc --noEmit` 干净
