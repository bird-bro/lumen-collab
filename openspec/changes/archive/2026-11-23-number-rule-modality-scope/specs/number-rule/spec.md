# number-rule Delta

## MODIFIED Requirements

### Requirement: Number Rule Directory

The system service SHALL manage the platform's number-rule directory — a `number_rule`
table in the SYSTEM schema (the generic rule-family shape, one ACTIVE live rule per
family × modality scope backstopped by a partial unique index) exposed as rule CRUD at
`/api/v1/system/number-rules[/{id}]`, gated by the `system:number:*` button perms, with
the current families seeded at migration time. Each rule row carries a nullable
`modality` scope — the dict-1024 VALUE modality token (`CT` / `DR` / `US`, the same
stored shape as `org_device.type` and `exam_item.modality`); NULL means 全局, the
family's platform-wide shared pool:

#### Scenario: Rule CRUD enforces system perms

- **WHEN** an administrator creates, edits, or deletes a rule through the system
  number-rule endpoints
- **THEN** each write requires its `system:number:*` button perm
- **AND** a second active rule for the same family AND same scope (including two
  global rules of one family) is rejected
- **AND** an active rule of one family with a DIFFERENT scope is accepted

#### Scenario: Family defaults are seeded

- **WHEN** the migrations run on a fresh database
- **THEN** the 影像号, 登记号, and 预约号 families each carry one active GLOBAL rule
  row with usable defaults

#### Scenario: Scoped rule coexists with the family global rule

- **WHEN** the administrator creates an active rule for family 登记号 scoped to
  modality `CT` while a global 登记号 rule is active
- **THEN** both rules coexist (same family, different scopes)

### Requirement: Platform Number Issuance

The system service SHALL issue numbers through a REQUIRES_NEW row-lock burned-serial
issuer (the increment commits in its OWN transaction; a caller's later rollback BURNS
the number) exposed at the machine endpoint `POST /api/v1/system/number-rules/issue`
(body `{ruleType, modality?}` — `modality` optional, the dict-1024 value token):

#### Scenario: Concurrent issuance does not duplicate

- **WHEN** two issuances for the same family and scope race
- **THEN** both succeed with distinct serials and no duplicate number

#### Scenario: Stamp rule resets by day

- **WHEN** a stamped family's day token rolls over
- **THEN** the serial restarts at 1 and the stamp token advances

#### Scenario: Burned serial survives a caller rollback

- **WHEN** a caller fails after a successful issuance
- **THEN** the increment has committed — the number is burned and never reissued

#### Scenario: Scoped pools count independently

- **WHEN** the same family issues through a `CT`-scoped rule and a `DR`-scoped rule
- **THEN** each scope row advances its own `serial_cur` (CT0001, CT0002 ... and
  DR0001, DR0002 ... never interleave)

## ADDED Requirements

### Requirement: Modality-Scoped Issuance Resolution

The issuer SHALL resolve the active rule for a request `{ruleType, modality?}` by
specificity: an exact active rule of `(ruleType, modality)` when `modality` is present,
otherwise the family's global active rule (`modality` NULL); row locks apply to the
resolved row only. A request without `modality` always resolves the global rule. An
unknown or unconfigured modality token is NOT validated — it simply matches no scoped
rule and falls back:

#### Scenario: Exact scoped rule wins

- **WHEN** an issue request carries `modality=CT` and family 登记号 has an active
  `CT`-scoped rule
- **THEN** the number comes from that scoped rule's serial pool and format

#### Scenario: No scoped rule falls back to the family global rule

- **WHEN** an issue request carries `modality=US` and family 登记号 has no `US`-scoped
  rule but has an active global rule
- **THEN** the number comes from the global rule's serial pool and format

#### Scenario: Blank modality resolves the global rule

- **WHEN** an issue request omits `modality` (the legacy single-arg shape)
- **THEN** the family's global active rule issues the number

#### Scenario: No active rule anywhere refuses

- **WHEN** an issue request resolves neither a scoped nor a global active rule for its
  family
- **THEN** the issuance is refused with the system-state B0001 error and no number is
  issued
