# Design: org-logo-upload-api

## OPS Lineage Map (1:1 targets)

| lumen artifact | OPS source |
|:--|:--|
| `org.birdbro.lumen.system.controller.CommonController` | `birdbro-launch/.../controller/manage/CommonController.uploadImage` |
| `common/FileUtils.uploadFile` (exists — reused as-is) | `birdbro-common/.../FileUtils.uploadFile` (already the copy) |
| `upload.home` / `upload.avatar` properties | OPS Nacos keys (values lost with Nacos; lumen sets committed safe defaults) |
| `framework/WebMvcConfig` locations completion (adaptation #7) | same file, truncated line 27 (comment says 本地文件上传路径, chain missing) |
| `/profile/**` anonymous GET | framework `SecurityConfig` line 109 (already copied, already permitting) |

## Decisions

- **D1 endpoint home (user-confirmed)**: lumen-system. OPS's launch service is the
  console aggregation; lumen's console service is lumen-system (JWT issuer, org domain
  owner). Package `org.birdbro.lumen.system.controller` per naming rules; path prefix
  `/api/v1/system/common` matches the house convention of controllers mapping the FULL
  gateway path (cf. `OrgController` `@RequestMapping("/api/v1/system/orgs")`).
- **D2 transport-only (user-confirmed)**: no sys_attach in v1. Response is the OPS
  `Map<String, String>` `{path}` (wrapped by `GlobalResponseAdvice` → envelope `data`).
- **D3 auth (user-confirmed)**: authenticated-only via the global chain. No
  `@PreAuthorize`, no new permission code — the dialog's affordances are already
  client-gated by `system:org:update` (button-perm-enforcement lineage).
- **D4 validation (user-confirmed)**: extension whitelist (`png/jpg/jpeg/webp`,
  case-insensitive) + size cap `2 * 1024 * 1024` + non-empty file; failures throw the
  `A0400` business error (`BusinessException` / `GlobalExceptionHandler` path, msg
  names the violated rule). Content-Type is NOT trusted — extension of the original
  filename is the gate (OPS `FileUtils` behavior; no magic-byte sniffing in v1).
- **D5 storage (OPS 1:1)**: `FileUtils.uploadFile(file, avatar + File.separator +
  yyyy/MM/dd, home)`; configured in the COMMITTED `application.yml` as
  `upload.home: ./upload`, `upload.avatar: /profile/avatar` (no credentials — the
  yml stays shippable); `application-dev.yml` may override both.
- **D6/adaptation #7**: complete the truncated `WebMvcConfig` line with
  `.addResourceLocations("file:" + uploadHome + "/")`. New `@Value("${upload.home:}")`
  field on the configurer; when blank, the registration runs exactly as OPS wrote it
  (no locations) — a conditional no-op, so the twenty-odd other servlet behaviors are
  byte-identical. Rationale for editing the OPS line instead of a second
  `WebMvcConfigurer` bean: same-pattern double registration in one
  `ResourceHandlerRegistry` has order-dependent semantics; completing the self-declared
  upload-path line is the honest minimal evolution, logged as adaptation #7 in the
  coordination doc.
- **D7 entry contract**: `/profile/**` outside `/api/v1` — the delta's Gateway Path
  Convention modification records the static segment + routing obligation; vite
  (web change) and future nginx implement it.

## Error Handling

| case | code | msg |
|:--|:--|:--|
| missing/empty `file` part | `A0400` | 文件不能为空 |
| extension not in whitelist | `A0400` | 仅支持 png/jpg/jpeg/webp 图片 |
| size > 2MB | `A0400` | 图片大小不能超过 2MB |
| disk write IOException | `B0001` | 上传文件失败 + cause (OPS 1:1 wrap) |
| no token | `203` | entry point (chain, not controller) |

## Testing (house integration harness, TDD)

- Harness correction (found at apply time; supersedes the standalone-MockMvc guess in
  the original draft): the established lumen-system harness is
  `AuthIntegrationTestBase` — `@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("test")`
  against the team test server (PG + Redis via gitignored `.env-test.local`), real
  servlet context, real security chain, real `GlobalResponseAdvice`. Standalone MockMvc
  would hand-build facsimiles of exactly the pieces these scenarios exercise (entry
  point `203`, envelope wrap, the adapted `WebMvcConfig`).
- `OrgLogoUploadTests extends AuthIntegrationTestBase`: its own
  `@DynamicPropertySource` injects `upload.home` (a run temp dir) + `upload.avatar`
  into a dedicated context (one extra cached context for the suite; every OTHER test
  class keeps the base context with NO `upload.home` — scenario 6's parity holds
  naturally there). Red-first per scenario: success (envelope `200` + `data.path`
  under `/profile/avatar/yyyy/MM/dd/` + identical bytes on disk + anonymous GET
  round-trip), the three `A0400` rejections (bad ext / oversize / empty) with
  nothing-written assertion, tokenless POST → `203` entry-point envelope.
- Parity default test (scenario 6, no context needed): a fresh `WebMvcConfig` instance
  has a blank `uploadHome` field (reflection) — the conditional guard is then
  code-by-construction; the integration serving test proves the non-blank path.
- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home &&
  ./mvnw -pl lumen-system -am test -q` from `lumen-sev/` (reactor rule, JDK 11;
  team test server required — same as every Auth* suite).
