import { useCallback } from 'react'
import { useAuthStore } from '@/stores/auth'

/**
 * perms-gating helper (batch 5b of web-foundation-auth-console, task 5.4;
 * spec `specs/web-auth-flow` § Dynamic Navigation from the Permission Set):
 * the session's `perms` strings gate button-level controls - an action the
 * user lacks `perms` for is hidden or disabled. Batch-6 console pages consume
 * `usePerm()` (reactive, re-renders when the permission set refreshes);
 * `hasPerm(code)` is the plain store-reading variant for non-component code
 * paths.
 */

/** Plain check against the current session's permission set (store read, no re-render). */
export function hasPerm(code: string): boolean {
  return useAuthStore.getState().perms.includes(code)
}

/**
 * Reactive hook form: returns a stable-per-render `hasPerm(code)` bound to
 * the session's permission set.
 */
export function usePerm(): (code: string) => boolean {
  const perms = useAuthStore((state) => state.perms)
  return useCallback((code: string) => perms.includes(code), [perms])
}
