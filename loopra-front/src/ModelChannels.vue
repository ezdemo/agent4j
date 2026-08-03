<template>
  <section class="model-channels" :data-theme="theme">
    <header class="model-channels-header">
      <button v-if="showBack" class="model-channels-back" type="button" title="返回" aria-label="返回" @click="emit('back')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div>
        <h1>模型渠道</h1>
        <p>每个渠道独立管理 API 地址、密钥和模型配置</p>
      </div>
      <button class="model-channels-save" type="button" :disabled="saving || loading" @click="save">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </header>

    <div v-if="loading" class="model-channels-empty">正在加载模型渠道...</div>
    <div v-else class="model-channels-body">
      <div v-if="setupNoticeVisible" class="model-channels-setup-overlay">
        <section class="model-channels-setup-notice" role="dialog" aria-modal="true" aria-labelledby="model-channels-setup-title">
          <h2 id="model-channels-setup-title">首次使用需要完成模型配置</h2>
          <p>请填写 API 地址和 API Key，添加并选择一个模型后保存。</p>
          <button class="model-channels-setup-confirm" type="button" @click="setupNoticeVisible = false">确定，开始配置</button>
        </section>
      </div>
      <section class="model-validator">
        <label>
          <span>命令校验模型</span>
          <select v-model="validationModelKey" title="替代人工 HITL 对待审批工具调用作出决定">
            <option value="">不启用</option>
            <optgroup v-for="channel in channels" :key="channel.id" :label="channel.name">
              <option v-for="model in namedModels(channel)" :key="model.id" :value="modelKey(channel.id, model.name)">
                {{ model.name }}
              </option>
            </optgroup>
          </select>
        </label>
      </section>
      <div v-if="!channels.length" class="model-channels-empty">暂无模型渠道</div>
      <article v-for="(channel, index) in channels" :key="channel.id" class="model-channel" :class="{ active: channel.id === activeChannelId }">
        <header class="model-channel-header">
          <button class="model-channel-toggle" type="button" :aria-expanded="channel.expanded" @click="toggleChannel(channel)">
            <svg class="model-collapse-icon" :class="{ expanded: channel.expanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 18 6-6-6-6"/></svg>
            <span class="model-channel-title">{{ channel.name || `渠道 ${index + 1}` }}</span>
            <span class="model-channel-count">{{ channel.models.length }} 个模型</span>
          </button>
          <label class="model-channel-current">
            <input v-model="activeChannelId" :value="channel.id" type="radio" name="active-channel" @change="ensureCurrentModel(channel)" />
            <span>当前渠道</span>
          </label>
          <button v-if="channels.length > 1" class="model-channel-delete" type="button" title="删除渠道" @click="removeChannel(index)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
          </button>
        </header>
        <div v-if="channel.expanded" class="model-channel-fields">
          <label>
            <span>渠道名称</span>
            <input v-model.trim="channel.name" type="text" placeholder="例如 OpenAI" />
          </label>
          <label>
            <span>API 地址</span>
            <input v-model.trim="channel.baseUrl" type="url" placeholder="https://api.openai.com/v1" />
          </label>
          <label>
            <span>接口类型</span>
            <select v-model="channel.apiProtocol">
              <option value="chat_completions">Chat Completions</option>
              <option value="responses">Responses</option>
            </select>
          </label>
          <label>
            <span>API 密钥</span>
            <input v-model="channel.apiKey" type="password" :placeholder="channel.secretConfigured ? '已保存，留空则不修改' : 'sk-...'" autocomplete="new-password" />
          </label>

          <section class="model-channel-models" aria-label="模型列表">
            <header class="model-channel-models-label">
              <span>模型列表</span>
              <div class="model-channel-actions">
                <button
                  class="model-channel-clear"
                  type="button"
                  :disabled="syncingChannelId === channel.id || !channel.models.length"
                  title="清空当前渠道的模型列表"
                  @click="clearModels(channel)"
                >
                  <DeleteOutlined />
                  清空当前模型列表
                </button>
                <button
                  class="model-channel-sync"
                  type="button"
                  :disabled="syncingChannelId === channel.id || !canSyncRemoteModels(channel)"
                  :title="canSyncRemoteModels(channel) ? '从此渠道的服务端同步模型列表' : '请填写 API 地址和密钥'"
                  @click="syncRemoteModels(channel)"
                >
                  <ReloadOutlined v-if="syncingChannelId !== channel.id" />
                  <LoadingOutlined v-else class="model-channel-spin" />
                  同步远端模型
                </button>
              </div>
            </header>

            <div v-if="!channel.models.length" class="model-list-empty">尚未添加模型</div>
            <article v-for="(model, modelIndex) in channel.models" :key="model.id" class="model-config-row" :class="{ expanded: model.expanded }">
              <header class="model-config-header">
                <button class="model-config-toggle" type="button" :aria-expanded="model.expanded" @click="toggleModel(model)">
                  <svg class="model-collapse-icon" :class="{ expanded: model.expanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 18 6-6-6-6"/></svg>
                  <span>{{ model.name || `模型 ${modelIndex + 1}` }}</span>
                </button>
                <label class="model-config-current" title="设为当前模型">
                  <input
                    :name="`current-model-${channel.id}`"
                    :checked="channel.id === activeChannelId && model.name === currentModel"
                    type="radio"
                    @change="selectCurrentModel(channel, model)"
                  />
                  <span class="sr-only">设为当前模型</span>
                </label>
                <button class="model-config-delete" type="button" title="删除模型" :aria-label="`删除模型 ${model.name || modelIndex + 1}`" @click="removeModel(channel, modelIndex)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
                </button>
              </header>
              <div v-if="model.expanded" class="model-config-fields">
                <div class="model-config-main">
                  <label>
                    <span>名称</span>
                    <input v-model.trim="model.name" type="text" placeholder="例如 gpt-4o" @change="handleModelNameChange(channel, model)" />
                  </label>
                  <label>
                    <span>上下文（tokens）</span>
                    <input v-model="model.contextTokens" type="number" min="1" step="1" placeholder="可空" />
                  </label>
                  <label class="model-config-switch">
                    <input v-model="model.imageInput" type="checkbox" />
                    <span>支持图片输入</span>
                  </label>
                </div>
                <div class="model-config-price">
                  <label class="model-config-switch">
                    <input v-model="model.priceEnabled" type="checkbox" />
                    <span>配置价格（元 / 百万 tokens）</span>
                  </label>
                  <template v-if="model.priceEnabled">
                    <label v-for="field in priceFields" :key="field">
                      <span>{{ priceFieldLabels[field] }}</span>
                      <input v-model="model.price[field]" type="number" min="0" step="0.001" :aria-label="`${model.name || '模型'} ${priceFieldLabels[field]}价格`" />
                    </label>
                  </template>
                </div>
              </div>
            </article>
            <button class="model-config-add" type="button" @click="addModel(channel)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>
              添加模型
            </button>
          </section>
        </div>
      </article>
      <button class="model-channel-add" type="button" @click="addChannel">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>
        添加渠道
      </button>
    </div>
  </section>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {message} from 'ant-design-vue'
