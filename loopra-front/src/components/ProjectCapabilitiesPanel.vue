<template>
  <section class="project-capabilities-panel">
    <header class="project-capabilities-header">
      <div class="project-capabilities-title-wrap">
        <div>
          <h2>项目能力</h2>
          <p :title="data?.workspacePath || ''">{{ workspaceName || '当前项目' }}</p>
        </div>
      </div>
      <button
        class="icon-button"
        :class="{ loading }"
        type="button"
        title="重新加载项目能力（含 MCP）"
        aria-label="刷新项目能力"
        :disabled="loading || !workspaceHash"
        @click="refresh"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M20 12a8 8 0 1 1-2.34-5.66L20 8" />
          <path d="M20 4v4h-4" />
        </svg>
      </button>
    </header>

    <div class="project-capabilities-body">
      <div v-if="!workspaceHash" class="capabilities-state">请先选择项目</div>
      <div v-else-if="loading && !data" class="capabilities-state">
        <span class="capabilities-spinner" aria-hidden="true" />正在读取项目能力…
      </div>
      <div v-else-if="error" class="capabilities-state capabilities-state-error">
        <span>{{ error }}</span>
        <button type="button" @click="refresh">重试</button>
      </div>
      <template v-else>
        <div class="capabilities-summary">
          <div class="capabilities-summary-item">
            <strong>{{ projectSkills.length }}</strong><span>Skill</span>
          </div>
          <div class="capabilities-summary-item">
            <strong>{{ mcpServers.length }}</strong><span>MCP</span>
          </div>
          <div class="capabilities-summary-item">
            <strong>{{ mcpToolCount }}</strong><span>工具</span>
          </div>
        </div>

        <section class="capabilities-section">
          <div class="capabilities-section-head">
            <div class="capabilities-section-label">
              <strong>项目 Skill</strong>
            </div>
            <span class="capabilities-count">{{ projectSkills.length }}</span>
          </div>
          <div v-if="projectSkills.length === 0" class="capabilities-empty">
            {{ data?.projectSkillsDirectoryExists ? '目录中暂无 SKILL.md' : '未找到 .loopra/skills/' }}
          </div>
          <div v-else class="capabilities-list">
            <div v-for="skill in projectSkills" :key="`${skill.mountAlias}:${skill.name}`" class="capability-row" :title="skill.path || ''">
              <span class="capability-status-dot project-dot" aria-hidden="true" />
              <div class="capability-row-main">
                <strong>{{ skill.name }}</strong>
                <small>{{ skill.description || skill.path || '项目技能' }}</small>
              </div>
            </div>
          </div>
        </section>

        <section class="capabilities-section">
          <div class="capabilities-section-head">
            <div class="capabilities-section-label">
              <strong>项目 MCP</strong>
            </div>
            <span class="capabilities-count">{{ mcpServers.length }}</span>
          </div>
          <div v-if="mcpServers.length === 0" class="capabilities-empty">
            {{ data?.mcpConfigExists ? '配置文件存在，但没有可用服务器' : '未找到 .loopra/mcp-servers.json' }}
          </div>
          <div v-else class="mcp-list">
            <details v-for="server in mcpServers" :key="server.name" class="mcp-card">
              <summary class="mcp-card-summary">
                <span class="capability-status-dot" :class="server.loaded ? 'loaded-dot' : 'offline-dot'" aria-hidden="true" />
                <span class="mcp-card-main">
                  <strong>{{ server.name }}</strong>
                  <small>{{ formatType(server.type) }} · {{ server.toolCount }} 个工具</small>
                </span>
                <span class="mcp-status" :class="server.loaded ? 'loaded' : 'offline'">{{ mcpStatus(server) }}</span>
                <span class="mcp-chevron" aria-hidden="true">›</span>
              </summary>
              <div class="mcp-card-detail">
                <div v-if="server.toolNames?.length" class="mcp-tools">
                  <code v-for="tool in server.toolNames" :key="tool">{{ tool }}</code>
                </div>
                <span v-else-if="server.error" class="mcp-error">加载失败</span>
                <span v-else class="mcp-no-tools">暂无工具</span>
              </div>
            </details>
          </div>
        </section>

      </template>
    </div>
  </section>
</template>

