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
