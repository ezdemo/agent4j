<template>
  <div class="chat">
    <!-- 可选头部 -->
    <div v-if="!hideHeader" class="chat-head">
      <span class="chat-head-title">对话</span>
      <span class="chat-head-count">{{ messages.length }} 条</span>
      <div style="flex:1"></div>
      <button class="btn btn-ghost btn-sm" @click="clearChat">清空</button>
      <button class="btn btn-ghost btn-sm" @click="exportChat">导出</button>
      <button :disabled="loadingPrompt" class="btn btn-ghost btn-sm" @click="viewSystemPrompt">提示词</button>
    </div>

    <!-- 流式加载动画横线 -->
    <div v-if="streaming" class="streaming-bar">
      <div class="streaming-bar-inner"></div>
    </div>

    <!-- 悬浮日志通知（全局，不受消息滚动影响） -->
    <div class="log-stack">
      <TransitionGroup name="log-bar">
        <div v-for="log in currentLogs" :key="log.id" :class="'log-' + (log.level || 'info').toLowerCase()"
             class="log-bar"
             @click="currentLogs = currentLogs.filter(l => l.id !== log.id)">
          <span class="log-bar-icon">📋</span>
          <span class="log-bar-text">{{ log.text }}</span>
          <span class="log-bar-time">{{ formatTime(log.time) }}</span>
        </div>
      </TransitionGroup>
    </div>

    <!-- 消息区 -->
    <div ref="messagesContainer" class="messages" :class="{ 'messages-welcome': !props.sessionName || messages.length === 0 }">
      <!-- 空状态：无会话或新建的空会话 -->
      <div v-if="!props.sessionName || messages.length === 0" class="empty welcome-screen">
        <section class="welcome-panel">
          <h1 class="welcome-heading">{{ welcomeGreeting }}</h1>
          <div class="welcome-composer" :class="{ 'workspace-menu-open': welcomeWorkspaceMenuOpen }">
            <div class="welcome-workspace-row">
              <button class="welcome-workspace-button" type="button" @click="toggleWelcomeWorkspace">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2h6.5A2.5 2.5 0 0 1 21 9.5v8A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z"/></svg>
                <span>{{ selectedWelcomeWorkspace?.name || '选择项目' }}</span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </button>
              <div v-if="welcomeWorkspaceMenuOpen" class="welcome-workspace-menu">
                <div class="welcome-workspace-menu-actions">
                  <button class="welcome-workspace-manage" type="button" @click="openWelcomeWorkspaceManager">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2h6.5A2.5 2.5 0 0 1 21 9.5v8A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z"/><path d="M12 10v6m-3-3h6"/></svg>
                    项目管理
                  </button>
                </div>
                <div class="welcome-workspace-list">
                  <button v-for="workspace in workspaces" :key="workspace.hash" type="button"
                          :class="{ active: workspace.hash === welcomeWorkspaceHash }"
                          @click="selectWelcomeWorkspace(workspace.hash)">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2h6.5A2.5 2.5 0 0 1 21 9.5v8A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z"/></svg>
                    {{ workspace.name }}
                  </button>
                  <span v-if="workspaces.length === 0" class="welcome-workspace-empty">暂无可用项目</span>
                </div>
              </div>
            </div>
            <ChatInput ref="welcomeInput" welcome-mode v-model:input-text="welcomeText" :usage="usage" :current-model="currentModel" :default-model="defaultModel" :default-model-channel-id="defaultModelChannelId" :setting-default-model="settingDefaultModel" :available-models="availableModels"
                       :current-reasoning-effort="currentReasoningEffort" :terminate-on-no-tool-call="terminateOnNoToolCall" :current-permission="currentPermission"
                       :workspace-hash="welcomeWorkspaceHash" :current-skill="currentSkill" @send="sendWelcomeMessage" @switch-model="handleSwitchModel" @set-default-model="handleSetDefaultModel"
                       @switch-reasoning-effort="handleSwitchReasoningEffort" @switch-terminate-on-no-tool-call="handleSwitchTerminateOnNoToolCall"
                       @switch-permission="handleSwitchPermission" @switch-skill="handleSwitchSkill" @picker-open="handleWelcomePickerOpen" @refresh-models="loadUsage" @manage-models="$emit('manageModels')" />
          </div>
        </section>
      </div>

      <!-- 消息列表：仅挂载可视区域附近的消息，保留上下占位以维持滚动位置。 -->
      <div v-if="virtualWindow.topHeight" class="virtual-spacer" :style="{ height: virtualWindow.topHeight + 'px' }"></div>
      <div v-for="item in virtualWindow.items" :key="item.msg.id"
           :ref="el => observeMessage(item.msg.id, el)"
           class="virtual-message-item"
           :class="{ 'message-role-transition': item.idx > 0 && messages[item.idx - 1]?.role !== item.msg.role }">
        <ChatMessage
            v-memo="[
              item.msg.id,
              item.msg.content,
              item.msg.blocks?.length,
              item.msg.rollbackId,
              item.msg.snapshotId,
              item.msg.rollbackTimestamp,
              item.idx === activeAssistantMessageIndex ? streamRenderVersion : 0,
              streaming,
              branchingSession,
              snapshotRollbackLoading.get(item.msg.rollbackId || item.msg.snapshotId || item.msg.rollbackTimestamp)
            ]"
            :idx="item.idx"
            :msg="item.msg"
            :workspace-path="activeWorkspacePath"
            :streaming="item.idx === activeAssistantMessageIndex"
            :snapshot-rollback-loading="snapshotRollbackLoading"
            :rollback-disabled="streaming"
            :branch-disabled="streaming || branchingSession"
            @preview-image="previewImage"
            @rollback-snapshot="openRollbackDialog"
            @copy-message="copyMessage"
            @branch-session="branchSession"
            @send-choice="sendChoice"
            @open-file="openFile"
            @open-diff="openStoredDiff"
            @revert-file-changes="openFileRevertDialog"
        />
      </div>
      <div v-if="virtualWindow.bottomHeight" class="virtual-spacer" :style="{ height: virtualWindow.bottomHeight + 'px' }"></div>



      <!-- 加载中：AI 准备中 -->
      <div v-if="waitingForAI" class="msg assistant">
        <div class="msg-body assistant-body">
          <div class="ai-preparing">
            <span class="ai-dot"></span>
            <span class="ai-dot"></span>
            <span class="ai-dot"></span>
            <span class="ai-label">AI 正在思考...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 工作流 TODO 指示器（已迁移至 ChatInput 组件中） -->

    <!-- 消息缩略图 dock（右侧 dock 栏，仅用户消息） -->
    <div v-if="userMessages.length > 0" class="msg-thumb-dock">
      <div class="thumb-dock-inner">
        <div
          v-for="(um, ui) in userMessages"
          :key="um.id"
          class="thumb-item"
          @click="jumpToMessage(um.globalIdx)"
        >
          <span class="thumb-indicator"></span>
          <span class="thumb-preview">{{ truncateText(um.content, 40) }}</span>
        </div>
      </div>
    </div>

    <!-- 滚动到底部按钮（固定在消息区右下角） -->
    <button v-show="showScrollBtn" class="scroll-bottom-btn" @click="scrollToBottom" title="滚动到底部">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <polyline points="7 13 12 18 17 13"/>
        <line x1="12" y1="18" x2="12" y2="6"/>
      </svg>
    </button>



    <!-- 系统提示词 Modal -->
    <Teleport to="body">
      <div v-if="promptModalOpen" class="prompt-modal-overlay" @click.self="promptModalOpen = false">
        <div class="prompt-modal">
          <div class="prompt-modal-head">
            <h3>系统提示词</h3>
            <span class="prompt-modal-size">{{ promptLength }} 字符</span>
            <div style="flex:1"></div>
            <button class="prompt-modal-close" @click="promptModalOpen = false">&times;</button>
          </div>
          <div class="prompt-modal-body">
            <div class="prompt-modal-content" v-html="fmtPrompt(promptContent)"></div>
          </div>
          <div class="prompt-modal-foot">
            <button class="btn btn-sm" @click="copyPrompt">复制</button>
            <button class="btn btn-sm" @click="promptModalOpen = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 图片预览弹窗 -->
    <Teleport to="body">
      <div v-if="imagePreviewOpen" class="image-preview-overlay" role="dialog" aria-modal="true" aria-label="图片预览" @click.self="closeImagePreview">
        <img :src="imagePreviewUrl" alt="图片预览" class="image-preview-full"/>
        <button type="button" class="image-preview-close" aria-label="关闭图片预览" title="关闭" @click="closeImagePreview">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </Teleport>

    <!-- Diff 查看器弹窗（复用 DiffViewer 组件） -->
    <DiffViewer
        :open="diffViewer.open"
        :file="diffViewer.file"
        :diff="diffViewer.diff"
        :content="diffViewer.content"
        :mode="diffViewer.mode"
        :loading="diffViewer.loading"
        :stat="diffViewer.stat"
        @close="closeDiffViewer"
        @change-mode="changeDiffViewerMode"
    />

    <!-- 输入区（独立组件） -->
    <Transition name="welcome-input-drop">
    <ChatInput v-if="props.sessionName && messages.length > 0"
        ref="chatInput"
        v-model:inputText="inputText"
        :streaming="streaming"
        :usage="usage"
        :currentModel="currentModel"
        :default-model="defaultModel"
        :default-model-channel-id="defaultModelChannelId"
        :setting-default-model="settingDefaultModel"
        :availableModels="availableModels"
        :currentReasoningEffort="currentReasoningEffort"
        :terminateOnNoToolCall="terminateOnNoToolCall"
        :workspaceHash="props.workspaceHash"
        :sessionName="props.sessionName"
        :rightPanelOpen="props.rightPanelOpen"
        :hasHistory="hasHistory"
        :version="props.version"
        :currentSkill="currentSkill"
        :currentPermission="currentPermission"
        :petState="petState"
        :queued-messages="queuedMessages"
        @send="(imgs, text) => sendMessage(imgs, text)"
        @remove-queued="removeQueuedMessage"
        @guide-queued="guideQueuedMessage"
        @abort="abortChat"
        @clear="clearChat"
        @export="exportChat"
        @refreshUsage="loadUsage"
        @switchModel="handleSwitchModel"
        @set-default-model="handleSetDefaultModel"
        @switchReasoningEffort="handleSwitchReasoningEffort"
        @switchTerminateOnNoToolCall="handleSwitchTerminateOnNoToolCall"
        @refreshModels="loadUsage"
        @continue="continueChat"
        @switchSkill="handleSwitchSkill"
        @switchPermission="handleSwitchPermission"
        @manageModels="$emit('manageModels')"
    />
    </Transition>

    <ActionConfirmDialog
        :model-value="rollbackDialog.visible"
        title="撤回消息"
        message="将删除当前消息及其后的会话内容。撤回代码会将工作区恢复到发送此消息前的状态。"
        :actions="rollbackActions"
        @update:model-value="value => { if (!value) closeRollbackDialog() }"
        @action="handleRollbackAction"
    />
    <ActionConfirmDialog
        :model-value="fileRevertDialog.visible"
        title="撤销本次代码修改"
        message="将按该回复记录的 diff 反向回打补丁，只撤销本次 AI 修改，不删除会话消息。文件在之后被修改过时会拒绝撤销。"
        :actions="fileRevertActions"
        :pending="fileRevertDialog.pending"
        @update:model-value="value => { if (!value) closeFileRevertDialog() }"
        @action="handleFileRevertAction"
    />
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {agentAPI, chatAPI, configAPI, gitAPI, sessionsAPI, snapshotAPI} from '../services/api'
import {md} from '../utils/highlight'
import {sanitize} from '../utils/sanitize'
import {getAssistantTurnBoundaries} from '../utils/sessionBranch'
import ChatInput from '../components/ChatInput.vue'
import ChatMessage from '../components/ChatMessage.vue'
import DiffViewer from '../components/DiffViewer.vue'
import ActionConfirmDialog from '../components/ActionConfirmDialog.vue'

