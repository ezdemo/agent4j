<template>
  <div class="settings-view">
    <div class="settings-header">
      <h2>设置终端</h2>
      <div class="settings-controls">
        <button class="terminal-button" @click="saveSettings">保存设置</button>
        <button class="terminal-button" @click="resetSettings">重置默认</button>
        <button class="terminal-button" @click="exportSettings">导出配置</button>
      </div>
    </div>
    
    <div class="settings-tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        @click="activeTab = tab.id"
        :class="['tab-button', { active: activeTab === tab.id }]"
      >
        {{ tab.label }}
      </button>
    </div>
    
    <div class="settings-content">
      <!-- 基本设置 -->
      <div v-if="activeTab === 'general'" class="settings-section">
        <h3>基本设置</h3>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">语言</span>
            <span class="label-description">界面显示语言</span>
          </div>
          <select v-model="settings.language" class="terminal-input">
            <option value="zh-CN">简体中文</option>
            <option value="en-US">English</option>
            <option value="ja-JP">日本語</option>
          </select>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">主题</span>
            <span class="label-description">界面主题风格</span>
          </div>
          <select v-model="settings.theme" class="terminal-input">
            <option value="retro-green">复古绿</option>
            <option value="retro-amber">复古琥珀</option>
            <option value="retro-blue">复古蓝</option>
            <option value="dark">深色</option>
            <option value="light">浅色</option>
          </select>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">字体大小</span>
            <span class="label-description">界面字体大小</span>
          </div>
          <div class="slider-container">
            <input 
              type="range" 
              v-model="settings.fontSize" 
              min="12" 
              max="24" 
              class="slider"
            />
            <span class="slider-value">{{ settings.fontSize }}px</span>
          </div>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">动画效果</span>
            <span class="label-description">启用界面动画效果</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.animations" />
            <span class="slider"></span>
          </label>
        </div>
      </div>
      
      <!-- AI 设置 -->
      <div v-if="activeTab === 'ai'" class="settings-section">
        <h3>AI 设置</h3>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">API 地址</span>
            <span class="label-description">OpenAI 兼容 API 的基础 URL</span>
          </div>
          <input 
            v-model="settings.ai.baseUrl" 
            class="terminal-input"
            placeholder="https://api.openai.com/v1"
          />
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">API 密钥</span>
            <span class="label-description">用于身份验证的 API 密钥</span>
          </div>
          <div class="password-input">
            <input 
              v-model="settings.ai.apiKey" 
              :type="showApiKey ? 'text' : 'password'"
              class="terminal-input"
              placeholder="sk-..."
            />
            <button class="toggle-password" @click="showApiKey = !showApiKey">
              {{ showApiKey ? '🙈' : '👁️' }}
            </button>
          </div>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">模型</span>
            <span class="label-description">使用的 AI 模型</span>
          </div>
          <select v-model="settings.ai.model" class="terminal-input">
            <option value="gpt-4">GPT-4</option>
            <option value="gpt-4-turbo">GPT-4 Turbo</option>
            <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
            <option value="deepseek-v4-flash">DeepSeek V4 Flash</option>
            <option value="ollama">Ollama (本地)</option>
          </select>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">推理强度</span>
            <span class="label-description">AI 推理的详细程度</span>
          </div>
          <select v-model="settings.ai.reasoningEffort" class="terminal-input">
            <option value="low">低</option>
            <option value="medium">中</option>
            <option value="high">高</option>
            <option value="max">最大</option>
          </select>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">温度</span>
            <span class="label-description">生成随机性控制 (0-2)</span>
          </div>
          <div class="slider-container">
            <input 
              type="range" 
              v-model="settings.ai.temperature" 
              min="0" 
              max="2" 
              step="0.1"
              class="slider"
            />
            <span class="slider-value">{{ settings.ai.temperature }}</span>
          </div>
        </div>
      </div>
      
      <!-- 工作区设置 -->
      <div v-if="activeTab === 'workspace'" class="settings-section">
        <h3>工作区设置</h3>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">工作区路径</span>
            <span class="label-description">默认工作目录</span>
          </div>
          <input 
            v-model="settings.workspace.dir" 
            class="terminal-input"
            placeholder="."
          />
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">编辑模式</span>
            <span class="label-description">文件编辑的确认方式</span>
          </div>
          <select v-model="settings.workspace.editMode" class="terminal-input">
            <option value="auto">自动应用</option>
            <option value="confirm">需要确认</option>
            <option value="preview">预览模式</option>
          </select>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">排除目录</span>
            <span class="label-description">搜索和索引时排除的目录</span>
          </div>
          <textarea 
            v-model="settings.workspace.excludeDirs" 
            class="terminal-input textarea"
            placeholder="node_modules, .git, target"
            rows="3"
          ></textarea>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">自动刷新索引</span>
            <span class="label-description">文件变更时自动更新索引</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.workspace.autoRefresh" />
            <span class="slider"></span>
          </label>
        </div>
      </div>
      
      <!-- 安全设置 -->
      <div v-if="activeTab === 'security'" class="settings-section">
        <h3>安全设置</h3>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">风暴断路器</span>
            <span class="label-description">防止重复工具调用死循环</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.security.stormBreaker" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">路径穿越防护</span>
            <span class="label-description">阻止访问工作区外的文件</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.security.pathTraversal" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">命令白名单</span>
            <span class="label-description">只允许执行白名单中的命令</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.security.commandWhitelist" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">操作日志</span>
            <span class="label-description">记录所有文件和命令操作</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.security.auditLog" />
            <span class="slider"></span>
          </label>
        </div>
      </div>
      
      <!-- 高级设置 -->
      <div v-if="activeTab === 'advanced'" class="settings-section">
        <h3>高级设置</h3>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">调试模式</span>
            <span class="label-description">启用详细日志输出</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.advanced.debugMode" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">上下文折叠</span>
            <span class="label-description">长对话时自动压缩历史</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.advanced.contextFolding" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">消息自愈</span>
            <span class="label-description">发送前自动修复消息格式</span>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" v-model="settings.advanced.messageHealing" />
            <span class="slider"></span>
          </label>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">会话自动保存</span>
            <span class="label-description">定期自动保存会话</span>
          </div>
          <div class="slider-container">
            <input 
              type="range" 
              v-model="settings.advanced.autoSaveInterval" 
              min="30" 
              max="300" 
              step="30"
              class="slider"
            />
            <span class="slider-value">{{ settings.advanced.autoSaveInterval }}秒</span>
          </div>
        </div>
        
        <div class="setting-item">
          <div class="setting-label">
            <span class="label-text">最大历史消息</span>
            <span class="label-description">保留的历史消息数量</span>
          </div>
          <div class="slider-container">
            <input 
              type="range" 
              v-model="settings.advanced.maxHistory" 
              min="10" 
              max="100" 
              step="10"
              class="slider"
            />
            <span class="slider-value">{{ settings.advanced.maxHistory }}条</span>
          </div>
        </div>
      </div>
    </div>
    
    <div class="settings-footer">
      <div class="settings-info">
        <span>配置文件位置: ~/.agent4j/config.json</span>
      </div>
      <div class="settings-actions">
        <button class="terminal-button" @click="openConfigFile">打开配置文件</button>
        <button class="terminal-button" @click="validateSettings">验证配置</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { configAPI } from '../services/api'

