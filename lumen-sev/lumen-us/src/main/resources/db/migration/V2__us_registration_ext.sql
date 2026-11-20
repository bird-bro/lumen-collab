-- V2__us_registration_ext.sql - the US-specific registration extension table
-- (us-station-registration-sev task 1.3, design D2 and specs/us-station "US Registration
-- Extension"). The vendored kernel DDL (V1) stays verbatim - the US fields (user
-- ruling 2026-11-20: 孕检为 lumen 决策 #5, EIS 对照 US 登记无 EIS 特有字段) live HERE
-- as a 1:1 extension of the kernel order, reached through the kernel RegistrationHook
-- seam in later batches. Column set (design D2):
--   order_id          - the 1:1 anchor, itself the PRIMARY KEY (exam_order.id, same
--                       schema - the reference stays LOGICAL, no physical FK, the
--                       kernel/patient shape)
--   pregnancy_status  - 妊娠状态 varchar(16) (dict-backed value, optional)
--   gestational_weeks - 孕周 integer (weeks, optional)
--   note              - 备注 varchar(255) (optional)
-- plus the EntityBase audit set (deleted/create_by/create_time/update_by/update_time)
-- every house table carries. House DDL rules honored: serial-style logical references,
-- varchar values, and COMMENT LINES CARRY NO SEMICOLONS (the re-execution tests split
-- statements on the semicolon character after stripping comment lines - the V36
-- house lesson).
-- Idempotence: a fresh-schema CREATE (the ledger applies it once - the
-- flyway_schema_history guarantee), matching the vendored V1 shape.

CREATE TABLE us_registration_ext (
    order_id          integer      PRIMARY KEY,    -- exam_order.id 1:1 (logical reference)
    pregnancy_status  varchar(16),                 -- 妊娠状态 (dict value, optional)
    gestational_weeks integer,                     -- 孕周 (weeks, optional)
    note              varchar(255),                -- 备注 (optional)
    deleted           integer      NOT NULL DEFAULT 0,
    create_by         integer,
    create_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by         integer,
    update_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