import {useAppStore} from '../stores/app'

// ============= 模型切换 =============
const handleSwitchModel = async (modelName, channelId) => {
  const currentChannelId = availableModels.value.find((model) => model.active)?.channelId
  if (modelName === currentModel.value && (!channelId || channelId === currentChannelId)) return
  if (props.sessionName) {
    const selection = {model: modelName, channelId: channelId || currentChannelId || ''}
    sessionModelSelections.value = {...sessionModelSelections.value, [conversationKey()]: selection}
    currentModel.value = modelName
    availableModels.value = availableModels.value.map(model => ({
      ...model,
      active: model.name === selection.model && (model.channelId || '') === selection.channelId
    }))
    return
  }
  try {
    const payload = {model: modelName}
    if (channelId) payload.modelChannelId = channelId
    const r = await configAPI.updateConfig(payload)
    if (r.success) {
      currentModel.value = modelName
      defaultModel.value = modelName
      defaultModelChannelId.value = channelId || currentChannelId || ''
      availableModels.value.forEach(m => {
        m.active = m.name === modelName && (!channelId || m.channelId === channelId)
      })
      loadUsage()
    }
  } catch (e) {
    console.error('切换模型失败:', e)
  }
}

const handleSetDefaultModel = async (modelName, channelId) => {
  if (!modelName || settingDefaultModel.value) return
  if (modelName === defaultModel.value && (channelId || '') === defaultModelChannelId.value) return
  settingDefaultModel.value = true
  try {
    const payload = {model: modelName}
    if (channelId) payload.modelChannelId = channelId
    const response = await configAPI.updateConfig(payload)
    if (response.success) {
      defaultModel.value = modelName
      defaultModelChannelId.value = channelId || defaultModelChannelId.value
      message.success('默认模型已更新')
      await loadUsage()
    }
  } catch (error) {
    console.error('设定默认模型失败:', error)
  } finally {
    settingDefaultModel.value = false
  }
}

// ============= 推理强度切换 =============
const currentReasoningEffort = ref('max')
const terminateOnNoToolCall = ref(true)

const handleSwitchReasoningEffort = (value) => {
  const reasoningEffort = String(value || '').trim()
  if (!reasoningEffort || reasoningEffort === currentReasoningEffort.value) return
  sessionReasoningEfforts.value = {
    ...sessionReasoningEfforts.value,
    [conversationKey()]: reasoningEffort
  }
  currentReasoningEffort.value = reasoningEffort
}

const handleSwitchTerminateOnNoToolCall = async (value) => {
  if (value === terminateOnNoToolCall.value) return
  try {
    const r = await configAPI.updateConfig({terminateOnNoToolCall: value})
    if (r.success) {
      terminateOnNoToolCall.value = value
    }
  } catch (e) {
    console.error('更新无工具调用结束策略失败:', e)
  }
}

// ============= 技能切换（多选） =============
const currentSkill = ref([])
const handleSwitchSkill = (skills) => {
  currentSkill.value = skills
}

// ============= 权限切换（hitl） =============
const currentPermission = ref('free')
const handleSwitchPermission = async (mode) => {
  if (mode === currentPermission.value) return
  try {
    const r = await configAPI.updateConfig({hitl: mode})
    if (r.success) {
      currentPermission.value = mode
    }
  } catch (e) {
    console.error('切换权限模式失败:', e)
  }
}

const props = defineProps({
  hideHeader: {type: Boolean, default: false},
  workspaceHash: {type: String, default: null},
  sessionName: {type: String, default: null},
  rightPanelOpen: {type: Boolean, default: false},
  workspaces: {type: Array, default: () => []},
  version: {type: String, default: ''}
})

const emit = defineEmits(['sessionUpdated', 'sessionBranched', 'startTask', 'switchWorkspace', 'manageWorkspaces', 'manageModels'])
const store = useAppStore()

const messagesContainer = ref(null)
const inputText = ref('')
const chatInput = ref(null)
const welcomeInput = ref(null)
const welcomeText = ref('')
const welcomeWorkspaceHash = ref('')
const welcomeWorkspaceMenuOpen = ref(false)
const welcomeModelMenuOpen = ref(false)
const welcomePermissionSelector = ref(null)
const welcomeEffortSelector = ref(null)
const welcomeSkillSelector = ref(null)

const welcomeGreeting = computed(() => {
  const hour = new Date().getHours()
  const period = hour < 5 ? '凌晨好' : hour < 8 ? '早晨好' : hour < 12 ? '上午好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : hour < 22 ? '晚间好' : '深夜好'
  const prompts = [
    '有什么想让我帮忙的吗？',
    '想先从哪件事开始？',
    '今天准备推进什么？',
    '有什么问题需要一起解决？',
    '把接下来的任务交给我吧。',
    '需要我帮你梳理一下思路吗？',
    '想先查看项目的哪个部分？',
    '有什么任务需要我协助完成？',
    '准备好开始下一项工作了吗？',
    '现在最想解决的问题是什么？'
  ]
  return `${period}，${prompts[Math.floor(Math.random() * prompts.length)]}`
})

const selectedWelcomeWorkspace = computed(() =>
  props.workspaces.find(workspace => workspace.hash === welcomeWorkspaceHash.value)
)
const activeWorkspacePath = computed(() =>
  props.workspaces.find(workspace => workspace.hash === props.workspaceHash)?.path || ''
)


watch(() => props.workspaceHash, (hash) => {
  if (hash) welcomeWorkspaceHash.value = hash
}, {immediate: true})

watch(() => props.workspaces, (workspaces) => {
  if (workspaces.length && !workspaces.some(workspace => workspace.hash === welcomeWorkspaceHash.value)) {
    welcomeWorkspaceHash.value = workspaces[0].hash
  }
}, {immediate: true})


const selectWelcomeWorkspace = (workspaceHash) => {
  welcomeWorkspaceMenuOpen.value = false
  emit('switchWorkspace', workspaceHash)
}

const openWelcomeWorkspaceManager = () => {
  welcomeWorkspaceMenuOpen.value = false
  emit('manageWorkspaces')
}

const closeWelcomeMenus = (except = '') => {
  if (except !== 'workspace') welcomeWorkspaceMenuOpen.value = false
  if (except !== 'model') welcomeModelMenuOpen.value = false
  if (except !== 'permission') welcomePermissionSelector.value?.close()
  if (except !== 'effort') welcomeEffortSelector.value?.close()
  if (except !== 'skill') welcomeSkillSelector.value?.close()
}

const toggleWelcomeWorkspace = () => {
  const nextOpen = !welcomeWorkspaceMenuOpen.value
  closeWelcomeMenus('workspace')
  if (nextOpen) welcomeInput.value?.closePickers()
  welcomeWorkspaceMenuOpen.value = nextOpen
}

const handleWelcomePickerOpen = () => {
  closeWelcomeMenus()
}

const toggleWelcomeModel = () => {
  const nextOpen = !welcomeModelMenuOpen.value
  closeWelcomeMenus('model')
  welcomeModelMenuOpen.value = nextOpen
}

const handleWelcomeOutsideClick = (event) => {
  const target = event.target
  if (target.closest('.welcome-workspace-row, .welcome-model-selector, .permission-selector, .reasoning-selector, .skill-selector')) return
  closeWelcomeMenus()
}

const selectWelcomeModel = async (modelName) => {
  welcomeModelMenuOpen.value = false
  await handleSwitchModel(modelName)
}


const sendWelcomeMessage = async (images, messageText) => {
  const prompt = messageText?.trim()
  if (!prompt) return

  if (props.sessionName) {
    welcomeText.value = ''
    await sendMessage(images, prompt)
    return
  }

  if (!welcomeWorkspaceHash.value) return
  welcomeText.value = ''
  emit('startTask', {prompt, workspaceHash: welcomeWorkspaceHash.value})
}

// 快照检查点：msgId -> snapshotId 映射（用于消息关联和撤回按钮显示）
const snapshotMap = ref(new Map())
const snapshotRollbackLoading = ref(new Map()) // msgId -> 是否正在撤回
const rollbackDialog = ref({visible: false, msgId: null, canRollbackCode: false})
const fileRevertDialog = ref({visible: false, pending: false, changes: []})
const fileRevertActions = [{key: 'cancel', label: '取消'}, {key: 'revert', label: '撤销代码', variant: 'danger'}]
const rollbackActions = computed(() => {
  const actions = [
    {key: 'cancel', label: '取消'},
    {key: 'message', label: '只撤回消息', variant: 'accent'}
  ]
  if (rollbackDialog.value.canRollbackCode) {
    actions.push({key: 'code', label: '撤回消息和代码', variant: 'danger'})
  }
  return actions
})

// 图片预览
const imagePreviewUrl = ref('')
const imagePreviewOpen = ref(false)
const previewImage = (url) => {
  imagePreviewUrl.value = url
  imagePreviewOpen.value = true
}
const closeImagePreview = () => {
  imagePreviewOpen.value = false
  imagePreviewUrl.value = ''
}
const handleImagePreviewKeydown = (event) => {
  if (event.key === 'Escape' && imagePreviewOpen.value) closeImagePreview()
}

// Diff 查看器
const diffViewer = ref({ open: false, file: '', diff: '', content: '', mode: 'content', loading: false, stat: '', diffStat: '', contentLoaded: false, contentExists: false, diffLoaded: false })

const loadDiffViewerContent = async () => {
  const file = diffViewer.value.file
  if (!file) return
  diffViewer.value.loading = true
  try {
    const r = await gitAPI.workingFileContent(props.workspaceHash, file)
    if (r.success && r.data) {
      if (diffViewer.value.file !== file || !diffViewer.value.open) return
      diffViewer.value.content = r.data.content ?? r.data.message ?? ''
      diffViewer.value.contentLoaded = true
      diffViewer.value.contentExists = Boolean(r.data.exists)
      if (diffViewer.value.mode === 'content') diffViewer.value.stat = diffViewer.value.contentExists ? '当前文件' : '文件不可用'
    }
  } catch (e) {
    if (diffViewer.value.file === file && diffViewer.value.open) {
      diffViewer.value.content = '加载文件失败: ' + (e.message || '')
      diffViewer.value.contentLoaded = true
    }
  } finally {
    if (diffViewer.value.file === file && diffViewer.value.open) diffViewer.value.loading = false
  }
}

const loadDiffViewerDiff = async () => {
  const file = diffViewer.value.file
  if (!file) return
  diffViewer.value.loading = true
  try {
    const r = await gitAPI.diffContent(props.workspaceHash, file)
    if (r.success && r.data) {
      if (diffViewer.value.file !== file || !diffViewer.value.open) return
      diffViewer.value.diff = r.data.diff || ''
      diffViewer.value.diffLoaded = true
      diffViewer.value.diffStat = r.data.stat || ''
      if (diffViewer.value.mode === 'diff') diffViewer.value.stat = diffViewer.value.diffStat
    }
  } catch (e) {
    if (diffViewer.value.file === file && diffViewer.value.open) {
      diffViewer.value.diff = '加载 Git diff 失败: ' + (e.message || '')
      diffViewer.value.diffLoaded = true
    }
  } finally {
    if (diffViewer.value.file === file && diffViewer.value.open) diffViewer.value.loading = false
  }
}

const changeDiffViewerMode = async (mode) => {
  if (!diffViewer.value.open || diffViewer.value.mode === mode) return
  diffViewer.value.mode = mode
  if (mode === 'content') {
    diffViewer.value.stat = diffViewer.value.contentLoaded ? (diffViewer.value.contentExists ? '当前文件' : '文件不可用') : ''
    if (!diffViewer.value.contentLoaded) await loadDiffViewerContent()
  }
  if (mode === 'diff') {
    diffViewer.value.stat = diffViewer.value.diffStat
    if (!diffViewer.value.diffLoaded) await loadDiffViewerDiff()
  }
}

