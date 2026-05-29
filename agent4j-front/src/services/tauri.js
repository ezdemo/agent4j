/**
 * Tauri 专用 API - 管理 agent4j-web 后端服务
 */

import { invoke } from '@tauri-apps/api/core'

// 检查是否在 Tauri 环境中运行
export function isTauriEnvironment() {
  return window.__TAURI__ !== undefined
}

// agent4j-web 服务管理 API
export const agent4jWebService = {
  /**
   * 获取 agent4j-web 状态
   * @returns {Promise<{installed: boolean, running: boolean, install_dir: string}>}
   */
  async getStatus() {
    if (!isTauriEnvironment()) {
      // 非 Tauri 环境，假设服务已在外部启动
      return {
        installed: true,
        running: true,
        install_dir: '~/.agent4j'
      }
    }
    
    try {
      return await invoke('get_agent4j_web_status')
    } catch (error) {
      console.error('Failed to get agent4j-web status:', error)
      return {
        installed: false,
        running: false,
        install_dir: '',
        error: error
      }
    }
  },

  /**
   * 启动 agent4j-web 服务
   * @returns {Promise<number>} 进程 PID
   */
  async start() {
    if (!isTauriEnvironment()) {
      console.warn('Not in Tauri environment, skipping start')
      return 0
    }
    
    try {
      const pid = await invoke('start_agent4j_web')
      console.log('Agent4j Web started with PID:', pid)
      return pid
    } catch (error) {
      console.error('Failed to start agent4j-web:', error)
      throw error
    }
  },

  /**
   * 停止 agent4j-web 服务
   */
  async stop() {
    if (!isTauriEnvironment()) {
      console.warn('Not in Tauri environment, skipping stop')
      return
    }
    
    try {
      await invoke('stop_agent4j_web')
      console.log('Agent4j Web stopped')
    } catch (error) {
      console.error('Failed to stop agent4j-web:', error)
      throw error
    }
  },

  /**
   * 等待服务就绪（轮询检查）
   * @param {number} maxAttempts - 最大尝试次数
   * @param {number} interval - 每次尝试间隔（毫秒）
   * @returns {Promise<boolean>}
   */
  async waitForReady(maxAttempts = 30, interval = 1000) {
    for (let i = 0; i < maxAttempts; i++) {
      try {
        const response = await fetch('http://localhost:8097/api/health', {
          method: 'GET',
          signal: AbortSignal.timeout(2000)
        })
        
        if (response.ok) {
          console.log('Agent4j Web is ready')
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
    try {
      const response = await fetch('http://localhost:8097/api/health', {
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
   * @returns {string}
   */
  getBaseUrl() {
    return 'http://localhost:8097'
  }
}

// 导出默认对象
export default {
  isTauriEnvironment,
  agent4jWebService
}
