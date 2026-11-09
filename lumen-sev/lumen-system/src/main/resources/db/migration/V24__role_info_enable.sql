-- V24__role_info_enable.sql - role_info gains the lifecycle flag (role-type-dict-api,
-- coordination D7, user review 2026-11-09: "角色也需要增加一个 启用/禁用的字段 enable").
-- Field name follows the dict vocabulary's `enable` (not orgs' `status`); existing
-- rows inherit the default (born enabled) - no backfill statement needed. Behavioral
-- consequences of a disabled role (perm recalc / login rejection) are OUT of scope:
-- store/display/toggle only, wired through PUT /roles/{id}/enable.
--
-- PostgreSQL appends ALTER TABLE ADD COLUMN fields AFTER the EntityBase audit block,
-- and the binding design requires the audit block to remain the exact table tail -
-- so the small table is REBUILT in the required order (the V17__org_info_column_order
-- pattern): row ids, business values, and the user_role/role_menu ownership FKs are
-- preserved; the swap drops and restores the dependent FK constraints without
-- touching user_role/role_menu data.

CREATE TABLE role_info_v24 (
    id          serial PRIMARY KEY,
    name        varchar(64)  NOT NULL,
    type        integer      NOT NULL DEFAULT 11,
    remark      varchar(255),
    enable      integer      NOT NULL DEFAULT 1,
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO role_info_v24 (
    id, name, type, remark, enable, deleted, create_by, create_time, update_by, update_time
)
SELECT
    id, name, type, remark, enable, deleted, create_by, create_time, update_by, update_time
  FROM role_info
 ORDER BY id;

SELECT setval(
    pg_get_serial_sequence('role_info_v24', 'id'),
    COALESCE((SELECT MAX(id) FROM role_info_v24), 1),
    TRUE
);

DROP TABLE role_info CASCADE;
ALTER TABLE role_info_v24 RENAME TO role_info;
ALTER SEQUENCE role_info_v24_id_seq RENAME TO role_info_id_seq;
ALTER TABLE role_info
    ALTER COLUMN id SET DEFAULT nextval('role_info_id_seq'::regclass);

-- Index names are schema-global in PostgreSQL: the new uk_role_info_name can only
-- be created AFTER the old table (holding the same index name) is dropped.
CREATE UNIQUE INDEX uk_role_info_name ON role_info (name) WHERE deleted = 0;

-- The CASCADE drop of the legacy table removes the old FKs; restore them against the
-- rebuilt table without changing user_role/role_menu data (the V1 bindings).
ALTER TABLE user_role
    ADD CONSTRAINT user_role_role_id_fkey
    FOREIGN KEY (role_id) REFERENCES role_info (id);
ALTER TABLE role_menu
    ADD CONSTRAINT role_menu_role_id_fkey
    FOREIGN KEY (role_id) REFERENCES role_info (id);
