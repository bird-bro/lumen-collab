# Spec Delta

## MODIFIED Requirements

### Requirement: Modality Vocabulary

Every catalog and template `modality` value SHALL be a value of the shared modality
dict catalog, resolved by its deterministic catalog ID **1024** (dict-lookup-by-id
ruling 2026-11-19: the id never moves; the operator may rename the catalog freely -
the name 「设备类型」 is display text, never an anchor). The value = the modality
string (DR/CT/MR/DSA/US/ENDO); the service SHALL NOT define a parallel modality
vocabulary. Write requests referencing an unknown modality value SHALL be rejected.

#### Scenario: Unknown modality rejected

- **WHEN** a create request carries a modality value absent from the modality
  catalog id 1024
- **THEN** the service rejects the write with a validation error

#### Scenario: Catalog rename does not affect validation

- **WHEN** catalog 1024 is renamed (name is display text only)
- **THEN** modality validation still resolves through the id anchor and keeps
  accepting exactly the catalog's enabled values
