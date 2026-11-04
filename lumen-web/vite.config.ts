/// <reference types="vitest/config" />
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { mockDevServerPlugin } from 'vite-plugin-mock-dev-server'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), mockDevServerPlugin()],
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
