import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '@/stores/auth'

/**
 * Session guard (batch 5b of web-foundation-auth-console, task 5.3; design
 * D6, spec `specs/web-auth-flow` § Session Guard): every route except /login
 * requires a live session. The guard reads ONLY the auth store's token - it
 * issues no request of its own, so an unauthenticated visitor is redirected
 * to the login page with zero API calls. The "203" transport-auth semantic
 * lands here indirectly: the api client's `onSessionExpired` clears the
 * store, this component re-renders, and the redirect happens.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}
