/// <reference types="vitest/config" />
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { mockDevServerPlugin } from 'vite-plugin-mock-dev-server'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  // Dev-only mock switch (design D3): mocks ON by default; VITE_USE_MOCK=false
  // disables them so /api/v1 falls through the proxy to the real unified entry
  // (nginx :80) and pages render REAL database data. Pages never branch on
  // this - the switch is dev-server-level only.
  plugins: [react(), ...(process.env.VITE_USE_MOCK === 'false' ? [] : [mockDevServerPlugin()])],
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
        target: process.env.VITE_API_ENTRY_URL ?? 'http://localhost:80',
        changeOrigin: true,
      },
    },
  },
})
