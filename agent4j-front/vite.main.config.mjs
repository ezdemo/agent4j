import {defineConfig} from 'vite'

// Vite 配置 — 主进程
// 需要指定 entry + fileName(.cjs)，避免 "type": "module" 导致 ESM 报错
export default defineConfig({
  build: {
    lib: {
      entry: 'electron/main.cjs',
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
