# Spec Delta: web-system-console

## Purpose

Land the placeholder convention + live report preview on the 报告模板 editing
surface (user ruling 2026-11-20): storage stays PLAIN TEXT (the 富文本否决
stands) — paragraphs, the 2em first-line indent and the amber placeholder
highlight are render-layer treatment over the stored strings.

## ADDED Requirements

### Requirement: Template Placeholder Convention and Live Preview

The 报告模板 editing surface SHALL keep storage PLAIN TEXT and own all
report-shape treatment at the render layer. The 【左|右】 delimited token
(doctor-typed; | separates quick-pick candidates; the whole bracket is
replaced with the actual finding at report time; an unpaired 【 degrades to
literal text) SHALL highlight with the warning-class amber tint (`.rep-ph`)
in the drawer's live 报告预览 and in the right table's prose
影像学表现/影像学意见 cells. The preview SHALL render one paragraph per line
with a 2em first-line indent (the print shape) and, in 分节模式, the B-form
mini measure table per section (名称 / 单位 / 测值 (always — on the template) /
参考范围 — 单侧开区间 `19 ~` / `~ 40`, both ends empty renders —). 插入占位
SHALL splice the token at the textarea caret and re-anchor the caret after it
(form fields via the Form.Item extra slot, 节所见 via the card's header row —
never nested inside the <label>: a label-nested button becomes the label's
second associated control). Save SHALL normalize leading whitespace per line
on 影像学表现/影像学意见/节所见 (and the 简单→分节 conversion) — the indent
belongs to the renderer, never the stored text.

#### Scenario: Live preview renders the print shape

- **WHEN** the template drawer's 表现 carries two lines including a 【左|右】
  token
- **THEN** the 报告预览 shows the token highlighted amber and each line as a
  paragraph with a 2em first-line indent
- **AND** in 分节模式 each section renders its 节名, the mini measure table and
  its 节所见 paragraphs (reference range `19 ~ 40` for a two-sided band)

#### Scenario: Placeholder tokens highlight in the right table

- **WHEN** the template table renders a prose row whose 表现/意见 carries a
  token
- **THEN** the token renders highlighted inline (`.rep-ph`) within the cell

#### Scenario: 插入占位 splices at the caret

- **WHEN** the user clicks 插入占位 on an empty 表现 textarea and keeps typing
- **THEN** the token lands first and the typing continues right after it (the
  caret re-anchors after the token on the next frame)

#### Scenario: Save normalizes leading whitespace per line

- **WHEN** a save carries a line starting with full-width spaces
- **THEN** the stored prose drops them (the 2em indent is render-layer CSS)
