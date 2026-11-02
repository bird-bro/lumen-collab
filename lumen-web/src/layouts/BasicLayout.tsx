import { Suspense } from 'react'
import { Layout, Menu, Spin, theme } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

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
 * App shell (task 5.1): navigation Sider + content region rendering the routed
 * child through `<Outlet />`. AntD 5 components are composed via props only -
 * never edited (`specs/web-app-shell` § Component Style); every color here
 * comes from the ConfigProvider theme token, never an inline hex (§ Design
 * Token Theming).
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
      <Layout.Sider>
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
