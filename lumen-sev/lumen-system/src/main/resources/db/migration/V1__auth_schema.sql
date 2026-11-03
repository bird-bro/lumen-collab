-- V1__auth_schema.sql - lumen-auth seven-table RBAC schema (auth-rbac-foundation task 2.1).
--
-- Binding DDL design (coordinator-decided from design D1/D2/D5):
--   D1: every id is `serial PRIMARY KEY` (Integer AUTO, MyBatis-Plus IdType.AUTO);
--       every table carries the EntityBase audit block (exact columns, same order):
--       deleted / create_by / create_time / update_by / update_time, with the
--       database defaults supplying deleted = 0 and CURRENT_TIMESTAMP (EntityBase
--       declares no FieldFill on the timestamps - data spec, "Standard entity fields").
--   D2: org_info is the pure org tree (no medical attributes); org_dep anchors to
--       it by FK. org_info deliberately has NO self-FK: root rows use up_id = 0 (the
--       TreeUtils rootParentValue convention) and tree integrity is service-level.
--   Table naming (user decision 2026-11-03): `org` carries the 机构 semantics
--       (English "unit" reads as a measurement unit), so org_info / org_dep with the
--       `_info` suffix consistent with user_info / menu_info / role_info.
--   Logical delete: unique values live in PARTIAL unique indexes (WHERE deleted = 0),
--       so a logically deleted row never blocks re-creating the value. The in-service
--       duplicate rejection remains the A-class path; the index is the backstop.

-- Hierarchical org tree: hospital roots and their sub-orgs (multi-organisation ready -
-- a second root is just another up_id = 0 row).
CREATE TABLE org_info (
    id          serial PRIMARY KEY,
    up_id       integer      NOT NULL DEFAULT 0,
    level       integer      NOT NULL DEFAULT 1,
    type        integer      NOT NULL DEFAULT 0, -- lumen-owned; 0 = hospital root
    name        varchar(128) NOT NULL,
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_org_info_up_id ON org_info (up_id);

-- Departments (org_dep) anchor medical workflows to the org tree; `code` (radiology/us/
-- endo) is the stable identifier lumen-integration routes DICOM/HL7 by.
CREATE TABLE org_dep (
    id          serial PRIMARY KEY,
    org_id      integer      NOT NULL REFERENCES org_info (id),
    code        varchar(32)  NOT NULL,
    name        varchar(128) NOT NULL,
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_org_dep_org_id ON org_dep (org_id);
CREATE UNIQUE INDEX uk_org_dep_code ON org_dep (code) WHERE deleted = 0;

-- Platform users. username is unique among live rows only (a logically deleted row does
-- not block re-creating the username). password holds the BCrypt hash (design D4).
CREATE TABLE user_info (
    id                   serial PRIMARY KEY,
    username             varchar(64)  NOT NULL,
    password             varchar(100) NOT NULL,
    must_change_password integer      NOT NULL DEFAULT 0, -- 1 = change at first login
    enable               integer      NOT NULL DEFAULT 1, -- 1 = active
    name                 varchar(64),
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            integer,
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_user_info_username ON user_info (username) WHERE deleted = 0;

-- Roles; type follows the copied RoleConstants lineage (10 system / 11 business).
CREATE TABLE role_info (
    id          serial PRIMARY KEY,
    name        varchar(64)  NOT NULL,
    type        integer      NOT NULL DEFAULT 11,
    remark      varchar(255),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_role_info_name ON role_info (name) WHERE deleted = 0;

-- User-role bindings.
CREATE TABLE user_role (
    id          serial PRIMARY KEY,
    user_id     integer NOT NULL REFERENCES user_info (id),
    role_id     integer NOT NULL REFERENCES role_info (id),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_role_user_id ON user_role (user_id);
CREATE UNIQUE INDEX uk_user_role_user_role ON user_role (user_id, role_id) WHERE deleted = 0;

-- Menu nodes: up_id tree with type M directory / C menu / F button (the copied MenuTree
-- POJO lineage). Design D5 resolution - trimmed to lumen needs: NO query/redirect/app/
-- breadcrumb/action/is_cache/is_frame columns (recorded in the task evidence).
CREATE TABLE menu_info (
    id          serial PRIMARY KEY,
    up_id       integer      NOT NULL DEFAULT 0,
    type        varchar(1)   NOT NULL DEFAULT 'C', -- M directory / C menu / F button
    title       varchar(64)  NOT NULL,
    perms       varchar(128),
    path        varchar(128),
    component   varchar(128),
    icon        varchar(64),
    sort        integer      NOT NULL DEFAULT 0,
    visible     integer      NOT NULL DEFAULT 1, -- 1 = visible
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_menu_info_up_id ON menu_info (up_id);

-- Role-menu bindings (the page/button permission grants).
CREATE TABLE role_menu (
    id          serial PRIMARY KEY,
    role_id     integer NOT NULL REFERENCES role_info (id),
    menu_id     integer NOT NULL REFERENCES menu_info (id),
    deleted     integer      NOT NULL DEFAULT 0,
    create_by   integer,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   integer,
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_role_menu_role_id ON role_menu (role_id);
CREATE UNIQUE INDEX uk_role_menu_role_menu ON role_menu (role_id, menu_id) WHERE deleted = 0;
