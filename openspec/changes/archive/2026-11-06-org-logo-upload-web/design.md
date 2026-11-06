# Design: org-logo-upload-web

## Component Shape

- **`LogoUploadField`** (`src/pages/system/orgs/logo-upload.tsx`, new file — mirrors
  the `RegionSelects` precedent: a self-contained form-bound control resolved via
  `Form.useFormInstance`, because AntD's `Upload` does not natively bind a bare
  string value):
  - Binds `logoUrl` (string | undefined) as the stored path; the tile image renders
    the value as-is (same-origin `/profile/...` via the static segment; absolute
    legacy URLs pass through).
  - `beforeUpload(file)`: extension whitelist `png/jpg/jpeg/webp` (case-insensitive,
    from the name — same gate as the backend) + `file.size > 2 * 1024 * 1024` →
    `message.warning` + `return false` (no request; AntD keeps the file out of the
    list). Valid → `return false` as well (the upload is driven by `customRequest`,
    not the default action poster).
  - `customRequest({ file, onSuccess, onError })`: `void uploadImage(file)` →
    `onSuccess(res)`; on `ApiError` → `message.error(res.msg)` + `onError(...)`; the
    form value is set on success only (`form.setFieldValue('logoUrl', path)`), so a
    failed upload leaves the previously stored value intact (delta scenario 4).
  - `maxCount: 1`, `accept="image/*"`, `listType: "picture-card"`; re-picking
    replaces the tile (AntD maxCount replace) and re-uploads.
- **Drawer integration**: `logoUrl` leaves `PROFILE_FIELDS` (whose entries are all
  Inputs) and gets its own labeled `Form.Item` next to the 地区 cascade — the
  PROFILE_FIELDS map keeps rendering the remaining five Input fields; the
  `openDialog` backfill (line ~513) and `submitDialog` body assembly need no change
  (the wire contract keeps `logoUrl`).
- **`client.ts` — `apiUpload<T>(path, file: File, partName = 'file')`**: new export;
  builds `FormData` (single part), `Authorization: Bearer` when a token exists, NO
  `Content-Type`, POST; envelope unwrap extracted from `apiRequest` into a private
  `unwrapEnvelope<T>(response, path)` helper used by both transports (public
  behavior of `apiRequest` byte-identical — the never-modify rule constrains the
  public surface, and its own unit tests pin that).

## Data Flow

pick file → beforeUpload gate → customRequest → `uploadImage(file)`
(`POST /api/v1/system/common/upload/image`) → envelope `{data:{path}}` →
form `logoUrl` = path → 保存 flows through the existing `createOrg`/`updateOrg`
body unchanged → after reload the card Avatar and the tile resolve the stored
`/profile/...` path through the dev proxy.

## Error Handling

| case | surfacing |
|:--|:--|
| wrong type / oversize (client gate) | `message.warning`, no request, tile unchanged |
| backend `A0400` (server gate) | `message.error(res.msg)` — dialog stays open, old value kept |
| transport/network `B0001` | same as above |
| `"203"` | shared `onSessionExpired()` semantic (redirect), then throw |

## Testing (Vitest + RTL, seam-stubbed)

- `api-clients.test.ts`: `apiUpload` — FormData part + Bearer header + NO
  Content-Type (fetch stubbed), envelope `200` → `{path}`, `203` fires
  `onSessionExpired` then throws, `A0400` throws `ApiError`; `apiRequest` JSON
  behavior regression (Content-Type still `application/json`).
- `orgs-page.test.tsx`: logo tile renders in the drawer (mocked `@/api/common`);
  successful upload → form value + submitted body carry the returned path;
  `.gif` pick → warning shown, `uploadImage` NOT called; upload failure → old
  value kept, message surfaced.
- `pnpm test` + `pnpm build` (tsc included) green before verify.
