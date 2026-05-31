<template>
  <a-modal
    :visible="true"
    :footer="null"
    :width="680"
    :bodyStyle="{ padding: 0 }"
    :destroyOnClose="false"
    wrapClassName="settings-modal"
    @cancel="goBack"
  >
    <!-- title 区域放 tab -->
    <template #title>
      <div class="modal-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="modal-tab-btn"
          :class="{ active: activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          <span class="tab-icon">{{ tab.icon }}</span>
          <span>{{ tab.label }}</span>
        </button>
      </div>
    </template>

    <!-- body — 内容区自动滚动 -->
    <!-- 基本设置 -->
    <div v-if="activeTab === 'general'" style="padding: 24px">
      <h4 style="margin: 0 0 4px">基本设置</h4>
      <p style="color: var(--fg-muted); font-size: 13px; margin: 0 0 20px">界面主题与显示选项</p>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">主题</div>
          <div class="setting-description">界面主题风格</div>
        </div>
        <div class="setting-control">
          <a-button
            v-for="theme in themes" 
            :key="theme.value"
            :type="settings.theme === theme.value ? 'primary' : 'default'"
            @click="settings.theme = theme.value"
            style="margin-right: 8px"
          >
            {{ theme.label }}
          </a-button>
        </div>
      </div>
    </div>

    <!-- 服务器设置 -->
    <div v-if="activeTab === 'server'" style="padding: 24px">
      <h4 style="margin: 0 0 4px">服务器设置</h4>
      <p style="color: var(--fg-muted); font-size: 13px; margin: 0 0 20px">配置后端 Agent4j 服务的连接地址</p>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">后端 API 地址</div>
          <div class="setting-description">留空使用默认代理（localhost:8097）</div>
        </div>
        <div class="setting-control">
          <a-input v-model:value="settings.server.apiBaseUrl" placeholder="留空 = 默认 http://localhost:8097" />
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">连接状态</div>
          <div class="setting-description">测试后端服务是否可达</div>
        </div>
        <div class="setting-control">
          <a-button @click="checkServerConnection" :loading="checkingConnection">检测连接</a-button>
          <span style="margin-left: 8px; font-size: 13px;" :style="{ color: connectionOk ? '#52c41a' : '#ff4d4f' }" v-if="connectionChecked">
            {{ connectionOk ? '✓ 连接成功' : '✗ 连接失败' }}
          </span>
        </div>
      </div>
    </div>

    <!-- AI 设置 -->
    <div v-if="activeTab === 'ai'" style="padding: 24px">
      <h4 style="margin: 0 0 4px">AI 模型设置</h4>
      <p style="color: var(--fg-muted); font-size: 13px; margin: 0 0 20px">配置 LLM API 连接与模型参数</p>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">API 地址</div>
          <div class="setting-description">OpenAI 兼容 API 的基础 URL</div>
        </div>
        <div class="setting-control">
          <a-input v-model:value="settings.ai.baseUrl" placeholder="https://api.openai.com/v1" />
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">API 密钥</div>
          <div class="setting-description">用于身份验证的 API 密钥</div>
        </div>
        <div class="setting-control">
          <a-input-password v-model:value="settings.ai.apiKey" :visible="showApiKey" @update:visible="showApiKey = $event" placeholder="sk-..." />
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">模型</div>
          <div class="setting-description">使用的 AI 模型</div>
        </div>
        <div class="setting-control">
          <a-select v-model:value="settings.ai.model" style="width: 200px">
            <a-select-option v-for="model in availableModels" :key="model.name" :value="model.name">{{ model.name }}</a-select-option>
          </a-select>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">推理强度</div>
          <div class="setting-description">AI 推理的详细程度</div>
        </div>
        <div class="setting-control">
          <a-select v-model:value="settings.ai.reasoningEffort" style="width: 200px">
            <a-select-option value="low">低</a-select-option>
            <a-select-option value="medium">中</a-select-option>
            <a-select-option value="high">高</a-select-option>
            <a-select-option value="max">最大</a-select-option>
          </a-select>
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">可用模型列表</div>
          <div class="setting-description">每行一个模型名称</div>
        </div>
        <div class="setting-control">
          <a-textarea v-model:value="settings.ai.availableModelsText" placeholder="deepseek-v4-flash&#10;gpt-4&#10;gpt-4-turbo" :rows="4" />
        </div>
      </div>
    </div>

    <!-- 工作区设置 -->
    <div v-if="activeTab === 'workspace'" style="padding: 24px">
      <h4 style="margin: 0 0 4px">工作区设置</h4>
      <p style="color: var(--fg-muted); font-size: 13px; margin: 0 0 20px">配置工作目录与编辑行为</p>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">工作区路径</div>
          <div class="setting-description">默认工作目录</div>
        </div>
        <div class="setting-control">
          <a-input v-model:value="settings.workspace.dir" placeholder="." />
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">编辑模式</div>
          <div class="setting-description">手动 = 写入操作需审批，自由 = 直接执行</div>
        </div>
        <div class="setting-control">
          <a-select v-model:value="settings.workspace.mode" style="width: 200px">
            <a-select-option :value="true">手动</a-select-option>
            <a-select-option :value="false">自由</a-select-option>
          </a-select>
        </div>
      </div>
    </div>

    <!-- footer — 按钮固定在底部 -->
    <template #footer>
      <div class="modal-footer-bar">
        <a-space>
          <a-button size="small" @click="openConfigFile"><FileOutlined /> 配置文件</a-button>
          <a-button size="small" @click="exportSettings"><DownloadOutlined /> 导出</a-button>
        </a-space>
        <a-button type="primary" @click="saveSettings" :loading="loading">
          <template #icon><SaveOutlined /></template>
          {{ loading ? '保存中…' : '保存设置' }}
        </a-button>
      </div>
    </template>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SaveOutlined, DownloadOutlined, FileOutlined } from '@ant-design/icons-vue'