const activeTab = ref('general')
const showApiKey = ref(false)
const loading = ref(false)
const error = ref('')

const tabs = [
  { id: 'general', label: '基本设置' },
  { id: 'ai', label: 'AI 设置' },
  { id: 'workspace', label: '工作区' },
  { id: 'security', label: '安全' },
  { id: 'advanced', label: '高级' }
]

const settings = reactive({
  language: 'zh-CN',
  theme: 'retro-green',
  fontSize: 14,
  animations: true,
  
  ai: {
    baseUrl: '',
    apiKey: '',
    model: '',
    reasoningEffort: 'max',
    temperature: 0.7
  },
  
  workspace: {
    dir: '',
    editMode: 'auto',
    excludeDirs: 'node_modules, .git, target, dist',
    autoRefresh: true
  },
  
  security: {
    stormBreaker: true,
    pathTraversal: true,
    commandWhitelist: true,
    auditLog: true
  },
  
  advanced: {
    debugMode: false,
    contextFolding: true,
    messageHealing: true,
    autoSaveInterval: 30,
    maxHistory: 50
  }
})

const loadSettings = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await configAPI.getConfig()
    if (response.success && response.data) {
      const config = response.data
      
      // 更新AI设置
      settings.ai.baseUrl = config.baseUrl || ''
      settings.ai.apiKey = config.apiKey || ''
      settings.ai.model = config.model || ''
      settings.ai.reasoningEffort = config.reasoningEffort || 'max'
      
      // 更新工作区设置
      settings.workspace.dir = config.workspace || '.'
      settings.workspace.editMode = config.editMode || 'auto'
      
      // 更新其他设置（从本地存储或默认值）
      const savedSettings = localStorage.getItem('agent4j-settings')
      if (savedSettings) {
        const parsed = JSON.parse(savedSettings)
        Object.assign(settings, parsed)
      }
    } else {
      error.value = response.error || '加载配置失败'
    }
  } catch (err) {
    console.error('加载配置失败:', err)
    error.value = '加载配置失败: ' + err.message
    
    // 使用默认值
    settings.ai.baseUrl = 'https://api.deepseek.com/v1'
    settings.ai.model = 'deepseek-v4-flash'
    settings.ai.reasoningEffort = 'max'
    settings.workspace.dir = '.'
    settings.workspace.editMode = 'auto'
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  // 保存到本地存储
  localStorage.setItem('agent4j-settings', JSON.stringify(settings))
  
  // 注意：实际的配置保存需要后端支持
  // 目前只保存到本地存储，因为后端API是只读的
  
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '设置已保存到本地存储' 
    }
  }))
  
  // 应用主题
  applyTheme(settings.theme)
}