import {DeleteOutlined, LoadingOutlined, ReloadOutlined} from '@ant-design/icons-vue'
import {useAppStore} from './stores/app'
import {configAPI} from './services/api'

const priceFields = ['input', 'cache', 'output']
const priceFieldLabels = {input: '输入', cache: '缓存', output: '输出'}

const props = defineProps({
  showBack: {type: Boolean, default: true},
  setupRequired: {type: Boolean, default: false}
})
const setupNoticeVisible = ref(props.setupRequired)
const emit = defineEmits(['back', 'saved'])
const store = useAppStore()
const theme = computed(() => store.settings.theme)
const channels = ref([])
const activeChannelId = ref('')
const currentModel = ref('')
const validationModel = ref('')
const validationModelChannelId = ref('')
const validationModelKey = computed({
  get: () => validationModel.value && validationModelChannelId.value
    ? modelKey(validationModelChannelId.value, validationModel.value)
    : '',
  set: (value) => {
    if (!value) {
      validationModel.value = ''
      validationModelChannelId.value = ''
      return
    }
    try {
      const [channelId, model] = JSON.parse(value)
      validationModelChannelId.value = channelId || ''
      validationModel.value = model || ''
    } catch {
      validationModel.value = ''
      validationModelChannelId.value = ''
    }
  }
})
const loading = ref(true)
const saving = ref(false)
const syncingChannelId = ref('')

function modelKey(channelId, model) {
  return JSON.stringify([channelId, model])
}

