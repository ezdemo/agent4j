<template>
  <div class="sub-agents-view">
    <header class="page-header">
      <div>
        <h1>子代理</h1>
        <p>内置角色、运行时工具权限与预置系统提示词（配置持久化在 ~/.loopra/sub-agents.json）</p>
      </div>
      <div class="header-actions">
        <button class="add-button" type="button" title="新增角色" aria-label="新增角色" @click="addProfile">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          新增角色
        </button>
        <button class="refresh-button" type="button" :disabled="loading" title="刷新" aria-label="刷新子代理" @click="loadSubAgents">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5"/>
            <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5"/>
          </svg>
        </button>
      </div>
    </header>

    <main class="page-content">
      <div v-if="loading" class="page-state">加载中...</div>
      <div v-else-if="error" class="page-state error-state">
        <span>{{ error }}</span>
        <button type="button" @click="loadSubAgents">重试</button>
      </div>
      <div v-else-if="!profiles.length" class="page-state">暂无角色</div>
      <div v-else class="profile-list">
        <section v-for="profile in profiles" :key="profile._key || profile.id" class="profile-card" :class="{ disabled: profile.enable === false }">
          <header class="profile-header">
            <div class="profile-identity">
              <div class="profile-icon" :class="{ writable: !profile.readOnly, off: profile.enable === false }">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <circle cx="12" cy="8" r="3.5"/>
                  <path d="M5 20v-2a7 7 0 0 1 14 0v2"/>
                </svg>
              </div>
              <div>
                <div class="profile-title">
                  <h2>{{ profile.name || profileName(profile.id) }}</h2>
                  <code>{{ profile.id }}</code>
                  <span class="access-badge" :class="{ writable: !profile.readOnly }">
                    {{ profile.readOnly ? '只读' : '可写' }}
                  </span>
                  <span v-if="profile.enable === false" class="access-badge off">已禁用</span>
                  <span v-if="profile.builtin" class="access-badge builtin" title="系统内置角色，内容以 Java 定义为准，重启后自动同步，不可编辑">系统内置</span>
                </div>
                <p>{{ profile.description || profileDescription(profile.id) }}</p>
              </div>
            </div>
            <div class="profile-actions">
              <template v-if="editingProfile === profile">
                <button class="action-button" type="button" :disabled="saving" @click="saveProfile(profile)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                  保存
                </button>
                <button class="action-button" type="button" :disabled="saving" @click="cancelEdit(profile)">取消</button>
                <button v-if="profile._isNew" class="action-button danger" type="button" @click="removeNewProfile(profile)">移除</button>
              </template>
              <template v-else>
                <button v-if="!profile.builtin" class="action-button" type="button" @click="startEdit(profile)">编辑</button>
                <button v-if="!profile.builtin && profile.id" class="action-button danger" type="button" title="删除此角色（不可恢复）" @click="deleteProfile(profile)">删除</button>
                <button class="action-button" type="button" @click="toggleEnable(profile)">
                  {{ profile.enable === false ? '启用' : '禁用' }}
                </button>
              </template>
            </div>
          </header>

          <!-- 编辑模式 -->
          <div v-if="editingProfile === profile" class="profile-body edit-mode">
            <div class="edit-form">
              <div class="form-row">
                <label class="form-label">ID</label>
                <input v-if="!profile._isNew" class="form-input" :value="profile.id" disabled />
                <input v-else v-model="profile.id" class="form-input" placeholder="如 my-agent（保存后不可更改）" />
              </div>
              <div class="form-row">
                <label class="form-label">名称</label>
                <input v-model="profile.name" class="form-input" placeholder="角色显示名" />
              </div>
              <div class="form-row">
                <label class="form-label">描述</label>
                <input v-model="profile.description" class="form-input" placeholder="角色简介" />
              </div>
              <div class="form-row">
                <label class="form-label">系统提示词</label>
                <textarea v-model="profile.instructions" class="form-textarea prompt-textarea" placeholder="预置系统提示词"></textarea>
              </div>
              <div class="form-row">
                <label class="form-label">工具白名单</label>
                <div class="tool-picker">
                  <div class="tag-input" @click.self="focusTagInput">
                    <span v-for="tool in profile._selectedTools" :key="tool" class="tag-input-item">
                      {{ tool }}
                      <button type="button" class="tag-remove" title="移除 {{ tool }}" aria-label="移除 {{ tool }}" @click.stop="removeTool(profile, tool)">&times;</button>
                    </span>
                    <input v-model="profile._tagInput" class="tag-input-field" placeholder="默认无需修改" @keydown.enter.prevent="addToolFromInput(profile)" @keydown.','.prevent="addToolFromInput(profile)" @keydown.backspace="onTagBackspace(profile)" @blur="commitTagInput(profile)" />
                  </div>
                  <div class="picker-bar">
                    <button type="button" class="picker-import" title="替换当前白名单为全部只读工具" @click="importReadOnlyTools(profile)">一键导入只读</button>
                    <button type="button" class="picker-import" title="替换当前白名单为全部写入工具" @click="importWriteTools(profile)">一键导入写入</button>
                    <button type="button" class="picker-toggle" @click="profile._showPicker = !profile._showPicker">
                      {{ profile._showPicker ? '收起工具列表' : '从工具列表选择' }}
                    </button>
                  </div>
                  <div v-if="profile._showPicker" class="tool-chips">
                    <button v-for="tool in allTools" :key="tool.name" type="button" class="tool-chip" :class="{ active: profile._selectedTools.includes(tool.name), disabled: isToolUnavailable(tool) }" :title="toolUnavailableHint(tool)" :disabled="isToolUnavailable(tool)" @click="toggleTool(profile, tool.name)">
                      {{ tool.name }}
                    </button>
                    <p v-if="!allTools.length" class="empty-tools">工具列表加载中...</p>
                  </div>
                </div>
              </div>
              <div class="form-row inline">
                <label class="form-check">
                  <input v-model="profile.readOnly" type="checkbox" />
                  只读角色（未显式配置工具时仅限只读工具）
                </label>
                <label class="form-check">
                  <input v-model="profile.enable" type="checkbox" />
                  启用该角色
                </label>
              </div>
            </div>
          </div>

          <!-- 展示模式 -->
          <div v-else class="profile-body">
            <div class="profile-section tools-section">
              <h3>实际可用工具</h3>
              <div v-if="profile.tools.length" class="tool-list">
                <code v-for="tool in profile.tools" :key="tool">{{ tool }}</code>
              </div>
              <p v-else class="empty-tools">当前没有可用工具</p>
            </div>
            <div class="profile-section prompt-section">
              <h3>预置系统提示词</h3>
              <pre>{{ profile.instructions }}</pre>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import {nextTick, onMounted, ref} from 'vue'
