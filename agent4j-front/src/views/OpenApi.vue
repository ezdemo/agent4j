<template>
  <div class="openapi-modal">
    <!-- 头部 -->
    <div class="modal-header">
      <div class="header-left">
        <svg fill="none" height="20" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="20">
          <path d="M16 3h5v5M8 3H3v5M3 16v5h5M16 21h5v-5"/>
          <line x1="21" x2="12" y1="3" y2="12"/>
          <line x1="3" x2="12" y1="3" y2="12"/>
          <line x1="21" x2="12" y1="21" y2="12"/>
          <line x1="3" x2="12" y1="21" y2="12"/>
        </svg>
        <span>OpenAPI 管理</span>
        <span class="badge">{{ sources.length }} 个接口源</span>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary btn-xs" @click="openAdd">
          <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
            <line x1="12" x2="12" y1="5" y2="19"/>
            <line x1="5" x2="19" y1="12" y2="12"/>
          </svg>
          添加
        </button>
        <button :disabled="loading" class="btn btn-xs" @click="refreshAll">
          <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
            <path d="M23 4v6h-6"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <div class="modal-body-scroll">
      <!-- 提示 -->
      <div class="info-bar">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" x2="12" y1="16" y2="12"/>
          <line x1="12" x2="12.01" y1="8" y2="8"/>
        </svg>
        <span>管理 OpenAPI 接口源。添加后 AI 代理可自动发现并调用这些 REST 接口。</span>
      </div>

      <!-- 接口搜索 -->
      <div class="search-section">
        <div class="search-row">
          <input v-model="searchKeyword" placeholder="搜索接口文档（多关键词用空格分隔，如：订单 查询）" type="text"
                 @keyup.enter="doSearch"/>
          <button :disabled="searching || !searchKeyword.trim()" class="btn btn-primary btn-xs" @click="doSearch">
            <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" x2="16.65" y1="21" y2="16.65"/>
            </svg>
            搜索
          </button>
        </div>

        <!-- 搜索结果 -->
        <div v-if="searchResult" class="search-result">
          <div class="result-head">
            <span>搜索结果</span>
            <button class="btn-icon-xs" title="清除" @click="clearSearch">×</button>
          </div>
          <div v-if="searching" class="result-loading">搜索中...</div>
          <div v-else-if="Array.isArray(searchResult) && searchResult.length === 0" class="result-empty">
            未找到匹配的接口
          </div>
          <div v-else-if="typeof searchResult === 'string'" class="result-empty">{{ searchResult }}</div>
          <div v-else class="result-list">
            <div v-for="(item, i) in searchResult" :key="i" class="result-item">
              <div class="ri-head">
                <code class="ri-name">{{ item.api_name }}</code>
                <span class="ri-cat">{{ item.category }}</span>
              </div>
              <div class="ri-desc">{{ item.description }}</div>
              <div class="ri-endpoint"><code>{{ item.endpoint }}</code></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载 / 错误 -->
      <div v-if="loading && sources.length === 0" class="state-box">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>
      <div v-else-if="error" class="state-box error">
        <p>{{ error }}</p>
        <button class="btn btn-xs" @click="loadData">重试</button>
      </div>

      <!-- 源卡片列表 -->
      <div v-if="sources.length > 0" class="card-list">
        <div v-for="source in sources" :key="source.docUrl" class="source-card">
          <div class="card-head">
            <div :class="source.status" class="status-tag">
              <span class="dot"></span>
              {{ statusLabel(source.status) }}
            </div>
            <div class="card-actions">
              <button class="btn-icon-xs" title="编辑" @click="openEdit(source)">
                <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button :disabled="loading" class="btn-icon-xs" title="刷新" @click="refreshSource(source)">
                <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <path d="M23 4v6h-6"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
              <button class="btn-icon-xs danger" title="删除" @click="removeSource(source)">
                <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="card-body">
            <div class="info-rows">
              <div class="row">
                <span class="label">文档</span>
                <code class="val">{{ source.docUrl }}</code>
              </div>
              <div v-if="source.authType && source.authType !== 'none'" class="row">
                <span class="label">认证</span>
                <code class="val">{{ authLabel(source) }}</code>
              </div>
              <div v-if="source.headers && Object.keys(source.headers).length" class="row">
                <span class="label">请求头</span>
                <code class="val">{{ JSON.stringify(source.headers) }}</code>
              </div>
            </div>
          </div>
          <div v-if="source.status === 'error' && source.errorMessage" class="card-error">
            <svg fill="none" height="11" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="11">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" x2="9" y1="9" y2="15"/>
              <line x1="9" x2="15" y1="9" y2="15"/>
            </svg>
            {{ source.errorMessage }}
          </div>
        </div>
      </div>

      <div v-if="sources.length === 0 && !loading" class="state-box">
        <p style="color:var(--fg-3)">暂无接口源，点击上方「添加」按钮注册一个 OpenAPI 文档</p>
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <Teleport to="body">
      <div v-if="showAddDialog" class="add-mask" @click.self="showAddDialog = false">
        <div class="add-dialog">
          <div class="add-head">
            <span>{{ editingSource ? '编辑 OpenAPI 接口源' : '添加 OpenAPI 接口源' }}</span>
            <button class="btn-icon-xs" @click="showAddDialog = false">×</button>
          </div>
          <div class="add-body">
            <div class="field">
              <label>文档地址 <span class="req">*</span></label>
              <input v-model="form.docUrl" placeholder="https://petstore.swagger.io/v2/swagger.json" type="text"/>
              <p class="hint">支持 http://、https:// 和 classpath: 开头</p>
            </div>
            <div class="field">
              <label>请求头（可选）</label>
              <div class="header-list">
                <div v-for="(h, i) in form.headerList" :key="i" class="header-row">
                  <input v-model="h.key" placeholder="名称" type="text"/>
                  <input v-model="h.value" placeholder="值" type="text"/>
                  <button class="btn-icon-xs" @click="form.headerList.splice(i, 1)">×</button>
                </div>
                <button class="btn btn-text btn-xs" @click="form.headerList.push({ key: '', value: '' })">+ 添加请求头
                </button>
              </div>
            </div>

            <!-- 认证方式 -->
            <div class="field">
              <label>认证方式</label>
              <div class="auth-tabs">
                <button :class="{ active: form.authType === 'none' }" class="auth-tab" @click="form.authType = 'none'">
                  无
                </button>
                <button :class="{ active: form.authType === 'bearer' }" class="auth-tab"
                        @click="form.authType = 'bearer'">Bearer Token
                </button>
                <button :class="{ active: form.authType === 'apikey' }" class="auth-tab"
                        @click="form.authType = 'apikey'">API Key
                </button>
                <button :class="{ active: form.authType === 'basic' }" class="auth-tab"
                        @click="form.authType = 'basic'">用户名/密码
                </button>
              </div>
            </div>

            <div v-if="form.authType === 'bearer'" class="field">
              <label>Token <span class="req">*</span></label>
              <input v-model="form.bearerToken" placeholder="sk-xxxxxx" type="text"/>
            </div>

            <div v-if="form.authType === 'apikey'" class="field">
              <label>Header 名称 <span class="req">*</span></label>
              <input v-model="form.apiKeyName" placeholder="X-API-Key" type="text"/>
              <label style="margin-top:8px">Header 值 <span class="req">*</span></label>
              <input v-model="form.apiKeyValue" placeholder="your-api-key" type="text"/>
            </div>

            <div v-if="form.authType === 'basic'" class="field">
              <label>用户名 <span class="req">*</span></label>
              <input v-model="form.basicUser" placeholder="admin" type="text"/>
              <label style="margin-top:8px">密码 <span class="req">*</span></label>
              <input v-model="form.basicPass" placeholder="••••••••" type="password"/>
            </div>
          </div>
          <div class="add-foot">
            <button class="btn btn-xs" @click="showAddDialog = false">取消</button>
            <button :disabled="!form.docUrl || submitting" class="btn btn-primary btn-xs" @click="submitAdd">
              {{ submitting ? '保存中...' : (editingSource ? '保存修改' : '添加') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {openApiAPI} from '../services/api'
import {useConfirm} from '../composables/useConfirm'
import {message} from 'ant-design-vue'

const {confirm} = useConfirm()

const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const showAddDialog = ref(false)
const editingSource = ref(null)
const sources = ref([])

// 搜索
const searchKeyword = ref('')
const searchResult = ref(null)
const searching = ref(false)

const form = ref({
  docUrl: '', headerList: [],
  authType: 'none', authConfig: {},
  bearerToken: '', apiKeyName: '', apiKeyValue: '', basicUser: '', basicPass: ''
})

function statusLabel(s) {
  return {loaded: '已加载', error: '失败', disabled: '禁用'}[s] || s
}

function authLabel(source) {
  if (!source.authType || source.authType === 'none') return ''
  const c = source.authConfig || {}
  if (source.authType === 'bearer') return 'Bearer Token'
  if (source.authType === 'apikey') return `API Key (${c.name || ''})`
  if (source.authType === 'basic') return `Basic (${c.username || ''})`
  return source.authType
}

// ==================== 搜索 ====================

async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searching.value = true
  try {
    const res = await openApiAPI.searchApis(kw)
    searchResult.value = res.data
  } catch (e) {
    searchResult.value = '搜索失败: ' + (e.message || '')
  } finally {
    searching.value = false
  }
}

function clearSearch() {
  searchKeyword.value = ''
  searchResult.value = null
}

async function loadData() {
  loading.value = true;
  error.value = ''
  try {
    const sr = await openApiAPI.getSources()
    sources.value = (sr.data || []).filter(s => s.docUrl)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  loading.value = true;
  await loadData()
}

// ==================== 打开对话框 ====================

function openAdd() {
  editingSource.value = null
  resetForm()
  showAddDialog.value = true
}

function openEdit(source) {
  editingSource.value = source
  // 预填表单
  form.value.docUrl = source.docUrl || ''

  // headers → headerList
  const hl = []
  if (source.headers) {
    for (const [k, v] of Object.entries(source.headers)) {
      hl.push({key: k, value: v})
    }
  }
  form.value.headerList = hl

  // 认证
  const authType = source.authType || 'none'
  form.value.authType = authType
  const ac = source.authConfig || {}
  form.value.bearerToken = ac.token || ''
  form.value.apiKeyName = ac.name || ''
  form.value.apiKeyValue = ac.value || ''
  form.value.basicUser = ac.username || ''
  form.value.basicPass = ac.password || ''

  showAddDialog.value = true
}

function resetForm() {
  form.value = {
    docUrl: '', headerList: [],
    authType: 'none', authConfig: {},
    bearerToken: '', apiKeyName: '', apiKeyValue: '', basicUser: '', basicPass: ''
  }
}

// ==================== 提交 ====================

async function submitAdd() {
  if (!form.value.docUrl) {
    message.warning('请填写文档地址');
    return
  }
  submitting.value = true
  try {
    // headers
    const headers = {}
    form.value.headerList.forEach(h => {
      if (h.key && h.value) headers[h.key] = h.value
    })

    // auth
    let authType = form.value.authType || 'none'
    let authConfig = null
    if (authType === 'bearer' && form.value.bearerToken) {
      authConfig = {token: form.value.bearerToken}
    } else if (authType === 'apikey' && form.value.apiKeyName && form.value.apiKeyValue) {
      authConfig = {name: form.value.apiKeyName, value: form.value.apiKeyValue}
    } else if (authType === 'basic' && form.value.basicUser && form.value.basicPass) {
      authConfig = {username: form.value.basicUser, password: form.value.basicPass}
    } else {
      authType = 'none'
    }

    if (editingSource.value) {
      // 编辑模式：先删旧的，再加新的
      const oldDocUrl = editingSource.value.docUrl
      // 如果 docUrl 没变，用 refresh；否则先删后加
      if (oldDocUrl === form.value.docUrl) {
        const res = await openApiAPI.refreshSource(form.value.docUrl, headers, authType, authConfig)
        if (res.success !== false) {
          message.success('修改成功')
        } else {
          message.error(res.error || '修改失败');
          submitting.value = false;
          return
        }
      } else {
        // docUrl 变了：先删旧的，再加新的
        await openApiAPI.removeSource(oldDocUrl)
        const res = await openApiAPI.addSource(form.value.docUrl, headers, authType, authConfig)
        if (res.success !== false) {
          message.success('修改成功')
        } else {
          message.error(res.error || '修改失败');
          submitting.value = false;
          return
        }
      }
    } else {
      // 添加模式
      const res = await openApiAPI.addSource(form.value.docUrl, headers, authType, authConfig)
      if (res.success !== false) {
        message.success('添加成功')
      } else {
        message.error(res.error || '添加失败');
        submitting.value = false;
        return
      }
    }

    showAddDialog.value = false
    resetForm()
    editingSource.value = null
    await loadData()
  } catch (e) {
    message.error('操作失败: ' + (e.message || ''))
  } finally {
    submitting.value = false
  }
}

async function refreshSource(source) {
  loading.value = true
  try {
    const res = await openApiAPI.refreshSource(
        source.docUrl,
        source.headers || {},
        source.authType || 'none',
        source.authConfig || null
    )
    if (res.success !== false) {
      message.success('刷新成功');
      await loadData()
    } else {
      message.error(res.error || '刷新失败')
    }
  } catch (e) {
    message.error('刷新失败: ' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

async function removeSource(source) {
  const ok = await confirm({
    title: '确认删除',
    message: `确定移除接口源「${source.docUrl}」？`,
    okText: '删除',
    cancelText: '取消'
  })
  if (!ok) return
  try {
    const res = await openApiAPI.removeSource(source.docUrl)
    if (res.success !== false) {
      message.success('已移除');
      await loadData()
    } else {
      message.error(res.error || '移除失败')
    }
  } catch (e) {
    message.error('移除失败: ' + (e.message || ''))
  }
}

onMounted(() => loadData())
</script>

<style scoped>
/* ========== 整体布局 ========== */
.openapi-modal {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-height: 80vh;
  width: 720px;
  max-width: 92vw;
}

/* 头部 */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--fg);
}

.header-left svg {
  flex-shrink: 0;
}

.badge {
  font-size: 11px;
  font-weight: 400;
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 1px 8px;
  border-radius: var(--r);
}

.header-actions {
  display: flex;
  gap: 6px;
}

/* 可滚动内容 */
.modal-body-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 16px;
}

/* 提示条 */
.info-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--accent-bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg-2);
}

