<template>
  <div class="desktop-update" :class="{ embedded }" :data-theme="theme">
    <header class="du-header">
      <div class="du-title">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
        <span>Loopra 更新</span>
      </div>
      <button class="du-close" type="button" title="关闭" @click="closeWindow">×</button>
    </header>

    <main class="du-body">
      <!-- 优先更新桌面端提示：桌面端与核心服务均有新版本时建议先更新桌面端 -->
      <div v-if="platform.isElectron && desktopHasNewVersion && hasNewVersion" class="du-priority-tip">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        <span>检测到桌面端与核心服务均有新版本，建议<b>先更新桌面端</b>，桌面端更新后核心服务可再更新</span>
      </div>

      <VersionInfoPanel
        :app-version="appVersion"
        :electron-version="electronVersion"
        :latest-version="latestVersion"
        :release-url="releaseUrl"
        :has-new-version="hasNewVersion"
        :desktop-has-new-version="desktopHasNewVersion"
        :checking="checking"
        :is-electron="platform.isElectron"
        :update-source="updateSource"
        :show-actions="false"
        @check="handleCheckVersion"
        @download="openDownload"
        @core-update="handleCoreServiceUpdate"
        @desktop-update="handleDesktopUpdate"
        @auto-update="handleAutoUpdate"
      />

      <!-- 下载源选择：GitHub 直连 / 镜像列表（自动测速排序） -->
      <section class="du-section">
        <div class="du-section-title du-source-title">
          <span>下载源</span>
          <button class="du-speedtest" type="button" :disabled="testingMirrors" @click="runMirrorSpeedTest">
            <svg v-if="!testingMirrors" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z"/></svg>
            <svg v-else class="du-speedtest-spin" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-6.22-8.56"/></svg>
            {{ testingMirrors ? '测速中' : '重新测速' }}
          </button>
        </div>
        <div class="du-source-row">
          <label class="du-source-option" :class="{ active: updateSource === UPDATE_SOURCE_NORMAL }">
            <input v-model="updateSource" type="radio" :value="UPDATE_SOURCE_NORMAL" />
            <span class="du-source-name">GitHub 直连</span>
            <span class="du-source-desc">官方发布源，海外网络推荐</span>
          </label>
          <label v-for="m in mirrorList" :key="m.value" class="du-source-option" :class="{ active: updateSource === m.value }">
            <input v-model="updateSource" type="radio" :value="m.value" />
            <span class="du-source-name">{{ m.label }}</span>
            <span class="du-source-desc">{{ m.latency != null ? `延迟 ${m.latency}ms` : '测速中 / 暂不可用' }}</span>
          </label>
        </div>
      </section>
    </main>

    <!-- 底部操作栏：检查更新 / 更新核心服务 / 更新桌面端（Web 端为自动更新） -->
    <footer class="du-footer">
      <button class="btn btn-primary" :disabled="checking" @click="handleCheckVersion">
        <svg :class="{ 'animate-spin': checking }" fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
        </svg>
        {{ checking ? '检查中...' : '检查更新' }}
      </button>
      <button v-if="platform.isElectron" class="btn" :class="hasNewVersion ? 'btn-update-highlight' : 'btn-secondary'" :disabled="desktopHasNewVersion" :title="desktopHasNewVersion ? '请先更新桌面端后再更新核心服务' : ''" @click="handleCoreServiceUpdate">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <span v-if="hasNewVersion" class="btn-update-badge">新版</span>
        更新核心服务
      </button>
      <button class="btn" :class="platform.isElectron ? (desktopHasNewVersion ? 'btn-update-highlight' : 'btn-secondary') : 'btn-recommend'" @click="handleDesktopUpdate" :title="desktopHasNewVersion ? '检测到桌面端新版本，建议优先更新' : (platform.isElectron ? '' : '下载桌面端安装包，桌面端体验更佳')">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
        </svg>
        <span v-if="platform.isElectron && desktopHasNewVersion" class="btn-update-badge">新版</span>
        {{ platform.isElectron ? '更新桌面端' : '下载桌面端（推荐）' }}
      </button>
      <button v-if="!platform.isElectron" class="btn" :class="hasNewVersion ? 'btn-update-highlight' : 'btn-secondary'" @click="handleAutoUpdate">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <span v-if="hasNewVersion" class="btn-update-badge">新版</span>
        更新核心服务
      </button>
      <span class="du-footer-hint">{{ platform.isElectron ? '优先更新桌面端，再更新核心服务' : '更新将在聊天框中执行' }}</span>
    </footer>
  </div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {platform} from './services/platform'
