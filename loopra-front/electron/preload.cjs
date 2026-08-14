const { contextBridge, ipcRenderer } = require('electron')

// 暴露 API 给渲染进程
contextBridge.exposeInMainWorld('electronAPI', {
  // loopra-web 服务管理
  loopraWebService: {
    getStatus: () => ipcRenderer.invoke('get_loopra_web_status'),
    getResourceDir: () => ipcRenderer.invoke('get_resource_dir'),
    checkInstallNeeded: (resourceDir) => ipcRenderer.invoke('check_install_needed', resourceDir),
    install: (resourceDir) => ipcRenderer.invoke('install_loopra_web', resourceDir),
    start: () => ipcRenderer.invoke('start_loopra_web'),
    stop: () => ipcRenderer.invoke('stop_loopra_web'),
    listProcesses: () => ipcRenderer.invoke('list_loopra_java_processes'),
    openProcess: (pid) => ipcRenderer.invoke('open_loopra_java_process', pid),
    terminateProcess: (pid) => ipcRenderer.invoke('terminate_loopra_java_process', pid),
    pickFolder: () => ipcRenderer.invoke('pick_loopra_workspace_folder'),
    getCurrentPort: () => ipcRenderer.invoke('get_loopra_web_port'),
    installOnline: (source) => ipcRenderer.invoke('install_loopra_web_online', { source })
  },

  // 启动窗口：检测/安装/启动完成后通知主进程创建主窗口
  splash: {
    ready: () => ipcRenderer.invoke('splash_ready'),
    resize: (size) => ipcRenderer.send('splash-resize', size)
  },

  // 更新窗口管理
  updateWindow: {
    open: () => ipcRenderer.invoke('open-update-window'),
    close: () => ipcRenderer.invoke('update-window-close'),
    requestChatUpdate: (source) => ipcRenderer.invoke('chat-update-request', { source })
  },

  // 引导页窗口：独立窗口承载首次使用引导流程（迁移会话/设置模型/导入 Skills/迁移 AGENTS.md/MCP）
  onboarding: {
    open: () => ipcRenderer.invoke('onboarding-window-open'),
    close: () => ipcRenderer.invoke('onboarding-window-close'),
    getDirs: () => ipcRenderer.invoke('onboarding-get-dirs'),
    pickDir: (title) => ipcRenderer.invoke('onboarding-pick-dir', title),
    pickFile: (options) => ipcRenderer.invoke('onboarding-pick-file', options),
    scanDir: (rootDir) => ipcRenderer.invoke('onboarding-scan-dir', rootDir),
    importSkills: (payload) => ipcRenderer.invoke('onboarding-import-skills', payload),
    importSessions: (payload) => ipcRenderer.invoke('onboarding-import-sessions', payload),
    importAgentsMd: (payload) => ipcRenderer.invoke('onboarding-import-agents-md', payload),
    readTextFile: (payload) => ipcRenderer.invoke('onboarding-read-text-file', payload),
    parseMcpConfig: (filePath) => ipcRenderer.invoke('onboarding-parse-mcp-config', filePath)
  },
  
  // 窗口控制
  window: {
    minimize: () => ipcRenderer.invoke('window-minimize'),
    maximize: () => ipcRenderer.invoke('window-maximize'),
    close: () => ipcRenderer.invoke('window-close'),
    isMaximized: () => ipcRenderer.invoke('window-is-maximized')
  },
  
  // 事件监听
  events: {
    listen: (eventName, callback) => {
      const subscription = (event, ...args) => callback(...args)
      ipcRenderer.on(eventName, subscription)
      
      // 返回取消监听函数
      return () => {
        ipcRenderer.removeListener(eventName, subscription)
      }
    }
  },

  elementWebView: {
    load: (url) => ipcRenderer.invoke('element-webview-load', url),
    show: (bounds) => ipcRenderer.invoke('element-webview-show', bounds),
    hide: () => ipcRenderer.invoke('element-webview-hide')
  },

  elementInspectorWindow: {
    open: (url) => ipcRenderer.invoke('open-element-inspector-window', url),
    ready: () => ipcRenderer.send('element-inspector-ready'),
    send: (payload) => ipcRenderer.send('element-inspector-send', payload),
    onDraft: (callback) => {
      const subscription = (event, payload) => callback(payload)
      ipcRenderer.on('element-inspector-draft', subscription)
      return () => ipcRenderer.removeListener('element-inspector-draft', subscription)
    }
  },

  aiBrowserWindow: {
    open: () => ipcRenderer.invoke('open-ai-browser-window'),
    getBridgeAddress: () => ipcRenderer.invoke('get-ai-browser-bridge-address')
  },

  desktopHomeMenu: {
    open: (theme) => ipcRenderer.invoke('desktop-home-context-menu', theme)
  },

  desktopTabMenu: {
    open: (payload) => ipcRenderer.invoke('desktop-tab-context-menu', payload)
  },

  requirementBoardWindow: {
    open: () => ipcRenderer.invoke('open-requirement-board-window')
  },

  desktopPet: {
    open: () => ipcRenderer.invoke('desktop-pet-open'),
    close: () => ipcRenderer.invoke('desktop-pet-close'),
    isVisible: () => ipcRenderer.invoke('desktop-pet-is-visible'),
    moveBy: (delta) => ipcRenderer.invoke('desktop-pet-move-by', delta),
    setInteractive: (interactive) => ipcRenderer.send('desktop-pet-set-interactive', Boolean(interactive)),
    activateMain: () => ipcRenderer.invoke('desktop-pet-activate-main'),
    refresh: () => ipcRenderer.invoke('desktop-pet-refresh'),
    onRefresh: (callback) => {
      const listener = () => callback()
      ipcRenderer.on('desktop-pet-refresh', listener)
      return () => ipcRenderer.removeListener('desktop-pet-refresh', listener)
    },
    onClosed: (callback) => {
      const listener = () => callback()
      ipcRenderer.on('desktop-pet-closed', listener)
      return () => ipcRenderer.removeListener('desktop-pet-closed', listener)
    },
    showReply: (text) => ipcRenderer.invoke('desktop-pet-reply', text),
    onReply: (callback) => {
      const listener = (event, payload) => callback(payload?.text || '')
      ipcRenderer.on('desktop-pet-reply', listener)
      return () => ipcRenderer.removeListener('desktop-pet-reply', listener)
    }
  },

  aiBrowser: {
    newTab: (url) => ipcRenderer.invoke('ai-browser-new-tab', url),
    navigate: (tabId, url) => ipcRenderer.invoke('ai-browser-navigate', tabId, url),
    history: (tabId, action) => ipcRenderer.invoke('ai-browser-history', tabId, action),
    activateTab: (tabId) => ipcRenderer.invoke('ai-browser-activate-tab', tabId),
    closeTab: (tabId) => ipcRenderer.invoke('ai-browser-close-tab', tabId),
    getState: () => ipcRenderer.invoke('ai-browser-get-state'),
    showView: (tabId, bounds) => ipcRenderer.invoke('ai-browser-view-show', tabId, bounds),
    hideView: () => ipcRenderer.invoke('ai-browser-view-hide')
  },

  desktopChatTabs: {
    create: (tab) => ipcRenderer.invoke('desktop-chat-tab-create', tab),
    ready: () => ipcRenderer.send('desktop-chat-tab-ready'),
    show: (tabId, bounds) => ipcRenderer.invoke('desktop-chat-tab-show', tabId, bounds),
    hide: () => ipcRenderer.invoke('desktop-chat-tab-hide'),
    close: (tabId) => ipcRenderer.invoke('desktop-chat-tab-close', tabId),
    reload: (tabId) => ipcRenderer.invoke('desktop-chat-tab-reload', tabId),
    toggleRightPanel: (tabId) => ipcRenderer.invoke('desktop-chat-tab-toggle-right-panel', tabId),
    toggleTerminal: (tabId) => ipcRenderer.invoke('desktop-chat-tab-toggle-terminal', tabId),
    setTheme: (theme) => ipcRenderer.invoke('desktop-chat-tab-set-theme', theme),
    openHome: () => ipcRenderer.send('desktop-chat-tab-open-home'),
    openModelChannels: () => ipcRenderer.send('desktop-chat-tab-open-model-channels'),
    sendCommand: (tabId, command) => ipcRenderer.invoke('desktop-chat-tab-send-command', tabId, command),
    reportTitle: (payload) => ipcRenderer.send('desktop-chat-tab-report-title', payload),
    reportWorkspace: (payload) => ipcRenderer.send('desktop-chat-tab-report-workspace', payload)
  },

  // 终端（node-pty + xterm）
  terminal: {
    create: (options) => ipcRenderer.invoke('terminal:create', options),
    listShells: () => ipcRenderer.invoke('terminal:list-shells'),
    input: (payload) => ipcRenderer.send('terminal:input', payload),
    resize: (payload) => ipcRenderer.send('terminal:resize', payload),
    kill: (id) => ipcRenderer.invoke('terminal:kill', id),
    onData: (callback) => {
      const subscription = (event, payload) => callback(payload)
      ipcRenderer.on('terminal:data', subscription)
      return () => ipcRenderer.removeListener('terminal:data', subscription)
    },
    onExit: (callback) => {
      const subscription = (event, payload) => callback(payload)
      ipcRenderer.on('terminal:exit', subscription)
      return () => ipcRenderer.removeListener('terminal:exit', subscription)
    }
  },

  // 元素检测（跨域 iframe 穿透）
  inspector: {
    inject: () => ipcRenderer.invoke('inspector-inject'),
    remove: () => ipcRenderer.invoke('inspector-remove')
  },

  // 打开外部链接
  openExternal: (url) => ipcRenderer.invoke('open-external', url),

  // 打开本地文件
  openFile: (filePath) => ipcRenderer.invoke('open-file', filePath),

  // 打开本地文件夹（系统原生文件管理器）
  openFolder: (folderPath) => ipcRenderer.invoke('open-folder', folderPath),

  // 文件资源管理器（桌面端，主进程直接操作文件系统，不接后端）
  fileExplorer: {
    list: (dirPath) => ipcRenderer.invoke('file-explorer-list', dirPath),
    rename: (filePath, newName) => ipcRenderer.invoke('file-explorer-rename', { filePath, newName }),
    remove: (filePath) => ipcRenderer.invoke('file-explorer-delete', filePath),
    read: (filePath) => ipcRenderer.invoke('file-explorer-read', filePath),
    write: (filePath, content) => ipcRenderer.invoke('file-explorer-write', { filePath, content }),
    search: (dirPath, keyword) => ipcRenderer.invoke('file-explorer-search', { dirPath, keyword }),
    watch: (dirPath) => ipcRenderer.invoke('file-explorer-watch', dirPath),
    unwatch: () => ipcRenderer.invoke('file-explorer-unwatch'),
    onDidChange: (callback) => {
      const subscription = (event, payload) => callback(payload)
      ipcRenderer.on('file-explorer-changed', subscription)
      return () => ipcRenderer.removeListener('file-explorer-changed', subscription)
    }
  },

  // 当前环境 Git 操作（只在 Electron 主进程执行）
  gitEnvironment: {
    status: (cwd) => ipcRenderer.invoke('git-environment-status', cwd),
    history: (payload) => ipcRenderer.invoke('git-environment-history', payload),
    commit: (payload) => ipcRenderer.invoke('git-environment-commit', payload),
    push: (payload) => ipcRenderer.invoke('git-environment-push', payload),
    merge: (payload) => ipcRenderer.invoke('git-environment-merge', payload)
  },

  // Electron 版本
  getElectronVersion: () => ipcRenderer.invoke('get_electron_version'),

  // 平台信息
  platform: process.platform,

  // 环境检测
  isElectron: true
})