/* 搜索 */
.search-section {
  margin-bottom: 12px;
}

.search-row {
  display: flex;
  gap: 6px;
}

.search-row input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
  color: var(--fg);
  font-size: 13px;
  outline: none;
}

.search-row input:focus {
  border-color: var(--accent);
}

.search-result {
  margin-top: 8px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
  overflow: hidden;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--bg-3);
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  font-weight: 500;
  color: var(--fg);
}

.result-loading, .result-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-3);
}

.result-list {
  display: flex;
  flex-direction: column;
  max-height: 240px;
  overflow-y: auto;
}

.result-item {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}

.result-item:last-child {
  border-bottom: none;
}

.ri-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.ri-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}

.ri-cat {
  font-size: 10px;
  padding: 0 4px;
  background: var(--accent-bg);
  color: var(--accent);
  border-radius: var(--r-sm);
}

.ri-desc {
  font-size: 11px;
  color: var(--fg-2);
}

.ri-endpoint {
  margin-top: 2px;
}

.ri-endpoint code {
  font-size: 10px;
  color: var(--fg-3);
}

/* 加载 / 空 / 错误 */
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
  gap: 8px;
  color: var(--fg-3);
  font-size: 13px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin .7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 源卡片 */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.source-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  overflow: hidden;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-3);
  border-bottom: 1px solid var(--border);
}

