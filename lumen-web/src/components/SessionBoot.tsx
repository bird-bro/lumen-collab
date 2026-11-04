import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { me } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * Boot session refresh (batch 5b of web-foundation-auth-console, task 5.3;
 * design D6): when the shell mounts with a stored token, `/me` refreshes the
 * profile/menus/perms (a partial `setSession` - the token stays). A stale
 * token makes `/me` answer the "203" envelope; the api client's wired
 * `onSessionExpired` clears the store and the `RequireAuth` guard redirects
 * to /login. Any other failure keeps the cached session (offline tolerance) -
 * the next API call will surface it.
 *
 * Rendered inside the shell route element (after `RequireAuth`), so it mounts
 * only with a live session. It renders nothing itself.
 */
export function SessionBoot({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token)
  const setSession = useAuthStore((state) => state.setSession)

  useEffect(() => {
    if (!token) return undefined
    let cancelled = false
    me()
      .then((data) => {
        if (!cancelled) {
          setSession({ profile: data.profile, menus: data.menus, perms: data.perms })
        }
      })
      .catch(() => {
        // "203" was already handled by the client (store cleared -> guard
        // redirects). Other errors keep the cached session; recorded here.
      })
    return () => {
      cancelled = true
    }
  }, [token, setSession])

  return <>{children}</>
}
