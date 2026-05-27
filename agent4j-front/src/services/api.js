import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 可以在这里添加认证令牌
    const token = localStorage.getItem('agent4j-token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('API Error:', error)
    
    // 处理特定错误状态码
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // 未授权，清除令牌并跳转到登录页
          localStorage.removeItem('agent4j-token')
          window.location.href = '/login'
          break
        case 403:
          console.error('权限不足')
          break
        case 404:
          console.error('资源不存在')
          break
        case 500:
          console.error('服务器内部错误')
          break
      }
    }
    
    return Promise.reject(error)
  }
)

// API方法
export const chatAPI = {
  // 发送消息
  sendMessage: (message, sessionId = null) => {
    return api.post('/chat', {
      message,
      sessionId
    })
  },
  
  // 获取会话历史
  getHistory: (sessionId) => {
    return api.get(`/chat/history/${sessionId}`)
  },
  
  // 创建新会话
  createSession: () => {
    return api.post('/chat/session')
  },
  
  // 获取会话列表
  getSessions: () => {
    return api.get('/chat/sessions')
  },
  
  // 删除会话
  deleteSession: (sessionId) => {
    return api.delete(`/chat/session/${sessionId}`)
  }
}

export const toolsAPI = {
  // 获取工具列表
  getTools: () => {
    return api.get('/tools')
  },
  
  // 执行工具
  executeTool: (toolName, params) => {
    return api.post('/tools/execute', {
      tool: toolName,
      params
    })
  },
  
  // 获取工具详情
  getToolDetails: (toolName) => {
    return api.get(`/tools/${toolName}`)
  }
}

export const settingsAPI = {
  // 获取设置
  getSettings: () => {
    return api.get('/settings')
  },
  
  // 更新设置
  updateSettings: (settings) => {
    return api.put('/settings', settings)
  },
  
  // 重置设置
  resetSettings: () => {
    return api.post('/settings/reset')
  }
}

export const systemAPI = {
  // 获取系统状态
  getStatus: () => {
    return api.get('/system/status')
  },
  
  // 获取系统信息
  getInfo: () => {
    return api.get('/system/info')
  },
  
  // 健康检查
  healthCheck: () => {
    return api.get('/system/health')
  }
}

export default api