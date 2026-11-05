-- V11__menu_group_icons.sql - level-2 group icons ported from OPS (user ruling 2026-11-05:
-- glyphs are taken verbatim from the ops_web iconfont sprite and rendered MONOCHROME
-- via currentColor - user named the four source symbols from the running OPS UI).
--
-- All four 系统管理 level-2 directories get icons matching their content, each a
-- verbatim OPS glyph (source symbol -> seed name):
--   组织架构 (13) -> 'org'    - OPS `icon-org` (building)
--   用户权限 (14) -> 'team'   - OPS `icon-user_account` (ID-card person)
--   系统配置 (15) -> 'config' - OPS `icon-system` (tools)
--   安全 (16)     -> 'shield' - OPS `icon-safe` (shield outline + check)
-- Frontend rendering: src/components/ops-icons.tsx (monochrome currentColor), wired
-- by name in src/layouts/BasicLayout.tsx MENU_ICON_BY_NAME (V5/V8 names stay mapped
-- for DBs not yet migrated).
--
-- IDEMPOTENCE: same guarded discipline as V8 - updates carry WHERE guards, so
-- the hand-applied hot run and a later Flyway re-run both converge.

UPDATE system.menu_info SET icon = 'org', update_time = CURRENT_TIMESTAMP
 WHERE id = 13 AND icon <> 'org';

UPDATE system.menu_info SET icon = 'team', update_time = CURRENT_TIMESTAMP
 WHERE id = 14 AND icon <> 'team';

UPDATE system.menu_info SET icon = 'config', update_time = CURRENT_TIMESTAMP
 WHERE id = 15 AND icon <> 'config';

UPDATE system.menu_info SET icon = 'shield', update_time = CURRENT_TIMESTAMP
 WHERE id = 16 AND icon <> 'shield';
