<template>
  <div class="settings-view">
    <!-- 头部 -->
    <div class="settings-header">
      <div class="header-left">
        <div class="header-title">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          <h2>设置</h2>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary btn-sm" @click="resetSettings">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="1 4 1 10 7 10"/>
            <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
          </svg>
          重置默认
        </button>
        <button class="btn btn-primary btn-sm" @click="saveSettings" :disabled="loading">
          <svg v-if="loading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
          {{ loading ? '保存中...' : '保存设置' }}
        </button>
      </div>
    </div>
    
    <!-- 标签页 -->
    <div class="settings-tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-label">{{ tab.label }}</span>
      </button>
    </div>
    
    <!-- 错误提示 -->
    <div v-if="error" class="error-banner">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <line x1="15" y1="9" x2="9" y2="15"/>
        <line x1="9" y1="9" x2="15" y2="15"/>
      </svg>
      <span>{{ error }}</span>
      <button class="btn-icon-sm" @click="error = ''">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    
    <!-- 设置内容 -->
    <div class="settings-content">
      <!-- 基本设置 -->
      <div v-if="activeTab === 'general'" class="settings-section">
        <div class="section-header">
          <h3>基本设置</h3>
          <p>配置界面语言、主题和显示选项</p>
        </div>
        
        <div class="settings-group">
          <div class="setting-item" v-if="false">
            <div class="setting-info">
              <div class="setting-label">语言</div>
              <div class="setting-description">界面显示语言</div>
            </div>
            <div class="setting-control">
              <select v-model="settings.language" class="form-select">
                <option value="zh-CN">简体中文</option>
                <option value="en-US">English</option>
                <option value="ja-JP">日本語</option>
              </select>
            </div>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">主题</div>
              <div class="setting-description">界面主题风格</div>
            </div>
            <div class="setting-control">
              <div class="theme-selector">
                <button 
                  v-for="theme in themes" 
                  :key="theme.value"
                  class="theme-btn"
                  :class="{ active: settings.theme === theme.value }"
                  @click="settings.theme = theme.value"
                >
                  <span class="theme-preview" :style="{ background: theme.color }"></span>
                  <span class="theme-name">{{ theme.label }}</span>
                </button>
              </div>
            </div>
          </div>
          

        </div>
      </div>
      
      <!-- 服务器设置 -->
      <div v-if="activeTab === 'server'" class="settings-section">
        <div class="section-header">
          <h3>服务器设置</h3>
          <p>配置后端 Agent4j 服务的连接地址</p>
        </div>

        <div class="settings-group">
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">后端 API 地址</div>
              <div class="setting-description">留空使用默认代理（localhost:8097）</div>
            </div>
            <div class="setting-control">
              <input
                v-model="settings.server.apiBaseUrl"
                type="url"
                class="form-input"
                placeholder="留空 = 默认 http://localhost:8097"
              />
            </div>
          </div>

          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">连接状态</div>
              <div class="setting-description">测试后端服务是否可达</div>
            </div>
            <div class="setting-control">
              <button class="btn btn-secondary btn-sm" @click="checkServerConnection" :disabled="checkingConnection">
                {{ checkingConnection ? '检测中...' : '检测连接' }}
              </button>
              <span class="connection-status-text" :class="connectionOk ? 'ok' : 'fail'" v-if="connectionChecked">
                {{ connectionOk ? '✓ 连接成功' : '✗ 连接失败' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- AI 设置 -->
      <div v-if="activeTab === 'ai'" class="settings-section">
        <div class="section-header">
          <h3>AI 设置</h3>
          <p>配置AI模型和API连接参数</p>
        </div>
        
        <div class="settings-group">
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">API 地址</div>
              <div class="setting-description">OpenAI 兼容 API 的基础 URL</div>
            </div>
            <div class="setting-control">
              <input 
                v-model="settings.ai.baseUrl" 
                type="url"
                class="form-input"
                placeholder="https://api.openai.com/v1"
              />
            </div>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">API 密钥</div>
              <div class="setting-description">用于身份验证的 API 密钥</div>
            </div>
            <div class="setting-control">
              <div class="password-input">
                <input 
                  v-model="settings.ai.apiKey" 
                  :type="showApiKey ? 'text' : 'password'"
                  class="form-input"
                  placeholder="sk-..."
                />
                <button class="btn-icon-sm toggle-password" @click="showApiKey = !showApiKey">
                  <svg v-if="showApiKey" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                  <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">模型</div>
              <div class="setting-description">使用的 AI 模型</div>
            </div>
            <div class="setting-control">
              <select v-model="settings.ai.model" class="form-select">
                <option 
                  v-for="model in availableModels" 
                  :key="model.name" 
                  :value="model.name"
                >
                  {{ model.name }}
                </option>
              </select>
            </div>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">推理强度</div>
              <div class="setting-description">AI 推理的详细程度</div>
            </div>
            <div class="setting-control">
              <select v-model="settings.ai.reasoningEffort" class="form-select">
                <option value="low">低</option>
                <option value="medium">中</option>
                <option value="high">高</option>
                <option value="max">最大</option>
              </select>
            </div>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">可用模型列表</div>
              <div class="setting-description">每行一个模型名称</div>
            </div>
            <div class="setting-control">
              <textarea
                v-model="settings.ai.availableModelsText"
                class="form-textarea"
                placeholder="deepseek-v4-flash&#10;gpt-4&#10;gpt-4-turbo"
                rows="4"
              ></textarea>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 工作区设置 -->
      <div v-if="activeTab === 'workspace'" class="settings-section">
        <div class="section-header">
          <h3>工作区设置</h3>
          <p>配置工作目录和文件编辑选项</p>
        </div>
        
        <div class="settings-group">
          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">工作区路径</div>
              <div class="setting-description">默认工作目录</div>
            </div>
            <div class="setting-control">
              <input
                v-model="settings.workspace.dir"
                type="text"
                class="form-input"
                placeholder="."
              />
            </div>
          </div>

          <div class="setting-item">
            <div class="setting-info">
              <div class="setting-label">编辑模式</div>
              <div class="setting-description">手动 = 写入操作需审批，自由 = 直接执行</div>
            </div>
            <div class="setting-control">
              <select v-model="settings.workspace.mode" class="form-select">
                <option :value="true">手动</option>
                <option :value="false">自由</option>
              </select>
            </div>
          </div>
          
        </div>
      </div>
    </div>
    
    <!-- 底部 -->
    <div class="settings-footer">
      <div class="footer-info">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="16" x2="12" y2="12"/>
          <line x1="12" y1="8" x2="12.01" y2="8"/>
        </svg>
        <span>配置文件位置: ~/.agent4j/config.json</span>
      </div>
      <div class="footer-actions">
        <button class="btn btn-ghost btn-sm" @click="openConfigFile">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
            <polyline points="13 2 13 9 20 9"/>
          </svg>
          打开配置文件
        </button>
        <button class="btn btn-ghost btn-sm" @click="exportSettings">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          导出配置
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useConfirm } from '../composables/useConfirm'
import { configAPI } from '../services/api'

// 状态
const activeTab = ref('general')
const showApiKey = ref(false)
const loading = ref(false)
const error = ref('')
const availableModels = ref([])
const { confirm } = useConfirm()
const checkingConnection = ref(false)
const connectionOk = ref(false)
const connectionChecked = ref(false)

// 标签页配置
const tabs = [
  { id: 'general', label: '基本设置', icon: '⚙️' },
  { id: 'server', label: '服务器', icon: '🌐' },
  { id: 'ai', label: 'AI 设置', icon: '🤖' },
  { id: 'workspace', label: '工作区', icon: '📁' }
]

// 主题配置
const themes = [
  { value: 'light', label: '浅色', color: '#ffffff' },
  { value: 'dark', label: '深色', color: '#0f172a' }
]

// 设置数据
const settings = reactive({
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
    model: '',
    reasoningEffort: 'max',
    availableModelsText: ''
  },
  
  workspace: {
    dir: '',
    mode: false
  }
})

