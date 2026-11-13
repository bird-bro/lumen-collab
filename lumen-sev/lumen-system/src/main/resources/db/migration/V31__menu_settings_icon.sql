-- V31__menu_settings_icon.sql - the L1 「设置」 directory swaps its rail icon (user
-- ruling 2026-11-13, menu-settings-root task 1.3): medicine-box was inherited from
-- the V28 group but mapped by NO console glyph, so the rail fell back to the
-- department radar glyph. sliders is the ruled replacement - already mapped by the
-- web console (MENU_ICON_BY_NAME: sliders -> SlidersOutlined, the V5/V8 legacy key),
-- zero frontend change. V30 stays frozen (never rewrite an applied migration) -
-- this content-guarded UPDATE on the live L1 row (the V30 contract keys) is the
-- forward fix, idempotent by the icon <> guard (proven by AuthExamSettingSeedTests).

UPDATE system.menu_info SET icon = 'sliders', update_time = CURRENT_TIMESTAMP
 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0
   AND icon <> 'sliders';
