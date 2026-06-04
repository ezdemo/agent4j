<template>
  <div class="tools-view">
    <!-- 头部 -->
    <div class="tools-header">
      <div class="header-left">
        <div class="header-title">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          <h2>工具箱</h2>
        </div>
        <span class="tool-count">{{ filteredTools.length }} 个工具</span>
      </div>
      <div class="header-actions">
        <button 
          class="refresh-btn"
          :class="{ refreshing }"
          @click="loadTools"
          :disabled="loading || refreshing"
          title="刷新工具列表"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
        </button>
        <div class="search-box">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="搜索工具..." 
            class="search-input"
          />
        </div>
      </div>
    </div>
    
    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon total">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
            <line x1="8" y1="21" x2="16" y2="21"/>
            <line x1="12" y1="17" x2="12" y2="21"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ tools.length }}</div>
          <div class="stat-label">总工具数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon readonly">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ readonlyToolsCount }}</div>
          <div class="stat-label">只读工具</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon write">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ writeToolsCount }}</div>
          <div class="stat-label">写入工具</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon exempt">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stormExemptCount }}</div>
          <div class="stat-label">风暴豁免</div>
        </div>
      </div>
    </div>
    
    <!-- 筛选器 -->
    <div class="filters">
      <button 
        v-for="filter in filters" 
        :key="filter.value"
        class="filter-btn"
        :class="{ active: activeFilter === filter.value }"
        @click="activeFilter = filter.value"
      >
        <span class="filter-icon">{{ filter.icon }}</span>
        <span class="filter-label">{{ filter.label }}</span>
        <span class="filter-count">{{ getFilterCount(filter.value) }}</span>
      </button>
    </div>
    
    <!-- 工具列表 -->
    <div class="tools-container">
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="error" class="error-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        <p>{{ error }}</p>
        <button class="btn btn-secondary btn-sm" @click="loadTools">重试</button>
      </div>
      
      <div v-else-if="filteredTools.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <h3>未找到匹配的工具</h3>
        <p>尝试调整搜索条件或筛选器</p>
        <button class="btn btn-secondary" @click="resetFilters">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="1 4 1 10 7 10"/>
            <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
          </svg>
          重置筛选
        </button>
      </div>
      
      <div v-else class="tools-grid">
        <div 
          v-for="tool in filteredTools" 
          :key="tool.name"
          class="tool-card"
          :class="{ expanded: expandedTools.includes(tool.name) }"
        >
          <div class="tool-header" @click="toggleDetails(tool.name)">
            <div class="tool-info">
              <div class="tool-icon" :class="getToolType(tool)">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                </svg>
              </div>
              <div class="tool-details">
                <div class="tool-name">{{ tool.name }}</div>
                <div class="tool-badges">
                  <span v-if="tool.readonly" class="badge readonly">只读</span>
                  <span v-if="tool.write" class="badge write">写入</span>
                  <span v-if="tool.stormExempt" class="badge exempt">豁免</span>
                </div>
              </div>
            </div>
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              class="expand-icon"
              :class="{ expanded: expandedTools.includes(tool.name) }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </div>
          
          <div class="tool-description">{{ tool.description }}</div>
          
          <div v-if="expandedTools.includes(tool.name)" class="tool-expanded">
            <!-- 参数 -->
            <div v-if="tool.params?.length" class="tool-section">
              <div class="section-title">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16 18 22 12 16 6"/>
                  <polyline points="8 6 2 12 8 18"/>
                </svg>
                <span>参数</span>
              </div>
              <div class="params-list">
                <div v-for="param in tool.params" :key="param.name" class="param-item">
                  <div class="param-header">
                    <span class="param-name">{{ param.name }}</span>
                    <span class="param-type">{{ param.type }}</span>
                    <span v-if="param.required" class="param-required">必填</span>
                  </div>
                  <div class="param-description">{{ param.description }}</div>
                </div>
              </div>
            </div>
            
            <!-- 示例 -->
            <div v-if="tool.example" class="tool-section">
              <div class="section-title">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16 18 22 12 16 6"/>
                  <polyline points="8 6 2 12 8 18"/>
                </svg>
                <span>示例</span>
              </div>
              <div class="code-block">
                <pre><code>{{ tool.example }}</code></pre>
              </div>
            </div>
            
            <!-- 注意事项 -->
            <div v-if="tool.notes?.length" class="tool-section">
              <div class="section-title">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="16" x2="12" y2="12"/>
                  <line x1="12" y1="8" x2="12.01" y2="8"/>
                </svg>
                <span>注意事项</span>
              </div>
              <ul class="notes-list">
                <li v-for="(note, index) in tool.notes" :key="index">{{ note }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { toolsAPI } from '../services/api'

// 状态
const searchQuery = ref('')
const activeFilter = ref('all')
const expandedTools = ref([])
const loading = ref(false)
const refreshing = ref(false)
const error = ref('')
const tools = ref([])

// 筛选器配置
const filters = [
  { label: '全部', value: 'all', icon: '🔧' },
  { label: '只读', value: 'readonly', icon: '👁' },
  { label: '写入', value: 'write', icon: '✏️' },
  { label: '豁免', value: 'exempt', icon: '🛡' }
]

// 计算属性
const readonlyToolsCount = computed(() => tools.value.filter(t => t.readonly).length)
const writeToolsCount = computed(() => tools.value.filter(t => t.write).length)
const stormExemptCount = computed(() => tools.value.filter(t => t.stormExempt).length)

const filteredTools = computed(() => {
  let result = tools.value
  
  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(tool => 
      tool.name.toLowerCase().includes(query) ||
      tool.description.toLowerCase().includes(query)
    )
  }
  
  // 类型过滤
  if (activeFilter.value !== 'all') {
    switch (activeFilter.value) {
      case 'readonly':
        result = result.filter(t => t.readonly)
        break
      case 'write':
        result = result.filter(t => t.write)
        break
      case 'exempt':
        result = result.filter(t => t.stormExempt)
        break
    }
  }
  
  return result
})

