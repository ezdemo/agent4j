import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'
import { useTheme } from './composables/useTheme'

// 初始化应用
const initApp = () => {
  // 主题由 useTheme 自动初始化
  useTheme()
  
  // 创建应用实例
  const app = createApp(App)
  
  // 添加Pinia状态管理
  const pinia = createPinia()
  app.use(pinia)
  
  // 注册 Ant Design Vue
  app.use(Antd)
  
  // 添加路由
  app.use(router)
  
  // 全局错误处理
  app.config.errorHandler = (err, vm, info) => {
    console.error('全局错误:', err, info)
    // 可以在这里添加错误上报逻辑
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
  
  console.log(`🚀 Agent4j v${app.config.globalProperties.$version} 已启动`)
}

// 等待DOM加载完成后初始化
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp)
} else {
  initApp()
}