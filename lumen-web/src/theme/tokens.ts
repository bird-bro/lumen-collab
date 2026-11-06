import type { ThemeConfig } from 'antd'

/**
 * The global-token map. `NonNullable` matters: `ThemeConfig['token']` is an
 * indexed access over an optional `ThemeConfig` field, so the bare annotation
 * would silently carry `| undefined` into every consumer (strict-mode tsc
 * flags each read). The exported map is always an object.
 */
type ThemeToken = NonNullable<ThemeConfig['token']>

/**
 * Design tokens for the single AntD 5 `ConfigProvider` (design D1).
 *
 * Palette is the **single blue-family system** settled by the user 2026-11-04
 * after prototype review: the layout shell uses the near-black tech blue
 * `#0B2540` (rail decision, hover `#12355C`, rail selection follows the action
 * primary) and the ACTION primary - buttons, links, information, text
 * selection, light-menu selection - is the bright blue `#3178fc` (the teal
 * `#0E7C86` action call was reverted by the user: discordant against the
 * near-black shell). Hover states derive from AntD's ramp. Red stays reserved
 * for clinical alarm semantics per `lumen-web/CLAUDE.md`.
 *
 * Light tints of the primary (`colorPrimaryBg` / `colorPrimaryBgHover` /
 * `colorPrimaryBorder`, the OPS `--primary-color-rgba1..3` equivalents) are
 * intentionally NOT hand-written: AntD derives the tint ramp from
 * `colorPrimary` automatically.
 *
 * This module is the ONLY place in `src/` where hex literals may appear
 * (`specs/web-design-system` § OPS Palette Tokens, hex discipline; enforced by
 * `src/test/hex-scan.test.ts`). Components read tokens via `ConfigProvider` /
 * `theme.useToken()` - never inline hex. The font stack names the bundled
 * MiSans family (`src/theme/fonts.css`) with system CJK fallbacks; Microsoft
 * YaHei appears only as a fallback name, the font itself is never bundled.
 */
export const themeTokens: ThemeToken = {
  colorPrimary: '#3178fc',
  colorInfo: '#3178fc',
  colorLink: '#3178fc',
  fontSize: 18,
  fontFamily: "'MiSans', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  colorText: '#333',
  colorTextSecondary: '#666',
  colorTextTertiary: '#999',
  colorBorder: '#d7dbe2',
  colorBgLayout: '#f3f5f7',
}

/**
 * Component-token mapping (design D1): navigation blues for the shell, dark
 * menu selection/hover blues, table chrome neutrals. Consumed together with
 * `themeTokens` via the exported `themeConfig`.
 */
const themeComponents: ThemeConfig['components'] = {
  Layout: {
    siderBg: '#0B2540',
    headerBg: '#ffffff',
  },
  Menu: {
    darkItemBg: '#0B2540',
    darkItemSelectedBg: '#3178fc',
    darkItemHoverBg: '#12355C',
  },
  Tree: {
    // Selected org rows need a clearly visible light-blue fill (user follow-up:
    // the default primary tint is too subtle against the white panel).
    nodeSelectedBg: '#cfe4ff',
    nodeHoverBg: '#e9f2ff',
  },
  Table: {
    // Header: the follow-up system's Element-UI el-table chrome (user ruling
    // 2026-11-05: every list header mirrors the el-table thead, background
    // first). theme-chalk defaults: gray-blue fill #f5f7fa, secondary-gray
    // text #909399, vertical splits off (el-table separates by the bottom
    // hairline only). Supersedes the earlier same-day white-header call -
    // that call rejected the OPS #f0f3fa tint, not a neutral gray fill.
    headerBg: '#f5f7fa',
    headerColor: '#909399',
    headerSplitColor: 'transparent',
    rowHoverBg: '#f6f6f6',
  },
}

/** Full AntD theme config handed to the app's single `ConfigProvider`. */
export const themeConfig: ThemeConfig = {
  token: themeTokens,
  components: themeComponents,
}
