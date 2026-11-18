# Spec Delta

## ADDED Requirements

### Requirement: Number Rule Page Shell

The 号码规则 page SHALL render a paged rule table (规则类型, 前缀, 后缀, 流水长度,
日期戳, 当前流水, 状态) against the real backend list endpoint with toolbar affordances
gated by the `patient:rule:*` button perms. The page SHALL mount under the 设置 L1's
「患者配置」 group per the menu seeds and follow the console page shell patterns.

#### Scenario: Page loads against the real backend

- **WHEN** an administrator opens the 号码规则 page
- **THEN** the table queries the real list endpoint and renders the active rule row
  with its current serial value

#### Scenario: Toolbar follows button perms

- **WHEN** the signed-in user lacks `patient:rule:create`
- **THEN** the 新建 affordance is hidden or disabled and no create drawer opens

### Requirement: Number Rule Form and Delete

Rule create/edit SHALL use a drawer (close control at the top-right end placement,
mask click does not close) with fields 前缀, 后缀, 流水长度, 是否带日期戳 and the
规则类型 fixed to 影像号 in v1. A successful submit SHALL close the drawer and refresh
the table. Number-format validation (serial length bounds, prefix charset) SHALL surface
on the offending field, and delete SHALL require confirmation.

#### Scenario: Create a stamped rule

- **WHEN** the administrator creates a rule with a prefix, 4-digit serial length, and
  the date stamp enabled
- **THEN** the drawer posts to the real create endpoint, closes on success, and the
  new row shows the stamp flag

#### Scenario: Invalid serial length surfaces on the field

- **WHEN** the form is submitted with an out-of-range serial length
- **THEN** the error renders on the 流水长度 field and the drawer stays open

#### Scenario: Delete requires confirmation

- **WHEN** the administrator deletes a rule
- **THEN** a confirmation step runs first and a successful delete removes the row from
  the table
