# Tasks — number-rule-unification-sev

## 1. lumen-api module + Feign contract

- [ ] 1.1 GREEN (structure): `lumen-api` module (plain jar, aggregator after `common`,
      minimal deps per design D5) with `NumberApi` (`@FeignClient` +
      `POST /api/v1/system/number-rules/issue`), `NumberIssueRequest`/`Response` DTOs,
      and the fail-fast fallback (THROWS — never fabricates). Architecture assertions:
      no entry point, no controllers-with-routes (the contract carries the endpoint
      DECLARATION only). Verify: `./mvnw -pl lumen-api -am compile -q` exit 0.

## 2. Platform number-rule domain (lumen-system)

- [x] 2.1 TDD: `NumberIssuerServiceTests` (lumen-system suite, fixture-family
      discipline + hard-DELETE sweeps) — RED `concurrentIssuanceDoesNotDuplicate` /
      `stampRuleResetsByDay` / `burnedSerialSurvivesCallerRollback` (REQUIRES_NEW
      commit despite outer rollback) / `noActiveRuleRejectedB0001`; GREEN the
      relocated row-lock issuer (design D3) + `NumberRule` entity/mapper
      (`selectActiveForUpdate`).
- [x] 2.2 TDD: `NumberRuleApiTests` — RED `ruleCrudEnforcesSystemPerms` (each write
      gated `system:number:*`, duplicate-active rejected) + `issueEndpointRejectsNonInternalCallers`
      (missing/wrong `X-Internal-Key` → auth rejection, no number); GREEN CRUD
      controller + `NumberRuleService` + the internal-key interceptor (design D4).
- [x] 2.3 Migration (V40 — V38/V39 taken by the parallel session, re-verify at write time): `number_rule` table +
      three family seeds + the menu seed move (号码规则 C node joins 系统管理 subtree
      with `system:number:list` + four button perms; the 患者配置 entry is retired);
      boot test extends to the new table + seed rows.

## 3. Patient rewire

- [x] 3.1 TDD: patient `PatientNumberIssuanceTests` rewritten — RED
      `rolledBackArchiveCreationBurnsTheNumber` (mocked `NumberApi`, REQUIRES_NEW-free
      delegation; the platform-side burn is the mock's contract); GREEN
      `PatientNumberService` delegating to `NumberApi` (direct-URL config via
      gitignored profile; `@MockBean` in tests).
- [x] 3.2 Retirement: patient-ledger migration DROPS `patient_number_rule`; the old
      controller + DTOs + service-local row-lock impl + `patient:rule:*` references
      removed (including the in-flight rename artifacts, superseded per the user
      ruling); stale tests removed/rewritten; `pnpm`-side API contract types follow in
      the web change.

## 4. Close-out

- [x] 4.1 Full reactor green: `./mvnw compile -q` + `./mvnw test -q` from the
      aggregator (JDK 11) — counts + log paths; lumen-system suite carries the
      relocated mechanism tests; lumen-patient suite green with the mocked contract.
- [ ] 4.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md; coordination
      registry updated.