// 方法
const loadTools = async () => {
  // 已有数据则为刷新模式，否则首次加载
  const isRefresh = tools.value.length > 0
  if (isRefresh) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  error.value = ''
  
  try {
    const response = await toolsAPI.list()
    if (response.success && response.data) {
      tools.value = response.data.map(tool => ({
        name: tool.name,
        description: tool.description,
        readonly: tool.readonly || false,
        write: !tool.readonly || false,
        stormExempt: tool.stormExempt || false,
        params: tool.parameters ? Object.entries(tool.parameters).map(([name, param]) => ({
          name,
          type: param.type || 'string',
          required: param.required || false,
          description: param.description || ''
        })) : [],
        example: tool.example || '',
        notes: tool.notes || []
      }))
    } else {
      error.value = response.error || '加载工具列表失败'
    }
  } catch (err) {
    console.error('加载工具列表失败:', err)
    error.value = '加载工具列表失败: ' + err.message
    
    // 使用默认工具列表作为后备
    tools.value = getDefaultTools()
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const getDefaultTools = () => [
  {
    name: 'read_file',
    description: '读取工作区内的文件内容。返回完整的文本内容（无行号标注）。',
    readonly: true,
    write: false,
    stormExempt: true,
    params: [
      { name: 'path', type: 'string', required: true, description: '文件路径（相对于工作区根目录）' },
      { name: 'head', type: 'int', required: false, description: '返回前 N 行' },
      { name: 'tail', type: 'int', required: false, description: '返回后 N 行' },
      { name: 'range', type: 'string', required: false, description: '行范围 "start-end"' }
    ],
    example: 'read_file({ path: "src/main.java" })',
    notes: ['一次性读取：请直接读取完整文件，不要使用 head/tail/range 分段读取', '大文件处理：超过 100 MiB 的文件会被拒绝读取']
  },
  {
    name: 'edit_file',
    description: '对已有文件执行 SEARCH/REPLACE 编辑。这是修改代码的主要工具。',
    readonly: false,
    write: true,
    stormExempt: false,
    params: [
      { name: 'path', type: 'string', required: true, description: '文件路径' },
      { name: 'search', type: 'string', required: true, description: '要搜索替换的精确文本（必须唯一）' },
      { name: 'replace', type: 'string', required: true, description: '替换后的文本' }
    ],
    example: 'edit_file({ path: "src/Hello.java", search: "Hello!", replace: "Hello, World!" })',
    notes: ['search 必须唯一：要搜索的文本在文件中只能出现一次', '精确匹配：search 文本必须与文件中完全一致', '缩进敏感：search/replace 中的缩进必须与源文件完全一致']
  },
  {
    name: 'write_file',
    description: '创建新文件或覆盖已有文件的内容。父目录会自动创建。',
    readonly: false,
    write: true,
    stormExempt: false,
    params: [
      { name: 'path', type: 'string', required: true, description: '文件路径' },
      { name: 'content', type: 'string', required: true, description: '文件内容' }
    ],
    example: 'write_file({ path: "src/NewFile.java", content: "public class NewFile {}" })',
    notes: ['创建新文件：指定 path 和 content，父目录不存在时会自动创建', '覆盖已有文件：会直接覆盖，不可恢复', '编辑已有文件：推荐使用 edit_file 而非 write_file']
  },
  {
    name: 'run_command',
    description: '在工作区执行 shell 命令。返回 stdout+stderr 合并输出。',
    readonly: false,
    write: true,
    stormExempt: false,
    params: [
      { name: 'command', type: 'string', required: true, description: 'shell 命令' },
      { name: 'timeoutSec', type: 'int', required: false, description: '超时秒数（默认 60）' }
    ],
    example: 'run_command({ command: "mvn compile" })',
    notes: ['只读命令立即执行', '写入命令需要用户确认', '支持管道、重定向等 shell 特性']
  }
]

const getToolType = (tool) => {
  if (tool.readonly) return 'readonly'
  if (tool.write) return 'write'
  if (tool.stormExempt) return 'exempt'
  return 'default'
}

const getFilterCount = (filter) => {
  switch (filter) {
    case 'all': return tools.value.length
    case 'readonly': return readonlyToolsCount.value
    case 'write': return writeToolsCount.value
    case 'exempt': return stormExemptCount.value
    default: return 0
  }
}

const toggleDetails = (toolName) => {
  const index = expandedTools.value.indexOf(toolName)
  if (index > -1) {
    expandedTools.value.splice(index, 1)
  } else {
    expandedTools.value.push(toolName)
  }
}

const resetFilters = () => {
  searchQuery.value = ''
  activeFilter.value = 'all'
}

// 生命周期
onMounted(() => {
  loadTools()
})
</script>

<style scoped>
.tools-view {
  padding: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}

/* 头部 */
.tools-header {
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

.tool-count {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
}

.header-actions {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

/* 刷新按钮 */
.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--fg-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.refresh-btn:hover {
  color: var(--brand-primary);
  border-color: var(--brand-primary);
  background: var(--accent-soft);
}

.refresh-btn:active {
  transform: scale(0.95);
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.refresh-btn.refreshing svg {
  animation: spin 0.8s linear infinite;
}

/* 搜索框 */
.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-box svg {
  position: absolute;
  left: var(--space-3);
  color: var(--fg-muted);
  pointer-events: none;
}

.search-input {
  width: 280px;
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-8);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg);
  transition: all var(--transition-fast);
}

.search-input:focus {
  background: var(--surface);
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  outline: none;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
}

.stat-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon.total {
  background: var(--accent-soft);
  color: var(--brand-primary);
}

.stat-icon.readonly {
  background: var(--success-bg);
  color: var(--success);
}

.stat-icon.write {
  background: var(--warning-bg);
  color: var(--warning);
}

.stat-icon.exempt {
  background: var(--info-bg);
  color: var(--info);
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  margin-top: 0.25rem;
}

/* 筛选器 */
.filters {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
}

.filter-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  transition: all var(--transition-fast);
}

.filter-btn:hover {
  background: var(--surface-hover);
  border-color: var(--fg-muted);
}

.filter-btn.active {
  background: var(--accent-soft);
  border-color: var(--brand-primary);
  color: var(--brand-primary);
}

.filter-icon {
  font-size: 14px;
}

.filter-label {
  font-weight: var(--font-medium);
}

.filter-count {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-full);
}

.filter-btn.active .filter-count {
  background: rgba(99, 102, 241, 0.2);
  color: var(--brand-primary);
}

/* 工具容器 */
.tools-container {
  margin-bottom: var(--space-6);
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  color: var(--fg-muted);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: var(--space-4);
}

/* 错误状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  color: var(--fg-muted);
}

.error-state svg {
  color: var(--danger);
  margin-bottom: var(--space-4);
}

.error-state p {
  margin-bottom: var(--space-4);
  color: var(--danger);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  color: var(--fg-muted);
}

.empty-state svg {
  color: var(--fg-muted);
  opacity: 0.5;
  margin-bottom: var(--space-4);
}

.empty-state h3 {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.empty-state p {
  margin-bottom: var(--space-6);
}

/* 工具网格 */
.tools-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 工具卡片 */
.tool-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-fast);
}

