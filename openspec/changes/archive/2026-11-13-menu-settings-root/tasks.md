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

- [x] 1.3 Icon swap (user ruling during 2.1 verification, 2026-11-13): the L1 设置
      rail icon `medicine-box` → `sliders` (frontend already maps it). TDD red
      (`settingsDirectoryCarriesTheSlidersIcon` + tail extended to V28→V31), green
      `V31__menu_settings_icon.sql` (content-guarded UPDATE, V30 frozen). Verify:
      suite exit 0, 49 classes / 242 tests, 0 failures; zero frontend change.

- [x] 1.4 L2 grouping (user ruling during 2.1 verification, 2026-11-13): the four
      pages re-group under two icon-bearing L2 directories 检查配置 (medicine-box) /
      报告配置 (file-text). TDD red (group-row errors + V32 file-missing), green
      `V32__menu_settings_groups.sql` (content-guarded inserts + per-group grant
      carry-over + pages re-parented BY PERMS + admin auto-grant + setvals; V30
      frozen; applied-then-edited checksum cleared by the delete-history-row ruling
      after two comment semicolons hit the split-on-semicolon trap); web adds two
      MENU_ICON_BY_NAME lines. Verify: backend suite exit 0 (49 classes / 243
      tests, 0 failures), web suite exit 0 (31 files / 362 tests).

- [x] 1.5 Group icons → RCP glyphs (user ruling during 2.1 verification,
      2026-11-13): the AntD placeholders swap to the RCP iconfont symbols `exam` /
      `temp` (the rcp-test dict_menu rows for the same console domain, mid
      1029/1025). TDD red (expected exam but was medicine-box + V33 file-missing),
      green `V33__menu_settings_group_icons.sql` (content-guarded UPDATEs, V32
      frozen); web ports the two symbols verbatim into ops-icons.tsx
      (ExamCatalogIcon / TemplateBookIcon, monochrome currentColor) and re-points
      the two MENU_ICON_BY_NAME lines. Verify: backend suite exit 0 (49 classes /
      243 tests, 0 failures), web suite exit 0 (31 files / 362 tests).

- [x] 1.6 L1 rail icon → RCP glyph (user ruling during 2.1 verification,
      2026-11-13): the AntD placeholder `sliders` (V31) swaps to the RCP
      `icon-setting` knobs glyph, wired as `tune` (the literal name is taken by
      the 系统 root's AntD gear). Also: ExamCatalogIcon optically scaled 0.8
      about center (its ink filled ~97% of the viewBox vs the ~75-82% family
      band - user feedback: oversized at 24px). TDD red (expected tune but was
      sliders + V34 file-missing), green `V34__menu_settings_root_icon.sql`
      (content-guarded UPDATE, V31-V33 frozen). Verify: backend suite exit 0 (49
      classes / 243 tests, 0 failures), web suite exit 0 (31 files / 362 tests);
      ported paths verified byte-identical against the RCP sprite.

## 2. Close-out

- [x] 2.1 User verifies the console on the real backend (rail: 系统 · 设置 ·
      workstations; setting pages under 设置) — confirmed by user 2026-11-13.
- [x] 2.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md —
      overall PASS: L1 both stacks exit=0, L2 3/3 IMPLEMENTED, L3 backend
      exit=0 (lumen-system 49 classes / 243 tests + common 1 class / 4 tests,
      0 failures) and web exit=0 (31 files / 362 tests); verify.md written.
