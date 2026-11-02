import path from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
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
