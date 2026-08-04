/**
 * 平台抽象层 - 根据当前环境提供统一的 API 接口
 * 支持 Electron 和 Web 环境
 */
import {RELEASES_URL} from '../utils/constants'

// 环境检测
const isElectron = typeof window !== 'undefined' && window.electronAPI !== undefined
const isWeb = !isElectron

// 从 config.json 读取 apiBase（缓存）
let _configApiBase = null
async function getConfigApiBase() {
  if (_configApiBase) return _configApiBase
  try {
    const resp = await fetch('/config.json')
    if (resp.ok) {
      const cfg = await resp.json()
      if (cfg.apiBase) _configApiBase = cfg.apiBase
    }
  } catch { /* ignore */ }
  return _configApiBase
}

/** 从 apiBase URL 中提取端口号 */
function extractPort(apiBase) {
  if (!apiBase) return 0
  try {
    const url = new URL(apiBase)
    return parseInt(url.port, 10) || (url.protocol === 'https:' ? 443 : 80)
  } catch { return 0 }
}

/**
 * 获取当前 base URL：localStorage > config.json > 空字符串（同域）
 * web 环境下使用此值，不自行拼接 127.0.0.1
 */
async function resolveBaseUrl() {
  // 优先使用 localStorage 中用户设置的值
  const stored = localStorage.getItem('loopra-api-base')
  if (stored) return stored.replace(/\/+$/, '')

  // 其次从 config.json 读取
  const apiBase = await getConfigApiBase()
  if (apiBase) return apiBase.replace(/\/+$/, '')

  // 兜底：空字符串表示同域（相对路径）
  return ''
}

/** 获取当前端口：localStorage > config.json > 4567 */
async function resolveCurrentPort() {
  const stored = localStorage.getItem('loopra-port')
  if (stored && parseInt(stored, 10) > 0) return parseInt(stored, 10)

  const apiBase = await getConfigApiBase()
  const port = extractPort(apiBase)
  if (port > 0) return port

  return 4567
}

/**
 * Web 环境下的默认实现
 * 假设服务已在外部启动，直接通过 HTTP 与后端通信
 * 始终使用 config.json 中的 apiBase 配置，不自行拼接端口
 */
const webImplementation = {
  loopraWebService: {
    async getStatus() {
      return {
        installed: true,
        running: true,
        install_dir: '~/.loopra'
      }
    },

    async getResourceDir() {
      return null
    },

    async checkInstallNeeded() {
      return { needed: false, reason: 'web_environment' }
    },

    async install() {
      return { success: true, steps: ['web_environment_skip'] }
    },

    async start() {
      // web 环境下不需要启动服务，直接返回 0
      return 0
    },

    async stop() {
      console.log('Web environment: stop service not implemented')
    },

    async listProcesses() {
      return { processes: [] }
    },

    async openProcess() {
      return { success: false }
    },

    async terminateProcess() {
      return { success: false }
    },

    async getCurrentPort() {
      // web 环境下端口概念不适用，返回 0
      // 前端通过 getBaseUrl() 获取完整地址
      return 0
    },

    // 在线安装：web 环境下跳转到浏览器下载
    async installOnline() {
      window.open(RELEASES_URL)
      return { success: true, steps: ['redirected_to_browser'] }
    },

    async waitForReady(maxAttempts = 30, interval = 1000) {
      const baseUrl = await this.getBaseUrl()

      for (let i = 0; i < maxAttempts; i++) {
        try {
          const response = await fetch(`${baseUrl}/api/system/health`, {
            method: 'GET',
            signal: AbortSignal.timeout(2000)
          })
          if (response.ok) return true
        } catch (e) {
          // 服务还没准备好
        }
        await new Promise(resolve => setTimeout(resolve, interval))
      }

      return false
    },

    async healthCheck() {
      const baseUrl = await this.getBaseUrl()
      try {
        const response = await fetch(`${baseUrl}/api/system/health`, {
          method: 'GET',
          signal: AbortSignal.timeout(5000)
        })
        return response.ok
      } catch (e) {
        return false
      }
    },

    async getBaseUrl() {
      // web 环境下：直接使用 config.json 中的 apiBase
      // 如果是 '/' 或空字符串，使用相对路径（同域）
      return await resolveBaseUrl()
    }
  },

  window: {
    minimize() { console.log('Web: minimize not supported') },
    maximize() { console.log('Web: maximize not supported') },
    close() { window.close() },
    async isMaximized() { return false }
  },

  // 打开本地文件（Web环境下尝试使用window.open，可能受浏览器安全限制）
  async openFile(filePath) {
    try {
      // 路径验证
      if (!filePath || typeof filePath !== 'string') {
        return { success: false, error: '无效的文件路径' }
      }
      // 防止路径遍历攻击
      if (filePath.includes('..') || filePath.includes('~')) {
        return { success: false, error: '文件路径包含非法字符' }
      }
      // Web环境下无法直接打开本地文件，尝试使用file://协议
      const fileUrl = filePath.startsWith('file://') ? filePath : `file://${filePath}`
      window.open(fileUrl, '_blank')
      return { success: true }
    } catch (e) {
      console.error('Web: openFile failed:', e)
      return { success: false, error: e.message }
    }
  },

  events: {
    async listen(eventName) {
      console.log(`Web: listen to ${eventName} not supported`)
      return () => {}
    }
  }
}

