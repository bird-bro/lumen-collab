import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { normalizeLines, renderTemplateText } from '@/utils/template-text'

/**
 * The template placeholder tokenizer (rep-temp-placeholder-preview-web,
 * ruling 2026-11-20: the 【左|右】 delimited token convention — storage stays
 * PLAIN TEXT, the highlight/paragraph treatment is render-layer only; the
 * 富文本否决 2026-11-19 stands). Balanced 【...】 spans wrap into `.rep-ph`
 * spans (styled by the caller with tokens); an unpaired 【 degrades to
 * literal text. normalizeLines strips leading half-width spaces, tabs and
 * full-width spaces PER LINE — the 2em indent belongs to the renderer, never
 * to the stored text (a doctor's habit-indent must not stack on top of it).
 */
describe('template-text placeholder tokenizer', () => {
  it('wraps balanced 【...】 tokens into .rep-ph spans and keeps prose verbatim', () => {
    const { container } = render(<div>{renderTemplateText('【左|右】侧肺野清晰。')}</div>)
    const ph = container.querySelector('.rep-ph')
    expect(ph).not.toBeNull()
    expect(ph?.textContent).toBe('【左|右】')
    expect(container.textContent).toBe('【左|右】侧肺野清晰。')
  })

  it('tokenizes multiple tokens and multi-candidate options verbatim', () => {
    const { container } = render(
      <div>{renderTemplateText('肝内胆管【轻度|中度|重度】扩张，【左|右】下叶斑片。')}</div>,
    )
    expect(container.querySelectorAll('.rep-ph')).toHaveLength(2)
    const phs = Array.from(container.querySelectorAll('.rep-ph'))
    expect(phs[0].textContent).toBe('【轻度|中度|重度】')
    expect(phs[1].textContent).toBe('【左|右】')
  })

  it('an unpaired 【 degrades to literal text (never throws, never swallows)', () => {
    const { container } = render(<div>{renderTemplateText('见【备注栏说明。')}</div>)
    expect(container.querySelector('.rep-ph')).toBeNull()
    expect(container.textContent).toBe('见【备注栏说明。')
  })

  it('an empty string renders nothing', () => {
    const { container } = render(<div>{renderTemplateText('')}</div>)
    expect(container.textContent).toBe('')
    expect(container.querySelector('.rep-ph')).toBeNull()
  })

  it('the caller-supplied highlight style rides the .rep-ph span', () => {
    const { container } = render(
      <div>{renderTemplateText('【左|右】', { background: 'rgb(255, 251, 230)' })}</div>,
    )
    const ph = container.querySelector('.rep-ph') as HTMLElement
    expect(ph.style.background).toBe('rgb(255, 251, 230)')
  })
})

describe('template-text line normalization', () => {
  it('strips leading half-width spaces, tabs and full-width spaces per line', () => {
    expect(normalizeLines('第一段。\n　　第二段。\n  第三段。\n\t第四段。')).toBe(
      '第一段。\n第二段。\n第三段。\n第四段。',
    )
  })

  it('keeps inner text and interior spacing verbatim (only LEADING whitespace goes)', () => {
    expect(normalizeLines('左房内径 34 mm')).toBe('左房内径 34 mm')
    expect(normalizeLines('a\n\nb')).toBe('a\n\nb')
  })

  it('empty string stays empty', () => {
    expect(normalizeLines('')).toBe('')
  })
})
