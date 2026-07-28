import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'


export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 8076,
    open: true,
    host: '0.0.0.0',
    // 代理 /api 到后端，使前后端同源，HttpOnly Cookie 可在 dev 环境正常工作
    proxy: {
      '/api': {
        target: 'http://localhost:8077',
        changeOrigin: true,
        // 后端路径无 /api 前缀，代理时需去掉
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  }
})
