import { defineConfig, loadEnv } from 'vite'

// Local dev proxies the Collection API (REST + WebSocket) to a backend chosen
// at run time via VITE_BACKEND_URL — e.g. the session's temporary preview
// instance or production. In a real deploy this app is served *by* the Element
// itself (/app/static/collection/), so API calls are same-origin relative paths
// and no proxy is involved.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8080'
  return {
    base: './',
    server: {
      proxy: {
        '/collection': { target: backend, changeOrigin: true, ws: true },
      },
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
  }
})
