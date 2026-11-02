# Batch 4 Review — Admin CRUD: units/departments/users/roles/menus (tasks 4.1-4.4)

> Reviewer verdict persisted verbatim by the coordinator (2026-11-02). Independent
> verification: full diff ec75fe0..b72d4de file-by-file; copied-machinery claims re-checked
> against framework/common sources; ./mvnw -pl lumen-auth -am test -q re-run → MVN_EXIT=0,
> 34/34 green (Application 1, AuditIdentity 1, DataLayer 4, Department 3, Login 5, RoleMenu 4,
> SchemaMigration 4, SeedMigration 5, UnitCrud 3, UserManagement 4).

## Spec Compliance — 10/10 PASS

1. Units (4.1): UnitController POST/PUT/DELETE/GET /tree per binding design; tree via
   UnitTreeService (root 0); DELETE is @TableLogic logical delete (EntityBase:22-27
   verified); two-root CONTAINS test (示例医院 + 第二根_<ts>, size>=2), child nesting after
   rename, unknown-parent A0400 (service-level - no self-FK by design, V1:12).
2. Departments (4.2): service-side FK A0400 with V1 DB FK kept as backstop; seeded
   CONTAINS assertions; duplicate code A0712 with self-exclusion on update.
3. Users (4.3): list via explicit-field UserItemDto (test asserts NO "password" key);
   service-level A0111 added on live rows (RED excerpt shows the old generic "500"); V1
   partial-unique index untouched; admin reset proven (old-pass A0210 / new-pass 200 /
   mustChangePassword=1); enable/disable → A0202 → re-enable; batch-3 handoff honored
   (A0111 + shared encoder bean).
4. Roles/menus (4.4): role CRUD (dup name A0714), full-replace PUT /{id}/menus
   (delete-then-insert, dedupe, empty clears), GET /menus/tree. Role-delete CASCADE
   verified CORRECT: the Batch-3 SQL (RoleMenuMapper:36, MenuInfoMapper:35) walks
   user_role→role_menu→menu_info with no role join - live bindings to a logically deleted
   role would keep granting forever; cascade is the only sound choice that does not touch
   off-limits Batch-3 code; test proves zero live binding rows + user perms drop.
5. 4.4 chain: create role → CreateUserRequest.roleIds → assign [3,6] → login AS THE NEW
   USER → /auth/me perms contains radiology:report:audit AND menus contain 放射科; grant-drop
   proven (full-replace). ([3,6] is required by the brief's letter - a bare [6] grant
   orphan-drops and cannot satisfy the 放射科 assertion.)
6. Envelope semantics verified against copied sources: R.ofSuccess() (void) = "204"
   (R.java:65-71), R.ofSuccess(data) = "200", R.ofFail ignores its data argument
   (R.java:83-88). Mutations return Boolean.TRUE/id consistently; recorded in javadoc.
7. No new anonymous paths (AuthWebSecurityConfig:35 unchanged); all new endpoints ride the
   copied anyRequest().authenticated() chain.
8. deleted=0 discipline: zero mapper files changed (wrapper queries; @TableLogic appends).
9. Verbatim discipline: git diff -- framework common EMPTY; 30 files all under lumen-auth.
10. Tests: 34/34 independently re-confirmed; RED shapes internally consistent; shared-DB
    hygiene held.

## Strengths

- Role-delete cascade: implementer read the actual Batch-3 SQL, recognized "leave
  bindings" was not inert, chose the only sound fix, proved grant-drop end-to-end.
- Envelope-contract discovery (void→"204", ofFail dropping data) recorded for the
  frontend mock effort instead of silently worked around.
- Real-behavior assertions (login with old/new passwords, DB verdicts, perms membership).
- Honest concern list; self-review matches independent findings.

## Issues

**Critical** — none. **Important** — none.

**Minor:**
1. Re-parent does not re-derive level (UnitServiceImpl.java:88) - stale depth on
   re-parent without explicit level; tree assembly keys on upId so cosmetic; overload
   candidate for a later change.
2. Partial-update DTOs accept blank strings (no @NotBlank on update request names/codes) -
   tighten when the admin UI validation pass lands.
3. Spec SHALL vs implementation: "authenticated administrator" - implementation enforces
   authentication only (any valid token); NOT a batch defect (no task in this change
   mandates authority enforcement; binding design says "all token-guarded"; permission
   gating queued to DataScope/radiology change) - MUST be recorded explicitly at
   archive/verify so the deferral is not silent.
4. Known recorded follow-ups: unit delete does not guard live children/departments;
   re-parent cycles undetected; /users/list unpaginated; update_time staleness from
   copied MetaObjectConfig.
5. Housekeeping: tick 4.1-4.4 in the closure commit (done by coordinator).

## Assessment

**Approved.** All four endpoint groups match the binding design and the spec delta's
WHEN/THEN scenarios; the two consequential judgment calls (role-delete cascade,
full-replace assignment) are verified correct against the actual copied machinery and
proven end-to-end; framework/common untouched; 34/34 independently re-confirmed.
