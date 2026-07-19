<template>
  <section class="model-channels" :data-theme="theme">
    <header class="model-channels-header">
      <button v-if="showBack" class="model-channels-back" type="button" title="返回" aria-label="返回" @click="emit('back')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div>
        <h1>模型渠道</h1>
        <p>每个渠道独立管理 API 地址、密钥和可用模型</p>
      </div>
      <button class="model-channels-save" type="button" :disabled="saving || loading" @click="save">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </header>

    <div v-if="loading" class="model-channels-empty">正在加载模型渠道...</div>
    <div v-else class="model-channels-body">
      <div v-if="!channels.length" class="model-channels-empty">暂无模型渠道</div>
      <article v-for="(channel, index) in channels" :key="channel.id" class="model-channel" :class="{ active: channel.id === activeChannelId }">
        <header class="model-channel-header">
          <label class="model-channel-current">
            <input v-model="activeChannelId" :value="channel.id" type="radio" name="active-channel" @change="ensureCurrentModel(channel)" />
            <span>当前渠道</span>
          </label>
          <button v-if="channels.length > 1" class="model-channel-delete" type="button" title="删除渠道" @click="removeChannel(index)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
          </button>
        </header>
        <div class="model-channel-fields">
          <label>
            <span>渠道名称</span>
            <input v-model.trim="channel.name" type="text" placeholder="例如 OpenAI" />
          </label>
          <label>
            <span>API 地址</span>
            <input v-model.trim="channel.baseUrl" type="url" placeholder="https://api.openai.com/v1" />
          </label>
          <label>
            <span>API 密钥</span>
            <input v-model="channel.apiKey" type="password" :placeholder="channel.secretConfigured ? '已保存，留空则不修改' : 'sk-...'" autocomplete="new-password" />
          </label>
          <label class="model-channel-models">
            <span class="model-channel-models-label">
              模型列表
              <button
                class="model-channel-sync"
                type="button"
                :disabled="syncingChannelId === channel.id || !canSyncRemoteModels(channel)"
                :title="canSyncRemoteModels(channel) ? '从此渠道的服务端同步模型列表' : '请填写 API 地址和密钥'"
                @click="syncRemoteModels(channel)"
              >
                <svg v-if="syncingChannelId !== channel.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 11a8 8 0 1 0 2 5.3"/><path d="M20 4v7h-7"/></svg>
                <svg v-else class="model-channel-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="8" stroke-dasharray="12 8"/></svg>
                同步远端模型
              </button>
            </span>
            <textarea v-model="channel.modelsText" rows="4" placeholder="每行一个模型名称"></textarea>
          </label>
        </div>
      </article>
      <button class="model-channel-add" type="button" @click="addChannel">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>
        添加渠道
      </button>

      <section class="model-pricing">
        <header class="model-pricing-header">
          <div>
            <h2>模型价格</h2>
            <p>人民币 / 百万 tokens，用于费用统计</p>
          </div>
        </header>
        <div v-if="!priceModelNames.length" class="model-pricing-empty">添加模型后可配置价格</div>
        <div v-else class="model-pricing-table-wrap">
          <table class="model-pricing-table">
            <thead>
              <tr>
                <th>模型</th>
                <th>输入</th>
                <th>缓存</th>
                <th>输出</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="modelName in priceModelNames" :key="modelName">
                <td class="model-pricing-name" :title="modelName">{{ modelName }}</td>
                <td v-for="field in priceFields" :key="field">
                  <input
                    :value="priceValue(modelName, field)"
                    type="number"
                    min="0"
                    step="0.001"
                    :aria-label="`${modelName} ${priceFieldLabels[field]}价格`"
                    @input="setPrice(modelName, field, $event.target.value)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {message} from 'ant-design-vue'
import {useAppStore} from './stores/app'
import {configAPI} from './services/api'

defineProps({ showBack: { type: Boolean, default: true } })
const emit = defineEmits(['back', 'saved'])
const store = useAppStore()
const theme = computed(() => store.settings.theme)
const channels = ref([])
const activeChannelId = ref('')
const currentModel = ref('')
const loading = ref(true)
const saving = ref(false)
const syncingChannelId = ref('')
const prices = ref({})
const priceFields = ['input', 'cache', 'output']
const priceFieldLabels = { input: '输入', cache: '缓存', output: '输出' }

const priceModelNames = computed(() => {
  const names = new Set(Object.keys(prices.value))
  for (const channel of channels.value) {
    for (const model of modelsOf(channel)) names.add(model)
  }
  return [...names].sort((a, b) => a.localeCompare(b))
})

