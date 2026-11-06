/// <reference types="vitest/config" />
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Direct-service preview override (web-app-shell spec): VITE_API_ENTRY_URL retargets
  // the /api/v1 proxy. Vite does NOT inject .env files into process.env for this config
  // file itself, so load them explicitly - .env.local (gitignored) then works without a
  // shell export (the former process.env-only read silently kept the :80 default and
  // surfaced as proxy 500s when no nginx entry was listening).
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  return {
  // Backend-first (change frontend-backend-first, user ruling 2026-11-05):
  // no dev-server mock layer exists - /api/v1 always falls through the proxy
  // to the real unified entry (nginx :80) and pages render REAL backend data,
  // failing as-is (transport B0001) when the entry is unreachable. The former
  // VITE_USE_MOCK switch died with the mock plugin it gated.
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
  build: {
    rollupOptions: {
      output: {
        // Department route modules are lazy chunks whose source file is
        // `src/pages/<department>/index.tsx`, so Rollup would name them all
        // `index-*.js`. Derive the department folder name into the output path
        // so the build log visibly proves one separate chunk per department
        // (tasks 5.3/5.5 evidence). All other chunks keep the default layout.
        chunkFileNames: (chunk) => {
          const department = /src[/\\]pages[/\\]([^/\\]+)[/\\]/.exec(
            chunk.facadeModuleId ?? '',
          )?.[1]
          return department
            ? `assets/pages/${department}/[name]-[hash].js`
            : 'assets/[name]-[hash].js'
        },
      },
    },
  },
  server: {
    // Default dev-server port 1573 (user request 2026-11-04; vite bumps to
    // the next free port when 1573 is taken). The /api/v1 proxy below is
    // unaffected - it is port-agnostic on the dev-server side.
    port: 1573,
    proxy: {
      // Unified API entry (design D3): /api/v1 goes to the nginx entry on port 80
      // per openspec/coordination/init-project-skeleton.md's port table - there is
      // no Java gateway. Override the target with VITE_API_ENTRY_URL; no service
      // port is ever hard-coded here.
      '/api/v1': {
        target: env.VITE_API_ENTRY_URL ?? 'http://localhost:80',
        changeOrigin: true,
      },
      // Static upload read-back (org-logo-upload-web, spec `web-app-shell` § Unified
      // API Entry Boundary): /profile/** is the ONE sanctioned segment outside /api/v1
      // - the SAME unified-entry target serves stored upload paths (org logo tiles)
      // same-origin. VITE_API_ENTRY_URL retargets it identically.
      '/profile': {
        target: env.VITE_API_ENTRY_URL ?? 'http://localhost:80',
        changeOrigin: true,
      },
    },
  },
}
})
