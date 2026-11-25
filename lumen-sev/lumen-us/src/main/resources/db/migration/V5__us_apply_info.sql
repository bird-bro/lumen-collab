-- V5__us_apply_info.sql - the HIS application-origin fields on the US extension table
-- (us-apply-info-sev; coordination parent openspec/coordination/us-apply-info.md,
-- user request 2026-11-24): the exam application (检查申请单 / 临床检查医嘱) is
-- normally opened by an outpatient/inpatient doctor in the HIS system - 请检信息
-- records the origin so the lumen order remembers who requested the exam:
--   apply_dept      - 申请科室 varchar(64) (the HIS opening department, free text)
--   apply_doctor    - 申请医生 varchar(64) (the HIS opening doctor, free text)
--   apply_time      - 申请时间 varchar(19) (raw 'yyyy-MM-dd HH:mm:ss' text as entered;
--                     informational only - no parse policy, no state semantics in v1)
-- plus the RESERVED id anchors (user ruling 2026-11-24: names alone are not enough -
-- the join anchors ride along once an HIS feed or a lumen picker surfaces them):
--   apply_dept_id   - varchar(64) (the HIS/lumen department id behind apply_dept)
--   apply_doctor_id - varchar(64) (the HIS/lumen doctor id behind apply_doctor)
-- varchar(64) ids: HIS codes are arbitrary token strings and lumen integer ids
-- serialize into them losslessly. Free text by coordination ruling: HIS identities
-- are external (HIS departments and doctors are not lumen org_dep / user rows - no
-- dict catalog, no id anchoring).
-- Nullable ADD COLUMN IF NOT EXISTS on the ledger table (the shared-DB hand-apply
-- discipline, V6 precedent). ALTER ADD COLUMN appends each column AFTER the audit
-- tail - the column-set ruling test pins THAT order (D2 set, audit tail, origin
-- trio, reserved ids). COMMENT LINES CARRY NO SEMICOLONS (the V2 house lesson -
-- the re-execution tests split statements on the semicolon character after stripping
-- comment lines).

ALTER TABLE us_registration_ext ADD COLUMN IF NOT EXISTS apply_dept      varchar(64);
ALTER TABLE us_registration_ext ADD COLUMN IF NOT EXISTS apply_doctor    varchar(64);
ALTER TABLE us_registration_ext ADD COLUMN IF NOT EXISTS apply_time      varchar(19);
ALTER TABLE us_registration_ext ADD COLUMN IF NOT EXISTS apply_dept_id   varchar(64);
ALTER TABLE us_registration_ext ADD COLUMN IF NOT EXISTS apply_doctor_id varchar(64);
