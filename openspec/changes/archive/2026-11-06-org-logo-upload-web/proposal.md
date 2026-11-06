# Proposal: org-logo-upload-web

## Why

The org dialog's Logo field (`Logo 地址` Input, placeholder `https://...`) asks
administrators to paste a URL no one can produce — there is no upload path behind it.
The backend change `org-logo-upload-api` lands the spec'd upload endpoint
(`POST /api/v1/system/common/upload/image`); this change makes the dialog use it,
replacing the URL input with a real image upload control (user direction 2026-11-06:
"logo 应该是上传图片，不是 url").

## What Changes

- `src/api/client.ts`: additive multipart transport `apiUpload()` (new export;
  `apiRequest` untouched — never-modify rule): FormData, Bearer header, NO
  `Content-Type` (browser sets the multipart boundary), same envelope unwrap +
  `"203"` semantic as JSON calls; the unwrap block is extracted into a private
  shared helper (internal refactor only).
- NEW `src/api/common.ts`: `uploadImage(file): Promise<{ path: string }>` →
  `POST /system/common/upload/image` (resource path joined on `BASE_PATH`).
- `src/api/types.ts`: `UploadedImage { path: string }`.
- Org drawer (`src/pages/system/orgs/`): the `logoUrl` entry leaves
  `PROFILE_FIELDS`'s Input rendering and becomes an Upload picture-card field —
  NEW `orgs/logo-upload.tsx` (`LogoUploadField`, mirrors the `RegionSelects`
  pattern: `Form.useFormInstance`, binds the form value `logoUrl` as the stored
  path): maxCount 1, `accept="image/*"`, `beforeUpload` validates
  png/jpg/jpeg/webp ≤2MB (rejection = message + no request), `customRequest` →
  `uploadImage` → form value = returned `path`; existing value renders as the
  tile image (relative `/profile/...` works via the proxy; legacy absolute
  `http(s)` URLs still display).
- `vite.config.ts`: one additive proxy entry `/profile` → the same unified-entry
  target (dev direct-service preview can resolve stored paths; production nginx
  routing is the api change's recorded entry contract).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-app-shell`: the Unified API Entry Boundary requirement gains the `/profile`
  static segment in the dev proxy; a new requirement documents the multipart
  upload transport as part of the client seam (envelope semantics preserved).
- `web-system-console`: the Hospital Profile and Campus Table Management
  requirement's profile-form surface changes — the logo field is an image upload
  control, not a URL input.

## Impact

- `lumen-web/src/api/` (client transport + one new resource client + one type),
  `src/pages/system/orgs/` (drawer form field), `vite.config.ts` (proxy entry).
- Depends on `org-logo-upload-api` being implemented first (backend-first ruling —
  the page builds against the REAL endpoint; unit tests stub the `@/api/common`
  seam only).
- Out of scope: sys_attach UI, multi-file upload, avatar upload elsewhere.