import { configAPI } from '../services/api'

const router = useRouter()
const goBack = () => router.back()

const activeTab = ref('general')
const showApiKey = ref(false)
const loading = ref(false)
const availableModels = ref([])
const checkingConnection = ref(false)
const connectionOk = ref(false)
const connectionChecked = ref(false)

const tabs = [
  { id: 'general', label: '基本设置', icon: '⚙️' },
  { id: 'server', label: '服务器', icon: '🌐' },
  { id: 'ai', label: 'AI 设置', icon: '🤖' },
  { id: 'workspace', label: '工作区', icon: '📁' }
]

const themes = [
  { value: 'light', label: '浅色', color: '#ffffff' },
  { value: 'dark', label: '深色', color: '#0f172a' }
]

const settings = reactive({
  language: 'zh-CN',
  theme: 'light',
  fontSize: 14,
  animations: true,
  server: { apiBaseUrl: '', autoConnect: true },
  ai: { baseUrl: '', apiKey: '', model: '', reasoningEffort: 'max', availableModelsText: '' },
  workspace: { dir: '', mode: false }
})

const loadSettings = async () => {
  loading.value = true
  try {
    const [configResponse, modelsResponse] = await Promise.all([
      configAPI.getConfig(),
      configAPI.getModels()
    ])
    if (configResponse.success && configResponse.data) {
      const config = configResponse.data
      settings.server.apiBaseUrl = config.serverApiBaseUrl || ''
      settings.ai.baseUrl = config.baseUrl || ''
      settings.ai.model = config.model || ''
      settings.ai.reasoningEffort = config.reasoningEffort || 'max'
      if (config.availableModels && Array.isArray(config.availableModels)) {
        settings.ai.availableModelsText = config.availableModels.join('\n')
      }
      settings.workspace.dir = config.workspaceDir || config.workspace || '.'
      settings.workspace.mode = config.hitl === true
      if (config.lang) {
        settings.language = config.lang === 'ZH' ? 'zh-CN' : 'en-US'
      }
      const savedPrefs = localStorage.getItem('agent4j-ui-preferences')
      if (savedPrefs) {
        try {
          const prefs = JSON.parse(savedPrefs)
          if (prefs.theme) settings.theme = prefs.theme
          if (prefs.fontSize) settings.fontSize = prefs.fontSize
          if (prefs.animations !== undefined) settings.animations = prefs.animations
        } catch (e) { /* ignore */ }
      }
    } else {
      message.error(configResponse.error || '加载配置失败')
    }
    if (modelsResponse.success && modelsResponse.data) {
      availableModels.value = modelsResponse.data.models || []
    }
  } catch (err) {
    console.error('加载配置失败:', err)
    message.error('加载配置失败: ' + err.message)
    settings.ai.baseUrl = 'https://api.deepseek.com/v1'
    settings.ai.model = 'deepseek-v4-flash'
    settings.ai.reasoningEffort = 'max'
    settings.workspace.dir = '.'
    settings.workspace.mode = false
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
    const uiPrefs = { theme: settings.theme, fontSize: settings.fontSize, animations: settings.animations }
    localStorage.setItem('agent4j-ui-preferences', JSON.stringify(uiPrefs))
    const configToUpdate = {
      serverApiBaseUrl: settings.server.apiBaseUrl,
      baseUrl: settings.ai.baseUrl,
      apiKey: settings.ai.apiKey,
      model: settings.ai.model,
      reasoningEffort: settings.ai.reasoningEffort,
      availableModels: settings.ai.availableModelsText.split('\n').map(s => s.trim()).filter(s => s),
      hitl: settings.workspace.mode === true,
      lang: settings.language === 'zh-CN' ? 'ZH' : 'EN'
    }
    const response = await configAPI.updateConfig(configToUpdate)
    if (response.success) {
      applyTheme(settings.theme)
      if (settings.workspace.dir && settings.workspace.dir.trim()) {
        try { await configAPI.switchWorkspace(settings.workspace.dir.trim()) } catch (e) { console.warn('切换工作目录失败:', e) }
      }
      const serverMsg = typeof response.data === 'string' ? response.data : (response.data?.message || '设置已保存')
      message.success(serverMsg + (settings.workspace.dir ? '，工作目录已切换' : ''))
    } else {
      message.error(response.error || '保存失败')
    }
  } catch (err) {
    console.error('保存配置失败:', err)
    message.error('保存失败: ' + err.message)
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
  } catch { connectionOk.value = false }
  connectionChecked.value = true
  checkingConnection.value = false
}

