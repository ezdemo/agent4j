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
    getCurrentPort: () => ipcRenderer.invoke('get_loopra_web_port'),
    installOnline: () => ipcRenderer.invoke('install_loopra_web_online')
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

  // 元素检测（跨域 iframe 穿透）
  inspector: {
    inject: () => ipcRenderer.invoke('inspector-inject'),
    remove: () => ipcRenderer.invoke('inspector-remove')
  },

  // 打开外部链接
  openExternal: (url) => ipcRenderer.invoke('open-external', url),

  // 打开本地文件
  openFile: (filePath) => ipcRenderer.invoke('open-file', filePath),

  // Electron 版本
  getElectronVersion: () => ipcRenderer.invoke('get_electron_version'),

  // 平台信息
  platform: process.platform,

  // 环境检测
  isElectron: true
})