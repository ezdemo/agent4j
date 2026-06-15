import {defineConfig} from 'vite'

// Vite 配置 — 预加载脚本
// 注意：不需要指定 build.lib.entry，插件会根据 forge.config.js 自动注入
export default defineConfig({
  build: {
    rollupOptions: {
      external: ['electron'],
    },
    minify: false,
    sourcemap: true,
  },
})