const exportSettings = () => {
  const blob = new Blob([JSON.stringify({ ...settings, exportedAt: new Date().toISOString() }, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `agent4j-settings-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(blob)
  message.success('设置已导出')
}

const openConfigFile = () => { message.info('配置文件: ~/.agent4j/config.json') }

const applyTheme = (theme) => {
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('agent4j-theme', theme)
}

watch(() => settings.theme, (t) => applyTheme(t))

onMounted(() => {
  loadSettings()
  const savedPrefs = localStorage.getItem('agent4j-ui-preferences')
  if (savedPrefs) {
    try {
      const prefs = JSON.parse(savedPrefs)
      if (prefs.theme) { settings.theme = prefs.theme; applyTheme(prefs.theme) }
      if (prefs.fontSize) settings.fontSize = prefs.fontSize
      if (prefs.animations !== undefined) settings.animations = prefs.animations
    } catch (e) { /* ignore */ }
  } else {
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
.modal-tabs {
  display: flex;
  gap: 4px;
}
.modal-tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 14px;
  color: var(--fg-secondary);
  cursor: pointer;
  transition: all 0.2s;
}
.modal-tab-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}
.modal-tab-btn.active {
  background: var(--brand-primary);
  color: #fff;
}
.tab-icon {
  font-size: 16px;
}
.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 12px;
}
.setting-item:hover {
  border-color: var(--border-focus);
}
.setting-info {
  flex: 1;
  min-width: 0;
}
.setting-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 2px;
}
.setting-description {
  font-size: 12px;
  color: var(--fg-muted);
}
.setting-control {
  flex-shrink: 0;
  min-width: 200px;
  display: flex;
  align-items: center;
}
.modal-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
[data-theme="dark"] .setting-item {
  background: var(--bg-secondary);
}
</style>
