-- V37__exam_item_external_code.sql - 通用对照编码 on exam_item (user rulings
-- 2026-11-17, exam-item-external-code): the catalog-level anchor for
-- external-system comparison - HIS is the first consumer, the future
-- lumen-integration HL7v2 route reads it. Nullable varchar(32) aligned with the
-- 院内 code; NO index and NO uniqueness - 打包计费 (one HIS charge item ↔
-- several catalog items) is a legal multi-to-one mapping, in deliberate
-- contrast to the exam_item_live_code_uidx partial unique on the 院内 code
-- space. Plain ADD COLUMN IF NOT EXISTS - no backfill, no content guard; V29
-- (the creator) stays frozen (never rewrite an applied migration).

ALTER TABLE exam_item ADD COLUMN IF NOT EXISTS external_code varchar(32);
