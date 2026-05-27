<template>
  <div class="help-view">
    <div class="help-header">
      <h2>帮助终端</h2>
      <div class="help-search">
        <input 
          v-model="searchQuery" 
          placeholder="搜索帮助文档..."
          class="terminal-input"
        />
      </div>
    </div>
    
    <div class="help-content">
      <div class="help-sidebar">
        <div class="sidebar-section">
          <h3>目录</h3>
          <ul class="help-nav">
            <li 
              v-for="section in sections" 
              :key="section.id"
              @click="scrollToSection(section.id)"
              :class="{ active: activeSection === section.id }"
            >
              {{ section.title }}
            </li>
          </ul>
        </div>
        
        <div class="sidebar-section">
          <h3>快捷键</h3>
          <div class="shortcuts-list">
            <div v-for="shortcut in shortcuts" :key="shortcut.keys" class="shortcut-item">
              <span class="keys">{{ shortcut.keys }}</span>
              <span class="desc">{{ shortcut.desc }}</span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="help-main" ref="helpMain">
        <!-- 快速入门 -->
        <section id="quickstart" class="help-section">
          <h3>快速入门</h3>
          <div class="section-content">
            <p>欢迎使用 Agent4J！这是一个基于 Java 的 AI 代理框架，提供推理循环、工具调用、会话管理等功能。</p>
            
            <div class="quick-start-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <h4>启动系统</h4>
                  <p>运行 Agent4J 应用，系统会自动加载配置和工具。</p>
                </div>
              </div>
              
              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <h4>开始对话</h4>
                  <p>在终端输入消息，与 AI 助手进行交互。</p>
                </div>
              </div>
              
              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <h4>使用工具</h4>
                  <p>AI 助手会自动调用合适的工具来完成任务。</p>
                </div>
              </div>
            </div>
          </div>
        </section>
        
        <!-- 命令参考 -->
        <section id="commands" class="help-section">
          <h3>命令参考</h3>
          <div class="section-content">
            <div class="command-table">
              <table class="terminal-table">
                <thead>
                  <tr>
                    <th>命令</th>
                    <th>描述</th>
                    <th>示例</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="cmd in commands" :key="cmd.name">
                    <td class="command-name">{{ cmd.name }}</td>
                    <td>{{ cmd.desc }}</td>
                    <td class="command-example">{{ cmd.example }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
        
        <!-- 工具列表 -->
        <section id="tools" class="help-section">
          <h3>工具列表</h3>
          <div class="section-content">
            <p>Agent4J 提供了丰富的工具集，用于文件操作、代码分析、网络搜索等。</p>
            
            <div class="tools-grid">
              <div v-for="tool in tools" :key="tool.name" class="tool-item">
                <div class="tool-header">
                  <span class="tool-name">{{ tool.name }}</span>
                  <span v-if="tool.readonly" class="badge readonly">只读</span>
                </div>
                <div class="tool-desc">{{ tool.desc }}</div>
                <div class="tool-params">
                  <span class="params-label">参数:</span>
                  <span class="params-list">{{ tool.params }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>
        
        <!-- 配置说明 -->
        <section id="config" class="help-section">
          <h3>配置说明</h3>
          <div class="section-content">
            <p>Agent4J 的配置文件位于 <code>~/.agent4j/config.json</code>。</p>
            
            <div class="config-example">
              <h4>配置文件示例:</h4>
              <pre class="code-block">{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": ".",
  "editMode": "auto",
  "reasoningEffort": "max",
  "lang": "ZH"
}</pre>
            </div>
            
            <div class="config-options">
              <h4>配置选项:</h4>
              <ul>
                <li><strong>baseUrl</strong>: OpenAI 兼容 API 的基础 URL</li>
                <li><strong>apiKey</strong>: API 密钥</li>
                <li><strong>model</strong>: 使用的模型名称</li>
                <li><strong>workspaceDir</strong>: 工作区目录路径</li>
                <li><strong>editMode</strong>: 编辑模式 (auto/confirm/preview)</li>
                <li><strong>reasoningEffort</strong>: 推理强度 (low/medium/high/max)</li>
                <li><strong>lang</strong>: 界面语言 (ZH/EN)</li>
              </ul>
            </div>
          </div>
        </section>
        
        <!-- 故障排除 -->
        <section id="troubleshooting" class="help-section">
          <h3>故障排除</h3>
          <div class="section-content">
            <div class="troubleshooting-list">
              <div v-for="issue in troubleshooting" :key="issue.title" class="issue-item">
                <h4 class="issue-title">{{ issue.title }}</h4>
                <div class="issue-solution">
                  <p><strong>症状:</strong> {{ issue.symptom }}</p>
                  <p><strong>解决方案:</strong> {{ issue.solution }}</p>
                </div>
              </div>
            </div>
          </div>
        </section>
        
        <!-- API 文档 -->
        <section id="api" class="help-section">
          <h3>API 文档</h3>
          <div class="section-content">
            <p>Agent4J 提供 RESTful API 接口，用于与外部系统集成。</p>
            
            <div class="api-endpoints">
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/chat</span>
                <span class="desc">同步聊天</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/chat/stream</span>
                <span class="desc">SSE流式聊天</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/agent/status</span>
                <span class="desc">获取Agent状态</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/agent/history</span>
                <span class="desc">获取历史消息</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/retry</span>
                <span class="desc">撤回并重试</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/rewind</span>
                <span class="desc">回退到指定轮次</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/compact</span>
                <span class="desc">折叠上下文</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/plan/enable</span>
                <span class="desc">进入计划模式</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/plan/disable</span>
                <span class="desc">退出计划模式</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/agent/hitl/status</span>
                <span class="desc">获取HITL状态</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/hitl/toggle</span>
                <span class="desc">切换HITL模式</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/hitl/approve</span>
                <span class="desc">批准HITL</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/agent/hitl/deny</span>
                <span class="desc">拒绝HITL</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/agent/hitl/pending</span>
                <span class="desc">获取待审批列表</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/sessions</span>
                <span class="desc">列出所有会话</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/sessions/current</span>
                <span class="desc">获取当前会话</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/sessions/new</span>
                <span class="desc">新建会话</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/sessions/{name}</span>
                <span class="desc">切换会话</span>
              </div>
              <div class="endpoint">
                <span class="method">DELETE</span>
                <span class="path">/api/sessions/{name}</span>
                <span class="desc">删除会话</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/tools</span>
                <span class="desc">列出所有工具</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/tools/{name}</span>
                <span class="desc">获取工具详情</span>
              </div>
              <div class="endpoint">
                <span class="method">POST</span>
                <span class="path">/api/tools/{name}/execute</span>
                <span class="desc">直接执行工具</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/config</span>
                <span class="desc">获取当前配置</span>
              </div>
              <div class="endpoint">
                <span class="method">GET</span>
                <span class="path">/api/usage</span>
                <span class="desc">获取Token用量统计</span>
              </div>
            </div>
          </div>
        </section>
        
        <!-- 更新日志 -->
        <section id="changelog" class="help-section">
          <h3>更新日志</h3>
          <div class="section-content">
            <div class="changelog-list">
              <div v-for="release in changelog" :key="release.version" class="release-item">
                <div class="release-header">
                  <span class="version">{{ release.version }}</span>
                  <span class="date">{{ release.date }}</span>
                </div>
                <div class="release-changes">
                  <div v-for="(change, index) in release.changes" :key="index" class="change-item">
                    <span class="change-type" :class="change.type">{{ change.type }}</span>
                    <span class="change-desc">{{ change.desc }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'

const searchQuery = ref('')
const activeSection = ref('quickstart')
const helpMain = ref(null)

const sections = [
  { id: 'quickstart', title: '快速入门' },
  { id: 'commands', title: '命令参考' },
  { id: 'tools', title: '工具列表' },
  { id: 'config', title: '配置说明' },
  { id: 'troubleshooting', title: '故障排除' },
  { id: 'api', title: 'API 文档' },
  { id: 'changelog', title: '更新日志' }
]

const shortcuts = [
  { keys: 'Enter', desc: '发送消息' },
  { keys: 'Shift+Enter', desc: '换行' },
  { keys: 'Ctrl+L', desc: '清空屏幕' },
  { keys: 'Ctrl+C', desc: '取消当前操作' },
  { keys: 'Ctrl+R', desc: '搜索历史命令' },
  { keys: 'Tab', desc: '自动补全' }
]

const commands = [
  { name: '/chat', desc: '开始与AI助手对话', example: '/chat' },
  { name: '/tools', desc: '查看可用工具列表', example: '/tools' },
  { name: '/sessions', desc: '管理会话历史', example: '/sessions' },
  { name: '/settings', desc: '系统配置设置', example: '/settings' },
  { name: '/help', desc: '查看帮助信息', example: '/help' },
  { name: '/new', desc: '开启新会话', example: '/new' },
  { name: '/plan', desc: '进入计划模式', example: '/plan' },
  { name: '/execute', desc: '退出计划模式', example: '/execute' },
  { name: '/compact', desc: '折叠历史消息', example: '/compact' },
  { name: '/retry', desc: '撤回最后一条消息并重试', example: '/retry' },
  { name: '/rewind', desc: '回退到第N轮对话', example: '/rewind 5' },
  { name: 'clear', desc: '清空终端输出', example: 'clear' },
  { name: 'exit', desc: '退出系统', example: 'exit' }
]

const tools = [
  { name: 'read_file', desc: '读取文件内容', params: 'path, head?, tail?, range?', readonly: true },
  { name: 'edit_file', desc: 'SEARCH/REPLACE编辑文件', params: 'path, search, replace', readonly: false },
  { name: 'multi_edit', desc: '批量原子编辑', params: 'edits[{path,search,replace}]', readonly: false },
  { name: 'write_file', desc: '创建或覆盖文件', params: 'path, content', readonly: false },
  { name: 'glob', desc: '按模式匹配文件名', params: 'pattern, maxResults?', readonly: true },
  { name: 'grep', desc: '正则表达式搜索内容', params: 'pattern, glob?, caseSensitive?, maxResults?', readonly: true },
  { name: 'tree', desc: '生成目录树结构', params: 'maxDepth?', readonly: true },
  { name: 'run_command', desc: '执行shell命令', params: 'command, timeoutSec?', readonly: false },
  { name: 'web_search', desc: '搜索互联网', params: 'query', readonly: true },
  { name: 'web_fetch', desc: '下载URL内容', params: 'url', readonly: true },
  { name: 'remember', desc: '保存记忆', params: 'name, type, scope, description, content', readonly: false },
  { name: 'task', desc: '创建子代理', params: 'name, arguments?', readonly: false }
]

const troubleshooting = [
  {
    title: '无法连接到AI服务',
    symptom: '发送消息后长时间无响应，或显示连接错误',
    solution: '检查 ~/.agent4j/config.json 中的 baseUrl 和 apiKey 是否正确。确保网络连接正常。'
  },
  {
    title: '工具调用失败',
    symptom: 'AI尝试使用工具但返回错误',
    solution: '检查工作区路径是否正确，确保有足够的文件操作权限。'
  },
  {
    title: '界面显示异常',
    symptom: '字符显示乱码或布局错乱',
    solution: '尝试切换主题或调整字体大小。确保终端支持UTF-8编码。'
  },
  {
    title: '会话历史丢失',
    symptom: '重新打开后之前的对话记录消失',
    solution: '检查 ~/.agent4j/sessions/ 目录是否有写入权限。会话会自动保存到此目录。'
  }
]

const changelog = [
  {
    version: 'v1.0.0',
    date: '2024-03-20',
    changes: [
      { type: '新增', desc: '初始版本发布' },
      { type: '新增', desc: '支持OpenAI兼容API' },
      { type: '新增', desc: '文件操作工具集' },
      { type: '新增', desc: '会话管理功能' }
    ]
  },
  {
    version: 'v0.9.0',
    date: '2024-03-15',
    changes: [
      { type: '新增', desc: '风暴断路器功能' },
      { type: '新增', desc: '消息自愈机制' },
      { type: '优化', desc: '上下文折叠算法' },
      { type: '修复', desc: '工具调用回收问题' }
    ]
  }
]

const scrollToSection = async (sectionId) => {
  activeSection.value = sectionId
  await nextTick()
  
  const element = document.getElementById(sectionId)
  if (element) {
    element.scrollIntoView({ behavior: 'smooth' })
  }
}

onMounted(() => {
  // 监听滚动事件，更新当前活动部分
  if (helpMain.value) {
    helpMain.value.addEventListener('scroll', () => {
      const sections = document.querySelectorAll('.help-section')
      let currentSection = 'quickstart'
      
      sections.forEach(section => {
        const rect = section.getBoundingClientRect()
        if (rect.top <= 200) {
          currentSection = section.id
        }
      })
      
      activeSection.value = currentSection
    })
  }
})
</script>

<style scoped>
.help-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.help-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  margin-bottom: var(--spacing-md);
}

.help-header h2 {
  color: var(--terminal-amber);
  font-size: var(--font-size-xl);
}

.help-search {
  width: 300px;
}

.help-content {
  display: flex;
  flex: 1;
  gap: var(--spacing-lg);
  overflow: hidden;
}

.help-sidebar {
  width: 250px;
  flex-shrink: 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  overflow-y: auto;
}

.sidebar-section {
  margin-bottom: var(--spacing-lg);
}

.sidebar-section h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  font-size: var(--font-size-sm);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.help-nav {
  list-style: none;
}

.help-nav li {
  padding: var(--spacing-sm);
  cursor: pointer;
  transition: all 0.2s;
  color: var(--terminal-green);
  margin-bottom: var(--spacing-xs);
}

.help-nav li:hover {
  background: var(--bg-tertiary);
}

.help-nav li.active {
  background: var(--bg-tertiary);
  border-left: 3px solid var(--terminal-green);
  padding-left: calc(var(--spacing-sm) - 3px);
}

.shortcuts-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.shortcut-item {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-size-sm);
}

.shortcut-item .keys {
  color: var(--terminal-cyan);
  font-weight: bold;
}

.shortcut-item .desc {
  color: var(--terminal-gray);
}

.help-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.help-section {
  margin-bottom: var(--spacing-xl);
  scroll-margin-top: var(--spacing-md);
}

.help-section h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
  font-size: var(--font-size-lg);
}

