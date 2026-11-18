-- V39__rep_temp_content.sql - structured section content on rep_temp
-- (rep-template-structured-content design D5): a jsonb column carrying the
-- department-agnostic section model, GIN-indexed per the flexible-structures
-- architecture rule (future measure-containment template lookups; write volume
-- here is low, so the index overhead is negligible). D1 exclusivity is a
-- WRITE-PATH (service) rule, not a DB constraint - "the prose pair is not read"
-- cannot be expressed as a CHECK; existing prose rows stay untouched (content
-- NULL = prose). ADD COLUMN IF NOT EXISTS + CREATE INDEX IF NOT EXISTS keep the
-- migration idempotent against shared-test-server re-runs.

ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb;

CREATE INDEX IF NOT EXISTS rep_temp_content_gin ON rep_temp USING gin (content);
