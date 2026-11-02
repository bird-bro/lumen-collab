import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { BasicLayout } from '@/layouts'

/**
 * Route table (task 5.3, design D7) - standalone module per
 * `specs/web-app-shell` § Module Layout.
 *
 * Department route modules are lazily loaded so each department ships as its
 * own chunk in a single SPA (no micro-frontend, § Module Layout). The shell
 * layout in `src/layouts/BasicLayout.tsx` is the parent route element and owns
 * the Suspense boundary covering these children.
 */
const RadiologyPage = lazy(() => import('@/pages/radiology'))
const UltrasoundPage = lazy(() => import('@/pages/ultrasound'))
const EndoscopyPage = lazy(() => import('@/pages/endoscopy'))

export const router = createBrowserRouter([
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      // Land on the first department; the placeholder itself declares that the
      // feature is not built yet, so the redirect implies no capability.
      { index: true, element: <Navigate to="/radiology" replace /> },
      { path: 'radiology', element: <RadiologyPage /> },
      { path: 'ultrasound', element: <UltrasoundPage /> },
      { path: 'endoscopy', element: <EndoscopyPage /> },
    ],
  },
])
