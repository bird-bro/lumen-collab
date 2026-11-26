# Tasks

> RECORDING change — both tasks verify the ALREADY-COMMITTED implementation
> (`9ca86f4`/`e737153`); no new code.

## 1. Contract verification

- [x] 1.1 Backend: the keyword API test passes in isolation (name/accession hits +
  meter narrowing) — verified 2026-11-26:
  `./mvnw test -q -pl lumen-us -am -Dtest='UsRegistrationApiTests#railQuickSearchOrMatchesTheIdentityHandles' -Dsurefire.failIfNoSpecifiedTests=false`
  exit=0. (The class's other 12 failures are the room change's mid-apply churn —
  recorded in the coordination note; not this change's surface.)
- [x] 1.2 Frontend: the page suite passes with the quick-search + chip assertions —
  verified 2026-11-26: `pnpm vitest run src/test/us-registration-page.test.tsx`
  38/38 exit=0; full web suite 522/522 + `pnpm build` exit=0.
