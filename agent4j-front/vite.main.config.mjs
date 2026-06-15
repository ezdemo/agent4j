import {defineConfig} from 'vite'

// Vite 配置 — 主进程
// 注意：不需要指定 build.lib.entry，插件会根据 forge.config.js 自动注入
// 也不需要指定 build.lib.formats，插件默认使用 cjs
export default defineConfig({
  build: {
    rollupOptions: {
      external: ['electron'],
    },
    minify: false,
    sourcemap: true,
  },
})
