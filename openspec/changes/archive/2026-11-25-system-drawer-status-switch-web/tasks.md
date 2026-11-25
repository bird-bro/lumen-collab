# Tasks: system-drawer-status-switch-web (retrospective — landed with the console page batches)

- [x] 1.1 Organization drawer: create hides status (enabled submit), edit Switch via the profile update — evidence: orgs-page.test.tsx:449/:469 green
- [x] 1.2 Department drawer: same lifecycle split — evidence: deps-page.test.tsx (:446 edit Switch 0/1 wire; the create-hides test) green
- [x] 1.3 Room drawer: create hides status (enabled), edit Switch through the room status endpoint — evidence: room-page.test.tsx:286+ (`system:room:status`) green
- [x] 2.1 Dedicated table status switches unchanged — evidence: orgs table switch tests (:268-278) green
- [x] 3.1 L1 + L3 green — evidence: verify.md (504/504)
- [x] 3.2 openspec validate passes — evidence: the synced ADDED requirement (Drawer Lifecycle Status Controls)
