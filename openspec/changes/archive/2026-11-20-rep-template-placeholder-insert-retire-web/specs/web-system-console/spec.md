# Spec Delta: web-system-console

## Purpose

Retire the 插入占位 affordances outright (user ruling 2026-11-20 follow-up — the
card's and both form fields' buttons are gone): the 【左|右】 token types by hand
and the 预览 modal already teaches the shape. The 影像所见 textarea grows to six
rows.

## MODIFIED Requirements

### Requirement: Template Placeholder Convention and Live Preview

The 报告模板 editing surface SHALL keep storage PLAIN TEXT and own all
report-shape treatment at the render layer. The 【左|右】 delimited token
(doctor-typed; | separates quick-pick candidates; the whole bracket is
replaced with the actual finding at report time; an unpaired 【 degrades to
literal text) SHALL highlight with the warning-class amber tint (`.rep-ph`)
in the 报告预览 modal and in the right table's prose 影像学表现/影像意见
cells. The 报告预览 SHALL be an ON-DEMAND modal — the template drawer's
footer carries a 预览 button summoning it (the always-on bottom block is
retired, ux-polish 2026-11-20); the modal renders the current draft — one
paragraph per line with a 2em first-line indent (the print shape) and, in
分节模式, the B-form mini measure table per section (名称 / 单位 / 测值 (always
— on the template) / 参考范围 — 单侧开区间 `19 ~` / `~ 40`, both ends empty
renders —). The 插入占位 affordances SHALL be retired outright
(rep-template-placeholder-insert-retire-web 2026-11-20: the card's and both
prose fields' buttons are gone — the token types by hand). The 影像所见
textarea renders SIX rows. Save SHALL normalize leading whitespace per
line on 影像学表现/影像意见/影像所见 (and the 简单→分节 conversion) — the
indent belongs to the renderer, never the stored text.

#### Scenario: Live preview renders the print shape

- **WHEN** the drawer's 表现 carries two lines including a 【左|右】 token and
  the user clicks the footer 预览 button
- **THEN** the 报告预览 modal opens showing the token highlighted amber and each
  line as a paragraph with a 2em first-line indent
- **AND** in 分节模式 each section renders its 节名, the mini measure table and
  its 影像所见 paragraphs (reference range `19 ~ 40` for a two-sided band)

#### Scenario: Placeholder tokens highlight in the right table

- **WHEN** the template table renders a prose row whose 表现/意见 carries a
  token
- **THEN** the token renders highlighted inline (`.rep-ph`) within the cell

#### ~~Scenario: 插入占位 splices at the caret~~

*Deprecated 2026-11-20 — superseded by the retirement above
(rep-template-placeholder-insert-retire-web: all 插入占位 affordances are gone —
the 【左|右】 token types by hand). Body preserved for history.*

- **WHEN** the user clicks 插入占位 on an empty 表现 textarea and keeps typing
- **THEN** the token lands first and the typing continues right after it (the
  caret re-anchors after the token on the next frame)

#### Scenario: Save normalizes leading whitespace per line

- **WHEN** a save carries a line starting with full-width spaces
- **THEN** the stored prose drops them (the 2em indent is render-layer CSS)
