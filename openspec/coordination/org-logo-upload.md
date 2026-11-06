# org-logo-upload — Coordination (Cross-Stack Feature)

> Feature: replace the org dialog's Logo URL text input with a real image upload,
> following the OPS reference upload pattern 1:1 (user direction 2026-11-06:
> "logo 应该是上传图片，不是 url，对于图片上传你可以去参考 ops 项目中").
> OPS lineage: `birdbro-launch/CommonController.uploadImage` + `common/FileUtils.uploadFile`
> + `/profile/**` static read-back (framework `WebMvcConfig` / `SecurityConfig`).

## Change Registry

| Change | Stack | Scope |
|:--|:--|:--|
| `org-logo-upload-api` | lumen-sev | Upload endpoint + storage config + `/profile/**` static serving |
| `org-logo-upload-web` | lumen-web | Multipart transport + upload client + org drawer Upload control + dev proxy |

**Gate**: feature done = BOTH changes verify PASS (`openspec/verify.config.yaml`) → archive each.
**SATISFIED 2026-11-06**: `org-logo-upload-api` PASS (7/7 scenarios, 107/107 backend tests) and
`org-logo-upload-web` PASS (10/10, 244/244 frontend tests) — both archived as
`2026-11-06-org-logo-upload-{api,web}`; specs synced into api / web-app-shell / web-system-console
(sync markers added per LoopForge convention). Git commit is a separate track.
Backend first; the frontend builds against the real endpoint (no mock layer, user ruling 2026-11-05).

## Shared Design (decided 2026-11-06, user-confirmed)

- **D1 — Endpoint home: lumen-system.** OPS puts the common upload in the launch
  aggregation service; lumen has no launch module, lumen-system is the console/JWT-issuer
  service and the org domain's owner. Endpoint: `POST /api/v1/system/common/upload/image`
  (controller `org.birdbro.lumen.system.controller.CommonController` — the OPS
  `CommonController` analog). Future services copy this precedent per-service.
- **D2 — Transport-only, no bookkeeping (sys_attach NOT built in v1).** OPS decouples the
  two layers: the upload endpoint stores a file and returns `{path}`; the `sys_attach`
  table (birdbro-ice: `biz_type` + `ref_id` + `tag` + file facts, primitives
  `listAttaches(bizType, refIds, tags...)` / `replaceByTag(...)`) is the optional
  bookkeeping layer business pages opt into. OPS's closest logo analog
  (`FilmHomePageService.uploadFilmHomePageImage`) also stores the URL into a business
  column WITHOUT an attach row. lumen follows: logo = column value (`org_info.logo_url`,
  already in V16) — no attach record. **Future direction** (recorded, not built): the
  first true attachment-collection scenario (report attachments, exam photos) triggers a
  separate change porting the sys_attach table + `IAttachService` primitives — the upload
  endpoint is unchanged by that.
- **D3 — Auth: authenticated-only.** Spring Security `anyRequest().authenticated()`
  covers the endpoint (no `@PreAuthorize`); the org dialog gates its own edit affordance
  by `system:org:update` client-side (existing button-level gating).
- **D4 — Validation double-sided**: extension whitelist `png/jpg/jpeg/webp` + `≤2MB`
  enforced client-side (beforeUpload) AND server-side (`A0400` per errors spec); empty
  file also `A0400`. (eis reference validates client-side only; user chose the hardened
  variant.)
- **D5 — Storage layout (OPS 1:1)**: disk root `${upload.home}` (default `./upload`),
  subdir `${upload.avatar}` (default `/profile/avatar`) + `/yyyy/MM/dd/` + `uuid.ext`,
  via the existing `common/FileUtils.uploadFile` (already the OPS copy). Stored value =
  `/profile/avatar/2026/11/06/uuid.png` — directly usable as `<img src>`.
- **D6 — Static read-back `/profile/**`**: anonymous GET, already permitted by the
  copied framework `SecurityConfig` (`.antMatchers(HttpMethod.GET, ..., "/profile/**")`).
  The copied framework `WebMvcConfig` registers `/profile/**` WITHOUT
  `.addResourceLocations` — an obviously truncated OPS line (its own comment says
  本地文件上传路径). Framework adaptation **#7** completes the line with
  `file:${upload.home}/` + `@Value("${upload.home:}")` (empty default = every other
  service registers exactly as today). Recorded here because `init-project-skeleton.md`
  is historical; naming.md's "six adaptations" snapshot predates this.
- **D7 — Entry contract for `/profile/**`**: the static segment lives OUTSIDE
  `/api/v1`. Vite dev proxy gains a `/profile` entry (same target); production nginx
  must route `/profile` → lumen-system when the deferred `docker/` change lands
  (recorded in the api spec's Gateway Path Convention delta, not implemented here).

## Framework Adaptations

- **#7 (2026-11-06, org-logo-upload-api)**: `framework/WebMvcConfig.addResourceHandlers`
  — completed the truncated OPS `/profile/**` registration with
  `.addResourceLocations("file:" + uploadHome + "/")`; new `@Value("${upload.home:}")`
  field (blank = no locations, OPS-parity for services without upload config).
  One-line completion of an OPS line that its own comment declares as the local upload
  path; additive-first satisfied by the conditional no-op default.

## Decisions Log

- 2026-11-06: endpoint home = lumen-system (user picked; no launch module exists).
- 2026-11-06: auth = 登录即可 (user picked, OPS 1:1).
- 2026-11-06: validation = png/jpg/jpeg/webp ≤2MB double-sided (user picked).
- 2026-11-06: sys_attach NOT built in v1 — transport-only (user picked); D2 records
  the port direction for the first real consumer.
- 2026-11-06 (smoke finding, user decision): **upload.home MUST be an ABSOLUTE path in
  the per-machine dev override.** Relative `./upload` anchors at the launcher's CWD and
  the anchors DISAGREE per launcher (this machine: the dev JVM resolved writes into
  `lumen-system/target/classes/upload` while `user.dir` reported `lumen-sev`, so the
  /profile handler served a different tree — silent 404; probe-file test proved the
  handler side correct). Committed yml keeps `./upload` as the portable default with a
  comment steering to the absolute dev override.
