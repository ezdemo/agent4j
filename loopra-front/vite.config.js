import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import {AntDesignVueResolver} from 'unplugin-vue-components/resolvers'
import {resolve} from 'path'

export default defineConfig({
  base: './',
  plugins: [
    vue({
      script: {
        defineModel: true,
        propsDestructure: true
      }
    }),
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false // 不导入样式，使用主题
        })
      ],
      dts: false // 不生成类型声明文件
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
    open: false,
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
    outDir: 'dist/renderer',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'esbuild',
    rollupOptions: {
      output: {
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        manualChunks(id) {
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router') || id.includes('node_modules/pinia')) {
            return 'vue';
          }
          if (id.includes('node_modules/axios')) {
            return 'vendor';
          }
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
