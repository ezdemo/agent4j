<template>
  <div class="version-info-panel">
    <!-- 当前版本双栏卡片 -->
    <div class="update-versions-grid">
      <div class="update-version-card">
        <div class="uvc-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>
        </div>
        <div class="uvc-info">
          <div class="uvc-name">核心服务</div>
          <div class="uvc-version">v{{ appVersion || '-' }}</div>
        </div>
        <div v-if="hasNewVersion" class="uvc-status warn">有新版本</div>
        <div v-else class="uvc-status ok">已是最新</div>
      </div>

      <div v-if="isElectron" class="update-version-card">
        <div class="uvc-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
        </div>
        <div class="uvc-info">
          <div class="uvc-name">桌面端</div>
          <div class="uvc-version">v{{ electronVersion || '加载中...' }}</div>
        </div>
        <div v-if="desktopHasNewVersion" class="uvc-status warn">有新版本</div>
        <div v-else class="uvc-status ok">已是最新</div>
      </div>
    </div>

    <!-- 最新版本 + 发布地址 -->
    <div class="update-latest-row">
      <div class="ulr-label">最新发布</div>
      <div class="ulr-version" :class="{ 'has-update': hasNewVersion || desktopHasNewVersion }">
        v{{ latestVersion || '...' }}
      </div>
      <a v-if="releaseUrl" :href="releaseUrl" target="_blank" class="ulr-link" @click.prevent="handleDownload">查看发布页 →</a>
    </div>

    <!-- 更新命令 -->
    <div class="update-commands">
      <div class="uc-label">更新命令</div>
      <div class="uc-list">
        <div class="uc-item" @click="copyText('irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex')" title="点击复制">
          <span class="uc-badge win">PS</span>
          <code>irm ...setup.ps1 | iex</code>
        </div>
        <div class="uc-item" @click="copyText('curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash')" title="点击复制">
          <span class="uc-badge unix">sh</span>
          <code>curl ...setup.sh | bash</code>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="update-actions">
      <button class="btn btn-primary" :disabled="checking" @click="$emit('check')">
        <svg :class="{ 'animate-spin': checking }" fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
        </svg>
        {{ checking ? '检查中...' : '检查更新' }}
      </button>
      <button v-if="isElectron" class="btn btn-secondary" @click="handleDownload">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        下载新版
      </button>
      <button v-if="!isElectron" class="btn btn-secondary" :disabled="autoUpdating" @click="$emit('auto-update')" style="margin-left:auto;">
        <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
          <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3"/>
        </svg>
        {{ autoUpdating ? '正在创建会话...' : '自动更新' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import {message} from 'ant-design-vue'

const props = defineProps({
  appVersion: {type: String, default: ''},
  electronVersion: {type: String, default: ''},
  latestVersion: {type: String, default: ''},
  releaseUrl: {type: String, default: ''},
  hasNewVersion: {type: Boolean, default: false},
  desktopHasNewVersion: {type: Boolean, default: false},
  checking: {type: Boolean, default: false},
  isElectron: {type: Boolean, default: false},
  autoUpdating: {type: Boolean, default: false}
})

const emit = defineEmits(['check', 'download', 'auto-update'])

function copyText(text) {
  try {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('已复制到剪贴板')
  }
}

function handleDownload() {
  if (props.releaseUrl) {
    emit('download', props.releaseUrl)
  } else {
    const url = 'https://gitee.com/ezdemo/agent4j/releases/latest'
    emit('download', url)
  }
}
</script>

<style scoped>
.version-info-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 版本双栏卡片 */
.update-versions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.update-version-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.2s;
}

.update-version-card:hover {
  border-color: var(--accent);
}

.uvc-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-3);
  border-radius: 8px;
  color: var(--fg-2);
  flex-shrink: 0;
}

.uvc-info {
  flex: 1;
  min-width: 0;
}

.uvc-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  margin-bottom: 2px;
}

.uvc-version {
  font-size: 13px;
  font-weight: 700;
  color: var(--fg);
  font-family: var(--font-mono);
  letter-spacing: -0.3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uvc-status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

.uvc-status.ok {
  background: rgba(34, 197, 94, 0.12);
  color: #22c55e;
}

.uvc-status.warn {
  background: rgba(255, 159, 28, 0.12);
  color: #ff9f1c;
}

/* 最新版本行 */
.update-latest-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06), rgba(168, 85, 247, 0.06));
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 10px;
}

.ulr-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  flex-shrink: 0;
}

.ulr-version {
  font-size: 18px;
  font-weight: 700;
  color: var(--fg);
  font-family: var(--font-mono);
  letter-spacing: -0.5px;
}

.ulr-version.has-update {
  color: var(--accent);
}

.ulr-link {
  margin-left: auto;
  font-size: 12px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 500;
  flex-shrink: 0;
  opacity: 0.8;
  transition: opacity 0.2s;
}

.ulr-link:hover {
  opacity: 1;
  text-decoration: underline;
}

/* 更新命令 */
.update-commands {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.uc-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
}

.uc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.uc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.uc-item:hover {
  border-color: var(--accent);
  background: var(--bg-3);
}

.uc-item code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--fg-2);
  user-select: all;
}

.uc-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  flex-shrink: 0;
  letter-spacing: 0.3px;
}

.uc-badge.win {
  background: rgba(0, 120, 215, 0.15);
  color: #0078d7;
}

.uc-badge.unix {
  background: rgba(51, 51, 51, 0.12);
  color: var(--fg-2);
}

/* 操作按钮 */
.update-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
</style>
