-- V27__org_device_room.sql - the device/room registry (org-device-room-api, task 1.1 -
-- spec deltas system-data "Org Device Core Columns" / "Org Device Access and Acquisition
-- Columns" / "Org Room Core Columns" / "Org Room Binding and Call Columns" / "Device and
-- Room Dict Catalogs" / "Device and Room Dict Values" / "Device and Room Menu Pages" /
-- "Device and Room Button Perms"). Four ordered sections, all idempotent under re-run:
--
--   1. The SPLIT resource registry (user ruling 2026-11-10, coordination
--      org-device-room Shared Design): system.org_device is the ASSET + DICOM access
--      point (ae/ip/port + the Box acquisition block live HERE - the device is what
--      connects). system.org_room is the BUSINESS place (oid+did anchored, 叫号 block,
--      rebindable dev_id service-enforced reference - ZERO physical FKs, the
--      user_info.dep_id shape). The OPS inline pile-up (room-carried dev_ae/dev_ip/
--      box*/capture*) is deliberately ABSENT from org_room - the audit block stays the
--      exact table tail (V17/V18 order discipline). Field trim ruling 2026-11-12: no
--      vehicle/ezviz/lessor/contract columns. Type rulings 2026-11-12 (second round):
--      org_device.type is varchar(16) storing the dict-1024 VALUE (the modality token
--      'CT'/'DR' - business matching joins on the token, EIS DictExam.modality shape).
--      org_room carries NO type at all (rooms hang off departments, 诊室类型 dissolved).
--   2. The dict catalogs with deterministic ids in the V26 successor band
--      (1024-1026 device vocabularies, details ca_id * 1000 + sort), every insert
--      ON CONFLICT (id) DO NOTHING, plus the setval tail so application-side dict
--      inserts never collide (V25/V26 discipline). The 1026 codes 10/11 keep the OPS
--      归属类型 numerics (V23 style). common_status (1000) codes 1/0 remain the room
--      enable vocabulary. 1023 is left UNSEEDED (the dissolved 诊室类型 band slot -
--      the id is retired, not reused). V26's 检查类型 catalog 1021 is RETIRED
--      (unification ruling 2026-11-12): ONE catalog 1024 serves both 设备类型 and
--      检查类型 - the same modality tokens, no duplicate vocabulary to keep in sync.
--   3. The two C pages under 组织架构 (menu 13) - CONTENT-guarded serial inserts with
--      a setval tail, NOT explicit ids: V14's setval tail lets a FRESH deployment's V15
--      serial F nodes occupy ids 18+ (V26 header - "explicit ids were proven wrong by
--      V15's own header" - the hot test DB sits at 120+ and masks the trap). The perms
--      strings are the contract - ids are data.
--   4. The F button nodes (新建/编辑/删除 under each C page, 启停 extra for rooms) and
--      the admin auto-grant - the V15 CONTENT-guard discipline, keyed by perms.
--
-- IDEMPOTENCE: section 1 re-runs as CREATE TABLE IF NOT EXISTS no-ops (fresh databases
-- create, migrated databases yield). Section 2 re-runs as ON CONFLICT no-ops (the
-- setval re-converges to MAX(id)). Sections 3-4 re-run as NOT-EXISTS no-ops (the V15/
-- V21 discipline - the setval tail re-converges). Comment lines and REMARK STRINGS
-- carry NO semicolons (the re-execution test splits statements on semicolons - a
-- semicolon inside either truncates a statement mid-body). Proven by
-- AuthDeviceRoomSeedTests.seedStatementsReExecuteAsARowCountNoOp.

-- ================= 1) the split registry tables (spec system-data) =================
CREATE TABLE IF NOT EXISTS org_device (
    id                   serial PRIMARY KEY,
    code                 varchar(32)  NOT NULL,
    name                 varchar(128) NOT NULL,
    type                 varchar(16)  NOT NULL,
    status               integer      NOT NULL,
    tag                  integer      NOT NULL,
    brand                varchar(64),
    model                varchar(64),
    spec                 varchar(64),
    serial_num           varchar(64),
    manufacturer         varchar(128),
    production_date      date,
    purchase_date        date,
    acceptance_date      date,
    responsible          varchar(64),
    responsible_phone    varchar(32),
    start_date           date,
    end_date             date,
    remark               varchar(255),
    oid                  integer      NOT NULL,
    did                  integer,
    ae                   varchar(64),
    ip                   varchar(64),
    port                 varchar(16),
    box_adapter          varchar(64),
    box_ip               varchar(64),
    box_port             varchar(16),
    box_mac              varchar(64),
    box_print            varchar(128),
    capture_w            integer,
    capture_h            integer,
    prune                boolean,
    prune_u              integer,
    prune_d              integer,
    prune_l              integer,
    prune_r              integer,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            integer,
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS org_room (
    id                   serial PRIMARY KEY,
    oid                  integer      NOT NULL,
    did                  integer      NOT NULL,
    name                 varchar(128) NOT NULL,
    code                 varchar(32),
    enable               integer      NOT NULL DEFAULT 1,
    call_ip              varchar(64),
    call_port            varchar(16),
    call_mac             varchar(64),
    dev_id               integer,
    deleted              integer      NOT NULL DEFAULT 0,
    create_by            integer,
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            integer,
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Service-path indexes (no unique constraints - duplicate checks are service-level,
-- D4: logical delete would break a plain unique index - partial indexes would duplicate
-- the service rule, so the registry stays constraint-light like its org_info kin).
CREATE INDEX IF NOT EXISTS idx_org_device_oid ON org_device (oid);
CREATE INDEX IF NOT EXISTS idx_org_device_oid_code ON org_device (oid, code);
CREATE INDEX IF NOT EXISTS idx_org_room_oid ON org_room (oid);
CREATE INDEX IF NOT EXISTS idx_org_room_did ON org_room (did);
CREATE INDEX IF NOT EXISTS idx_org_room_dev ON org_room (dev_id);

-- ================= 2) dict catalogs 1024-1026 + the 检查类型 unification (spec system-data) ==
-- Catalogs anchor BY NAME (V12 dropped the code column). MACHINE-VALUE SPLIT (user
-- rulings 2026-11-12): the modality pair 设备类型/检查类型 stores the detail VALUE as
-- the machine token ('CT'/'DR' - org_device.type is varchar and saves it verbatim,
-- future exam tables join 检查类型 on the same token, the two catalogs MUST keep the
-- same value set), while the numeric catalogs 设备状态/归属类型 keep the OPS-lineage
-- convention (org_device.status/tag store the integer detail code, the value is
-- display text). The detail `code` stays the integer admin ordering key everywhere -
-- never a stored business value for the modality pair. Later items are
-- operator-managed rows in 字典管理 (zero code change, the role-type-dict D1 pattern).

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1024, '设备类型', 1, 99, '设备/检查类型共用模态词表(org-device-room-api,机器值=value模态串,新增模态直接在此维护)'),
    (1025, '设备状态', 1, 99, '设备生命周期词表(org-device-room-api,在用/停用/维修/报废)'),
    (1026, '归属类型', 1, 99, '设备权属词表(org-device-room-api,沿用OPS码值 10 自购 11 租赁)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1024001, 1024, 1,  'DR',   1, 1),
    (1024002, 1024, 2,  'CT',   2, 1),
    (1024003, 1024, 3,  'MR',   3, 1),
    (1024004, 1024, 4,  'DSA',  4, 1),
    (1024005, 1024, 5,  'US',   5, 1),
    (1024006, 1024, 6,  'ENDO', 6, 1),
    (1025001, 1025, 1,  '在用', 1, 1),
    (1025002, 1025, 2,  '停用', 2, 1),
    (1025003, 1025, 3,  '维修', 3, 1),
    (1025004, 1025, 4,  '报废', 4, 1),
    (1026001, 1026, 10, '自购', 1, 1),
    (1026002, 1026, 11, '租赁', 2, 1)
    ON CONFLICT (id) DO NOTHING;

-- The 检查类型 unification (ruling 2026-11-12, superseding the dual-catalog plan):
-- ONE catalog serves both 设备类型 and 检查类型 - 1024, the same modality tokens.
-- V26's 检查类型 catalog (1021, the data-visibility vocabulary) is RETIRED: user
-- modalitys CSVs and every future exam reference read the unified 1024 codes. The
-- delete anchors BY NAME (uk_dict_catal_name) so any environment's duplicate band
-- (a 1027-style experiment included) converges too. Naturally idempotent.
DELETE FROM public.dict_detail WHERE ca_id IN (
    SELECT id FROM public.dict_catal WHERE name = '检查类型');
DELETE FROM public.dict_catal WHERE name = '检查类型';

-- 1024's remark states the dual purpose (the INSERT above already carries it on
-- fresh databases - this UPDATE converges databases seeded by an earlier V27 rev).
UPDATE public.dict_catal
   SET remark = '设备/检查类型共用模态词表(org-device-room-api,机器值=value模态串,新增模态直接在此维护)'
 WHERE id = 1024;

