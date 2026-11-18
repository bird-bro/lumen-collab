# Proposal

## Why

All three department workstations (radiology / US / endoscopy) need one shared patient archive, and the US workstation (next in line) cannot start without it: its registration flow's first step is patient resolution/archiving. The reference system's evidence (EIS deep-dive 2026-11-16, `lumen-reference/NOTES-us-workstation.md`) shows what happens without a patient domain: reuse trusts a client-supplied uid with no dedup fallback, card_no carries no uniqueness, the same patient data is physically stored in three tables, and correcting a patient means writing all three - the exact failures the 2026-11-04 patient-domain decisions (#1-#5) were made to prevent. Those decisions are complete but unbuilt; this change lands them.

## What Changes

- New backend service module `lumen-patient` (executable, port 8082, own `patient` schema + Flyway ledger, nginx `/api/v1/patient/**`), declared in the `lumen-sev` aggregator.
- Patient master archive, one person one record: `patient_master` (surrogate id = patientUid, `pat_id` image number issued at creation) + `patient_identifier` (multi-number table: card/physical/outpatient/inpatient/HIS ids, no hard uniqueness on card_no).
- Identity resolution chain API: `id_no` exact -> card_no -> soft-match candidates (name+phone / name+gender+birth) -> create new.
- Archive state machine PROVISIONAL -> RESOLVED: unnamed emergency placeholder rows issue image numbers normally; backfill triggers resolve-or-merge **minimal merge** (merged_into pointer + merge log, image number stays traceable).
- Image-number issuance rules: `patient_number_rule` table + atomic DB-row-lock issuance inside the creation transaction (no Redis dependency); rule management API.
- Event contract via **outbox table** (PATIENT_CHANGED / PATIENT_MERGED, payload jsonb, status PENDING): no MQ wiring in v1 (no MQ service deployed; consumers arrive with `lumen-core`).
- One console page: image-number rule management, mounted under the 设置 L1 menu; page calls `/api/v1/patient/**`.
- Menu + button-permission seeds (`patient:*` family) written cross-schema into `system.menu_info`/`role_menu` (V22 workstation-node precedent).

## Capabilities

### New Capabilities

- `patient-domain`: the patient master index domain - archive model, identity resolution, provisional/merge state machine, image-number issuance, changed/merged event outbox contract, and the rule-management console surface.

### Modified Capabilities

- `system-data`: menu IA gains the number-rule management page under the 设置 L1 (menu node + button perms + role_menu carry rules).
- `web-system-console`: the console gains the number-rule page shell and drawer form behaviors.

## Impact

- **Backend**: new Maven module `lumen-sev/lumen-patient` (`org.birdbro.lumen.patient`, app class `PatientApplication`); aggregator pom gains one module; 5 new tables in a new `patient` schema (own Flyway ledger V1+); no MQ, no hard Redis dependency; test-server PG reused per the existing data-layer test pattern.
- **Frontend**: one new console page (number-rule management) under 设置 L1, plus menu/perms seeds consumed by the existing console shell.
- **Coordination**: port table in `init-project-skeleton.md` gains 8082.
- **Not in scope**: MQ consumers/publisher (outbox rows stay PENDING), accession (exam number) issuance (belongs to `lumen-core`), HIS patientId auto-mapping (table carries it; integration change consumes), full manual archive merge (v2), any patient UI beyond the rule page (resolution/merge UIs live in future workstation changes).
