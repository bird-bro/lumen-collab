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
  - Modules copied verbatim from OPS keep name, artifactId, and package:
    `framework` → `org.birdbro.framework`, `common` → `org.birdbro.common`.
  - New lumen modules use `org.birdbro.lumen.<module>`:
    `org.birdbro.lumen.{auth,patient,radiology,us,endo,integration,examcore}`,
    `org.birdbro.lumen.api.{patient,integration}`.
- **In-service layering packages** (fixed): `controller / service / mapper / entity / dto`.
  The application class lives in the service root package
  (e.g. `org.birdbro.lumen.auth.AuthApplication`); services scan `org.birdbro`
  (OPS `LaunchApplication` habit) so shared beans from `framework`/`common` register.
- **Module artifact names**: copied modules keep OPS names (`framework`, `common`);
  new modules are `lumen-*`. Department services use each domain's most recognizable
  short name: `lumen-radiology` (radiology), `lumen-us` (ultrasound, DICOM modality US),
  `lumen-endo` (endoscopy, DICOM modality ENDO); future departments follow
  `lumen-<standard abbrev>` (e.g. `lumen-ecg`) - not strict modality-code uniformity.
  The product-category term **RIS stays in prose** (root `CLAUDE.md` Business Context:
  "v1 delivers a RIS"), never in module or package names (user decision 2026-10-29:
  department-name consistency beats system-acronym naming).
- **Author attribution**: `@author birdbro` in EVERY class header - ported code's headers are
  normalized to birdbro (adaptation (f), 2026-10-30; original `@date` lines preserved);
  pom `<developer><id>birdbro</id>`; MIT LICENSE `Copyright (c) 2026 birdbro`;
  Docker images `LABEL org.opencontainers.image.authors="birdbro"`.
- **Library boundaries**: `framework`/`common`/`exam-core` are non-executable jars - no
  `@SpringBootApplication`, and `exam-core` carries no controllers (the HTTP surface belongs
  to each department service).

