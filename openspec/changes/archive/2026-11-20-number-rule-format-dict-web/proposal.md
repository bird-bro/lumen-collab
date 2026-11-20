# Proposal: number-rule-format-dict-web

## Why
号码规则抽屉需要与其他系统抽屉保持一致，并且格式不能再固定为日期模式，应支持日期、时间戳等多种由数据字典维护的选项。

## What Changes
- 新建/编辑抽屉按 `布局与保存状态` 对齐系统抽屉规范，`前缀/后缀` 使用紧凑双列，控件全宽。
- `stamp` 改为字典「编号格式」（catalog 1029，id 锚定）驱动的 Select；开启日期/时间戳后必选，编辑回显现有模式，保存携带所选模式。
- 抽屉 `流水长度` 文案与表格统一为 `编号长度`；格式预览按所选 Java 模式生成。

## Affected Stacks
- single-stack: lumen-web

## Depends-On
- `number-rule-format-dict-sev` (dict seed deployed before functional verification)

## Scope
- In scope: `/system/number-rules` create/edit drawer and dict-backed format options.
- Out of scope: issuance API, rule table data model, format reset-policy changes.

## Success Criteria
- Drawer lists dict items, submits the selected `stamp`, and requires it when stamping is enabled.
- Existing rows echo their stored format.
- Dict lookup failure degrades to an empty Select without breaking the rule table.

## Constraints
- Real backend only; no mock layer.
- Keep platform rule families immutable and serial-length validation on the field.

## Risks
- Missing/unconfigured dict → Select shows 「格式字典未配置」 and blocks submit until configured.
