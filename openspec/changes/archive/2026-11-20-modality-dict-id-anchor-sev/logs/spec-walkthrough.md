# Spec walkthrough — modality-dict-id-anchor-sev (task 2.2)

Date: 2026-11-20 · Reviewer: implementer pass (verify.md L2 素材)

## exam-setting / Modality Vocabulary (MODIFIED)

- Delta 全文整块搬运自主规格（`openspec/specs/exam-setting/spec.md` L161-173），仅改锚点
  措辞：按名 `uk_dict_catal_name` → 按 id **1024**；场景 "Unknown modality rejected"
  的 WHEN 由 "absent from dict 1021" 更新为 "absent from the modality catalog
  id 1024"——语义保留（未知值拒绝），超集成立。
- 新增场景 "Catalog rename does not affect validation" 属超集扩展；实现侧结构保证：
  SQL `WHERE c.id = 1024` 无名字依赖（`DictModalityMapper.countLiveModality`）。
- 实现一致性：SQL 已切换（commit 前 diff），锚点测试
  `DictModalityMapperTests` RED→GREEN 锁定（logs/test-mapper-red.log → green.log）。

## system-data / Dict Seed Data (MODIFIED)

- 全场景超集核对：Fresh database / Re-run no-op / **Exam-type vocabulary renders
  abbreviations（WHEN 更新为 id 1024 lookup，THEN 不变）** / Audit-authority（原样）。
- 描述段的修订仅限「检查类型」条款：标注 V26 种 1021 → V27 统一退役 → 词表唯一载体
  1024「设备类型」、消费方按 id 锚定。「审核权限」（1022）条款原样。
- 数据侧一致性：共享库实查（2026-11-20）1024=「设备类型」6 项启用
  （DR/CT/MR/DSA/US/ENDO）、1022「审核权限」在位——delta 描述与库态一致。
- 与既有要求的相容性：system-data "Device and Room Dict Catalogs"（1024 唯一模态目录）
  与本 delta 同向，无冲突；seed 守卫测试 `AuthDeviceRoomSeedTests` 的
  「检查类型保持退役」断言与本 delta 一致（测试基座卫兵已同步停造 1027 目录）。

## 结论

两处 MODIFIED 均为超集、与实现和共享库态一致；无 REMOVED/RENAMED。
