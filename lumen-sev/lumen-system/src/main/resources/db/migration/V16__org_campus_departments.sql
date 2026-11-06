-- V16__org_campus_departments.sql - controlled on-premise hospital/campus model
-- (org-management-redesign-api).
--
-- The legacy model allowed arbitrary multi-root hierarchies and attached departments to
-- any org node. v1 on-premise deployments need one hospital root, campus children, and
-- departments anchored to a live campus. Existing department ids and codes stay stable;
-- only their owning campus changes during migration.

-- 1) Organization profile/lifecycle fields.
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS code           varchar(32);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS short_name    varchar(64);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS status        integer     NOT NULL DEFAULT 1;
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS province_code varchar(32);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS city_code     varchar(32);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS district_code varchar(32);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS address       varchar(255);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS contact       varchar(64);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS phone         varchar(32);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS email         varchar(128);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS logo_url      varchar(255);
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS sort          integer     NOT NULL DEFAULT 0;
ALTER TABLE org_info ADD COLUMN IF NOT EXISTS is_default    integer     NOT NULL DEFAULT 0;

-- 2) Backfill stable organization codes. The original product seed uses the short
--    bootstrap code; all other legacy nodes receive a deterministic generated code.
UPDATE org_info
   SET code = 'ORG-' || id
 WHERE code IS NULL OR btrim(code) = '';

UPDATE org_info
   SET code = 'HOSPITAL', name = '未命名医院'
 WHERE id = 1
   AND up_id = 0
   AND deleted = 0
   AND code = 'ORG-1'
   AND name = '示例医院';

ALTER TABLE org_info ALTER COLUMN code SET NOT NULL;

-- 3) Department lifecycle status. Existing and newly created departments are enabled.
ALTER TABLE org_dep ADD COLUMN IF NOT EXISTS status integer NOT NULL DEFAULT 1;
UPDATE org_dep SET status = 1 WHERE status IS NULL;

-- 4) Ensure every live hospital root has one default campus. Reuse an existing
--    总院区 when possible, otherwise create a deterministic bootstrap campus.
UPDATE org_info campus
   SET is_default = 1
 WHERE campus.deleted = 0
   AND campus.up_id <> 0
   AND campus.up_id IN (
        SELECT root.id FROM org_info root
         WHERE root.up_id = 0 AND root.deleted = 0
       )
   AND campus.name = '总院区'
   AND NOT EXISTS (
        SELECT 1 FROM org_info existing_default
         WHERE existing_default.up_id = campus.up_id
           AND existing_default.deleted = 0
           AND existing_default.is_default = 1
       );

INSERT INTO org_info
    (up_id, level, type, name, code, status, sort, is_default)
SELECT
    root.id,
    2,
    1,
    '总院区',
    'CAMPUS-' || root.id,
    1,
    0,
    1
  FROM org_info root
 WHERE root.up_id = 0
   AND root.deleted = 0
   AND NOT EXISTS (
        SELECT 1 FROM org_info campus
         WHERE campus.up_id = root.id
           AND campus.deleted = 0
           AND campus.is_default = 1
       );

-- 5) Move departments attached directly to hospital roots onto the root's default
--    campus. Department ids, codes, and other attributes remain unchanged.
UPDATE org_dep department
   SET org_id = campus.id,
       status = 1
  FROM org_info campus
 WHERE department.deleted = 0
   AND campus.deleted = 0
   AND campus.type = 1
   AND campus.is_default = 1
   AND campus.up_id = department.org_id
   AND EXISTS (
        SELECT 1 FROM org_info hospital
         WHERE hospital.id = campus.up_id
           AND hospital.up_id = 0
           AND hospital.deleted = 0
       );

-- 6) Replace global department-code uniqueness with campus-scoped uniqueness.
DROP INDEX IF EXISTS uk_org_dep_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_dep_org_code
    ON org_dep (org_id, code)
 WHERE deleted = 0;

-- Organization code is unique among live siblings. The up_id scope supports a
-- hospital root (one sibling group) and its campuses (another sibling group).
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_info_up_code
    ON org_info (up_id, code)
 WHERE deleted = 0;

-- At most one default campus per hospital.
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_info_default_campus
    ON org_info (up_id, is_default)
 WHERE deleted = 0
   AND up_id <> 0
   AND is_default = 1;
