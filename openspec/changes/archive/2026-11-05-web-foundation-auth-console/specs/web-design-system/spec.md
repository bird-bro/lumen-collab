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
The design tokens SHALL adopt the single blue-family palette settled by the user 2026-11-04
(revising both the 2026-11-03 all-OPS-blue call and the 2026-11-04 teal-action call): the
ACTION primary - buttons, links, information, text and light-menu selection - is the bright
blue `#3178fc` (with its tints), while the navigation shell uses the near-black tech blue
`#0B2540` (rail decision 2026-11-04, hover `#12355C`, rail selection follows the action
primary); plus the OPS neutrals for text, borders, table headers, and striped rows. All color values SHALL live only in the theme
token module - ad-hoc hex values in components remain forbidden. The primary color MUST NOT be
red; red stays reserved for clinical alarm semantics.

#### Scenario: Primary color resolves to OPS blue
- **WHEN** the application shell renders
- **THEN** the AntD `ConfigProvider` theme resolves primary/information/link colors to the
  bright blue `#3178fc` family
- **AND** the layout shell resolves to the near-black tech blue navigation family
- **AND** the resolved primary color is not red

#### Scenario: Hex discipline holds
- **WHEN** a page component needs a color
- **THEN** it reads a theme token
- **AND** no inline hex literal appears outside the theme token module

### Requirement: OPS Layout Metrics
The application shell SHALL adopt the OPS three-region layout (user feedback 2026-11-04,
read from the OPS layout sources): a 68px near-black tech blue icon main-nav rail
(user rail-color decision 2026-11-04: `#0B2540`) carrying the level-1
menus (icon + short label, logo on top), a 60px header spanning the remaining width (product
title left, user area right), and below the header a 160px white secondary sidebar
(collapsible to 40px) beside the content area. Sidebar navigation SHALL render as group
titles (level-2 directories) with flat page items beneath them (level-3) - a level-1 section
without sub-directories renders as a single flat item. Metrics, region colors, and the menu
display pattern SHALL be expressed through theme tokens or shared constants, not magic
numbers scattered in pages. List pages SHALL NOT render page-title header blocks; the page
identity is carried by the active sidebar item (OPS compact-list style).

#### Scenario: Shell regions
- **WHEN** the main layout renders
- **THEN** the icon rail is 68px wide and dark blue with 26px white icons and white
  labels (OPS rail sizes, user feedback 2026-11-04), the header is 60px tall, and the
  secondary sidebar is 160px wide (40px collapsed) and white
- **AND** the content area padding is 5px (user-specified 2026-11-04, revised from the
  earlier 10px call - tighter than the OPS equivalent)
- **AND** the values trace to a single shared definition

#### Scenario: Header composition
- **WHEN** the shell header renders for a logged-in user
- **THEN** the left side shows the product title (brand name only, per user request
  2026-11-04) and the right side shows the user area as the OPS `.right-info` verbatim:
  full header height, a left hairline separator, stacked username + org text with the
  avatar at the right edge, no caret, and NO hover background (user feedback 2026-11-04)
- **AND** activating the user area opens a popover holding the basic info and a plain
  logout button

#### Scenario: Menu hierarchy display
- **WHEN** the logged-in user's menu tree carries a level-1 section
- **THEN** the rail shows the section's icon entry, and the sidebar renders the section's
  children: leaf entries as flat items, sub-directories as group titles with their children
  as flat items beneath (user feedback 2026-11-04: the level-1 title never repeats in the
  sidebar)
- **AND** group titles carry icons while leaf page items render text-only, per the OPS
  sidebar (user feedback 2026-11-04)
- **AND** no accordion submenu nesting is introduced

#### Scenario: Compact list pages
- **WHEN** a console list page renders
- **THEN** the content area opens with the search/tool row and table directly
- **AND** no page-title header block is rendered
