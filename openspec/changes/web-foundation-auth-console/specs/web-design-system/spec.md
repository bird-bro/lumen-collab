# Spec Delta

## Purpose

Defines the web SPA's visual language: the bundled MiSans font, the 18px-based type scale, the
OPS deep-blue palette, and the OPS layout metrics. Every page change inherits these rules; the
capability owns the token-only styling discipline so look-and-feel decisions have exactly one
home.

## ADDED Requirements

### Requirement: MiSans Font Bundling
The web application SHALL bundle the MiSans font family (Regular, Medium, Bold), copied from
the OPS reference project, as self-hosted assets loaded via font-face declarations, with a
system CJK fallback stack. Microsoft YaHei MUST NOT be bundled (licensing). The fonts MUST load
without any network request to a third-party host.

#### Scenario: Fonts load offline
- **WHEN** the app is served with no external network access
- **THEN** text renders in MiSans from the bundled assets
- **AND** no font request goes to a third-party CDN

#### Scenario: Bundled font set
- **WHEN** the font assets are inspected in the repository
- **THEN** MiSans Regular, Medium, and Bold are present under the app's asset directory
- **AND** no Microsoft YaHei font file is present

### Requirement: OPS Type Scale
The application's type scale SHALL follow the OPS reference: base font size 18px, small 16px,
medium 20px. The AntD theme's base font size SHALL resolve to the 18px base, and text styled
outside AntD components SHALL use the same scale (token-derived, not ad-hoc px).

#### Scenario: Base size applied
- **WHEN** the application shell renders
- **THEN** default text renders at 18px
- **AND** AntD components derive their sizes from the same scale rather than the 14px default

### Requirement: OPS Palette Tokens
The design tokens SHALL adopt the OPS deep-blue palette: primary `#3178fc` (with its
transparency tints), deep-blue navigation family `#003676`/`#007bc2`, and the OPS neutrals for
text, borders, table headers, and striped rows. All color values SHALL live only in the theme
token module - ad-hoc hex values in components remain forbidden. The primary color MUST NOT be
red; red stays reserved for clinical alarm semantics.

#### Scenario: Primary color resolves to OPS blue
- **WHEN** the application shell renders
- **THEN** the AntD `ConfigProvider` theme resolves primary/information/link colors to the
  OPS `#3178fc` family
- **AND** the resolved primary color is not red

#### Scenario: Hex discipline holds
- **WHEN** a page component needs a color
- **THEN** it reads a theme token
- **AND** no inline hex literal appears outside the theme token module

### Requirement: OPS Layout Metrics
The application shell SHALL adopt the OPS layout metrics: 60px header height and a 160px
sidebar width, expressed through theme tokens or shared constants, not magic numbers scattered
in pages.

#### Scenario: Shell metrics
- **WHEN** the main layout renders
- **THEN** the header is 60px tall and the navigation sidebar is 160px wide
- **AND** the values trace to a single shared definition
