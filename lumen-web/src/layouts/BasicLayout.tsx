import {
  ApartmentOutlined,
  LockOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  RadarChartOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SlidersOutlined,
} from '@ant-design/icons'
import { Button, ConfigProvider, Layout, Menu, Popover, Spin, theme } from 'antd'
import type { ThemeConfig } from 'antd'
import type { ComponentType, CSSProperties } from 'react'
import { Suspense, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { activeTopLevelNode, firstLeafRouteOf, resolveMenuRoute } from '@/routes/menu-paths'
import { ExamCatalogIcon, OrgBuildingIcon, ShieldCheckIcon, SystemToolIcon, TemplateBookIcon, TuneIcon, UserAccountIcon } from '@/components/ops-icons'
import headLogo from '@/assets/head.png'
import { useAuthStore } from '@/stores/auth'
import { WorkstationChip } from './WorkstationChip'
import { sidebarMenus } from '@/config/workstations'
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
 * - zone 2: a 60px white header - product title left with the workstation chip
 *   immediately to its right; the user area right is
 *   the OPS form: two-line text (name/org) LEFT + a 50px round avatar RIGHT,
 *   no caret; a click opens a Popover with the basic info and a plain
 *   退出登录 button (store.clear() + navigate /login).
 * - zone 3: a 160px white collapsible secondary sidebar rendering the ACTIVE
 *   L1's children as group titles (AntD Menu `type: 'group'`, 16px text +
 *   24px icon; the OPS glyphs run optically smaller than AntD's, so the group
 *   icon size is bumped 20 -> 24 per user ruling 2026-11-05) and flat
 *   text-only 40px leaf rows; selection = light-blue chip
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
  // V5/V8 seed names stay mapped for DBs not yet migrated (V11 re-points all
  // four level-2 groups).
  lock: LockOutlined,
  // V11 group icons: glyphs ported VERBATIM from the OPS iconfont, monochrome
  // via currentColor (user ruling 2026-11-05; the source symbol per name is
  // documented in ops-icons.tsx / V11__menu_group_icons.sql).
  org: OrgBuildingIcon,
  team: UserAccountIcon,
  config: SystemToolIcon,
  shield: ShieldCheckIcon,
  // V33/V34 the 设置 root family (menu-settings-root tasks 1.5/1.6, user rulings
  // 2026-11-13): the RCP reference glyphs for the same console domain (exam /
  // temp / tune per rcp-test dict_menu mid 1029/1025/1018), ported in
  // ops-icons.tsx. `tune` is the semantic key for RCP's `setting` symbol - the
  // literal name is taken by the 系统 root's AntD gear above.
  exam: ExamCatalogIcon,
  temp: TemplateBookIcon,
  tune: TuneIcon,
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

export function MenuIcon({ name, size }: { name: string | null; size: number }) {
  const Icon = (name && MENU_ICON_BY_NAME[name]) || RAIL_FALLBACK_ICON
  return <Icon style={{ fontSize: size }} />
}

