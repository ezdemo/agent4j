<template>
  <section class="desktop-home">
    <div class="desktop-home-search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
      <input v-model="query" type="search" placeholder="搜索会话" />
    </div>

    <div class="desktop-home-grid">
      <aside class="desktop-projects">
        <div class="desktop-home-heading"><span>项目</span></div>
        <div class="desktop-project-list">
          <button
            v-for="workspace in workspaces"
            :key="workspace.hash"
            class="desktop-project"
            :class="{ active: workspace.hash === activeWorkspaceHash }"
          type="button"
          @click="emit('select-workspace', workspace.hash)"
        >
            <span class="desktop-monogram" :class="badgeTone(workspace.name)">{{ initial(workspace.name) }}</span>
            <span>{{ workspace.name }}</span>
          </button>
          <div v-if="!workspaces.length" class="desktop-home-muted">暂无项目</div>
        </div>
        <div class="desktop-project-footer">
          <button type="button" @click="emit('open-settings')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06A1.65 1.65 0 0 0 15.14 19a1.65 1.65 0 0 0-1 1.51V20.6a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.86 15a1.65 1.65 0 0 0-1.51-1H3.4a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 5 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9.32 4a1.65 1.65 0 0 0 1-1.51V2.4a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19 8.32a1.65 1.65 0 0 0 1.51 1h.09a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.11 1.68Z"/></svg>
            设置
          </button>
        </div>
      </aside>

      <div class="desktop-sessions">
        <div class="desktop-home-heading">
          <span>{{ activeWorkspace?.name || '会话' }}</span>
          <button type="button" @click="emit('new-session')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
            新建会话
          </button>
        </div>
        <div v-if="loading" class="desktop-home-muted">加载会话...</div>
        <div v-else-if="sessionGroups.length" class="desktop-session-timeline">
          <section v-for="group in sessionGroups" :key="group.key" class="desktop-session-group">
            <h3>{{ group.label }}</h3>
            <div class="desktop-session-list">
              <button v-for="session in group.sessions" :key="session.name" class="desktop-session" type="button" @click="openSession(session)">
                <span class="desktop-monogram desktop-session-monogram">L</span>
                <span>{{ session.title || session.name }}</span>
              </button>
            </div>
          </section>
        </div>
        <div v-else class="desktop-home-muted">暂无会话</div>
      </div>
    </div>
  </section>
</template>

<script setup>
import {computed, ref, watch} from 'vue'
import {sessionsAPI} from './services/api'

const props = defineProps({
  workspaces: { type: Array, default: () => [] },
  activeWorkspaceHash: { type: String, default: '' }
})
const emit = defineEmits(['select-workspace', 'new-session', 'open-session', 'open-settings'])

const query = ref('')
const sessions = ref([])
const loading = ref(false)
const activeWorkspace = computed(() => props.workspaces.find((workspace) => workspace.hash === props.activeWorkspaceHash))
const filteredSessions = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return sessions.value.filter((session) => !keyword || `${session.title || ''} ${session.name || ''}`.toLowerCase().includes(keyword))
})
const sessionGroups = computed(() => {
  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)
  const groups = [
    { key: 'today', label: '今天', sessions: [] },
    { key: 'earlier', label: '更早', sessions: [] }
  ]
  for (const session of [...filteredSessions.value].sort((a, b) => sessionTime(b) - sessionTime(a))) {
    groups[sessionTime(session) >= todayStart.getTime() ? 0 : 1].sessions.push(session)
  }
  return groups.filter((group) => group.sessions.length > 0)
})

function initial(name) {
  return String(name || 'L').trim().charAt(0).toUpperCase() || 'L'
}

function badgeTone(name) {
  let hash = 0
  for (const char of String(name || '')) hash = ((hash * 31) + char.charCodeAt(0)) >>> 0
  return `tone-${hash % 5}`
}