// 方法
const loadSettings = async () => {
  loading.value = true
  error.value = ''
  
  try {
    // 并行加载配置和模型列表
    const [configResponse, modelsResponse] = await Promise.all([
      configAPI.getConfig(),
      configAPI.getModels()
    ])
    
    if (configResponse.success && configResponse.data) {
      const config = configResponse.data
      
      // 更新服务器设置
      settings.server.apiBaseUrl = config.serverApiBaseUrl || ''

      // 更新AI设置
      settings.ai.baseUrl = config.baseUrl || ''
      // apiKey 后端返回的是脱敏后的值，不加载到输入框
      settings.ai.model = config.model || ''
      settings.ai.reasoningEffort = config.reasoningEffort || 'max'
      // 可用模型列表从后端数组拼成文本
      if (config.availableModels && Array.isArray(config.availableModels)) {
        settings.ai.availableModelsText = config.availableModels.join('\n')
      }
      
      // 更新工作区设置
      settings.workspace.dir = config.workspaceDir || config.workspace || '.'
      // hitl = true 表示手动（需审批），false 表示自由（直接执行）
      settings.workspace.mode = config.hitl === true
      
      // 更新语言设置（后端是 'ZH'/'EN'，前端是 'zh-CN'/'en-US'）
      if (config.lang) {
        settings.language = config.lang === 'ZH' ? 'zh-CN' : 'en-US'
      }
      
      // 仅恢复 UI 本地偏好（主题、字体大小），不覆盖后端配置数据
      const savedPrefs = localStorage.getItem('agent4j-ui-preferences')
      if (savedPrefs) {
        try {
          const prefs = JSON.parse(savedPrefs)
          if (prefs.theme) settings.theme = prefs.theme
          if (prefs.fontSize) settings.fontSize = prefs.fontSize
          if (prefs.animations !== undefined) settings.animations = prefs.animations
        } catch (e) {
          // ignore parse error
        }
      }
    } else {
      error.value = configResponse.error || '加载配置失败'
    }
    
    // 更新可用模型列表
    if (modelsResponse.success && modelsResponse.data) {
      availableModels.value = modelsResponse.data.models || []
    }
  } catch (err) {
    console.error('加载配置失败:', err)
    error.value = '加载配置失败: ' + err.message
    
    // 使用默认值
    settings.ai.baseUrl = 'https://api.deepseek.com/v1'
    settings.ai.model = 'deepseek-v4-flash'
    settings.ai.reasoningEffort = 'max'
    settings.workspace.dir = '.'
    settings.workspace.mode = false
    
    // 默认模型列表
    availableModels.value = [
      { name: 'deepseek-v4-flash', active: true },
      { name: 'gpt-4', active: false },
      { name: 'gpt-4-turbo', active: false },
      { name: 'gpt-3.5-turbo', active: false }
    ]
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  loading.value = true
  error.value = ''
  
  try {
    // 分离 UI 本地偏好和后端配置
    const uiPrefs = {
      theme: settings.theme,
      fontSize: settings.fontSize,
      animations: settings.animations
    }
    localStorage.setItem('agent4j-ui-preferences', JSON.stringify(uiPrefs))

    // 构建要保存到后端的配置
    const configToUpdate = {
      serverApiBaseUrl: settings.server.apiBaseUrl,
      baseUrl: settings.ai.baseUrl,
      apiKey: settings.ai.apiKey,
      model: settings.ai.model,
      reasoningEffort: settings.ai.reasoningEffort,
      availableModels: settings.ai.availableModelsText
        .split('\n')
        .map(s => s.trim())
        .filter(s => s),
      // hitl: true=手动(需审批), false=自由(直接执行)
      hitl: settings.workspace.mode === true,
      lang: settings.language === 'zh-CN' ? 'ZH' : 'EN'
    }
    
    // 调用后端API保存配置
    const response = await configAPI.updateConfig(configToUpdate)
    
    if (response.success) {
      // 应用主题
      applyTheme(settings.theme)
      
      // 如果工作区路径有变化，同步切换运行时工作目录
      if (settings.workspace.dir && settings.workspace.dir.trim()) {
        try {
          await configAPI.switchWorkspace(settings.workspace.dir.trim())
        } catch (e) {
          console.warn('切换工作目录失败:', e)
        }
      }

      // 显示服务端返回的成功消息
      const serverMsg = typeof response.data === 'string'
        ? response.data
        : (response.data?.message || '设置已保存')
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'success', 
          text: serverMsg + (settings.workspace.dir ? '，工作目录已切换' : '') 
        }
      }))
    } else {
      error.value = response.error || '保存失败'
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'error', 
          text: '保存失败: ' + (response.error || '未知错误') 
        }
      }))
    }
  } catch (err) {
    console.error('保存配置失败:', err)
    error.value = '保存失败: ' + err.message
    
    // 降级：UI 偏好已保存到 localStorage，后端配置保存失败提示用户
    applyTheme(settings.theme)
    
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'warning', 
        text: '服务器保存失败，后端配置未更新' 
      }
    }))
  } finally {
    loading.value = false
  }
}