import {message} from 'ant-design-vue'
import {toolsAPI} from '../services/api'
import {useConfirm} from '../composables/useConfirm'

const profiles = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editingProfile = ref(null)
const allTools = ref([])
const deniedTools = ref([])
let toolNamesLoaded = false
const { confirm } = useConfirm()

const profileMeta = {
  explore: ['探索', '定位代码、追溯调用链并基于证据汇报'],
  implement: ['实现', '按指定范围实现功能或修复并执行相关检查'],
  test: ['测试', '确认覆盖缺口并添加或调整必要测试'],
  review: ['审查', '寻找缺陷、回归、安全问题与测试缺口'],
  plan: ['方案', '理解现状并给出可执行的分步方案']
}

const profileName = (id) => profileMeta[id]?.[0] || id
const profileDescription = (id) => profileMeta[id]?.[1] || ''

const serialize = (profile) => {
  return {
    id: (profile.id || '').trim(),
    name: profile.name,
    description: profile.description,
    enable: profile.enable !== false,
    readOnly: !!profile.readOnly,
    instructions: profile.instructions,
    ...(profile._selectedTools && profile._selectedTools.length ? {allowedTools: profile._selectedTools} : {})
  }
}

/** 加载子代理列表。silent=true 时不切换 loading 态（避免列表高度骤变导致滚动位置跳到顶部） */
async function loadSubAgents(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  editingProfile.value = null
  try {
    const response = await toolsAPI.listSubAgents()
    if (!response.success) throw new Error(response.message || '加载失败')
    profiles.value = (response.data || []).map((profile, index) => ({
      ...profile,
      _key: 'p-' + index + '-' + Math.random().toString(36).slice(2, 7),
      instructions: profile.systemPrompt || '',
      _selectedTools: profile.allowedTools || [],
      _tagInput: ''
    }))
  } catch (loadError) {
    error.value = loadError.message || '无法加载子代理'
  } finally {
    if (!silent) loading.value = false
  }
}

