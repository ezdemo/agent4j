import {defineConfig} from 'vite'
import {builtinModules} from 'node:module'

// Vite 配置 — 预加载脚本
export default defineConfig({
  build: {
    lib: {
      entry: {
        preload: 'electron/preload.cjs',
        'element-preload': 'electron/element-preload.cjs'
      },
      fileName: (format, entryName) => `${entryName}.cjs`,
      formats: ['cjs'],
    },
    rollupOptions: {
      external: ['electron', ...builtinModules],
    },
    minify: false,
    sourcemap: true,
  },
})
