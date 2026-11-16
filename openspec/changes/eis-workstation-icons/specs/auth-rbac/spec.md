## Modified Requirements

### Requirement: Workstation Nodes in the Menu Tree

The menu tree SHALL carry workstation nodes as `type = W` entries at L1 level, granted
through the existing role_menu mechanism (roles own menu permissions; users bind
roles):

- V22 SHALL seed 放射工作站/超声工作站/内镜工作站 W nodes (admin auto-grant, content-
  guard discipline) and SHALL drop the wrong machinery (`user_workstation` table, the
  per-user assignment endpoint's button node, the `menu_info.workstation` column).
- Each department W node SHALL carry its rail icon (V36 forward migration,
  eis-workstation-icons, user ruling 2026-11-16 — the EIS reference glyphs, ported
  verbatim monochrome in the frontend ops-icons family; the icon keys are the W
  nodes' path values):
  - 放射工作站 — icon `radiology` (EIS `icon-paipianl`, display with two bones
    X-ray film)
  - 超声工作站 — icon `us` (EIS `icon-chaosheng`, monitor + probe sector scan)
  - 内镜工作站 — icon `endo` (EIS `icon-shipincaiji`, tripod video camera — the
    EIS sprite carries no literal endoscope glyph; the camera is the endoscopy
    video-capture metaphor, user ruling 2026-11-16)
- The menu management page SHALL offer the 工作站 type (the station set grows only
  through the menu tree, NOT through a per-user grant surface).
- A role granted a W node SHALL pass that workstation's subtree to every bound user's
  login/me menu tree; a user whose roles hold no W node SHALL see the console only.

#### Scenario: W nodes seeded and role-granted

(unchanged from the synced spec — the V22 admin auto-grant contract)

#### Scenario: Department icons ride the seed

- **WHEN** the menu migrations run on a fresh database
- **THEN** the three W nodes live at L1 carrying the icons `radiology` / `us` /
  `endo` respectively

#### Scenario: Icon migration re-execution is a no-op

- **WHEN** the V36 statements re-run on an already-migrated database
- **THEN** no W row is added or altered (the `icon <>` guard makes every UPDATE a
  row-count no-op) and the icons stay `radiology` / `us` / `endo`
