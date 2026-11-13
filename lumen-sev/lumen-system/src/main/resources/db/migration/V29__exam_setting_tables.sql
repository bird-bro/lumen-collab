-- V29__exam_setting_tables.sql - the exam-setting domain tables (exam-setting-api,
-- task 1.2 as amended by the 2026-11-13 merge ruling - spec deltas exam-setting
-- "Setting Schema Ownership" / "Exam Body Group Columns" / "Exam Body Columns" /
-- "Exam Method Columns" / "Exam Item Columns" / "Exam Item Method Binding" /
-- "Report Template Columns").
--
--   The six tables of the 检查设置 domain, hosted in the SYSTEM schema (the
--   2026-11-13 ruling dissolved the standalone lumen-setting service: one service =
--   one schema = one Flyway ledger - the former V1__setting_schema.sql in the
--   retired lumen-setting module lands here as V29 of the single ledger). The
--   domain keeps its own package (org.birdbro.lumen.setting, inside the
--   lumen-system module) and its own URL prefix (/api/v1/setting/**) - only the
--   service/schema host changed. Hospital-wide exam catalog (body groups FLAT,
--   bodies nested within a group by up_id - the 2026-11-12 Option-A split ruling
--   that dissolves RCP's upId=100000 synthetic root) and the report-template
--   knowledge base (one tree, type 1 公共 / 2 个人, flag 0 目录 / 1 模板). Billing +
--   performance-score weights (price / rate / tag / score_rep / score_aud) live on
--   exam_item per the user ruling 2026-11-12. Deliberate RCP deviations
--   (coordination exam-setting.md): NO denormalized groupId/methodValue/bodyValue
--   name copies - joins inside one schema - NO synthetic group root. Modality
--   values are dict-1021 strings, validated service-side (the vocabulary itself is
--   NOT migrated here - public.dict_catal is system-owned).
--
--   Conventions: serial Integer PK (house id ruling), EntityBase audit tail, zero
--   physical FKs (references are service-enforced, user_info.dep_id shape), soft
--   delete via deleted. Live-row identity is backed by PARTIAL unique indexes
--   (WHERE deleted = 0) so soft-deleted rows free their names/codes for reuse -
--   the service checks first (friendly A0400), the index is the race-safe backstop.
--
-- IDEMPOTENCE: re-runs as CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
-- no-ops (fresh databases create, migrated databases yield). Proven by
-- SettingSchemaSeedTests.migrationReExecutionIsARowCountNoOp. Comment lines carry NO
-- semicolons (the re-execution test splits statements on semicolons - a semicolon
-- inside a comment truncates a statement mid-body).

-- ================= 1) the body-part split (spec exam-setting) =================
CREATE TABLE IF NOT EXISTS exam_body_group (
    id                   serial PRIMARY KEY,
    modality             varchar(16)  NOT NULL,
    name                 varchar(64)  NOT NULL,
    sort                 integer,
    enable               integer      NOT NULL DEFAULT 1,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE INDEX IF NOT EXISTS exam_body_group_modality_idx ON exam_body_group (modality);
CREATE UNIQUE INDEX IF NOT EXISTS exam_body_group_live_name_uidx
    ON exam_body_group (modality, name) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS exam_body (
    id                   serial PRIMARY KEY,
    modality             varchar(16)  NOT NULL,
    group_id             integer      NOT NULL,
    up_id                integer,
    name                 varchar(64)  NOT NULL,
    code                 varchar(32),
    sort                 integer,
    enable               integer      NOT NULL DEFAULT 1,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE INDEX IF NOT EXISTS exam_body_group_id_idx ON exam_body (group_id);
CREATE INDEX IF NOT EXISTS exam_body_modality_idx ON exam_body (modality);

CREATE TABLE IF NOT EXISTS exam_method (
    id                   serial PRIMARY KEY,
    modality             varchar(16)  NOT NULL,
    name                 varchar(64)  NOT NULL,
    code                 varchar(32),
    sort                 integer,
    enable               integer      NOT NULL DEFAULT 1,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE INDEX IF NOT EXISTS exam_method_modality_idx ON exam_method (modality);
CREATE UNIQUE INDEX IF NOT EXISTS exam_method_live_code_uidx
    ON exam_method (modality, code) WHERE deleted = 0 AND code IS NOT NULL;

-- ================= 2) the exam item + its method bindings (spec exam-setting) =====
CREATE TABLE IF NOT EXISTS exam_item (
    id                   serial PRIMARY KEY,
    modality             varchar(16)  NOT NULL,
    exam                 varchar(200) NOT NULL,
    code                 varchar(32),
    body_id              integer      NOT NULL,
    price                numeric(10,2),
    rate                 numeric(4,2),
    tag                  integer,
    score_rep            integer,
    score_aud            integer,
    is_common            boolean      NOT NULL DEFAULT false,
    sort                 integer,
    enable               integer      NOT NULL DEFAULT 1,
    remark               varchar(255),
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE INDEX IF NOT EXISTS exam_item_modality_idx ON exam_item (modality);
CREATE INDEX IF NOT EXISTS exam_item_body_id_idx ON exam_item (body_id);
CREATE INDEX IF NOT EXISTS exam_item_common_idx ON exam_item (modality, is_common);
CREATE UNIQUE INDEX IF NOT EXISTS exam_item_live_code_uidx
    ON exam_item (modality, code) WHERE deleted = 0 AND code IS NOT NULL;

CREATE TABLE IF NOT EXISTS exam_item_method (
    id                   serial PRIMARY KEY,
    exam_id              integer      NOT NULL,
    method_id            integer      NOT NULL,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS exam_item_method_uidx
    ON exam_item_method (exam_id, method_id);
CREATE INDEX IF NOT EXISTS exam_item_method_method_id_idx ON exam_item_method (method_id);

-- ================= 3) the report-template knowledge base (spec exam-setting) ======
CREATE TABLE IF NOT EXISTS rep_temp (
    id                   serial PRIMARY KEY,
    type                 integer      NOT NULL,
    owner_uid            integer,
    modality             varchar(16)  NOT NULL,
    flag                 integer      NOT NULL,
    up_id                integer,
    name                 varchar(128) NOT NULL,
    finding              text,
    impression           text,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp,
    update_by            integer,
    update_time          timestamp
);
CREATE INDEX IF NOT EXISTS rep_temp_scope_idx ON rep_temp (type, modality, up_id);
