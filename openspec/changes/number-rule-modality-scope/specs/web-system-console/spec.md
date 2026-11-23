# web-system-console Delta

## MODIFIED Requirements

### Requirement: Number Rule Page Shell

The 号码规则 page SHALL render a paged rule table (规则类型, 适用范围, 前缀, 后缀, 流水长度,
日期戳, 当前流水, 状态) against the SYSTEM service's list endpoint
(`/api/v1/system/number-rules`) with toolbar affordances gated by the `system:number:*`
button perms. The page SHALL mount under the 系统管理 subtree per the migrated menu
seeds (the 患者配置 entry retires) and follow the console page shell patterns. The
适用范围 column SHALL render 全局 for a NULL-modality rule and the modality token for a
scoped rule:

#### Scenario: Page loads against the real backend

- **WHEN** an administrator opens the 号码规则 page
- **THEN** the table queries the system service's list endpoint and renders each
  family's active rule row with its current serial value

#### Scenario: Toolbar follows button perms

- **WHEN** the signed-in user lacks `system:number:create`
- **THEN** the 新建 affordance is hidden or disabled and no create drawer opens

#### Scenario: Scope column distinguishes global and scoped rows

- **WHEN** the list renders a NULL-modality rule and a `CT`-scoped rule of one family
- **THEN** the 适用范围 column shows 全局 on the former and the modality token on the latter

### Requirement: Number Rule Form and Delete

Rule create/edit SHALL use a system-console drawer (`destroyOnHidden`, controls disabled while saving, footer 取消/保存 at the end) with fields 适用范围, 前缀, 后缀, 编号长度, 是否带日期/时间戳, 格式 and a 规则类型 SELECT over the platform families (影像号 / 检查号 / 预约号). The 适用范围 field SHALL be a SELECT defaulting to 全局 whose modality options come from the dict-1024 catalog loaded through the id-anchored items endpoint (rendered by their value token — the dict-1024 single-text model where the token IS the display name — and submitted as that same token, the modality-string storage shape). When stamping is enabled, the 格式 field SHALL offer live items from the 「编号格式」 dictionary catalog (id 1029) and require a selection; each option value SHALL be the Java date/time pattern submitted in `stamp`. A successful submit SHALL close the drawer and refresh the table. Number-format validation (serial length bounds, prefix charset) SHALL surface on the offending field, and delete SHALL require confirmation:

#### Scenario: Create a rule with a dict-backed stamp format

- **WHEN** the administrator enables 日期/时间戳, chooses `yyyyMMdd` from 「编号格式」, and submits a valid create body
- **THEN** the drawer posts `stamp=yyyyMMdd`, closes on success, and the refreshed row shows that format

#### Scenario: Create a stamped rule

- **WHEN** the administrator creates a rule with a prefix, 4-digit serial length, and
  the date stamp enabled
- **THEN** the drawer posts to the system create endpoint, closes on success, and the
  new row shows the stamp flag

#### Scenario: Edit echoes the stored format

- **WHEN** an administrator opens a stamped rule for edit
- **THEN** the 格式 Select preselects the rule's stored `stamp` value

#### Scenario: Missing format blocks submit

- **WHEN** stamping is enabled but no format is selected
- **THEN** the validation message renders on the 格式 field and the drawer stays open

#### Scenario: Invalid serial length surfaces on the field

- **WHEN** the form is submitted with an out-of-range 编号长度
- **THEN** the error renders on the 编号长度 field and the drawer stays open

#### Scenario: Delete requires confirmation

- **WHEN** the administrator deletes a rule
- **THEN** a confirmation step runs first and a successful delete removes the row from the table

#### Scenario: Rule type selects the platform family

- **WHEN** the administrator opens the create drawer
- **THEN** the 规则类型 field offers the platform families (影像号/检查号/预约号) instead of a fixed 影像号 value

#### Scenario: Scope defaults to global and submits the token

- **WHEN** the administrator opens the create drawer
- **THEN** the 适用范围 Select preselects 全局, and choosing a modality option submits
  that option's dict-1024 value token in `modality` (choosing 全局 submits NULL)

#### Scenario: Edit echoes the stored scope

- **WHEN** an administrator opens a scoped rule for edit
- **THEN** the 适用范围 Select preselects the rule's stored modality (rendered as its
  value token — the dict-1024 single-text model; a token outside the loaded catalog
  echoes raw)

#### Scenario: Scoped duplicate-active rejection surfaces verbatim

- **WHEN** the administrator activates a rule whose family × scope already has another
  active rule
- **THEN** the server's duplicate-active rejection renders verbatim and the drawer
  stays open
