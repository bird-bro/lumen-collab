-- V49__number_rule_modality_scope.sql - the modality SCOPE dimension on the platform
-- number-rule directory (number-rule-modality-scope task 1.1, design D1/D2/D8):
-- number_rule.modality stores the dict-1024 VALUE modality token (CT / DR / US, the
-- same stored shape as org_device.type and exam_item.modality) and NULL means 全局,
-- the family's platform-wide shared pool. Exactly one ACTIVE live rule per
-- family × scope: the partial unique index recreates as the COALESCE expression form
-- (PG treats NULLs as distinct in a plain partial unique index, so the NULL-safe
-- uniqueness keys on COALESCE(modality, '') - version-proof, no PG15 NULLS NOT
-- DISTINCT coupling, design D2).
--
-- No seed changes, no data backfill - every existing row simply becomes its family's
-- GLOBAL rule untouched. Comment lines carry NO semicolons (the re-execution-test
-- split discipline). Guards keep the statements re-runnable after a history-row
-- repair (the V40/V46 discipline).

ALTER TABLE number_rule ADD COLUMN IF NOT EXISTS modality varchar(16);

DROP INDEX IF EXISTS uk_number_rule_active;

CREATE UNIQUE INDEX IF NOT EXISTS uk_number_rule_active
    ON number_rule (rule_type, COALESCE(modality, '')) WHERE active = 1 AND deleted = 0;
