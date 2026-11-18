# exam-item-table-polish-web — 检查设置三页列表修饰：项目+编码合列 · 表头精简 · 创建时间下线

**Date**: 2026-11-17 · **Status**: implemented-pending-user-confirm · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-17：三个检查设置列表读的都是初始化导入的目录数据，创建时间没有
运营价值，从列表下线；检查项目列表表头按读表习惯精简。

## What Changes

检查项目列表（`/setting/exam-items`）：

- 项目名称 → **项目**；项目编码列下线，编码并入 项目 同一单元格**分上下两行**
  （名称在上、编码 tertiary 小字在下——deps 科室 / roles 创建人列惯例，2026-11-17
  复核定稿）；
- 对照编码 → **对照码**；
- 诊断计费数量 → **计费数量**，与 诊断得分 / 审核得分 一并居中；
- 创建时间列下线。
- 抽屉表单不动：项目编码 / 对照编码 / 项目名称 等标签保持（exam-body-table-polish
  先例：表头改名不传导到抽屉）。
- 单价 / 折扣 保持右对齐（不在裁决范围）。

检查部位右表、检查方法列表：仅创建时间列下线，其余不动。

**评审第二轮（2026-11-17，页面复核意见，同 change 收编）**：

- 操作列迁入壳层 chrome：**删除 换 delete 字形**（原先误骑 edit 字形）、**操作
  表头右缘挂刷新**（重跑当前页查询）；同一约定补齐 检查方法 页（此前无壳层）。
- 抽屉重排：**编码 必填**（空存拦截）、标签缩短（编码/名称/对照码/计费数量，与
  列表头一致）、名称上限 50→60、对照码 移到 检查方法 之下、排序 独占一行、数字带
  收窄为 单价/折扣/计费数量 3-up、常用项目 移到 备注 之上（label 居左控件居右）。

**评审第三轮（2026-11-17，列宽 + 选项行回移）**：单价（元）列宽 110，
折扣/计费数量/诊断得分/审核得分 统一 100；抽屉 常用项目 选项行移到 备注 之上。

**评审第四轮（2026-11-17，常用项目回移）**：备注上方观感不佳——常用项目 选项行
还原到 备注 之下收尾（label 居左控件居右不变）；列宽维持 单价110 / 其余100。

## Scope

- `lumen-web`: `src/pages/setting/{exam-item,exam-body,exam-method}/index.tsx`
  + 三份页测（`exam-item-page.test.tsx` / `exam-body-page.test.tsx` / `exam-method-page.test.tsx`）
- specs delta: `web-system-console` § Exam Item Page / Exam Body Page / Exam Method Page

### 后端 SQL 分析结论（用户问询「是否要精简」，裁定：不动）

三个列表查询均为 MyBatis-Plus `LambdaQueryWrapper` 包装器查询（setting 域无手写
SQL、无 XML mapper），`create_time` 不在任何 Java 代码里——它随 `EntityBase`
审计列被自动 SELECT 带出，排序走 `id DESC`（不依赖 create_time）。精简它需要给每个
wrapper 钉显式列清单：

1. PG 行存下 SELECT 少一列不省堆表 I/O；每页 ≤20 行，负载差 ≈ 半 KB/页，无测量意义；
2. 钉死的列清单在加列时必然失修——V37 `external_code` 昨天加列，自动 SELECT 免费跟上，
   显式清单就得同步改（这是它唯一能"省"出来的东西：维护风险）；
3. `ItemVO`/`ExamMethod` 等携带 `createTime` 是 OPS `EntityBase` 统一审计面，剔除属于
   API 契约变更，要动 api spec 三个端点段，收益为零。

结论：前端停用展示列即可，查询与 VO 原样保留（HERO：该失败模式改变不了下一个动作，
不做防御性精简）。

## Impact

- 无 API 契约变更，无库变更，无 coordination doc（单栈轻量）。
- 归档顺序：在 `exam-item-external-code-web` 与 `exam-item-polish-web` **之后**——
  本 change 的 Exam Item Page delta 描述两者终态叠加本轮修饰（表头列清单、合列单元格）。
