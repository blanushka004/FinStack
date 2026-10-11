import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  if (process.env.VERCEL === '1') {
    const apiBaseUrl = loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL
    let valid = false
    try {
      const url = new URL(apiBaseUrl)
      valid = url.protocol === 'https:' && url.origin === apiBaseUrl
    } catch { /* A missing or malformed URL is rejected below. */ }
    if (!valid) throw new Error('Set VITE_API_BASE_URL to the HTTPS Render API origin in Vercel project settings')
  }
  return {
    plugins: [react(), tailwindcss()],
    server: { proxy: { '/api': 'http://localhost:8081', '/actuator': 'http://localhost:8081' } },
  }
})
