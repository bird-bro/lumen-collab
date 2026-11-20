-- V1__exam_kernel_registration.sql - the canonical exam-workflow kernel registration
-- DDL (lumen-core-registration-kernel task 1.3, design D2). COPY-FROM SOURCE ONLY:
-- this file is NOT wired into any service Flyway ledger. Each consuming department
-- service VENDOR-COPIES these statements into its own ledger (its next V number, the
-- merge-setting-into-system precedent) - per-ledger Flyway version uniqueness forbids
-- a shared classpath location with fixed version numbers. Kernel tables live in the
-- CONSUMER's schema under the consumer's ledger. This copy is applied by the kernel's
-- own tests to the test-only `core` schema on the shared test server (design D9).
--
-- House DDL rules honored here: audit column set per EntityBase (deleted/create_by/
-- create_time/update_by/update_time), serial integer ids, varchar enum names, and
-- COMMENT LINES CARRY NO SEMICOLONS - the re-execution test splits statements on `;`
-- and strips `--` comment lines (the V36 house lesson).
--
-- Member tables of the registration aggregate (anti-god-table, spec exam-workflow-kernel
-- "Exam-Order Aggregate"): exam_order (document) / exam_execution (1:1 execution) /
-- exam_report (1:1 placeholder) with the patient reference + jsonb snapshot carried on
-- exam_order. Department-specific fields (孕检/hp/拍片退回) are deliberately absent -
-- they belong to department services or their extension columns.

CREATE TABLE exam_order (
    id               serial PRIMARY KEY,
    accession_no     varchar(64)  NOT NULL,        -- 检查号 (kernel-issued, rule 'accession')
    booking_no       varchar(64),                  -- 预约号 (walk-in orders carry none)
    apply_no         varchar(64),                  -- 申请单号 (HIS or manual)
    his_id           varchar(64),                  -- HIS 侧单据标识
    channel          varchar(16)  NOT NULL DEFAULT 'LOCAL', -- 申请渠道 local/remote(预留未启用)
    status           varchar(32)  NOT NULL,        -- BOOKED/REGISTERED/IN_PROGRESS/COMPLETED/BOOKING_CANCELLED/CANCELLED
    patient_uid      integer      NOT NULL,        -- patient 域 pat_uid 逻辑引用 (跨 schema 无 FK)
    patient_snapshot jsonb,                        -- jsonb 患者快照 (PatientJsonbTypeHandler 同款 Types.OTHER)
    exam_items       jsonb,                        -- 检查项目引用+名称快照数组 (setting 域目录)
    emg_flag         integer      NOT NULL DEFAULT 0,
    cancel_reason    varchar(255),
    deleted          integer      NOT NULL DEFAULT 0,
    create_by        integer,
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        integer,
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_exam_order_accession_live ON exam_order (accession_no) WHERE deleted = 0;
CREATE INDEX idx_exam_order_status ON exam_order (status);
CREATE INDEX idx_exam_order_patient_uid ON exam_order (patient_uid);

CREATE TABLE exam_execution (
    id          serial PRIMARY KEY,
    order_id    integer   NOT NULL UNIQUE,        -- 1:1 with the order document
    start_time  timestamp,                        -- 检查开始
    end_time    timestamp,                        -- 检查结束
    operator_id integer,                          -- 执行人 uid (nullable)
    remark      varchar(255),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE exam_report (
    id                serial PRIMARY KEY,
    order_id          integer      NOT NULL UNIQUE, -- 1:1 with the order document
    status            varchar(16)  NOT NULL DEFAULT 'DRAFTING', -- DRAFTING/ISSUED(已出)
    completion_source varchar(16)  NOT NULL DEFAULT 'LOCAL',    -- local/remote (来源可插拔接缝)
    deleted           integer      NOT NULL DEFAULT 0,
    create_by         integer,
    create_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by         integer,
    update_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- HIS two-phase intake cache (design D7, EIS 1.4 semantics): source = raw inbound
-- payload, target = dictionary-mapped exam items (setting-catalog references + name
-- snapshots, unmatchable items dropped at cache time). status CACHED until the manual
-- confirm commits it to a formal order - there is NO server-side auto-commit. A
-- committed row rejects further target corrections.
CREATE TABLE exam_apply_cache (
    id           serial PRIMARY KEY,
    his_id       varchar(64)  NOT NULL,
    apply_no     varchar(64),
    status       varchar(16)  NOT NULL DEFAULT 'CACHED', -- CACHED/COMMITTED
    source       jsonb,                                  -- 原始报文
    target       jsonb,                                  -- 映射后项目集
    patient_hint jsonb,                                  -- 患者提示信息 (confirm 前无身份)
    deleted      integer      NOT NULL DEFAULT 0,
    create_by    integer,
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    integer,
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_exam_apply_cache_his_id ON exam_apply_cache (his_id, status);
