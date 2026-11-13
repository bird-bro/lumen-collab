-- V33__menu_settings_group_icons.sql - the two L2 group icons swap to the RCP
-- reference glyphs (user ruling 2026-11-13, menu-settings-root task 1.5): the AntD
-- placeholders medicine-box / file-text read as generic, and RCP (the remote-RIS
-- reference, rcp-test DB dict_menu mid 1025/1029) styles the same console domain
-- with its iconfont `exam` (检查 group) and `temp` (模板 group) symbols. Both are
-- ported verbatim into src/components/ops-icons.tsx as ExamCatalogIcon /
-- TemplateBookIcon (monochrome currentColor, the V11 porting route). Content-
-- guarded UPDATEs on the live L2 rows (the V32 contract keys), idempotent by the
-- icon <> guard. V32 stays frozen (never rewrite an applied migration). Comment
-- lines carry NO semicolons (the re-execution test splits statements on
-- semicolons). Proven by AuthExamSettingSeedTests.

UPDATE system.menu_info SET icon = 'exam', update_time = CURRENT_TIMESTAMP
 WHERE type = 'M' AND title = '检查配置' AND deleted = 0
   AND up_id = (SELECT id FROM system.menu_info
                 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND icon <> 'exam';

UPDATE system.menu_info SET icon = 'temp', update_time = CURRENT_TIMESTAMP
 WHERE type = 'M' AND title = '报告配置' AND deleted = 0
   AND up_id = (SELECT id FROM system.menu_info
                 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND icon <> 'temp';
