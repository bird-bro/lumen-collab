-- V40__number_rule_platform.sql - the PLATFORM number-rule directory (number-rule-
-- unification-sev task 2.3, user ruling 2026-11-18): ONE generic rule-family table in
-- the SYSTEM schema behind ONE row-lock burned-serial issuance mechanism, replacing
-- the per-domain duplicates (the patient-side patient_number_rule retires with the
-- patient ledger's drop migration, the kernel never carried one). The 系统管理域
-- (org.birdbro.lumen.manage) owns the surface, perms system:number:*.
--
-- REPAIR-PATH NOTE: the patient seed V2 guards its inserts by the OLD perms strings -
-- a ledger repair that re-runs V2 RESURRECTS the old-shape rows; replay V40 AFTER V2
-- (content-anchored UPDATEs re-move whatever V2 resurrects) and the state converges.
--
-- House DDL rules honored: audit column set per EntityBase, serial integer ids,
-- varchar enum names, COMMENT LINES CARRY NO SEMICOLONS (the V36 house lesson - the
-- re-execution test splits statements on `;` and strips `--` comment lines).

CREATE TABLE number_rule (
    id            serial PRIMARY KEY,
    rule_type     varchar(32) NOT NULL,
    prefix        varchar(32) NOT NULL DEFAULT '',
    suffix        varchar(32) NOT NULL DEFAULT '',
    serial_length integer     NOT NULL DEFAULT 4,
    serial_cur    integer     NOT NULL DEFAULT 0,
    is_stamp      integer     NOT NULL DEFAULT 0, -- 1 = the serial resets per stamp period
    stamp         varchar(16),                    -- date stamp format, Java pattern (v1: yyMMdd)
    stamp_value   varchar(16),                    -- last-issued stamp token (NULL until first issuance)
    active        integer     NOT NULL DEFAULT 1,
    deleted       integer     NOT NULL DEFAULT 0,
    create_by     integer,
    create_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     integer,
    update_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_number_rule_active ON number_rule (rule_type) WHERE active = 1 AND deleted = 0;

-- Family-default seeds (guarded inserts keep the migration re-runnable after a
-- history-row repair): the pat_id default mirrors the retired patient seed 1:1; the
-- exam-side families mirror the kernel canonical DDL's seeds. Live hospital config
-- (e.g. a custom pat_id prefix) is re-entered on the console page - NO cross-schema
-- data migration (user-approved seed-defaults decision).
INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,
                         is_stamp, stamp, stamp_value, active)
SELECT 'pat_id', '', '', 4, 0, 1, 'yyMMdd', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM number_rule WHERE rule_type = 'pat_id');

INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,
                         is_stamp, stamp, stamp_value, active)
SELECT 'accession', 'A', '', 6, 0, 1, 'yyMMdd', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM number_rule WHERE rule_type = 'accession');

INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,
                         is_stamp, stamp, stamp_value, active)
SELECT 'booking', 'B', '', 4, 0, 1, 'yyMMdd', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM number_rule WHERE rule_type = 'booking');

-- Menu seed move (number-rule-unification-sev task 2.3): the 号码规则 C node (id 976)
-- leaves the 设置 L1 患者配置 group and joins the 系统 (id 15) group - the
-- config-catalog group (数据字典/系统菜单 live there) - with the system:number:*
-- perms family and the /system/number-rules frontend route segment. The now-empty
-- 患者配置 group (id 975, sole child was 976) retires. The F children (977/978/979)
-- keep their ids - only their perms strings migrate.
UPDATE system.menu_info SET up_id = 15, path = 'number-rules', perms = 'system:number:list', sort = 3
 WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0;
UPDATE system.menu_info SET perms = 'system:number:create'
 WHERE id = 977 AND perms = 'patient:rule:create' AND deleted = 0;
UPDATE system.menu_info SET perms = 'system:number:update'
 WHERE id = 978 AND perms = 'patient:rule:update' AND deleted = 0;
UPDATE system.menu_info SET perms = 'system:number:delete'
 WHERE id = 979 AND perms = 'patient:rule:delete' AND deleted = 0;
UPDATE system.menu_info SET deleted = 1
 WHERE id = 975 AND type = 'M' AND title = '患者配置' AND deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.menu_info c
                    WHERE c.up_id = 975 AND c.deleted = 0);
