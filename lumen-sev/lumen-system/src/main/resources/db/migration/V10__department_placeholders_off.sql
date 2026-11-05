-- V10__department_placeholders_off.sql - park the department placeholder menus
-- (user ruling 2026-11-05).
--
-- The three department L1 placeholders seeded by V2 (放射科 3 / 超声科 4 /
-- 内镜科 5) plus the 报告审核 audit-button example (6, under 放射科) leave the
-- live menu tree: they point at placeholder pages and the real department IA
-- (workflow-phase groups under each department, the recorded workstation-plan
-- discussion) lands with the radiology implementation. They will be re-added or
-- reshaped on demand then.
--
-- LOGICAL delete only (the @TableLogic discipline): rows stay recoverable for
-- the future workstation change, and the role_menu bindings follow them so the
-- admin grant set matches the live tree. Guarded WHERE clauses make the hot
-- psql run and a later Flyway re-run both no-op-safe.

UPDATE system.menu_info SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE id IN (3, 4, 5, 6) AND deleted = 0;

UPDATE system.role_menu SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE menu_id IN (3, 4, 5, 6) AND deleted = 0;
