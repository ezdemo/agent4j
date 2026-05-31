<template>
  <a-layout class="settings-layout">
    <!-- 左侧导航 -->
    <a-layout-sider width="200" theme="light" class="settings-sider">
      <div class="sider-title">设置</div>
      <a-menu
        v-model:selectedKeys="activeKey"
        mode="inline"
        @click="onMenuClick"
      >
        <a-menu-item key="general">
          <setting-outlined />
          <span>基本</span>
        </a-menu-item>
        <a-menu-item key="server">
          <cloud-server-outlined />
          <span>服务器</span>
        </a-menu-item>
        <a-menu-item key="ai">
          <robot-outlined />
          <span>AI 模型</span>
        </a-menu-item>
        <a-menu-item key="workspace">
          <folder-outlined />
          <span>工作区</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <!-- 右侧内容 -->
    <a-layout-content class="settings-content">
      <!-- ========== 基本设置 ========== -->
      <div v-show="activeKey[0] === 'general'">
        <a-typography-title :level="5">基本设置</a-typography-title>
        <a-typography-paragraph type="secondary">界面主题与显示选项</a-typography-paragraph>

        <a-card size="small" class="setting-card" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="主题">
              <a-radio-group v-model:value="settings.theme" button-style="solid">
                <a-radio-button value="light">☀️ 浅色</a-radio-button>
                <a-radio-button value="dark">🌙 深色</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </a-form>
        </a-card>
      </div>

      <!-- ========== 服务器设置 ========== -->
      <div v-show="activeKey[0] === 'server'">
        <a-typography-title :level="5">服务器设置</a-typography-title>
        <a-typography-paragraph type="secondary">配置后端 Agent4j 服务的连接地址</a-typography-paragraph>

        <a-card size="small" class="setting-card" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="后端 API 地址">
              <a-input v-model:value="settings.server.apiBaseUrl" placeholder="留空 = http://localhost:8097" />
            </a-form-item>
            <a-form-item label="连接状态">
              <a-space>
                <a-button @click="checkServerConnection" :loading="checkingConnection">检测连接</a-button>
                <a-tag v-if="connectionChecked" :color="connectionOk ? 'success' : 'error'">
                  {{ connectionOk ? '连接成功' : '连接失败' }}
                </a-tag>
              </a-space>
            </a-form-item>
          </a-form>
        </a-card>
      </div>

      <!-- ========== AI 设置 ========== -->
      <div v-show="activeKey[0] === 'ai'">
        <a-typography-title :level="5">AI 模型设置</a-typography-title>
        <a-typography-paragraph type="secondary">配置 LLM API 连接与模型参数</a-typography-paragraph>

        <a-card size="small" class="setting-card" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="API 地址">
              <a-input v-model:value="settings.ai.baseUrl" placeholder="https://api.openai.com/v1" />
            </a-form-item>
            <a-form-item label="API 密钥">
              <a-input-password v-model:value="settings.ai.apiKey" placeholder="sk-..." />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="模型">
                  <a-select v-model:value="settings.ai.model" style="width: 100%">
                    <a-select-option v-for="m in availableModels" :key="m.name" :value="m.name">{{ m.name }}</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="推理强度">
                  <a-select v-model:value="settings.ai.reasoningEffort" style="width: 100%">
                    <a-select-option value="low">低</a-select-option>
                    <a-select-option value="medium">中</a-select-option>
                    <a-select-option value="high">高</a-select-option>
                    <a-select-option value="max">最大</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="可用模型列表（每行一个）">
              <a-textarea v-model:value="settings.ai.availableModelsText" placeholder="deepseek-v4-flash&#10;gpt-4o" :rows="3" style="max-width: 400px;" />
            </a-form-item>
          </a-form>
        </a-card>
      </div>

      <!-- ========== 工作区设置 ========== -->
      <div v-show="activeKey[0] === 'workspace'">
        <a-typography-title :level="5">工作区设置</a-typography-title>
        <a-typography-paragraph type="secondary">配置工作目录与编辑行为</a-typography-paragraph>

        <a-card size="small" class="setting-card" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="工作区路径">
              <a-input v-model:value="settings.workspace.dir" placeholder="." />
            </a-form-item>
            <a-form-item label="编辑模式">
              <a-radio-group v-model:value="settings.workspace.mode" button-style="solid">
                <a-radio-button :value="true">🛡️ 手动（需审批）</a-radio-button>
                <a-radio-button :value="false">⚡ 自由（直接执行）</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </a-form>
        </a-card>
      </div>
    </a-layout-content>

    <!-- 底部操作栏 -->
    <div class="settings-bottom-bar">
      <a-space>
        <a-button size="small" @click="openConfigFile">
          <template #icon><FileOutlined /></template>
          配置文件
        </a-button>
        <a-button size="small" @click="exportSettings">
          <template #icon><DownloadOutlined /></template>
          导出
        </a-button>
      </a-space>
      <a-button type="primary" @click="saveSettings" :loading="loading" size="large" class="save-btn">
        <template #icon><SaveOutlined /></template>
        {{ loading ? '保存中…' : '保存设置' }}
      </a-button>
    </div>
  </a-layout>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  SaveOutlined, DownloadOutlined, FileOutlined,
  SettingOutlined, CloudServerOutlined, RobotOutlined, FolderOutlined
} from '@ant-design/icons-vue'
import { configAPI } from '../services/api'

const activeKey = ref(['general'])
const showApiKey = ref(false)
const loading = ref(false)
const availableModels = ref([])
const checkingConnection = ref(false)
const connectionOk = ref(false)
const connectionChecked = ref(false)

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

const onMenuClick = ({ key }) => { activeKey.value = [key] }

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
      message.success(serverMsg)
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
.settings-layout {
  height: 100%;
  background: transparent;
}

/* 左侧导航 */
.settings-sider {
  border-right: 1px solid var(--border);
  background: var(--bg-secondary);
  overflow: auto;
}

.sider-title {
  padding: 20px 24px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.settings-sider :deep(.ant-menu) {
  border-inline-end: none !important;
  background: transparent;
}

/* 右侧内容 */
.settings-content {
  padding: 28px 32px;
  overflow-y: auto;
  flex: 1;
}

.setting-card {
  margin-top: 16px;
  background: var(--surface);
  border-radius: 8px;
  max-width: 640px;
}

.setting-card + .setting-card {
  margin-top: 12px;
}

/* 底部操作栏 */
.settings-bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
}

.save-btn {
  min-width: 140px;
}
</style>
