<template>
  <div class="desktop-onboarding" :data-theme="theme">
    <header class="ob-header">
      <div class="ob-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18"><path d="M12 2 2 7l10 5 10-5-10-5z"/><path d="m2 17 10 5 10-5"/><path d="m2 12 10 5 10-5"/></svg>
        <span>Loopra 引导</span>
      </div>
      <button class="ob-close" type="button" title="关闭" @click="closeWindow">×</button>
    </header>

    <div class="ob-body">
      <!-- 左侧步骤导航 -->
      <aside class="ob-sidebar">
        <button
          v-for="(step, idx) in steps"
          :key="idx"
          type="button"
          class="ob-step"
          :class="{ active: currentStep === idx, done: isStepDone(idx) }"
          :disabled="busy"
          @click="goToStep(idx)"
        >
          <span class="ob-step-index">
            <svg v-if="isStepDone(idx)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            <template v-else>{{ idx + 1 }}</template>
          </span>
          <span class="ob-step-label">{{ step }}</span>
        </button>
      </aside>

      <!-- 右侧内容 -->
      <main class="ob-content">
        <!-- 步骤 0：欢迎 -->
        <section v-if="currentStep === 0" class="ob-page">
          <h2 class="ob-page-title">欢迎使用 Loopra 桌面版</h2>
          <p class="ob-page-desc">几步完成基础配置，把其他 Agent 工具中的会话、Skills、AGENTS.md / CLAUDE.md 与 MCP 配置迁移过来。</p>

          <div class="ob-service-card" :class="serviceOk ? 'ok' : ''">
            <span class="ob-service-dot" />
            <span v-if="serviceChecking">正在检测核心服务...</span>
            <template v-else>
              <span v-if="serviceOk">核心服务运行正常（端口 {{ servicePort }}）</span>
              <span v-else>核心服务未就绪，部分步骤可能无法完成</span>
            </template>
          </div>

          <div class="ob-feature-grid">
            <div class="ob-feature" v-for="item in features" :key="item.title">
              <div class="ob-feature-icon" v-html="item.icon" />
              <div>
                <div class="ob-feature-title">{{ item.title }}</div>
                <div class="ob-feature-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>

          <p class="ob-hint">引导页仅完成基础配置，之后可在主界面「设置」中继续调整。</p>
        </section>

        <!-- 步骤 1：迁移会话 -->
        <section v-else-if="currentStep === 1" class="ob-page">
          <h2 class="ob-page-title">迁移会话</h2>
          <p class="ob-page-desc">从其他 Agent 工具（Claude Code / Codex / Cursor 等）的会话记录中选择要迁移的会话，复制到当前工作区。目标工作区：<b class="ob-path">{{ workspacePath || '未获取到' }}</b></p>

          <div class="ob-field">
            <label class="ob-field-label">选择源目录</label>
            <div class="ob-dir-row">
              <button
                v-for="dir in dirs?.candidateAgentDirs || []"
                :key="dir.dir"
                type="button"
                class="ob-chip"
                :class="{ active: sourceDir === dir.path }"
                :disabled="!dir.exists || busy"
                @click="chooseDir(dir.path)"
              >
                {{ dir.label }}
              </button>
              <button type="button" class="ob-chip ob-chip-browse" :disabled="busy" @click="chooseDir('')">浏览...</button>
            </div>
            <div v-if="sourceDir" class="ob-dir-current">已选择：<span class="ob-path">{{ sourceDir }}</span></div>
          </div>

          <div v-if="scanning" class="ob-loading">正在扫描会话文件...</div>

          <div v-else-if="scanResult" class="ob-list-card">
            <div class="ob-list-head">
              <span>发现 {{ scanResult.sessions.length }} 个会话文件</span>
              <label v-if="scanResult.sessions.length" class="ob-check-all">
                <input v-model="sessionsAllChecked" type="checkbox" @change="toggleSessionsAll" />
                全选
              </label>
            </div>
            <div v-if="!scanResult.sessions.length" class="ob-empty">未在所选目录发现会话文件（*.jsonl）</div>
            <div v-for="session in scanResult.sessions" :key="session.path" class="ob-list-item">
              <label class="ob-item-label">
                <input v-model="sessionsSelected" type="checkbox" :value="session" />
                <span class="ob-item-main">
                  <span class="ob-item-name">{{ session.name }}</span>
                  <span class="ob-item-meta">{{ formatSize(session.size) }} · {{ formatTime(session.mtime) }}</span>
                </span>
              </label>
            </div>
          </div>

          <div v-if="sessionsResult" class="ob-result-card">
            <div v-for="r in sessionsResult" :key="r.name" class="ob-result-row" :class="r.ok ? 'ok' : 'err'">
              <span class="ob-result-mark">{{ r.ok ? '✓' : '✕' }}</span>
              <span class="ob-item-name">{{ r.name }}</span>
              <span class="ob-result-msg">{{ r.ok ? sessionResultText(r) : (r.error || '导入失败') }}</span>
            </div>
          </div>

          <div v-if="!workspaceHash" class="ob-hint">未获取到工作区信息（核心服务可能尚未就绪），暂时无法迁移会话。</div>

          <div class="ob-action-row">
            <button class="btn btn-primary" :disabled="busy || !sessionsSelected.length || !workspaceHash" @click="importSessions">
              {{ sessionsSelected.length ? `迁移 ${sessionsSelected.length} 个会话` : '迁移会话' }}
            </button>
          </div>
        </section>

        <!-- 步骤 2：设置模型 -->
        <section v-else-if="currentStep === 2" class="ob-page">
          <h2 class="ob-page-title">设置模型</h2>
          <p class="ob-page-desc">配置 API 地址与密钥，探测可用模型并选择默认模型。保存后立即生效。</p>

          <div class="ob-form">
            <div class="ob-field">
              <label class="ob-field-label">渠道名称</label>
              <input v-model="modelForm.channelName" class="ob-input" type="text" placeholder="例如：默认渠道" maxlength="60" />
            </div>
            <div class="ob-field">
              <label class="ob-field-label">API 地址（Base URL）</label>
              <input v-model="modelForm.baseUrl" class="ob-input" type="text" placeholder="https://api.deepseek.com/v1" />
            </div>
            <div class="ob-field">
              <label class="ob-field-label">API 密钥</label>
              <input v-model="modelForm.apiKey" class="ob-input" type="password" :placeholder="existingChannelSecret ? '已配置（留空保持不变）' : 'sk-...'" />
            </div>
            <div class="ob-field-row">
              <button class="btn" :disabled="probing || !modelForm.baseUrl" @click="probeModels">
                {{ probing ? '探测中...' : '探测可用模型' }}
              </button>
              <span v-if="remoteModels.length" class="ob-inline-hint">发现 {{ remoteModels.length }} 个模型</span>
            </div>
          </div>

          <div v-if="remoteModels.length" class="ob-list-card">
            <div class="ob-list-head"><span>选择默认模型</span></div>
            <label v-for="model in remoteModels" :key="model" class="ob-item-label">
              <input v-model="selectedModel" type="radio" :value="model" />
              <span class="ob-item-name">{{ model }}</span>
            </label>
          </div>

          <div v-if="modelSaved" class="ob-success-banner">✓ 模型配置已保存并生效（当前模型：{{ selectedModel }}）</div>

          <div class="ob-action-row">
            <button class="btn btn-primary" :disabled="busy || !remoteModels.length || !selectedModel" @click="saveModel">保存模型配置</button>
          </div>
        </section>

        <!-- 步骤 3：导入 Skills -->
        <section v-else-if="currentStep === 3" class="ob-page">
          <h2 class="ob-page-title">导入 Skills</h2>
          <p class="ob-page-desc">扫描外部 Agent 目录中的技能（含 SKILL.md 的目录），选择后导入到 Loopra 技能库。</p>

          <div class="ob-field">
            <label class="ob-field-label">选择源目录</label>
            <div class="ob-dir-row">
              <button
                v-for="dir in dirs?.candidateAgentDirs || []"
                :key="dir.dir"
                type="button"
                class="ob-chip"
                :class="{ active: sourceDir === dir.path }"
                :disabled="!dir.exists || busy"
                @click="chooseDir(dir.path)"
              >
                {{ dir.label }}
              </button>
              <button type="button" class="ob-chip ob-chip-browse" :disabled="busy" @click="chooseDir('')">浏览...</button>
            </div>
            <div v-if="sourceDir" class="ob-dir-current">已选择：<span class="ob-path">{{ sourceDir }}</span></div>
          </div>

          <div class="ob-field">
            <label class="ob-field-label">导入方式</label>
            <div class="ob-mode-row">
              <label class="ob-mode-option" :class="{ active: skillMode === 'hardlink' }">
                <input v-model="skillMode" type="radio" value="hardlink" />
                <span class="ob-mode-name">硬链接（推荐）</span>
                <span class="ob-mode-desc">节省磁盘空间；跨盘符失败时自动回退为复制</span>
              </label>
              <label class="ob-mode-option" :class="{ active: skillMode === 'copy' }">
                <input v-model="skillMode" type="radio" value="copy" />
                <span class="ob-mode-name">复制</span>
                <span class="ob-mode-desc">完整复制文件，与源目录互不影响</span>
              </label>
            </div>
          </div>

          <div v-if="scanning" class="ob-loading">正在扫描 Skills...</div>

          <div v-else-if="scanResult" class="ob-list-card">
            <div class="ob-list-head">
              <span>发现 {{ scanResult.skills.length }} 个 Skills</span>
              <label v-if="scanResult.skills.length" class="ob-check-all">
                <input v-model="skillsAllChecked" type="checkbox" @change="toggleSkillsAll" />
                全选
              </label>
            </div>
            <div v-if="!scanResult.skills.length" class="ob-empty">未在所选目录发现 Skills（未找到 SKILL.md）</div>
            <div v-for="skill in scanResult.skills" :key="skill.sourceDir" class="ob-list-item">
              <label class="ob-item-label">
                <input v-model="skillsSelected" type="checkbox" :value="skill" />
                <span class="ob-item-main">
                  <span class="ob-item-name">{{ skill.name }}</span>
                  <span class="ob-item-meta">{{ skill.description || skill.sourceDir }}</span>
                </span>
              </label>
            </div>
          </div>

          <div v-if="skillsResult" class="ob-result-card">
            <div v-for="r in skillsResult" :key="r.name" class="ob-result-row" :class="r.ok ? 'ok' : 'err'">
              <span class="ob-result-mark">{{ r.ok ? '✓' : '✕' }}</span>
              <span class="ob-item-name">{{ r.name }}</span>
              <span class="ob-result-msg">{{ r.ok ? (r.mode === 'hardlink' ? '已硬链接' : '已复制') : (r.error || '导入失败') }}</span>
            </div>
          </div>

          <div class="ob-action-row">
            <button class="btn btn-primary" :disabled="busy || !skillsSelected.length" @click="importSkills">
              {{ skillsSelected.length ? `导入 ${skillsSelected.length} 个 Skills` : '导入 Skills' }}
            </button>
          </div>
        </section>

        <!-- 步骤 4：迁移规则文件（AGENTS.md / CLAUDE.md） -->
        <section v-else-if="currentStep === 4" class="ob-page">
          <h2 class="ob-page-title">迁移 AGENTS.md / CLAUDE.md</h2>
          <p class="ob-page-desc">从其他 Agent 导入规则文件到当前 Loopra 工作区根目录：选择项目目录（曾用 Codex / Claude Code 等开发的目录）扫描，或直接选用下方的全局规则。</p>

          <div v-if="candidateRuleFiles.length" class="ob-field">
            <label class="ob-field-label">全局规则（自动检测）</label>
            <div class="ob-dir-row">
              <button
                v-for="file in candidateRuleFiles"
                :key="file.path"
                type="button"
                class="ob-chip"
                :class="{ active: agentsMdSelected?.path === file.path }"
                @click="selectAgentsMd({path: file.path, name: pathBasename(file.path)})"
              >
                {{ file.label }}
              </button>
            </div>
          </div>

          <div class="ob-field">
            <label class="ob-field-label">选择源文件（项目目录或 Agent 配置目录）</label>
            <div class="ob-dir-row">
              <button
                v-for="file in (scanResult?.agentsMd || [])"
                :key="file.path"
                type="button"
                class="ob-chip"
                :class="{ active: agentsMdSelected?.path === file.path }"
                @click="selectAgentsMd(file)"
              >
                {{ file.name }}（{{ dirName(file.path) }}）
              </button>
              <button type="button" class="ob-chip ob-chip-browse" @click="browseAgentsMd">浏览...</button>
            </div>
            <div v-if="!(scanResult?.agentsMd || []).length" class="ob-dir-current">选择源目录后会自动扫描其中的 AGENTS.md / CLAUDE.md；也可直接点击「浏览...」选取文件。</div>
          </div>

          <div v-if="agentsMdSelected" class="ob-field">
            <label class="ob-field-label">预览（前 256KB）</label>
            <pre class="ob-preview">{{ agentsMdPreview || '（无法读取或内容为空）' }}</pre>
          </div>

          <div class="ob-field">
            <label class="ob-field-label">目标位置</label>
            <div class="ob-dir-current">将写入：<span class="ob-path">{{ workspacePath ? workspacePath + '\\' + (agentsMdSelected ? agentsMdFileName : 'AGENTS.md') : '未获取到工作区路径' }}</span></div>
            <label class="ob-inline-check">
              <input v-model="overwriteAgentsMd" type="checkbox" />
              目标已存在同名文件时覆盖
            </label>
          </div>

          <div v-if="agentsMdResult" class="ob-result-card">
            <div class="ob-result-row" :class="agentsMdResult.ok ? 'ok' : 'err'">
              <span class="ob-result-mark">{{ agentsMdResult.ok ? '✓' : '✕' }}</span>
              <span class="ob-result-msg">{{ agentsMdResult.ok ? '已迁移到 ' + agentsMdResult.path : (agentsMdResult.error || '迁移失败') }}</span>
            </div>
          </div>

          <div class="ob-action-row">
            <button class="btn btn-primary" :disabled="busy || !agentsMdSelected || !workspacePath" @click="importAgentsMd">迁移到工作区</button>
          </div>
        </section>

        <!-- 步骤 5：迁移 MCP -->
        <section v-else-if="currentStep === 5" class="ob-page">
          <h2 class="ob-page-title">迁移 MCP</h2>
          <p class="ob-page-desc">从外部 Agent 的 MCP 配置文件解析服务器列表，勾选后逐条注册到 Loopra。支持 .claude.json / mcp.json / config.toml。</p>

          <div class="ob-field">
            <label class="ob-field-label">选择配置文件</label>
            <div class="ob-dir-row">
              <button
                v-for="file in mcpConfigCandidates"
                :key="file.path"
                type="button"
                class="ob-chip"
                :class="{ active: mcpConfigFile === file.path }"
                @click="parseMcpFile(file.path)"
              >
                {{ file.label }}
              </button>
              <button type="button" class="ob-chip ob-chip-browse" @click="browseMcpConfig">浏览...</button>
            </div>
          </div>

          <div v-if="parsedServers.length" class="ob-list-card">
            <div class="ob-list-head">
              <span>解析到 {{ parsedServers.length }} 个 MCP 服务器</span>
              <label class="ob-check-all">
                <input v-model="mcpAllChecked" type="checkbox" @change="toggleMcpAll" />
                全选
              </label>
            </div>
            <div v-for="server in parsedServers" :key="server.name" class="ob-list-item">
              <label class="ob-item-label">
                <input v-model="server.selected" type="checkbox" />
                <span class="ob-item-main">
                  <span class="ob-item-name">{{ server.name }}
                    <span v-if="existingMcpNames.has(server.name)" class="ob-tag-warn">已存在</span>
                  </span>
                  <span class="ob-item-meta">{{ mcpServerDesc(server) }}</span>
                </span>
              </label>
            </div>
          </div>

          <div class="ob-field">
            <label class="ob-inline-check">
              <input v-model="mcpCheckFirst" type="checkbox" />
              导入前先检测连接（较慢，建议开启）
            </label>
          </div>

          <div v-if="mcpResults" class="ob-result-card">
            <div v-for="r in mcpResults" :key="r.name" class="ob-result-row" :class="r.ok ? 'ok' : 'err'">
              <span class="ob-result-mark">{{ r.ok ? '✓' : '✕' }}</span>
              <span class="ob-item-name">{{ r.name }}</span>
              <span class="ob-result-msg">{{ r.ok ? '已注册' : (r.error || '迁移失败') }}</span>
            </div>
          </div>

          <div class="ob-action-row">
            <button class="btn btn-primary" :disabled="busy || !parsedServers.length" @click="importMcp">开始迁移 MCP</button>
          </div>
        </section>

        <!-- 步骤 6：完成 -->
        <section v-else class="ob-page">
          <h2 class="ob-page-title">完成！</h2>
          <p class="ob-page-desc">以下是本次引导的完成情况：</p>

          <div class="ob-summary-card">
            <div class="ob-summary-row"><span>迁移会话</span><span>{{ summaryText(stepDone[1], sessionsOkCount) }}</span></div>
            <div class="ob-summary-row"><span>设置模型</span><span>{{ summaryText(stepDone[2], selectedModel ? `当前：${selectedModel}` : '') }}</span></div>
            <div class="ob-summary-row"><span>导入 Skills</span><span>{{ summaryText(stepDone[3], skillsOkCount) }}</span></div>
            <div class="ob-summary-row"><span>迁移规则文件</span><span>{{ summaryText(stepDone[4], agentsMdResult?.ok ? '已迁移' : '') }}</span></div>
            <div class="ob-summary-row"><span>迁移 MCP</span><span>{{ summaryText(stepDone[5], mcpOkCount) }}</span></div>
          </div>

          <p class="ob-hint">点击「完成」关闭引导页，开始使用 Loopra。未完成的步骤随时可通过主窗口标题栏的引导按钮重新打开。</p>
        </section>
      </main>
    </div>

    <footer class="ob-footer">
      <button v-if="currentStep > 0 && currentStep < 6" class="btn" :disabled="busy" @click="goToStep(currentStep - 1)">上一步</button>
      <span class="ob-footer-spacer" />
      <button v-if="currentStep > 0 && currentStep < 6" class="btn btn-ghost" :disabled="busy" @click="skipStep">跳过</button>
      <button class="btn btn-primary" :disabled="busy || (currentStep === 0 && serviceChecking)" @click="nextStep">
        {{ currentStep === 6 ? '完成' : '下一步' }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import {ref, reactive, computed, onMounted, watch} from 'vue'
import {message} from 'ant-design-vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI, mcpAPI, agentAPI, systemAPI} from './services/api'
import {platform} from './services/platform'

const store = useAppStore()
const theme = computed(() => store.settings.theme)

const steps = ['欢迎', '迁移会话', '设置模型', '导入 Skills', '迁移规则文件', '迁移 MCP', '完成']

const features = [
  {
    title: '迁移会话',
    desc: '从其他 Agent 工具导入历史会话',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 17 10 11 13 14 16 9"/><polyline points="16 4 20 4 20 8"/><path d="M4 4h6"/><path d="M20 16v4H4"/></svg>'
  },
  {
    title: '设置模型',
    desc: '配置 API 地址并选择默认模型',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>'
  },
  {
    title: '导入 Skills',
    desc: '从外部 Agent 复制或硬链接技能库',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>'
  },
  {
    title: '迁移规则文件与 MCP',
    desc: '带入 AGENTS.md / CLAUDE.md 与外部工具连接',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.2 15.89A3.75 3.75 0 0 1 18 19.5H6a3.75 3.75 0 0 1 0-7.5h.22"/><path d="M2.8 8.11A3.75 3.75 0 0 1 6 4.5h12a3.75 3.75 0 0 1 0 7.5h-.22"/></svg>'
  }
]

const currentStep = ref(0)
const stepDone = reactive({})
const busy = ref(false)

// 目录信息与扫描
const dirs = ref(null)
const sourceDir = ref('')
const scanResult = ref(null)
const scanning = ref(false)

// 服务状态
const serviceOk = ref(false)
const serviceChecking = ref(true)
const servicePort = ref(0)

// 会话迁移
const sessionsSelected = ref([])
const sessionsAllChecked = ref(false)
const sessionsResult = ref(null)
const workspaceHash = ref('')
const workspacePath = ref('')

// 模型设置
const modelForm = reactive({channelName: '默认渠道', baseUrl: '', apiKey: ''})
const existingChannelSecret = ref(false)
const existingChannelId = ref('')
const probing = ref(false)
const remoteModels = ref([])
const selectedModel = ref('')
const modelSaved = ref(false)

// Skills
const skillMode = ref('hardlink')
const skillsSelected = ref([])
const skillsAllChecked = ref(false)
const skillsResult = ref(null)

// AGENTS.md
const agentsMdSelected = ref(null)
const agentsMdPreview = ref('')
const overwriteAgentsMd = ref(false)
const agentsMdResult = ref(null)

// 选中规则文件的文件名（AGENTS.md / CLAUDE.md 等），用于目标路径展示
const agentsMdFileName = computed(() => {
  if (!agentsMdSelected.value?.path) return 'AGENTS.md'
  return pathBasename(agentsMdSelected.value.path)
})

// MCP
const mcpConfigFile = ref('')
const parsedServers = ref([])
const mcpAllChecked = ref(false)
const mcpCheckFirst = ref(true)
const mcpResults = ref(null)
const existingMcpNames = ref(new Set())

const onboarding = () => window.electronAPI?.onboarding

// IPC 传参前转换为可结构化克隆的普通对象：Vue ref/reactive 的响应式 Proxy
// 无法被 Electron 结构化克隆（报 An object could not be cloned），JSON 深拷贝去除代理
const toPlain = (value) => JSON.parse(JSON.stringify(value))

const mcpConfigCandidates = computed(() => {
  const fromDirs = scanResult?.mcpConfigs || []
  const homeCandidates = dirs.value?.candidateMcpConfigs || []
  const merged = []
  const seen = new Set()
  for (const item of [...fromDirs, ...homeCandidates]) {
    if (seen.has(item.path)) continue
    seen.add(item.path)
    merged.push({...item, label: item.label || item.name})
  }
  return merged
})

// 自动检测到的常见 Agent 全局规则文件（Codex / Claude Code 等）
const candidateRuleFiles = computed(() => (dirs.value?.candidateRuleFiles || []).filter((f) => f.exists))

const sessionsOkCount = computed(() => (sessionsResult.value || []).filter((r) => r.ok).length)
const skillsOkCount = computed(() => (skillsResult.value || []).filter((r) => r.ok).length)
const mcpOkCount = computed(() => (mcpResults.value || []).filter((r) => r.ok).length)

function isStepDone(idx) {
  return stepDone[idx] === true
}

function goToStep(idx) {
  if (busy.value) return
  if (idx >= 0 && idx <= 6) currentStep.value = idx
}

function skipStep() {
  if (currentStep.value < 6) currentStep.value += 1
}

function nextStep() {
  if (currentStep.value < 6) currentStep.value += 1
  else finishOnboarding()
}

async function closeWindow() {
  try {
    if (platform.isElectron && onboarding()?.close) {
      await onboarding().close()
      return
    }
  } catch {}
  window.close()
}

function finishOnboarding() {
  try {
    localStorage.setItem('loopra-onboarding-done', '1')
  } catch {}
  closeWindow()
}

// ==================== 服务状态 ====================

async function checkService() {
  serviceChecking.value = true
  try {
    if (platform.isElectron && onboarding()?.getDirs) {
      // Electron 下优先经服务端口探测
      const port = await window.electronAPI.loopraWebService.getCurrentPort()
      if (port > 0) {
        const resp = await fetch(`http://127.0.0.1:${port}/api/system/health`, {signal: AbortSignal.timeout(3000)})
        serviceOk.value = resp.ok
        servicePort.value = port
      }
    } else {
      const res = await systemAPI.healthCheck()
      serviceOk.value = res.success === true
    }
  } catch {
    serviceOk.value = false
  } finally {
    serviceChecking.value = false
    // 服务已就绪后补拉工作区信息（首次运行时引导窗口可能早于服务启动完成）
    if (serviceOk.value && (!workspacePath.value || !workspaceHash.value)) {
      void loadWorkspaceInfo()
    }
  }
}

// ==================== 目录选择与扫描 ====================

async function loadDirs() {
  if (!platform.isElectron || !onboarding()) return
  try {
    dirs.value = await onboarding().getDirs()
  } catch {}
}

async function chooseDir(dirPath) {
  let target = dirPath
  if (!target) {
    try {
      target = await onboarding()?.pickDir('选择外部 Agent 目录')
    } catch {}
    if (!target) return
  }
  sourceDir.value = target
  scanning.value = true
  try {
    scanResult.value = await onboarding()?.scanDir(target)
    // 目录变更后旧选中项/旧结果指向已不存在的文件，清空避免误导入上一目录的文件
    sessionsSelected.value = []
    skillsSelected.value = []
    sessionsResult.value = null
    skillsResult.value = null
  } catch (e) {
    message.error('扫描失败：' + (e.message || '未知错误'))
    scanResult.value = null
  } finally {
    scanning.value = false
  }
}

function dirName(filePath) {
  if (!filePath) return ''
  const parts = String(filePath).split(/[\\/]/)
  return parts[parts.length - 2] || ''
}

function pathBasename(filePath) {
  if (!filePath) return ''
  const parts = String(filePath).split(/[\\/]/)
  return parts[parts.length - 1] || ''
}

// ==================== 步骤 1：迁移会话 ====================

function toggleSessionsAll() {
  if (sessionsAllChecked.value) {
    sessionsSelected.value = scanResult.value.sessions.map((s) => ({path: s.path, name: s.name}))
  } else {
    sessionsSelected.value = []
  }
}

watch(() => sessionsSelected.value.length, (len) => {
  const total = scanResult.value?.sessions?.length || 0
  sessionsAllChecked.value = total > 0 && len === total
})

async function importSessions() {
  if (!sessionsSelected.value.length) {
    message.warning('请先勾选要迁移的会话')
    return
  }
  busy.value = true
  try {
    const result = await onboarding()?.importSessions({
      files: toPlain(sessionsSelected.value),
      workspaceHash: workspaceHash.value
    })
    sessionsResult.value = result || []
    const okCount = sessionsResult.value.filter((r) => r.ok).length
    if (okCount > 0) {
      stepDone[1] = true
      try {
        await sessionsAPI.list(workspaceHash.value)
      } catch {}
      message.success(`已导入 ${okCount} 个会话`)
    } else {
      message.warning('会话导入失败，请查看下方结果')
    }
  } catch (e) {
    message.error('导入失败：' + (e.message || '未知错误'))
  } finally {
    busy.value = false
  }
}

// ==================== 步骤 2：设置模型 ====================

async function loadCurrentModel() {
  try {
    const res = await configAPI.getConfig()
    const data = res.data || {}
    const channels = data.modelChannels || []
    const active = channels.find((c) => c.id === data.modelChannelId) || channels[0]
    if (active) {
      modelForm.channelName = active.name || '默认渠道'
      modelForm.baseUrl = active.baseUrl || ''
      // 后端返回掩码密钥：未配置为空串，已配置为 '****'（短密钥）或 sk-1****a2bc（长密钥）。
      // 非空即已配置，不能用 !== '****' 判断（短密钥会被误判为未配置）。
      existingChannelSecret.value = Boolean(active.apiKey)
      existingChannelId.value = active.id || ''
      selectedModel.value = data.model || ''
    }
  } catch {}
}

async function probeModels() {
  if (!modelForm.baseUrl.trim()) {
    message.warning('请先填写 API 地址')
    return
  }
  if (!modelForm.apiKey.trim() && !existingChannelSecret.value) {
    message.warning('请先填写 API 密钥')
    return
  }
  probing.value = true
  try {
    const res = await configAPI.probeRemoteModels({
      baseUrl: modelForm.baseUrl.trim(),
      apiKey: modelForm.apiKey.trim(),
      channelId: existingChannelId.value || undefined
    })
    if (res.success === false) throw new Error(res.message || '探测失败')
    remoteModels.value = (res.data || [])
      .map((m) => (typeof m === 'string' ? m.trim() : String(m?.name || '').trim()))
      .filter(Boolean)
    if (!selectedModel.value && remoteModels.value.length) selectedModel.value = remoteModels.value[0]
    if (remoteModels.value.length) message.success(`发现 ${remoteModels.value.length} 个模型，请选择默认模型`)
    else message.warning('未发现模型，请检查 API 地址与密钥')
  } catch (e) {
    message.error('探测失败：' + (e.message || '未知错误'))
  } finally {
    probing.value = false
  }
}

async function saveModel() {
  if (!remoteModels.value.length) {
    message.warning('请先探测可用模型')
    return
  }
  if (!selectedModel.value) {
    message.warning('请选择默认模型')
    return
  }
  busy.value = true
  try {
    // 读取现有渠道并只合并更新当前渠道。后端 modelChannels 是整体替换语义
    // （LoopraConfig.mergeModelChannelUpdates 只保留提交的渠道），
    // 只提交单个渠道会把用户已配置的其他渠道全部删掉。
    let existingChannels = []
    try {
      const current = await configAPI.getConfig()
      existingChannels = (current.data?.modelChannels || []).map((c) => ({
        id: c.id || '',
        name: c.name || '',
        baseUrl: c.baseUrl || '',
        apiProtocol: c.apiProtocol || 'chat_completions',
        apiKey: c.apiKey || '', // 掩码值原样回传，后端合并时会回退到已保存的密钥
        models: (c.models || []).map((m) => ({
          name: m.name,
          contextTokens: m.contextTokens ?? null,
          imageInput: m.imageInput ?? false,
          price: m.price ?? null
        }))
      }))
    } catch {}
    const channel = {
      id: existingChannelId.value || `channel-${Date.now()}`,
      name: modelForm.channelName.trim() || '默认渠道',
      baseUrl: modelForm.baseUrl.trim(),
      apiProtocol: 'chat_completions',
      apiKey: modelForm.apiKey.trim(),
      models: remoteModels.value.map((name) => ({name, contextTokens: null, imageInput: false}))
    }
    const channelIndex = existingChannels.findIndex((c) => c.id === channel.id)
    if (channelIndex >= 0) existingChannels[channelIndex] = channel
    else existingChannels.push(channel)
    const res = await configAPI.updateConfig({
      modelChannels: existingChannels,
      modelChannelId: channel.id,
      model: selectedModel.value
    })
    if (res.success === false) throw new Error(res.message || '保存失败')
    existingChannelId.value = channel.id
    existingChannelSecret.value = true
    modelForm.apiKey = ''
    modelSaved.value = true
    stepDone[2] = true
    message.success('模型配置已保存并生效')
  } catch (e) {
    message.error('保存失败：' + (e.message || '未知错误'))
  } finally {
    busy.value = false
  }
}

// ==================== 步骤 3：导入 Skills ====================

function toggleSkillsAll() {
  if (skillsAllChecked.value) {
    skillsSelected.value = scanResult.value.skills.map((s) => ({sourceDir: s.sourceDir, name: s.name}))
  } else {
    skillsSelected.value = []
  }
}

watch(() => skillsSelected.value.length, (len) => {
  const total = scanResult.value?.skills?.length || 0
  skillsAllChecked.value = total > 0 && len === total
})

async function importSkills() {
  if (!skillsSelected.value.length) {
    message.warning('请先勾选要导入的 Skills')
    return
  }
  busy.value = true
  try {
    const result = await onboarding()?.importSkills({
      items: toPlain(skillsSelected.value),
      mode: skillMode.value
    })
    skillsResult.value = result || []
    const okCount = skillsResult.value.filter((r) => r.ok).length
    if (okCount > 0) {
      stepDone[3] = true
      try {
        await agentAPI.getSkills() // 服务端自带技能池刷新
      } catch {}
      message.success(`已导入 ${okCount} 个 Skills`)
    } else {
      message.warning('Skills 导入失败，请查看下方结果')
    }
  } catch (e) {
    message.error('导入失败：' + (e.message || '未知错误'))
  } finally {
    busy.value = false
  }
}

// ==================== 步骤 4：迁移 AGENTS.md ====================

async function selectAgentsMd(file) {
  agentsMdSelected.value = file
  agentsMdResult.value = null
  try {
    agentsMdPreview.value = await onboarding()?.readTextFile({path: file.path}) || ''
  } catch (e) {
    agentsMdPreview.value = ''
    message.warning('预览失败：' + (e.message || '无法读取文件'))
  }
}

async function browseAgentsMd() {
  const filePath = await onboarding()?.pickFile('选择规则文件（AGENTS.md / CLAUDE.md）', [
    {name: 'Markdown', extensions: ['md']}
  ])
  if (filePath) await selectAgentsMd({path: filePath, name: pathBasename(filePath)})
}

async function importAgentsMd() {
  if (!agentsMdSelected.value) {
    message.warning('请先选择规则文件')
    return
  }
  if (!workspacePath.value) {
    message.warning('未获取到目标工作区路径')
    return
  }
  busy.value = true
  try {
    const result = await onboarding()?.importAgentsMd({
      sourcePath: agentsMdSelected.value.path,
      targetDir: workspacePath.value,
      overwrite: overwriteAgentsMd.value
    })
    agentsMdResult.value = result
    if (result?.ok) {
      stepDone[4] = true
      message.success(`${agentsMdFileName} 已迁移`)
    } else {
      message.warning(result?.error || '迁移失败')
    }
  } catch (e) {
    message.error('迁移失败：' + (e.message || '未知错误'))
  } finally {
    busy.value = false
  }
}

// ==================== 步骤 5：迁移 MCP ====================

async function parseMcpFile(filePath) {
  mcpConfigFile.value = filePath
  parsedServers.value = []
  mcpResults.value = null
  try {
    const servers = await onboarding()?.parseMcpConfig(filePath)
    parsedServers.value = (servers || []).map((s) => ({...s, selected: true}))
    if (parsedServers.value.length) message.success(`解析到 ${parsedServers.value.length} 个 MCP 服务器`)
    else message.warning('配置文件中未发现 MCP 服务器')
  } catch (e) {
    message.error('解析失败：' + (e.message || '未知错误'))
  }
}

async function browseMcpConfig() {
  const filePath = await onboarding()?.pickFile('选择 MCP 配置文件', [
    {name: 'JSON / TOML', extensions: ['json', 'toml']}
  ])
  if (filePath) await parseMcpFile(filePath)
}

function toggleMcpAll() {
  for (const server of parsedServers.value) server.selected = mcpAllChecked.value
}

// 单个勾选变化时反向同步“全选”状态（与会话/Skills 步骤保持一致）
watch(() => parsedServers.value.map((s) => s.selected), (selected) => {
  mcpAllChecked.value = selected.length > 0 && selected.every(Boolean)
})

async function loadExistingMcp() {
  try {
    const res = await mcpAPI.listServers()
    existingMcpNames.value = new Set((res.data || []).map((s) => s.name))
  } catch {}
}

async function importMcp() {
  const selected = parsedServers.value.filter((s) => s.selected)
  if (!selected.length) {
    message.warning('请先勾选要迁移的 MCP 服务器')
    return
  }
  busy.value = true
  mcpResults.value = null
  const results = []
  try {
    for (const server of selected) {
      if (existingMcpNames.value.has(server.name)) {
        results.push({name: server.name, ok: false, error: '已存在同名 MCP 服务器'})
        continue
      }
      let ok = true
      let error = ''
      if (mcpCheckFirst.value) {
        try {
          const check = await mcpAPI.checkConnection(server)
          if (check.success === false) {
            ok = false
            error = check.message || '连接检测失败'
          }
        } catch (e) {
          ok = false
          error = e.message || '连接检测异常'
        }
      }
      if (ok) {
        try {
          const add = await mcpAPI.addServer(server)
          if (add.success === false) {
            ok = false
            error = add.message || '添加失败'
          } else {
            existingMcpNames.value.add(server.name)
          }
        } catch (e) {
          ok = false
          error = e.message || '添加异常'
        }
      }
      results.push({name: server.name, ok, error})
    }
    mcpResults.value = results
    const okCount = results.filter((r) => r.ok).length
    if (okCount > 0) {
      stepDone[5] = true
      message.success(`已迁移 ${okCount} 个 MCP 服务器`)
    }
  } finally {
    busy.value = false
  }
}

function mcpServerDesc(server) {
  if (!server) return ''
  const type = server.type || 'stdio'
  const endpoint = server.url || (server.command ? `${server.command} ${(server.args || []).join(' ')}` : '')
  return `${type}${endpoint ? ' · ' + endpoint : ''}`
}

// ==================== 工具函数 ====================

function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

function formatTime(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return ''
  try {
    return new Date(ms).toLocaleString()
  } catch {
    return ''
  }
}

const SESSION_FORMAT_LABELS = { loopra: 'Loopra', claude: 'Claude Code', codex: 'Codex' }

// 会话导入结果描述：Loopra 原样导入；Claude Code / Codex 显示转换与跳过统计
function sessionResultText(r) {
  if (!r || !r.format) return '已导入'
  const label = SESSION_FORMAT_LABELS[r.format] || r.format
  let text = `已导入（${label}`
  if (r.converted) text += `，转换 ${r.converted} 条`
  if (r.skipped) text += `，跳过 ${r.skipped} 条`
  return text + '）'
}

function summaryText(done, detail) {
  if (done) return detail || '已完成'
  return '已跳过'
}

// 进入需要工作区信息的步骤时，若信息缺失则重试加载（服务可能刚启动完成）
watch(currentStep, (step) => {
  if ((step === 1 || step === 4) && (!workspacePath.value || !workspaceHash.value)) {
    void loadWorkspaceInfo()
  }
})

// ==================== 初始化 ====================

onMounted(async () => {
  await Promise.all([loadDirs(), checkService(), loadWorkspaceInfo(), loadCurrentModel(), loadExistingMcp()])
})

async function loadWorkspaceInfo() {
  try {
    const [wsRes, listRes] = await Promise.all([configAPI.getWorkspace(), configAPI.listWorkspaces()])
    const currentPath = wsRes.data
    workspacePath.value = currentPath || ''
    const found = (listRes.data || []).find((w) => w.path === currentPath)
    workspaceHash.value = found?.hash || ''
  } catch {}
}
</script>

<style scoped>
.desktop-onboarding {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
  color: var(--fg);
  font-size: 14px;
}

/* 标题栏 */
.ob-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  height: 44px;
  min-height: 44px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  -webkit-app-region: drag;
  user-select: none;
  flex-shrink: 0;
}

