import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发期代理到本地后端；生产由反向代理承担
      // 可用 RP_FRONTEND_API_URL 指向验收中的源码服务，默认保持本地开发端口。
      '/api': {
        target: process.env.RP_FRONTEND_API_URL || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