const resetSettings = () => {
  if (confirm('确定要重置所有设置为默认值吗？')) {
    // 重置为默认值
    Object.assign(settings, {
      language: 'zh-CN',
      theme: 'retro-green',
      fontSize: 14,
      animations: true,
      
      ai: {
        baseUrl: 'https://api.deepseek.com/v1',
        apiKey: '',
        model: 'deepseek-v4-flash',
        reasoningEffort: 'max',
        temperature: 0.7
      },
      
      workspace: {
        dir: '.',
        editMode: 'auto',
        excludeDirs: 'node_modules, .git, target, dist',
        autoRefresh: true
      },
      
      security: {
        stormBreaker: true,
        pathTraversal: true,
        commandWhitelist: true,
        auditLog: true
      },
      
      advanced: {
        debugMode: false,
        contextFolding: true,
        messageHealing: true,
        autoSaveInterval: 30,
        maxHistory: 50
      }
    })
    
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'system', 
        text: '设置已重置为默认值' 
      }
    }))
  }
}

const exportSettings = () => {
  const exportData = {
    ...settings,
    exportedAt: new Date().toISOString(),
    version: '1.0'
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
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '配置文件位置: ~/.agent4j/config.json' 
    }
  }))
}

const validateSettings = async () => {
  // 简单的验证逻辑
  const errors = []
  
  if (!settings.ai.baseUrl) {
    errors.push('API 地址不能为空')
  }
  
  if (settings.ai.temperature < 0 || settings.ai.temperature > 2) {
    errors.push('温度值必须在 0-2 之间')
  }
  
  if (errors.length > 0) {
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'error', 
        text: '配置验证失败:\n' + errors.join('\n') 
      }
    }))
  } else {
    // 尝试从后端验证配置
    try {
      const response = await configAPI.getConfig()
      if (response.success && response.data) {
        window.dispatchEvent(new CustomEvent('terminal-output', { 
          detail: { 
            type: 'system', 
            text: '配置验证通过 - 后端配置有效' 
          }
        }))
      } else {
        window.dispatchEvent(new CustomEvent('terminal-output', { 
          detail: { 
            type: 'system', 
            text: '配置验证通过 - 本地配置有效' 
          }
        }))
      }
    } catch (err) {
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'system', 
          text: '配置验证通过 - 本地配置有效' 
        }
      }))
    }
  }
}