function sessionTime(session) {
  const value = session?.mtime
  if (typeof value === 'number') return value
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function openSession(session) {
  emit('open-session', {
    workspaceHash: session.workspaceHash,
    sessionName: session.name,
    title: session.title
  })
}

async function loadSessions() {
  if (!props.activeWorkspaceHash) { sessions.value = []; return }
  loading.value = true
  try {
    const response = await sessionsAPI.list(props.activeWorkspaceHash)
    sessions.value = response.success ? (response.data || []).map((session) => ({ ...session, workspaceHash: props.activeWorkspaceHash })) : []
  } catch (error) {
    console.error('[desktop-home] failed to load sessions:', error)
    sessions.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.activeWorkspaceHash, loadSessions, { immediate: true })
</script>

<style scoped>
.desktop-home { --project-column: 260px; --column-gap: 55px; height: 100%; min-height: 0; display: flex; flex-direction: column; overflow: hidden; padding: 28px clamp(28px, 10vw, 190px); box-sizing: border-box; }
.desktop-home-search { height: 38px; margin: 0 0 34px calc(var(--project-column) + var(--column-gap)); display: flex; align-items: center; gap: 10px; padding: 0 13px; box-sizing: border-box; color: var(--fg-4, #9ca3af); background: var(--bg-3, #f6f6f6); border-radius: 6px; flex: 0 0 auto; }
.desktop-home-search svg { width: 16px; height: 16px; flex: 0 0 auto; }
.desktop-home-search input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--fg, #202124); font: inherit; font-size: 13px; }
.desktop-home-grid { min-height: 0; flex: 1; display: grid; grid-template-columns: var(--project-column) minmax(0, 1fr); gap: var(--column-gap); width: 100%; overflow: hidden; }
.desktop-projects, .desktop-sessions { min-height: 0; display: flex; flex-direction: column; }
.desktop-home-heading { height: 28px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; color: var(--fg, #202124); font-size: 14px; font-weight: 650; flex: 0 0 auto; }
.desktop-home-heading button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: var(--fg-2, #5f6368); font: inherit; font-size: 13px; cursor: pointer; padding: 4px; border-radius: 4px; }.desktop-home-heading button:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }.desktop-home-heading button svg { width: 15px; height: 15px; }
.desktop-project-list, .desktop-session-timeline { min-height: 0; overflow: auto; scrollbar-width: none; }.desktop-project-list { display: grid; gap: 2px; flex: 1; align-content: start; }.desktop-session-timeline { padding-right: 4px; }.desktop-session-list { display: grid; gap: 2px; }.desktop-session-group + .desktop-session-group { margin-top: 18px; }.desktop-session-group h3 { height: 24px; display: flex; align-items: center; margin: 0 0 4px; color: var(--fg-3, #727987); font-size: 13px; font-weight: 500; }
.desktop-project-list::-webkit-scrollbar, .desktop-session-timeline::-webkit-scrollbar { width: 0; height: 0; }
.desktop-project-list:hover, .desktop-session-timeline:hover { scrollbar-width: thin; }
.desktop-project-list:hover::-webkit-scrollbar, .desktop-session-timeline:hover::-webkit-scrollbar { width: 6px; }
.desktop-project-list:hover::-webkit-scrollbar-thumb, .desktop-session-timeline:hover::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.38); border-radius: 6px; }
.desktop-project-list:hover::-webkit-scrollbar-track, .desktop-session-timeline:hover::-webkit-scrollbar-track { background: transparent; }
.desktop-project, .desktop-session { width: 100%; height: 32px; display: flex; align-items: center; gap: 8px; border: 0; border-radius: 5px; background: transparent; color: var(--fg-2, #525866); font: inherit; font-size: 13px; text-align: left; cursor: pointer; padding: 0 8px; box-sizing: border-box; }.desktop-project:hover, .desktop-session:hover, .desktop-project.active { background: var(--bg-3, #f2f3f5); color: var(--fg, #202124); }.desktop-project > span:last-child, .desktop-session > span:last-child { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.desktop-session { font-weight: 400; }.desktop-home-muted { padding: 12px 8px; color: var(--fg-4, #9ca3af); font-size: 12px; }
.desktop-monogram { width: 17px; height: 17px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 4px; color: #fff; font-size: 11px; font-weight: 700; line-height: 1; text-shadow: 0 1px rgba(0, 0, 0, 0.25); box-shadow: inset 0 1px rgba(255, 255, 255, 0.25), 0 1px 1px rgba(0, 0, 0, 0.16); }.desktop-monogram.tone-0 { background: linear-gradient(135deg, #697382, #47515e); }.desktop-monogram.tone-1 { background: linear-gradient(135deg, #24b8d4, #188eaf); }.desktop-monogram.tone-2 { background: linear-gradient(135deg, #ff924f, #e66b2e); }.desktop-monogram.tone-3 { background: linear-gradient(135deg, #7b8df1, #5768cd); }.desktop-monogram.tone-4 { background: linear-gradient(135deg, #59b58a, #368b66); }.desktop-session-monogram { background: linear-gradient(135deg, #737373, #4c4c4c); }
.desktop-project-footer { padding-top: 10px; border-top: 1px solid var(--border, #e8e8e8); flex: 0 0 auto; }.desktop-project-footer button { width: 100%; height: 32px; display: flex; align-items: center; gap: 8px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: var(--fg-3, #727987); font: inherit; font-size: 13px; cursor: pointer; }.desktop-project-footer button:hover { background: var(--bg-3, #f2f3f5); color: var(--fg, #202124); }.desktop-project-footer svg { width: 16px; height: 16px; }
@media (max-width: 1000px) { .desktop-home { --project-column: 260px; --column-gap: 48px; } }
@media (max-width: 720px) { .desktop-home { --project-column: 1fr; --column-gap: 28px; padding: 22px 24px; overflow: auto; }.desktop-home-search { margin-left: 0; }.desktop-home-grid { flex: initial; grid-template-columns: 1fr; overflow: visible; }.desktop-project-list, .desktop-session-timeline { overflow: visible; } }
</style>
