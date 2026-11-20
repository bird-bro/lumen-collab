import type { CSSProperties, ReactNode } from 'react'

/**
 * Template placeholder text utilities (rep-temp-placeholder-preview-web,
 * ruling 2026-11-20). The 报告模板 storage stays PLAIN TEXT (富文本否决
 * 2026-11-19 stands) — everything here is render-layer treatment over the
 * stored strings:
 *
 * - 【左|右】 is the doctor-typed PLACEHOLDER token (user-picked delimiter,
 *   | separates quick-pick candidates; the whole bracket is replaced with
 *   the actual finding at report time). `renderTemplateText` wraps balanced
 *   tokens into `.rep-ph` spans for the amber highlight; an unpaired 【
 *   degrades to literal text (never throws, never swallows prose).
 * - `normalizeLines` strips LEADING whitespace per line (half/full-width
 *   spaces, tabs): the report's 2em first-line indent belongs to the
 *   renderer (`text-indent: 2em` per paragraph), never to the stored text —
 *   a doctor's habit-indent must not stack on top of it.
 */

/** The token the 插入占位 affordance inserts (candidates shown in the tooltip). */
export const PLACEHOLDER_TOKEN = '【左|右】'

const PLACEHOLDER_RE = /【[^】]*】/g

/**
 * Strip leading half-width spaces / tabs / full-width spaces from every line.
 * Interior spacing stays verbatim; blank lines pass through unchanged.
 */
export function normalizeLines(text: string): string {
  return text
    .split('\n')
    .map((line) => line.replace(/^[ \t　]+/, ''))
    .join('\n')
}

/**
 * Split `text` around balanced 【...】 tokens: prose rides verbatim, each
 * token renders as a `.rep-ph` span carrying the caller's token-derived
 * highlight style (the util stays token-free — components own the colors).
 * The className is the stable test/reader anchor; the style is the amber.
 */
export function renderTemplateText(text: string, highlightStyle?: CSSProperties): ReactNode[] {
  const parts: ReactNode[] = []
  const re = new RegExp(PLACEHOLDER_RE.source, 'g')
  let last = 0
  let match: RegExpExecArray | null
  while ((match = re.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index))
    parts.push(
      <span key={match.index} className="rep-ph" style={highlightStyle}>
        {match[0]}
      </span>,
    )
    last = match.index + match[0].length
  }
  if (last < text.length) parts.push(text.slice(last))
  return parts
}
