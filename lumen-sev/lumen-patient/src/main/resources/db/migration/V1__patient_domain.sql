-- V1__patient_domain.sql - lumen-patient five-table EMPI ledger (patient-domain task 1.3).
--
-- Binding DDL design (coordinator-decided from design D1/D3/D6 + design.md D2/D4):
--   D1 (issuance = DB row lock): patient_number_rule carries the config parameters
--       AND the hot serial_cur counter - incremented under a SELECT ... FOR UPDATE
--       row lock on the issuance path, inside the caller's transaction. Burned
--       numbers (failed creation after issuance) are tolerated and never reissued.
--   D2 (event carriage = outbox): patient_event_outbox is append-only (PENDING until
--       the first consumer change adds the poller); patient_merge_log is the merge
--       audit trail.
--   D4 (uniqueness): the natural identity document (id_type + id_no) lives ON
--       patient_master under a PARTIAL unique index WHERE id_no IS NOT NULL AND
--       merged_into IS NULL - merged masters drop out of the identity key without
--       touching identifier rows, so a re-registered patient with the same document
--       is not blocked. patient_identifier carries ONLY additional numbers (visit
--       card / physical / outpatient / inpatient / HIS) and deliberately has NO
--       unique index.
--   Every id is `serial PRIMARY KEY` (Integer AUTO, MyBatis-Plus IdType.AUTO); every
--       table ends with the EntityBase audit block (exact columns, same order):
--       deleted / create_by / create_time / update_by / update_time, with the
--       database defaults supplying deleted = 0 and CURRENT_TIMESTAMP.
--   NO physical foreign keys anywhere in this module (execution contract): the
--       logical references (patient_id / merged_into / survivor_id / oid) stay
--       service-level - hence no REFERENCES clauses, unlike V1__auth_schema.
--   archive_status is a service-enforced ONE-WAY machine (PROVISIONAL -> RESOLVED):
--       varchar(16) with no DB CHECK, per the house DDL style.
--   Exactly ONE active rule per rule_type is backstopped by the partial unique
--       uk_patient_number_rule_active (WHERE active = 1 AND deleted = 0); multiple
--       inactive rules per type are allowed, and deleting the ACTIVE rule is a
--       service-level A-class rejection (only inactive rules delete).
--   B1 amendment (coordinator): stamp_value varchar(16) NULL - runtime state (the
--       stamp token the current serial_cur was issued under, NULL until first
--       issuance) so the ruled day-reset branch can compare stamp days; `stamp` stays
--       the FORMAT parameter. idx_patient_number_rule_type serves the issuance hot
--       path (rule resolution by type).
--   B1 amendment #2: `stamp` stores a JAVA DATE PATTERN directly (the spec's
--       "changing the number format SHALL NOT require a code change" is literally
--       true - any valid Java pattern works with zero code); the v1 seed is 'yyMMdd'
--       (never the lowercase form - uppercase 'Y' is java's week-year trap).
--   Seed: the default pat_id rule is a GUARDED insert (WHERE NOT EXISTS - house seed
--       discipline: a Flyway history-row repair must be able to re-run V1).

-- Patient master index (EMPI root): pat_id is the platform patient number (every row
-- carries one, PROVISIONAL placeholders included); id_type/id_no are NULLable because
-- an unnamed emergency registration may carry no identity document yet.
CREATE TABLE patient_master (
    id             serial PRIMARY KEY,
    pat_id         varchar(64)  NOT NULL,
    id_type        varchar(32),
    id_no          varchar(128),
    name           varchar(64),                     -- NULLable: unnamed emergency
    pinyin         varchar(128),                    -- generated server-side
    gender         integer      NOT NULL DEFAULT 0, -- 0 unknown / 1 male / 2 female
    birth_date     date,                            -- typed column, never a string birthday
    phone          varchar(32),
    nation         varchar(32),
    address        varchar(255),
    archive_status varchar(16)  NOT NULL DEFAULT 'PROVISIONAL',
    merged_into    integer,                         -- survivor master id; NULL = not merged
    oid            integer,                         -- issuer org id (filled when known)
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Identity anchor: live, unmerged masters only (predicate verbatim from the task).
CREATE UNIQUE INDEX uk_patient_master_identity ON patient_master (id_type, id_no)
    WHERE id_no IS NOT NULL AND merged_into IS NULL;
CREATE INDEX idx_patient_master_pat_id ON patient_master (pat_id);
CREATE INDEX idx_patient_master_merged_into ON patient_master (merged_into);

-- Additional identifiers: many rows per master, card-number resolve lookup by id_no.
CREATE TABLE patient_identifier (
    id         serial PRIMARY KEY,
    patient_id integer      NOT NULL,
    id_type    varchar(32)  NOT NULL,
    id_no      varchar(128) NOT NULL,
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_patient_identifier_patient_id ON patient_identifier (patient_id);
CREATE INDEX idx_patient_identifier_id_no ON patient_identifier (id_no);

-- Number rules: rule_type generalizes ('pat_id' today; future accession rules follow
-- the same per-service pattern). stamp = FORMAT parameter (Java pattern);
-- stamp_value = runtime state (B1 amendment).
CREATE TABLE patient_number_rule (
    id            serial PRIMARY KEY,
    rule_type     varchar(32) NOT NULL,
    prefix        varchar(32) NOT NULL DEFAULT '',
    suffix        varchar(32) NOT NULL DEFAULT '',
    serial_length integer     NOT NULL DEFAULT 4,
    serial_cur    integer     NOT NULL DEFAULT 0,
    is_stamp      integer     NOT NULL DEFAULT 0, -- 1 = the serial resets per stamp period
    stamp         varchar(16),                    -- date stamp format, Java DateTimeFormatter pattern (v1 seed: yyMMdd)
    stamp_value   varchar(16),                    -- last-issued stamp token (NULL until first issuance)
    active        integer     NOT NULL DEFAULT 1,
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_patient_number_rule_type ON patient_number_rule (rule_type);
-- Exactly-one-active-rule backstop: one ACTIVE live rule per type; inactive rows are
-- unrestricted (and only inactive rules may delete - service-level ruling).
CREATE UNIQUE INDEX uk_patient_number_rule_active ON patient_number_rule (rule_type)
    WHERE active = 1 AND deleted = 0;

-- Default pat_id rule (yyMMdd stamp + 4-digit serial): guarded insert keeps the
-- migration re-runnable after a history-row repair.
INSERT INTO patient_number_rule (rule_type, prefix, suffix, serial_length, serial_cur,
                                 is_stamp, stamp, stamp_value, active)
SELECT 'pat_id', '', '', 4, 0, 1, 'yyMMdd', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM patient_number_rule WHERE rule_type = 'pat_id');

-- Transactional-outbox carriage: rows land PENDING inside the patient transaction;
-- the first consumer change (lumen-core snapshot refresh / exam-order repoint)
-- adds the poller that flips status.
CREATE TABLE patient_event_outbox (
    id         serial PRIMARY KEY,
    event_type varchar(32) NOT NULL,
    patient_id integer     NOT NULL,
    payload    jsonb       NOT NULL,
    status     varchar(16) NOT NULL DEFAULT 'PENDING',
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_patient_event_outbox_status ON patient_event_outbox (status);
CREATE INDEX idx_patient_event_outbox_patient_id ON patient_event_outbox (patient_id);

-- Merge audit: one row per merge decision (merged_id absorbed, survivor_id kept).
CREATE TABLE patient_merge_log (
    id          serial PRIMARY KEY,
    merged_id   integer      NOT NULL,
    survivor_id integer      NOT NULL,
    reason      varchar(255),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_patient_merge_log_merged_id ON patient_merge_log (merged_id);
CREATE INDEX idx_patient_merge_log_survivor_id ON patient_merge_log (survivor_id);
