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
    // 添加认证令牌
    const token = localStorage.getItem('agent4j-token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // 添加请求ID用于追踪
    config.headers['X-Request-ID'] = generateRequestId()
    
    // 添加时间戳
    config.headers['X-Timestamp'] = Date.now().toString()
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    // 记录响应时间
    const requestTime = response.config.headers['X-Timestamp']
    if (requestTime) {
      const duration = Date.now() - parseInt(requestTime)
      console.debug(`API请求完成: ${response.config.url} (${duration}ms)`)
    }
    
    return response.data
  },
  (error) => {
    console.error('API Error:', error)
    
    // 处理特定错误状态码
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 401:
          // 未授权，清除令牌并跳转到登录页
          localStorage.removeItem('agent4j-token')
          window.location.href = '/login'
          break
          
        case 403:
          console.error('权限不足:', data?.message || '未知错误')
          break
          
        case 404:
          console.error('资源不存在:', error.config.url)
          break
          
        case 429:
          console.error('请求过于频繁，请稍后重试')
          break
          
        case 500:
          console.error('服务器内部错误:', data?.message || '未知错误')
          break
          
        case 502:
        case 503:
        case 504:
          console.error('服务暂时不可用，请稍后重试')
          break
      }
      
      // 返回结构化错误信息
      return Promise.reject({
        code: status,
        message: data?.message || error.message,
        data: data
      })
    }
    
    // 网络错误
    if (error.code === 'ECONNABORTED') {
      console.error('请求超时')
      return Promise.reject({
        code: 'TIMEOUT',
        message: '请求超时，请检查网络连接'
      })
    }
    
    if (!window.navigator.onLine) {
      console.error('网络连接已断开')
      return Promise.reject({
        code: 'OFFLINE',
        message: '网络连接已断开'
      })
    }
    
    return Promise.reject(error)
  }
)

// 生成请求ID
function generateRequestId() {
  return 'req_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now().toString(36)
}

// 聊天 API
export const chatAPI = {
  // 同步聊天 - POST /api/chat
  sendMessage: (message) => {
    return api.post('/chat', { message })
  },
  
  // SSE流式聊天 - POST /api/chat/stream
  sendMessageStream: (message, onMessage, onDone, onError) => {
    const abortController = new AbortController()

    ;(async () => {
      try {
        const res = await fetch('/api/chat/stream', {
          method: 'POST',
          headers: { 
            'Content-Type': 'application/json',
            'X-Request-ID': generateRequestId(),
            'X-Timestamp': Date.now().toString()
          },
          body: JSON.stringify({ message }),
          signal: abortController.signal
        })

        if (!res.ok) {
          const text = await res.text()
          if (onError) onError(new Error(`HTTP ${res.status}: ${text}`))
          return
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let currentEventType = null

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()

          for (const line of lines) {
            const trimmed = line.trim()

            if (trimmed === '') {
              currentEventType = null
              continue
            }
            if (trimmed.startsWith(':')) continue

            if (trimmed.startsWith('event:')) {
              currentEventType = trimmed.slice(6).trim()
            } else if (trimmed.startsWith('data:')) {
              const payload = trimmed.slice(5).trim()
              if (payload === '[DONE]') {
                if (onDone) onDone()
                return
              }

              try {
                const parsed = JSON.parse(payload)
                if (typeof parsed === 'string') {
                  if (onMessage) onMessage({ type: currentEventType, content: parsed })
                } else if (parsed && typeof parsed === 'object') {
                  if (onMessage) onMessage({ type: currentEventType, ...parsed })
                } else {
                  if (onMessage) onMessage({ type: currentEventType, content: payload })
                }
              } catch (e) {
                if (onMessage) onMessage({ type: currentEventType, content: payload })
              }
              currentEventType = null
            }
          }
        }
        if (onDone) onDone()
      } catch (err) {
        if (err.name !== 'AbortError' && onError) onError(err)
      }
    })()

    return { abort: () => abortController.abort() }
  }
}

// Agent API
export const agentAPI = {
  // 获取Agent状态 - GET /api/agent/status
  getStatus: () => {
    return api.get('/agent/status')
  },
  
  // 获取历史消息 - GET /api/agent/history
  getHistory: () => {
    return api.get('/agent/history')
  },
  
  // 执行命令 - POST /api/chat
  runCommand: (command) => {
    return api.post('/chat', { message: command })
  },
  
  // 获取Agent信息 - GET /api/agent/info
  getInfo: () => {
    return api.get('/agent/info')
  },
  
  // 获取Agent统计 - GET /api/agent/stats
  getStats: () => {
    return api.get('/agent/stats')
  }
}

// 会话 API
export const sessionsAPI = {
  // 列出所有会话 - GET /api/sessions?workspaceHash=xxx
  list: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/sessions', { params })
  },
  
  // 获取当前会话信息 - GET /api/sessions/current
  getCurrent: () => {
    return api.get('/sessions/current')
  },
  
  // 新建空白会话 - POST /api/sessions/new
  createNew: () => {
    return api.post('/sessions/new')
  },
  
  // 切换会话 - POST /api/sessions/{name}
  switchSession: (name) => {
    return api.post(`/sessions/${name}`)
  },
  
  // 删除会话 - DELETE /api/sessions/{name}
  deleteSession: (name) => {
    return api.delete(`/sessions/${name}`)
  },
  
  // 获取会话详情 - GET /api/sessions/{name}
  getDetails: (name) => {
    return api.get(`/sessions/${name}`)
  },
  
  // 重命名会话 - PUT /api/sessions/{name}
  renameSession: (name, newName) => {
    return api.put(`/sessions/${name}`, { name: newName })
  },
  
  // 导出会话 - GET /api/sessions/{name}/export
  exportSession: (name) => {
    return api.get(`/sessions/${name}/export`, { responseType: 'blob' })
  },
  
  // 导入会话 - POST /api/sessions/import
  importSession: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/sessions/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