.ob-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.ob-title svg {
  color: var(--fg-3);
}

.ob-close {
  margin-left: auto;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--fg-3);
  font-size: 16px;
  cursor: pointer;
  -webkit-app-region: no-drag;
}

.ob-close:hover {
  background: var(--bg-3);
  color: var(--fg);
}

/* 主体 */
.ob-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 左侧步骤导航 */
.ob-sidebar {
  width: 172px;
  min-width: 172px;
  padding: 16px 10px;
  background: var(--bg-2);
  border-right: 1px solid var(--border);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ob-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--fg-3);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.ob-step:hover:not(:disabled) {
  background: var(--bg-3);
  color: var(--fg);
}

.ob-step.active {
  background: var(--accent-bg);
  color: var(--fg);
  font-weight: 600;
}

.ob-step.done {
  color: var(--fg-2);
}

.ob-step-index {
  width: 20px;
  height: 20px;
  min-width: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-3);
  color: var(--fg-3);
  font-size: 11px;
  font-weight: 600;
}

.ob-step.active .ob-step-index {
  background: var(--accent);
  color: #fff;
}

.ob-step.done .ob-step-index {
  background: var(--green);
  color: #fff;
}

.ob-step-index svg {
  width: 11px;
  height: 11px;
}

/* 右侧内容 */
.ob-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 20px 24px 28px;
}