const resetSettings = async () => {
  const ok = await confirm({ message: '确定要重置所有设置为默认值吗？' })
  if (ok) {
    // 重置为默认值
    Object.assign(settings, {
      language: 'zh-CN',
      theme: 'light',
      fontSize: 14,
      animations: true,

      server: {
        apiBaseUrl: '',
        autoConnect: true
      },
      
      ai: {
        baseUrl: 'https://api.deepseek.com/v1',
        apiKey: '',
        model: 'deepseek-v4-flash',
        reasoningEffort: 'max',
        availableModelsText: ''
      },
      
      workspace: {
        dir: '.',
        mode: false
      }
    })
    
    // 清除本地存储的 UI 偏好
    localStorage.removeItem('agent4j-ui-preferences')
    localStorage.removeItem('agent4j-theme')
    
    // 显示消息
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'system', 
        text: '设置已重置为默认值' 
      }
    }))
  }
}

const checkServerConnection = async () => {
  checkingConnection.value = true
  connectionChecked.value = false
  try {
    const baseUrl = settings.server.apiBaseUrl.trim() || '/api'
    const url = baseUrl.endsWith('/api') ? baseUrl + '/agent/status' : baseUrl.replace(/\/+$/, '') + '/api/agent/status'
    const resp = await fetch(url, { signal: AbortSignal.timeout(5000) })
    connectionOk.value = resp.ok
  } catch {
    connectionOk.value = false
  }
  connectionChecked.value = true
  checkingConnection.value = false
}