function makeId() {
  return `channel-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function normalizeChannel(channel, index) {
  const models = Array.isArray(channel.models) ? channel.models : []
  return {
    id: channel.id || makeId(),
    name: channel.name || `渠道 ${index + 1}`,
    baseUrl: channel.baseUrl || '',
    apiKey: '',
    secretConfigured: Boolean(channel.apiKey),
    modelsText: models.join('\n')
  }
}

function modelsOf(channel) {
  return [...new Set(String(channel.modelsText || '').split(/\r?\n/).map((item) => item.trim()).filter(Boolean))]
}

function normalizePrice(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : 0
}

function normalizePrices(source) {
  return Object.fromEntries(Object.entries(source || {}).map(([modelName, price]) => [modelName, {
    input: normalizePrice(price?.input),
    cache: normalizePrice(price?.cache),
    output: normalizePrice(price?.output)
  }]))
}

function priceValue(modelName, field) {
  return prices.value[modelName]?.[field] ?? 0
}

function setPrice(modelName, field, value) {
  prices.value = {
    ...prices.value,
    [modelName]: {
      input: 0,
      cache: 0,
      output: 0,
      ...prices.value[modelName],
      [field]: normalizePrice(value)
    }
  }
}

function buildPricePayload() {
  return Object.fromEntries(priceModelNames.value.map((modelName) => [modelName, {
    input: priceValue(modelName, 'input'),
    cache: priceValue(modelName, 'cache'),
    output: priceValue(modelName, 'output')
  }]))
}

function canSyncRemoteModels(channel) {
  return Boolean(channel?.secretConfigured || (channel?.baseUrl || '').trim() && (channel?.apiKey || '').trim())
}

function ensureCurrentModel(channel) {
  const models = modelsOf(channel)
  if (!models.includes(currentModel.value)) currentModel.value = models[0] || ''
}

function addChannel() {
  const channel = normalizeChannel({}, channels.value.length)
  channels.value.push(channel)
  activeChannelId.value = channel.id
  currentModel.value = ''
}

function removeChannel(index) {
  const [removed] = channels.value.splice(index, 1)
  if (removed?.id === activeChannelId.value) {
    const next = channels.value[0]
    activeChannelId.value = next?.id || ''
    ensureCurrentModel(next || { modelsText: '' })
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
    prices.value = normalizePrices(config.price)
    if (!channels.value.length) addChannel()
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
    apiKey: channel.apiKey.trim(),
    models: modelsOf(channel)
  }))
  const active = payloadChannels.find((channel) => channel.id === activeChannelId.value) || payloadChannels[0]
  if (!active?.name || !active.baseUrl || !active.models.length) {
    message.error('当前渠道需要填写名称、API 地址和至少一个模型')
    return
  }
  const model = active.models.includes(currentModel.value) ? currentModel.value : active.models[0]
  saving.value = true
  try {
    const response = await configAPI.updateConfig({
      modelChannels: payloadChannels,
      modelChannelId: active.id,
      model,
      price: buildPricePayload()
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
    const models = [...new Set((response.data || []).map((item) => String(item || '').trim()).filter(Boolean))]
    if (!models.length) {
      message.warning('远端没有返回可用模型')
      return
    }
    channel.modelsText = models.join('\n')
    if (channel.id === activeChannelId.value && !models.includes(currentModel.value)) currentModel.value = models[0]
    message.success(`已同步 ${models.length} 个远端模型，保存后生效`)
  } catch (error) {
    message.error('远端模型同步失败：' + (error.message || '未知错误'))
  } finally {
    syncingChannelId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.model-channels { width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; display: flex; flex-direction: column; color: var(--fg); background: var(--bg); }.model-channels-header { height: 64px; display: flex; align-items: center; gap: 12px; padding: 0 28px; border-bottom: 1px solid var(--border); flex: 0 0 auto; }.model-channels-header h1 { margin: 0; font-size: 16px; font-weight: 600; }.model-channels-header p { margin: 3px 0 0; color: var(--fg-4); font-size: 12px; }.model-channels-back, .model-channel-delete { width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border: 0; border-radius: 5px; background: transparent; color: var(--fg-3); cursor: pointer; }.model-channels-back:hover { background: var(--bg-3); color: var(--fg); }.model-channels-back svg, .model-channel-delete svg { width: 17px; height: 17px; }.model-channels-save { height: 32px; margin-left: auto; padding: 0 14px; border: 0; border-radius: 5px; background: var(--accent); color: #fff; font: inherit; font-size: 13px; cursor: pointer; }.model-channels-save:disabled { opacity: .55; cursor: default; }.model-channels-body { box-sizing: border-box; width: 100%; min-width: 0; min-height: 0; flex: 1; margin: 0; padding: 28px max(24px, calc((100% - 780px) / 2)) 48px; overflow-x: hidden; overflow-y: auto; }.model-channels-empty { height: 100%; display: grid; place-items: center; color: var(--fg-4); font-size: 13px; }.model-channel { min-width: 0; margin-bottom: 14px; overflow: hidden; border: 1px solid var(--border); border-radius: 7px; background: var(--bg); }.model-channel.active { border-color: color-mix(in srgb, var(--accent) 52%, var(--border)); }.model-channel-header { height: 42px; display: flex; align-items: center; padding: 0 10px 0 14px; border-bottom: 1px solid var(--border); background: var(--bg-2); }.model-channel-current { display: inline-flex; align-items: center; gap: 7px; color: var(--fg-2); font-size: 12px; cursor: pointer; }.model-channel-current input { accent-color: var(--accent); }.model-channel-delete { margin-left: auto; color: var(--fg-4); }.model-channel-delete:hover { color: #c2413b; background: rgba(220, 38, 38, .09); }.model-channel-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; padding: 18px; }.model-channel-fields label { min-width: 0; display: grid; gap: 6px; color: var(--fg-3); font-size: 12px; }.model-channel-fields input, .model-channel-fields textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--border); border-radius: 5px; outline: none; background: var(--bg); color: var(--fg); font: inherit; font-size: 13px; }.model-channel-fields input { height: 34px; padding: 0 9px; }.model-channel-fields textarea { padding: 8px 9px; line-height: 1.5; resize: vertical; }.model-channel-fields input:focus, .model-channel-fields textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 12%, transparent); }.model-channel-models { grid-column: 1 / -1; }.model-channel-add { width: 100%; height: 38px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; border: 1px dashed var(--border); border-radius: 6px; background: transparent; color: var(--fg-3); font: inherit; font-size: 13px; cursor: pointer; }.model-channel-add:hover { color: var(--accent); border-color: var(--accent); background: var(--accent-bg); }.model-channel-add svg { width: 16px; height: 16px; }
.model-channel-models-label { display: flex; align-items: center; min-height: 18px; }.model-channel-sync { margin-left: auto; display: inline-flex; align-items: center; gap: 5px; border: 0; border-radius: 4px; padding: 2px 5px; background: transparent; color: var(--fg-4); font: inherit; font-size: 12px; cursor: pointer; }.model-channel-sync:hover:not(:disabled) { background: var(--bg-3); color: var(--accent); }.model-channel-sync:disabled { cursor: not-allowed; opacity: .48; }.model-channel-sync svg { width: 14px; height: 14px; }.model-channel-spin { animation: model-channel-spin .8s linear infinite; } @keyframes model-channel-spin { to { transform: rotate(360deg); } }
.model-pricing { min-width: 0; margin-top: 28px; border-top: 1px solid var(--border); padding-top: 22px; }.model-pricing-header { display: flex; align-items: center; margin-bottom: 12px; }.model-pricing-header h2 { margin: 0; color: var(--fg); font-size: 14px; font-weight: 600; }.model-pricing-header p { margin: 3px 0 0; color: var(--fg-4); font-size: 12px; }.model-pricing-empty { height: 72px; display: grid; place-items: center; border: 1px dashed var(--border); border-radius: 6px; color: var(--fg-4); font-size: 12px; }.model-pricing-table-wrap { width: 100%; min-width: 0; overflow-x: auto; overflow-y: visible; border: 1px solid var(--border); border-radius: 7px; }.model-pricing-table { width: 100%; border-collapse: collapse; table-layout: fixed; }.model-pricing-table th, .model-pricing-table td { height: 44px; padding: 0 12px; border-bottom: 1px solid var(--border); text-align: left; }.model-pricing-table tr:last-child td { border-bottom: 0; }.model-pricing-table th { height: 36px; background: var(--bg-2); color: var(--fg-3); font-size: 11px; font-weight: 500; }.model-pricing-table th:first-child { width: 40%; }.model-pricing-name { overflow: hidden; color: var(--fg-2); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.model-pricing-table input { width: 100%; height: 30px; box-sizing: border-box; border: 1px solid var(--border); border-radius: 5px; outline: none; padding: 0 8px; background: var(--bg); color: var(--fg); font: inherit; font-size: 12px; }.model-pricing-table input:focus { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 12%, transparent); }
@media (max-width: 640px) { .model-channels-header { padding: 0 14px; }.model-channels-header p { display: none; }.model-channels-body { width: 100%; padding: 14px 14px 32px; }.model-channel-fields { grid-template-columns: minmax(0, 1fr); gap: 12px; padding: 14px; }.model-channel-models { grid-column: auto; }.model-pricing-table { min-width: 620px; } }
</style>
