import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MenuIcon } from '@/layouts/BasicLayout'

/**
 * The EIS department workstation glyphs (eis-workstation-icons tasks 2.1-2.2, user
 * ruling 2026-11-16): the three chip icon keys (`radiology` / `us` / `endo` - the
 * W nodes' seed path values, V36) resolve to the ported 1024-viewBox ops-icons
 * family (bare svg, monochrome currentColor - NOT AntD wrappers), and unknown or
 * null names still ride the RadarChartOutlined fallback.
 */
describe('MENU_ICON_BY_NAME department keys (eis-workstation-icons)', () => {
  it.each([
    ['radiology', '放射工作站'],
    ['us', '超声工作站'],
    ['endo', '内镜工作站'],
  ])('name %s renders the ported EIS glyph (1024 viewBox, no anticon wrapper)', (name) => {
    const { container } = render(<MenuIcon name={name} size={16} />)
    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
    expect(svg).toHaveAttribute('viewBox', '0 0 1024 1024')
    expect(container.querySelector('.anticon')).toBeNull()
  })

  it('unknown and null names ride the RadarChartOutlined fallback', () => {
    const fallback = render(<MenuIcon name={null} size={16} />)
    expect(fallback.container.querySelector('.anticon')).not.toBeNull()
    const unknown = render(<MenuIcon name="no-such-glyph" size={16} />)
    expect(unknown.container.querySelector('.anticon')).not.toBeNull()
  })
})
