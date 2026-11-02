import { ConfigProvider } from 'antd'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/router'
import { themeTokens } from '@/theme'

/**
 * App root (tasks 5.1-5.3): a single `ConfigProvider` supplies the design
 * tokens (teal primary - never red, see `src/theme/tokens.ts`) to the whole
 * tree, and the data router from `src/router.tsx` renders the shell layout
 * with lazily-loaded department routes.
 */
function App() {
  return (
    <ConfigProvider theme={{ token: themeTokens }}>
      <RouterProvider router={router} />
    </ConfigProvider>
  )
}

export default App
