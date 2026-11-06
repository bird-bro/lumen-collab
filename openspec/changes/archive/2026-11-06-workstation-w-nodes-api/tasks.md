# Tasks: workstation-w-nodes-api

## 1. V22 migration (red → green)

- [x] 1.1 Failing seed test (rewrite `AuthWorkstationSeedTests`): three type-W L1 nodes (放射工作站/超声工作站/内镜工作站) with admin grants; `user_workstation` GONE; `menu_info.workstation` column GONE; `system:user:assign-ws` node GONE; re-run no-op - verify: red, then green after 1.2.
- [x] 1.2 Write `V22__workstation_w_nodes.sql` per design D2 (FK-safe statement order; content-guarded W inserts + admin grants; comment lines without semicolons) - verify: suites green with JDK 11.

## 2. Machinery removal (red → green)

- [x] 2.1 Delete: `WorkstationService`, `UserWorkstation`, `UserWorkstationMapper`, `AssignWorkstationsRequest`, the controller endpoint + additive-DI field, the login/me/Profile payload fields and their AuthServiceImpl wiring - verify: compile clean; `PUT /users/{id}/workstations` answers 404.
- [x] 2.2 Menu DTO `@Pattern` gains `W`; the deleted-user scenario tests and assign-ws perm references are removed - verify: full reactor green (existing suites unaffected - the W nodes are additive tree data).

## 3. Full verification

- [x] 3.1 Whole-reactor build + tests from the aggregator with explicit JDK 11 - verify: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q` passes in `lumen-sev/`.
- [x] 3.2 Walk each WHEN/THEN of the deltas against implemented behavior - verify: every scenario mapped to a passing test or proof; record in the apply summary.

## Workflow follow-up (reference, outside tracked progress)

- Spec re-sync REPLACES the two workstation requirements synced from the archived change (REMOVED + ADDED shape).
- `workstation-context-web` (rewound to proposing) absorbs the frontend side: chip derives from tree W nodes, users-page assignment drawer dropped, menu type radio gains 工作站.
