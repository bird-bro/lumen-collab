import { render } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { BasicLayout } from '@/layouts/BasicLayout'
import { themeConfig } from '@/theme'

/**
 * OPS shell metrics (batch 2 of web-foundation-auth-console, spec
 * `specs/web-design-system` § OPS Layout Metrics): the app shell renders a
 * 60px header and a 160px navigation sidebar. The shell renders wrapped in
 * the real `themeConfig` so the assertions cover the wiring a page actually
 * runs under, and inside a `MemoryRouter` because the layout reads the
 * location and renders the routed child through `<Outlet />`.
 */
function renderShell() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <MemoryRouter initialEntries={['/radiology']}>
        <BasicLayout />
      </MemoryRouter>
    </ConfigProvider>,
  )
}

describe('BasicLayout OPS shell metrics (web-foundation-auth-console 2.3)', () => {
  it('renders a 60px-tall header', () => {
    const { container } = renderShell()
    const header = container.querySelector<HTMLElement>('.ant-layout-header')
    expect(header).not.toBeNull()
    expect(header?.style.height).toBe('60px')
  })

  it('renders a 160px-wide navigation sider', () => {
    const { container } = renderShell()
    const sider = container.querySelector<HTMLElement>('.ant-layout-sider')
    expect(sider).not.toBeNull()
    expect(sider?.style.width).toBe('160px')
  })
})