.ob-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 560px;
}

.ob-page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--fg);
}

.ob-page-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--fg-3);
}

.ob-hint {
  margin: 0;
  font-size: 12px;
  color: var(--fg-4);
  line-height: 1.6;
}

.ob-path {
  font-weight: 600;
  color: var(--fg-2);
  word-break: break-all;
}

/* 服务状态 */
.ob-service-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-2);
  color: var(--fg-3);
  font-size: 13px;
}

.ob-service-card.ok {
  border-color: rgba(22, 163, 74, 0.4);
  background: var(--green-bg);
  color: var(--green);
}

.ob-service-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--fg-4);
  flex-shrink: 0;
}

.ob-service-card.ok .ob-service-dot {
  background: var(--green);
}

/* 功能卡片 */
.ob-feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.ob-feature {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 10px;
}

.ob-feature-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--accent-bg);
  color: var(--fg-2);
}

.ob-feature-icon svg {
  width: 16px;
  height: 16px;
}

.ob-feature-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.ob-feature-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--fg-4);
  line-height: 1.5;
}

/* 表单 */
.ob-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ob-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ob-field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-3);
}

.ob-input {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  color: var(--fg);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}

.ob-input:focus {
  border-color: var(--border-2);
}

.ob-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ob-inline-hint {
  font-size: 12px;
  color: var(--fg-4);
}

