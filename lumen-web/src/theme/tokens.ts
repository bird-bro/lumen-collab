import type { ThemeConfig } from 'antd'

/**
 * Design tokens for the single AntD 5 `ConfigProvider` (design D6).
 *
 * Primary is **teal `#0E7C86`**, not red: on the HSL wheel its hue is ~185 deg
 * (cyan/teal band), while red-family hues sit at 0/360 deg. Red is reserved for
 * clinical alarm semantics per `lumen-web/CLAUDE.md` ("avoid red as a primary
 * color - keeps alarm semantics for clinical warnings") and
 * `specs/web-app-shell` § Design Token Theming.
 *
 * `colorInfo` and `colorLink` follow the same hue so informational UI and
 * links stay in the brand family instead of falling back to AntD's blue.
 *
 * This module is the ONLY place in `src/` where hex literals may appear
 * (`specs/web-app-shell` § Design Token Theming: ad-hoc hex MUST NOT replace
 * tokens); components read tokens via `ConfigProvider` / `theme.useToken()`.
 */
export const themeTokens: ThemeConfig['token'] = {
  colorPrimary: '#0E7C86',
  colorInfo: '#0E7C86',
  colorLink: '#0E7C86',
}
