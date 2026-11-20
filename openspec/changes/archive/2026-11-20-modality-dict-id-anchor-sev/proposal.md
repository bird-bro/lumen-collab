# Proposal

## Why
模态词表消费者的锚点三态漂移：主规格锚 dict 1021（已退役）、实现锚目录名「检查类型」（V27 统一后**不存在**的目录名）、数据实际是 id 1024「设备类型」。单一瓶颈点 `DictModalityMapper.countLiveModality`（`c.name='检查类型'` 恒空 join）使 `ExamBodyGroupServiceImpl.requireModality` 全拒——ExamItem/ExamMethod/ExamBody/RepTemp 四个 ServiceImpl 共 12 处调用（列表+写入）全数失败，setting 域模态路径整体不可用。

## What Changes
- `DictModalityMapper` 校验 SQL 由 `c.name = '检查类型'` 改为 `c.id = 1024`（dict-lookup-by-id 裁决 2026-11-19：id 不动、name 可改）。
- javadoc 由「按名锚定（uk_dict_catal_name）」改写为「按 id 锚定」理由——原 javadoc 的 1021/1027 翻动史论据随裁决作废。
- 主规格同步：exam-setting「Modality Vocabulary」与 system-data「Dict Seed Data」的按名措辞收敛到 id 1024。
- API 契约、表结构、种子数据零变更。

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `exam-setting`: Modality Vocabulary 要求由按名锚定改为按 id 1024 锚定；「Unknown modality rejected」场景保留（超集），新增目录改名韧性场景。
- `system-data`: Dict Seed Data 的「检查类型」措辞与场景跟随 V27 统一后的现实（1024 单一模态目录、按 id 解析）；「审核权限」场景原样保留。

## Impact
- 代码：`lumen-system` setting 域 1 个文件（`DictModalityMapper`）+ 1 个新测试。
- 行为：setting 域全部模态路径（列表+写入）恢复可用；校验结果从「恒拒」变为「按 1024 词表接受」。
- 无新增依赖、无 Flyway 迁移、共享库 public.dict_* 直读语义不变。
- 跨栈协调：`openspec/coordination/modality-dict-id-anchor.md`（web 半边见 `modality-dict-id-anchor-web`）。