.status-tag {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 500;
}

.status-tag .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.status-tag.loaded .dot {
  background: var(--green);
}

.status-tag.loaded {
  color: var(--green);
}

.status-tag.error .dot {
  background: var(--red);
}

.status-tag.error {
  color: var(--red);
}

.status-tag.disabled .dot {
  background: var(--fg-4);
}

.status-tag.disabled {
  color: var(--fg-4);
}

.card-actions {
  display: flex;
  gap: 2px;
}

.card-body {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  gap: 16px;
}

.info-rows {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.info-rows .row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

.info-rows .label {
  color: var(--fg-3);
  flex-shrink: 0;
  min-width: 44px;
}

.info-rows .val {
  color: var(--fg);
  font-size: 11px;
  word-break: break-all;
}

.card-error {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  background: var(--danger-bg);
  border-top: 1px solid var(--border);
  color: var(--red);
  font-size: 11px;
}

.tool-section {
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.tool-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 3px 8px;
}

.search-box input {
  border: none;
  outline: none;
  background: transparent;
  color: var(--fg);
  font-size: 12px;
  width: 140px;
}

.tool-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 11px;
  cursor: default;
  transition: border-color var(--t);
}

.tool-chip:hover {
  border-color: var(--accent);
}

.tool-chip .method {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: var(--r-sm);
  flex-shrink: 0;
}

