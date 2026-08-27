<template>
  <section class="sub-agent-panel">
    <header class="sub-agent-header">
      <div>
        <h2>子代理会话</h2>
        <p>双击记录打开回放标签</p>
      </div>
      <button class="icon-button" :class="{ loading: loadingList }" type="button" title="刷新子代理会话" aria-label="刷新子代理会话" :disabled="loadingList" @click="refresh()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66L20 8"/><path d="M20 4v4h-4"/></svg>
      </button>
    </header>

    <!-- 会话列表：单击打开回放标签（重复打开幂等），打开后由编辑器标签栏承载 -->
    <div class="sub-agent-list">
      <div v-if="loadingList" class="sub-agent-empty">加载中…</div>
      <div v-else-if="!canLoad" class="sub-agent-empty">当前无会话</div>
      <div v-else-if="list.length === 0" class="sub-agent-empty">当前会话暂无子代理执行记录</div>
      <template v-else>
        <div v-for="item in list" :key="item.subSessionId" class="sub-agent-item"
             :class="{ selected: selectedSubId === item.subSessionId }"
             :title="item.title ? (item.name ? item.name + '：' : '') + item.title : (item.task || '（无任务描述）')"
             @click="openItem(item)">
          <div class="sub-agent-item-main">
            <!-- 单行：名字（旧数据回退为 task）+ 时间；标题等完整信息在悬停提示中 -->
            <div class="sub-agent-item-row">
              <span class="sub-agent-item-name">{{ item.name || item.task || '子代理' }}</span>
              <span class="sub-agent-item-time">{{ formatTime(item.startedAt) }}</span>
            </div>
          </div>
          <button class="sub-agent-delete" type="button" title="删除该子代理会话" aria-label="删除该子代理会话"
                  :disabled="deletingId === item.subSessionId"
                  @click.stop="deleteItem(item)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>
          </button>
        </div>
      </template>
    </div>

    <!-- 删除确认（系统统一 ActionConfirmDialog） -->
    <ActionConfirmDialog
      :model-value="deleteConfirm.visible"
      title="删除子代理会话？"
      :message="deleteConfirm.item ? `“${deleteConfirm.item.name || deleteConfirm.item.task || '该会话'}”将被永久删除，无法恢复。` : ''"
      :actions="deleteConfirmActions"
      :pending="deletingId !== null"
      @update:model-value="closeDeleteConfirm"
      @action="handleDeleteConfirmAction"
    />
  </section>
</template>

<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {subSessionsAPI} from '../services/api'
import ActionConfirmDialog from './ActionConfirmDialog.vue'

const props = defineProps({
  workspaceHash: {type: String, default: null},
  sessionName: {type: String, default: null}
})

const emit = defineEmits(['open', 'removed'])

const list = ref([])
const loadingList = ref(false)
const selectedSubId = ref(null)

const canLoad = computed(() => Boolean(props.workspaceHash && props.sessionName))

const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadList(silent = false) {
  if (!canLoad.value) return
  if (!silent) loadingList.value = true
  try {
    const res = await subSessionsAPI.list(props.workspaceHash, props.sessionName)
    if (res?.success) list.value = res.data || []
  } catch (e) {
    console.warn('[sub-agent-panel] 加载子代理会话列表失败:', e)
  } finally {
    if (!silent) loadingList.value = false
  }
}

/** 静默刷新列表（供父组件在子代理结束时调用，同步状态点） */
function refresh() {
  return loadList(true)
}

function selectItem(item) {
  selectedSubId.value = item.subSessionId
}

/** 单击/双击均可打开：交给编辑器标签栏打开回放标签（重复打开幂等，双击=两次单击） */
function openItem(item) {
  selectItem(item)
  emit('open', item)
}

const deletingId = ref(null)
// 删除确认对话框（系统统一 ActionConfirmDialog）
const deleteConfirm = reactive({ visible: false, item: null })
const deleteConfirmActions = [
  { key: 'cancel', label: '取消' },
  { key: 'confirm', label: '删除', variant: 'danger' }
]

/** 点击删除按钮：弹出统一确认对话框 */
function deleteItem(item) {
  deleteConfirm.item = item
  deleteConfirm.visible = true
}

function closeDeleteConfirm() {
  deleteConfirm.visible = false
}

/** 确认框按钮回调：取消则关闭，确认则执行删除，成功后刷新列表并通知父组件关闭对应回放标签 */
async function handleDeleteConfirmAction(key) {
  if (key !== 'confirm') {
    closeDeleteConfirm()
    return
  }
  const item = deleteConfirm.item
  if (!item) return
  deleteConfirm.visible = false
  deletingId.value = item.subSessionId
  try {
    const res = await subSessionsAPI.remove(item.subSessionId, props.workspaceHash, props.sessionName)
    if (res?.success) {
      emit('removed', item.subSessionId)
      await loadList(true)
    } else {
      console.warn('[sub-agent-panel] 删除子代理会话失败:', res?.error || res)
      window.alert(res?.error || '删除失败')
    }
  } catch (e) {
    console.warn('[sub-agent-panel] 删除子代理会话异常:', e)
    window.alert('删除失败：' + (e?.message || e))
  } finally {
    deletingId.value = null
  }
}

onMounted(() => loadList())

defineExpose({refresh})
</script>

<style scoped>
.sub-agent-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.sub-agent-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}

.sub-agent-header h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg, #1a1d21);
}

.sub-agent-header p {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--fg-3, #727987);
}

.icon-button {
  border: 0;
  background: transparent;
  color: var(--fg-3, #727987);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
}

.icon-button:hover { color: var(--fg, #1a1d21); background: var(--bg-hover, rgba(0, 0, 0, .05)); }
.icon-button.loading svg { animation: sub-agent-spin 1s linear infinite; }
.icon-button:disabled { opacity: .5; cursor: not-allowed; }

@keyframes sub-agent-spin { to { transform: rotate(360deg); } }

.sub-agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
  min-height: 0;
}

.sub-agent-empty {
  padding: 20px 12px;
  font-size: 12px;
  color: var(--fg-3, #727987);
  text-align: center;
  line-height: 1.7;
}

.sub-agent-item {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 7px 6px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
}

.sub-agent-item:hover { background: var(--bg-hover, rgba(0, 0, 0, .05)); }
.sub-agent-item.selected { background: var(--accent-soft, rgba(37, 99, 235, .09)); }

.sub-agent-item-main { min-width: 0; flex: 1; }

.sub-agent-item-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.sub-agent-item-name {
  font-size: 12px;
  color: var(--fg, #1a1d21);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
  flex: 1;
  min-width: 0;
}

.sub-agent-item-time {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--fg-3, #727987);
}

.sub-agent-delete {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: var(--fg-3, #727987);
  cursor: pointer;
  padding: 3px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  opacity: 0;
  margin-top: 1px;
}

.sub-agent-item:hover .sub-agent-delete { opacity: 1; }
.sub-agent-delete:hover { color: #ef4444; background: rgba(239, 68, 68, .1); }
.sub-agent-delete:disabled { opacity: .4; cursor: not-allowed; }
.sub-agent-delete svg { width: 13px; height: 13px; }

.sub-agent-item-task {
  font-size: 12px;
  color: var(--fg, #1a1d21);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}
</style>
