-- V34__menu_settings_root_icon.sql - the L1 「设置」 rail icon swaps to the RCP
-- reference glyph (user ruling 2026-11-13, menu-settings-root task 1.6): the AntD
-- placeholder sliders (V31) reads generic, and RCP styles its own L1 设置 root
-- with the knobs glyph (rcp-test DB dict_menu mid 1018, icon `setting`). The
-- symbol is ported verbatim into src/components/ops-icons.tsx as TuneIcon
-- (monochrome currentColor, the V11 route) and wired under the semantic name
-- `tune` - the literal source name `setting` is already taken by the 系统 root's
-- AntD gear (the V2 seed). Content-guarded UPDATE on the live L1 row (the V30
-- contract keys), idempotent by the icon <> guard. V31/V32/V33 stay frozen
-- (never rewrite an applied migration). The icon chain converges in the
-- ascending tail order (a V31 re-run re-applies its placeholder, then this
-- migration re-applies tune) - the D2 discipline the V30/V32 pair follows.
-- Comment lines carry NO semicolons (the re-execution test splits statements on
-- semicolons). Proven by AuthExamSettingSeedTests.

UPDATE system.menu_info SET icon = 'tune', update_time = CURRENT_TIMESTAMP
 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0
   AND icon <> 'tune';