.tool-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
}

.tool-card.expanded {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

/* 工具头部 */
.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.tool-header:hover {
  background: var(--surface-hover);
}

.tool-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.tool-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-icon.readonly {
  background: var(--success-bg);
  color: var(--success);
}

.tool-icon.write {
  background: var(--warning-bg);
  color: var(--warning);
}

.tool-icon.exempt {
  background: var(--info-bg);
  color: var(--info);
}

.tool-icon.default {
  background: var(--accent-soft);
  color: var(--brand-primary);
}

.tool-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tool-name {
  font-size: var(--text-base);
  font-weight: var(--font-semibold);
  font-family: var(--font-mono);
  color: var(--fg);
}

.tool-badges {
  display: flex;
  gap: var(--space-1);
}

.badge {
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-full);
}

.badge.readonly {
  background: var(--success-bg);
  color: var(--success);
}

.badge.write {
  background: var(--warning-bg);
  color: var(--warning);
}

.badge.exempt {
  background: var(--info-bg);
  color: var(--info);
}

.expand-icon {
  color: var(--fg-muted);
  transition: transform var(--transition-fast);
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

/* 工具描述 */
.tool-description {
  padding: 0 var(--space-4) var(--space-4);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  line-height: 1.6;
}

/* 展开内容 */
.tool-expanded {
  padding: var(--space-4);
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
}

.tool-section {
  margin-bottom: var(--space-4);
}

.tool-section:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-3);
}

