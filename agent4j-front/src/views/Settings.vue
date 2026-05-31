<template>
  <div class="settings-view">
    <!-- 头部 -->
    <div class="settings-header">
      <span class="settings-title">设置</span>
    </div>
    
    <!-- antdv tabs 接管切换 -->
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="general" tab="⚙️ 基本设置">
        <a-form layout="vertical">
          <a-form-item label="主题" help="界面主题风格">
            <a-button-group>
              <a-button
                v-for="theme in themes" 
                :key="theme.value"
                :type="settings.theme === theme.value ? 'primary' : 'default'"
                @click="settings.theme = theme.value"
              >
                {{ theme.label }}
              </a-button>
            </a-button-group>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="server" tab="🌐 服务器">
        <a-form layout="vertical">
          <a-form-item label="后端 API 地址" help="留空使用默认代理（localhost:8097）">
            <a-input v-model:value="settings.server.apiBaseUrl" placeholder="留空 = 默认 http://localhost:8097" />
          </a-form-item>
          <a-form-item label="连接状态">
            <a-button @click="checkServerConnection" :loading="checkingConnection">检测连接</a-button>
            <span style="margin-left: 8px; font-size: 13px;" :style="{ color: connectionOk ? '#52c41a' : '#ff4d4f' }" v-if="connectionChecked">
              {{ connectionOk ? '✓ 连接成功' : '✗ 连接失败' }}
            </span>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="ai" tab="🤖 AI 设置">
        <a-form layout="vertical">
          <a-form-item label="API 地址" help="OpenAI 兼容 API 的基础 URL">
            <a-input v-model:value="settings.ai.baseUrl" placeholder="https://api.openai.com/v1" />
          </a-form-item>
          <a-form-item label="API 密钥" help="用于身份验证的 API 密钥">
            <a-input-password v-model:value="settings.ai.apiKey" :visible="showApiKey" @update:visible="showApiKey = $event" placeholder="sk-..." />
          </a-form-item>
          <a-form-item label="模型" help="使用的 AI 模型">
            <a-select v-model:value="settings.ai.model" style="width: 240px">
              <a-select-option v-for="model in availableModels" :key="model.name" :value="model.name">{{ model.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="推理强度" help="AI 推理的详细程度">
            <a-select v-model:value="settings.ai.reasoningEffort" style="width: 240px">
              <a-select-option value="low">低</a-select-option>
              <a-select-option value="medium">中</a-select-option>
              <a-select-option value="high">高</a-select-option>
              <a-select-option value="max">最大</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="可用模型列表" help="每行一个模型名称">
            <a-textarea v-model:value="settings.ai.availableModelsText" placeholder="deepseek-v4-flash&#10;gpt-4&#10;gpt-4-turbo" :rows="4" style="max-width: 400px;" />
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="workspace" tab="📁 工作区">
        <a-form layout="vertical">
          <a-form-item label="工作区路径" help="默认工作目录">
            <a-input v-model:value="settings.workspace.dir" placeholder="." />
          </a-form-item>
          <a-form-item label="编辑模式" help="手动 = 写入操作需审批，自由 = 直接执行">
            <a-select v-model:value="settings.workspace.mode" style="width: 240px">
              <a-select-option :value="true">手动</a-select-option>
              <a-select-option :value="false">自由</a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </a-tab-pane>
    </a-tabs>
    
    <!-- 底部 -->
    <div class="settings-footer">
      <div class="footer-actions">
        <a-button size="small" @click="openConfigFile">
          <template #icon><FileOutlined /></template>
          打开配置文件
        </a-button>
        <a-button size="small" @click="exportSettings">
          <template #icon><DownloadOutlined /></template>
          导出配置
        </a-button>
        <a-button type="primary" @click="saveSettings" :loading="loading" style="margin-left: auto;">
          <template #icon><SaveOutlined /></template>
          {{ loading ? '保存中...' : '保存设置' }}
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SaveOutlined, DownloadOutlined, FileOutlined } from '@ant-design/icons-vue'
import { configAPI } from '../services/api'

// 状态
const activeTab = ref('general')
const showApiKey = ref(false)
const loading = ref(false)
const availableModels = ref([])
const checkingConnection = ref(false)
const connectionOk = ref(false)
const connectionChecked = ref(false)

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
      message.error(configResponse.error || '加载配置失败')
    }
    
    // 更新可用模型列表
    if (modelsResponse.success && modelsResponse.data) {
      availableModels.value = modelsResponse.data.models || []
    }
  } catch (err) {
    console.error('加载配置失败:', err)
    message.error('加载配置失败: ' + err.message)
    
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
      message.success(serverMsg + (settings.workspace.dir ? '，工作目录已切换' : ''))
    } else {
      message.error(response.error || '保存失败')
    }
  } catch (err) {
    console.error('保存配置失败:', err)
    message.error('保存失败: ' + err.message)
    
    // 降级：UI 偏好已保存到 localStorage，后端配置保存失败提示用户
    applyTheme(settings.theme)
  } finally {
    loading.value = false
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
  
  message.success('设置已导出')
}

const openConfigFile = () => {
  // 这里可以调用后端API打开配置文件
  // 目前只是显示提示
  message.info('配置文件位置: ~/.agent4j/config.json')
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
  margin-bottom: var(--space-6);
}

/* 底部 — 保存按钮在右下角 */
.settings-footer {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  margin-top: auto;
}

.footer-actions {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  width: 100%;
}
</style>