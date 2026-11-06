-- V20__menu_info_remark.sql - add the optional menu remark without disturbing the
-- EntityBase audit-block tail.
--
-- role_menu.menu_id holds the only inbound FK, so it is detached before the
-- table swap and restored against the rebuilt menu_info table.
--
-- PostgreSQL appends ALTER TABLE ADD COLUMN fields after update_time, so the small
-- menu table is rebuilt in binding order. Row ids and role_menu bindings are preserved.

CREATE TABLE menu_info_v20 (
    id        serial PRIMARY KEY,
    up_id     integer      NOT NULL DEFAULT 0,
    type      varchar(1)   NOT NULL DEFAULT 'C', -- M directory / C menu / F button
    title     varchar(64)  NOT NULL,
    perms     varchar(128),
    path      varchar(128),
    component varchar(128),
    icon      varchar(64),
    sort      integer      NOT NULL DEFAULT 0,
    visible   integer      NOT NULL DEFAULT 1, -- 1 = visible
    remark    varchar(255),
    deleted   integer      NOT NULL DEFAULT 0,
    create_by integer,
    create_time timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by integer,
    update_time timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO menu_info_v20 (
    id, up_id, type, title, perms, path, component, icon, sort, visible,
    remark, deleted, create_by, create_time, update_by, update_time
)
SELECT
    id, up_id, type, title, perms, path, component, icon, sort, visible,
    NULL, deleted, create_by, create_time, update_by, update_time
  FROM menu_info
 ORDER BY id;

SELECT setval(
    pg_get_serial_sequence('menu_info_v20', 'id'),
    COALESCE((SELECT MAX(id) FROM menu_info_v20), 1),
    TRUE
);

ALTER TABLE role_menu DROP CONSTRAINT role_menu_menu_id_fkey;

DROP TABLE menu_info;
ALTER TABLE menu_info_v20 RENAME TO menu_info;
ALTER SEQUENCE menu_info_v20_id_seq RENAME TO menu_info_id_seq;
ALTER TABLE menu_info
    ALTER COLUMN id SET DEFAULT nextval('menu_info_id_seq'::regclass);

CREATE INDEX idx_menu_info_up_id ON menu_info (up_id);

ALTER TABLE role_menu
    ADD CONSTRAINT role_menu_menu_id_fkey
    FOREIGN KEY (menu_id) REFERENCES menu_info (id);
