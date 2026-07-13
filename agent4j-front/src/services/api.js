import axios from 'axios'
import { message } from 'ant-design-vue'

/** 默认兜底值，运行时优先读 public/config.json */
export const DEFAULT_API_BASE = 'http://localhost:4567'

/** 应用启动时调用一次，从 public/config.json 加载默认地址到 localStorage */
export async function initConfig() {
  try {
    const resp = await fetch('/config.json')
    if (resp.ok) {
      const cfg = await resp.json()
      if (cfg.apiBase && !localStorage.getItem('agent4j-api-base')) {
        localStorage.setItem('agent4j-api-base', cfg.apiBase)
      }
    }
  } catch { /* 加载失败则用 DEFAULT_API_BASE */ }
}

// 读取持久化的 API 地址（用户在设置页配置的）
// 优先级：localStorage 用户设置 > config.json > 硬编码
function getCustomBaseURL() {
  return localStorage.getItem('agent4j-api-base') || DEFAULT_API_BASE
}

const api = axios.create({
  baseURL: '/api',  // 默认值，请求拦截器中会动态覆盖
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 动态 baseURL：优先使用用户配置的服务端地址
    const customBase = getCustomBaseURL()
    if (customBase) {
      config.baseURL = customBase.replace(/\/+$/, '') + '/api'
    }
    
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

    const data = response.data
    // 后端业务错误（HTTP 200 但 success=false）
    if (data && data.success === false) {
      const errMsg = data.error || data.message || '操作失败'
      message.error(errMsg)
      return Promise.reject({ code: response.status, message: errMsg, data })
    }

    return data
  },
  (error) => {
    console.error('API Error:', error)

    if (error.response) {
      const { status, data } = error.response
      const errorMsg = data?.error || data?.message || error.message || '未知错误'

      switch (status) {
        case 401:
          localStorage.removeItem('agent4j-token')
          window.location.href = '/login'
          break
        case 403:
          message.error('权限不足: ' + errorMsg)
          break
        case 404:
          message.error('资源不存在: ' + (error.config?.url || ''))
          break
        case 429:
          message.warning('请求过于频繁，请稍后重试')
          break
        default:
          if (status >= 500) {
            message.error('服务器错误: ' + errorMsg)
          } else {
            message.error(errorMsg)
          }
      }

      return Promise.reject({
        code: status,
        message: errorMsg,
        data: data
      })
    }

    if (error.code === 'ECONNABORTED') {
      message.error('请求超时，请检查网络连接')
      return Promise.reject({
        code: 'TIMEOUT',
        message: '请求超时，请检查网络连接'
      })
    }

    if (!window.navigator.onLine) {
      message.error('网络连接已断开')
      return Promise.reject({
        code: 'OFFLINE',
        message: '网络连接已断开'
      })
    }

    message.error('请求失败: ' + (error.message || '未知错误'))
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
  
  // 中断当前聊天 - POST /api/chat/abort
  abort: () => {
    return api.post('/chat/abort')
  },

    // SSE流式聊天 - POST /api/chat/stream
  sendMessageStream: (message, onMessage, onDone, onError, options = {}) => {
    const abortController = new AbortController()

    ;(async () => {
      try {
        const requestBody = { message }
        // 添加工作区和会话信息
        if (options.workspaceHash) requestBody.workspaceHash = options.workspaceHash
        if (options.sessionName) requestBody.sessionName = options.sessionName
          // 添加图片（base64 Data URI 列表）
          if (options.images && options.images.length > 0) {
              requestBody.images = options.images
          }

          // 剔除尾部斜杠，防止 apiBase 为 '/' 时产生协议相对 URL
          const base = (getCustomBaseURL() || '').replace(/\/+$/, '')
          // base 剔除斜杠后若为空，使用相对路径（由 Vite 代理转发到后端）
        const url = base ? `${base}/api/chat/stream` : '/api/chat/stream'
        const res = await fetch(url, {
          method: 'POST',
          headers: { 
            'Content-Type': 'application/json',
            'X-Request-ID': generateRequestId(),
            'X-Timestamp': Date.now().toString()
          },
          body: JSON.stringify(requestBody),
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
  
  // 获取历史消息 - GET /api/agent/history?workspaceHash=xxx&sessionName=xxx
  getHistory: (workspaceHash, sessionName) => {
    const params = {}
    if (workspaceHash) params.workspaceHash = workspaceHash
    if (sessionName) params.sessionName = sessionName
    return api.get('/agent/history', { params })
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
  },
  
  // 获取可用命令列表 - GET /api/agent/commands
  getCommands: () => {
    return api.get('/agent/commands')
  },
  
  // 获取可用skill列表 - GET /api/agent/skills
  getSkills: () => {
    return api.get('/agent/skills')
  },

    // 获取当前会话的系统提示词 - GET /api/agent/prompt?workspaceHash=xxx&sessionName=xxx
    getSystemPrompt: (params) => {
        return api.get('/agent/prompt', {params: params || {}})
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
  
  // 新建空白会话 - POST /api/sessions/new?workspaceHash=xxx&sessionName=xxx
  createNew: (params) => {
    return api.post('/sessions/new', null, { params: params || {} })
  },
  
  // 切换会话 - POST /api/sessions/{name}?workspaceHash=xxx
  switchSession: (name, workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.post(`/sessions/${name}`, null, { params })
  },
  
  // 删除会话 - DELETE /api/sessions/{name}?workspaceHash=xxx
  deleteSession: (name, workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.delete(`/sessions/${name}`, { params })
  },

  // 清空所有会话 - DELETE /api/sessions?workspaceHash=xxx
  clearAll: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.delete('/sessions', { params })
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
  },
  
  // 获取会话工作流 - GET /api/sessions/{name}/workflow?workspaceHash=xxx
  getWorkflow: (name, workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get(`/sessions/${name}/workflow`, { params })
  },

}

// 工具 API
export const toolsAPI = {
  // 列出所有已注册工具（含已禁用的）- GET /api/tools
  list: () => {
    return api.get('/tools')
  },
  
  // 获取工具详情 - GET /api/tools/{name}
  getDetails: (name) => {
    return api.get(`/tools/${name}`)
  },
  
  // 切换工具启用/禁用状态 - POST /api/tools/{name}/toggle
  toggle: (name) => {
    return api.post(`/tools/${name}/toggle`)
  },
  
  // 直接执行工具 - POST /api/tools/{name}/execute
  execute: (name, args) => {
    return api.post(`/tools/${name}/execute`, { arguments: args })
  },
  
  // 切换工具自动放行状态 - POST /api/tools/{name}/auto-toggle
  autoToggle: (name) => {
    return api.post(`/tools/${name}/auto-toggle`)
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
  
  // 获取可用模型列表 - GET /api/models
  getModels: () => {
    return api.get('/models')
  },

  // 从远程 API 获取模型列表 - GET /api/remote-models
  getRemoteModels: () => {
    return api.get('/remote-models')
  },

  // 从远程 API 获取视觉模型列表 - GET /api/remote-vision-models
  getRemoteVisionModels: () => {
    return api.get('/remote-vision-models')
  },

  // 从 AI 模型复制视觉配置 - POST /api/config/copy-vision-from-ai
  copyVisionFromAi: () => {
    return api.post('/config/copy-vision-from-ai')
  },
  
  // 获取Token用量统计 - GET /api/usage?workspaceHash=xxx&sessionName=xxx
  getUsage: (params) => {
    return api.get('/usage', { params })
  },

  // 获取数据面板 - GET /api/usage/dashboard?days=7
  getDashboard: (days) => {
    const params = days ? { days } : {}
    return api.get('/usage/dashboard', { params })
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
  
  // 删除工作区 - DELETE /api/workspaces/{hash}
  deleteWorkspace: (hash) => {
    return api.delete(`/workspaces/${hash}`)
  },

  // 获取 agent4j.md 内容 - GET /api/agent4j-md
  getAgent4jMd: () => {
    return api.get('/agent4j-md')
  },

  // 更新 agent4j.md 内容 - PUT /api/agent4j-md
  updateAgent4jMd: (content) => {
    return api.put('/agent4j-md', content, {
      headers: { 'Content-Type': 'text/plain' }
    })
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
  
  // 健康检查 - GET /api/system/health
  healthCheck: () => {
    return api.get('/system/health')
  },
  
  // 获取系统版本 - GET /api/system/version
  getVersion: () => {
    return api.get('/system/version')
  },

  // 获取当前版本（新版） - GET /api/version/
  getCurrentVersion: () => {
    return api.get('/version/')
  },

  // 检查最新版本 - GET /api/version/check
  checkLatestVersion: () => {
    return api.get('/version/check')
  },
  
  // 获取系统日志
  getLogs: (params) => {
    return api.get('/logs', { params })
  }
}

// OpenAPI 管理 API
export const openApiAPI = {
    getSources: () => {
        return api.get('/openapi/sources')
    },

    searchApis: (keyword) => {
        return api.get('/openapi/search', {params: {keyword}})
    },

    addSource: (docUrl, headers, authType, authConfig) => {
        return api.post('/openapi/sources', {docUrl, headers, authType, authConfig})
    },

    removeSource: (docUrl) => {
        return api.delete('/openapi/sources', {data: {docUrl}})
    },

    refreshSource: (docUrl, headers, authType, authConfig) => {
        return api.put('/openapi/sources/refresh', {docUrl, headers, authType, authConfig})
    }
}

// MCP 服务器管理 API
export const mcpAPI = {
    // 获取所有 MCP 服务器列表 - GET /api/mcp/servers
    listServers: () => {
        return api.get('/mcp/servers')
    },

    // 新增 MCP 服务器 - POST /api/mcp/servers/add
    addServer: (server) => {
        return api.post('/mcp/servers/add', server)
    },

    // 更新 MCP 服务器 - POST /api/mcp/servers/update
    updateServer: (originalName, server) => {
        return api.post('/mcp/servers/update', { originalName, server })
    },

    // 删除 MCP 服务器 - POST /api/mcp/servers/remove
    removeServer: (name) => {
        return api.post('/mcp/servers/remove', { name })
    },

    // 启用/禁用 MCP 服务器 - POST /api/mcp/servers/toggle
    toggleServer: (name, enabled) => {
        return api.post('/mcp/servers/toggle', { name, enabled })
    },

    // 检测 MCP 服务器连接 - POST /api/mcp/servers/check
    checkConnection: (server) => {
        return api.post('/mcp/servers/check', server)
    },

    // 查看服务器工具列表 - GET /api/mcp/servers/tools?name=xxx
    listTools: (name) => {
        return api.get('/mcp/servers/tools', { params: { name } })
    },

    // 保存工具权限 - POST /api/mcp/servers/tools/save
    saveToolPermissions: (serverName, disallowedTools) => {
        return api.post('/mcp/servers/tools/save', { serverName, disallowedTools })
    }
}

// Git API
export const gitAPI = {
  // 获取当前分支 - GET /api/git/branch?workspaceHash=xxx
  branch: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/git/branch', { params })
  },

  // 获取变更文件列表 - GET /api/git/diff?workspaceHash=xxx
  diff: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/git/diff', { params })
  },

  // 综合状态检测（git 可用性 + 仓库状态 + 分支 + 变更文件） - GET /api/git/status?workspaceHash=xxx
  status: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/git/status', { params })
  },

  // 初始化 Git 仓库 - POST /api/git/init?workspaceHash=xxx&initialCommit=true
  init: (workspaceHash, initialCommit) => {
    const params = workspaceHash ? { workspaceHash } : {}
    if (initialCommit !== undefined) params.initialCommit = initialCommit
    return api.post('/git/init', null, { params })
  },

  // 获取 Diff 内容（unified diff 文本 + stat 摘要） - GET /api/git/diff-content?workspaceHash=xxx&path=xxx
  diffContent: (workspaceHash, path) => {
    const params = workspaceHash ? { workspaceHash } : {}
    if (path) params.path = path
    return api.get('/git/diff-content', { params })
  },

  // 获取 Git 仓库中指定版本的文件内容 - GET /api/git/file-content?workspaceHash=xxx&path=xxx&ref=HEAD
  fileContent: (workspaceHash, path, ref) => {
    const params = {}
    if (workspaceHash) params.workspaceHash = workspaceHash
    if (path) params.path = path
    if (ref) params.ref = ref
    return api.get('/git/file-content', { params })
  },

  // Git 提交 - POST /api/git/commit?workspaceHash=xxx  body: { message, files, authorName, authorEmail }
  commit: (workspaceHash, message, files, authorName, authorEmail) => {
    const params = workspaceHash ? { workspaceHash } : {}
    const body = { message }
    if (files && files.length) body.files = files
    if (authorName) body.authorName = authorName
    if (authorEmail) body.authorEmail = authorEmail
    return api.post('/git/commit', body, { params })
  },

  // 获取提交作者配置 (已保存 > git config > Agent4j 默认) - GET /api/git/config?workspaceHash=xxx
  getConfig: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/git/config', { params })
  },

  // 保存提交作者配置到工作区 - POST /api/git/config?workspaceHash=xxx  body: { authorName, authorEmail, model }
  saveConfig: (workspaceHash, authorName, authorEmail, model) => {
    const params = workspaceHash ? { workspaceHash } : {}
    const body = { authorName, authorEmail }
    if (model) body.model = model
    return api.post('/git/config', body, { params })
  },

  // 切换文件状态 - POST /api/git/toggle?workspaceHash=xxx  body: { path }
  toggle: (workspaceHash, path) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.post('/git/toggle', { path }, { params })
  },

  // AI 自动生成提交消息 - POST /api/git/generate-commit-message?workspaceHash=xxx  body: { files, model }
  generateCommitMessage: (workspaceHash, files, model) => {
    const params = workspaceHash ? { workspaceHash } : {}
    const body = {}
    if (files && files.length) body.files = files
    if (model) body.model = model
    return api.post('/git/generate-commit-message', body, { params, timeout: 120000 })
  },

  // 获取提交历史记录 - GET /api/git/log?workspaceHash=xxx&limit=50
  commitHistory: (workspaceHash, limit) => {
    const params = workspaceHash ? { workspaceHash } : {}
    if (limit) params.limit = limit
    return api.get('/git/log', { params })
  },

  // 获取可用模型列表 - GET /api/models
  getModels: () => {
    return api.get('/models')
  }
}

// 快照检查点 API
export const snapshotAPI = {
  // 创建快照检查点 - POST /api/snapshots/checkpoint?workspaceHash=xxx&msgId=xxx
  createCheckpoint: (workspaceHash, msgId) => {
    const params = {}
    if (workspaceHash) params.workspaceHash = workspaceHash
    params.msgId = msgId
    return api.post('/snapshots/checkpoint', null, { params })
  },

    // 撤回到快照 - POST /api/snapshots/rollback?workspaceHash=xxx&msgId=xxx&sessionName=xxx
    rollback: (workspaceHash, msgId, sessionName) => {
    const params = {}
    if (workspaceHash) params.workspaceHash = workspaceHash
    params.msgId = msgId
        if (sessionName) params.sessionName = sessionName
    return api.post('/snapshots/rollback', null, { params })
  },

  // 列出快照 - GET /api/snapshots?workspaceHash=xxx&sessionName=xxx
  list: (workspaceHash, sessionName) => {
    const params = {}
    if (workspaceHash) params.workspaceHash = workspaceHash
    if (sessionName) params.sessionName = sessionName
    return api.get('/snapshots', { params })
  },

  // 检查 Git 仓库状态 - GET /api/snapshots/status?workspaceHash=xxx
  getStatus: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/snapshots/status', { params })
  },

  // 删除快照 - DELETE /api/snapshots/{msgId}?workspaceHash=xxx
  deleteSnapshot: (msgId, workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.delete(`/snapshots/${msgId}`, { params })
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

// 技能市场 API
export const skillMarketAPI = {
  // 获取可用市场列表 - GET /api/skill-market/markets
  getMarkets: () => {
    return api.get('/skill-market/markets')
  },

  // 浏览/搜索技能 - GET /api/skill-market/proxy
  proxy: (params) => {
    return api.get('/skill-market/proxy', { params })
  },

  // 获取技能详情 - GET /api/skill-market/detail
  getDetail: (slug, marketName) => {
    const params = { slug }
    if (marketName) params.marketName = marketName
    return api.get('/skill-market/detail', { params })
  },

  // 安装技能 - POST /api/skill-market/install
  install: (slug, marketName) => {
    const params = new URLSearchParams()
    params.append('slug', slug)
    if (marketName) params.append('marketName', marketName)
    return api.post('/skill-market/install', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
  },

  // 卸载技能 - POST /api/skill-market/uninstall
  uninstall: (slug) => {
    const params = new URLSearchParams()
    params.append('slug', slug)
    return api.post('/skill-market/uninstall', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
  }
}

// LSP 服务器管理 API
export const lspAPI = {
    listServers: () => api.get('/lsp/servers'),
    addServer: (server) => api.post('/lsp/servers/add', server),
    updateServer: (originalName, server) => api.post('/lsp/servers/update', { originalName, server }),
    removeServer: (name) => api.post('/lsp/servers/remove', { name }),
    toggleServer: (name, enabled) => api.post('/lsp/servers/toggle', { name, enabled }),
}

// 定时任务 API
export const scheduleAPI = {
  // 列出指定工作区的所有定时任务 - GET /api/schedules?workspaceHash=xxx
  list: (workspaceHash) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get('/schedules', { params })
  },

  // 获取单个定时任务 - GET /api/schedules/{id}?workspaceHash=xxx
  get: (workspaceHash, id) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.get(`/schedules/${id}`, { params })
  },

  // 创建定时任务 - POST /api/schedules?workspaceHash=xxx
  create: (workspaceHash, task) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.post('/schedules', task, { params })
  },

  // 更新定时任务 - PUT /api/schedules/{id}?workspaceHash=xxx
  update: (workspaceHash, id, task) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.put(`/schedules/${id}`, task, { params })
  },

  // 启用/禁用定时任务 - POST /api/schedules/{id}/toggle?workspaceHash=xxx
  toggle: (workspaceHash, id) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.post(`/schedules/${id}/toggle`, null, { params })
  },

  // 手动触发执行 - POST /api/schedules/{id}/run?workspaceHash=xxx
  runNow: (workspaceHash, id) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.post(`/schedules/${id}/run`, null, { params })
  },

  // 删除定时任务 - DELETE /api/schedules/{id}?workspaceHash=xxx
  delete: (workspaceHash, id) => {
    const params = workspaceHash ? { workspaceHash } : {}
    return api.delete(`/schedules/${id}`, { params })
  }
}

export const petAPI = {
  /** 获取宠物元数据（旧版兼容，同 getActive） */
  getInfo: () => api.get('/pets/active'),
  /** 保存位置/大小 */
  savePosition: (pos) => api.put('/pets/position', pos),
  /** 获取当前活跃宠物的 spritesheet URL */
  getSpritesheetUrl: () => '/api/pets/active/spritesheet',

  /** 列出所有可用宠物 */
  listPets: () => api.get('/pets'),
  /** 获取指定宠物元数据 */
  getPetInfo: (name) => api.get(`/pets/${name}`),
  /** 获取指定宠物的 spritesheet URL */
  getPetSpritesheetUrl: (name) => `/api/pets/${name}/spritesheet`,
  /** 删除指定宠物（清空文件夹） */
  deletePet: (name) => api.delete(`/pets/${name}`),
  /** 获取当前活跃宠物信息 */
  getActive: () => api.get('/pets/active'),
  /** 设置活跃宠物 */
  setActive: (name) => api.put('/pets/active', { name }),
}

export default api

