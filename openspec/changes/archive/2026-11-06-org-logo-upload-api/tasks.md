# Tasks: org-logo-upload-api

## 1. Upload endpoint (TDD)

- [x] 1.1 Write failing `OrgLogoUploadTests` (house integration base
      `AuthIntegrationTestBase` + dedicated context with a run temp-dir `upload.home` —
      harness corrected from the standalone-MockMvc draft, see design.md): upload success →
      envelope `200`, `data.path` = `/profile/avatar/yyyy/MM/dd/<uuid>.<ext>`, bytes on disk; wrong extension / >2MB / empty file → `A0400` and nothing written; tokenless → `203`.
- [x] 1.2 Add `upload.home: ./upload` + `upload.avatar: /profile/avatar` to the
      committed `lumen-system/src/main/resources/application.yml` (comment: OPS
      vocabulary, no credentials; dev override via gitignored `application-dev.yml`) —
      plus `spring.servlet.multipart` ceilings (5MB/10MB) so over-cap files reach the
      controller's `A0400` instead of Boot's 1MB default 500.
- [x] 1.3 Implement `org.birdbro.lumen.system.controller.CommonController`
      (`@RequestMapping("/api/v1/system/common")`, `@PostMapping("/upload/image")`,
      `@RequestPart("file")`): validation per design D4 (whitelist/size/empty →
      `A0400`), storage via `common/FileUtils.uploadFile` (D5), return
      `Map<String,String>{path}`. `@author birdbro` header. Green: 1.1 passes.
- [x] 1.4 Serving test (red→green): complete `framework/WebMvcConfig` with
      `file:${upload.home}/profile/` locations (DISK TREE MIRRORS URL TREE — FileUtils
      composes `/profile/...` under `upload.home`) behind `@Value("${upload.home:}")`
      (adaptation #7); test asserts anonymous `GET /profile/...` serves stored bytes
      with the locations present, and a blank `upload.home` registers without
      locations (other-service parity scenario).

## 2. Verification

- [x] 2.1 `export JAVA_HOME=...jdk-11.jdk/.../Home && ./mvnw -pl lumen-system -am
      test -q` from `lumen-sev/` — all green (reactor rule, no `-f` from root).
      Evidence: 107/107 (one earlier 2-failure run was cross-session team-DB
      interference from the parallel workstation-context archive — reproduced clean
      twice, isolated 8/8 with and without my changes).
- [x] 2.2 Manual smoke against `pnpm dev` + running lumen-system: upload a logo,
      confirm `{path}`, open `http://localhost:8081/profile/...` directly.
      Evidence: upload → envelope `{path}` ✓; anonymous GET → `200 image/png`,
      byte-identical ✓; file landed at the ABSOLUTE `upload.home` anchor
      (application-dev.yml, user decision 2026-11-06 — the relative `./upload`
      default split write/read anchors across launcher CWDs and 404'd silently;
      coordination doc D5 amendment).
- [x] 2.3 Run `/opsx:verify` for this change (L1 build / L2 WHEN-THEN / L3 tests);
      only after `org-logo-upload-web` also verifies PASS, archive both (feature
      gate in `openspec/coordination/org-logo-upload.md`).
      Result: **PASS** (verify.md, 2026-11-06) — L1 compile exit 0, L2 7/7,
      L3 26 suites / 107 tests / 0 failures.
