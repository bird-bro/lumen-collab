-- V26__user_profile_columns.sql - the user profile columns (user-edit-delete-profile-api,
-- task 1.1 - spec deltas data "User Profile Columns" + system-data "Dict Seed Data" +
-- auth-rbac "User Management"). Three ordered sections, all idempotent under re-run:
--
--   1. Eight nullable profile columns on system.user_info via a TABLE REBUILD (the
--      V17/V24 pattern). PostgreSQL appends ADD COLUMN fields AFTER the EntityBase
--      audit block, and the binding design requires the audit block to remain the
--      exact table tail (AuthSchemaMigrationTests pins the order) - so the small
--      table is rebuilt in the required order. Row ids, business values, and the
--      user_role bindings are preserved. dep_id is a SERVICE-enforced reference
--      (the update path validates a live department, A-class on a miss): user_info
--      stays at ZERO physical FKs, the V1 binding design's D1 shape (the org_info
--      no-self-FK precedent - integrity is service-level, AuthSchemaMigrationTests
--      pins user_info at 0).
--   2. The 「检查类型」/「审核权限」 dict catalogs (spec system-data): deterministic ids
--      1021/1022 (clear of V6's 1001-1007, V23's 1020, V25's 1030 bands), detail ids
--      +200000 offset (1021001+ reads as catalog 1021's block), every statement
--      ON CONFLICT (id) DO NOTHING, plus the setval tail so application-side dict
--      inserts never collide (V25 discipline - V23 shipped without it once).
--   3. The two F nodes under 用户管理 (id 2): 编辑用户 system:user:update sort 5,
--      删除用户 system:user:delete sort 6 - the V15 CONTENT-guard discipline (NOT
--      EXISTS among LIVE rows, explicit ids were proven wrong by V15's own header)
--      plus the admin auto-grant NOT-EXISTS guard.
--
-- IDEMPOTENCE: section 1's rebuild re-runs as a data no-op (INSERT ... SELECT carries
-- the rows through, the swap is shape-stable). Section 2 re-runs as ON CONFLICT
-- no-ops (a live seeded id yields, and the setval re-converges to MAX(id)). Section 3
-- re-runs as NOT-EXISTS no-ops (V15/V21 discipline). Comment lines and REMARK
-- STRINGS carry NO semicolons (the re-execution test splits statements on
-- semicolons - a semicolon inside either truncates a statement mid-body). Proven by
-- AuthUserProfileSeedTests.seedStatementsReExecuteAsARowCountNoOp.

-- ================= 1) profile columns via table rebuild (spec data) =================
CREATE TABLE user_info_v26 (
    id                   serial PRIMARY KEY,
    username             varchar(64)  NOT NULL,
    password             varchar(100) NOT NULL,
    must_change_password integer      NOT NULL DEFAULT 0,
    enable               integer      NOT NULL DEFAULT 1,
    name                 varchar(64),
    phone                varchar(32),
    email                varchar(128),
    gender               integer,
    title                integer,
    dep_id               integer,
    modalitys            varchar(128),
    rep_audit            integer,
    signature            varchar(255),
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            integer,
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO user_info_v26 (
    id, username, password, must_change_password, enable, name,
    phone, email, gender, title, dep_id, modalitys, rep_audit, signature,
    deleted, create_by, create_time, update_by, update_time
)
SELECT
    id, username, password, must_change_password, enable, name,
    phone, email, gender, title, dep_id, modalitys, rep_audit, signature,
    deleted, create_by, create_time, update_by, update_time
  FROM user_info
 ORDER BY id;

SELECT setval(
    pg_get_serial_sequence('user_info_v26', 'id'),
    COALESCE((SELECT MAX(id) FROM user_info_v26), 1),
    TRUE
);

DROP TABLE user_info CASCADE;
ALTER TABLE user_info_v26 RENAME TO user_info;
ALTER SEQUENCE user_info_v26_id_seq RENAME TO user_info_id_seq;
ALTER TABLE user_info
    ALTER COLUMN id SET DEFAULT nextval('user_info_id_seq'::regclass);

-- Index names are schema-global in PostgreSQL: the new uk_user_info_username can
-- only be created AFTER the old table (holding the same index name) is dropped.
CREATE UNIQUE INDEX uk_user_info_username ON user_info (username) WHERE deleted = 0;

-- The CASCADE drop of the legacy table removes the old FK, so restore it against the
-- rebuilt table without changing user_role data (the V1 binding).
ALTER TABLE user_role
    ADD CONSTRAINT user_role_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES user_info (id);

-- ================= 2) dict catalogs 检查类型 / 审核权限 (spec system-data) =================
-- The catalog anchors BY NAME (V12 dropped the code column), and the details' code is
-- the numeric value user_info.gender/title/rep_audit store (spec data: dict codes,
-- never denormalized labels). 检查类型's details are the modality abbreviations the
-- user ruled 2026-11-10 (CT/MR/US/... the exam-type data visibility), and the seed
-- holds the v1 set - later items are operator-managed rows in 字典管理 (zero code
-- change, the role-type-dict D1 pattern).
-- 审核权限's tiers mirror the user-ruled report-workflow semantics (报告医生 1 /
-- 初审医生 2 / 终审医生 3, ascending authority, and 0 = 不参与 the RCP lineage's opt-out
-- row - a user without a tier stores NULL and renders the empty dash).

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1021, '检查类型', 1, 99, '检查类型数据可见性词表(user-edit-delete-profile-api,新增模态直接在此维护)'),
    (1022, '审核权限', 1, 99, '报告审核层级词表(user-edit-delete-profile-api,报告医生/初审医生/终审医生)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1021001, 1021, 1, 'CT',   1, 1),
    (1021002, 1021, 2, 'MR',   2, 1),
    (1021003, 1021, 3, 'US',   3, 1),
    (1021004, 1021, 4, 'ENDO', 4, 1),
    (1022001, 1022, 0, '不参与',   1, 1),
    (1022002, 1022, 1, '报告医生', 2, 1),
    (1022003, 1022, 2, '初审医生', 3, 1),
    (1022004, 1022, 3, '终审医生', 4, 1)
    ON CONFLICT (id) DO NOTHING;

-- The setval tail (V25 discipline): both sequences advance past the explicit ids so
-- the next application-side dict insert never collides with a seeded id.
SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));

-- ================= 3) the two F nodes under 用户管理 (spec auth-rbac) =================
-- V15 CONTENT-guard discipline: NOT EXISTS among LIVE rows (up_id, type, perms) -
-- idempotent by content, indifferent to dead-row history, immune to id-block
-- collisions. 编辑用户 sort 5 / 删除用户 sort 6 continue the 用户 group's
-- 新建(1)/重置密码(2)/启停(3)/工作站授权(4) ordering.

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 2, 'F', '编辑用户', 'system:user:update', 5
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 2, 'F', '删除用户', 'system:user:delete', 6
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:delete' AND deleted = 0);

-- The admin auto-grant (V15 discipline: NOT-EXISTS live-binding guard - role 1 owns
-- every new node without a manual step).
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.type = 'F' AND m.perms IN ('system:user:update', 'system:user:delete')
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);
