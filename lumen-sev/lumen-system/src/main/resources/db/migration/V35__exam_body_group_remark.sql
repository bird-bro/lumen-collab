-- V35__exam_body_group_remark.sql - 备注 columns for the exam-setting body and
-- body group (user ruling 2026-11-16: the 分组/部位 drawers gain the 备注 field
-- without a proposal; dedicated varchar(255) columns following the exam_item
-- remark precedent). Plain ADD COLUMN IF NOT EXISTS - no backfill, no content
-- guard; V29 (the creator) stays frozen (never rewrite an applied migration).

ALTER TABLE exam_body ADD COLUMN IF NOT EXISTS remark varchar(255);
ALTER TABLE exam_body_group ADD COLUMN IF NOT EXISTS remark varchar(255);
