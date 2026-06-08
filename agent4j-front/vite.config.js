import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'path'

export default defineConfig({
  plugins: [
    vue({
      script: {
        defineModel: true,
        propsDestructure: true
      }
    })
  ],
  
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@components': resolve(__dirname, 'src/components'),
      '@views': resolve(__dirname, 'src/views'),
      '@assets': resolve(__dirname, 'src/assets'),
      '@stores': resolve(__dirname, 'src/stores'),
      '@utils': resolve(__dirname, 'src/utils'),
      '@services': resolve(__dirname, 'src/services')
    }
  },
  
  server: {
    port: 3000,
    host: '0.0.0.0',
    open: true,
      cors: true,
      // 开发环境下将 /api 请求代理到后端，避免跨域
      proxy: {
          '/api': {
              target: 'http://localhost:4567',
              changeOrigin: true
          }
      }
  },
  
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'esbuild',
    rollupOptions: {
      output: {
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          vendor: ['axios']
        }
      }
    },
    chunkSizeWarningLimit: 1000
  },
  
  css: {
    preprocessorOptions: {
      css: {
        charset: false
      }
    },
    devSourcemap: true
  },
  
  optimizeDeps: {
    include: ['vue', 'vue-router', 'pinia', 'axios'],
    exclude: []
  },
  
  define: {
    __VUE_OPTIONS_API__: true,
    __VUE_PROD_DEVTOOLS__: false,
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false
  },
  
  envPrefix: 'VUE_APP_',
  
  logLevel: 'info',
  
  clearScreen: true
})