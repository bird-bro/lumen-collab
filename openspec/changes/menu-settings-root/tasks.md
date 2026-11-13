# Tasks — menu-settings-root

## 1. Backend (lumen-sev — single-stack)

- [x] 1.1 TDD red: re-point `AuthExamSettingSeedTests` to the new IA (L1 设置
      directory; pages under it by their perms rows; directory grant on the new
      title), rework the re-execution proof to run the V28→V30 tail with live-row
      counting, and update the root-title assertions in `AuthLoginTests` /
      `AuthDataLayerTests` (系统管理 → 系统). Verify: 4 failures + 1 file-missing
      error on the current seed (no V30 yet).
- [x] 1.2 Green: `V30__menu_settings_root.sql` per design D2–D6/D8 (rename id 1;
      soft-delete + fresh L1 设置 with grant carry-over; pages re-parented BY
      PERMS; admin auto-grant; setval tails). Verify: full `./mvnw -pl
      lumen-system -am test -q` exit 0 (49 classes / 241 tests, 0 failures);
      DB end-state spot-checked (1 L1 设置, 0 live 检查设置, 4 pages under it,
      admin grant live). One iteration: a semicolon inside a header comment
      truncated the tail re-run's statement split (the documented discipline
      trap) - fixed, and the applied-then-edited checksum mismatch was cleared by
      the delete-history-row-and-rerun ruling (V30 idempotence proven by
      converging the partial run's leftover dup).

## 2. Close-out

- [ ] 2.1 User verifies the console on the real backend (rail: 系统 · 设置 ·
      workstations; setting pages under 设置).
- [ ] 2.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.
