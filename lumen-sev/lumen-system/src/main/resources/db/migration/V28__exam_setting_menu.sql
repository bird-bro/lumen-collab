-- V28__exam_setting_menu.sql - the exam-setting console menu seeds (exam-setting-api,
-- task 6.1 - spec deltas system-data "Exam Setting Menu Pages" / "Exam Setting Button
-- Perms"). The modality vocabulary needs NO seed here: the shared 检查类型 dict catalog
-- (name-unique per uk_dict_catal_name) is owned by V26/V27 and the setting service
-- resolves it by NAME (exam-setting design D5 - the id flip-flopped 1021/1027 across
-- sessions - the name is the stable anchor).
--
-- Sections, all idempotent under re-run:
--   1. The L2 directory 「检查设置」 under the 系统管理 root (pinned id 1, the V2 seed)
--      as a CONTENT-guarded serial insert (V15/V27 discipline - NO explicit id: a fresh
--      deployment's serial menu ids are unpredictable, the perms/title are the
--      contract), icon per the console group-icon convention (V11).
--   2. The four C pages (检查项目/检查部位/检查方法/报告模板) under the directory,
--      resolved BY TITLE (the group carries no perms), guarded by perms - CONTENT-guard
--      serial inserts + the setval tail (V14 discipline).
--   3. The F button nodes 新建/编辑/删除 ×4 (V15 CONTENT-guard, keyed by perms, parent
--      resolved BY PERMS) + the admin auto-grant (role 1) - the V27 shape.
--
-- IDEMPOTENCE: every insert is NOT EXISTS-guarded among LIVE rows by its contract
-- string - re-runs converge (the setval tail re-advances past MAX(id)). Comment lines
-- carry NO semicolons (the re-execution test splits statements on semicolons - a
-- semicolon inside a comment truncates a statement mid-body). Proven by
-- AuthExamSettingSeedTests.

-- ================= 1) the 检查设置 directory (spec system-data) =================
INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT 1, 'M', '检查设置', NULL, NULL, NULL, 'medicine-box', 5
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 1 AND type = 'M' AND title = '检查设置'
                      AND deleted = 0);

-- ================= 2) the four C pages (spec system-data) ======================
INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 1 AND type = 'M' AND title = '检查设置' AND deleted = 0),
       'C', '检查项目', 'setting:item:list', 'exam-item', 'setting/exam-item/index',
       NULL, 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'setting:item:list' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 1 AND type = 'M' AND title = '检查设置' AND deleted = 0),
       'C', '检查部位', 'setting:body:list', 'exam-body', 'setting/exam-body/index',
       NULL, 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'setting:body:list' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 1 AND type = 'M' AND title = '检查设置' AND deleted = 0),
       'C', '检查方法', 'setting:method:list', 'exam-method', 'setting/exam-method/index',
       NULL, 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'setting:method:list' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 1 AND type = 'M' AND title = '检查设置' AND deleted = 0),
       'C', '报告模板', 'setting:temp:list', 'rep-template', 'setting/rep-template/index',
       NULL, 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0);

-- ================= 3) F button nodes + admin auto-grant (spec system-data) ======
-- The V15 CONTENT-guard discipline (NOT EXISTS among LIVE rows by perms - idempotent
-- by content, indifferent to dead-row history). The parent is the C page resolved BY
-- PERMS, so the button lands under the right page whatever serial id it took.

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:item:list' AND deleted = 0),
       'F', '新建项目', 'setting:item:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:item:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:item:list' AND deleted = 0),
       'F', '编辑项目', 'setting:item:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:item:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:item:list' AND deleted = 0),
       'F', '删除项目', 'setting:item:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:item:delete' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:body:list' AND deleted = 0),
       'F', '新建部位', 'setting:body:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:body:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:body:list' AND deleted = 0),
       'F', '编辑部位', 'setting:body:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:body:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:body:list' AND deleted = 0),
       'F', '删除部位', 'setting:body:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:body:delete' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:method:list' AND deleted = 0),
       'F', '新建方法', 'setting:method:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:method:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:method:list' AND deleted = 0),
       'F', '编辑方法', 'setting:method:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:method:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:method:list' AND deleted = 0),
       'F', '删除方法', 'setting:method:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:method:delete' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '新建模板', 'setting:temp:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '编辑模板', 'setting:temp:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '删除模板', 'setting:temp:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:delete' AND deleted = 0);

-- The admin auto-grant (role 1 = the seeded 系统管理员): every live menu row this
-- migration seeded (the directory + four pages + twelve F nodes), CONTENT-guarded
-- per row (V27 shape).
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE ((m.type = 'M' AND m.title = '检查设置')
        OR (m.type = 'C' AND m.perms IN ('setting:item:list', 'setting:body:list',
                                         'setting:method:list', 'setting:temp:list'))
        OR m.perms IN ('setting:item:create', 'setting:item:update',
                       'setting:item:delete', 'setting:body:create',
                       'setting:body:update', 'setting:body:delete',
                       'setting:method:create', 'setting:method:update',
                       'setting:method:delete', 'setting:temp:create',
                       'setting:temp:update', 'setting:temp:delete'))
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- The setval tail (V14 discipline): the sequence advances past the new rows so later
-- serial menu inserts never collide with this seed.
SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