const openDiff = async (filePath) => {
  diffViewer.value = { open: true, file: filePath, diff: '', content: '', mode: 'content', loading: true, stat: '当前文件', diffStat: '', contentLoaded: false, contentExists: false, diffLoaded: false }
  await loadDiffViewerContent()
}
const openStoredDiff = (change) => {
  // AI 消息底部的文件变更列表：点击默认展示 Git Diff（diff 已随 change 内联保存，无需等待）
  diffViewer.value = {
    open: true,
    file: change?.path || '',
    diff: change?.diff || '此历史记录没有保存差异快照。',
    content: '',
    mode: 'diff',
    loading: false,
    stat: `+${change?.additions || 0} -${change?.deletions || 0}`,
    diffStat: `+${change?.additions || 0} -${change?.deletions || 0}`,
    contentLoaded: false,
    contentExists: false,
    diffLoaded: true
  }
  // 当前文件内容懒加载：切到「当前文件」标签时由 changeDiffViewerMode 触发，避免打开 diff 时白等 content
}
const closeDiffViewer = () => {
  diffViewer.value = { open: false, file: '', diff: '', content: '', mode: 'content', loading: false, stat: '', diffStat: '', contentLoaded: false, contentExists: false, diffLoaded: false }
}

const messages = computed(() => store.getSessionMessages(props.sessionName))
const streaming = computed(() => store.getSessionStreaming(props.sessionName))
const queuedMessagesBySession = ref({})
const SESSION_MODEL_STORAGE_KEY = 'loopra.session-model-selections'
const SESSION_REASONING_EFFORT_STORAGE_KEY = 'loopra.session-reasoning-efforts'
const loadSessionModelSelections = () => {
  try {
    const stored = JSON.parse(localStorage.getItem(SESSION_MODEL_STORAGE_KEY) || '{}')
    return stored && typeof stored === 'object' ? stored : {}
  } catch {
    return {}
  }
}
const loadSessionReasoningEfforts = () => {
  try {
    const stored = JSON.parse(localStorage.getItem(SESSION_REASONING_EFFORT_STORAGE_KEY) || '{}')
    return stored && typeof stored === 'object' ? stored : {}
  } catch {
    return {}
  }
}
const sessionModelSelections = ref(loadSessionModelSelections())
const sessionReasoningEfforts = ref(loadSessionReasoningEfforts())
const conversationKey = (workspaceHash = props.workspaceHash, sessionName = props.sessionName) => `${workspaceHash || ''}::${sessionName || ''}`
const queuedMessages = computed(() => queuedMessagesBySession.value[conversationKey()] || [])

watch(sessionModelSelections, selections => {
  localStorage.setItem(SESSION_MODEL_STORAGE_KEY, JSON.stringify(selections))
}, {deep: true})

watch(sessionReasoningEfforts, efforts => {
  localStorage.setItem(SESSION_REASONING_EFFORT_STORAGE_KEY, JSON.stringify(efforts))
}, {deep: true})

const getSessionModelSelection = (sessionName = props.sessionName, workspaceHash = props.workspaceHash) => {
  const selected = sessionModelSelections.value[conversationKey(workspaceHash, sessionName)]
  if (selected) return selected
  const active = availableModels.value.find(model => model.active)
  return {model: currentModel.value, channelId: active?.channelId || ''}
}

const getSessionReasoningEffort = (sessionName = props.sessionName, workspaceHash = props.workspaceHash) => (
  sessionReasoningEfforts.value[conversationKey(workspaceHash, sessionName)] || currentReasoningEffort.value
)

const addQueuedMessage = (sessionName, workspaceHash, images, text, modelSelection, reasoningEffort) => {
  if (!sessionName) return
  const key = conversationKey(workspaceHash, sessionName)
  const queue = queuedMessagesBySession.value[key] || []
  queuedMessagesBySession.value = {
    ...queuedMessagesBySession.value,
    [key]: [...queue, {id: `${Date.now()}-${Math.random().toString(36).slice(2)}`, workspaceHash, images, text, modelSelection, reasoningEffort}]
  }
}

const takeQueuedMessage = (sessionName, workspaceHash, id) => {
  const key = conversationKey(workspaceHash, sessionName)
  const queue = queuedMessagesBySession.value[key] || []
  const item = queue.find(message => message.id === id)
  if (!item) return null
  queuedMessagesBySession.value = {
    ...queuedMessagesBySession.value,
    [key]: queue.filter(message => message.id !== id)
  }
  return item
}

const removeQueuedMessage = (id) => {
  takeQueuedMessage(props.sessionName, props.workspaceHash, id)
}

const sendNextQueuedMessage = async (sessionName, workspaceHash) => {
  if (!sessionName || store.getSessionStreaming(sessionName)) return
  const queue = queuedMessagesBySession.value[conversationKey(workspaceHash, sessionName)] || []
  const next = queue[0]
  if (!next) return
  takeQueuedMessage(sessionName, workspaceHash, next.id)
  await sendMessage(next.images, next.text, next.modelSelection, sessionName, workspaceHash, next.reasoningEffort)
}

const guideQueuedMessage = async (id) => {
  const queued = takeQueuedMessage(props.sessionName, props.workspaceHash, id)
  if (!queued) return
  if (streaming.value) await abortChat()
  await sendMessage(queued.images, queued.text, queued.modelSelection, props.sessionName, queued.workspaceHash, queued.reasoningEffort)
}

const ESTIMATED_MESSAGE_HEIGHT = 320
const VIRTUAL_OVERSCAN = 1200
const VIRTUALIZATION_THRESHOLD = 60
const messageKey = message => String(message.id)
const messageHeights = reactive(new Map())
const virtualScrollTop = ref(0)
const virtualViewportHeight = ref(0)
const virtualWindow = computed(() => {
  const total = messages.value.length
  const offsets = new Array(total + 1).fill(0)
  for (let index = 0; index < total; index++) {
    offsets[index + 1] = offsets[index] + (messageHeights.get(messageKey(messages.value[index])) || ESTIMATED_MESSAGE_HEIGHT)
  }

  if (total <= VIRTUALIZATION_THRESHOLD) {
    return {
      items: messages.value.map((msg, idx) => ({msg, idx})),
      topHeight: 0,
      bottomHeight: 0,
      offsets
    }
  }

  const startOffset = Math.max(0, virtualScrollTop.value - VIRTUAL_OVERSCAN)
  const endOffset = virtualScrollTop.value + virtualViewportHeight.value + VIRTUAL_OVERSCAN
  let start = 0
  while (start < total && offsets[start + 1] < startOffset) start++
  let end = start
  while (end < total && offsets[end] < endOffset) end++
  return {
    items: messages.value.slice(start, end).map((msg, offset) => ({msg, idx: start + offset})),
    topHeight: offsets[start],
    bottomHeight: offsets[total] - offsets[end],
    offsets
  }
})

let messageResizeObserver = null
let pendingScrollAdjustment = 0
let scrollAdjustmentFrame = 0
const observedMessageElements = new Map()
const observeMessage = (id, element) => {
  const key = String(id)
  const previous = observedMessageElements.get(key)
  if (!element) {
    if (previous) messageResizeObserver?.unobserve(previous)
    observedMessageElements.delete(key)
    return
  }
  if (previous && previous !== element) messageResizeObserver?.unobserve(previous)
  element.dataset.virtualMessageId = key
  observedMessageElements.set(key, element)
  messageResizeObserver?.observe(element)
}

const updateVirtualViewport = () => {
  const el = messagesContainer.value
  if (!el) return
  virtualScrollTop.value = el.scrollTop
  virtualViewportHeight.value = el.clientHeight
}

const firstVisibleMessageIndex = offsets => {
  let index = 0
  while (index < messages.value.length && offsets[index + 1] <= virtualScrollTop.value) index++
  return index
}

const compensateScroll = adjustment => {
  if (!adjustment) return
  pendingScrollAdjustment += adjustment
  if (scrollAdjustmentFrame) return
  scrollAdjustmentFrame = requestAnimationFrame(() => {
    const el = messagesContainer.value
    if (el) el.scrollTop += pendingScrollAdjustment
    pendingScrollAdjustment = 0
    scrollAdjustmentFrame = 0
    updateVirtualViewport()
  })
}

watch(() => messages.value.map(messageKey), ids => {
  const activeIds = new Set(ids)
  for (const id of messageHeights.keys()) {
    if (!activeIds.has(id)) messageHeights.delete(id)
  }
  nextTick(updateVirtualViewport)
})

watch(() => props.sessionName, () => {
  messageHeights.clear()
  virtualScrollTop.value = 0
})

// Only the active streaming message needs to repatch for every server event.
const streamRenderVersion = ref(0)
const activeAssistantMessageIndex = computed(() => {
  if (!streaming.value) return -1
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'assistant') return i
  }
  return -1
})
const branchingSession = ref(false)
const hasHistory = computed(() => messages.value.length > 0)
const planMode = ref(false)

// Usage 相关
const usage = ref({
  promptTokens: 0,
  completionTokens: 0,
  cacheHit: 0,
  cacheMiss: 0,
  maxContextTokens: 128000,
  lastPromptTokens: 0
})
let usageRequestId = 0
const currentModel = ref('')
const defaultModel = ref('')
const defaultModelChannelId = ref('')
const settingDefaultModel = ref(false)
const availableModels = ref([])

// ==================== 工作流 TODO（已迁移至 ChatInput 组件中）====================

const loadUsage = async (override) => {
  const requestId = ++usageRequestId
  try {
    const params = {}
    const wsHash = override?.workspaceHash ?? props.workspaceHash
    const sessName = override?.sessionName ?? props.sessionName
    if (wsHash) params.workspaceHash = wsHash
    if (sessName) params.sessionName = sessName

    const [usageRes, modelsRes, configRes] = await Promise.allSettled([
      wsHash ? configAPI.getUsage(params) : Promise.resolve(null),
      configAPI.getModels(),
      configAPI.getConfig()
    ])
    // 会话切换可能在请求返回前再次发生，过期响应不得覆盖当前会话的用量。
    if (requestId !== usageRequestId) return
    if (usageRes.status === 'fulfilled' && usageRes.value?.success) {
      usage.value = {...usage.value, ...usageRes.value.data}
    }
    const configuredModel = configRes.status === 'fulfilled' && configRes.value.success
      ? configRes.value.data?.model || ''
      : ''
    if (modelsRes.status === 'fulfilled' && modelsRes.value.success) {
      const defaultModel = modelsRes.value.data?.current
        || modelsRes.value.data?.models?.find(model => model.active)?.name
        || configuredModel
        || currentModel.value
      const defaultChannelId = modelsRes.value.data?.currentChannelId
        || modelsRes.value.data?.models?.find(model => model.active)?.channelId
        || ''
      const selection = sessionModelSelections.value[conversationKey()]
        || {model: defaultModel, channelId: defaultChannelId}
      currentModel.value = selection.model
      availableModels.value = (modelsRes.value.data?.models || []).map(model => ({
        ...model,
        active: model.name === selection.model && (model.channelId || '') === selection.channelId
      }))
    }
    if (configRes.status === 'fulfilled' && configRes.value.success) {
      defaultModel.value = configuredModel
      defaultModelChannelId.value = configRes.value.data?.modelChannelId || ''
      if (!currentModel.value) currentModel.value = configuredModel
      currentReasoningEffort.value = sessionReasoningEfforts.value[conversationKey()]
        || configRes.value.data?.reasoningEffort || 'max'
      terminateOnNoToolCall.value = configRes.value.data?.terminateOnNoToolCall !== false
      currentPermission.value = configRes.value.data?.hitl || 'free'
    }
  } catch {
  }
}

// 日志通知列表（逐条堆叠，每条6秒后自动移除）
const currentLogs = ref([])

