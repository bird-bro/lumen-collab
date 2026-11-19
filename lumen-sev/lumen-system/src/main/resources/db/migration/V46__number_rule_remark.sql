-- V46__number_rule_remark.sql - 备注 column for the platform number-rule table
-- (user ruling 2026-11-19: the rule drawer gains the 备注 field; dedicated
-- varchar(255) column following the V38 exam-method remark precedent - same
-- direct-add-without-proposal ruling). Plain ADD COLUMN IF NOT EXISTS - no
-- backfill, no content guard; V40 (the creator) stays frozen.

ALTER TABLE number_rule ADD COLUMN IF NOT EXISTS remark varchar(255);
