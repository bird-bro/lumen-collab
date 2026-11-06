# Proposal: org-logo-upload-api

## Why

The organization dialog's Logo field is a URL text input — there is no file-upload
facility anywhere in lumen, so logo (and every future image need) has no real path.
The OPS reference project ships a proven pattern (authenticated `upload/image` endpoint
→ disk via `FileUtils.uploadFile` → anonymous `/profile/**` read-back); lumen already
carries the copied `common/FileUtils.uploadFile`, the permitting `SecurityConfig` line,
and the `org_info.logo_url` column (V16) — only the endpoint and the static-serving
locations are missing. The user ruled the logo field should be an upload, not a URL,
modeled 1:1 on OPS (2026-11-06).

## What Changes

- NEW `POST /api/v1/system/common/upload/image` in lumen-system
  (`org.birdbro.lumen.system.controller.CommonController`, the OPS
  `birdbro-launch/CommonController` analog): `@RequestPart("file") MultipartFile`,
  authenticated-only (Spring Security default, no `@PreAuthorize`), validated
  server-side (extension whitelist `png/jpg/jpeg/webp`, `≤2MB`, non-empty; rejections
  → `A0400`), stored via the existing `common/FileUtils.uploadFile` under
  `${upload.avatar}/yyyy/MM/dd/uuid.ext`, returns `{ "path": <stored path> }` through
  the standard envelope (OPS 1:1; `GlobalResponseAdvice` wraps).
- NEW storage config in the committed `lumen-system` `application.yml` (no
  credentials): `upload.home: ./upload`, `upload.avatar: /profile/avatar` — the OPS
  property vocabulary; overridable via the gitignored `application-dev.yml`.
- FRAMEWORK adaptation #7: the copied `framework/WebMvcConfig` registers `/profile/**`
  with no `.addResourceLocations` (a truncated OPS line) — completed with
  `file:${upload.home}/` locations behind a blank-default `@Value`, so services without
  upload config behave exactly as today. Recorded in
  `openspec/coordination/org-logo-upload.md`.
- No DB migration: `org_info.logo_url` already exists (V16).
- No sys_attach bookkeeping layer in v1 — transport-only (user decision 2026-11-06,
  D2 of the coordination doc); logo is a 1:1 column value like OPS's film-home image.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: the Gateway Path Convention requirement gains the `/profile/**` static
  read-back segment (anonymous GET, outside the `/api/v1` envelope surface); a new
  requirement documents the common image upload endpoint (request shape, response
  envelope, validation, auth, storage layout, read-back).

## Impact

- `lumen-sev/lumen-system`: new `CommonController` + config entries; `application.yml`.
- `lumen-sev/framework`: `WebMvcConfig` one-line completion (adaptation #7) — every
  Servlet service loads it; blank default keeps other services unchanged.
- API surface: one new endpoint + one static segment; consumers = the web change
  `org-logo-upload-web` (org drawer). Entry contract: production nginx routes
  `/profile` → lumen-system when the deferred `docker/` change lands (not built here).
- Out of scope: sys_attach table, nginx implementation, multi-file/biz-tagged upload.