const exportSettings = () => {
  const exportData = {
    ...settings,
    exportedAt: new Date().toISOString(),
    version: '1.0.0'
  }
  
  const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `agent4j-settings-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '设置已导出' 
    }
  }))
}

const openConfigFile = () => {
  // 这里可以调用后端API打开配置文件
  // 目前只是显示提示
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '配置文件位置: ~/.agent4j/config.json' 
    }
  }))
}

const applyTheme = (theme) => {
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('agent4j-theme', theme)
}

// 监听设置变化
watch(() => settings.theme, (newTheme) => {
  applyTheme(newTheme)
})

// 生命周期 — 每次进入设置页面都从后端重新加载最新配置
onMounted(() => {
  loadSettings()
  
  // 应用保存的 UI 偏好（仅本地，不影响后端配置）
  const savedPrefs = localStorage.getItem('agent4j-ui-preferences')
  if (savedPrefs) {
    try {
      const prefs = JSON.parse(savedPrefs)
      if (prefs.theme) {
        settings.theme = prefs.theme
        applyTheme(prefs.theme)
      }
      if (prefs.fontSize) settings.fontSize = prefs.fontSize
      if (prefs.animations !== undefined) settings.animations = prefs.animations
    } catch (e) {
      // ignore
    }
  } else {
    // 兼容旧版本：从旧 key 迁移
    const savedTheme = localStorage.getItem('agent4j-theme')
    if (savedTheme) {
      settings.theme = savedTheme
      applyTheme(savedTheme)
      localStorage.setItem('agent4j-ui-preferences', JSON.stringify({ theme: savedTheme }))
      localStorage.removeItem('agent4j-theme')
    }
  }
})
</script>

<style scoped>
.settings-view {
  padding: var(--space-6);
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* 头部 */
.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--fg);
}

.header-title svg {
  color: var(--brand-primary);
}

.header-title h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

/* 标签页 */
.settings-tabs {
  display: flex;
  gap: var(--space-1);
  margin-bottom: var(--space-6);
  padding: var(--space-1);
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.settings-tabs::-webkit-scrollbar {
  display: none;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.tab-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

.tab-btn.active {
  background: var(--surface);
  color: var(--brand-primary);
  box-shadow: var(--shadow-sm);
}

.tab-icon {
  font-size: 14px;
}

/* 设置内容 */
.settings-content {
  flex: 1;
  margin-bottom: var(--space-6);
}

.settings-section {
  animation: fadeIn var(--transition-base) ease-out;
}

.section-header {
  margin-bottom: var(--space-6);
}

.section-header h3 {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-1);
}

.section-header p {
  font-size: var(--text-sm);
  color: var(--fg-muted);
}

/* 设置组 */
.settings-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
}

.setting-item:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-sm);
}

.setting-info {
  flex: 1;
  min-width: 0;
}

.setting-label {
  font-size: var(--text-base);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: 0.25rem;
}

.setting-description {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  line-height: 1.5;
}

.setting-control {
  flex-shrink: 0;
  min-width: 200px;
}

/* 表单元素 */
.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg);
  transition: all var(--transition-fast);
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  background: var(--surface);
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  outline: none;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right var(--space-3) center;
  padding-right: var(--space-8);
}

/* 密码输入 */
.password-input {
  position: relative;
  display: flex;
  align-items: center;
}

.password-input .form-input {
  padding-right: var(--space-10);
}

.toggle-password {
  position: absolute;
  right: var(--space-2);
  color: var(--fg-muted);
  transition: color var(--transition-fast);
}

.toggle-password:hover {
  color: var(--fg);
}

/* 滑块控制 */
.slider-control {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.form-slider {
  flex: 1;
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-full);
  outline: none;
  -webkit-appearance: none;
  appearance: none;
}

.form-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  background: var(--brand-primary);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--surface);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
}

.form-slider::-webkit-slider-thumb:hover {
  transform: scale(1.1);
  box-shadow: var(--shadow-md);
}

.form-slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  background: var(--brand-primary);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--surface);
  box-shadow: var(--shadow-sm);
}

.slider-value {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  min-width: 50px;
  text-align: right;
  font-family: var(--font-mono);
}

/* 主题选择器 */
.theme-selector {
  display: flex;
  gap: var(--space-2);
}

.theme-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--bg-secondary);
  border: 2px solid var(--border);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  min-width: 80px;
}

.theme-btn:hover {
  border-color: var(--fg-muted);
}

.theme-btn.active {
  border-color: var(--brand-primary);
  background: var(--accent-soft);
}

.theme-preview {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.theme-name {
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
}

.theme-btn.active .theme-name {
  color: var(--brand-primary);
}

/* 开关 */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 48px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--bg-tertiary);
  transition: var(--transition-fast);
  border-radius: var(--radius-full);
  border: 1px solid var(--border);
}

.toggle-slider:before {
  position: absolute;
  content: '';
  height: 18px;
  width: 18px;
  left: 2px;
  bottom: 2px;
  background-color: var(--fg-muted);
  transition: var(--transition-fast);
  border-radius: 50%;
}

.toggle-switch input:checked + .toggle-slider {
  background-color: var(--accent-soft);
  border-color: var(--brand-primary);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(24px);
  background-color: var(--brand-primary);
}

/* 底部 */
.settings-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  margin-top: auto;
}

.footer-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--fg-muted);
}

.footer-info svg {
  color: var(--fg-muted);
}

.footer-actions {
  display: flex;
  gap: var(--space-2);
}

/* 动画 */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-view {
    padding: var(--space-4);
  }
  
  .settings-header {
    flex-direction: column;
    gap: var(--space-4);
    align-items: flex-start;
  }
  
  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
  
  .settings-tabs {
    flex-wrap: wrap;
  }
  
  .tab-btn {
    flex: 1;
    min-width: calc(50% - var(--space-1));
    justify-content: center;
  }
  
  .setting-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }
  
  .setting-control {
    width: 100%;
    min-width: auto;
  }
  
  .theme-selector {
    flex-wrap: wrap;
  }
  
  .theme-btn {
    min-width: calc(50% - var(--space-1));
  }
  
  .settings-footer {
    flex-direction: column;
    gap: var(--space-3);
    text-align: center;
  }
  
  .footer-actions {
    width: 100%;
    justify-content: center;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .setting-item {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .setting-item:hover {
  border-color: var(--brand-primary-light);
}

[data-theme="dark"] .settings-tabs {
  background: var(--bg-tertiary);
}

[data-theme="dark"] .tab-btn.active {
  background: var(--bg-secondary);
}

[data-theme="dark"] .settings-footer {
  background: var(--bg-tertiary);
  border-color: var(--border);
}

/* 错误提示 */
.error-banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--danger-bg);
  border: 1px solid var(--danger);
  border-radius: var(--radius);
  color: var(--danger);
  font-size: var(--text-sm);
  margin-bottom: var(--space-4);
}

.error-banner svg {
  flex-shrink: 0;
}

.error-banner span {
  flex: 1;
}

.error-banner .btn-icon-sm {
  color: var(--danger);
}

.error-banner .btn-icon-sm:hover {
  background: rgba(220, 38, 38, 0.1);
}
</style>