// 工具 API
export const toolsAPI = {
  // 列出所有已注册工具 - GET /api/tools
  list: () => {
    return api.get('/tools')
  },
  
  // 获取工具详情 - GET /api/tools/{name}
  getDetails: (name) => {
    return api.get(`/tools/${name}`)
  },
  
  // 直接执行工具 - POST /api/tools/{name}/execute
  execute: (name, args) => {
    return api.post(`/tools/${name}/execute`, { arguments: args })
  },
  
  // 搜索工具 - GET /api/tools/search
  search: (query) => {
    return api.get('/tools/search', { params: { q: query } })
  },
  
  // 获取工具分类 - GET /api/tools/categories
  getCategories: () => {
    return api.get('/tools/categories')
  }
}

// 配置 API
export const configAPI = {
  // 获取当前配置 - GET /api/config
  getConfig: () => {
    return api.get('/config')
  },
  
  // 更新配置 - PUT /api/config
  updateConfig: (config) => {
    return api.put('/config', config)
  },
  
  // 获取Token用量统计 - GET /api/usage
  getUsage: () => {
    return api.get('/usage')
  },
  
  // 获取使用历史 - GET /api/usage/history
  getUsageHistory: (params) => {
    return api.get('/usage/history', { params })
  },
  
  // 重置统计 - POST /api/usage/reset
  resetUsage: () => {
    return api.post('/usage/reset')
  },
  
  // 获取当前工作目录 - GET /api/workspace
  getWorkspace: () => {
    return api.get('/workspace')
  },
  
  // 切换工作目录 - POST /api/workspace
  switchWorkspace: (path) => {
    return api.post('/workspace', { path })
  },
  
  // 获取所有工作区列表 - GET /api/workspaces
  listWorkspaces: () => {
    return api.get('/workspaces')
  },
  
  // 切换到指定工作区 - POST /api/workspaces/switch
  switchToWorkspace: (path) => {
    return api.post('/workspaces/switch', { path })
  },
  
  // 删除工作区 - DELETE /api/workspaces/{hash}
  deleteWorkspace: (hash) => {
    return api.delete(`/workspaces/${hash}`)
  }
}

// 记忆 API
export const memoryAPI = {
  // 列出所有记忆 - GET /api/memory
  list: () => {
    return api.get('/memory')
  },
  
  // 获取记忆详情 - GET /api/memory/{name}
  getDetails: (name) => {
    return api.get(`/memory/${name}`)
  },
  
  // 保存记忆 - POST /api/memory
  save: (memory) => {
    return api.post('/memory', memory)
  },
  
  // 删除记忆 - DELETE /api/memory/{name}
  delete: (name) => {
    return api.delete(`/memory/${name}`)
  },
  
  // 搜索记忆 - GET /api/memory/search
  search: (query) => {
    return api.get('/memory/search', { params: { q: query } })
  }
}

// 作业 API
export const jobAPI = {
  // 列出所有作业 - GET /api/jobs
  list: () => {
    return api.get('/jobs')
  },
  
  // 获取作业详情 - GET /api/jobs/{id}
  getDetails: (id) => {
    return api.get(`/jobs/${id}`)
  },
  
  // 获取作业输出 - GET /api/jobs/{id}/output
  getOutput: (id, params) => {
    return api.get(`/jobs/${id}/output`, { params })
  },
  
  // 停止作业 - POST /api/jobs/{id}/stop
  stop: (id) => {
    return api.post(`/jobs/${id}/stop`)
  }
}

// 系统 API
export const systemAPI = {
  // 获取系统状态
  getStatus: () => {
    return agentAPI.getStatus()
  },
  
  // 获取系统信息
  getInfo: () => {
    return configAPI.getConfig()
  },
  
  // 健康检查
  healthCheck: () => {
    return api.get('/health')
  },
  
  // 获取系统版本
  getVersion: () => {
    return api.get('/version')
  },
  
  // 获取系统日志
  getLogs: (params) => {
    return api.get('/logs', { params })
  }
}

// 工具函数
export const utils = {
  // 格式化错误消息
  formatError: (error) => {
    if (typeof error === 'string') return error
    if (error.message) return error.message
    if (error.data?.message) return error.data.message
    return '未知错误'
  },
  
  // 检查是否为网络错误
  isNetworkError: (error) => {
    return error.code === 'OFFLINE' || error.code === 'TIMEOUT' || !error.response
  },
  
  // 检查是否为认证错误
  isAuthError: (error) => {
    return error.code === 401
  },
  
  // 检查是否为权限错误
  isPermissionError: (error) => {
    return error.code === 403
  },
  
  // 重试请求
  retryRequest: async (requestFn, maxRetries = 3, delay = 1000) => {
    for (let i = 0; i < maxRetries; i++) {
      try {
        return await requestFn()
      } catch (error) {
        if (i === maxRetries - 1) throw error
        if (!utils.isNetworkError(error)) throw error
        
        console.warn(`请求失败，${delay}ms 后重试 (${i + 1}/${maxRetries})`)
        await new Promise(resolve => setTimeout(resolve, delay * (i + 1)))
      }
    }
  }
}

export default api