/* 目录选择 */
.ob-dir-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ob-chip {
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-2);
  color: var(--fg-2);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.ob-chip:hover:not(:disabled) {
  border-color: var(--border-2);
  background: var(--bg-3);
}

.ob-chip.active {
  border-color: var(--accent);
  background: var(--accent-bg);
  color: var(--fg);
}

.ob-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ob-chip-browse {
  border-style: dashed;
}

.ob-dir-current {
  font-size: 12px;
  color: var(--fg-4);
  word-break: break-all;
}

/* 导入方式选择 */
.ob-mode-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.ob-mode-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.ob-mode-option input {
  display: none;
}

.ob-mode-option.active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.ob-mode-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.ob-mode-desc {
  font-size: 12px;
  color: var(--fg-4);
  line-height: 1.5;
}

/* 列表卡片 */
.ob-list-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg);
}

.ob-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-3);
}

.ob-check-all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--fg-3);
  cursor: pointer;
}

.ob-list-item {
  border-bottom: 1px solid var(--border);
}

.ob-list-item:last-child {
  border-bottom: 0;
}

.ob-item-label {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.ob-item-label:hover {
  background: var(--bg-2);
}

.ob-item-label input {
  margin-top: 3px;
  accent-color: var(--accent);
}

.ob-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ob-item-name {
  font-size: 13px;
  color: var(--fg);
  word-break: break-all;
}

.ob-item-meta {
  font-size: 12px;
  color: var(--fg-4);
  word-break: break-all;
}

.ob-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-4);
}

