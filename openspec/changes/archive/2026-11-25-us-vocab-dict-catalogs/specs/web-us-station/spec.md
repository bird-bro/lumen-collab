## ADDED Requirements

### Requirement: Registration Workbench Dict-Resolved Vocabularies

The US registration workbench SHALL resolve its four vocabularies from the dict
system through the id-anchored session-cached lookup (`dictItemsById`): 性别 from
catalog 1004 (stored token = the dict numeric code), 证件类型 from catalog 1006,
患者来源 from catalog 1023, and 妊娠状态 from catalog 1002 (stored token = the
dict value text for the three string fields). The page SHALL NOT carry hard-coded
option constants for these vocabularies. The wide-list 患者类型 filter SHALL ride
the same 患者来源 catalog. A failed dict fetch SHALL degrade to an empty option
set with one error message and never block the workbench.

#### Scenario: Form selects render dict-driven options

- **WHEN** the workbench mounts and the dict lookups succeed
- **THEN** the gender radios render the catalog-1004 labels with the dict codes as values
- **AND** the 证件类型 / 患者来源 / 妊娠状态 selects render the catalog value texts
  as both option value and label
- **AND** the wide-list 患者类型 filter offers the 患者来源 catalog values

#### Scenario: Pregnancy cell conditions on the dict female code

- **WHEN** the gender value equals the dict female code (100402)
- **THEN** the 妊娠 cell renders; any other code or an unset gender keeps it hidden
- **AND** the 孕周 input disables while 妊娠状态 is 未孕 or 未知

#### Scenario: Dict failure degrades without blocking

- **WHEN** a dict lookup fails
- **THEN** the page shows one error message and renders the affected control with
  no options (creation still possible without the optional fields)
- **AND** no hard-coded fallback option set reappears
