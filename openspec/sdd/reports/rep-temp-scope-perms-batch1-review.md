# Batch 1 Review: rep-temp-scope-perms (tasks 1.1-1.4, lumen-sev)

## Verdict: PASS (0 Critical / 0 Important / 3 Minor)

Commit 9e18f6c implements D1/D2/D3(V42-renumber)/D5 faithfully; the 5 delta scenarios map 1:1 to tests; commit hygiene clean; TDD evidence verified.

### Minor
1. **D5 cell untested - "only PUBLIC family writes its OWN private row"**: AuthRepTempScopePermTests.java:224-244
   covers only the non-owner variant (A0400); the owner variant (public-only family on own private
   row -> A0301 from the type=2 branch, implemented at RepTempServiceImpl.java:152-155) has no pin;
   likewise "base:list + public:list read-only user" is covered only via the dual-family token. The
   5 delta scenarios ARE covered 1:1 - gap is vs D5's full grid. Fold 1-2 assertions into the verify
   run; not blocking.
2. **RED wording nit**: /tmp/rep-temp-red.log shows 13 run / 6F+1E where dualFamilyUserKeepsEveryPathGreen
   passed in RED by construction (dual tokens carry the base family the old pins demanded) - a
   regression canary, not a RED proof. Genuine; no action.
3. **Brief typo handled correctly**: brief task 1.3 says "四个写端点"; the controller has three mutating
   pins - implementer followed design D1 / delta. No action.

## Spec compliance (delta scenarios, all pinned in AuthRepTempScopePermTests)
pins-admit-either-family (:150-186, public-only creator passes pin + service); non-owner private
refused content-free (:224-244); private create binds owner (:206-210); public write w/o family
A0301 incl. refused-delete leaves row live (:81-116); public write w/ family owner_uid NULL
(:166-168); public read w/o public:list A0301 tree+page + private-tree contrast 200 (:119-145).
system-data: 4 F nodes under the 报告模板 C row (up_id re-validated as C/'setting:temp:list'), role-1
grants, sorts 4-7, replay no-op (:249-300).

## Quality
- V42 vs V28: perms-verbatim F inserts, NOT EXISTS among LIVE rows by type+perms, parent resolved
  BY PERMS, role-1 grant guarded per row, setval tail, comments semicolon-free.
- D2 order exact: create asserts BEFORE requireModality/guardContent (ServiceImpl:126-128);
  update/delete = requireOwnedRow -> row-type assert (:152-155, :184-186); reads assert public:list
  only when type=1 (:76-79, :109-111). Refusal = AccessDeniedException -> AuthDenyAdvice (@Order
  HIGHEST_PRECEDENCE, AuthDenyAdvice.java:31-41) A0301 content-free (envelope carries only the missing
  perm string, per D2); no new error codes; A0400 untouched.
- Transactions: create/update/delete carry NO @Transactional - asserts precede all DML, each write is
  one mapper statement, no partial-write window.
- Null-type hole closed: TempCreateRequest.type / TempPageQuery.type @NotNull + @Valid at binding;
  requireScopeFamily never sees null via HTTP. Architecture: private ServiceImpl helper,
  SecurityContextHolder direct read, zero framework/common edits, no out-of-scope files.
- Tests: authorities production-homologous (mintToken -> real TokenService.createToken(UserLogin), same
  shapes the pin chain and SecurityContextHolder see); empty quadrant via legacy noPerm token. RED
  verified: /tmp/rep-temp-red.log "Tests run: 13, Failures: 6, Errors: 1" (RED-era name v41ReExecution...
  consistent with the 10:32 V41 collision; V41__number_rule_menu_component.sql exists). GREEN via
  surefire: ScopePerm 7/7 + PermPin 3/3 + Api 3/3 + Content 7/7 = 20/20; PermPin also pins read methods
  stay class-gated. Manage-suite interference (15F) matches the documented shared-server hazard; verify
  needs an exclusive window.

## 8 Gates
1. 提交卫生: PASS - 6 files all lumen-sev; zero openspec/, zero lumen-web; parent=43ef5f6.
2. V42 vs V28: PASS - F nodes/guards/grant/setval verbatim shape; replay no-op green.
3. D2: PASS - create family-first; update/delete ownership-first; reads type=1 -> public:list; A0301
   content-free; A0400 preserved (explicit stranger test).
4. D1 pins: PASS - 3 pins hasAnyAuthority both families; class gate + tree/page untouched.
5. authorities: PASS - real TokenService mint path; quadrants covered (2 thin cells = Minor 1).
6. TDD evidence: PASS - RED 13/6F+1E + GREEN 20/20 verified; 13 legacy intact.
7. V42 history-row replay: PASS - row deleted -> clean re-apply; post-episode boots all ran Flyway
   validation (no checksum divergence can persist); committed == worktree.
8. 架构账: PASS - private ServiceImpl helper, no framework touch, no out-of-scope files.