const addLog = (log) => {
  const id = Date.now() + Math.random()
  currentLogs.value.unshift({...log, id})
  setTimeout(() => {
    currentLogs.value = currentLogs.value.filter(l => l.id !== id)
  }, 6000)
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'})
}

// 监听 workspace 和 session 变化，重新加载 usage
watch([() => props.workspaceHash, () => props.sessionName], ([ws, sess]) => {
  if (ws || sess) {
    loadUsage()
  }
})

onMounted(() => {
  loadUsage()
  window.addEventListener('keydown', handleImagePreviewKeydown)
  window.addEventListener('resize', updateVirtualViewport)
  // 监听复制成功事件
  window.addEventListener('copy-success', (e) => {
    addLog({level: 'INFO', text: '✅ ' + (e.detail || '已复制'), time: Date.now()})
  })
  messageResizeObserver = new ResizeObserver(entries => {
    const offsets = virtualWindow.value.offsets
    const firstVisible = firstVisibleMessageIndex(offsets)
    let adjustment = 0
    for (const entry of entries) {
      const id = entry.target.dataset.virtualMessageId
      const height = Math.ceil(entry.borderBoxSize?.[0]?.blockSize || entry.contentRect.height)
      const index = messages.value.findIndex(message => String(message.id) === id)
      const previousHeight = messageHeights.get(id) || ESTIMATED_MESSAGE_HEIGHT
      if (id && index >= 0 && height > 0 && previousHeight !== height) {
        if (index < firstVisible) adjustment += height - previousHeight
        messageHeights.set(id, height)
      }
    }
    compensateScroll(adjustment)
    nextTick(updateVirtualViewport)
  })
  for (const element of observedMessageElements.values()) {
    messageResizeObserver.observe(element)
  }
  // 监听消息容器滚动 + 初始检查
  const el = messagesContainer.value
  if (el) {
    el.addEventListener('scroll', onScroll)
    requestAnimationFrame(() => {
      updateVirtualViewport()
      onScroll()
    })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleImagePreviewKeydown)
  window.removeEventListener('resize', updateVirtualViewport)
  messageResizeObserver?.disconnect()
  messageResizeObserver = null
  if (scrollAdjustmentFrame) cancelAnimationFrame(scrollAdjustmentFrame)
  scrollAdjustmentFrame = 0
  pendingScrollAdjustment = 0
  observedMessageElements.clear()
  window.removeEventListener('copy-success', () => {
  })
  const el = messagesContainer.value
  if (el) el.removeEventListener('scroll', onScroll)
})

const suggestions = ['解释这段代码', '优化这个函数', '写个单元测试', '检查潜在问题']

// 不在聊天区显示的静默命令（只发给后端，不加用户消息气泡）
const SILENT_CMDS = new Set(['/agree', '/deny', '/exit', '/continue'])

const hasAssistant = computed(() => {
  if (!streaming.value) return false
  // 检查最后一条助手消息是否有内容
  const msgs = messages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'assistant') {
      return msgs[i].blocks?.length > 0
    }
  }
  return false
})

// 是否正在等待 AI 回复（用于显示加载动画）
const waitingForAI = computed(() => {
  return streaming.value && !hasAssistant.value
})

// ── 宠物状态：根据流式事件推断当前阶段 ──
const petState = computed(() => {
  if (!streaming.value) return 'idle'
  // 等待 AI 响应
  if (!hasAssistant.value) return 'waiting'
  // 有流了，取最后一条 assistant 的最后一个 block 判断
  const msgs = messages.value
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'assistant' && msgs[i].blocks?.length) {
      const lastBlock = msgs[i].blocks[msgs[i].blocks.length - 1]
      if (lastBlock.type === 'reasoning') return 'thinking'
      if (lastBlock.type === 'tool_call') return 'tool_call'
      if (lastBlock.type === 'content') return 'content'
    }
  }
  return 'waiting'
})

// ===== 消息缩略图 dock =====
/** 只取 role === 'user' 的消息，并记录全局索引用于跳转 */
const userMessages = computed(() => {
  return messages.value
    .map((m, idx) => ({...m, globalIdx: idx}))
    .filter(m => m.role === 'user')
})

/** 截取文本前 N 个字符 */
const truncateText = (text, max) => {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}

/** 点击缩略图跳转到对应消息 */
const jumpToMessage = (globalIdx) => {
  const el = messagesContainer.value
  const offset = virtualWindow.value.offsets[globalIdx]
  if (el && Number.isFinite(offset)) {
    el.scrollTo({top: offset, behavior: 'smooth'})
  }
}

const now = () => new Date().toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit'})

// 格式化时间戳（Unix 毫秒）为本地时间字符串
const formatTimestamp = (timestamp) => {
  if (!timestamp) return now()
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit'})
}

// 全局函数：代码复制（被 onclick 引用）
window.copyCode = (btn) => {
  const wrap = btn.closest('.code-block-wrap')
  const code = wrap?.querySelector('code')?.textContent || ''
  navigator.clipboard.writeText(code).then(() => {
    window.dispatchEvent(new CustomEvent('copy-success', {detail: '代码已复制'}))
  }).catch(() => {
  })
}

// 使用共享 marked 实例（语法高亮 + 复制按钮已内置）
const fmt = c => {
  if (!c) return ''
  return md.parse(c)
}

const fmtPrompt = c => {
  if (!c) return ''
  return sanitize(md.parse(c))
}

// 复制整条消息内容
const copyMessage = (msg) => {
  let text = ''
  if (msg.role === 'user') {
    text = msg.content || ''
  } else if (msg.role === 'assistant' && msg.blocks) {
    text = msg.blocks
        .filter(b => b.type === 'content' || b.type === 'reasoning')
        .map(b => b.content || '')
        .join('\n\n')
  }
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    window.dispatchEvent(new CustomEvent('copy-success', {detail: '消息已复制'}))
  }).catch(() => {
  })
}

// 分支到新会话：复制当前消息及之前的消息到新会话
const branchSession = async (msg, msgIdx) => {
  if (!props.sessionName || !props.workspaceHash || streaming.value || branchingSession.value) return
  branchingSession.value = true
  try {
    let count = msg.sourceMessageCount
    if (!count) {
      const history = await agentAPI.getHistory(props.workspaceHash, props.sessionName)
      const assistantOrdinal = messages.value.slice(0, msgIdx + 1)
          .filter(item => item.role === 'assistant').length - 1
      count = history.success ? getAssistantTurnBoundaries(history.data || [])[assistantOrdinal] : null
    }
    if (!Number.isInteger(count) || count <= 0) {
      throw new Error('无法确定完整的助手消息边界')
    }
    const r = await sessionsAPI.branchSession(props.sessionName, props.workspaceHash, count)
    if (r.success && r.data?.sessionName) {
      window.dispatchEvent(new CustomEvent('copy-success', {detail: '已分支到新会话'}))
      emit('sessionBranched', r.data.sessionName)
    } else {
      window.dispatchEvent(new CustomEvent('copy-success', {detail: r.error || '分支失败'}))
    }
  } catch (err) {
    window.dispatchEvent(new CustomEvent('copy-success', {detail: '分支失败: ' + (err.message || err)}))
  } finally {
    branchingSession.value = false
  }
}

// 输入框事件已迁移到 ChatInput 组件

const showScrollBtn = ref(false)

// 用户是否主动滚离了底部（区别于被内容推上去）
let userScrolledAway = false

const SCROLL_THRESHOLD = 80

const isNearBottom = () => {
  const el = messagesContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < SCROLL_THRESHOLD
}

const scroll = async (force = false, smooth = false) => {
  await nextTick()
  const el = messagesContainer.value
  if (!el) return
  // 流式渲染中只要用户没主动滚走就一直滚；否则按阈值
  if (force || (streaming.value && !userScrolledAway) || isNearBottom()) {
    el.scrollTo({top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto'})
  }
  updateScrollBtn()
}

const scrollToBottom = () => {
  userScrolledAway = false
  scroll(true, true)
}

// 监听容器的滚动事件，检测用户是否主动滚离底部
const updateScrollBtn = () => {
  const nearBottom = isNearBottom()
  showScrollBtn.value = !nearBottom
  if (!nearBottom && !streaming.value) {
    // 不在流式时，用户滚走就算主动离开
    userScrolledAway = true
  }
}

// 额外监听 wheel / touch 事件：滚动中如果用户向上滚，标记为主动离开
const onScroll = () => {
  const el = messagesContainer.value
  if (!el) return
  updateVirtualViewport()
  const nearBottom = isNearBottom()
  showScrollBtn.value = !nearBottom
  if (!nearBottom) {
    userScrolledAway = true
  } else {
    userScrolledAway = false
  }
}

/** 用户点击选项按钮 → 直接发送 value 作为消息，清理旧工具卡片 */
// HITL 审批 question 格式化：将工具名转为 `tool` 形式的展示文本
const formatHitlQuestion = (title) => {
  return '将执行 ' + title.split('、').map(n => '`' + n + '`').join('、')
}

const sendChoice = async (value, block) => {
  // 子代理 HITL 审批：调用 REST API 而非发送聊天消息
  if (block?.subId != null) {
    // 解析 action：/agree → approve, /deny → deny
    const action = value === '/agree' ? 'approve' : 'deny'
    try {
      await fetch('/api/chat/sub-hitl/' + block.subId, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action })
      })
      // 标记已选择
      block.resolved = true
      block.selectedTitle = action === 'approve' ? '同意执行' : '拒绝执行'
    } catch (e) {
      console.error('子代理 HITL 审批请求失败:', e)
    }
    return
  }
  // 主代理 HITL 审批：发送聊天消息
  // 标记已选择
  if (block) {
    block.resolved = true
    const opt = (block.options || []).find(o => o.value === value)
    block.selectedTitle = opt ? opt.title : value
  }
  // 清理当前消息中已拦截的 tool_call 块（避免与重放执行重复）
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'assistant' && last.blocks) {
    last.blocks = last.blocks.filter(b =>
        b === block || b.type !== 'tool_call'
    )
  }
  inputText.value = value
  sendMessage()
}

// 打开文件（显示当前工作区代码预览）
const openFile = async (filePath) => {
  await openDiff(filePath)
}

// 键盘事件已迁移到 ChatInput 组件

/**
 * 核心发送逻辑：
 * - 普通消息：加用户气泡 → 创建助手占位 → 流式填充
 * - 静默命令（SILENT_CMDS 中的命令，如 /new、/agree、/deny 等）：不加气泡直接发后端；
 *   收到有内容的 SSE 事件时才创建助手气泡
 * - /skill: 命令：显示用户气泡 + 助手气泡（正常流程）
 */
const mergeFileChanges = (blocks, changes) => {
  if (!Array.isArray(changes) || changes.length === 0) return
  let summary = blocks.find(block => block.type === 'file_changes')
  if (!summary) {
    summary = {type: 'file_changes', changes: []}
    blocks.push(summary)
  }
  const byPath = new Map(summary.changes.map(change => [change.path, {...change}]))
  for (const change of changes) {
    if (!change?.path) continue
    const existing = byPath.get(change.path)
    byPath.set(change.path, existing ? {
      ...existing,
      additions: Number(existing.additions || 0) + Number(change.additions || 0),
      deletions: Number(existing.deletions || 0) + Number(change.deletions || 0),
      created: Boolean(existing.created || change.created),
      diff: [existing.diff, change.diff].filter(Boolean).join('\n')
    } : {...change})
  }
  summary.changes = [...byPath.values()]
}

const moveFileChangesToEnd = (blocks) => {
  const changes = blocks.filter(block => block.type === 'file_changes')
  if (changes.length === 0) return
  const summary = changes[0]
  const rest = blocks.filter(block => block.type !== 'file_changes')
  blocks.splice(0, blocks.length, ...rest, summary)
}

