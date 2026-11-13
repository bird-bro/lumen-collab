# Tasks — menu-root-parent-option

## 1. Frontend (lumen-web — single-stack; no backend change)

- [x] 1.1 TDD red: `menus-page.test.tsx` — (a) toolbar 添加 drawer shows the
      上级菜单 selector prefilled 根目录 and saving sends `upId: 0`; (b) editing a
      top-level node displays 根目录 in the selector and the dropdown offers
      根目录. Verify: both new tests fail against the current page (2 failed |
      14 passed).
- [x] 1.2 Green: `buildParentOptions` prepends the 根目录 (value `0`) option;
      the 上级菜单 form item renders unconditionally (create toolbar / create
      under node / edit); `openCreate(null)` prefills `upId: 0`; submit reads
      `upId` from the form field (drop the hardcoded-0 branch). Verify: full
      `pnpm test` exit 0 (31 files / 361 tests).
- [x] 1.3 Follow-up (user bug report 2026-11-13, "下拉框里非常多的根目录"): the
      root option sat inside the recursive builder, so every subtree level grew
      its own value-0 根目录. Fix: split `collectParentOptions` (pure recursion)
      out of `buildParentOptions` (composes the single root option); a new test
      asserts exactly one 根目录 across an opened dropdown (red 1 failed →
      green; full `pnpm test` 362/362 + `pnpm build` exit 0).

## 2. Close-out

- [ ] 2.1 `pnpm build` exit 0 (done); user confirms visuals on the real backend.
- [ ] 2.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.