function startEdit(profile) {
  profile._snapshot = {
    name: profile.name,
    description: profile.description,
    enable: profile.enable,
    readOnly: profile.readOnly,
    instructions: profile.instructions,
    _selectedTools: [...(profile._selectedTools || [])]
  }
  profile._showPicker = false
  editingProfile.value = profile
  void loadToolNames()
}

/** 加载全部已注册工具（含只读分类与启用状态）及子代理不可用清单（首次编辑时加载一次） */
async function loadToolNames() {
  if (toolNamesLoaded) return
  try {
    const [toolsResponse, deniedResponse] = await Promise.all([
      toolsAPI.list(),
      toolsAPI.listSubAgentDeniedTools()
    ])
    if (toolsResponse.success && Array.isArray(toolsResponse.data)) {
      allTools.value = toolsResponse.data
        .map((tool) => ({
          name: tool.name,
          readOnly: tool.readOnlyOverride ?? tool.readOnly,
          enabled: tool.enabled !== false
        }))
        .sort((a, b) => a.name.localeCompare(b.name))
    }
    if (deniedResponse.success && Array.isArray(deniedResponse.data)) {
      deniedTools.value = deniedResponse.data
    }
  } catch {
    // 工具列表加载失败不阻塞编辑，白名单仍可手动输入
  } finally {
    toolNamesLoaded = true
  }
}

/** 子代理不可用的工具：主代理专用（SUB_AGENT_DENY）或已被禁用 */
const isToolUnavailable = (tool) => deniedTools.value.includes(tool.name) || tool.enabled === false
const toolUnavailableHint = (tool) => deniedTools.value.includes(tool.name)
  ? '子代理不可用（主代理专用）'
  : (tool.enabled === false ? '该工具已被禁用' : '')

function toggleTool(profile, toolName) {
  const tools = [...(profile._selectedTools || [])]
  const index = tools.indexOf(toolName)
  if (index >= 0) {
    tools.splice(index, 1)
  } else {
    tools.push(toolName)
  }
  profile._selectedTools = tools.sort()
}

/** 一键导入：白名单替换为全部可用的只读/写入工具（排除子代理不可用与已禁用的） */
function importReadOnlyTools(profile) {
  profile._selectedTools = allTools.value
    .filter((tool) => !isToolUnavailable(tool) && tool.readOnly)
    .map((tool) => tool.name)
}

function importWriteTools(profile) {
  profile._selectedTools = allTools.value
    .filter((tool) => !isToolUnavailable(tool) && !tool.readOnly)
    .map((tool) => tool.name)
}

function removeTool(profile, toolName) {
  profile._selectedTools = (profile._selectedTools || []).filter((tool) => tool !== toolName)
}

/** 回车/逗号/失焦：把输入框内容作为工具名添加为 tag */
function addToolFromInput(profile) {
  const value = (profile._tagInput || '').trim()
  if (value && !(profile._selectedTools || []).includes(value)) {
    profile._selectedTools = [...(profile._selectedTools || []), value].sort()
  }
  profile._tagInput = ''
}

const commitTagInput = addToolFromInput