import {systemAPI} from './services/api'
import {RELEASE_LATEST_URL} from './utils/constants'
import VersionInfoPanel from './components/VersionInfoPanel.vue'
import {UPDATE_SOURCE_NORMAL, loadUpdateSource, saveUpdateSource} from './utils/updateScripts'
import {
  MIRROR_SOURCES,
  applyLatencies,
  loadCachedLatencies,
  measureMirrors,
  sortMirrors
} from './utils/mirrors'
import {useAppStore} from './stores/app'

// embedded：以组件形式嵌入父页面（Web 端点击版本号在页面内展示），关闭/自动更新通过事件通知父组件
const props = defineProps({
  embedded: {type: Boolean, default: false}
})
const emit = defineEmits(['close', 'auto-update'])

const store = useAppStore()
const theme = computed(() => store.settings.theme)

// 下载源（localStorage 持久化，与其他窗口共享）：'normal'（GitHub 直连）或具体镜像 URL
const updateSource = ref(loadUpdateSource())

// 镜像列表（latency 由测速填充），按延迟升序排列（未测/失败的排末尾）
const mirrorList = ref(sortMirrors(applyLatencies(MIRROR_SOURCES, loadCachedLatencies())))
const testingMirrors = ref(false)

// 自动测速：并发测速所有镜像并按延迟排序（结果缓存 30 分钟，过期自动重测）
async function runMirrorSpeedTest() {
  if (testingMirrors.value) return
  testingMirrors.value = true
  try {
    mirrorList.value = await measureMirrors(MIRROR_SOURCES)
  } catch (e) {
    console.warn('[DesktopUpdate] mirror speed test failed:', e)
  } finally {
    testingMirrors.value = false
  }
}

// 版本信息
const appVersion = ref('')
const electronVersion = ref('')
const desktopAppVersion = ref('')
const latestVersion = ref('')
const releaseUrl = ref('')
const hasNewVersion = ref(false)
const desktopHasNewVersion = ref(false)
const checking = ref(false)

watch(updateSource, (value) => saveUpdateSource(value))

onMounted(async () => {
  // 镜像测速：无新鲜缓存（30 分钟内）时自动重测并按延迟排序，不阻塞主流程
  if (!loadCachedLatencies()) void runMirrorSpeedTest()
  await refreshCurrentVersion()
  await handleCheckVersion()
})

// 版本对比工具
function compareVersions(a, b) {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na > nb) return 1
    if (na < nb) return -1
  }
  return 0
}

// 刷新当前核心服务版本
async function refreshCurrentVersion() {
  try {
    const res = await systemAPI.getCurrentVersion()
    if (res.success && res.data) {
      appVersion.value = res.data.version || appVersion.value
    }
  } catch (e) {
    console.warn('[DesktopUpdate] failed to load current version:', e)
  }
}

// 获取 Electron 版本并判断是否落后
async function fetchElectronVersion() {
  if (!platform.isElectron) return
  try {
    const ver = await window.electronAPI.getElectronVersion()
    electronVersion.value = ver
    desktopAppVersion.value = ver
    if (latestVersion.value && ver && ver !== '未知') {
      desktopHasNewVersion.value = compareVersions(ver, latestVersion.value) < 0
    }
  } catch (e) {
    electronVersion.value = '未知'
    console.warn('[DesktopUpdate] failed to load electron version:', e)
  }
}

// 检查最新版本
async function handleCheckVersion() {
  checking.value = true
  try {
    const res = await systemAPI.checkLatestVersion()
    if (res.success && res.data) {
      hasNewVersion.value = !!res.data.hasNewVersion
      latestVersion.value = res.data.latestVersion || ''
      releaseUrl.value = res.data.releaseUrl || ''
      if (res.data.currentVersion) {
        appVersion.value = res.data.currentVersion
      }
    }
  } catch (e) {
    console.warn('[DesktopUpdate] failed to check latest version:', e)
  }
  if (platform.isElectron) {
    await fetchElectronVersion()
  }
  checking.value = false
}