.ob-loading {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--fg-3);
}

/* 结果卡片 */
.ob-result-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  max-height: 180px;
  overflow-y: auto;
}

.ob-result-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

.ob-result-row:last-child {
  border-bottom: 0;
}

.ob-result-row.ok {
  background: var(--green-bg);
  color: var(--green);
}

.ob-result-row.err {
  background: var(--red-bg);
  color: var(--red);
}

.ob-result-mark {
  font-weight: 700;
  flex-shrink: 0;
}

.ob-result-msg {
  font-size: 12px;
  opacity: 0.9;
  word-break: break-all;
}

/* 成功横幅 */
.ob-success-banner {
  padding: 10px 14px;
  border: 1px solid rgba(22, 163, 74, 0.4);
  border-radius: 10px;
  background: var(--green-bg);
  color: var(--green);
  font-size: 13px;
}

/* 预览 */
.ob-preview {
  max-height: 180px;
  overflow: auto;
  padding: 10px 12px;
  margin: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-2);
  color: var(--fg-3);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.ob-inline-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--fg-3);
  cursor: pointer;
}

.ob-inline-check input {
  accent-color: var(--accent);
}

.ob-tag-warn {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--yellow-bg);
  color: var(--yellow);
  font-size: 11px;
}

/* 总结卡片 */
.ob-summary-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}

.ob-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

.ob-summary-row:last-child {
  border-bottom: 0;
}

.ob-summary-row span:first-child {
  color: var(--fg-3);
}

.ob-summary-row span:last-child {
  color: var(--fg);
  font-weight: 600;
}

/* 操作按钮行 */
.ob-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ob-action-row .btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 120px;
}

/* 底部操作栏 */
.ob-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-2);
  flex-shrink: 0;
}

.ob-footer-spacer {
  flex: 1;
}

.ob-footer .btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
}

.ob-footer .btn-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.ob-footer .btn-primary:disabled {
  opacity: 0.5;
}
</style>
