# Design: us-station-registration-web

## Decisions

- **Route wiring**: `src/pages/us/registration/index.tsx` keyed by the seeded component key; the
  workstation↔route mapping registers statically in the router (decision #8 — compile-time lock,
  no EIS-style string maps). Read the existing workstation route registration shape at apply.
- **`PatientDemographicsForm`** lives in `src/components/` (decision #5 shared component): the
  demographics field group (name/gender/birth/phone/id_type/id_no/card_no/address…— fields read
  from `PatientArchiveSaveRequest`'s shape at apply) with the 急诊占位 (PROVISIONAL) toggle; US
  page composes it today, radiology/endo later.
- **API clients**: `src/api/us-orders.ts` typed from the us contract (resolve passthrough, create,
  page, register, cancel); the patient domain is NEVER called directly from this page — all
  patient data flows through the us endpoints (server-side adapter).
- **Resolve flow**: a single lookup bar (id_no / card_no switch) → exact match fills the form;
  soft-match opens the candidate modal (name+phone / name+gender+birth rows); empty result opens
  the demographics form. The chosen identity (or new-archive body) rides the create submit.
- **Order form**: 检查项目 multi-select from the setting item catalog filtered to the US modality
  (client-side filter over the catalog payload — the setting API stays untouched); US extension
  block (妊娠状态 select / 孕周 number / 备注) maps 1:1 to `us_registration_ext`;急诊 flag switch.
  Success toast carries the returned accession.
- **List & actions**: console-shell table kit (ConsoleTable/ConsolePagination precedent), state
  tags per the kernel enum, 登记 (no confirm), 取消 (Popconfirm + reason input — drawer-confirm
  precedent), errors verbatim via `ApiError.msg`.
- **Tests**: Vitest with `vi.mock('@/api/us-orders')` seam stubs (page tests never serve runtime
  data — ruling 2026-11-05); cover resolve paths, create payload incl. ext, transitions, cancel
  reason, perm-gated affordances.

## Risks / Trade-offs

- Setting-catalog modality filtering is client-side in v1 (small catalog); a server-side filter
  param is a future API addition if the catalog grows.
- The US extension block is registration-only until the report/execution domains consume it.
