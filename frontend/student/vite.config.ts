import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 15173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true
      },
      '/actuator': {
        target: 'http://127.0.0.1:18082',
        changeOrigin: true
      },
      '/media': {
        target: 'http://127.0.0.1:18082',
        changeOrigin: true
      }
    }
  }
})