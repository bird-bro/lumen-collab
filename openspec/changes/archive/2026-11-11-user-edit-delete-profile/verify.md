<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: user-edit-delete-profile
  date: 2026-11-11
  overall: PASS   # 2026-11-11 refresh: api-17 denial test added (AuthPermEnforcementTests 9/9)
  stacks:
    backend:
      L1: PASS
      L2: "32/32"             # backend-owned scenarios; the 16 web-system-console scenarios belong to the sibling change
      L3: PASS
---

# Verification: user-edit-delete-profile

Overall: **PASS** — 2026-11-11 refresh closes the single PARTIAL: the perm-less
denial test for the two new write endpoints landed
(`AuthPermEnforcementTests.permLessUserIsDeniedOnTheNewUserWriteEndpoints`, 9/9).

## backend (lumen-sev · lumen-system)

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q`; exit=0; logs/build-backend.log |
| L2 spec | 32/32 | 0 PARTIAL (the former api-17 gap closed) |
| L3 tests | PASS | `./mvnw test -q`; exit=0; 173/173 (lumen-system 169 + common 4), 0 failures 0 errors; logs/test-backend.log |

## Scenario alignment

### api — User Management Endpoints (17)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Paged user list | IMPLEMENTED | UserInfoController.java:87-91; AuthUserProfileApiTests.java:351 |
| List rows carry the profile and role names | IMPLEMENTED | UserInfoServiceImpl.java:186-222; AuthUserProfileApiTests.java:51-91 |
| Create user | IMPLEMENTED | UserInfoServiceImpl.java:102-117; AuthUserManagementTests.java:48-80 |
| Duplicate username | IMPLEMENTED | UserInfoServiceImpl.java:161-168; AuthUserManagementTests.java:83-108 |
| User detail for the edit dialog | IMPLEMENTED | UserInfoServiceImpl.java:225-234; AuthUserProfileApiTests.java:115-137 |
| Detail of a missing user | IMPLEMENTED | AuthUserProfileApiTests.java:139-149 (B0031) |
| Profile update | IMPLEMENTED | UserInfoServiceImpl.java:237-273; AuthUserProfileApiTests.java:152-190 |
| Signature stores the uploaded path | IMPLEMENTED | UserInfoServiceImpl.java:264; AuthUserProfileApiTests.java:73 |
| Username is immutable through the update | IMPLEMENTED | UpdateUserRequest (no username field); AuthUserProfileApiTests.java:245-265 |
| Update validates role ids and department | IMPLEMENTED | UserInfoServiceImpl.java:242-248,339-353; AuthUserProfileApiTests.java:218-243 |
| User logical delete with cascade unbind | IMPLEMENTED | UserInfoServiceImpl.java:278-305; AuthUserProfileApiTests.java:267-297 |
| Self-delete rejected | IMPLEMENTED | UserInfoServiceImpl.java:283-286; AuthUserProfileApiTests.java:299-311 |
| Seed-admin delete rejected | IMPLEMENTED | UserInfoServiceImpl.java:288-291,308; AuthUserProfileApiTests.java:313-324 |
| Password reset and enable toggle | IMPLEMENTED | UserInfoController.java:126-137; AuthUserManagementTests.java:111-196 |
| Encrypted password fields | IMPLEMENTED | UserInfoServiceImpl.java:124,156; AuthUserManagementTests.java:54,120 |
| Plaintext or undecryptable value rejected | IMPLEMENTED | PasswordCipherHelperTest.java:91-103; AuthLoginTests.java:102-130 |
| New button permissions gate the new endpoints | IMPLEMENTED | @PreAuthorize pins UserInfoController.java:103,115 + V26 F nodes (AuthUserProfileSeedTests.java:96-108); the perm-less denial test `permLessUserIsDeniedOnTheNewUserWriteEndpoints` (AuthPermEnforcementTests.java:172-220) proves A0301 fires before the controller on both writes - no column moves, the row stays live |

### system-data — Dict Seed Data (4)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Fresh database carries the starter dictionary | IMPLEMENTED | V26 section 2; AuthUserProfileSeedTests.java:86-94 |
| Re-run is a no-op | IMPLEMENTED | AuthUserProfileSeedTests.java:111-128 (dollar-quote-aware splitter) |
| Exam-type vocabulary renders abbreviations | IMPLEMENTED | V26 dict_detail 1021001-1021004 (CT/MR/US/ENDO); AuthUserProfileSeedTests.java:86-89 |
| Audit-authority vocabulary carries the three tiers | IMPLEMENTED | V26 1022001-1022004 (不参与/报告/初审/终审); AuthUserProfileSeedTests.java:91-94 |

### data — User Profile Columns (5)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Profile columns added without breaking existing rows | IMPLEMENTED | V26 rebuild; AuthUserProfileSeedTests.java:46-67 (nullable=ALL); AuthSchemaMigrationTests.java:104-127 (audit-tail pin) |
| Department anchor references a live department | IMPLEMENTED | AuthUserProfileSeedTests.java:69-84 (0 physical FKs); UserInfoServiceImpl.java:242-245 (A0400 on dead depId) |
| Gender and title store dict codes | IMPLEMENTED | AuthUserProfileApiTests.java:70-72 (100401/100503 codes) |
| Exam-type visibility stores a CSV of codes | IMPLEMENTED | AuthUserProfileApiTests.java:74; V26 col `modalitys varchar(128)` |
| Signature stores a served path | IMPLEMENTED | AuthUserProfileApiTests.java:75; UserInfoServiceImpl.java:264 |

### auth-rbac — User Management (6)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Administrator creates a user | IMPLEMENTED | AuthUserManagementTests.java:48-80 (create_by fill) |
| Duplicate username rejected | IMPLEMENTED | AuthUserManagementTests.java:83-108 |
| Administrator edits a user's profile | IMPLEMENTED | AuthUserProfileApiTests.java:152-190 (update_by via MetaObjectConfig) |
| Role rebinding is a full replace | IMPLEMENTED | AuthUserProfileApiTests.java:192-215; UserInfoServiceImpl.java:357-371 |
| Administrator deletes a user | IMPLEMENTED | AuthUserProfileApiTests.java:267-297 (one transaction) |
| Protected accounts cannot be deleted | IMPLEMENTED | AuthUserProfileApiTests.java:299-324 |

### web-system-console — User Management (16)

OUT OF SCOPE for this change's verify: the page work is the sibling change
`user-edit-delete-profile-web` (coordination Change Registry) — its scenarios are
verified in that change's own `/opsx:verify`. The backend API these scenarios consume
is implemented and proven above.

## Issues

(none — the 2026-11-11 refresh closed the only PARTIAL; L2 32/32, L3 173/173.)
