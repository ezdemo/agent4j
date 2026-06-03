/**
 * Tauri 专用 API - 管理 agent4j-web 后端服务
 */

import { invoke } from '@tauri-apps/api/core'

// 用于"探测" Tauri 环境——未确认 Tauri 环境时，优雅降级
async function tryInvoke(command, args) {
  try {
    return await invoke(command, args)
  } catch {
    return undefined  // invoke 失败 = 非 Tauri 环境或命令不可用
  }
}

// 用于"已确认 Tauri 环境"的场景——必须透传真实错误
async function mustInvoke(command, args) {
  return await invoke(command, args)
}

// 获取当前后端端口（Rust 动态分配，不再硬编码 8097）
async function getCurrentPort() {
  const port = await tryInvoke('get_agent4j_web_port')
  if (port > 0) {
    localStorage.setItem('agent4j-port', String(port))
    return port
  }

  const stored = parseInt(localStorage.getItem('agent4j-port') || '', 10)
  if (stored > 0) return stored

  return 0
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
   * 获取资源目录路径（Tauri 桌面端专用）
   * @returns {Promise<string|null>}
   */
  async getResourceDir() {
    return await tryInvoke('get_resource_dir')
  },

  /**
   * 检查是否需要安装（比对 jar hash）
   * @param {string} resourceDir - 资源目录路径
   * @returns {Promise<{needed: boolean, reason: string}>}
   */
  async checkInstallNeeded(resourceDir) {
    const result = await tryInvoke('check_install_needed', { resourceDir })
    if (result) return result

    // 非 Tauri 环境：假设已安装
    return { needed: false, reason: 'not_tauri' }
  },

  /**
   * 执行安装（解压 tar.gz、复制文件、创建脚本）
   * 注意：安装流程只在已确认的 Tauri 环境下调用，
   * 必须使用 mustInvoke 让真实错误透传，不要吞掉异常。
   * @param {string} resourceDir - 资源目录路径
   * @returns {Promise<{success: boolean, steps: string[]}>}
   */
  async install(resourceDir) {
    // 这里用 mustInvoke 替代 tryInvoke，确保 Rust 端的真实错误能透传给前端
    return await mustInvoke('install_agent4j_web', { resourceDir })
  },

  /**
   * 启动 agent4j-web 服务（随机端口，返回端口号）
   * 注意：同样使用 mustInvoke 透传真实错误
   * @returns {Promise<number>}
   */
  async start() {
    const port = await mustInvoke('start_agent4j_web')
    console.log('Agent4j Web started on port:', port)
    localStorage.setItem('agent4j-port', String(port))
    return port
  },

  /**
   * 步骤1：检查 Java 环境
   */
  async step1CheckJava(resourceDir) {
    return await mustInvoke('install_step1_check_java', { resourceDir })
  },

  /**
   * 步骤2：解压安装包
   */
  async step2Extract(resourceDir) {
    return await mustInvoke('install_step2_extract', { resourceDir })
  },

  /**
   * 步骤3：复制文件
   */
  async step3CopyFiles(resourceDir) {
    return await mustInvoke('install_step3_copy_files', { resourceDir })
  },

  /**
   * 步骤4：配置环境
   */
  async step4ConfigureEnv(resourceDir) {
    return await mustInvoke('install_step4_configure_env', { resourceDir })
  },

  /**
   * 停止 agent4j-web 服务
   */
  async stop() {
    await mustInvoke('stop_agent4j_web')
    console.log('Agent4j Web stopped')
  },

  /**
   * 等待服务就绪（轮询检查，端口动态）
   * @param {number} maxAttempts
   * @param {number} interval
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
        // 服务还没准备好
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

export default {
  agent4jWebService
}