<script setup>
import {computed, ref, watch} from 'vue'
import {agentAPI} from '../services/api'

const props = defineProps({
  workspaceHash: {type: String, default: null},
  workspaceName: {type: String, default: ''}
})

const data = ref(null)
const loading = ref(false)
const error = ref('')
let requestSerial = 0

const skills = computed(() => data.value?.skills || [])
const projectSkills = computed(() => skills.value.filter(skill => skill.scope === 'project'))
const mcpServers = computed(() => data.value?.mcpServers || [])
const mcpToolCount = computed(() => mcpServers.value.reduce((total, server) => total + (server.toolCount || 0), 0))

function formatType(type) {
  const labels = {stdio: 'stdio', sse: 'SSE', streamable: 'Streamable HTTP', http: 'HTTP'}
  return labels[type] || type || 'MCP'
}

function mcpStatus(server) {
  if (!server.enabled) return '已禁用'
  if (server.loaded) return '已连接'
  return server.error ? '加载失败' : '未加载'
}

async function loadCapabilities(loader) {
  const hash = props.workspaceHash
  if (!hash) {
    data.value = null
    error.value = ''
    return
  }
  const serial = ++requestSerial
  loading.value = true
  error.value = ''
  try {
    const response = await loader(hash)
    if (serial !== requestSerial) return
    if (!response?.success) throw new Error(response?.message || '项目能力读取失败')
    data.value = response.data || null
  } catch (cause) {
    if (serial === requestSerial) {
      error.value = cause?.message || '项目能力读取失败'
      data.value = null
    }
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

function load() {
  return loadCapabilities(hash => agentAPI.getProjectCapabilities(hash))
}

function refresh() {
  return loadCapabilities(hash => agentAPI.refreshProjectCapabilities(hash))
}

watch(() => props.workspaceHash, () => {
  data.value = null
  void load()
}, {immediate: true})

defineExpose({load, refresh})
</script>

<style scoped>
.project-capabilities-panel { display: flex; flex-direction: column; height: 100%; min-width: 0; color: var(--fg, #202124); background: var(--bg, #fff); }
.project-capabilities-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 14px 14px 12px; border-bottom: 1px solid var(--border, #e5e7eb); flex: 0 0 auto; }
.project-capabilities-title-wrap { min-width: 0; }
.project-capabilities-header h2 { margin: 0; color: var(--fg, #202124); font-size: 14px; font-weight: 600; line-height: 1.3; }
.project-capabilities-header p { max-width: 210px; margin: 3px 0 0; overflow: hidden; color: var(--fg-3, #727987); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.icon-button { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; padding: 0; border: 1px solid transparent; border-radius: 50%; color: var(--fg-4); background: transparent; cursor: pointer; transition: color var(--t), background-color var(--t), border-color var(--t), transform .12s ease; }
.icon-button:hover:not(:disabled) { border-color: color-mix(in srgb, var(--accent) 16%, var(--border)); color: var(--accent); background: color-mix(in srgb, var(--accent) 7%, transparent); }
.icon-button:active:not(:disabled) { transform: scale(.94); }
.icon-button:focus-visible { border-color: color-mix(in srgb, var(--accent) 48%, transparent); outline: none; box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 10%, transparent); }
.icon-button:disabled { cursor: default; }
.icon-button svg { width: 15px; height: 15px; transition: opacity var(--t); }
.icon-button.loading svg { opacity: 0; }
.icon-button.loading::after { position: absolute; width: 13px; height: 13px; box-sizing: border-box; border: 1.5px solid color-mix(in srgb, currentColor 24%, transparent); border-top-color: currentColor; border-radius: 50%; content: ''; animation: environment-refresh-spin .7s linear infinite; }
@keyframes environment-refresh-spin { to { transform: rotate(360deg); } }
@keyframes capabilities-spin { to { transform: rotate(360deg); } }
.project-capabilities-body { flex: 1; min-height: 0; overflow-y: auto; padding: 12px 14px 14px; }
.capabilities-state { display: flex; min-height: 120px; align-items: center; justify-content: center; gap: 7px; padding: 20px 12px; color: var(--fg-3, #727987); font-size: 12px; text-align: center; line-height: 1.6; }
.capabilities-state-error { flex-direction: column; color: var(--danger, #dc4c64); }
.capabilities-state-error button { padding: 4px 9px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; color: inherit; background: transparent; cursor: pointer; font: inherit; }
.capabilities-spinner { width: 12px; height: 12px; border: 1.5px solid var(--border, #e5e7eb); border-top-color: var(--fg-3, #727987); border-radius: 50%; animation: capabilities-spin 1s linear infinite; }
.capabilities-summary { display: flex; align-items: baseline; gap: 12px; margin: 0 2px 20px; padding: 0 0 10px; border-bottom: 1px solid var(--border, #e5e7eb); color: var(--fg-3, #727987); font-size: 11px; }
.capabilities-summary-item { display: inline-flex; align-items: baseline; gap: 4px; min-width: 0; }
.capabilities-summary-item + .capabilities-summary-item { position: relative; }
.capabilities-summary-item + .capabilities-summary-item::before { content: '·'; position: absolute; left: -8px; color: var(--fg-4, #9299a6); }
.capabilities-summary-item strong { color: var(--fg, #202124); font-size: 13px; font-weight: 600; line-height: 1.2; }
.capabilities-summary-item span { color: var(--fg-3, #727987); }
.capabilities-section { margin-bottom: 20px; }
.capabilities-section-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 2px; padding: 0 2px 7px; border-bottom: 1px solid var(--border, #e5e7eb); }
.capabilities-section-label { min-width: 0; color: var(--fg-2, #4f5663); font-size: 12px; }
.capabilities-count { min-width: 12px; color: var(--fg-4, #9299a6); font-size: 10px; text-align: right; }
.capabilities-list { display: flex; flex-direction: column; }
.capability-row { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 8px 2px; }
.capability-row:hover, .mcp-card-summary:hover { background: var(--bg-hover, rgba(0, 0, 0, .04)); }
.capability-status-dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: var(--fg-4, #a5acb8); }
.project-dot { background: var(--fg-3, #727987); }
.loaded-dot { background: #4c8a68; }
.offline-dot { background: var(--fg-4, #a5acb8); }
.capability-row-main, .mcp-card-main { min-width: 0; flex: 1; }
.capability-row-main strong, .capability-row-main small, .mcp-card-main strong, .mcp-card-main small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.capability-row-main strong, .mcp-card-main strong { color: var(--fg-2, #4f5663); font-size: 12px; font-weight: 600; }
.capability-row-main small, .mcp-card-main small { margin-top: 2px; color: var(--fg-4, #9299a6); font-size: 10px; }
.capabilities-empty { padding: 9px 2px; color: var(--fg-4, #9299a6); font-size: 11px; text-align: left; }
.mcp-list { display: flex; flex-direction: column; }
.mcp-card { overflow: hidden; border: 0; border-bottom: 1px solid var(--border, #e5e7eb); border-radius: 0; background: transparent; }
.mcp-card-summary { display: flex; align-items: center; gap: 8px; padding: 8px 2px; cursor: pointer; list-style: none; }
.mcp-card-summary::-webkit-details-marker { display: none; }
.mcp-status { flex: 0 0 auto; font-size: 10px; }
.mcp-status.loaded { color: #4c8a68; }
.mcp-status.offline { color: var(--fg-4, #9299a6); }
.mcp-chevron { color: var(--fg-4, #9299a6); font-size: 16px; line-height: 12px; transition: transform .15s ease; }
.mcp-card[open] .mcp-chevron { transform: rotate(90deg); }
.mcp-card-detail { padding: 0 2px 9px 16px; }
.mcp-tools { display: block; color: var(--fg-3, #727987); font-size: 10px; line-height: 1.8; }
.mcp-tools code { max-width: 100%; overflow: hidden; padding: 0; color: inherit; background: transparent; font-size: inherit; text-overflow: ellipsis; white-space: nowrap; }
.mcp-tools code + code::before { content: ' · '; color: var(--fg-4, #9299a6); }
.mcp-no-tools, .mcp-error { color: var(--fg-4, #9299a6); font-size: 10px; }
.mcp-error { color: var(--danger, #dc4c64); }
[data-theme="dark"] .loaded-dot { background: #84b995; }
[data-theme="dark"] .mcp-status.loaded { color: #84b995; }
</style>
