import { lazy } from 'react'
import { Navigate, createBrowserRouter } from 'react-router-dom'
import type { RouteObject } from 'react-router-dom'
import { Result } from 'antd'
import { RequireAuth, SessionBoot } from '@/components'
import { BasicLayout } from '@/layouts'
import LoginPage from '@/pages/login'
import { firstGrantedRoute } from '@/routes/menu-paths'
import { useAuthStore } from '@/stores/auth'

/**
 * Route table (batch 5b of web-foundation-auth-console, tasks 5.2/5.3; design
 * D6/D7). Structure:
 *
 * - `/login` - the standalone login page, registered OUTSIDE the shell;
 * - `/` - the shell parent route, wrapped by `RequireAuth` (store-only guard)
 *   and `SessionBoot` (boot `/me` refresh; "203" clears the session and the
 *   guard redirects), whose index child redirects to the FIRST route the
 *   user's menu tree grants;
 * - `/system/**` - the lazily-loaded console routes (design D8; batch 6
 *   replaced the placeholder modules in place; audit-log-unify-web 2.3
 *   retired the `/system/logs/sign` sibling — the unified audit page
 *   covers both event families);
 * - the three department routes, lazily loaded, unchanged.
 *
 * Department route modules are lazily loaded so each department ships as its
 * own chunk in a single SPA (no micro-frontend, § Module Layout). The shell
 * layout in `src/layouts/BasicLayout.tsx` is the parent route element and owns
 * the Suspense boundary covering these children.
 */
const OrgsPage = lazy(() => import('@/pages/system/orgs'))
const DevicePage = lazy(() => import('@/pages/system/device'))
const RoomPage = lazy(() => import('@/pages/system/room'))
const DepsPage = lazy(() => import('@/pages/system/deps'))
const UsersPage = lazy(() => import('@/pages/system/users'))
const RolesPage = lazy(() => import('@/pages/system/roles'))
const MenusPage = lazy(() => import('@/pages/system/menus'))
const DictsPage = lazy(() => import('@/pages/system/dicts'))
const LogsPage = lazy(() => import('@/pages/system/logs'))
const RadiologyPage = lazy(() => import('@/pages/radiology'))
const UltrasoundPage = lazy(() => import('@/pages/ultrasound'))
const EndoscopyPage = lazy(() => import('@/pages/endoscopy'))

/**
 * The `/` index child: redirect to the first menu route the session's tree
 * grants (design D6 - the admin fixture lands on /system/orgs). An account
 * with no navigable menu gets an explicit no-menu state instead of a redirect
 * loop or a blank region.
 */
function IndexRedirect() {
  const menus = useAuthStore((state) => state.menus)
  const target = firstGrantedRoute(menus)
  if (target) return <Navigate to={target} replace />
  return (
    <Result
      status="info"
      title="No granted menu for this account"
      subTitle="The session carries no navigable menu; contact an administrator."
    />
  )
}

/** The route table, shared by the app's data router and the tests. */
export const appRoutes: RouteObject[] = [
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <SessionBoot>
          <BasicLayout />
        </SessionBoot>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <IndexRedirect /> },
      { path: 'system/orgs', element: <OrgsPage /> },
      { path: 'system/devices', element: <DevicePage /> },
      { path: 'system/rooms', element: <RoomPage /> },
      { path: 'system/deps', element: <DepsPage /> },
      { path: 'system/users', element: <UsersPage /> },
      { path: 'system/roles', element: <RolesPage /> },
      { path: 'system/menus', element: <MenusPage /> },
      { path: 'system/dicts', element: <DictsPage /> },
      { path: 'system/logs', element: <LogsPage /> },
      { path: 'radiology', element: <RadiologyPage /> },
      { path: 'ultrasound', element: <UltrasoundPage /> },
      { path: 'endoscopy', element: <EndoscopyPage /> },
    ],
  },
]

export const router = createBrowserRouter(appRoutes)
