## ADDED Requirements

### Requirement: Cancel Verb Glyph

<!-- user ruling 2026-11-26: the trash read as physical deletion, but a cancel is a
     state transition - the order stays for audit and lands in the 已取消 status. The
     shared ACTION_ICONS dictionary gains the `cancel` key; 删除 verbs keep `remove`. -->

Icon-only actions for 单据取消 verbs SHALL carry the ×-in-circle cancel glyph through
the shared ACTION_ICONS `cancel` key, and the cancel key SHALL NOT be used for
physical-deletion verbs.

#### Scenario: 单据取消 actions ride the cancel glyph

- **WHEN** a console surface renders an icon-only action for a 单据取消 verb (取消登记,
  取消预约 — any verb that transitions a persisted document into a cancelled status)
- **THEN** the action renders the ×-in-circle glyph (`CloseCircleOutlined` through the
  shared `cancel` ACTION_ICONS key) with the danger styling and the verb Tooltip

#### Scenario: 删除 verbs keep the trash glyph

- **WHEN** a console surface renders an icon-only action for a physical-deletion verb
- **THEN** the action renders the trash glyph (`remove`) — the `cancel` and `remove`
  keys never collapse into one another