const sendMessage = async (images = [], overrideText = null, modelSelection = null,
                            targetSessionName = props.sessionName, targetWorkspaceHash = props.workspaceHash,
                            reasoningEffort = getSessionReasoningEffort(targetSessionName, targetWorkspaceHash)) => {
  const text = overrideText || inputText.value.trim()
  if (!text && images.length === 0) return
  const sessionName = targetSessionName
  if (!sessionName) return
  const selectedModel = modelSelection || getSessionModelSelection(sessionName, targetWorkspaceHash)
  const selectedReasoningEffort = reasoningEffort || getSessionReasoningEffort(sessionName, targetWorkspaceHash)
  if (store.getSessionStreaming(sessionName)) {
    addQueuedMessage(sessionName, targetWorkspaceHash, images, text, selectedModel, selectedReasoningEffort)
    inputText.value = ''
    return
  }

  const firstWord = text.split(/\s+/)[0].toLowerCase()
  // 静默命令不显示用户气泡（系统命令、模式切换、HITL 审批等）
  const isSilent = SILENT_CMDS.has(firstWord)


  // 静默命令不显示用户气泡
  if (!isSilent) {
    const userMsg = {id: Date.now(), role: 'user', content: text, time: now(), snapshotId: null, rollbackId: null}
    if (images.length > 0) userMsg.images = images
    store.addSessionMessage(sessionName, userMsg)
    // Empty sessions are intentionally hidden. Show the session as soon as it has a user message.
    emit('sessionUpdated', sessionName, true)
  }
  userScrolledAway = false
  inputText.value = ''
  await scroll(true)  // 用户刚发送，强制滚到底

  store.setSessionStreaming(sessionName, true)

  // 使用唯一 ID 追踪当前 assistant 消息
  const assistantId = Date.now() + 1
  let silentAssistantId = null

  // 静默命令不预创建助手占位
  if (!isSilent) {
    store.addSessionMessage(sessionName, {id: assistantId, role: 'assistant', time: now(), blocks: []})
  }

  let getMsg = () => {
    const msgs = store.getSessionMessages(sessionName)
    const targetId = silentAssistantId || assistantId
    if (!targetId) return null
    return msgs.find(m => m.id === targetId)
  }
  let silentBubbleCreated = false

  try {
    const processStreamEvent = (data) => {
          // 静默命令：首次收到有内容的数据时才创建助手气泡（只创建一次）
          if (isSilent && !silentBubbleCreated) {
            if (!data.type || data.type === 'done') return
            const hasContent = (data.type === 'content' && data.content?.trim()) ||
                (data.type === 'reasoning' && data.content?.trim()) ||
                data.type === 'tool_call' || data.type === 'tool_result' || data.type === 'file_changes' || data.type === 'error'
            if (!hasContent) return
            // 有实际内容了，插入助手气泡
            silentAssistantId = Date.now()
            store.addSessionMessage(sessionName, {id: silentAssistantId, role: 'assistant', time: now(), blocks: []})
            silentBubbleCreated = true
          }

          const msg = getMsg()
          if (!msg) return

          // 按 subId 查找或创建子代理容器块（用于并行子代理事件路由）
          const findSubAgentBlock = (subId) => {
            for (let i = msg.blocks.length - 1; i >= 0; i--) {
              if (msg.blocks[i].type === 'sub_agent' && msg.blocks[i].subId === subId) {
                return msg.blocks[i]
              }
            }
            const container = { type: 'sub_agent', subId, blocks: [], status: '运行中', taskName: '子代理', expanded: true }
            msg.blocks.push(container)
            return container
          }

          // ===== 子代理事件：注入 sub_agent 容器块，内部渲染 =====
          if (data.type === 'sub_content' || data.type === 'sub_reasoning' ||
              data.type === 'sub_tool_call' || data.type === 'sub_error') {
            const container = findSubAgentBlock(data.subId)
            // 向容器内添加内容
            if (data.type === 'sub_content') {
              const lb = container.blocks[container.blocks.length - 1]
              const content = data.token || data.content || ''
              if (lb?.type === 'content') lb.content += content
              else container.blocks.push({type: 'content', content: content})
            } else if (data.type === 'sub_reasoning') {
              const lb = container.blocks[container.blocks.length - 1]
              const reasoningContent = data.token || data.content || ''
              if (lb?.type === 'reasoning') lb.content += reasoningContent
              else container.blocks.push({type: 'reasoning', content: reasoningContent, showContent: false})
            } else if (data.type === 'sub_tool_call') {
              let name = data.name || '', args = data.args || data.arguments || ''
              if (typeof args === 'string') try {
                args = JSON.parse(args)
              } catch {
              }
              container.blocks.push({
                type: 'tool_call',
                name: name || 'unknown',
                status: '执行中',
                args,
                result: '',
                expanded: true
              })
            } else if (data.type === 'sub_error') {
              const errText = data.error || data.content || '未知错误'
              container.blocks.push({type: 'content', content: '❌ ' + errText})
            }
          } else if (data.type === 'sub_tool_result') {
            const c = findSubAgentBlock(data.subId)
            let result = data.result || data.content || ''
            const rn = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
            let targetName = data.name || ''
            let matched = false
            if (targetName) {
              for (let j = c.blocks.length - 1; j >= 0; j--) {
                if (c.blocks[j].type === 'tool_call' && c.blocks[j].name === targetName && !c.blocks[j].result) {
                  c.blocks[j].result = rn; c.blocks[j].status = '成功'; c.blocks[j].expanded = false
                  matched = true; break
                }
              }
            }
            if (!matched) {
              for (let j = c.blocks.length - 1; j >= 0; j--) {
                if (c.blocks[j].type === 'tool_call' && !c.blocks[j].result) {
                  c.blocks[j].result = rn; c.blocks[j].status = '成功'; c.blocks[j].expanded = false
                  break
                }
              }
            }
          } else if (data.type === 'sub_complete') {
            const c = findSubAgentBlock(data.subId)
            c.status = '已完成'
            c.taskName = data?.task || c.taskName || '子代理'
            c.expanded = false
          } else if (data.type === 'sub_choice') {
            // 子代理 HITL 审批 → 作为顶级 choice 块渲染在主消息流中
            let options = data.options || []
            if (typeof options === 'string') {
              try { options = JSON.parse(options) } catch {}
            }
            const title = data.title || ''
            const desc = data.description || ''
            const question = title
                ? '子代理 ' + formatHitlQuestion(title)
                : '子代理工具调用需要审批'
            msg.blocks.push({
              type: 'choice',
              subId: data.subId,
              options: options,
              question,
              description: desc,
              resolved: false
            })
          } else if (data.type === 'sub_usage' || data.type === 'sub_log') {
            // 暂不处理
            // ===== 普通主代理事件 =====
          } else if (data.type === 'reasoning') {
            const lb = msg.blocks[msg.blocks.length - 1]
            if (lb?.type === 'reasoning') lb.content += (data.content || '')
            else msg.blocks.push({type: 'reasoning', content: data.content || '', showContent: false})
          } else if (data.type === 'content') {
            const lb = msg.blocks[msg.blocks.length - 1]
            if (lb?.type === 'content') lb.content += (data.content || '')
            else msg.blocks.push({type: 'content', content: data.content || ''})
          } else if (data.type === 'tool_call') {
            let name = data.name || '', args = data.args || data.arguments || ''
            if (typeof args === 'string') try {
              args = JSON.parse(args)
            } catch {
            }
            msg.blocks.push({
              type: 'tool_call',
              name: name || 'unknown',
              status: '执行中',
              args,
              result: '',
              expanded: true
            })
          } else if (data.type === 'tool_result') {
            let result = data.result || data.content || ''
            const rn = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
            let targetName = data.name || ''
            // 优先按 name 匹配（异步执行时完成顺序与调用顺序可能不同）
            let matched = false
            if (targetName) {
              for (let i = msg.blocks.length - 1; i >= 0; i--) {
                if (msg.blocks[i].type === 'tool_call' && msg.blocks[i].name === targetName && !msg.blocks[i].result) {
                  msg.blocks[i].result = rn;
                  msg.blocks[i].status = '成功';
                  msg.blocks[i].expanded = false;
                  matched = true
                  break
                }
              }
            }
            // 按 name 没匹配到，fallback 到从后往前找第一个无结果的
            if (!matched) {
              for (let i = msg.blocks.length - 1; i >= 0; i--) {
                if (msg.blocks[i].type === 'tool_call' && !msg.blocks[i].result) {
                  msg.blocks[i].result = rn;
                  msg.blocks[i].status = '成功';
                  msg.blocks[i].expanded = false;
                  break
                }
              }
            }
          } else if (data.type === 'file_changes') {
            const changes = Array.isArray(data.changes) ? data.changes : []
            mergeFileChanges(msg.blocks, changes)
            moveFileChangesToEnd(msg.blocks)
          } else if (data.type === 'error') {
            msg.blocks.push({type: 'content', content: '错误: ' + (data.error || data.content || '未知')})
          } else if (data.type === 'usage') {
            // 更新 usage 数据
            if (data.promptTokens !== undefined) {
              usage.value = {...usage.value, ...data}
            }
          } else if (data.type === 'choice') {
            // 选项按钮（如 HITL 审批、ask_choice）
            let options = data.options || []
            if (typeof options === 'string') {
              try {
                options = JSON.parse(options)
              } catch {
              }
            }
            if (Array.isArray(options) && options.length > 0) {
              // HITL 审批：使用后端传入的 title/description
              let question = data.title
                ? formatHitlQuestion(data.title)
                : (data.question || '')
              const description = data.description || ''
              let toolOptions = null
              // 尝试从同消息的 ask_choice tool_call 中获取 question 和 summary（向后兼容）
              if (!question && msg.blocks) {
                for (let i = msg.blocks.length - 1; i >= 0; i--) {
                  const b = msg.blocks[i]
                  if (b.type === 'tool_call' && b.name === 'ask_choice') {
                    try {
                      const args = typeof b.args === 'string' ? JSON.parse(b.args) : b.args
                      if (args?.question && !question) question = args.question
                      if (args?.options) toolOptions = args.options
                    } catch {}
                  }
                }
              }
              // 如果后端 choice 事件没带 summary，从 tool_call args 补上
              if (toolOptions && Array.isArray(toolOptions)) {
                options = options.map((opt, idx) => {
                  const toolOpt = toolOptions[idx]
                  if (toolOpt && !opt.summary && toolOpt.summary) {
                    return { ...opt, summary: toolOpt.summary }
                  }
                  return opt
                })
              }
              msg.blocks.push({type: 'choice', options, question, description})
            }
          } else if (data.type === 'log') {
            // 系统日志（如 [compact] 折叠结果）→ 仅展示 INFO 及以上级别
            const level = (data.level || 'INFO').toUpperCase()
            if (level === 'DEBUG') return
            const text = data.message || data.content || ''
            addLog({level, text, time: Date.now()})
          } else if (data.type === 'snapshot') {
            // 每条用户消息都会收到撤回定位 ID；只有实际创建快照时才可撤回代码。
            if (data.msgId) {
              if (data.hasCodeSnapshot) {
                store.addSnapshot(sessionName, data.msgId, data.msgId)
                snapshotMap.value.set(data.msgId, data.msgId)
              }
              // 将撤回定位 ID 关联到最后一条尚未关联的用户消息
              const msgs = store.getSessionMessages(sessionName)
              for (let i = msgs.length - 1; i >= 0; i--) {
                if (msgs[i].role === 'user' && !msgs[i].rollbackId) {
                  msgs[i].rollbackId = data.msgId
                  if (data.hasCodeSnapshot) msgs[i].snapshotId = data.msgId
                  break
                }
              }
            }
          }
    }

    const pendingStreamEvents = []
    let streamFrameId = 0
    const flushStreamEvents = () => {
      if (streamFrameId) {
        cancelAnimationFrame(streamFrameId)
        streamFrameId = 0
      }
      if (pendingStreamEvents.length === 0) return

      const events = pendingStreamEvents.splice(0)
      for (const event of events) processStreamEvent(event)
      streamRenderVersion.value++
      scroll()
    }
    const enqueueStreamEvent = (data) => {
      pendingStreamEvents.push(data)
      if (streamFrameId) return
      streamFrameId = requestAnimationFrame(() => {
        streamFrameId = 0
        flushStreamEvents()
      })
    }

    const streamResult = chatAPI.sendMessageStream(text,
        enqueueStreamEvent,
        () => {
          flushStreamEvents()
          store.setSessionStreaming(sessionName, false)
          // 流结束后清理空的助手气泡
          const msgs = store.getSessionMessages(sessionName)
          const last = msgs[msgs.length - 1]
          if (last?.role === 'assistant' && (!last.blocks || last.blocks.length === 0)) {
            store.setSessionMessages(sessionName, msgs.slice(0, -1))
          }
          // 刷新 usage 数据
          loadUsage()
          // 通知父组件刷新会话列表（标题可能已更新）
          emit('sessionUpdated')
          nextTick(() => sendNextQueuedMessage(sessionName, targetWorkspaceHash))
        },
        () => {
          flushStreamEvents()
          store.setSessionStreaming(sessionName, false)
          const msg = getMsg()
          if (msg && !msg.blocks.length) msg.blocks.push({type: 'content', content: '连接错误'})
          emit('sessionUpdated')
          nextTick(() => sendNextQueuedMessage(sessionName, targetWorkspaceHash))
        },
        // 传递工作区、会话和图片信息
        {
          workspaceHash: targetWorkspaceHash,
          sessionName,
          images,
          model: selectedModel.model,
          modelChannelId: selectedModel.channelId,
          reasoningEffort: selectedReasoningEffort
        }
    )
    store.setSessionController(sessionName, streamResult)
  } catch {
    store.setSessionStreaming(sessionName, false)
    emit('sessionUpdated')
    nextTick(() => sendNextQueuedMessage(sessionName, targetWorkspaceHash))
  }
  await scroll()
}

