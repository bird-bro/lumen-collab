# Proposal — eis-workstation-icons

## Why

User ruling 2026-11-16: the three department workstation W nodes (放射工作站/超声工作站/
内镜工作站, seeded icon-less by V22) render with the `RadarChartOutlined` rail fallback
in the WorkstationChip. The EIS reference (院内流程, `lumen-reference/code/eis-web`,
`src/assets/iconfont.js` — 225 symbols, same 1024 viewBox family as OPS/RCP) styles its
own department domain with department glyphs. Port them so each station reads at a
glance in the chip button and dropdown (the only surfaces W nodes render on — the W node
itself never enters the menu bar, per web-app-shell).

Glyph selection verified against the sprite (extracted, rendered monochrome on light AND
dark backgrounds, ink measured): the artwork survives the monochrome currentColor
pipeline — cutouts are true fill-rule holes, no white occlusion layers. Only
`paipianl` carries a brand fill (`#858AC1`), which the pipeline strips.

## What Changes

- **lumen-web (primary stack)** — three EIS iconfont glyphs join `ops-icons.tsx`
  (verbatim paths, monochrome currentColor, the V11 porting route) and
  `MENU_ICON_BY_NAME` (keys = the W nodes' seed path values):
  1. `RadiologyFilmIcon` — EIS `icon-paipianl` (display with two bones X-ray film);
     fills stripped; ink ~83.6×73.4% — inside the family band, no optical scale
     (sister glyph of the ultrasound icon, identical grid metrics).
  2. `UltrasoundProbeIcon` — EIS `icon-chaosheng` (monitor + probe sector scan);
     already fill-free; ink ~83.6×73.0% — no optical scale.
  3. `EndoscopyCameraIcon` — EIS `icon-shipincaiji` (tripod video camera = endoscopy
     video capture — the sprite has no literal endoscope glyph, user ruling 2026-11-16
     picked the camera metaphor); ink ~92.2% wide runs past the ~75–82% family band,
     so the paths scale 0.85 about the viewBox center (the RCP `exam` 0.8 precedent).
  4. Mapping keys `radiology` / `us` / `endo`; the `RAIL_FALLBACK_ICON` comment updated
     (the three department L1s no longer ride the fallback).
- **lumen-sev (in scope, mentioned)** — `V36__workstation_department_icons.sql`:
  three content-guarded UPDATEs on the V22 W rows (`up_id = 0 AND type = 'W' AND
  title = …`, idempotent by the `icon <>` guard; V22 stays frozen). Proven by
  `AuthWorkstationSeedTests` (icon assertions + the V36 re-execution no-op proof).

## Scope / Impact

- No API contract change (the menu tree already returns `icon`); no schema change (the
  `menu_info.icon` column exists since the console seed). UI surface = WorkstationChip
  button + dropdown items at 16px.
- Non-goals: no L2 page icons (department children carry their own when built); no
  other EIS glyph ports; no chip size/style change.
