import {defineStore} from 'pinia'
import {computed, ref, watch} from 'vue'

export const useAppStore = defineStore('app', () => {
  // 连接状态
  const connectionStatus = ref('disconnected')
  const isConnecting = ref(false)
  const lastError = ref(null)
  
  // 会话状态
  const currentSession = ref(null)
  const sessions = ref([])
  const isLoadingSessions = ref(false)
  
  // 消息状态
  const messages = ref([])
  const isStreaming = ref(false)
  const streamingMessage = ref(null)
  
  // 设置状态
  const settings = ref({
    language: 'zh-CN',
    theme: 'light',
    fontSize: 14,
    animations: true,
    server: {
      apiBaseUrl: '',
      autoConnect: true
    },
    ai: {
      baseUrl: '',
      apiKey: '',
      model: 'deepseek-v4-flash',
      reasoningEffort: 'max',
      temperature: 0.7
    },
    workspace: {
      dir: '.',
      editMode: 'auto',
      excludeDirs: 'node_modules, .git, target, dist',
      autoRefresh: true
    },
    security: {
      stormBreaker: true,
      pathTraversal: true,
      commandWhitelist: true,
      auditLog: true
    },
    advanced: {
      debugMode: false,
      contextFolding: true,
      messageHealing: true,
      autoSaveInterval: 30,
      maxHistory: 50
    }
  })
  
  // 工具状态
  const tools = ref([])
  const isLoadingTools = ref(false)
  
  // 统计状态
  const usageStats = ref({
    totalTokens: 0,
    promptTokens: 0,
    completionTokens: 0,
    cacheHit: 0
  })
  
  // UI 状态
  const activeModal = ref(null)
  const notifications = ref([])
  const isLoading = ref(false)
  
  // 计算属性
  const isConnected = computed(() => connectionStatus.value === 'connected')
  const hasMessages = computed(() => messages.value.length > 0)
  const unreadNotifications = computed(() => 
    notifications.value.filter(n => !n.read).length
  )
  
  // 连接状态管理
  const setConnectionStatus = (status) => {
    connectionStatus.value = status
    isConnecting.value = status === 'connecting'
    if (status === 'connected') {
      lastError.value = null
    }
  }
  
  const setConnectionError = (error) => {
    lastError.value = error
    connectionStatus.value = 'error'
    isConnecting.value = false
  }
  
  // 会话管理
  const setCurrentSession = (session) => {
    currentSession.value = session
  }
  
  const setSessions = (sessionList) => {
    sessions.value = sessionList
    isLoadingSessions.value = false
  }
  
  const addSession = (session) => {
    sessions.value.unshift(session)
  }
  
  const removeSession = (sessionId) => {
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (currentSession.value?.id === sessionId) {
      currentSession.value = sessions.value[0] || null
    }
  }
  
  const updateSession = (sessionId, updates) => {
    const index = sessions.value.findIndex(s => s.id === sessionId)
    if (index > -1) {
      sessions.value[index] = { ...sessions.value[index], ...updates }
    }
    if (currentSession.value?.id === sessionId) {
      currentSession.value = { ...currentSession.value, ...updates }
    }
  }
  
  // 消息管理
  const addMessage = (message) => {
    messages.value.push({
      id: Date.now(),
      timestamp: new Date().toISOString(),
      ...message
    })
  }
  
  const updateMessage = (messageId, updates) => {
    const index = messages.value.findIndex(m => m.id === messageId)
    if (index > -1) {
      messages.value[index] = { ...messages.value[index], ...updates }
    }
  }
  
  const removeMessage = (messageId) => {
    messages.value = messages.value.filter(m => m.id !== messageId)
  }
  
  const clearMessages = () => {
    messages.value = []
    streamingMessage.value = null
  }
  
  // 流式消息管理
  const startStreaming = (message) => {
    isStreaming.value = true
    streamingMessage.value = message
    addMessage(message)
  }
  
  const updateStreamingContent = (content) => {
    if (streamingMessage.value) {
      const index = messages.value.findIndex(m => m.id === streamingMessage.value.id)
      if (index > -1) {
        messages.value[index].content += content
      }
    }
  }
  
  const finishStreaming = () => {
    isStreaming.value = false
    streamingMessage.value = null
  }
  
  // 设置管理
  const updateSettings = (newSettings) => {
    settings.value = { ...settings.value, ...newSettings }
    localStorage.setItem('agent4j-settings', JSON.stringify(settings.value))
  }
  
  const loadSettings = () => {
    const savedSettings = localStorage.getItem('agent4j-settings')
    if (savedSettings) {
      try {
        const parsed = JSON.parse(savedSettings)
        settings.value = { ...settings.value, ...parsed }
      } catch (e) {
        console.error('加载设置失败:', e)
      }
    }
  }
  
  const resetSettings = () => {
    settings.value = {
      language: 'zh-CN',
      theme: 'light',
      fontSize: 14,
      animations: true,
      server: {
        apiBaseUrl: '',
        autoConnect: true
      },
      ai: {
        baseUrl: '',
        apiKey: '',
        model: 'deepseek-v4-flash',
        reasoningEffort: 'max',
        temperature: 0.7
      },
      workspace: {
        dir: '.',
        editMode: 'auto',
        excludeDirs: 'node_modules, .git, target, dist',
        autoRefresh: true
      },
      security: {
        stormBreaker: true,
        pathTraversal: true,
        commandWhitelist: true,
        auditLog: true
      },
      advanced: {
        debugMode: false,
        contextFolding: true,
        messageHealing: true,
        autoSaveInterval: 30,
        maxHistory: 50
      }
    }
    localStorage.removeItem('agent4j-settings')
  }
  
  // 工具管理
  const setTools = (toolList) => {
    tools.value = toolList
    isLoadingTools.value = false
  }
  
  const getToolByName = (name) => {
    return tools.value.find(t => t.name === name)
  }
  
  // 统计管理
  const updateUsageStats = (stats) => {
    usageStats.value = { ...usageStats.value, ...stats }
  }
  
  const incrementTokens = (tokens) => {
    usageStats.value.totalTokens += tokens
  }
  
  // UI 管理
  const openModal = (modalName) => {
    activeModal.value = modalName
  }
  
  const closeModal = () => {
    activeModal.value = null
  }
  
  // 通知管理
  const addNotification = (notification) => {
    const id = Date.now()
    notifications.value.push({
      id,
      timestamp: new Date().toISOString(),
      read: false,
      ...notification
    })
    
    if (notification.duration) {
      setTimeout(() => {
        removeNotification(id)
      }, notification.duration)
    }
    
    return id
  }
  
  const removeNotification = (id) => {
    notifications.value = notifications.value.filter(n => n.id !== id)
  }
  
  const markNotificationRead = (id) => {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.read = true
    }
  }
  
  const clearNotifications = () => {
    notifications.value = []
  }
  
  const setLoading = (loading) => {
    isLoading.value = loading
  }

    // ========== 会话隔离的消息/流状态 ==========
    const sessionMessages = ref({})
    const sessionStreaming = ref({})
    const sessionControllers = ref({})

    function ensureSession(name) {
        if (!name) return
        if (!sessionMessages.value[name]) {
            sessionMessages.value[name] = []
        }
    }

    /** 获取指定会话的消息列表 */
    function getSessionMessages(name) {
        if (!name) return []
        ensureSession(name)
        return sessionMessages.value[name]
    }

    /** 设置指定会话的消息列表（用于 loadHistory） */
    function setSessionMessages(name, msgs) {
        if (!name) return
        sessionMessages.value[name] = msgs || []
    }

    /** 向指定会话追加一条消息 */
    function addSessionMessage(name, msg) {
        if (!name) return
        ensureSession(name)
        sessionMessages.value[name].push(msg)
    }

    /** 更新指定会话中的某条消息（通过 id 查找）
     *  @param updater 回调，接收消息对象，直接修改它
     */
    function updateSessionMessage(name, msgId, updater) {
        if (!name) return
        const arr = sessionMessages.value[name]
        if (!arr) return
        const idx = arr.findIndex(m => m.id === msgId)
        if (idx === -1) return
        updater(arr[idx])
        // 替换数组引用触发 computed
        sessionMessages.value[name] = [...arr]
    }

    /** 清空指定会话的消息 */
    function clearSessionMessages(name) {
        if (!name) return
        sessionMessages.value[name] = []
        sessionStreaming.value[name] = false
    }

    /** 设置指定会话的流状态 */
    function setSessionStreaming(name, val) {
        if (name) sessionStreaming.value[name] = val
    }

    /** 获取指定会话的流状态 */
    function getSessionStreaming(name) {
        return name ? (sessionStreaming.value[name] ?? false) : false
    }

    /** 设置指定会话的 AbortController */
    function setSessionController(name, ctrl) {
        if (name) sessionControllers.value[name] = ctrl
    }

    /** 获取指定会话的 AbortController */
    function getSessionController(name) {
        return name ? sessionControllers.value[name] : null
    }

  // 初始化
  const initialize = () => {
    loadSettings()
    
    // 从独立 key 读取主题
    const savedTheme = localStorage.getItem('agent4j-theme')
    if (savedTheme) {
      settings.value.theme = savedTheme
    }
    
    document.documentElement.setAttribute('data-theme', settings.value.theme)
    document.documentElement.style.fontSize = `${settings.value.fontSize}px`
  }

  // 监听主题变化，自动同步到全局
  watch(() => settings.value.theme, (val) => {
    document.documentElement.setAttribute('data-theme', val)
    localStorage.setItem('agent4j-theme', val)
  })
  
  return {
    connectionStatus,
    isConnecting,
    lastError,
    currentSession,
    sessions,
    isLoadingSessions,
    messages,
    isStreaming,
    streamingMessage,
    settings,
    tools,
    isLoadingTools,
    usageStats,
    activeModal,
    notifications,
    isLoading,
    isConnected,
    hasMessages,
    unreadNotifications,
    setConnectionStatus,
    setConnectionError,
    setCurrentSession,
    setSessions,
    addSession,
    removeSession,
    updateSession,
    addMessage,
    updateMessage,
    removeMessage,
    clearMessages,
    startStreaming,
    updateStreamingContent,
    finishStreaming,
    updateSettings,
    loadSettings,
    resetSettings,
    setTools,
    getToolByName,
    updateUsageStats,
    incrementTokens,
    openModal,
    closeModal,
    addNotification,
    removeNotification,
    markNotificationRead,
    clearNotifications,
    setLoading,
      initialize,
      // 会话隔离
      sessionMessages,
      sessionStreaming,
      sessionControllers,
      ensureSession,
      getSessionMessages,
      setSessionMessages,
      addSessionMessage,
      updateSessionMessage,
      clearSessionMessages,
      setSessionStreaming,
      getSessionStreaming,
      setSessionController,
      getSessionController
  }
})