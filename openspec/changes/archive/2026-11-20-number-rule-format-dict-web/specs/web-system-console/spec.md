## MODIFIED Requirements

### Requirement: Number Rule Form and Delete

Rule create/edit SHALL use a system-console drawer (`destroyOnHidden`, controls disabled while saving, footer 取消/保存 at the end) with fields 前缀, 后缀, 编号长度, 是否带日期/时间戳, 格式 and a 规则类型 SELECT over the platform families (影像号 pat_id / 检查号 accession / 预约号 booking). When stamping is enabled, the 格式 field SHALL offer live items from the 「编号格式」 dictionary catalog (id 1029) and require a selection; each option value SHALL be the Java date/time pattern submitted in `stamp`. A successful submit SHALL close the drawer and refresh the table. Number-format validation (serial length bounds, prefix charset) SHALL surface on the offending field, and delete SHALL require confirmation:

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