function makeId(prefix = 'channel') {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function normalizeNonNegative(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : 0
}

function normalizeContextLength(value) {
  if (value === '' || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.trunc(number) : null
}

function newModel(name = '') {
  return {
    id: makeId('model'),
    expanded: false,
    name,
    originalName: name,
    contextTokens: null,
    imageInput: false,
    priceEnabled: false,
    price: {input: 0, cache: 0, output: 0}
  }
}

function normalizeModel(model) {
  if (typeof model === 'string') return newModel(model.trim())
  const normalized = newModel(String(model?.name || '').trim())
  normalized.contextTokens = normalizeContextLength(model?.contextTokens)
  normalized.imageInput = Boolean(model?.imageInput)
  normalized.priceEnabled = model?.price !== null && typeof model?.price === 'object'
  if (normalized.priceEnabled) {
    normalized.price = {
      input: normalizeNonNegative(model.price.input),
      cache: normalizeNonNegative(model.price.cache),
      output: normalizeNonNegative(model.price.output)
    }
  }
  return normalized
}

function normalizeChannel(channel, index) {
  const models = Array.isArray(channel.models) ? channel.models.map(normalizeModel) : []
  return {
    id: channel.id || makeId(),
    expanded: false,
    name: channel.name || `渠道 ${index + 1}`,
    baseUrl: channel.baseUrl || '',
    apiProtocol: channel.apiProtocol === 'responses' ? 'responses' : 'chat_completions',
    apiKey: '',
    secretConfigured: Boolean(channel.apiKey),
    models
  }
}

function namedModels(channel) {
  return channel.models.filter((model) => model.name.trim())
}

function ensureCurrentModel(channel) {
  if (!channel || channel.id !== activeChannelId.value) return
  const names = namedModels(channel).map((model) => model.name)
  if (!names.includes(currentModel.value)) currentModel.value = names[0] || ''
}

function ensureValidationModel() {
  const channel = channels.value.find((item) => item.id === validationModelChannelId.value)
  if (!channel || !namedModels(channel).some((model) => model.name === validationModel.value)) {
    validationModel.value = ''
    validationModelChannelId.value = ''
  }
}

function handleModelNameChange(channel, model) {
  if (channel.id === validationModelChannelId.value && model.originalName === validationModel.value) {
    validationModel.value = model.name
  }
  if (channel.id === activeChannelId.value && model.originalName === currentModel.value) {
    currentModel.value = model.name
  }
  model.originalName = model.name
  ensureCurrentModel(channel)
  ensureValidationModel()
}

function selectCurrentModel(channel, model) {
  activeChannelId.value = channel.id
  currentModel.value = model.name
}

function toggleChannel(channel) {
  channel.expanded = !channel.expanded
}

function toggleModel(model) {
  model.expanded = !model.expanded
}

function addModel(channel) {
  const model = newModel()
  model.expanded = true
  channel.models.push(model)
}

function removeModel(channel, index) {
  const model = channel.models[index]
  if (channel.id === validationModelChannelId.value && model?.name === validationModel.value) {
    message.warning('请先取消或更换命令校验模型')
    return
  }
  const [removed] = channel.models.splice(index, 1)
  if (channel.id === activeChannelId.value && removed?.name === currentModel.value) ensureCurrentModel(channel)
  ensureValidationModel()
}

function clearModels(channel) {
  if (!channel?.models.length) return
  channel.models = []
  ensureCurrentModel(channel)
  ensureValidationModel()
  message.success('已清空当前渠道模型列表')
}

function addChannel() {
  const channel = normalizeChannel({}, channels.value.length)
  channel.expanded = true
  channels.value.push(channel)
  activeChannelId.value = channel.id
  currentModel.value = ''
}

function removeChannel(index) {
  const channel = channels.value[index]
  if (channel?.id === validationModelChannelId.value) {
    message.warning('请先取消或更换命令校验模型')
    return
  }
  const [removed] = channels.value.splice(index, 1)
  if (removed?.id === activeChannelId.value) {
    const next = channels.value[0]
    activeChannelId.value = next?.id || ''
    ensureCurrentModel(next || {models: []})
  }
  ensureValidationModel()
}

function canSyncRemoteModels(channel) {
  return Boolean(channel?.secretConfigured || (channel?.baseUrl || '').trim() && (channel?.apiKey || '').trim())
}

function modelPayload(model) {
  return {
    name: model.name.trim(),
    contextTokens: normalizeContextLength(model.contextTokens),
    imageInput: Boolean(model.imageInput),
    price: model.priceEnabled
      ? {
          input: normalizeNonNegative(model.price.input),
          cache: normalizeNonNegative(model.price.cache),
          output: normalizeNonNegative(model.price.output)
        }
      : null
  }
}

async function load() {
  loading.value = true
  try {
    const response = await configAPI.getConfig()
    if (!response.success) throw new Error(response.message || '加载模型配置失败')
    const config = response.data || {}
    channels.value = (config.modelChannels || []).map(normalizeChannel)
    activeChannelId.value = config.modelChannelId || channels.value[0]?.id || ''
    currentModel.value = config.model || ''
    validationModel.value = config.validationModel || ''
    validationModelChannelId.value = config.validationModelChannelId || ''
    if (!channels.value.length) addChannel()
    ensureValidationModel()
    const active = channels.value.find((channel) => channel.id === activeChannelId.value) || channels.value[0]
    if (active) {
      activeChannelId.value = active.id
      ensureCurrentModel(active)
    }
  } catch (error) {
    message.error('加载模型配置失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function save() {
  const payloadChannels = channels.value.map((channel) => ({
    id: channel.id,
    name: channel.name.trim(),
    baseUrl: channel.baseUrl.trim(),
    apiProtocol: channel.apiProtocol,
    apiKey: channel.apiKey.trim(),
    models: namedModels(channel).map(modelPayload)
  }))
  const active = payloadChannels.find((channel) => channel.id === activeChannelId.value) || payloadChannels[0]
  if (!active?.name || !active.baseUrl || !active.models.length) {
    message.error('当前渠道需要填写名称、API 地址和至少一个具备名称的模型')
    return
  }
  const modelNames = active.models.map((model) => model.name)
  const model = modelNames.includes(currentModel.value) ? currentModel.value : modelNames[0]
  ensureValidationModel()
  saving.value = true
  try {
    const response = await configAPI.updateConfig({
      modelChannels: payloadChannels,
      modelChannelId: active.id,
      model,
      validationModel: validationModel.value,
      validationModelChannelId: validationModelChannelId.value
    })
    if (!response.success) throw new Error(response.message || '保存模型配置失败')
    for (const channel of channels.value) {
      if (channel.apiKey.trim()) channel.secretConfigured = true
      channel.apiKey = ''
    }
    currentModel.value = model
    message.success('模型渠道已保存')
    emit('saved')
  } catch (error) {
    message.error('保存模型配置失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

async function syncRemoteModels(channel) {
  if (!canSyncRemoteModels(channel)) {
    message.warning('请先填写 API 地址和密钥')
    return
  }
  syncingChannelId.value = channel.id
  try {
    const response = await configAPI.probeRemoteModels({
      channelId: channel.id,
      baseUrl: channel.baseUrl.trim(),
      apiKey: channel.apiKey.trim()
    })
    if (!response.success) throw new Error(response.message || '远端模型同步失败')
    const remoteNames = [...new Set((response.data || [])
      .map((item) => typeof item === 'string' ? item.trim() : String(item?.name || '').trim())
      .filter(Boolean))]
    channel.models = remoteNames.map((name) => newModel(name))
    if (channel.id === activeChannelId.value) ensureCurrentModel(channel)
    ensureValidationModel()
    if (!remoteNames.length) {
      message.warning('远端没有返回可用模型，已清空本地模型')
      return
    }
    message.success(`已同步 ${remoteNames.length} 个远端模型，已替换本地模型，保存后生效`)
  } catch (error) {
    message.error('远端模型同步失败：' + (error.message || '未知错误'))
  } finally {
    syncingChannelId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.model-channels { width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; display: flex; flex-direction: column; color: var(--fg); background: var(--bg); }
.model-channels-header { height: 64px; display: flex; align-items: center; gap: 12px; padding: 0 28px; border-bottom: 1px solid var(--border); flex: 0 0 auto; }
.model-channels-header h1 { margin: 0; font-size: 16px; font-weight: 600; }
.model-channels-header p { margin: 3px 0 0; color: var(--fg-4); font-size: 12px; }
.model-channels-setup-overlay { position: fixed; z-index: 10; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(0, 0, 0, .38); }
.model-channels-setup-notice { width: min(100%, 400px); box-sizing: border-box; padding: 20px; border: 1px solid var(--border); border-top: 3px solid var(--accent); border-radius: 7px; background: var(--bg); box-shadow: 0 18px 48px rgba(0, 0, 0, .24); }
.model-channels-setup-notice h2 { margin: 0; color: var(--fg); font-size: 16px; line-height: 1.4; }
.model-channels-setup-notice p { margin: 8px 0 18px; color: var(--fg-2); font-size: 13px; line-height: 1.55; }
.model-channels-setup-confirm { display: inline-flex; align-items: center; justify-content: center; min-height: 32px; padding: 0 13px; border: 0; border-radius: 5px; background: var(--accent); color: #fff; font: inherit; font-size: 13px; cursor: pointer; }
.model-channels-setup-confirm:hover { filter: brightness(.96); }

.model-channels-back:hover { background: var(--bg-3); color: var(--fg); }
.model-channels-back svg, .model-channel-delete svg, .model-config-delete svg { width: 17px; height: 17px; }
.model-collapse-icon { width: 16px; height: 16px; flex: 0 0 auto; transition: transform .15s ease; }
.model-collapse-icon.expanded { transform: rotate(90deg); }
.model-channels-save { height: 32px; margin-left: auto; padding: 0 14px; border: 0; border-radius: 5px; background: var(--accent); color: #fff; font: inherit; font-size: 13px; cursor: pointer; }
.model-channels-save:disabled { opacity: .55; cursor: default; }
.model-channels[data-theme="dark"] .model-channels-save:not(:disabled) { background: #d4d4d8; color: #18181b; }
.model-channels[data-theme="dark"] .model-channels-save:not(:disabled):hover { background: #f4f4f5; }
.model-channels[data-theme="dark"] .model-channels-save:disabled { background: #303034; color: #9499a3; opacity: 1; }
.model-channels-body { box-sizing: border-box; width: 100%; min-width: 0; min-height: 0; flex: 1; margin: 0; padding: 28px max(24px, calc((100% - 840px) / 2)) 48px; overflow-x: hidden; overflow-y: auto; }
.model-validator { margin-bottom: 14px; padding: 14px 16px; border: 1px solid var(--border); border-radius: 7px; background: var(--bg-2); }
.model-validator label { display: grid; grid-template-columns: minmax(130px, auto) minmax(220px, 1fr); align-items: center; gap: 16px; color: var(--fg-2); font-size: 13px; }
.model-validator select { width: 100%; height: 32px; padding: 0 8px; border: 1px solid var(--border); border-radius: 5px; outline: none; background: var(--bg); color: var(--fg); font: inherit; font-size: 13px; }
.model-validator select:focus { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 12%, transparent); }
.model-channels-empty { height: 100%; display: grid; place-items: center; color: var(--fg-4); font-size: 13px; }
.model-channel { min-width: 0; margin-bottom: 14px; overflow: hidden; border: 1px solid var(--border); border-radius: 7px; background: var(--bg); }
.model-channel.active { border-color: color-mix(in srgb, var(--accent) 52%, var(--border)); }
.model-channel-header { height: 42px; display: flex; align-items: center; gap: 8px; padding: 0 10px 0 8px; background: var(--bg-2); }
.model-channel-toggle, .model-config-toggle { min-width: 0; display: inline-flex; align-items: center; gap: 7px; border: 0; background: transparent; color: var(--fg-2); font: inherit; cursor: pointer; text-align: left; }
.model-channel-toggle { flex: 1; height: 100%; padding: 0 6px; }
.model-channel-toggle:hover, .model-config-toggle:hover { color: var(--fg); }
.model-channel-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; }
.model-channel-count { flex: 0 0 auto; color: var(--fg-4); font-size: 12px; }
.model-channel-current { display: inline-flex; align-items: center; gap: 7px; color: var(--fg-2); font-size: 12px; cursor: pointer; white-space: nowrap; }
.model-channel-current input, .model-config-current input, .model-config-switch input { accent-color: var(--accent); }
.model-channel-delete { color: var(--fg-4); }
.model-channel-delete:hover, .model-config-delete:hover { color: #c2413b; background: rgba(220, 38, 38, .09); }
.model-channel-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; padding: 16px; border-top: 1px solid var(--border); }
.model-channel-fields > label, .model-config-row label { min-width: 0; display: grid; gap: 5px; color: var(--fg-3); font-size: 12px; }
.model-channel-fields input, .model-channel-fields select, .model-config-row input { width: 100%; box-sizing: border-box; border: 1px solid var(--border); border-radius: 5px; outline: none; background: var(--bg); color: var(--fg); font: inherit; font-size: 13px; }
.model-channel-fields > label input, .model-channel-fields > label select, .model-config-row input[type="text"], .model-config-row input[type="number"] { height: 32px; padding: 0 8px; }
.model-channel-fields input:focus, .model-channel-fields select:focus, .model-config-row input:focus { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 12%, transparent); }
.model-channel-models { grid-column: 1 / -1; min-width: 0; display: grid; gap: 8px; }
.model-channel-models-label { display: flex; align-items: center; min-height: 18px; color: var(--fg-3); font-size: 12px; }
.model-channel-actions { margin-left: auto; display: inline-flex; align-items: center; gap: 8px; }
.model-channel-clear, .model-channel-sync { display: inline-flex; align-items: center; gap: 5px; border: 0; border-radius: 4px; padding: 2px 5px; background: transparent; color: var(--fg-4); font: inherit; font-size: 12px; cursor: pointer; }
.model-channel-clear:hover:not(:disabled) { background: rgba(220, 38, 38, .09); color: #c2413b; }
.model-channel-sync:hover:not(:disabled) { background: var(--bg-3); color: var(--accent); }
.model-channel-clear:disabled, .model-channel-sync:disabled { cursor: not-allowed; opacity: .48; }
.model-channel-clear :deep(svg), .model-channel-sync :deep(svg), .model-config-add svg { width: 14px; height: 14px; }
.model-channel-spin { animation: model-channel-spin .8s linear infinite; }
@keyframes model-channel-spin { to { transform: rotate(360deg); } }
.model-list-empty { padding: 10px; border: 1px dashed var(--border); border-radius: 5px; color: var(--fg-4); font-size: 12px; text-align: center; }
.model-config-row { overflow: hidden; border: 1px solid var(--border); border-radius: 6px; background: var(--bg-2); }
.model-config-header { height: 38px; display: flex; align-items: center; gap: 7px; padding: 0 5px 0 7px; }
.model-config-toggle { flex: 1; height: 100%; overflow: hidden; font-size: 13px; }
.model-config-toggle span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-config-current { display: flex !important; align-items: center; justify-content: center; width: 30px; height: 30px; cursor: pointer; }
.model-config-current input, .model-config-switch input { width: auto; height: auto; margin: 0; box-shadow: none; }
.model-config-fields { display: grid; gap: 7px; padding: 9px; border-top: 1px solid var(--border); }
.model-config-main { display: grid; grid-template-columns: minmax(150px, 1.4fr) minmax(120px, .9fr) auto; align-items: end; gap: 8px; }
.model-config-switch { display: inline-flex !important; grid-template-columns: auto 1fr; align-items: center; gap: 6px !important; min-height: 32px; color: var(--fg-2) !important; cursor: pointer; white-space: nowrap; }
.model-config-price { display: flex; align-items: end; gap: 8px; padding-top: 7px; border-top: 1px dashed var(--border); }
.model-config-price > label:not(.model-config-switch) { width: 100px; }
.model-config-price .model-config-switch { margin-right: auto; }
.model-config-add, .model-channel-add { display: inline-flex; align-items: center; justify-content: center; gap: 7px; border: 1px dashed var(--border); border-radius: 6px; background: transparent; color: var(--fg-3); font: inherit; font-size: 12px; cursor: pointer; }
.model-config-add { height: 31px; }
.model-channel-add { width: 100%; height: 38px; font-size: 13px; }
.model-config-add:hover, .model-channel-add:hover { color: var(--accent); border-color: var(--accent); background: var(--accent-bg); }
.model-channel-add svg { width: 16px; height: 16px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 700px) { .model-channels-header { padding: 0 14px; } .model-channels-header p { display: none; } .model-channels-body { width: 100%; padding: 14px 14px 32px; } .model-validator label { grid-template-columns: minmax(0, 1fr); gap: 7px; } .model-channel-current span { display: none; } .model-channel-fields { grid-template-columns: minmax(0, 1fr); gap: 12px; padding: 14px; } .model-channel-models { grid-column: auto; } .model-config-main { grid-template-columns: minmax(0, 1fr) auto; } .model-config-main .model-config-switch { grid-column: 1 / -1; } .model-config-price { flex-wrap: wrap; } .model-config-price .model-config-switch { width: 100%; } }
</style>