/** 输入框为空时按退格删除最后一个 tag */
function onTagBackspace(profile) {
  if (!profile._tagInput && (profile._selectedTools || []).length) {
    profile._selectedTools = profile._selectedTools.slice(0, -1)
  }
}

/** 点击 tag 容器空白处聚焦输入框 */
function focusTagInput(event) {
  event.currentTarget.querySelector('input')?.focus()
}

function cancelEdit(profile) {
  if (profile._isNew) {
    // 未保存的新增角色：取消 = 放弃新增，从列表移除（无论是否已填写 ID）
    removeNewProfile(profile)
    return
  }
  if (profile._snapshot) {
    Object.assign(profile, profile._snapshot)
    delete profile._snapshot
  }
  editingProfile.value = null
}

function addProfile() {
  const profile = {
    _key: 'new-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7),
    _isNew: true,
    id: '',
    name: '',
    description: '',
    enable: true,
    readOnly: false,
    instructions: '',
    tools: [],
    _selectedTools: [],
    _tagInput: '',
    _showPicker: false
  }
  profiles.value.push(profile)
  editingProfile.value = profile
  void loadToolNames()
  // 滚动到新卡片，方便直接填写
  void nextTick(() => {
    document.querySelector('.profile-card:last-child')?.scrollIntoView({behavior: 'smooth', block: 'center'})
  })
}

function removeNewProfile(profile) {
  const index = profiles.value.indexOf(profile)
  if (index >= 0) profiles.value.splice(index, 1)
  if (editingProfile.value === profile) editingProfile.value = null
  delete profile._snapshot
}