const applyTheme = (theme) => {
  const root = document.documentElement
  
  switch (theme) {
    case 'retro-green':
      root.style.setProperty('--terminal-green', '#33ff33')
      root.style.setProperty('--terminal-amber', '#ffb000')
      break
    case 'retro-amber':
      root.style.setProperty('--terminal-green', '#ffb000')
      root.style.setProperty('--terminal-amber', '#ff8c00')
      break
    case 'retro-blue':
      root.style.setProperty('--terminal-green', '#3399ff')
      root.style.setProperty('--terminal-amber', '#33ccff')
      break
    case 'dark':
      root.style.setProperty('--terminal-green', '#00ff00')
      root.style.setProperty('--terminal-amber', '#ffff00')
      root.style.setProperty('--bg-primary', '#000000')
      root.style.setProperty('--bg-secondary', '#111111')
      break
    case 'light':
      root.style.setProperty('--terminal-green', '#006600')
      root.style.setProperty('--terminal-amber', '#cc6600')
      root.style.setProperty('--bg-primary', '#ffffff')
      root.style.setProperty('--bg-secondary', '#f5f5f5')
      break
  }
}

// 监听设置变化
watch(() => settings.theme, (newTheme) => {
  applyTheme(newTheme)
})

// 初始化
onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.settings-view {
  max-width: 900px;
  margin: 0 auto;
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.settings-header h2 {
  color: var(--terminal-amber);
  font-size: var(--font-size-xl);
}

.settings-controls {
  display: flex;
  gap: var(--spacing-md);
}

.settings-tabs {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-sm);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  flex-wrap: wrap;
}

.tab-button {
  flex: 1;
  min-width: 100px;
  padding: var(--spacing-sm) var(--spacing-md);
  background: transparent;
  border: 1px solid transparent;
  color: var(--terminal-gray);
  font-family: var(--font-mono);
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.tab-button:hover {
  color: var(--terminal-green);
  border-color: var(--border-color);
}

.tab-button.active {
  background: var(--bg-tertiary);
  color: var(--terminal-green);
  border-color: var(--terminal-green);
}

.settings-content {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.settings-section h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.setting-label {
  flex: 1;
}

.label-text {
  display: block;
  color: var(--terminal-green);
  font-weight: bold;
  margin-bottom: var(--spacing-xs);
}

.label-description {
  display: block;
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.terminal-input {
  width: 300px;
  max-width: 100%;
}

.textarea {
  resize: vertical;
  min-height: 80px;
}

.slider-container {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  width: 300px;
  max-width: 100%;
}

.slider {
  flex: 1;
  -webkit-appearance: none;
  height: 6px;
  background: var(--bg-primary);
  border-radius: 3px;
  outline: none;
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  background: var(--terminal-green);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--bg-primary);
}

.slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  background: var(--terminal-green);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--bg-primary);
}

.slider-value {
  color: var(--terminal-green);
  font-weight: bold;
  min-width: 50px;
  text-align: right;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 50px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-switch .slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--bg-primary);
  transition: 0.4s;
  border-radius: 24px;
  border: 1px solid var(--border-color);
}

.toggle-switch .slider:before {
  position: absolute;
  content: "";
  height: 16px;
  width: 16px;
  left: 4px;
  bottom: 3px;
  background-color: var(--terminal-gray);
  transition: 0.4s;
  border-radius: 50%;
}

.toggle-switch input:checked + .slider {
  background-color: var(--bg-tertiary);
  border-color: var(--terminal-green);
}

.toggle-switch input:checked + .slider:before {
  transform: translateX(26px);
  background-color: var(--terminal-green);
}

.password-input {
  position: relative;
  width: 300px;
  max-width: 100%;
}

.password-input .terminal-input {
  width: 100%;
  padding-right: 40px;
}

.toggle-password {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.2rem;
}

.settings-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.settings-info {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.settings-actions {
  display: flex;
  gap: var(--spacing-md);
}

@media (max-width: 768px) {
  .settings-header {
    flex-direction: column;
    gap: var(--spacing-md);
    align-items: flex-start;
  }
  
  .settings-tabs {
    flex-direction: column;
  }
  
  .tab-button {
    min-width: auto;
  }
  
  .setting-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-md);
  }
  
  .terminal-input,
  .slider-container,
  .password-input {
    width: 100%;
  }
  
  .settings-footer {
    flex-direction: column;
    gap: var(--spacing-md);
    text-align: center;
  }
  
  .settings-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>