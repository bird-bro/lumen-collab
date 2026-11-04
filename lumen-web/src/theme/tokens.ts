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
 * Palette is the **OPS deep-blue family** (user decision 2026-11-03: the OPS
 * reference palette replaces the former teal D6 baseline): primary blue for
 * brand/information/link colors, navigation blues for the layout shell, and
 * OPS neutrals for text, borders, and table chrome. Red stays reserved for
 * clinical alarm semantics per `lumen-web/CLAUDE.md` - the avoid-red principle
 * is preserved; only the hue family changed (blue instead of teal), and blue
 * is equally far from the red family.
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
    siderBg: '#0f356a',
    headerBg: '#ffffff',
  },
  Menu: {
    darkItemBg: '#0f356a',
    darkItemSelectedBg: '#007bc2',
    darkItemHoverBg: '#32aef3',
  },
  Table: {
    headerBg: '#f0f3fa',
    rowHoverBg: '#f6f6f6',
  },
}

/** Full AntD theme config handed to the app's single `ConfigProvider`. */
export const themeConfig: ThemeConfig = {
  token: themeTokens,
  components: themeComponents,
}
