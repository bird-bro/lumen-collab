# Tasks: org-logo-upload-web

## 1. Transport + client (TDD, depends on org-logo-upload-api being implemented)

- [x] 1.1 Failing `apiUpload` tests in `api-clients.test.ts` (FormData part +
      Bearer + no Content-Type; `200`→`{path}` / `203` / `A0400` semantics;
      `apiRequest` JSON regression pin).
- [x] 1.2 Extract the private `unwrapEnvelope` helper in `client.ts`, add
      `apiUpload<T>(path, file, partName = 'file')` (new export only) — green.
- [x] 1.3 Add `UploadedImage` to `types.ts` and `uploadImage()` to the new
      `src/api/common.ts` (`POST /system/common/upload/image`).

## 2. Org drawer upload control (TDD)

- [x] 2.1 Failing orgs-page tests: logo tile renders in the drawer; valid pick →
      `uploadImage` called, form `logoUrl` = returned path, save submits it;
      `.gif` → warning, no request; failed upload → old value kept + message.
- [x] 2.2 Implement `orgs/logo-upload.tsx` `LogoUploadField` — green. TWO design
      deviations found during TDD, both forcing correctness fixes: (a) the control is a
      CONTROLLED form control (`<Form.Item name="logoUrl">` injects value/onChange), not
      a `Form.useFormInstance` watcher — a bare watcher + unnamed field DROPPED `logoUrl`
      from `validateFields` (the submit contract broke) and could not read the pre-mount
      backfill; (b) NO `maxCount` — the internal list would cap at 1 and block the
      re-pick-replaces-tile flow (`showUploadList={false}` keeps the list out of view;
      `value` is the model). Recorded for the L2 audit.
- [x] 2.3 Drawer integration: remove `logoUrl` from `PROFILE_FIELDS`, add the
      labeled `Form.Item` beside the 地区 cascade; backfill/submit paths unchanged.

## 3. Entry + verification

- [x] 3.1 `vite.config.ts`: additive `/profile` proxy entry (same target as
      `/api/v1`, honors `VITE_API_ENTRY_URL`).
- [x] 3.2 `pnpm test` + `pnpm build` green — 244/244 (was 213 + 31 new), build clean.
      Visual smoke on the real backend belongs to the user check before /opsx:verify
      (backend endpoint already verified: upload → {path} → anonymous read-back).
- [x] 3.3 `/opsx:verify` for this change; archive together with
      `org-logo-upload-api` once BOTH verify PASS (feature gate).
      Result: **PASS** (verify.md, 2026-11-06) — L1 build exit 0, L2 10/10,
      L3 22 files / 244 tests / 0 failures.
