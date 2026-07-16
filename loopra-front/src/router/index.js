import {createRouter, createWebHashHistory} from 'vue-router'

// 路由配置
const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: 'Loopra - 智能AI代码助手',
      description: '智能AI代码助手，提供代码分析、生成、优化等功能'
    }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/Chat.vue'),
    meta: {
      title: 'Loopra - 对话',
      description: '与AI助手进行对话'
    }
  },
  {
    path: '/sessions',
    name: 'Sessions',
    component: () => import('../views/Sessions.vue'),
    meta: {
      title: 'Loopra - 会话管理',
      description: '管理您的对话会话'
    }
  },
  {
    path: '/tools',
    name: 'Tools',
    component: () => import('../views/Tools.vue'),
    meta: {
      title: 'Loopra - 工具箱',
      description: '查看可用的AI工具'
    }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue'),
    meta: {
      title: 'Loopra - 设置',
      description: '配置Loopra'
    }
  },
  {
    path: '/help',
    name: 'Help',
    component: () => import('../views/Help.vue'),
    meta: {
      title: 'Loopra - 帮助',
      description: '获取使用帮助'
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: {
      title: 'Loopra - 页面未找到',
      description: '请求的页面不存在'
    }
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 如果有保存的位置，恢复到该位置
    if (savedPosition) {
      return savedPosition
    }
    
    // 如果目标有锚点，滚动到锚点
    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth',
        top: 80
      }
    }
    
    // 默认滚动到顶部
    return {
      top: 0,
      behavior: 'smooth'
    }
  }
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 更新页面标题
  const title = to.meta.title || 'Loopra - 智能AI代码助手'
  document.title = title
  
  // 更新meta描述
  const description = to.meta.description || '智能AI代码助手，提供代码分析、生成、优化等功能'
  let metaDescription = document.querySelector('meta[name="description"]')
  if (metaDescription) {
    metaDescription.setAttribute('content', description)
  }
  
  // 添加页面加载动画
  const app = document.getElementById('app')
  if (app) {
    app.style.opacity = '0'
    app.style.transform = 'translateY(10px)'
    
    requestAnimationFrame(() => {
      app.style.transition = 'all 0.3s ease-out'
      app.style.opacity = '1'
      app.style.transform = 'translateY(0)'
    })
  }
  
  next()
})

// 全局后置钩子
router.afterEach((to, from) => {
  // 页面切换完成后的处理
  console.log(`导航: ${from.path} → ${to.path}`)
  
  // 可以在这里添加页面访问统计
  if (typeof gtag !== 'undefined') {
    gtag('config', 'GA_MEASUREMENT_ID', {
      page_path: to.path,
      page_title: to.meta.title
    })
  }
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
  
  // 如果是动态导入失败，可能是网络问题
  if (error.message.includes('Failed to fetch dynamically imported module')) {
    // 可以显示一个友好的错误页面
    console.warn('动态导入失败，请检查网络连接')
  }
})

export default router