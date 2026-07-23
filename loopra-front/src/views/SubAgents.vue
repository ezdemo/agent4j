<template>
  <div class="sub-agents-view">
    <header class="page-header">
      <div>
        <h1>子代理</h1>
        <p>内置角色、运行时工具权限与预置系统提示词</p>
      </div>
      <button class="refresh-button" type="button" :disabled="loading" title="刷新" aria-label="刷新子代理" @click="loadSubAgents">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5"/>
          <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5"/>
        </svg>
      </button>
    </header>

    <main class="page-content">
      <div v-if="loading" class="page-state">加载中...</div>
      <div v-else-if="error" class="page-state error-state">
        <span>{{ error }}</span>
        <button type="button" @click="loadSubAgents">重试</button>
      </div>
      <div v-else class="profile-list">
        <section v-for="profile in profiles" :key="profile.id" class="profile-card">
          <header class="profile-header">
            <div class="profile-identity">
              <div class="profile-icon" :class="{ writable: !profile.readOnly }">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <circle cx="12" cy="8" r="3.5"/>
                  <path d="M5 20v-2a7 7 0 0 1 14 0v2"/>
                </svg>
              </div>
              <div>
                <div class="profile-title">
                  <h2>{{ profileName(profile.id) }}</h2>
                  <code>{{ profile.id }}</code>
                  <span class="access-badge" :class="{ writable: !profile.readOnly }">
                    {{ profile.readOnly ? '只读' : '可写' }}
                  </span>
                </div>
                <p>{{ profileDescription(profile.id) }}</p>
              </div>
            </div>
            <span class="tool-count">{{ profile.tools.length }} 个工具</span>
          </header>

          <div class="profile-body">
            <div class="profile-section tools-section">
              <h3>实际可用工具</h3>
              <div v-if="profile.tools.length" class="tool-list">
                <code v-for="tool in profile.tools" :key="tool">{{ tool }}</code>
              </div>
              <p v-else class="empty-tools">当前没有可用工具</p>
            </div>
            <div class="profile-section prompt-section">
              <h3>预置系统提示词</h3>
              <pre>{{ profile.systemPrompt }}</pre>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {toolsAPI} from '../services/api'

const profiles = ref([])
const loading = ref(false)
const error = ref('')

const profileMeta = {
  explore: ['探索', '定位代码、追溯调用链并基于证据汇报'],
  implement: ['实现', '按指定范围实现功能或修复并执行相关检查'],
  test: ['测试', '确认覆盖缺口并添加或调整必要测试'],
  review: ['审查', '寻找缺陷、回归、安全问题与测试缺口'],
  plan: ['方案', '理解现状并给出可执行的分步方案']
}

const profileName = (id) => profileMeta[id]?.[0] || id
const profileDescription = (id) => profileMeta[id]?.[1] || ''

async function loadSubAgents() {
  loading.value = true
  error.value = ''
  try {
    const response = await toolsAPI.listSubAgents()
    if (!response.success) throw new Error(response.message || '加载失败')
    profiles.value = response.data || []
  } catch (loadError) {
    error.value = loadError.message || '无法加载子代理'
  } finally {
    loading.value = false
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
  box-sizing: border-box;
  padding: 0 28px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}

.page-header h1 { margin: 0; font-size: 18px; font-weight: 650; }
.page-header p { margin: 4px 0 0; color: var(--fg-4, #8b929d); font-size: 12px; }
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
.profile-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 18px 20px; border-bottom: 1px solid var(--border, #e5e7eb); }
.profile-identity { display: flex; gap: 13px; min-width: 0; }
.profile-icon { width: 34px; height: 34px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 6px; background: #eaf2ff; color: #2563eb; }
.profile-icon.writable { background: #e9f7ef; color: #16803d; }
.profile-icon svg { width: 19px; height: 19px; }
.profile-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.profile-title h2 { margin: 0; font-size: 15px; font-weight: 650; }
.profile-title code { color: var(--fg-4, #8b929d); font-size: 11px; }
.profile-identity p { margin: 5px 0 0; color: var(--fg-3, #616975); font-size: 12px; }
.access-badge { padding: 2px 6px; border: 1px solid #bfdbfe; border-radius: 3px; background: #eff6ff; color: #1d4ed8; font-size: 10px; }
.access-badge.writable { border-color: #bbf7d0; background: #f0fdf4; color: #15803d; }
.tool-count { flex: 0 0 auto; color: var(--fg-4, #8b929d); font-size: 11px; line-height: 24px; }
.profile-body { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(320px, .9fr); }
.profile-section { min-width: 0; padding: 18px 20px 20px; }
.prompt-section { border-left: 1px solid var(--border, #e5e7eb); }
.profile-section h3 { margin: 0 0 12px; color: var(--fg-3, #616975); font-size: 11px; font-weight: 600; }
.tool-list { display: flex; flex-wrap: wrap; gap: 6px; }
.tool-list code { padding: 4px 7px; border: 1px solid var(--border, #e5e7eb); border-radius: 3px; background: var(--bg-2, #f8f9fa); color: var(--fg-2, #3f4650); font-size: 11px; line-height: 1.2; }
.empty-tools { margin: 0; color: var(--fg-4, #8b929d); font-size: 12px; }
.prompt-section pre { margin: 0; color: var(--fg-2, #3f4650); font: inherit; font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }

@media (max-width: 760px) {
  .page-header { padding: 0 18px; }
  .page-content { padding: 18px 14px 36px; }
  .profile-header { padding: 16px; }
  .profile-body { grid-template-columns: 1fr; }
  .profile-section { padding: 16px; }
  .prompt-section { border-top: 1px solid var(--border, #e5e7eb); border-left: 0; }
}
</style>
