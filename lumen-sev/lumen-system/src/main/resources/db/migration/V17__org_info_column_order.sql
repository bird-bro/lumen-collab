-- V17__org_info_column_order.sql - restore the binding physical column order after V16.
--
-- PostgreSQL appends ALTER TABLE ADD COLUMN fields after the EntityBase audit block.
-- V1's binding design requires the audit block to remain the exact table tail, so this
-- migration rebuilds the small organization table in the required order. Row ids,
-- business values, and the org_dep ownership relationship are preserved.

CREATE TABLE org_info_v17 (
    id            serial PRIMARY KEY,
    up_id         integer      NOT NULL DEFAULT 0,
    level         integer      NOT NULL DEFAULT 1,
    type          integer      NOT NULL DEFAULT 0, -- 0 = hospital root, 1 = campus
    name          varchar(128) NOT NULL,
    code          varchar(32)  NOT NULL,
    short_name    varchar(64),
    status        integer      NOT NULL DEFAULT 1,
    province_code varchar(32),
    city_code     varchar(32),
    district_code varchar(32),
    address       varchar(255),
    contact       varchar(64),
    phone         varchar(32),
    email         varchar(128),
    logo_url      varchar(255),
    sort          integer      NOT NULL DEFAULT 0,
    is_default    integer      NOT NULL DEFAULT 0,
    deleted       integer      NOT NULL DEFAULT 0,
    create_by     integer,
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     integer,
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO org_info_v17 (
    id, up_id, level, type, name, code, short_name, status, province_code,
    city_code, district_code, address, contact, phone, email, logo_url,
    sort, is_default, deleted, create_by, create_time, update_by, update_time
)
SELECT
    id, up_id, level, type, name, code, short_name, status, province_code,
    city_code, district_code, address, contact, phone, email, logo_url,
    sort, is_default, deleted, create_by, create_time, update_by, update_time
  FROM org_info
 ORDER BY id;

SELECT setval(
    pg_get_serial_sequence('org_info_v17', 'id'),
    COALESCE((SELECT MAX(id) FROM org_info_v17), 1),
    TRUE
);

DROP TABLE org_info CASCADE;
ALTER TABLE org_info_v17 RENAME TO org_info;
ALTER SEQUENCE org_info_v17_id_seq RENAME TO org_info_id_seq;
ALTER TABLE org_info
    ALTER COLUMN id SET DEFAULT nextval('org_info_id_seq'::regclass);

CREATE INDEX idx_org_info_up_id ON org_info (up_id);

-- The CASCADE drop of the legacy table removes the old FK; restore it against the
-- rebuilt table without changing org_dep data.
ALTER TABLE org_dep
    ADD CONSTRAINT org_dep_org_id_fkey
    FOREIGN KEY (org_id) REFERENCES org_info (id);
