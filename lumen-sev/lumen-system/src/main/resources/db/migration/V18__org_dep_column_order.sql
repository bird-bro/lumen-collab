-- V18__org_dep_column_order.sql - complete the binding physical column order after V16.
--
-- Like V17, this rebuild is required because PostgreSQL appends status after the
-- EntityBase audit block. Department row ids and business values are preserved. The
-- org_info ownership FK is recreated against the rebuilt table.

-- Restore the organization indexes dropped together with the V16-era table rebuild.
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_info_up_code
    ON org_info (up_id, code)
 WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_info_default_campus
    ON org_info (up_id, is_default)
 WHERE deleted = 0
   AND up_id <> 0
   AND is_default = 1;

CREATE TABLE org_dep_v18 (
    id          serial PRIMARY KEY,
    org_id      integer      NOT NULL REFERENCES org_info (id),
    code        varchar(32)  NOT NULL,
    name        varchar(128) NOT NULL,
    status      integer      NOT NULL DEFAULT 1, -- 1 = enabled, 0 = disabled
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO org_dep_v18 (
    id, org_id, code, name, status, deleted,
    create_by, create_time, update_by, update_time
)
SELECT
    id, org_id, code, name, status, deleted,
    create_by, create_time, update_by, update_time
  FROM org_dep
 ORDER BY id;

SELECT setval(
    pg_get_serial_sequence('org_dep_v18', 'id'),
    COALESCE((SELECT MAX(id) FROM org_dep_v18), 1),
    TRUE
);

DROP TABLE org_dep CASCADE;
ALTER TABLE org_dep_v18 RENAME TO org_dep;
ALTER SEQUENCE org_dep_v18_id_seq RENAME TO org_dep_id_seq;
ALTER TABLE org_dep
    ALTER COLUMN id SET DEFAULT nextval('org_dep_id_seq'::regclass);

CREATE INDEX idx_org_dep_org_id ON org_dep (org_id);
CREATE UNIQUE INDEX uk_org_dep_org_code
    ON org_dep (org_id, code)
 WHERE deleted = 0;
