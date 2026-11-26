## ADDED Requirements

### Requirement: Birth Date Capture

The US registration create chain SHALL carry an optional birth date end to end:
`RegistrationCreateRequest.birthDate` (nullable) flows through the kernel port
request into the resolve-or-register contract, where the patient domain stores
it on the created walk-in archive and treats it as a soft-match key. The birth
date lives on the archive only — the order snapshot stays age-only and the
order view does not surface a birth date.

#### Scenario: Walk-in birth date joins the archive chain

- **WHEN** an order is created whose body carries `birthDate`
- **THEN** the resolve-or-register contract sent to the patient domain carries
  that birth date as a soft-match key and the created archive holds it
- **AND** the create succeeds and returns the derived state as before

#### Scenario: Birth date is optional

- **WHEN** an order is created whose body carries no `birthDate`
- **THEN** the contract's birth date is null and archive creation behaves
  exactly as before (PROVISIONAL/RESOLVED resolution unchanged)
- **AND** the order snapshot carries age only — no birth date field appears on
  the snapshot or the order view