const abortChat = async (targetSessionName = props.sessionName, targetWorkspaceHash = props.workspaceHash) => {
  const ctrl = store.getSessionController(targetSessionName)
  try {
    // 保持 SSE 读取，等待服务端取消 Agent 并主动关闭 emitter。
    await chatAPI.abort({
      workspaceHash: targetWorkspaceHash,
      sessionName: targetSessionName,
      requestId: ctrl?.requestId
    })
  } catch {
  }
}

const openRollbackDialog = (msgId, canRollbackCode, rollbackTimestamp) => {
  const rollbackKey = msgId || rollbackTimestamp
  if (streaming.value || !rollbackKey || snapshotRollbackLoading.value.get(rollbackKey)) return
  rollbackDialog.value = {visible: true, msgId, rollbackTimestamp, canRollbackCode}
}

const openFileRevertDialog = (changes) => {
  if (streaming.value || !Array.isArray(changes) || changes.length === 0) return
  fileRevertDialog.value = {visible: true, pending: false, changes}
}

const closeFileRevertDialog = () => {
  if (fileRevertDialog.value.pending) return
  fileRevertDialog.value = {visible: false, pending: false, changes: []}
}

const handleFileRevertAction = async (action) => {
  if (action === 'cancel') {
    closeFileRevertDialog()
    return
  }
  const changes = fileRevertDialog.value.changes
  if (!changes.length || fileRevertDialog.value.pending) return
  fileRevertDialog.value.pending = true
  try {
    const res = await sessionsAPI.revertFileChanges(props.workspaceHash, changes)
    if (res.success) {
      addLog({level: 'INFO', text: `✅ ${res.data?.message || '已撤销本次 AI 的文件修改'}`, time: Date.now()})
      fileRevertDialog.value = {visible: false, pending: false, changes: []}
      await refreshHistory()
      emit('sessionUpdated')
    }
  } catch {
    // The API interceptor already displays the failure notification.
  } finally {
    if (fileRevertDialog.value.visible) fileRevertDialog.value.pending = false
  }
}

const closeRollbackDialog = () => {
  rollbackDialog.value = {visible: false, msgId: null, rollbackTimestamp: null, canRollbackCode: false}
}

const confirmRollback = (rollbackCode) => {
  const msgId = rollbackDialog.value.msgId
  const rollbackTimestamp = rollbackDialog.value.rollbackTimestamp
  closeRollbackDialog()
  rollbackSnapshot(msgId, rollbackCode, rollbackTimestamp)
}

const handleRollbackAction = (action) => {
  if (action === 'cancel') {
    closeRollbackDialog()
    return
  }
  confirmRollback(action === 'code')
}

/** 撤回会话消息，并按选择决定是否恢复 AI 修改的代码。 */
const rollbackSnapshot = async (msgId, rollbackCode, rollbackTimestamp) => {
  const loadingKey = msgId || rollbackTimestamp
  if (streaming.value || !loadingKey) return
  if (snapshotRollbackLoading.value.get(loadingKey)) return // 防止重复点击
  snapshotRollbackLoading.value.set(loadingKey, true)

  try {
    const res = await snapshotAPI.rollback(props.workspaceHash, msgId, props.sessionName, rollbackCode, rollbackTimestamp)
    if (res.success) {
      addLog({level: 'INFO', text: `✅ ${res.data?.message || '工作区已恢复'}`, time: Date.now()})
      // 截断该消息之后的所有快照记录
      store.truncateSnapshotsAfter(props.sessionName, msgId)
      // 从 snapshotMap 中移除
      snapshotMap.value.delete(msgId)

      // 找到对应用户消息及其位置，截断会话消息，回填输入框
      const msgs = store.getSessionMessages(props.sessionName)
      let targetIdx = -1
      // 优先使用后端返回的 rollbackUserText（从 JSONL 持久化数据中取得）
      let rollbackContent = res.data?.rollbackUserText || ''
      let rollbackImages = []
      for (let i = 0; i < msgs.length; i++) {
        if ((msgId && (msgs[i].rollbackId === msgId || msgs[i].snapshotId === msgId))
            || (!msgId && msgs[i].rollbackTimestamp === rollbackTimestamp)) {
          targetIdx = i
          // 如果后端没返回文本，从前端消息中取
          if (!rollbackContent) rollbackContent = msgs[i].content || ''
          rollbackImages = msgs[i].images || []
          break
        }
      }
      if (targetIdx >= 0) {
        // 截断：保留目标消息之前的所有消息，删除目标消息及之后的所有消息
        const kept = msgs.slice(0, targetIdx)
        store.setSessionMessages(props.sessionName, kept)
        const useWelcomeInput = kept.length === 0
        if (useWelcomeInput) {
          welcomeText.value = rollbackContent
        } else {
          inputText.value = rollbackContent
        }
        await nextTick()
        const targetInput = useWelcomeInput ? welcomeInput.value : chatInput.value
        targetInput?.restoreImages?.(rollbackImages)
        targetInput?.focus?.()
      }

      // 通知父组件刷新会话列表和 Git 状态
      emit('sessionUpdated')
    } else {
      addLog({level: 'ERROR', text: `❌ 撤回失败: ${res.error || '未知错误'}`, time: Date.now()})
    }
  } catch (e) {
    addLog({level: 'ERROR', text: `❌ 撤回失败: ${e.message || e}`, time: Date.now()})
  } finally {
    snapshotRollbackLoading.value.delete(loadingKey)
  }
}

const clearChat = async () => {
  store.clearSessionMessages(props.sessionName)
  // 发送 /new 给后端清空会话
  store.setSessionStreaming(props.sessionName, true)
  try {
    chatAPI.sendMessageStream('/new', () => {
    }, () => {
      store.setSessionStreaming(props.sessionName, false);
      loadUsage()
    }, () => {
      store.setSessionStreaming(props.sessionName, false)
    })
  } catch {
    store.setSessionStreaming(props.sessionName, false)
  }
}

// 暴露给父组件的清空方法（/new 属于 SILENT_CMDS，不显示气泡）
const clearMessages = () => {
  store.clearSessionMessages(props.sessionName)
  store.setSessionStreaming(props.sessionName, true)
  try {
    chatAPI.sendMessageStream('/new', () => {
    }, () => {
      store.setSessionStreaming(props.sessionName, false);
      loadUsage()
    }, () => {
      store.setSessionStreaming(props.sessionName, false)
    })
  } catch {
    store.setSessionStreaming(props.sessionName, false)
  }
}

/** 继续生成：发送 /continue 命令让 AI 继续推理，复用以有的 SSE 流式逻辑 */
const continueChat = async () => {
  if (!props.sessionName || streaming.value) return
  inputText.value = '/continue'
  nextTick(() => sendMessage())
}

// 仅清空本地消息，不请求后端（配合 REST API 创建新会话时使用）
const resetLocalMessages = () => {
  store.clearSessionMessages(props.sessionName)
}

