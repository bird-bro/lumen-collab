// Vitest global setup (task 1.1, batch 1 of web-foundation-auth-console).
// Registers jest-dom matchers (toBeInTheDocument, ...) on Vitest's expect.
import '@testing-library/jest-dom/vitest'

// jsdom implements no matchMedia; AntD's ResponsiveObserver (Grid/Row/Col, used
// transitively by Button etc.) calls window.matchMedia on mount. Stub a static
// matcher so component tests render (batch 5b).
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  })
}

// The auth store persists to localStorage; zustand's hydration is a microtask that
// can re-apply a PREVIOUS test file's session AFTER the current file seeds its own
// (observed as menu-tree ghosts during workstation-filter testing). Each file
// starts from a clean persisted session: clear the storage AND synchronously
// re-hydrate the store to it (async hydration would otherwise resurrect the stale
// session over the current file's setSession).
localStorage.clear();
import { useAuthStore } from '@/stores/auth';
useAuthStore.persist.rehydrate();
