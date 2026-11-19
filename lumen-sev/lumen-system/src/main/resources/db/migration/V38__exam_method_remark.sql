-- V38__exam_method_remark.sql - 备注 column for the exam-setting method table
-- (user ruling 2026-11-18: the 检查方法 drawer gains the 备注 field without a
-- proposal; dedicated varchar(255) column following the V35 body/body-group
-- remark precedent). Plain ADD COLUMN IF NOT EXISTS - no backfill, no content
-- guard; V29 (the creator) stays frozen (never rewrite an applied migration).

ALTER TABLE exam_method ADD COLUMN IF NOT EXISTS remark varchar(255);
