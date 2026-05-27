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
          headers: { 'Content-Type': 'application/json' },
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
        let currentEventType = null // SSE event: 字段

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() // 保留未完成的行

          for (const line of lines) {
            const trimmed = line.trim()

            if (trimmed === '') {
              // 空行 = 事件分隔符，重置 eventType
              currentEventType = null
              continue
            }
            if (trimmed.startsWith(':')) continue // 注释行

            if (trimmed.startsWith('event:')) {
              currentEventType = trimmed.slice(6).trim()
            } else if (trimmed.startsWith('data:')) {
              const payload = trimmed.slice(5).trim()
              if (payload === '[DONE]') {
                if (onDone) onDone()
                return
              }

              // 发送 { type, ...parsed } 给回调
              try {
                const parsed = JSON.parse(payload)
                if (typeof parsed === 'string') {
                  // reasoning/content 事件的 data 是 JSON 字符串如 "The"
                  if (onMessage) onMessage({ type: currentEventType, content: parsed })
                } else if (parsed && typeof parsed === 'object') {
                  if (onMessage) onMessage({ type: currentEventType, ...parsed })
                } else {
                  if (onMessage) onMessage({ type: currentEventType, content: payload })
                }
              } catch (e) {
                // 非 JSON 或格式异常，作为纯文本
                if (onMessage) onMessage({ type: currentEventType, content: payload })
              }
              currentEventType = null // 重置
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
  
  // 撤回并重试 - POST /api/agent/retry
  retryLast: () => {
    return api.post('/agent/retry')
  },
  
  // 回退到指定轮次 - POST /api/agent/rewind
  rewind: (step) => {
    return api.post('/agent/rewind', { step })
  },
  
  // 折叠上下文 - POST /api/agent/compact
  compact: () => {
    return api.post('/agent/compact')
  },
  
  // 进入计划模式 - POST /api/agent/plan/enable
  enablePlanMode: () => {
    return api.post('/agent/plan/enable')
  },
  
  // 退出计划模式 - POST /api/agent/plan/disable
  disablePlanMode: () => {
    return api.post('/agent/plan/disable')
  },
  
  // 获取HITL状态 - GET /api/agent/hitl/status
  getHitlStatus: () => {
    return api.get('/agent/hitl/status')
  },
  
  // 切换HITL模式 - POST /api/agent/hitl/toggle
  toggleHitl: () => {
    return api.post('/agent/hitl/toggle')
  },
  
  // 批准HITL - POST /api/agent/hitl/approve
  approveHitl: () => {
    return api.post('/agent/hitl/approve')
  },
  
  // 拒绝HITL - POST /api/agent/hitl/deny
  denyHitl: () => {
    return api.post('/agent/hitl/deny')
  },
  
  // 获取待审批列表 - GET /api/agent/hitl/pending
  getPendingHitl: () => {
    return api.get('/agent/hitl/pending')
  }
}

// 会话 API
export const sessionsAPI = {
  // 列出所有会话 - GET /api/sessions
  list: () => {
    return api.get('/sessions')
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
  execute: (name, arguments_) => {
    return api.post(`/tools/${name}/execute`, { arguments: arguments_ })
  }
}

// 配置 API
export const configAPI = {
  // 获取当前配置 - GET /api/config
  getConfig: () => {
    return api.get('/config')
  },
  
  // 获取Token用量统计 - GET /api/usage
  getUsage: () => {
    return api.get('/usage')
  }
}

// 系统 API（兼容性保留）
export const systemAPI = {
  // 获取系统状态（兼容旧接口）
  getStatus: () => {
    return agentAPI.getStatus()
  },
  
  // 获取系统信息（兼容旧接口）
  getInfo: () => {
    return configAPI.getConfig()
  },
  
  // 健康检查（兼容旧接口）
  healthCheck: () => {
    return api.get('/health')
  }
}

export default api