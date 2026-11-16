-- V36__workstation_department_icons.sql - the three department W nodes gain their
-- chip icons (user ruling 2026-11-16, eis-workstation-icons): the V22 seed left the
-- three workstations icon-less and the WorkstationChip rendered the
-- RadarChartOutlined rail fallback. The glyphs come from the EIS reference
-- (lumen-reference/code/eis-web, src/assets/iconfont.js - the 院内流程 reference,
-- same 1024 viewBox family as the OPS/RCP sprites):
--   放射工作站 -> 'radiology' - EIS `icon-paipianl` (display with two bones X-ray film)
--   超声工作站 -> 'us'       - EIS `icon-chaosheng` (monitor + probe sector scan)
--   内镜工作站 -> 'endo'     - EIS `icon-shipincaiji` (tripod video camera - the EIS
--                               sprite carries no literal endoscope glyph, the camera
--                               is the endoscopy video-capture metaphor)
-- Ported verbatim into src/components/ops-icons.tsx (monochrome currentColor, the
-- V11 porting route), keyed by the W nodes' path values. Content-guarded UPDATEs on
-- the V22 contract rows - the guard is IS DISTINCT FROM because the V22 seed left
-- icon NULL (plain `icon <>` evaluates unknown on NULL and would skip the row,
-- unlike the V33/V34 rows whose icons were already non-NULL). Idempotent both ways.
-- V22 stays frozen (never rewrite an applied migration). Comment lines carry NO
-- semicolons (the re-execution test splits statements on semicolons). Proven by
-- AuthWorkstationSeedTests.

UPDATE system.menu_info SET icon = 'radiology', update_time = CURRENT_TIMESTAMP
 WHERE up_id = 0 AND type = 'W' AND title = '放射工作站' AND deleted = 0
   AND icon IS DISTINCT FROM 'radiology';

UPDATE system.menu_info SET icon = 'us', update_time = CURRENT_TIMESTAMP
 WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0
   AND icon IS DISTINCT FROM 'us';

UPDATE system.menu_info SET icon = 'endo', update_time = CURRENT_TIMESTAMP
 WHERE up_id = 0 AND type = 'W' AND title = '内镜工作站' AND deleted = 0
   AND icon IS DISTINCT FROM 'endo';