.section-title svg {
  color: var(--brand-primary);
}

/* 参数列表 */
.params-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.param-item {
  padding: var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.param-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.param-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  font-family: var(--font-mono);
  color: var(--fg);
}

.param-type {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}

.param-required {
  font-size: var(--text-xs);
  color: var(--danger);
  background: var(--danger-bg);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}

.param-description {
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  line-height: 1.5;
}

/* 代码块 */
.code-block {
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow-x: auto;
}

.code-block pre {
  margin: 0;
  padding: var(--space-3);
  background: none;
  border: none;
}

.code-block code {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--fg);
  background: none;
  padding: 0;
}

/* 注意事项 */
.notes-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.notes-list li {
  padding: var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  line-height: 1.5;
  position: relative;
  padding-left: var(--space-6);
}

.notes-list li::before {
  content: '•';
  position: absolute;
  left: var(--space-3);
  color: var(--brand-primary);
  font-weight: bold;
}

/* 动画 */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tools-view {
    padding: var(--space-4);
  }
  
  .tools-header {
    flex-direction: column;
    gap: var(--space-4);
    align-items: flex-start;
  }
  
  .header-actions {
    width: 100%;
  }
  
  .search-input {
    width: 100%;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .filters {
    flex-wrap: wrap;
  }
  
  .filter-btn {
    flex: 1;
    min-width: calc(50% - var(--space-2));
    justify-content: center;
  }
  
  .tool-header {
    flex-direction: column;
    gap: var(--space-3);
    align-items: flex-start;
  }
  
  .expand-icon {
    align-self: flex-end;
  }
  
  .param-header {
    flex-wrap: wrap;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .tool-card {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .tool-card:hover {
  border-color: var(--brand-primary-light);
}

[data-theme="dark"] .tool-expanded {
  background: var(--bg-tertiary);
}

[data-theme="dark"] .stat-card {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .param-item {
  background: var(--bg-tertiary);
  border-color: var(--border);
}
</style>