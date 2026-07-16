<template>
  <div class="dashboard">
    <div class="dashboard-toolbar">
      <div class="dashboard-toolbar-actions">
        <select v-model="days" class="days-select" @change="fetchData">
          <option :value="7">近 7 天</option>
          <option :value="14">近 14 天</option>
          <option :value="30">近 30 天</option>
        </select>
        <button class="dashboard-refresh" type="button" @click="fetchData" :disabled="loading" :title="loading ? '刷新中...' : '刷新'">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               :class="{ 'spin': loading }">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </button>
      </div>
    </div>

    <div v-if="loading && !data" class="dashboard-loading">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>
    <div v-else-if="!data" class="dashboard-empty">暂无数据</div>

    <template v-else>
      <!-- 统计卡片 -->
      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-icon tokens">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <div class="summary-info">
            <div class="summary-value">{{ fmtNum(totalTokens) }}</div>
            <div class="summary-label">总 Token</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-icon cost">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
          </div>
          <div class="summary-info">
            <div class="summary-value">¥{{ fmtCost(data.totalCost) }}</div>
            <div class="summary-label">总费用</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-icon days">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          <div class="summary-info">
            <div class="summary-value">{{ data.activeDays }}</div>
            <div class="summary-label">活跃天数</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="summary-icon requests">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="summary-info">
            <div class="summary-value">{{ fmtNum(data.totalRequests) }}</div>
            <div class="summary-label">总请求数</div>
          </div>
        </div>
      </div>

      <!-- 每日趋势图 -->
      <div class="section">
        <div class="section-title">
          <span>每日 Token 用量趋势</span>
          <span class="section-sub">缓存 / 非缓存输入 / 输出</span>
        </div>
        <div class="chart-container">
          <div class="chart-y-axis">
            <span v-for="tick in yTicks" :key="tick">{{ fmtNum(tick) }}</span>
          </div>
          <div class="chart-bars">
            <div v-for="d in data.dailyStats" :key="d.date" class="chart-bar-group">
              <div class="bar-wrapper">
                <div class="input-group"
                     :title="'输入: ' + fmtNum(d.promptTokens) + '\n缓存命中: ' + fmtNum(d.cacheHit) + '\n非缓存: ' + fmtNum(Math.max(0, d.promptTokens - d.cacheHit))">
                  <div class="bar cache-hit" :style="{ height: barHeight(d.cacheHit) + '%' }"></div>
                  <div class="bar non-cache" :style="{ height: barHeight(Math.max(0, d.promptTokens - d.cacheHit)) + '%', bottom: barHeight(d.cacheHit) + '%' }"></div>
                </div>
                <div class="bar completion" :style="{ height: barHeight(d.completionTokens) + '%' }"
                     :title="'输出: ' + fmtNum(d.completionTokens)"></div>
              </div>
              <div class="bar-label">{{ shortDate(d.date) }}</div>
            </div>
          </div>
        </div>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot cache-hit"></span>缓存命中</span>
          <span class="legend-item"><span class="legend-dot non-cache"></span>非缓存输入</span>
          <span class="legend-item"><span class="legend-dot completion"></span>输出 Token</span>
        </div>
      </div>

      <!-- 每日费用趋势 -->
      <div class="section">
        <div class="section-title">
          <span>每日费用趋势</span>
          <span class="section-sub">¥ CNY</span>
        </div>
        <div class="chart-container">
          <div class="chart-y-axis">
            <span v-for="tick in costTicks" :key="tick">¥{{ fmtCost(tick) }}</span>
          </div>
          <div class="chart-bars">
            <div v-for="d in data.dailyStats" :key="d.date + '_cost'" class="chart-bar-group">
              <div class="bar-wrapper single">
                <div class="bar cost-bar" :style="{ height: costBarHeight(d.cost) + '%' }"
                     :title="'费用: ¥' + fmtCost(d.cost)"></div>
              </div>
              <div class="bar-label">{{ shortDate(d.date) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 模型用量排行 -->
      <div class="section" v-if="data.modelStats.length">
        <div class="section-title">
          <span>模型用量排行</span>
          <span class="section-sub">按 Token 总量排序</span>
        </div>
        <div class="model-table">
          <div class="model-row model-header">
            <span class="col-model">模型</span>
            <span class="col-num">输入</span>
            <span class="col-num">缓存</span>
            <span class="col-num">输出</span>
            <span class="col-num">总计</span>
            <span class="col-num">命中</span>
            <span class="col-num">费用</span>
            <span class="col-num">请求</span>
          </div>
          <div v-for="m in data.modelStats" :key="m.model" class="model-row">
            <span class="col-model" :title="priceTooltip(m.model)">{{ shortModel(m.model) }}</span>
            <span class="col-num">{{ fmtNum(m.promptTokens) }}</span>
            <span class="col-num cache-col-token">{{ fmtNum(m.cacheHit) }}</span>
            <span class="col-num">{{ fmtNum(m.completionTokens) }}</span>
            <span class="col-num total">{{ fmtNum(m.totalTokens) }}</span>
            <span class="col-num">{{ cacheRate(m) }}%</span>
            <span class="col-num cost">¥{{ fmtCost(m.cost) }}</span>
            <span class="col-num">{{ m.requests }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { configAPI } from '../services/api'

const data = ref(null)
const loading = ref(false)
const days = ref(7)

const totalTokens = computed(() => {
  if (!data.value) return 0
  return data.value.totalPromptTokens + data.value.totalCompletionTokens
})

const maxDailyToken = computed(() => {
  if (!data.value?.dailyStats) return 1
  let max = 0
  for (const d of data.value.dailyStats) {
    max = Math.max(max, d.promptTokens, d.completionTokens)
  }
  return max || 1
})

const maxDailyCost = computed(() => {
  if (!data.value?.dailyStats) return 1
  let max = 0
  for (const d of data.value.dailyStats) {
    max = Math.max(max, d.cost)
  }
  return max || 0.0001
})

const yTicks = computed(() => {
  const max = maxDailyToken.value
  const step = niceStep(max, 4)
  const ticks = []
  for (let v = 0; v <= max + step; v += step) {
    ticks.push(v)
  }
  return ticks.slice(-5)
})

const costTicks = computed(() => {
  const max = maxDailyCost.value
  const step = niceStep(max, 4)
  const ticks = []
  for (let v = 0; v <= max + step; v += step) {
    ticks.push(v)
  }
  return ticks.slice(-5)
})

function niceStep(max, targetTicks) {
  if (max <= 0) return 1
  const rough = max / targetTicks
  const pow = Math.pow(10, Math.floor(Math.log10(rough)))
  const norm = rough / pow
  let nice
  if (norm <= 1) nice = 1
  else if (norm <= 2) nice = 2
  else if (norm <= 5) nice = 5
  else nice = 10
  return nice * pow
}

function barHeight(val) {
  if (val <= 0) return 0
  const max = maxDailyToken.value
  const pct = (val / max) * 100
  return Math.max(pct, 3)
}

function costBarHeight(val) {
  const max = maxDailyCost.value
  if (max === 0) return 0
  return Math.max(0, (val / max) * 100)
}

function fmtNum(n) {
  if (n === undefined || n === null) return '0'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 10_000) return (n / 1_000).toFixed(1) + 'K'
  if (n >= 1_000) return (n / 1_000).toFixed(2) + 'K'
  return n.toString()
}

function fmtCost(c) {
  if (c === undefined || c === null) return '0'
  if (c < 0.01) return c.toFixed(4)
  if (c < 1) return c.toFixed(3)
  return c.toFixed(2)
}

function shortDate(dateStr) {
  // "2024-01-15" -> "01/15"
  const parts = dateStr.split('-')
  return parts[1] + '/' + parts[2]
}

function shortModel(model) {
  if (!model) return 'unknown'
  if (model.length <= 30) return model
  // Try to extract the meaningful part
  const slashIdx = model.lastIndexOf('/')
  if (slashIdx >= 0 && slashIdx < model.length - 1) {
    return model.substring(slashIdx + 1)
  }
  return model.substring(0, 28) + '…'
}

function cacheRate(m) {
  if (m.promptTokens === 0) return '0.0'
  return ((m.cacheHit / m.promptTokens) * 100).toFixed(1)
}

function priceTooltip(model) {
  const p = data.value?.modelPrices?.[model]
  if (!p) return model
  const parts = []
  if (p.input != null) parts.push('输入: ¥' + p.input + '/M tokens')
  if (p.cache != null) parts.push('缓存: ¥' + p.cache + '/M tokens')
  if (p.output != null) parts.push('输出: ¥' + p.output + '/M tokens')
  if (parts.length === 0) return model
  return model + '\n\n' + parts.join('\n')
}

async function fetchData() {
  loading.value = true
  try {
    const r = await configAPI.getDashboard(days.value)
    if (r.success) {
      data.value = r.data
      message.success('已刷新')
    } else {
      message.error('刷新失败')
    }
  } catch (e) {
    console.error('获取数据面板失败:', e)
    message.error('请求失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
defineExpose({ refresh: fetchData })
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--fg);
  font-size: 13px;
}

.dashboard-toolbar {
  display: flex;
  align-items: center;
  min-height: 30px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.dashboard-toolbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.days-select {
  height: 30px;
  font-size: 12px;
  padding: 0 8px;
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--fg-2);
  outline: none;
  cursor: pointer;
}

.days-select:hover,
.days-select:focus { border-color: color-mix(in srgb, var(--accent) 45%, var(--border)); }

.dashboard-refresh {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.dashboard-refresh:hover:not(:disabled) {
  border-color: var(--border);
  background: var(--bg);
  color: var(--accent);
}

.dashboard-refresh:disabled { cursor: wait; opacity: 0.6; }

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.dashboard-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--fg-3);
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.dashboard-empty {
  text-align: center;
  color: var(--fg-3);
  padding: 40px 0;
}

/* 统计卡片 */
.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.summary-card {
  min-height: 72px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.summary-icon.tokens {
  background: color-mix(in srgb, var(--blue) 15%, transparent);
  color: var(--blue);
}

.summary-icon.cost {
  background: color-mix(in srgb, var(--accent) 15%, transparent);
  color: var(--accent);
}

.summary-icon.days {
  background: color-mix(in srgb, var(--green) 15%, transparent);
  color: var(--green);
}

.summary-icon.requests {
  background: color-mix(in srgb, var(--yellow) 15%, transparent);
  color: var(--yellow);
}

.summary-value {
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--fg);
}

.summary-label {
  font-size: 11px;
  color: var(--fg-3);
  margin-top: 2px;
}

/* 区块 */
.section {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 14px;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 12px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.section-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--fg-4);
}

/* 图表 */
.chart-container {
  display: flex;
  gap: 8px;
  height: 150px;
  padding-bottom: 24px;
  position: relative;
}

.chart-y-axis {
  display: flex;
  flex-direction: column-reverse;
  justify-content: space-between;
  min-width: 40px;
  text-align: right;
  font-size: 10px;
  color: var(--fg-4);
  padding-bottom: 2px;
}

.chart-bars {
  flex: 1;
  display: flex;
  align-items: stretch;
  gap: 4px;
  border-left: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  padding: 0 6px 0 8px;
  position: relative;
  overflow: hidden;
}

.chart-bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  position: relative;
}

.bar-wrapper {
  flex: 1;
  width: 100%;
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
}

.bar-wrapper.single {
  justify-content: center;
}

.input-group {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 45%;
  height: 100%;
  cursor: default;
}

.bar {
  width: 45%;
  min-height: 1px;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s ease;
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
}

.bar.cache-hit {
  background: var(--green);
  opacity: 0.85;
  width: 100%;
  border-radius: 0;
}

.bar.non-cache {
  background: var(--blue);
  opacity: 0.85;
  width: 100%;
  border-radius: 2px 2px 0 0;
}

.bar.completion {
  background: var(--accent);
  opacity: 0.85;
  transform: translateX(0);
}

.bar.cost-bar {
  background: var(--yellow);
  width: 60%;
  opacity: 0.85;
  transform: translateX(-50%);
}

.bar-label {
  font-size: 10px;
  color: var(--fg-4);
  margin-top: 4px;
  white-space: nowrap;
  text-align: center;
}

.chart-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--fg-3);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend-dot.cache-hit {
  background: var(--green);
  opacity: 0.85;
}

.legend-dot.non-cache {
  background: var(--blue);
  opacity: 0.85;
}

.legend-dot.completion {
  background: var(--accent);
  opacity: 0.85;
}

/* 模型表格 */
.model-table {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
}

.model-row {
  display: grid;
  grid-template-columns: 1fr 70px 70px 70px 70px 50px 70px 45px;
  gap: 4px;
  padding: 10px 12px;
  background: var(--bg);
  font-size: 12px;
  align-items: center;
}

.model-header {
  font-weight: 600;
  font-size: 11px;
  color: var(--fg-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.col-model {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.col-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--fg-2);
}

.col-num.total {
  font-weight: 600;
  color: var(--fg);
}

.col-num.cost {
  color: var(--accent);
}

.cache-col-token {
  color: var(--blue);
}

@media (max-width: 700px) {
  .summary-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .model-row {
    grid-template-columns: 1fr 55px 55px 55px 55px 40px 55px 35px;
  }
  .model-row .col-num:nth-child(n+6) {
    display: none;
  }
  .model-header .col-num:nth-child(n+6) {
    display: none;
  }
}
</style>
