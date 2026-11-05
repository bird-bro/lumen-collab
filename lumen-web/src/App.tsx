import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/router'
import { themeConfig } from '@/theme'

/**
 * App root (tasks 5.1-5.3; batch 2 of web-foundation-auth-console): a single
 * `ConfigProvider` supplies the OPS design tokens to the whole tree and the
 * data router from `src/router.tsx` renders the shell layout with
 * lazily-loaded department routes.
 *
 * Wiring note (batch 2): the provider now consumes the full `themeConfig`
 * (global `themeTokens` + Layout/Menu/Table component tokens) instead of the
 * token slice alone, so the shell chrome (sider/header/menu backgrounds)
 * actually resolves from the token pipeline - see `src/theme/tokens.ts`.
 */
function App() {
  return (
    <ConfigProvider theme={themeConfig} locale={zhCN}>
      <RouterProvider router={router} />
    </ConfigProvider>
  )
}

export default App