-- The setval tail (V25 discipline): both sequences advance past the explicit ids so
-- the next application-side dict insert never collides with a seeded id.
SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));

-- ================= 3) the two C pages under 组织架构 (spec system-data) =================
-- CONTENT-guard serial inserts (V15/V22 discipline - the coordination decision
-- "Menu ids are data, not contract" - a fresh deployment's V15 F nodes occupy ids 18+,
-- so no low explicit id is safe). The perms strings are the stable contract.

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT 13, 'C', '设备档案', 'system:device:list', 'device', 'system/device/index', NULL, 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 13 AND type = 'C' AND perms = 'system:device:list'
                      AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT 13, 'C', '诊室配置', 'system:room:list', 'room', 'system/room/index', NULL, 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 13 AND type = 'C' AND perms = 'system:room:list'
                      AND deleted = 0);

-- ================= 4) F button nodes + admin auto-grant (spec system-data) =================
-- The V15 CONTENT-guard discipline (NOT EXISTS among LIVE rows by perms - idempotent
-- by content, indifferent to dead-row history). The parent is the C page resolved BY
-- PERMS, so the button lands under the right page whatever serial id it took.

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:device:list' AND deleted = 0),
       'F', '新建设备', 'system:device:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:device:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:device:list' AND deleted = 0),
       'F', '编辑设备', 'system:device:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:device:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:device:list' AND deleted = 0),
       'F', '删除设备', 'system:device:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:device:delete' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:room:list' AND deleted = 0),
       'F', '新建诊室', 'system:room:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:room:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:room:list' AND deleted = 0),
       'F', '编辑诊室', 'system:room:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:room:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:room:list' AND deleted = 0),
       'F', '启停诊室', 'system:room:status', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:room:status' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'system:room:list' AND deleted = 0),
       'F', '删除诊室', 'system:room:delete', 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'system:room:delete' AND deleted = 0);

-- The admin auto-grant (V15 discipline: NOT-EXISTS live-binding guard keyed by perms -
-- role 1 owns the two C pages and all seven F nodes without a manual step).
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE ((m.type = 'C' AND m.perms IN ('system:device:list', 'system:room:list'))
        OR m.perms IN ('system:device:create', 'system:device:update',
                       'system:device:delete', 'system:room:create',
                       'system:room:update', 'system:room:status',
                       'system:room:delete'))
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- The setval tail (V14 discipline): the sequence advances past the new rows so later
-- serial menu inserts never collide with this seed.
SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