export function BasicLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()
  const menus = useAuthStore((state) => state.menus)
  const profile = useAuthStore((state) => state.profile)
  const activeWorkstation = useAuthStore((state) => state.activeWorkstation)
  const [folded, setFolded] = useState(false)
  // The currently open mini-rail flyout (null when none): while browsing
  // another section, the route section's highlight yields (user 2026-11-05:
  // two L2 buttons lit at once read as a bug).
  const [openSectionKey, setOpenSectionKey] = useState<string | null>(null)

  const sidebarTree = useMemo(() => sidebarMenus(menus, activeWorkstation), [menus, activeWorkstation])
  const activeL1 = useMemo(() => activeTopLevelNode(sidebarTree, pathname), [sidebarTree, pathname])

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
        <span style={{ fontSize: 18, fontWeight: 600, color: token.colorText }}>
          {profile?.name ?? '未登录'}
        </span>
        <span style={{ fontSize: 15, color: token.colorTextTertiary }}>{SEED_ORG_LABEL}</span>
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
    items: Array<{ key: string; label: string }>
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
          // Console-table current-row chrome (OPS dict-page highlight-current-row).
          '--lumen-row-current-bar': token.colorPrimary,
          '--lumen-row-current-bg': token.colorBgContainer,
          // Console-table zebra + row ruling (OPS parity, user review
          // 2026-11-09): the line var is the raw primary - basic-layout.css
          // color-mixes it to the 15% alpha of OPS --table-border so it
          // composites over both the plain and striped fills; the stripe
          // follows colorFillTertiary (AntD's alpha fill reads like the OPS
          // light stripe and auto-adapts in dark - no dark override needed).
          '--lumen-row-line': token.colorPrimary,
          '--lumen-row-striped': token.colorFillTertiary,
          // Mini-rail highlight color: previously only the expanded Menu
          // defined this var, so the collapsed rail's active color resolved
          // to inherited near-black (user-reported color mismatch).
          '--lumen-menu-active-bar': token.colorPrimary,
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
            style={{ width: 50, height: 50, borderRadius: 12, display: 'block' }}
          />
        </div>
        {sidebarTree.map((item) => {
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
          <WorkstationChip />
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
                      <MenuIcon name={section.icon} size={24} />
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
              <div className="lumen-side-mini">
                {sidebarSections.map((section) => {
                  const firstLabel = section.items[0]?.label ?? ''
                  const routeActive = section.items.some((entry) => entry.key === pathname)
                  const active = routeActive && (openSectionKey === null || openSectionKey === section.key)
                  return (
                    <Popover
                      key={section.key}
                      trigger="click"
                      placement="rightTop"
                      onOpenChange={(open) => setOpenSectionKey(open ? section.key : null)}
                      overlayInnerStyle={{
                        padding: '10px 8px',
                        borderRadius: 10,
                        border: `1px solid ${token.colorBorder}`,
                        boxShadow: '0 6px 24px rgba(0, 0, 0, 0.12)',
                      }}
                      content={
                        <div
                          className="lumen-side-mini-pop"
                          style={
                            {
                              '--lumen-pop-border': token.colorBorder,
                              '--lumen-menu-active-bar': token.colorPrimary,
                              '--lumen-title-color': token.colorText,
                              '--lumen-fold-color': token.colorTextTertiary,
                            } as CSSProperties
                          }
                        >
                          {section.title ? (
                            <div className="lumen-side-mini-pop-title">
                              {section.icon ? <MenuIcon name={section.icon} size={20} /> : null}
                              <span>{section.title}</span>
                            </div>
                          ) : null}
                          {section.items.map((entry) => (
                            <button
                              key={entry.key}
                              type="button"
                              className={
                                entry.key === pathname
                                  ? 'lumen-side-mini-item lumen-side-mini-item-active'
                                  : 'lumen-side-mini-item'
                              }
                              onClick={() => {
                                // Stay folded after jumping (user request
                                // 2026-11-05): the mini rail remains for the
                                // next section switch.
                                if (entry.key.startsWith('/')) navigate(entry.key)
                              }}
                            >
                              {String(entry.label)}
                            </button>
                          ))}
                        </div>
                      }
                    >
                      <button
                        type="button"
                        className={
                          active
                            ? 'lumen-side-mini-btn lumen-side-mini-btn-active'
                            : 'lumen-side-mini-btn'
                        }
                        title={section.title ?? firstLabel}
                      >
                        {section.icon ? (
                          <MenuIcon name={section.icon} size={26} />
                        ) : (
                          <span className="lumen-side-mini-char">{firstLabel.slice(0, 1) || '·'}</span>
                        )}
                      </button>
                    </Popover>
                  )
                })}
                <button
                  type="button"
                  className="lumen-side-expand"
                  onClick={() => setFolded(false)}
                  title="展开侧栏"
                >
                  <MenuUnfoldOutlined style={{ fontSize: 24 }} />
                </button>
              </div>
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
          {/* Gray page tint (the theme pins colorBgLayout to the OPS page
              tint): the query rows and white table cards float on non-white. */}
          <Layout.Content
            style={{ padding: CONTENT_PADDING, minWidth: 0, background: token.colorBgLayout }}
          >
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
