import * as themeExports from '@/theme'
import { themeTokens } from '@/theme'
import { describe, expect, it } from 'vitest'

/**
 * Token contract of `src/theme/tokens.ts` (batch 2 of
 * web-foundation-auth-console, design D1): the OPS deep-blue palette, the
 * 18px type scale, and the MiSans font stack. Global-token assertions read
 * the exported `themeTokens`; the component-token mapping (Layout / Menu /
 * Table) is probed through the exported `themeConfig` so a dropped mapping
 * fails here before a page ever renders the wrong chrome.
 */
describe('themeTokens - OPS global tokens (web-foundation-auth-console 2.2)', () => {
  it('resolves primary/information/link colors to the clinical teal #0E7C86', () => {
    expect(themeTokens.colorPrimary).toBe('#0E7C86')
    expect(themeTokens.colorInfo).toBe('#0E7C86')
    expect(themeTokens.colorLink).toBe('#0E7C86')
  })

  it('uses the OPS 18px base type scale', () => {
    expect(themeTokens.fontSize).toBe(18)
  })

  it('points the font stack at the bundled MiSans family', () => {
    // CSS font families with spaces are quoted, so the first family in the
    // stack is `'MiSans'` (leading quote) - normalize before the prefix check.
    const firstFamily = (themeTokens.fontFamily ?? '')
      .replace(/'/g, '')
      .trimStart()
    expect(firstFamily.startsWith('MiSans')).toBe(true)
  })

  it('adopts the OPS neutral text ramp #333 / #666 / #999', () => {
    expect(themeTokens.colorText).toBe('#333')
    expect(themeTokens.colorTextSecondary).toBe('#666')
    expect(themeTokens.colorTextTertiary).toBe('#999')
  })

  it('adopts the OPS form border and layout background neutrals', () => {
    expect(themeTokens.colorBorder).toBe('#d7dbe2')
    expect(themeTokens.colorBgLayout).toBe('#f3f5f7')
  })
})

describe('themeConfig - OPS component tokens (design D1 mapping)', () => {
  it('exposes a full ThemeConfig whose token is themeTokens', () => {
    expect(themeExports).toHaveProperty('themeConfig')
    expect(themeExports).toHaveProperty('themeConfig.token', themeTokens)
  })

  it('styles the layout shell with the OPS navigation blues', () => {
    expect(themeExports).toHaveProperty('themeConfig.components.Layout.siderBg', '#0B2540')
    expect(themeExports).toHaveProperty('themeConfig.components.Layout.headerBg', '#ffffff')
  })

  it('styles the dark menu with the OPS selected/hover blues', () => {
    expect(themeExports).toHaveProperty('themeConfig.components.Menu.darkItemBg', '#0B2540')
    expect(themeExports).toHaveProperty(
      'themeConfig.components.Menu.darkItemSelectedBg',
      '#0E7C86',
    )
    expect(themeExports).toHaveProperty('themeConfig.components.Menu.darkItemHoverBg', '#12355C')
  })

  it('styles the table chrome with the OPS header/row neutrals', () => {
    expect(themeExports).toHaveProperty('themeConfig.components.Table.headerBg', '#f0f3fa')
    expect(themeExports).toHaveProperty('themeConfig.components.Table.rowHoverBg', '#f6f6f6')
  })
})
