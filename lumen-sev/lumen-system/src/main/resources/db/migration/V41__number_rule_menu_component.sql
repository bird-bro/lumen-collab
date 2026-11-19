-- V41__number_rule_menu_component.sql - finish the V40 menu move: the 号码规则
-- leaf (type C) migrated up_id/path/perms/sort but kept the patient-seed
-- component key (patient/number-rule/index, lumen-patient V2). The web console
-- resolves navigable leaves by the component column (menu-paths.ts
-- CONSOLE_ROUTE_BY_COMPONENT), so the stale key left the leaf unclickable.
-- Content-anchored like V40 - ids are data, path + perms are the stable key.
UPDATE system.menu_info SET component = 'system/number-rule/index'
 WHERE type = 'C' AND path = 'number-rules' AND perms = 'system:number:list'
   AND component = 'patient/number-rule/index' AND deleted = 0;