.section-content {
  line-height: 1.6;
}

.quick-start-steps {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.step {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.step-number {
  width: 30px;
  height: 30px;
  background: var(--terminal-green);
  color: var(--bg-primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  flex-shrink: 0;
}

.step-content h4 {
  color: var(--terminal-green);
  margin-bottom: var(--spacing-sm);
}

.command-table {
  overflow-x: auto;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--spacing-md);
}

.tool-item {
  background: var(--bg-tertiary);
  padding: var(--spacing-md);
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.tool-name {
  color: var(--terminal-cyan);
  font-weight: bold;
}

.badge {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  border-radius: 4px;
}

.badge.readonly {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
  border: 1px solid var(--terminal-green);
}

.tool-desc {
  color: var(--terminal-green);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
}

.tool-params {
  font-size: var(--font-size-xs);
  color: var(--terminal-gray);
}

.params-label {
  color: var(--terminal-amber);
}

.config-example {
  margin: var(--spacing-md) 0;
}

.code-block {
  background: var(--bg-primary);
  padding: var(--spacing-md);
  border-radius: 4px;
  font-family: var(--font-mono);
  color: var(--terminal-green);
  overflow-x: auto;
}

.config-options ul {
  list-style: none;
  padding: 0;
}

.config-options li {
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  margin-bottom: var(--spacing-sm);
  border-radius: 4px;
}

.config-options strong {
  color: var(--terminal-cyan);
}

.troubleshooting-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.issue-item {
  background: var(--bg-tertiary);
  padding: var(--spacing-md);
  border-radius: 4px;
}

.issue-title {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-sm);
}

.issue-solution p {
  margin-bottom: var(--spacing-sm);
  color: var(--terminal-green);
}

.api-endpoints {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.endpoint {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  border-radius: 4px;
  align-items: center;
}

.method {
  background: var(--terminal-green);
  color: var(--bg-primary);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: 4px;
  font-weight: bold;
  font-size: var(--font-size-xs);
  min-width: 60px;
  text-align: center;
}

.path {
  color: var(--terminal-cyan);
  font-family: var(--font-mono);
  min-width: 150px;
}

.endpoint .desc {
  color: var(--terminal-gray);
  flex: 1;
}

.changelog-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.release-item {
  background: var(--bg-tertiary);
  padding: var(--spacing-md);
  border-radius: 4px;
}

.release-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
}

.version {
  color: var(--terminal-green);
  font-weight: bold;
}

.date {
  color: var(--terminal-gray);
}

.release-changes {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.change-item {
  display: flex;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.change-type {
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: 4px;
  font-size: var(--font-size-xs);
  min-width: 50px;
  text-align: center;
}

.change-type.新增 {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
  border: 1px solid var(--terminal-green);
}

.change-type.优化 {
  background: rgba(51, 153, 255, 0.1);
  color: var(--terminal-blue);
  border: 1px solid var(--terminal-blue);
}

.change-type.修复 {
  background: rgba(255, 176, 0, 0.1);
  color: var(--terminal-amber);
  border: 1px solid var(--terminal-amber);
}

.change-desc {
  color: var(--terminal-green);
}

@media (max-width: 768px) {
  .help-header {
    flex-direction: column;
    gap: var(--spacing-md);
    align-items: flex-start;
  }
  
  .help-search {
    width: 100%;
  }
  
  .help-content {
    flex-direction: column;
  }
  
  .help-sidebar {
    width: 100%;
    height: auto;
    max-height: 200px;
  }
  
  .help-nav {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-sm);
  }
  
  .help-nav li {
    padding: var(--spacing-xs) var(--spacing-sm);
    background: var(--bg-tertiary);
    border-radius: 4px;
  }
  
  .help-nav li.active {
    border-left: none;
    padding-left: var(--spacing-sm);
  }
  
  .tools-grid {
    grid-template-columns: 1fr;
  }
  
  .endpoint {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
  }
  
  .method, .path {
    min-width: auto;
  }
}
</style>