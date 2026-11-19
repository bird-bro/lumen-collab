# Tasks — number-rule-unification-web

## 1. Client + page migration

- [x] 1.1 TDD red: page tests repoint — mocked module becomes
      `@/api/number-rules`, perms strings `system:number:*`, route path per the
      migrated seed; RED `ruleTypeSelectsThePlatformFamily` (select offers
      影像号/检查号/预约号). Verify: red on the current page.
- [x] 1.2 GREEN: `src/api/number-rules.ts` (typed against the system endpoints); page
      moves to the 系统管理 page directory (route per the seed); imports repoint; the
      patient.ts rule section + old-location files deleted. Verify: full `pnpm test`
      exit 0.

## 2. Close-out

- [x] 2.1 `pnpm build` exit 0 + `pnpm test` counts; user visual confirm on the real
      backend (page under 系统管理, family select, drawer flows).
- [x] 2.2 `/opsx:verify` → verify.md; coordination registry updated.