const exportChat = () => {
  const text = messages.value.map(m => {
    const h = `[${m.time}] ${m.role === 'user' ? '用户' : '助手'}:`
    let c = h + '\n'
    if (m.blocks) for (const b of m.blocks) {
      if (b.type === 'reasoning') c += '\n思考: ' + b.content + '\n'
      else if (b.type === 'content') c += b.content + '\n'
      else if (b.type === 'tool_call') c += `工具 ${b.name}: ${JSON.stringify(b.args)}\n`
    }
    return c
  }).join('\n---\n\n')
  const blob = new Blob([text], {type: 'text/plain'})
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `chat-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
}

// 查看系统提示词
const loadingPrompt = ref(false)
const promptModalOpen = ref(false)
const promptContent = ref('')
const promptLength = ref(0)

const copyPrompt = () => {
  if (!promptContent.value) return
  navigator.clipboard.writeText(promptContent.value).then(() => {
    addLog({level: 'INFO', text: '✅ 提示词已复制', time: Date.now()})
  }).catch(() => {
  })
}

const viewSystemPrompt = async () => {
  loadingPrompt.value = true
  try {
    const params = {}
    if (props.workspaceHash) params.workspaceHash = props.workspaceHash
    if (props.sessionName) params.sessionName = props.sessionName
    const res = await agentAPI.getSystemPrompt(params)
    if (res.success && res.data) {
      promptContent.value = res.data.content || ''
      promptLength.value = res.data.length || 0
      promptModalOpen.value = true
    } else {
      addLog({level: 'ERROR', text: '获取提示词失败', time: Date.now()})
    }
  } catch (e) {
    addLog({level: 'ERROR', text: '获取提示词失败: ' + (e.message || '未知错误'), time: Date.now()})
  } finally {
    loadingPrompt.value = false
  }
}

const togglePlan = async () => {
  planMode.value = !planMode.value
  inputText.value = planMode.value ? '/plan' : '/execute'
  await sendMessage()
}

const loadHistory = async (sessionName, force = false) => {
  const targetSession = sessionName || props.sessionName
  if (!targetSession) return
  
  // 如果 force=true 强制从后端刷新，跳过缓存
  const existing = store.getSessionMessages(targetSession)
  if (!force && existing.length > 0) {
    if (targetSession === props.sessionName) await scroll(true)
    return
  }
  
  try {
    const r = await agentAPI.getHistory(props.workspaceHash, targetSession)
    if (r.success && r.data) {
      const raw = r.data, tr = {}
      const assistantBoundaries = getAssistantTurnBoundaries(raw)
      let assistantTurn = 0
      for (const m of raw) if (m.role === 'tool' && m.tool_call_id) tr[m.tool_call_id] = m.content || ''
      const merged = []
      let lastAssistantItem = null
      let idCounter = 0
      for (const m of raw) {
        if (m.role === 'tool') continue
        if (m.role === 'user') {
          // 用户消息：创建新item
          const item = {id: Date.now() + idCounter++, role: 'user', time: formatTimestamp(m.timestamp), blocks: []}
          // 多模态消息：contentParts 为 [{type:'text',...},{type:'image_url',...}] 数组
          const parts = m.contentParts || (Array.isArray(m.content) ? m.content : null)
          if (parts && parts.length > 0) {
            const texts = []
            const imgs = []
            for (const part of parts) {
              if (part.type === 'text' && part.text) texts.push(part.text)
              if (part.type === 'image_url') {
                const url = part.image_url?.url || part.imageUrl?.url
                if (url) imgs.push(url)
              }
            }
            item.content = texts.join('\n')
            if (imgs.length > 0) item.images = imgs
          } else {
            item.content = m.content || ''
          }
          // 恢复快照检查点 ID（JSONL 持久化的 snapshot_id 字段）
          if (m.snapshot_id) {
            item.snapshotId = m.snapshot_id
          }
          // rollback_id 独立于代码快照；旧会话则以 snapshot_id 兼容。
          item.rollbackId = m.rollback_id || m.snapshot_id || null
          item.rollbackTimestamp = m.timestamp || null
          merged.push(item)
          lastAssistantItem = null // 重置
        } else {
          // assistant消息：合并连续的assistant消息
          if (!lastAssistantItem) {
            // 创建新的assistant item
            lastAssistantItem = {id: Date.now() + idCounter++, role: 'assistant', time: formatTimestamp(m.timestamp), blocks: [], sourceMessageCount: assistantBoundaries[assistantTurn++]}
            merged.push(lastAssistantItem)
          } else {
            // 更新时间戳为最新的
            lastAssistantItem.time = formatTimestamp(m.timestamp)
          }
          if (m.reasoning_content) lastAssistantItem.blocks.push({
            type: 'reasoning',
            content: m.reasoning_content,
            showContent: false
          })
          if (m.tool_calls) for (const tc of m.tool_calls) {
            let name = tc.function?.name || tc.name || '', args = tc.function?.arguments || tc.arguments || ''
            if (typeof args === 'string') try {
              args = JSON.parse(args)
            } catch {
            }
            lastAssistantItem.blocks.push({
              type: 'tool_call',
              name,
              status: tr[tc.id] ? '成功' : '执行中',
              args,
              result: tr[tc.id] || '',
              expanded: !tr[tc.id]
            })
          }
          if (m.content) lastAssistantItem.blocks.push({type: 'content', content: m.content})
          const fileChanges = m.file_changes || m.fileChanges
          if (Array.isArray(fileChanges) && fileChanges.length > 0) {
            mergeFileChanges(lastAssistantItem.blocks, fileChanges)
          }
        }
      }
      for (const item of merged) {
        if (item.role === 'assistant') moveFileChangesToEnd(item.blocks)
      }
      store.setSessionMessages(targetSession, merged)
      if (targetSession === props.sessionName) await scroll(true)
    }
  } catch {
  }
}

// 强制从后端刷新指定会话的历史（跳过缓存）
// 刷新当前展示的会话后跳至底部，后台会话不影响当前视图。
const refreshHistory = async (name) => {
  const target = name || props.sessionName
  if (!target) return
  try {
    await loadHistory(target, true)
    if (target === props.sessionName) {
      userScrolledAway = false
      await scroll(true)
    }
    addLog({level: 'INFO', text: '聊天记录已刷新', time: Date.now()})
  } catch (e) {
    addLog({level: 'ERROR', text: '刷新失败: ' + (e.message || '未知错误'), time: Date.now()})
  }
}

const loadSession = async (name, workspaceHash) => {
  try {
    const {sessionsAPI} = await import('../services/api')
    await sessionsAPI.switchSession(name, workspaceHash)
    const existing = store.getSessionMessages(name)
    if (existing.length === 0) {
      await loadHistory(name)
    } else {
      // 缓存命中，直接滚动到底部
      await scroll(true)
    }
    await loadUsage({sessionName: name, workspaceHash})
  } catch (e) {
    console.error('切换会话失败:', e)
  }
}

const sendCommand = async cmd => {
  inputText.value = cmd;
  await sendMessage()
}

const startWelcomePrompt = async (prompt) => {
  inputText.value = prompt || ''
  await nextTick()
  await sendMessage()
}

const appendFileSelection = async ({ file }) => {
  const path = String(file || '').trim()
  if (!path) return false
  const useSessionInput = Boolean(props.sessionName && messages.value.length > 0)
  const targetInput = useSessionInput ? chatInput.value : welcomeInput.value
  if (!targetInput?.addFileContext?.({ file: path })) return false
  await nextTick()
  targetInput.focus?.()
  return true
}

const appendElementInspection = async (inspection) => {
  const useSessionInput = Boolean(props.sessionName && messages.value.length > 0)
  const targetInput = useSessionInput ? chatInput.value : welcomeInput.value
  if (!targetInput?.addElementContext?.(inspection)) return false
  await nextTick()
  targetInput.focus?.()
  return true
}

// 加载历史消息（仅在明确选了 session 时）
onMounted(() => {
  document.addEventListener('click', handleWelcomeOutsideClick)
  if (props.sessionName) loadHistory()
})

onBeforeUnmount(() => document.removeEventListener('click', handleWelcomeOutsideClick))

const setDraft = async (text) => {
  const draft = text || ''
  if (!props.sessionName || messages.value.length === 0) {
    welcomeText.value = draft
    await nextTick()
    welcomeInput.value?.focus?.()
    return
  }
  inputText.value = draft
  await nextTick()
  chatInput.value?.focus?.()
}

defineExpose({clearMessages, resetLocalMessages, loadSession, sendCommand, startWelcomePrompt, appendFileSelection, appendElementInspection, exportChat, refreshHistory, setDraft})
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
  position: relative;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border);
}

.chat-head-title {
  font-size: 14px;
  font-weight: 600;
}

.chat-head-count {
  font-size: 12px;
  color: var(--fg-4);
}

/* 流式加载动画横线 */
.streaming-bar {
  height: 2px;
  background: var(--bg-3, rgba(0,0,0,0.06));
  overflow: hidden;
  position: relative;
}

.streaming-bar-inner {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 40%;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  border-radius: 1px;
  animation: streaming-slide 1.4s ease-in-out infinite;
}

@keyframes streaming-slide {
  0% { left: -40%; }
  100% { left: 100%; }
}

/* 消息区 */
.messages {
  flex: 1;
  overflow-y: auto;
  overflow-anchor: none;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
  padding: 16px 72px 146px;
  position: relative;
}

.messages::-webkit-scrollbar {
  width: 3px;
}

.messages::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 3px;
}

.messages:hover {
  scrollbar-color: var(--fg-4) transparent;
}

.messages:hover::-webkit-scrollbar-thumb {
  background: var(--fg-4);
}

.virtual-message-item {
  display: flow-root;
  padding-bottom: 8px;
}

.virtual-message-item.message-role-transition {
  padding-top: 12px;
}

.virtual-message-item > :deep(.msg) {
  margin: 0;
}


/* 空状态 */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--fg-3);
  text-align: center;
}

.empty-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 2px dashed var(--border);
  border-radius: 50%;
  margin-bottom: 12px;
  color: var(--fg-4);
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 4px;
}

.empty-desc {
  font-size: 13px;
  color: var(--fg-3);
  margin-bottom: 16px;
}

.empty-suggestions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

.suggestion {
  padding: 4px 10px;
  font-size: 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  color: var(--fg-2);
  transition: all var(--t);
}

.suggestion:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.quick-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.quick-action {
  background: var(--bg);
  border: 1px solid var(--border);
  padding: 9px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--fg-2);
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  transition: all 0.15s;
  font-weight: 500;
}

.quick-action:hover {
  border-color: var(--text, var(--fg));
  color: var(--text, var(--fg));
}

.quick-action i {
  font-size: 12px;
  color: var(--fg-4);
}

.quick-action:hover i {
  color: var(--text, var(--fg));
}

/* AI 准备中动画 */
.ai-preparing {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
}

/* 助手消息气泡样式（Chat.vue 中的加载状态需要） */
.msg.assistant {
  display: flex;
  justify-content: flex-start;
}

.msg.assistant .msg-body {
  max-width: 78%;
}

.assistant-body {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 8px 12px;
  box-shadow: var(--glass-shadow);
}

.ai-dot {
  width: 8px;
  height: 8px;
  background: var(--accent);
  border-radius: 50%;
  opacity: 0.4;
  animation: ai-pulse 1.4s ease-in-out infinite;
}

.ai-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.ai-dot:nth-child(3) {
  animation-delay: 0.4s;
}

.ai-label {
  font-size: 13px;
  color: var(--fg-3);
  margin-left: 4px;
}

@keyframes ai-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

/* 全部输入区样式已迁移到 ChatInput.vue 组件中（.input-area, .input-box, .usage-bar, .todo-*, .slash-popup, .model-selector 等） */

/* 滚动到底部按钮（固定在消息区右下角，不随内容滚动） */
.scroll-bottom-btn {
  position: absolute;
  right: 24px;
  bottom: 110px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  border: 2px solid var(--bg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.scroll-bottom-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.scroll-bottom-btn svg {
  animation: bounce-down 1.5s ease-in-out infinite;
}

@keyframes bounce-down {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

/* 使用 v-show 控制显隐 */

/* ===== 日志堆叠容器 ===== */
.log-stack {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  z-index: 100;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* 确保在消息滚动时仍固定在顶部 */
  overflow: visible;
}

.log-stack > * {
  pointer-events: auto;
}

/* 🎯 灵动岛风格日志通知 — 大气居中，超长省略 */
.log-bar {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  margin: 0 auto;

  background: rgba(30, 30, 40, 0.78);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.4;
  color: #f0f0f0;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
}

.log-bar:hover {
  background: rgba(40, 40, 55, 0.92);
  border-radius: 10px;
  padding: 8px 20px;
}

.log-bar.log-warn {
  border-color: rgba(245, 158, 11, 0.5);
}

.log-bar.log-error {
  border-color: rgba(239, 68, 68, 0.5);
}

.log-bar-icon {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1;
}

.log-bar-text {
  min-width: 0;
  max-width: 50ch;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
  transition: max-width 0.3s ease;
}

.log-bar:hover .log-bar-text {
  max-width: 100ch;
}

.log-bar-time {
  flex-shrink: 0;
  font-size: 10px;
  opacity: 0.4;
  font-family: var(--mono);
}

/* 进出动画：从顶部滑入 + 淡入 */
.log-bar-enter-active {
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.log-bar-leave-active {
  transition: all 0.2s ease;
}

.log-bar-enter-from {
  opacity: 0;
  transform: translateY(-16px) scale(0.92);
}

.log-bar-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}


/* ===== 系统提示词弹窗 ===== */
.prompt-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.prompt-modal {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  width: 80vw;
  max-width: 900px;
  height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--glass-shadow);
}

.prompt-modal-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.prompt-modal-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.prompt-modal-size {
  font-size: 11px;
  color: var(--fg-4);
  background: var(--bg-3);
  padding: 2px 8px;
  border-radius: var(--r-sm);
}

.prompt-modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: var(--fg-3);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.prompt-modal-close:hover {
  color: var(--fg);
}

.prompt-modal-body {
  flex: 1;
  overflow: auto;
  padding: 20px 24px;
}

.prompt-modal-content {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  overflow-x: hidden;
}

.prompt-modal-content :deep(pre) {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}

.prompt-modal-content :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
}

.prompt-modal-content :deep(pre code) {
  background: none;
  padding: 0;
}

.prompt-modal-content :deep(strong) {
  font-weight: 600;
}

.prompt-modal-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.prompt-modal-content :deep(h1),
.prompt-modal-content :deep(h2),
.prompt-modal-content :deep(h3),
.prompt-modal-content :deep(h4) {
  margin: 0.5em 0;
  font-weight: 600;
}

.prompt-modal-content :deep(ul),
.prompt-modal-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.prompt-modal-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}

.prompt-modal-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}

.prompt-modal-content :deep(th),
.prompt-modal-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}

.prompt-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

/* 图片预览 */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 40px;
  background: rgba(0, 0, 0, 0.72);
}

.image-preview-full {
  display: block;
  max-width: min(92vw, 1440px);
  max-height: calc(100vh - 80px);
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}

.image-preview-close {
  position: fixed;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  cursor: pointer;
}

.image-preview-close:hover,
.image-preview-close:focus-visible {
  border-color: #fff;
  background: rgba(0, 0, 0, 0.6);
}

.image-preview-close:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

/* ===== 无会话时禁用输入条 ===== */
.no-session-input-bar {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg);
}

.no-session-input-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: 1px dashed var(--border-2);
  border-radius: var(--r);
  color: var(--fg-4);
  font-size: 13px;
  cursor: default;
  user-select: none;
}

/* ===== 工作流 TODO（已迁移至 ChatInput 组件中） ===== */

/* ===== 消息缩略图 dock（右侧 dock 栏） ===== */
.msg-thumb-dock {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 50;
  pointer-events: none;
}

.thumb-dock-inner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-sizing: border-box;
  max-height: min(360px, calc(100vh - 240px));
  padding: 8px 4px;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  box-shadow: var(--glass-shadow);
  pointer-events: auto;
  opacity: 0.35;
  transition: opacity 0.25s ease, box-shadow 0.25s ease;
  overflow-y: auto;
  overscroll-behavior: contain;
  min-width: 10px;
}

.thumb-dock-inner:hover {
  opacity: 0.95;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
}

.thumb-dock-inner {
  scrollbar-width: none;
}

.thumb-dock-inner::-webkit-scrollbar {
  display: none;
}

.thumb-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 22px;
  box-sizing: border-box;
  padding: 3px 6px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
}

.thumb-item:hover {
  background: var(--accent-bg);
}

.thumb-item:hover .thumb-indicator {
  background: var(--accent);
  transform: scale(1.2);
}

.thumb-indicator {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-4);
  transition: all 0.2s ease;
}

.thumb-preview {
  font-size: 11px;
  color: var(--fg-3);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 0;
  opacity: 0;
  transition: max-width 0.25s ease, opacity 0.25s ease, margin-left 0.25s ease;
}

.thumb-dock-inner:hover .thumb-preview {
  max-width: 180px;
  opacity: 1;
  margin-left: 2px;
}

.empty-welcome {
  color: var(--fg);
  padding: 40px 24px 96px;
}

.empty-welcome-mark {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 26px;
  color: var(--fg-4);
}

.empty-welcome-title {
  margin: 0 0 42px;
  color: var(--fg);
  font-size: 30px;
  font-weight: 500;
  letter-spacing: 0;
}

.welcome-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 16px;
  width: min(100%, 980px);
}

.welcome-action {
  min-height: 142px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  color: var(--fg);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--t), background var(--t), box-shadow var(--t), transform var(--t);
}

.welcome-action:hover {
  background: var(--bg-2);
  border-color: var(--border-2);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.welcome-action svg { margin-bottom: auto; }
.welcome-action span { font-size: 16px; font-weight: 600; }
.welcome-action small { color: var(--fg-3); font-size: 12px; line-height: 1.45; }
.welcome-action.explore svg { color: var(--blue); }
.welcome-action.build svg { color: #7c3aed; }
.welcome-action.review svg { color: var(--green); }
.welcome-action.fix svg { color: var(--red); }

/* ===== 移动端适配 ===== */
@media (max-width: 1100px) {
  .welcome-actions {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
    max-width: 620px;
  }
}

@media (max-width: 640px) {
  .messages { padding: 12px 8px 100px; }
  .msg-body { max-width: 95%; }
  .empty-title { font-size: 14px; }
  .empty-desc { font-size: 12px; }
  .empty-welcome { padding: 28px 16px 72px; }
  .empty-welcome-mark { margin-bottom: 16px; }
  .empty-welcome-title { margin-bottom: 24px; font-size: 24px; }
  .welcome-actions { grid-template-columns: 1fr 1fr; gap: 10px; }
  .welcome-action { min-height: 118px; padding: 14px; }
  .welcome-action span { font-size: 14px; }
  .welcome-action small { font-size: 11px; }
  .suggestion { font-size: 11px; padding: 3px 8px; }
  .scroll-bottom-btn { right: 12px; bottom: 100px; width: 32px; height: 32px; }
  .ai-preparing { padding: 6px 10px; }
  .ai-dot { width: 6px; height: 6px; }
  .ai-label { font-size: 12px; }
  .msg-thumb-dock { display: none; } /* 手机端隐藏缩略图dock */
  .chat-head { padding: 6px 10px; }
    .chat-head-title { font-size: 13px; }
}

.messages.messages-welcome { padding: 0; }

.welcome-input-drop-enter-active {
  transition: opacity 220ms ease-out, transform 420ms cubic-bezier(0.22, 0.8, 0.24, 1);
}

.welcome-input-drop-enter-from {
  opacity: 0.35;
  transform: translateY(-180px);
}

</style>

<style scoped>
.welcome-screen {
  position: relative;
  min-height: 100%;
  padding: 40px 24px 132px;
  overflow: hidden;
  isolation: isolate;
}

.welcome-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100%, 800px);
  margin: auto;
  transform: translateY(34px);
}

.welcome-heading {
  margin: 0 0 54px;
  color: #29292d;
  font-size: 36px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0;
  text-align: center;
}

.welcome-composer {
  position: relative;
  z-index: 20;
  width: 100%;
  padding: 0;
  background: transparent;
}

.welcome-composer.workspace-menu-open {
  z-index: 80;
}

.welcome-workspace-row {
  position: relative;
  z-index: 0;
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 6px;
}

.workspace-menu-open .welcome-workspace-row {
  z-index: 60;
}

.welcome-workspace-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 8px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--fg-2);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.welcome-workspace-button:hover,
.welcome-workspace-button:focus-visible {
  background: #e7e7e9;
  outline: none;
}

.welcome-workspace-button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-workspace-button > svg:first-child { color: var(--fg-3); }
.welcome-workspace-button > svg:last-child { color: var(--fg-4); }

.welcome-workspace-menu,
.welcome-model-menu {
  position: absolute;
  z-index: 20;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--bg);
  box-shadow: var(--shadow-lg);
}

.welcome-workspace-menu {
  top: calc(100% - 3px);
  left: 8px;
  width: min(300px, calc(100vw - 64px));
  height: 248px;
  padding: 4px;
  display: flex;
  flex-direction: column;
}

.welcome-workspace-menu-actions {
  flex-shrink: 0;
  padding-bottom: 4px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.welcome-workspace-manage {
  font-weight: 600;
}

.welcome-workspace-list {
  min-height: 0;
  overflow-y: auto;
}

.welcome-workspace-menu button,
.welcome-model-menu button {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  min-height: 34px;
  padding: 7px 9px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.welcome-workspace-menu button:hover,
.welcome-workspace-menu button.active,
.welcome-model-menu button:hover,
.welcome-model-menu button.active {
  background: var(--bg-3);
  color: var(--fg);
}

.welcome-workspace-empty {
  display: block;
  padding: 10px;
  color: var(--fg-4);
  font-size: 12px;
}

.welcome-composer textarea {
  display: block;
  width: 100%;
  min-height: 96px;
  padding: 16px;
  border: 1px solid #dedee1;
  border-bottom: 0;
  border-radius: 10px 10px 0 0;
  outline: none;
  resize: none;
  background: var(--bg);
  color: var(--fg);
  font: inherit;
  font-size: 15px;
  line-height: 1.55;
}

.welcome-composer textarea::placeholder { color: #aaaaaf; }
.welcome-composer textarea:focus { border-color: var(--accent); }
.welcome-composer:focus-within .welcome-composer-footer { border-color: var(--accent); }

.welcome-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  padding: 8px 10px 8px 15px;
  border: 1px solid #dedee1;
  border-top: 0;
  border-radius: 0 0 10px 10px;
  background: var(--bg);
}

.welcome-composer-options,
.welcome-composer-actions,
.welcome-option {
  display: flex;
  align-items: center;
}

.welcome-composer-options { gap: 16px; }
.welcome-option {
  gap: 6px;
  color: var(--fg-2);
  font-size: 13px;
  white-space: nowrap;
}

.welcome-option:first-child { color: var(--fg-3); }
.welcome-composer-actions { gap: 10px; }

.welcome-model-selector { position: relative; }
.welcome-model-button {
  display: flex;
  align-items: center;
  gap: 5px;
  max-width: 210px;
  padding: 5px 6px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2);
  font: 13px var(--mono);
  cursor: pointer;
}

.welcome-model-button:hover { background: var(--bg-3); }
.welcome-model-button:disabled { cursor: default; opacity: 0.7; }
.welcome-model-button:disabled:hover { background: transparent; }
.welcome-model-button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.welcome-model-button svg { flex: 0 0 auto; color: var(--fg-4); }

.welcome-model-menu {
  right: 0;
  bottom: calc(100% + 8px);
  min-width: 190px;
  padding: 4px;
}

.welcome-model-menu button { font-family: var(--mono); }

.welcome-control { position: relative; }
.welcome-control-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 6px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.welcome-control-button:hover { background: var(--bg-3); }
.welcome-control-button svg { color: var(--fg-4); }

.welcome-control-menu,
.welcome-effort-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  z-index: 30;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--bg);
  box-shadow: var(--shadow-lg);
}

.welcome-control-menu {
  min-width: 128px;
  padding: 4px;
}

.welcome-control-menu button {
  width: 100%;
  min-height: 32px;
  padding: 6px 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2);
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.welcome-control-menu button:hover,
.welcome-control-menu button.active {
  background: var(--bg-3);
  color: var(--accent);
}

.welcome-effort-menu {
  width: min(270px, calc(100vw - 28px));
  padding: 12px;
}

.welcome-effort-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--fg-3);
  font-size: 11px;
}

.welcome-effort-summary strong { color: var(--accent); font-size: 13px; }
.welcome-effort-range { width: 100%; accent-color: var(--accent); cursor: pointer; }
.welcome-effort-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 3px;
  color: var(--fg-4);
  font-size: 10px;
}

.welcome-send-button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: #707177;
  color: #fff;
  cursor: pointer;
  transition: background var(--t), transform var(--t);
}

.welcome-send-button:hover:not(:disabled) {
  background: #4e4f54;
  transform: translateY(-1px);
}

.welcome-send-button:disabled { cursor: not-allowed; opacity: 0.42; }

[data-theme="dark"] .welcome-heading { color: var(--fg); }
[data-theme="dark"] .welcome-composer { background: #202020; }
[data-theme="dark"] .welcome-workspace-button:hover,
[data-theme="dark"] .welcome-workspace-button:focus-visible { background: #2b2b2b; }
[data-theme="dark"] .welcome-composer textarea,
[data-theme="dark"] .welcome-composer-footer {
  border-color: #303030;
  background: #171717;
}

@media (max-width: 768px) {
  .welcome-screen { padding: 24px 8px 84px; }
  .welcome-panel { transform: translateY(16px); }
  .welcome-heading { margin-bottom: 28px; font-size: 28px; }
  .welcome-composer-options { gap: 10px; }
  .welcome-model-button { max-width: 130px; }
}

@media (max-width: 520px) {
  .welcome-heading { font-size: 24px; }
  .welcome-composer-footer { align-items: flex-end; gap: 10px; }
  .welcome-composer-options { flex-direction: column; align-items: flex-start; gap: 4px; }
  .welcome-model-button { max-width: 94px; }
  .welcome-control-button { padding-inline: 4px; }
  .welcome-composer-actions { gap: 4px; }
}
</style>