.mg-get {
  background: var(--green-bg);
  color: var(--green);
}

.mg-post {
  background: var(--accent-bg);
  color: var(--accent);
}

.mg-put {
  background: var(--warning-bg);
  color: var(--warning);
}

.mg-del {
  background: var(--danger-bg);
  color: var(--red);
}

.mg-patch {
  background: var(--accent-bg);
  color: var(--accent);
}

.tool-chip .name {
  color: var(--fg);
}

.tool-chip .tag {
  font-size: 9px;
  padding: 0 4px;
  background: var(--accent-bg);
  color: var(--accent);
  border-radius: var(--r-sm);
}

.tool-empty {
  font-size: 12px;
  color: var(--fg-3);
  padding: 12px 0;
}

/* 添加/编辑对话框 */
.add-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, .45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.add-dialog {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  width: 480px;
  max-width: 92vw;
  box-shadow: var(--shadow);
  overflow: hidden;
}

.add-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  font-size: 14px;
  color: var(--fg);
}

.add-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 55vh;
  overflow-y: auto;
}

.add-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

.field label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--fg);
}

.field .req {
  color: var(--red);
}

.field input[type="text"], .field input[type="password"] {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
  color: var(--fg);
  font-size: 13px;
}

.field input[type="text"]:focus, .field input[type="password"]:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-bg);
}

.field .hint {
  font-size: 11px;
  color: var(--fg-4);
  margin: 4px 0 0;
}

.header-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.header-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.header-row input {
  flex: 1;
}

/* 认证方式选项卡 */
.auth-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.auth-tab {
  padding: 5px 12px;
  font-size: 12px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg-3);
  color: var(--fg-2);
  cursor: pointer;
  transition: all var(--t);
}

.auth-tab:hover {
  border-color: var(--accent);
  color: var(--fg);
}

.auth-tab.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  cursor: pointer;
  font-size: 12px;
  transition: all var(--t);
  background: var(--bg-3);
  color: var(--fg);
}

.btn:hover {
  background: var(--border);
}

.btn-primary {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

.btn-primary:hover {
  opacity: .88;
}

.btn-primary:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.btn-text {
  background: transparent;
  border: none;
  color: var(--accent);
  padding: 0;
}

.btn-text:hover {
  text-decoration: underline;
}

.btn-xs {
  padding: 3px 8px;
  font-size: 11px;
}

.btn-icon-xs {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: var(--r);
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.btn-icon-xs:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.btn-icon-xs.danger:hover {
  background: var(--danger-bg);
  color: var(--red);
}
</style>