/**
 * Electron 环境下的实现
 * 通过 preload 脚本暴露的 API 与主进程通信
 */
const electronImplementation = {
  loopraWebService: {
    async getStatus() {
      return await window.electronAPI.loopraWebService.getStatus()
    },
    async getResourceDir() {
      return await window.electronAPI.loopraWebService.getResourceDir()
    },
    async checkInstallNeeded(resourceDir) {
      return await window.electronAPI.loopraWebService.checkInstallNeeded(resourceDir)
    },
    async install(resourceDir) {
      return await window.electronAPI.loopraWebService.install(resourceDir)
    },
    async start() {
      return await window.electronAPI.loopraWebService.start()
    },
    async stop() {
      return await window.electronAPI.loopraWebService.stop()
    },
    async listProcesses() {
      return await window.electronAPI.loopraWebService.listProcesses()
    },
    async openProcess(pid) {
      return await window.electronAPI.loopraWebService.openProcess(pid)
    },
    async terminateProcess(pid) {
      return await window.electronAPI.loopraWebService.terminateProcess(pid)
    },
    async getCurrentPort() {
      return await window.electronAPI.loopraWebService.getCurrentPort()
    },
    async installOnline(source) {
      return await window.electronAPI.loopraWebService.installOnline(source)
    },
    async waitForReady(maxAttempts = 30, interval = 1000) {
      const port = await this.getCurrentPort()
      const baseUrl = `http://127.0.0.1:${port}`
      for (let i = 0; i < maxAttempts; i++) {
        try {
          const response = await fetch(`${baseUrl}/api/system/health`, {
            method: 'GET',
            signal: AbortSignal.timeout(2000)
          })
          if (response.ok) return true
        } catch (e) {
          // 服务还没准备好
        }
        await new Promise(resolve => setTimeout(resolve, interval))
      }
      return false
    },
    async healthCheck() {
      const port = await this.getCurrentPort()
      try {
        const response = await fetch(`http://127.0.0.1:${port}/api/system/health`, {
          method: 'GET',
          signal: AbortSignal.timeout(5000)
        })
        return response.ok
      } catch (e) {
        return false
      }
    },
    async getBaseUrl() {
      const port = await this.getCurrentPort()
      return `http://127.0.0.1:${port}`
    }
  },

  window: {
    minimize() { window.electronAPI.window.minimize() },
    maximize() { window.electronAPI.window.maximize() },
    close() { window.electronAPI.window.close() },
    async isMaximized() {
      return await window.electronAPI.window.isMaximized()
    }
  },

  // 打开本地文件（Electron环境）
  async openFile(filePath) {
    return await window.electronAPI.openFile(filePath)
  },

  events: {
    async listen(eventName, callback) {
      return window.electronAPI.events.listen(eventName, callback)
    }
  }
}

// 根据环境选择实现
function getImplementation() {
  return isElectron ? electronImplementation : webImplementation
}

// 导出统一的 API
export const platform = {
  isElectron,
  isWeb,

  get implementation() {
    return getImplementation()
  }
}

export default platform
