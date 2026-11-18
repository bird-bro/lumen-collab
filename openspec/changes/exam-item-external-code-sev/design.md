# Design — exam-item-external-code-sev

## Decisions

- **D1 — 通用 `external_code`，非 HIS 专名**（用户裁决 2026-11-17）: HIS 是第一个
  消费方但不是唯一可预期的对照系统；专名列在第二系统出现时要迁移改名，通用列免迁移。
  否决项：`his_code` 专名（第二系统加列/改名成本）；映射表
  `(item_id, system, code)`（v1 只有一个消费方，建表+维护面过重 — HERO）。
  EIS 参照系对照锚点在订单级（`clinicItemId`，字典同步模式），目录级无先例可抄，
  本列是 lumen 的目录级最小落法。
- **D2 — 不强制唯一**（用户裁决 2026-11-17）: 打包计费（一 HIS 收费项 ↔ 多院内
  检查项）是合法映射，硬唯一堵死；集成未落地前重复可经 keyword 检索人工发现。
  与 `exam_item_live_code_uidx`（院内 code 的 partial unique index）形成有意对比：
  那是自有编码空间，本列是外部系统空间。
- **D3 — additive V37**: V35（`exam_body_group_remark`）之后顺延；V36（workstation_department_icons，eis-workstation-icons change）已占用；
  `ALTER TABLE exam_item ADD COLUMN external_code varchar(32);` 一条语句，不触碰
  已应用迁移。列宽 32 对齐院内 `code`（外部 HIS 项目编码不会更长；如未来系统需要
  更长编码再提案加宽）。
- **D4 — 无索引**: 唯一性不做（D2）；keyword LIKE 不吃普通 btree，目录表量级
  （数百行/模态）全表扫描可接受。无查询路径即无索引 — HERO。
- **D5 — 1:1 copy 自动覆盖（update 链例外）**: `externalCode` 字段名在
  request/entity/VO 三侧一致；create 走 `BeanUtil.copyProperties(request, item, true)`
  自动携带；**update 走显式 `LambdaUpdateWrapper.set` 链**（D9 null-aware），须补一行
  `.set(ExamItem::getExternalCode, request.getExternalCode())`——否则 absent/present
  两种 update 都静默不写对照编码（实施时发现，2026-11-17）。VO 继承实体自动携带。
  另一行为改动是 keyword LIKE 分支补
  `.or().like(ExamItem::getExternalCode, query.getKeyword())`（`ExamItemServiceImpl:90`）。
- **D6 — 不新增分页过滤参数**: keyword 覆盖检索即可；对照维护走抽屉编辑 +
  表格列查看（web change），不做独立 filter。
- **D7 — 跨栈分工**: 本 change 只动 `lumen-sev`；抽屉字段/表格列/api client 由
  配套 `exam-item-external-code-web` 落地（后端先行的惯例顺序）。

## Risks / Notes

- `exam-item-common-filter-drop`（active）也改 `Exam Item Endpoints` requirement：
  本 delta 基于 post-filter-drop 文本（无 isCommon filter）书写，归档顺序
  filter-drop → 本 change，sync 无冲突；coordination 文档记录依赖。
- 共享测试库：新测试走 `SettingIntegrationTestBase` 基类前缀家族，清扫按
  hard-delete 裁决（2026-11-07）回填基类前缀清单。
