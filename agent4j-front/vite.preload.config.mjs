import {defineConfig} from 'vite'

// Vite 配置 — 预加载脚本
export default defineConfig({
  build: {
    lib: {
      entry: 'electron/preload.cjs',
      fileName: () => '[name].cjs',
      formats: ['cjs'],
    },
    rollupOptions: {
      external: ['electron'],
    },
    minify: false,
    sourcemap: true,
  },
})
