-- V2__exam_kernel_pre_registration.sql - the kernel pre-registration DDL source
-- (kernel-pre-registration tasks 1.1/1.2, design D3/D6). COPY-FROM SOURCE ONLY,
-- the V1 precedent and rules apply verbatim: NOT wired into any service Flyway
-- ledger - each consuming department service VENDOR-COPIES these statements into
-- its own ledger (its next V number). This copy is applied by the kernel's own
-- tests to the test-only `core` schema alongside V1 (Flyway picks up both from
-- classpath:db/kernel). Additive only - V1 stays untouched.
--
-- Pre-registration substrate (proposal ① of us-registration-redesign, 拍板 02-B/06-A):
-- exam_order gains the slot anchor booking_time (nullable - walk-in orders carry
-- NULL), and exam_slot_config holds the per-modality capacity policy (granularity
-- minutes default 30, capacity per slot default 4, open time windows as jsonb
-- [{start,end} HH:mm] pairs defaulting to '[]' = pre-registration refused, opt-in
-- per modality). modality carries the dict-1024 VALUE token (org_device.type /
-- number_rule.modality storage precedent).
--
-- House DDL rules honored here: audit column set per EntityBase (deleted/create_by/
-- create_time/update_by/update_time), serial integer ids, and COMMENT LINES CARRY
-- NO SEMICOLONS (the V36 house lesson).

ALTER TABLE exam_order ADD COLUMN booking_time timestamp;

CREATE TABLE exam_slot_config (
    id                 serial PRIMARY KEY,
    modality           varchar(16)  NOT NULL,       -- dict-1024 VALUE token (US/CT/MR..), one row per modality
    granularity_minutes integer     NOT NULL DEFAULT 30, -- 开台时段粒度（分钟）
    slot_capacity      integer      NOT NULL DEFAULT 4,  -- 每时段容量（预登记占用上限）
    open_windows       jsonb        NOT NULL DEFAULT '[]', -- 开台时间窗 [{"start":"08:00","end":"12:00"}]，空数组=未开台
    deleted            integer      NOT NULL DEFAULT 0,
    create_by          integer,
    create_time        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          integer,
    update_time        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_exam_slot_config_modality_live ON exam_slot_config (modality) WHERE deleted = 0;
CREATE INDEX idx_exam_slot_config_windows ON exam_slot_config USING GIN (open_windows);
