<template>
  <div class="tools-view">
    <div class="tools-header">
      <h2>工具终端</h2>
      <div class="tools-controls">
        <input 
          v-model="searchQuery" 
          placeholder="搜索工具..."
          class="terminal-input search-input"
        />
        <div class="filter-buttons">
          <button 
            v-for="filter in filters" 
            :key="filter.value"
            @click="activeFilter = filter.value"
            :class="['terminal-button', { active: activeFilter === filter.value }]"
          >
            {{ filter.label }}
          </button>
        </div>
      </div>
    </div>
    
    <div class="tools-stats">
      <div class="stat-item">
        <span class="stat-label">总工具数:</span>
        <span class="stat-value">{{ tools.length }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">只读工具:</span>
        <span class="stat-value readonly">{{ readonlyToolsCount }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">写入工具:</span>
        <span class="stat-value write">{{ writeToolsCount }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">风暴豁免:</span>
        <span class="stat-value exempt">{{ stormExemptCount }}</span>
      </div>
    </div>
    
    <div class="tools-list">
      <div v-for="tool in filteredTools" :key="tool.name" class="tool-card">
        <div class="tool-header">
          <div class="tool-title">
            <span class="tool-name">{{ tool.name }}</span>
            <div class="tool-badges">
              <span v-if="tool.readonly" class="badge readonly">只读</span>
              <span v-if="tool.stormExempt" class="badge exempt">风暴豁免</span>
              <span v-if="tool.write" class="badge write">写入</span>
            </div>
          </div>
          <button class="terminal-button details-btn" @click="toggleDetails(tool.name)">
            {{ expandedTools.includes(tool.name) ? '收起' : '详情' }}
          </button>
        </div>
        
        <div class="tool-description">{{ tool.description }}</div>
        
        <div v-if="expandedTools.includes(tool.name)" class="tool-details">
          <div class="tool-params">
            <h4>参数:</h4>
            <div v-if="tool.params && tool.params.length" class="params-list">
              <div v-for="param in tool.params" :key="param.name" class="param-item">
                <span class="param-name">{{ param.name }}</span>
                <span class="param-type">{{ param.type }}</span>
                <span v-if="param.required" class="param-required">必填</span>
                <span class="param-desc">{{ param.description }}</span>
              </div>
            </div>
            <div v-else class="no-params">无参数</div>
          </div>
          
          <div class="tool-example">
            <h4>使用示例:</h4>
            <pre class="code-block">{{ tool.example }}</pre>
          </div>
          
          <div class="tool-notes">
            <h4>注意事项:</h4>
            <ul>
              <li v-for="(note, index) in tool.notes" :key="index">{{ note }}</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
    
    <div v-if="filteredTools.length === 0" class="no-results">
      <div class="no-results-icon">🔍</div>
      <div class="no-results-text">未找到匹配的工具</div>
      <button class="terminal-button" @click="resetFilters">重置筛选</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { toolsAPI } from '../services/api'

const searchQuery = ref('')
const activeFilter = ref('all')
const expandedTools = ref([])
const loading = ref(false)
const error = ref('')

const filters = [
  { label: '全部', value: 'all' },
  { label: '只读', value: 'readonly' },
  { label: '写入', value: 'write' },
  { label: '风暴豁免', value: 'exempt' }
]

const tools = ref([])

const loadTools = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await toolsAPI.list()
    if (response.success && response.data) {
      // 转换后端数据格式为前端格式
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
    tools.value = [
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
          { name: 'range', type: 'string', required: false, description: '行范围 "start-end"，如 "50-100"' }
        ],
        example: 'read_file({ path: "src/main.java", head: 50 })',
        notes: ['完整读取：默认返回整个文件内容', '范围读取：通过参数控制输出范围', '大文件处理：超过 32 MiB 的文件会被拒绝读取']
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
      }
    ]
  } finally {
    loading.value = false
  }
}

const readonlyToolsCount = computed(() => {
  return tools.value.filter(tool => tool.readonly).length
})

const writeToolsCount = computed(() => {
  return tools.value.filter(tool => tool.write).length
})

const stormExemptCount = computed(() => {
  return tools.value.filter(tool => tool.stormExempt).length
})

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
        result = result.filter(tool => tool.readonly)
        break
      case 'write':
        result = result.filter(tool => tool.write)
        break
      case 'exempt':
        result = result.filter(tool => tool.stormExempt)
        break
    }
  }
  
  return result
})

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

