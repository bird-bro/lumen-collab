---
description: Glob-scoped naming rules - loads for all stack code paths
globs: ["lumen-sev/**", "lumen-web/**", "docker/**"]
---
<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# Naming Conventions (glob-scoped)

Global naming rules (kebab-case file names, overloading policy, English-only) live in root `CLAUDE.md` § Conventions - the single source. Only add rules here that apply specifically to the globbed code paths.

## Java / Maven naming (backend - `lumen-sev/`)

User decisions 2026-10-29 (see `openspec/coordination/init-project-skeleton.md`):

- **groupId**: `org.birdbro` for every module (author namespace, OPS-aligned).
- **Dual package root by code origin**:
  - Modules copied from OPS keep name, artifactId, and package:
    `framework` → `org.birdbro.framework`, `common` → `org.birdbro.common`.
  - New lumen modules use `org.birdbro.lumen.<module>`:
    `org.birdbro.lumen.{system,patient,radiology,us,endo,integration,examcore}`,
    `org.birdbro.lumen.api.{patient,integration}`.
- **framework/common evolvability** (user decision 2026-11-03, supersedes the
  verbatim-only reading of 2026-10-29): the OPS-lineage shared platform layer MAY be
  adjusted/optimized. Evolution style is ADDITIVE-FIRST - new `framework`/`common` files
  over modified OPS lines, so future upstream syncs stay conflict-free; lumen additions
  keep the `org.birdbro.{framework,common}` namespaces. Every adaptation is recorded in
  the coordination doc's adaptation list (six as of 2026-11-03).
- **In-service layering packages** (fixed): `controller / service / mapper / entity / dto`.
  The application class lives in the service root package
  (e.g. `org.birdbro.lumen.system.SystemApplication`); services scan `org.birdbro`
  (OPS `LaunchApplication` habit) so shared beans from `framework`/`common` register.
- **Service package layout** (user decision 2026-11-06, service-package-tidy; precedent
  example swapped 2026-11-09 by role-list-api when the same-day MenuManageService-merge
  ruling executed): the
  `service/` top level holds BUSINESS services only - OPS-copied ones stay
  interface + `impl/` (never move, upstream sync); lumen-new ones are concrete classes
  (single impl, no ceremony - `MenuService` precedent; the former `MenuManageService`
  寄住 form is dissolved). Stateless collaborators
  with no HTTP surface of their own (tree assemblers `*TreeService`, dict label/code
  helpers `*Helper`) live in `service/support/`; `impl/` only holds implementations of a
  sibling top-level interface. Same-package javadoc `{@link}`s to support classes use the
  FQN. Domain ownership (role-list-api 2026-11-09): ALL `role_menu` binding writes
  (assign full-replace, role-delete cascade, menu-create admin auto-grant, menu-delete
  unbind) live in the RoleService family - menu-side services call them inside their own
  transactions, never host them. All service modules follow this.
- **Module artifact names**: copied modules keep OPS names (`framework`, `common`);
  new modules are `lumen-*`. Department services use each domain's most recognizable
  short name: `lumen-radiology` (radiology), `lumen-us` (ultrasound, DICOM modality US),
  `lumen-endo` (endoscopy, DICOM modality ENDO); future departments follow
  `lumen-<standard abbrev>` (e.g. `lumen-ecg`) - not strict modality-code uniformity.
  The product-category term **RIS stays in prose** (root `CLAUDE.md` Business Context:
  "v1 delivers a RIS"), never in module or package names (user decision 2026-10-29:
  department-name consistency beats system-acronym naming).
- **Hosted-domain packages** (user decision 2026-11-13, merge-setting-into-system): a
  domain merged INTO an existing service keeps its own package root
  `org.birdbro.lumen.<domain>` inside the hosting module's jar - exam-setting lives as
  `org.birdbro.lumen.setting` inside `lumen-system` (package FQNs, classes, tests
  unchanged; only the Maven host and the schema moved). The hosting service's
  `@SpringBootApplication(scanBasePackages = "org.birdbro")` +
  `@MapperScan(basePackages = "org.birdbro.**.mapper")` discover it unchanged. The
  application class stays at the SERVICE root package (`SystemApplication`) - the root
  package mirrors the SERVICE identity (module name, `spring.application.name`, nginx
  `/api/v1/<service>/**` prefix), never a single domain. A merged domain keeps its own
  URL prefix and perms-string family (`/api/v1/setting/**`, `setting:*`); its tables
  join the hosting service's ONE schema + Flyway ledger (V29). The domain's schema is
  re-extractable: re-home the package into a new module and re-point the schema.
- **Author attribution**: `@author birdbro` in EVERY class header - ported code's headers are
  normalized to birdbro (adaptation (f), 2026-10-30; original `@date` lines preserved);
  pom `<developer><id>birdbro</id>`; Apache-2.0 LICENSE & NOTICE `Copyright 2026 birdbro`;
  Docker images `LABEL org.opencontainers.image.authors="birdbro"`.
- **Library boundaries**: `framework`/`common`/`exam-core` are non-executable jars - no
  `@SpringBootApplication`, and `exam-core` carries no controllers (the HTTP surface belongs
  to each department service).

