## ADDED Requirements

### Requirement: Number Stamp Format Dict

The system data SHALL seed an enabled stamp-format dictionary catalog (id 1029) whose stable id anchors consumer lookups; the display name is operator-editable (seeded 「号码戳格式」, renamed 「编号格式」 by the follow-up rename migration). Each live detail's `value` SHALL be the Java date/time pattern stored verbatim in `number_rule.stamp`, and later patterns SHALL be maintainable through dictionary management.

#### Scenario: Seeded formats are available by catalog id

- **WHEN** the dict-items lookup requests the stamp-format catalog 1029 (编号格式)
- **THEN** it returns the enabled seeded patterns, including `yyMMdd`, `yyyyMMdd`, minute patterns, and second-level timestamp patterns

#### Scenario: Migration replay is guarded

- **WHEN** the V43 seed statements are replayed
- **THEN** existing seeded rows are unchanged and dictionary sequences remain past the deterministic ids
