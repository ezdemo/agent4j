import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './utils/hljsTheme' // 高亮主题（在 main.css 前加载，避免闪烁）
import './assets/styles/main.css'
import { initConfig } from './services/api'

// 初始化应用
const initApp = async () => {
  // 优先从 public/config.json 加载运行时配置
  await initConfig()

  // 创建应用实例
  const app = createApp(App)

  // 添加 Pinia 状态管理
  const pinia = createPinia()
  app.use(pinia)

  // 添加路由
  app.use(router)

  // Pinia 初始化后，执行 store 的 initialize
  const { useAppStore } = await import('./stores/app')
  const store = useAppStore()
  store.initialize()

  // 全局错误处理
  app.config.errorHandler = (err, vm, info) => {
    console.error('全局错误:', err, info)
  }

  // 全局警告处理（开发环境）
  if (import.meta.env.DEV) {
    app.config.warnHandler = (msg, vm, trace) => {
      console.warn('Vue警告:', msg, trace)
    }
  }

  // 全局属性
  app.config.globalProperties.$appName = 'Agent4j'
  app.config.globalProperties.$version = '1.0.0'

  // 挂载应用
  app.mount('#app')

  // 移除加载动画
  const loader = document.getElementById('app-loader')
  if (loader) {
    setTimeout(() => {
      loader.style.opacity = '0'
      setTimeout(() => {
        loader.remove()
      }, 300)
    }, 500)
  }

  console.log('Agent4j v' + app.config.globalProperties.$version + ' 已启动')
}

// 等待DOM加载完成后初始化
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp)
} else {
  initApp()
}
