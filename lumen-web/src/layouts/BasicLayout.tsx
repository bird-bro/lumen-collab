import {
  ApartmentOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  RadarChartOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SlidersOutlined,
} from '@ant-design/icons'
import { Button, ConfigProvider, Layout, Menu, Popover, Spin, theme } from 'antd'
import type { MenuProps, ThemeConfig } from 'antd'
import type { ComponentType, CSSProperties } from 'react'
import { Suspense, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { activeTopLevelNode, firstLeafRouteOf, resolveMenuRoute } from '@/routes/menu-paths'
import headLogo from '@/assets/head.png'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import './basic-layout.css'

/**
 * OPS three-zone shell (batch 5b of web-foundation-auth-console, task 5.4;
 * user-confirmed prototype `console-shell.html` rev.11/12):
 *
 * - zone 1: a 68px deep-blue main-nav RAIL (full height) - the L1 entries of
 *   the store menu tree as a 26px icon over a ~16px white label; clicking an
 *   entry activates it and navigates to its first leaf. The rail is custom
 *   buttons (AntD Menu has no icon-over-label mode); its hover/active colors
 *   are the Menu dark tokens from `src/theme/tokens.ts` injected as CSS
 *   variables (see `./basic-layout.css` - the CSS file itself holds no
 *   colors).
 * - zone 2: a 60px white header - product title left; the user area right is
 *   the OPS form: two-line text (name/org) LEFT + a 50px round avatar RIGHT,
 *   no caret; a click opens a Popover with the basic info and a plain
 *   退出登录 button (store.clear() + navigate /login).
 * - zone 3: a 160px white collapsible secondary sidebar rendering the ACTIVE
 *   L1's children as group titles (AntD Menu `type: 'group'`, 16px text +
 *   20px icon) and flat text-only 40px leaf rows; selection = light-blue chip
 *   + 3px primary left bar (token-injected variable). Fold control per OPS:
 *   a 24px Fold icon absolute at the sidebar bottom-right; collapsed = a
 *   40px strip with the Expand icon + vertical active-L1 text.
 * - content: the routed child through `<Outlet />` under the Suspense
 *   boundary, with the user-set 10px padding.
 *
 * The navigation data comes ONLY from the auth store's menu tree (`/me`) -
 * NAV_ITEMS is deleted (design D6). AntD 5 components are composed via props
 * only - never edited (`specs/web-app-shell` § Component Style).
 */

/** OPS shell metrics (`specs/web-design-system` § OPS Layout Metrics). */
export const RAIL_WIDTH = 68
export const HEADER_HEIGHT = 60
export const SIDER_WIDTH = 160
export const SIDER_COLLAPSED_WIDTH = 40
export const CONTENT_PADDING = 5

/**
 * org label: the profile DTO carries no org field yet, so the seeded hospital
 * root name stands in (the mock's only org row, `示例医院`); replace with
 * `profile.org` once the backend adds it to /me (recorded batch-report item).
 */
const SEED_ORG_LABEL = '示例医院'

type IconComponent = ComponentType<{ style?: CSSProperties }>

/** Menu fixture `icon` column names -> AntD icon components (group titles + rail). */
const MENU_ICON_BY_NAME: Record<string, IconComponent> = {
  setting: SettingOutlined,
  // BankOutlined's glyph rides high in its viewBox (optically misaligned with
  // the title text - user feedback 2026-11-04); ApartmentOutlined is the
  // org-tree metaphor AND optically centered.
  bank: ApartmentOutlined,
  safety: SafetyCertificateOutlined,
  sliders: SlidersOutlined,
}

/**
 * Rail fallback for menu nodes without an icon (the three department L1s):
 * the prototype's department glyphs are circular/ray marks - RadarChartOutlined
 * is the closest AntD glyph (recorded batch-report icon note).
 */
const RAIL_FALLBACK_ICON: IconComponent = RadarChartOutlined

/**
 * Static component-token reads (values live in `src/theme/tokens.ts`; the
 * tokens test pins every key read here).
 */
const RAIL_HOVER_BG = themeConfig.components?.Menu?.darkItemHoverBg
const RAIL_ACTIVE_BG = themeConfig.components?.Menu?.darkItemSelectedBg
const SIDE_ITEM_HOVER_BG = themeConfig.components?.Table?.headerBg

/** Derived global tokens of the project config (computed once, module scope). */
const DESIGN_TOKEN = theme.getDesignToken(themeConfig)

/**
 * The light secondary-sidebar Menu theme (nested ConfigProvider): the
 * prototype's `--menu-text-color` gray maps to the token ramp
 * `colorTextSecondary`, and the prototype's light-blue selected chip to the
 * derived `colorPrimaryBg` tint of the action primary (both rows are recorded
 * in the batch report's prototype-vs-implementation table). AntD's inline
 * selected bar renders at the inline END, so it is zeroed here and the OPS
 * 3px LEFT bar comes from the token-injected CSS variable override.
 */
const SIDE_MENU_THEME: ThemeConfig = {
  components: {
    Menu: {
      // OPS .el-menu-item verbatim: text = var(--menu-text-color) which OPS
      // resolves to var(--text-color) (its dark neutral), base 18px, height
      // 40, padding 0 20px.
      itemColor: DESIGN_TOKEN.colorText,
      fontSize: 18,
      itemHoverColor: DESIGN_TOKEN.colorText,
      itemHoverBg: SIDE_ITEM_HOVER_BG,
      itemSelectedColor: DESIGN_TOKEN.colorPrimary,
      itemSelectedBg: DESIGN_TOKEN.colorPrimaryBg,
      itemHeight: 40,
      // Selected chip (user request 2026-11-04: LEFT inset, RIGHT flush -
      // a tab that bleeds into the content side): margin 8px 0, radius on the
      // left corners only. Leaf text aligns to the group-title text column:
      // 8 margin + 37 padding = 45px from the sidebar edge (icon 15-35).
      itemMarginInline: 8,
      itemMarginBlock: 0,
      itemPaddingInline: 37,
      itemBorderRadius: DESIGN_TOKEN.borderRadius,
      groupTitleColor: DESIGN_TOKEN.colorTextTertiary,
      groupTitleFontSize: 16,
      activeBarWidth: 0,
      activeBarBorderWidth: 0,
    },
  },
}

function MenuIcon({ name, size }: { name: string | null; size: number }) {
  const Icon = (name && MENU_ICON_BY_NAME[name]) || RAIL_FALLBACK_ICON
  return <Icon style={{ fontSize: size }} />
}

export function BasicLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()
  const menus = useAuthStore((state) => state.menus)
  const profile = useAuthStore((state) => state.profile)
  const [folded, setFolded] = useState(false)

  const activeL1 = useMemo(() => activeTopLevelNode(menus, pathname), [menus, pathname])

  const logout = () => {
    useAuthStore.getState().clear()
    navigate('/login', { replace: true })
  }

  const userPopContent = (
    <div style={{ width: 250, padding: 14 }}>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
          paddingBottom: 12,
          marginBottom: 12,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ fontSize: token.fontSizeSM, fontWeight: 500, color: token.colorText }}>
          {profile?.name ?? '未登录'}
        </span>
        <span style={{ fontSize: 12, color: token.colorTextTertiary }}>{SEED_ORG_LABEL}</span>
      </div>
      <Button block size="large" onClick={logout}>
        退出登录
      </Button>
    </div>
  )

  /**
   * The ACTIVE L1's children as SEPARATE sections (batch 6b polish, following
   * the Teamix reference structure the user supplied: a title ROW and the menu
   * body are SIBLING blocks, so the group icon/text alignment is plain flex
   * rather than AntD group-title padding interplay). L2 directories become
   * titled sections; leaf children render as a label-less single-item menu.
   */
  interface SidebarSection {
    key: string
    icon: string | null
    title: string | null
    items: NonNullable<MenuProps['items']>
  }
  const sidebarSections = useMemo<SidebarSection[]>(() => {
    const sections: SidebarSection[] = []
    for (const child of activeL1?.children ?? []) {
      if (child.type === 'F') continue
      const leaves = (child.children ?? [])
        .filter((leaf) => leaf.type !== 'F')
        .map((leaf) => {
          const route = resolveMenuRoute(leaf)
          return { key: route ?? `#node-${leaf.mid}`, label: leaf.title }
        })
      if (child.type === 'M') {
        if (leaves.length === 0) continue
        sections.push({ key: `group-${child.mid}`, icon: child.icon, title: child.title, items: leaves })
      } else {
        const route = resolveMenuRoute(child)
        if (route) sections.push({ key: `leaf-${child.mid}`, icon: null, title: null, items: [{ key: route, label: child.title }] })
      }
    }
    return sections
  }, [activeL1])

  return (
    <Layout
      style={
        {
          minHeight: '100vh',
          // CSS custom properties consumed by ./basic-layout.css - every value
          // comes from the token pipeline (the CSS file itself holds no colors).
          '--lumen-title-color': token.colorText,
          '--lumen-user-separator': token.colorBorder,
          '--lumen-user-name-color': token.colorText,
          '--lumen-user-org-color': token.colorTextTertiary,
          '--lumen-avatar-color': token.colorTextLightSolid,
          '--lumen-avatar-from': token.colorPrimary,
          '--lumen-avatar-to': token.colorPrimaryHover,
          '--lumen-group-color': token.colorTextTertiary,
          '--lumen-group-separator': token.colorBorder,
          '--lumen-fold-color': token.colorTextTertiary,
          '--lumen-fold-hover-bg': SIDE_ITEM_HOVER_BG,
          '--lumen-fold-hover-color': token.colorText,
        } as CSSProperties
      }
    >
      {/* zone 1: 68px main-nav rail, full height, deep blue */}
      <Layout.Sider
        className="lumen-rail"
        width={RAIL_WIDTH}
        style={
          {
            '--lumen-rail-hover': RAIL_HOVER_BG,
            '--lumen-rail-active': RAIL_ACTIVE_BG,
            '--lumen-rail-text': token.colorTextLightSolid,
          } as CSSProperties
        }
      >
        <div
          style={{
            height: HEADER_HEIGHT,
            width: '100%',
            display: 'grid',
            placeItems: 'center',
            flex: '0 0 auto',
          }}
          title="LumenCollab"
        >
          <img
            src={headLogo}
            alt="LumenCollab"
            style={{ width: 44, height: 44, borderRadius: 11, display: 'block' }}
          />
        </div>
        {menus.map((item) => {
          const target = firstLeafRouteOf(item)
          const active = activeL1?.mid === item.mid
          return (
            <button
              key={item.mid}
              type="button"
              className={active ? 'lumen-rail-item lumen-rail-item-active' : 'lumen-rail-item'}
              onClick={() => {
                if (target) navigate(target)
              }}
              title={item.title}
            >
              <MenuIcon name={item.icon} size={26} />
              <span className="lumen-rail-label">{item.title}</span>
            </button>
          )
        })}
      </Layout.Sider>

      <Layout>
        {/* zone 2: 60px white header */}
        <Layout.Header
          style={{
            height: HEADER_HEIGHT,
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorder}`,
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            padding: '0 20px',
            lineHeight: 'normal',
          }}
        >
          <span className="lumen-header-title">LumenCollab</span>
          <div style={{ marginLeft: 'auto' }}>
            <Popover trigger="click" placement="bottomRight" content={userPopContent}>
              <button type="button" className="lumen-header-user" title="用户区">
                <span className="lumen-header-user-info">
                  <span className="lumen-header-user-name">{profile?.name ?? '未登录'}</span>
                  <span className="lumen-header-user-org">{SEED_ORG_LABEL}</span>
                </span>
                <span className="lumen-header-avatar" style={{ width: 50, height: 50 }}>
                  {profile?.name ? profile.name.slice(-2) : '??'}
                </span>
              </button>
            </Popover>
          </div>
        </Layout.Header>

        {/* zone 3 + content */}
        <Layout hasSider>
          <Layout.Sider
            className="lumen-side"
            width={SIDER_WIDTH}
            collapsible
            collapsed={folded}
            collapsedWidth={SIDER_COLLAPSED_WIDTH}
            trigger={null}
            style={
              {
                background: token.colorBgContainer,
                borderRight: `1px solid ${token.colorBorderSecondary}`,
                paddingTop: 10,
                overflow: 'hidden',
                position: 'relative',
              } as CSSProperties
            }
          >
            {!folded &&
              sidebarSections.map((section) => (
                <div key={section.key} className="lumen-side-group">
                  {section.title ? (
                    <div className="lumen-side-group-label">
                      <MenuIcon name={section.icon} size={20} />
                      <span>{section.title}</span>
                    </div>
                  ) : null}
                  <ConfigProvider theme={SIDE_MENU_THEME}>
                    <Menu
                      className="lumen-side-menu"
                      mode="inline"
                      items={section.items}
                      selectedKeys={[pathname]}
                      onClick={({ key }) => {
                        if (key.startsWith('/')) navigate(key)
                      }}
                      style={
                        {
                          borderInlineEnd: 0,
                          background: 'transparent',
                          '--lumen-menu-active-bar': token.colorPrimary,
                        } as CSSProperties
                      }
                    />
                  </ConfigProvider>
                </div>
              ))}
            {folded ? (
              <button
                type="button"
                className="lumen-side-expand"
                onClick={() => setFolded(false)}
                title="展开侧栏"
              >
                <MenuUnfoldOutlined style={{ fontSize: 24 }} />
                <span className="lumen-side-expand-text">{activeL1?.title}</span>
              </button>
            ) : (
              <button
                type="button"
                className="lumen-side-fold"
                onClick={() => setFolded(true)}
                title="收起侧栏"
              >
                <MenuFoldOutlined style={{ fontSize: 24 }} />
              </button>
            )}
          </Layout.Sider>
          <Layout.Content style={{ padding: CONTENT_PADDING, minWidth: 0 }}>
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
    </Layout>
  )
}
