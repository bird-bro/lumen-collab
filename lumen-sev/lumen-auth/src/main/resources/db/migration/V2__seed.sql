-- V2__seed.sql - lumen-auth seed data (auth-rbac-foundation task 2.2).
--
-- Runs exactly once by Flyway versioned semantics: plain INSERTs with EXPLICIT ids so
-- the FK references below are stable. Audit-block columns are omitted and fall to the
-- V1 defaults (deleted = 0, CURRENT_TIMESTAMP). Chinese strings are product display
-- data; comments stay English.

-- Hospital root unit (up_id = 0 root convention, level 1, lumen type 0 = hospital root).
INSERT INTO unit_info (id, up_id, level, type, name) VALUES
    (1, 0, 1, 0, '示例医院');

-- The three v1 departments anchored to the hospital root. The codes are the stable
-- identifiers lumen-integration routes DICOM/HL7 by (binding DDL rationale).
INSERT INTO department (id, unit_id, code, name) VALUES
    (1, 1, 'radiology', '放射科'),
    (2, 1, 'us',        '超声科'),
    (3, 1, 'endo',      '内镜科');

-- Default administrator. Initial credentials admin / admin123 (BCrypt hash verified
-- with spring-security-crypto 5.5.8, the Boot 2.5.15 train artifact); the
-- must_change_password flag forces a password change at first login (spec: Seed Data).
INSERT INTO user_info (id, username, password, must_change_password, enable, name) VALUES
    (1, 'admin', '$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m', 1, 1, '系统管理员');

-- Default administrator role: type 10 = system (copied RoleConstants lineage).
INSERT INTO role (id, name, type, remark) VALUES
    (1, '系统管理员', 10, 'seed default administrator role');

INSERT INTO user_role (id, user_id, role_id) VALUES
    (1, 1, 1);

-- Minimal menu set: the system directory + user management, the three department page
-- placeholders, and the audit-button example. Visible defaults to 1; menu 2 nests under
-- 1, menu 6 (the F button) nests under 3.
INSERT INTO menu_info (id, up_id, type, title, perms, path, component, icon, sort) VALUES
    (1, 0, 'M', '系统管理', NULL,                 '/system',    NULL,               'setting', 1),
    (2, 1, 'C', '用户管理', 'system:user:list',   'user',       'system/user/index', NULL,     1),
    (3, 0, 'C', '放射科',   NULL,                 '/radiology', 'radiology/index',   NULL,     2),
    (4, 0, 'C', '超声科',   NULL,                 '/us',        'us/index',          NULL,     3),
    (5, 0, 'C', '内镜科',   NULL,                 '/endo',      'endo/index',        NULL,     4),
    (6, 3, 'F', '报告审核', 'radiology:report:audit', NULL,     NULL,                NULL,     1);

-- The administrator role owns all six menus.
INSERT INTO role_menu (id, role_id, menu_id) VALUES
    (1, 1, 1),
    (2, 1, 2),
    (3, 1, 3),
    (4, 1, 4),
    (5, 1, 5),
    (6, 1, 6);

-- Advance every serial sequence past the explicit seed ids. Without this, the first
-- application-side insert (MyBatis-Plus IdType.AUTO = nextval) would collide with the
-- seeded id 1 rows.
SELECT setval(pg_get_serial_sequence('unit_info',  'id'), (SELECT COALESCE(MAX(id), 1) FROM unit_info));
SELECT setval(pg_get_serial_sequence('department', 'id'), (SELECT COALESCE(MAX(id), 1) FROM department));
SELECT setval(pg_get_serial_sequence('user_info',  'id'), (SELECT COALESCE(MAX(id), 1) FROM user_info));
SELECT setval(pg_get_serial_sequence('role',       'id'), (SELECT COALESCE(MAX(id), 1) FROM role));
SELECT setval(pg_get_serial_sequence('user_role',  'id'), (SELECT COALESCE(MAX(id), 1) FROM user_role));
SELECT setval(pg_get_serial_sequence('menu_info',  'id'), (SELECT COALESCE(MAX(id), 1) FROM menu_info));
SELECT setval(pg_get_serial_sequence('role_menu',  'id'), (SELECT COALESCE(MAX(id), 1) FROM role_menu));
