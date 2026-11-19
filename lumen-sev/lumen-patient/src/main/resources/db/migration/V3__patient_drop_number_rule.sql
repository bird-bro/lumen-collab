-- V3__patient_drop_number_rule.sql - the patient-side rule directory RETIRES
-- (number-rule-unification-sev task 3.2, user ruling 2026-11-18): the platform
-- number-rule table lives in the SYSTEM schema (V40 of the system ledger) behind the
-- system issuance API; the patient service issues pat_id through the NumberApi Feign
-- contract. The pat_id family's config row was SEEDED platform-side (system V40
-- defaults) - the live hospital config is re-entered on the migrated console page
-- (seed-defaults decision, NO cross-schema data migration). The row-lock issuer
-- implementation retired with the rewire; the burned-serial semantics survive
-- remotely (the platform commits the increment; a caller rollback burns the number).
--
-- House rule: COMMENT LINES CARRY NO SEMICOLONS (the V36 lesson - the re-execution
-- test splits statements on `;` and strips `--` comment lines).

DROP TABLE IF EXISTS patient_number_rule;
