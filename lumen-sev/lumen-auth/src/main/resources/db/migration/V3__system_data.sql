-- V3__system_data.sql - platform-shared system tables in the public schema (system-data task 1.1).
--
-- Binding DDL design (design D1/D2/D3, user decision 2026-11-03):
--   D1: the four tables are cross-service shared and live in PUBLIC - the DDL is
--       explicitly qualified public.<name> while Flyway history itself stays in the
--       auth schema (spring.flyway.schemas = auth). Every id is `serial PRIMARY KEY`
--       (Integer AUTO, MyBatis-Plus IdType.AUTO) per the lumen binding convention.
--   dict_catal / dict_detail: the copied entities (framework/dict, OPS manage lineage)
--       extend EntityBase, so every dict table ends with the V1 audit block
--       (deleted / create_by / create_time / update_by / update_time, exact order).
--       Unique values live in PARTIAL unique indexes (WHERE deleted = 0) so a
--       logically deleted row never blocks re-creating the value. Where the copied
--       entities refine the plan sketch the ENTITY wins: the spare-value fields map
--       to back_1 / back_2 (explicit @TableField), and DictDetailEntity also carries
--       remark / initial columns absent from the plan sketch.
--   log_info / log_sign: append-write tables - the copied framework/log entities do
--       NOT extend EntityBase, so there is NO audit block (no create_by / update_by);
--       columns map the copied LogInfoEntity / LogSignEntity fields 1:1
--       camelCase -> snake_case. `deleted` is an integer DEFAULT 0: the copied mapper
--       XML filters `WHERE deleted = 0` as raw SQL and MyBatis-Plus logic delete
--       embeds the raw 0/1 config values, so a boolean column would break both.
--   Seeds: the common_status catalog with its 启用/停用 details under EXPLICIT ids;
--       the setval tail advances the dict sequences past the seeded ids (V2
--       discipline). The log tables seed nothing, so their serial sequences stay
--       virgin (a setval to MAX(id) = 1 over an empty table would needlessly skip
--       id 1 on the first append).

-- Dictionary catalog: the dropdown directories (code is the stable lookup key).
CREATE TABLE public.dict_catal (
    id          serial PRIMARY KEY,
    code        varchar(64)  NOT NULL,
    name        varchar(64)  NOT NULL,
    enable      integer      NOT NULL DEFAULT 1, -- 1 = enabled
    sort        integer      NOT NULL DEFAULT 0,
    remark      varchar(255),
    cls         integer,
    initial     varchar(64),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_dict_catal_code ON public.dict_catal (code) WHERE deleted = 0;

-- Dictionary details: ca_id + ca_code anchor the detail to its catalog twice (the
-- ca_code redundancy lets lookups resolve by catalog code without a join); `value`
-- holds the machine value, `label` the display text.
CREATE TABLE public.dict_detail (
    id          serial PRIMARY KEY,
    ca_id       integer      NOT NULL,
    ca_code     varchar(64)  NOT NULL,
    code        integer,
    value       varchar(128),
    label       varchar(128) NOT NULL,
    color       varchar(32),
    css         varchar(128),
    sort        integer      NOT NULL DEFAULT 0,
    enable      integer      NOT NULL DEFAULT 1, -- 1 = enabled
    back_1      varchar(128),
    back_2      varchar(128),
    remark      varchar(255),
    initial     varchar(64),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dict_detail_ca_id ON public.dict_detail (ca_id);
CREATE UNIQUE INDEX uk_dict_detail_ca_code_value ON public.dict_detail (ca_code, value) WHERE deleted = 0;

-- Operation log (append-write; date_ym = int yyyymm partition helper, action_time is
-- the query key). Column set = the copied LogInfoEntity fields, 1:1.
CREATE TABLE public.log_info (
    id          serial PRIMARY KEY,
    date_ym     integer      NOT NULL,
    title       varchar(255),
    descr       varchar(255),
    type        varchar(255),
    uid         integer,
    account     varchar(128),
    action_time timestamp,
    spent       integer,
    method      varchar(16),
    name        varchar(128),
    pack        varchar(255),
    url         varchar(255),
    session     varchar(128),
    mobile      boolean,
    ip          varchar(64),
    os          varchar(64),
    browser     varchar(64),
    engine      varchar(64),
    record      integer,                          -- 0 INFO / 1 ERROR
    param       text,
    result      text,
    exception   text,
    app         varchar(32),
    deleted     integer      NOT NULL DEFAULT 0,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_info_date_ym ON public.log_info (date_ym);
CREATE INDEX idx_log_info_uid ON public.log_info (uid);

-- Sign-in / sign-out log (append-write; same shape discipline as log_info).
CREATE TABLE public.log_sign (
    id          serial PRIMARY KEY,
    date_ym     integer      NOT NULL,
    msg         varchar(255),
    descr       varchar(128),
    type        integer,
    uid         integer,
    account     varchar(128),
    action_time timestamp,
    mobile      boolean,
    ip          varchar(64),
    os          varchar(64),
    browser     varchar(64),
    engine      varchar(64),
    session     varchar(128),
    token       varchar(255),
    app         varchar(32),
    deleted     integer      NOT NULL DEFAULT 0,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_sign_date_ym ON public.log_sign (date_ym);
CREATE INDEX idx_log_sign_uid ON public.log_sign (uid);

-- Seed: the common_status catalog - the platform-wide 启用/停用 option pair. The
-- audit-block columns are omitted and fall to the DDL defaults (deleted = 0,
-- CURRENT_TIMESTAMP); Chinese strings are product display data (V2 precedent).
INSERT INTO public.dict_catal (id, code, name, enable, sort) VALUES
    (1, 'common_status', '通用状态', 1, 0);

-- Both details anchor to the catalog twice (ca_id = 1 and ca_code = 'common_status')
-- and are themselves live options (enable = 1); code/value follow the lumen enable
-- convention 1/0.
INSERT INTO public.dict_detail (id, ca_id, ca_code, code, value, label, sort, enable) VALUES
    (1, 1, 'common_status', 1, '1', '启用', 1, 1),
    (2, 1, 'common_status', 0, '0', '停用', 2, 1);

-- Advance the seeded sequences past the explicit seed ids (V2 discipline): without
-- this, the first application-side insert (MyBatis-Plus IdType.AUTO = nextval) would
-- collide with the seeded id 1 rows.
SELECT setval(pg_get_serial_sequence('public.dict_catal',  'id'), (SELECT COALESCE(MAX(id), 1) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'), (SELECT COALESCE(MAX(id), 1) FROM public.dict_detail));
