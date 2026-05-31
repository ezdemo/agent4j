/**
 * Tauri 专用 API - 管理 agent4j-web 后端服务
 */

import { invoke } from '@tauri-apps/api/core'

// 直接尝试 invoke，失败说明不在 Tauri 环境或命令不可用
async function tryInvoke(command, args) {
  try {
    return await invoke(command, args)
  } catch {
    return undefined  // invoke 失败 = 非 Tauri 环境
  }
}

// 获取当前后端端口（始终优先从 Rust 侧获取最新端口）
async function getCurrentPort() {
  // 1) 先尝试从 Rust 获取（保证是最新的）
  const port = await tryInvoke('get_agent4j_web_port')
  if (port > 0) {
    localStorage.setItem('agent4j-port', String(port))
    return port
  }

  // 2) 非 Tauri：从 localStorage 取（之前连接成功时保存的）
  const stored = parseInt(localStorage.getItem('agent4j-port') || '', 10)
  if (stored > 0) return stored

  // 3) 回退到默认端口
  return 8097
}

// agent4j-web 服务管理 API
export const agent4jWebService = {
  /**
   * 获取 agent4j-web 状态
   */
  async getStatus() {
    const status = await tryInvoke('get_agent4j_web_status')
    if (status) return status

    // 非 Tauri：假设服务已在外部启动
    return {
      installed: true,
      running: true,
      install_dir: '~/.agent4j'
    }
  },

  /**
   * 启动 agent4j-web 服务（返回端口号）
   * @returns {Promise<number>} 端口号
   */
  async start() {
    const port = await tryInvoke('start_agent4j_web')
    if (port > 0) {
      console.log('Agent4j Web started on port:', port)
      localStorage.setItem('agent4j-port', String(port))
      return port
    }
    console.warn('Not in Tauri environment, skipping start')
    return 0
  },

  /**
   * 停止 agent4j-web 服务
   */
  async stop() {
    const result = await tryInvoke('stop_agent4j_web')
    if (result !== undefined) {
      console.log('Agent4j Web stopped')
      return
    }
    console.warn('Not in Tauri environment, skipping stop')
  },

  /**
   * 等待服务就绪（轮询检查，端口动态）
   * @param {number} maxAttempts - 最大尝试次数
   * @param {number} interval - 每次尝试间隔（毫秒）
   * @returns {Promise<boolean>}
   */
  async waitForReady(maxAttempts = 30, interval = 1000) {
    const port = await getCurrentPort()
    const baseUrl = `http://127.0.0.1:${port}`

    for (let i = 0; i < maxAttempts; i++) {
      try {
        const response = await fetch(`${baseUrl}/api/system/health`, {
          method: 'GET',
          signal: AbortSignal.timeout(2000)
        })
        
        if (response.ok) {
          console.log('Agent4j Web is ready on port', port)
          return true
        }
      } catch (e) {
        // 服务还没准备好，继续等待
      }
      
      await new Promise(resolve => setTimeout(resolve, interval))
      console.log(`Waiting for Agent4j Web... (${i + 1}/${maxAttempts})`)
    }
    
    console.error('Agent4j Web failed to start within timeout')
    return false
  },

  /**
   * 检查服务健康状态
   * @returns {Promise<boolean>}
   */
  async healthCheck() {
    const port = await getCurrentPort()
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

  /**
   * 获取服务 API 基础地址
   * @returns {Promise<string>}
   */
  async getBaseUrl() {
    const port = await getCurrentPort()
    return `http://127.0.0.1:${port}`
  }
}

// 导出默认对象
export default {
  agent4jWebService
}
