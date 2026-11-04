import { Suspense } from 'react'
import { Layout, Menu, Spin, theme } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

/**
 * OPS shell metrics (`specs/web-design-system` § OPS Layout Metrics): the
 * header height and sidebar width trace to this single shared definition -
 * never scatter magic numbers across pages.
 */
export const HEADER_HEIGHT = 60
export const SIDER_WIDTH = 160

/**
 * Top-level navigation entries. Each key is the route path of a department
 * route module (`src/router.tsx`, design D7).
 */
const NAV_ITEMS: MenuProps['items'] = [
  { key: '/radiology', label: 'Radiology' },
  { key: '/ultrasound', label: 'Ultrasound' },
  { key: '/endoscopy', label: 'Endoscopy' },
]

/**
 * App shell (task 5.1; batch 2 of web-foundation-auth-console): navigation
 * Sider (brand block on top, OPS deep blue via the `siderBg` component token)
 * + OPS-metrics Header (60px tall, background via the `headerBg` component
 * token) + content region rendering the routed child through `<Outlet />`.
 * AntD 5 components are composed via props only - never edited
 * (`specs/web-app-shell` § Component Style); every color here comes from the
 * ConfigProvider theme tokens, never an inline hex (§ Design Token Theming).
 *
 * The header content is a placeholder: the user/logout region belongs to the
 * auth batch (task 5.4), so only the environment name renders for now, in a
 * token-derived muted color.
 *
 * The Suspense boundary wraps the whole content region so it covers every
 * lazily-loaded department route module (task 5.3).
 */
export function BasicLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider width={SIDER_WIDTH}>
        <div
          style={{
            color: token.colorTextLightSolid,
            fontSize: token.fontSizeLG,
            fontWeight: 600,
            padding: token.padding,
            textAlign: 'center',
          }}
        >
          LumenCollab
        </div>
        <Menu
          mode="inline"
          theme="dark"
          items={NAV_ITEMS}
          selectedKeys={[pathname]}
          onClick={({ key }) => {
            navigate(key)
          }}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header
          style={{
            height: HEADER_HEIGHT,
            lineHeight: `${HEADER_HEIGHT}px`,
            paddingInline: 24,
          }}
        >
          {/* Placeholder pending the auth batch (task 5.4): user/logout region. */}
          <span style={{ color: token.colorTextSecondary }}>{import.meta.env.MODE}</span>
        </Layout.Header>
        <Layout.Content style={{ padding: token.paddingLG }}>
          <Suspense
            fallback={
              <Spin
                size="large"
                style={{ display: 'block', margin: `${token.sizeXXL}px auto` }}
              />
            }
          >
            <Outlet />
          </Suspense>
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