async function saveProfile(profile) {
  if (!profile.id || !profile.id.trim()) {
    message.error('请先填写角色 ID（保存后不可更改）')
    return
  }
  if (!profile.instructions || !profile.instructions.trim()) {
    message.error('系统提示词不能为空')
    return
  }
  saving.value = true
  try {
    const response = await toolsAPI.saveSubAgents(profiles.value.map(serialize))
    if (!response.success) throw new Error(response.message || '保存失败')
    message.success('子代理配置已保存')
    await loadSubAgents(true) // silent：避免列表重建时滚动位置跳动
  } catch (saveError) {
    message.error('保存失败：' + (saveError.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function toggleEnable(profile) {
  const disabling = profile.enable !== false
  const original = profile.enable
  profile.enable = !disabling
  // 本地更新 + 后台保存，不重载列表（保持滚动位置）；失败时回滚
  void (async () => {
    try {
      const response = await toolsAPI.saveSubAgents(profiles.value.map(serialize))
      if (!response.success) throw new Error(response.message || '保存失败')
      message.success(disabling ? '已禁用' : '已启用')
    } catch (saveError) {
      profile.enable = original
      message.error('操作失败：' + (saveError.message || '未知错误'))
    }
  })()
}

/** 删除自定义角色（全量保存后从配置移除；内置角色不可删除） */
async function deleteProfile(profile) {
  const ok = await confirm({ message: `确定删除角色「${profile.name || profile.id}」？删除后不可恢复。` })
  if (!ok) return
  const index = profiles.value.indexOf(profile)
  if (index >= 0) profiles.value.splice(index, 1)
  if (editingProfile.value === profile) editingProfile.value = null
  try {
    const response = await toolsAPI.saveSubAgents(profiles.value.map(serialize))
    if (!response.success) throw new Error(response.message || '删除失败')
    message.success('角色已删除')
    // 本地已移除，无需重载（保持滚动位置）
  } catch (deleteError) {
    message.error('删除失败：' + (deleteError.message || '未知错误'))
    await loadSubAgents(true) // 服务端未保存，静默重载恢复列表
  }
}

onMounted(loadSubAgents)
</script>

<style scoped>
.sub-agents-view {
  box-sizing: border-box;
  min-height: 100%;
  background: var(--bg, #fff);
  color: var(--fg, #202124);
}

.page-header {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-sizing: border-box;
  padding: 0 28px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}

.page-header h1 { margin: 0; font-size: 18px; font-weight: 650; }
.page-header p { margin: 4px 0 0; color: var(--fg-4, #8b929d); font-size: 12px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
.add-button { height: 32px; display: inline-flex; align-items: center; gap: 6px; padding: 0 12px; border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg, #202124); font: inherit; font-size: 13px; cursor: pointer; }
.add-button:hover { background: var(--bg-3, #f3f4f6); color: var(--accent, #2563eb); }
.add-button svg { width: 15px; height: 15px; }
.refresh-button { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg-3, #616975); cursor: pointer; }
.refresh-button:hover:not(:disabled) { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.refresh-button:disabled { opacity: .5; cursor: default; }
.refresh-button svg { width: 16px; height: 16px; }
.page-content { width: min(100%, 1080px); box-sizing: border-box; margin: 0 auto; padding: 24px 28px 48px; }
.page-state { min-height: 240px; display: grid; place-items: center; color: var(--fg-4, #8b929d); font-size: 13px; }
.error-state { align-content: center; gap: 12px; color: #b42318; }
.error-state button { border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg, #202124); padding: 6px 14px; cursor: pointer; }
.profile-list { display: grid; gap: 14px; }
.profile-card { border: 1px solid var(--border, #e5e7eb); border-radius: 6px; background: var(--bg, #fff); overflow: hidden; }
.profile-card.disabled { opacity: .65; }
.profile-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 18px 20px; border-bottom: 1px solid var(--border, #e5e7eb); }
.profile-identity { display: flex; gap: 13px; min-width: 0; }
.profile-icon { width: 34px; height: 34px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 6px; background: #eaf2ff; color: #2563eb; }
.profile-icon.writable { background: #e9f7ef; color: #16803d; }
.profile-icon.off { background: #f1f2f4; color: #9aa1ab; }
.profile-icon svg { width: 19px; height: 19px; }
.profile-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.profile-title h2 { margin: 0; font-size: 15px; font-weight: 650; }
.profile-title code { color: var(--fg-4, #8b929d); font-size: 11px; }
.profile-identity p { margin: 5px 0 0; color: var(--fg-3, #616975); font-size: 12px; }
.access-badge { padding: 2px 6px; border: 1px solid #bfdbfe; border-radius: 3px; background: #eff6ff; color: #1d4ed8; font-size: 10px; }
.access-badge.writable { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
.access-badge.off { border-color: #e5e7eb; background: #f3f4f6; color: #6b7280; }
.access-badge.builtin { border-color: #fde68a; background: #fef3c7; color: #92400e; }
.profile-actions { flex: 0 0 auto; display: flex; align-items: center; gap: 6px; }
.action-button { height: 28px; display: inline-flex; align-items: center; gap: 5px; padding: 0 10px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg, #fff); color: var(--fg-2, #3f4650); font: inherit; font-size: 12px; cursor: pointer; }
.action-button svg { width: 13px; height: 13px; }
.action-button:hover:not(:disabled) { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.action-button:disabled { opacity: .5; cursor: default; }
.action-button.danger:hover:not(:disabled) { color: #b42318; border-color: #fecaca; background: #fef2f2; }
.profile-body { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(320px, .9fr); }
.profile-section { min-width: 0; padding: 18px 20px 20px; }
.prompt-section { border-left: 1px solid var(--border, #e5e7eb); }
.profile-section h3 { margin: 0 0 12px; color: var(--fg-3, #616975); font-size: 11px; font-weight: 600; }
.tool-list { display: flex; flex-wrap: wrap; gap: 6px; }
.tool-list code { padding: 4px 7px; border: 1px solid var(--border, #e5e7eb); border-radius: 3px; background: var(--bg-2, #f8f9fa); color: var(--fg-2, #3f4650); font-size: 11px; line-height: 1.2; }
.empty-tools { margin: 0; color: var(--fg-4, #8b929d); font-size: 12px; }
.prompt-section pre { margin: 0; color: var(--fg-2, #3f4650); font: inherit; font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }

/* 编辑表单 */
.profile-body.edit-mode { display: block; }
.edit-form { display: grid; gap: 12px; padding: 18px 20px 20px; }
.form-row { display: grid; grid-template-columns: 88px minmax(0, 1fr); align-items: start; gap: 10px; }
.form-row.inline { grid-template-columns: 1fr; display: flex; gap: 20px; }
.form-label { padding-top: 7px; color: var(--fg-3, #616975); font-size: 12px; line-height: 1.4; }
.form-input { box-sizing: border-box; width: 100%; padding: 6px 9px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg, #fff); color: var(--fg, #202124); font: inherit; font-size: 13px; }
.form-input:disabled { background: var(--bg-2, #f8f9fa); color: var(--fg-4, #8b929d); }
.form-textarea { box-sizing: border-box; width: 100%; min-height: 72px; padding: 6px 9px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg, #fff); color: var(--fg, #202124); font: inherit; font-size: 13px; line-height: 1.5; resize: vertical; }
.prompt-textarea { min-height: 140px; }
.form-check { display: flex; align-items: center; gap: 6px; color: var(--fg-2, #3f4650); font-size: 13px; cursor: pointer; }
.tool-picker { min-width: 0; }
.tag-input { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; padding: 4px 6px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg, #fff); cursor: text; }
.tag-input:focus-within { border-color: var(--accent, #2563eb); }
.tag-input-item { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px 2px 9px; border-radius: 12px; background: #eff6ff; color: #1d4ed8; font-size: 12px; line-height: 1.5; }
.tag-remove { display: grid; place-items: center; width: 15px; height: 15px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: #1d4ed8; font-size: 14px; line-height: 1; cursor: pointer; }
.tag-remove:hover { background: #dbeafe; }
.tag-input-field { flex: 1; min-width: 140px; box-sizing: border-box; border: 0; outline: none; background: transparent; color: var(--fg, #202124); font: inherit; font-size: 13px; padding: 3px 2px; }
.picker-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 6px; }
.picker-import { padding: 4px 10px; border: 1px solid #bfdbfe; border-radius: 4px; background: #eff6ff; color: #1d4ed8; font: inherit; font-size: 12px; cursor: pointer; }
.picker-import:hover { background: #dbeafe; }
.picker-toggle { padding: 4px 10px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg-2, #f8f9fa); color: var(--fg-2, #3f4650); font: inherit; font-size: 12px; cursor: pointer; }
.picker-toggle:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.tool-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; max-height: 180px; overflow-y: auto; padding: 8px; border: 1px solid var(--border, #e5e7eb); border-radius: 4px; background: var(--bg-2, #f8f9fa); }
.tool-chip { padding: 3px 9px; border: 1px solid var(--border, #e5e7eb); border-radius: 12px; background: var(--bg, #fff); color: var(--fg-2, #3f4650); font: inherit; font-size: 12px; cursor: pointer; }
.tool-chip:hover { border-color: var(--accent, #2563eb); color: var(--accent, #2563eb); }
.tool-chip.active { border-color: var(--accent, #2563eb); background: #eff6ff; color: #1d4ed8; }
.tool-chip.disabled { opacity: .45; cursor: not-allowed; }
.tool-chip.disabled:hover { border-color: var(--border, #e5e7eb); color: var(--fg-2, #3f4650); }

@media (max-width: 760px) {
  .page-header { padding: 0 18px; }
  .page-content { padding: 18px 14px 36px; }
  .profile-header { padding: 16px; flex-wrap: wrap; }
  .profile-actions { width: 100%; justify-content: flex-end; }
  .profile-body { grid-template-columns: 1fr; }
  .profile-section { padding: 16px; }
  .prompt-section { border-top: 1px solid var(--border, #e5e7eb); border-left: 0; }
  .form-row { grid-template-columns: 1fr; gap: 4px; }
}
</style>