// 打开下载页面（「查看发布页」链接）
async function openDownload() {
  const url = releaseUrl.value || RELEASE_LATEST_URL
  if (platform.isElectron) {
    try {
      await window.electronAPI.openExternal(url)
    } catch {
      window.open(url, '_blank')
    }
  } else {
    window.open(url, '_blank')
  }
}

// 更新桌面端：打开最新发布页下载桌面端安装包（优先于核心服务更新）
async function handleDesktopUpdate() {
  const url = releaseUrl.value || RELEASE_LATEST_URL
  if (platform.isElectron) {
    try {
      await window.electronAPI.openExternal(url)
    } catch {
      window.open(url, '_blank')
    }
  } else {
    window.open(url, '_blank')
  }
  message.info('已打开发布页，请下载最新桌面端安装包并覆盖安装')
}

// 更新核心服务：走聊天框更新逻辑（主窗口新建会话后由 Agent 在聊天框执行更新脚本）
async function handleCoreServiceUpdate() {
  if (!platform.isElectron) return
  try {
    const ok = await window.electronAPI?.updateWindow?.requestChatUpdate(updateSource.value)
    if (ok === false) {
      message.warning('主窗口未运行，无法发起聊天更新')
    } else {
      message.success('已通知主窗口新建更新会话，请前往聊天界面查看进度')
    }
  } catch (e) {
    console.error('[DesktopUpdate] chat update request failed:', e)
    message.error('发起聊天更新失败: ' + (e.message || '未知错误'))
  }
}

// Web 端「更新核心服务」：embedded 时由父组件（App.vue）执行聊天框更新；独立标签页时通知 opener
async function handleAutoUpdate() {
  if (platform.isElectron) {
    message.info('桌面端请使用「更新核心服务」按钮，由聊天框执行更新')
    return
  }
  if (props.embedded) {
    emit('auto-update')
    return
  }
  if (window.opener) {
    window.opener.postMessage({type: 'loopra-auto-update'}, '*')
    message.success('已在主窗口发起自动更新，可关闭本窗口')
  } else {
    message.warning('请从主窗口打开更新界面后执行自动更新')
  }
}

// 关闭更新窗口：embedded 时通知父组件隐藏
function closeWindow() {
  if (props.embedded) {
    emit('close')
  } else {
    platform.implementation.window.close()
  }
}
</script>

<style scoped>
.desktop-update {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
  color: var(--fg);
}

/* embedded（Web 端弹层）时填满父容器，避免 100vh 溢出导致底部按钮被裁剪 */
.desktop-update.embedded {
  height: 100%;
}

.du-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.du-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.du-close {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--fg-3);
  font-size: 16px;
  cursor: pointer;
}

.du-close:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.du-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.du-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 优先更新桌面端提示条 */
.du-priority-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 159, 28, 0.1);
  border: 1px solid rgba(255, 159, 28, 0.35);
  border-radius: var(--r);
  color: var(--yellow);
  font-size: 13px;
  line-height: 1.5;
}

.du-priority-tip svg {
  flex-shrink: 0;
  color: var(--yellow);
}

.du-priority-tip b {
  font-weight: 700;
}

.du-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg-3);
}

.du-source-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.du-speedtest {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-2);
  color: var(--fg-3);
  cursor: pointer;
  transition: all 0.15s;
}

.du-speedtest:hover:not(:disabled) {
  color: var(--fg);
  border-color: var(--border-2);
  background: var(--bg-3);
}

.du-speedtest:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.du-speedtest-spin {
  animation: du-speedtest-rotate 0.9s linear infinite;
}

@keyframes du-speedtest-rotate {
  to { transform: rotate(360deg); }
}

.du-source-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.du-source-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.du-source-option input {
  display: none;
}

.du-source-option.active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.du-source-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.du-source-desc {
  font-size: 12px;
  color: var(--fg-4);
}

/* 底部操作栏 */
.du-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-1);
  flex-shrink: 0;
}

.du-footer .btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.du-footer .btn svg {
  flex-shrink: 0;
}

.du-footer-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--fg-4);
}
</style>
