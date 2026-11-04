/**
 * Design-token theming for AntD 5 `ConfigProvider` (design D1/D2).
 *
 * - `./fonts.css` self-hosts MiSans Regular/Medium/Bold (400/500/700) via
 *   `@font-face`; the `fontFamily` token in `./tokens` names the family with
 *   system CJK fallbacks. Microsoft YaHei is never bundled (licensing) - it
 *   appears only as a fallback name in the stack.
 * - Tokens live in `./tokens` - the only module in `src/` allowed to hold hex
 *   literals (hex discipline, guarded by `src/test/hex-scan.test.ts`). The
 *   primary is the OPS deep blue (user decision 2026-11-03, replacing the
 *   teal D6 baseline), never red: red is reserved for clinical alarm
 *   semantics (`lumen-web/CLAUDE.md`).
 */
import './fonts.css'

export { themeConfig, themeTokens } from './tokens';
