# Spec Delta

## ADDED Requirements

### Requirement: Modality Catalog Resolution by Id
The setting pages (检查项目/检查部位/检查方法/报告模板) SHALL resolve the shared
modality dict catalog by its deterministic id 1024 through the items-by-id
endpoint - never by catalog name (dict-lookup-by-id ruling 2026-11-19: the id
never moves, the operator may rename the catalog).

#### Scenario: Pages load against the id anchor

- **WHEN** a setting page loads its 检查类型 filter options
- **THEN** the options come from the items-by-id lookup of catalog 1024, and the
  page loads its list against the real setting endpoints

#### Scenario: Catalog rename is invisible to the pages

- **WHEN** catalog 1024 is renamed (display-text change)
- **THEN** the setting pages keep loading the same vocabulary without code change