onMounted(() => {
  loadTools()
})
</script>

<style scoped>
.tools-view {
  max-width: 1200px;
  margin: 0 auto;
}

.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.tools-header h2 {
  color: var(--terminal-amber);
  font-size: var(--font-size-xl);
}

.tools-controls {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
}

.search-input {
  width: 250px;
}

.filter-buttons {
  display: flex;
  gap: var(--spacing-sm);
}

.filter-buttons .active {
  background: var(--terminal-green);
  color: var(--bg-primary);
}

.tools-stats {
  display: flex;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.stat-label {
  color: var(--terminal-gray);
}

.stat-value {
  font-weight: bold;
}

.stat-value.readonly {
  color: var(--terminal-green);
}

.stat-value.write {
  color: var(--terminal-amber);
}

.stat-value.exempt {
  color: var(--terminal-cyan);
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.tool-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  transition: all 0.2s;
}

.tool-card:hover {
  border-color: var(--terminal-green);
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-md);
}

.tool-title {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.tool-name {
  color: var(--terminal-cyan);
  font-size: var(--font-size-lg);
  font-weight: bold;
}

.tool-badges {
  display: flex;
  gap: var(--spacing-sm);
}

.badge {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.badge.readonly {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
  border: 1px solid var(--terminal-green);
}

.badge.write {
  background: rgba(255, 176, 0, 0.1);
  color: var(--terminal-amber);
  border: 1px solid var(--terminal-amber);
}

.badge.exempt {
  background: rgba(51, 255, 255, 0.1);
  color: var(--terminal-cyan);
  border: 1px solid var(--terminal-cyan);
}

.details-btn {
  font-size: var(--font-size-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
}

.tool-description {
  color: var(--terminal-green);
  line-height: 1.6;
  margin-bottom: var(--spacing-md);
}

.tool-details {
  border-top: 1px solid var(--border-color);
  padding-top: var(--spacing-md);
}

.tool-params h4,
.tool-example h4,
.tool-notes h4 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.params-list {
  margin-bottom: var(--spacing-md);
}

.param-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
}

.param-name {
  color: var(--terminal-cyan);
  font-weight: bold;
  width: 120px;
  flex-shrink: 0;
}

.param-type {
  color: var(--terminal-gray);
  width: 80px;
  flex-shrink: 0;
}

.param-required {
  color: var(--terminal-red);
  font-size: var(--font-size-xs);
  width: 60px;
  flex-shrink: 0;
}

.param-desc {
  color: var(--terminal-green);
  flex: 1;
}

.no-params {
  color: var(--terminal-gray);
  font-style: italic;
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
}

.tool-example {
  margin-bottom: var(--spacing-md);
}

.code-block {
  background: var(--bg-primary);
  padding: var(--spacing-md);
  border-radius: 4px;
  font-family: var(--font-mono);
  color: var(--terminal-green);
  overflow-x: auto;
}

.tool-notes ul {
  list-style: none;
  padding: 0;
}

.tool-notes li {
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  margin-bottom: var(--spacing-sm);
  color: var(--terminal-green);
  font-size: var(--font-size-sm);
}

.tool-notes li::before {
  content: "• ";
  color: var(--terminal-amber);
}

.no-results {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--terminal-gray);
}

.no-results-icon {
  font-size: 3rem;
  margin-bottom: var(--spacing-md);
}

.no-results-text {
  margin-bottom: var(--spacing-md);
}

@media (max-width: 768px) {
  .tools-header {
    flex-direction: column;
    gap: var(--spacing-md);
    align-items: flex-start;
  }
  
  .tools-controls {
    flex-direction: column;
    width: 100%;
  }
  
  .search-input {
    width: 100%;
  }
  
  .filter-buttons {
    flex-wrap: wrap;
    width: 100%;
  }
  
  .filter-buttons .terminal-button {
    flex: 1;
    min-width: calc(50% - var(--spacing-sm));
  }
  
  .tools-stats {
    flex-wrap: wrap;
    gap: var(--spacing-md);
  }
  
  .stat-item {
    width: calc(50% - var(--spacing-md));
  }
  
  .tool-header {
    flex-direction: column;
    gap: var(--spacing-md);
  }
  
  .param-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
  }
  
  .param-name,
  .param-type,
  .param-required {
    width: auto;
  }
}
</style>