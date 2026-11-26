# Proposal: patient-identity-card-projection

## Why

The US workbench's resolve backfill never fills 就诊卡号: the archive stores visit cards
as `patient_identifier` rows (`idType 'card'` — the resolve chain walks them), but the
internal identity projection (`InternalPatientController.toIdentity`) reads the
`PatientMaster` only, so `PatientIdentityResponse.cardNo` is structurally absent — the
web type and the backfill mapping were built anticipating the field
(`us-orders.ts:180`, the page's `identity.cardNo ?? null`). Everything else the archive
carries (idType/idNo/name/gender/birthDate/phone + the derived age) already backfills;
就诊卡号 is the only gap (患者来源/住院号/床号/身高/体重 are exam-time attributes the
archive deliberately does not carry).

## What Changes

- `PatientIdentityResponse` gains the OPTIONAL `cardNo` field (additive wire shape —
  consumers without it keep the strict shape).
- `toIdentity` projects the archive's LATEST live card identifier (`idType 'card'`,
  highest id — an archive accumulates visit cards per registration, the latest is the
  current one); a match through the card chain naturally re-projects that card.
- The resolve passthrough (us) forwards the Feign result verbatim — the cardNo rides
  with ZERO us code change; the web backfill mapping already exists. Candidates ride
  the same projection (the candidate modal gains the card display for free).
- **Out of scope**: candidates-modal UI changes (the field arrives, the modal layout
  stays); multi-card selection UI (the latest-card rule is server-owned).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `patient-domain`: the Internal Service Surface's identity projection carries the
  latest live card identifier as the optional `cardNo`.
