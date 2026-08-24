<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <div>
        <h1>用量</h1>
        <p>查看 Token 用量与活跃度统计。</p>
      </div>
      <div class="dashboard-actions">
        <span class="range-hint">近一年</span>
        <button class="refresh-button" type="button" :disabled="loading" title="刷新" @click="fetchData">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spin: loading }">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/><path d="M21 3v6h-6"/>
          </svg>
          <span>刷新</span>
        </button>
      </div>
    </header>

    <div v-if="loading && !data" class="dashboard-state">加载中...</div>
    <div v-else-if="!data" class="dashboard-state">暂无数据</div>

    <template v-else>
      <section class="summary-cards">
        <div v-for="card in summaryCards" :key="card.label" class="summary-card">
          <div class="summary-card-icon" :class="card.color" v-html="card.icon"></div>
          <div class="summary-card-value">{{ card.value }}</div>
          <div class="summary-card-label">{{ card.label }}</div>
        </div>
      </section>

      <section class="panel activity-panel">
        <div class="panel-heading">
          <div>
            <h2>Token 活跃度</h2>
            <p>{{ dateRange }}</p>
          </div>
          <button class="panel-refresh" type="button" :disabled="loading" @click="fetchData">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spin: loading }">
              <path d="M21 12a9 9 0 1 1-6.219-8.56"/><path d="M21 3v6h-6"/>
            </svg>
            刷新
          </button>
        </div>
        <div class="heatmap-wrap">
          <div ref="heatmapScroll" class="heatmap-scroll">
            <div class="month-labels" :style="{ gridTemplateColumns: `repeat(${heatmapCols}, minmax(16px, 22px))`, minWidth: heatmapMinWidth + 'px' }">
              <span v-for="month in heatmapMonths" :key="month.key" :style="{ gridColumnStart: month.col + 1 }">{{ month.label }}</span>
            </div>
            <div class="heatmap-grid" :style="{ gridTemplateRows: `repeat(7, auto)`, gridTemplateColumns: `repeat(${heatmapCols}, minmax(16px, 22px))`, minWidth: heatmapMinWidth + 'px' }">
              <span v-for="(cell, index) in heatmapCells" :key="index" class="heatmap-cell" :class="cell.level" :title="cell.title"></span>
            </div>
          </div>
          <div class="heatmap-legend"><span>少</span><i class="level-0"></i><i class="level-1"></i><i class="level-2"></i><i class="level-3"></i><i class="level-4"></i><span>多</span></div>
        </div>
      </section>

      <section class="metric-grid">
        <div class="panel metric-panel">
          <h2>活跃度指标</h2>
          <div class="metric-values">
            <div><span>总会话数</span><strong>{{ data.totalRequests }}</strong></div>
            <div><span>总运行数</span><strong>{{ data.totalRequests }}</strong></div>
            <div><span>平均 tokens / 运行</span><strong>{{ formatCompact(averageTokens) }}</strong></div>
            <div><span>最长活跃天数</span><strong>{{ data.activeDays }} 天</strong></div>
          </div>
        </div>
        <div class="panel metric-panel">
          <h2>Token 指标</h2>
          <div class="metric-values">
            <div><span>输入 tokens</span><strong>{{ formatCompact(data.totalPromptTokens) }}</strong></div>
            <div><span>输出 tokens</span><strong>{{ formatCompact(data.totalCompletionTokens) }}</strong></div>
            <div><span>缓存读取 tokens</span><strong>{{ formatCompact(data.totalCacheHit) }}</strong></div>
            <div><span>缓存写入 tokens</span><strong>0</strong></div>
          </div>
        </div>
        <div class="panel metric-panel">
          <h2>Cache 指标</h2>
          <div class="metric-values two-columns">
            <div><span>缓存 tokens</span><strong>{{ formatCompact(data.totalCacheHit) }}</strong></div>
            <div><span>Cache 命中率</span><strong>{{ cacheRate }}%</strong></div>
          </div>
        </div>
      </section>

      <section class="bottom-grid">
          <div class="panel cost-panel">
            <div class="panel-heading cost-heading">
              <div>
                <h2>费用预估</h2>
                <p>根据当前模型价格估算</p>
              </div>
            </div>
            <div class="cost-stats">
              <div class="cost-stat">
                <span>预估总费用</span>
                <strong>¥{{ formatCost(data.totalCost) }}</strong>
              </div>
              <div class="cost-stat">
                <span>最高单日</span>
                <strong>¥{{ formatCost(maxDailyCost) }}</strong>
              </div>
            </div>
          </div>
        <div class="panel models-panel">
          <div class="panel-heading compact-heading">
            <h2>模型用量</h2>
            <div class="pagination"><span>{{ modelsPage }} / {{ totalModelPages }}</span><button type="button" :disabled="modelsPage <= 1" title="上一页" @click="prevModelsPage"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg></button><button type="button" :disabled="modelsPage >= totalModelPages" title="下一页" @click="nextModelsPage"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg></button></div>
          </div>
          <div v-if="visibleModels.length" class="model-list">
            <div v-for="model in visibleModels" :key="model.model" class="model-item">
              <div class="model-main"><strong>{{ model.model }}</strong><small>{{ model.model === 'unknown' ? '默认渠道' : 'model provider' }}</small></div>
              <div class="model-total"><strong>{{ formatCompact(model.totalTokens) }}</strong><small>总 tokens · {{ model.requests }} 次</small></div>
              <div class="model-breakdown"><span>输入 tokens <b>{{ formatCompact(model.promptTokens) }}</b></span><span>输出 tokens <b>{{ formatCompact(model.completionTokens) }}</b></span><span>缓存读取 <b>{{ formatCompact(model.cacheHit) }}</b></span><span>缓存写入 <b>0</b></span></div>
            </div>
          </div>
          <div v-else class="model-empty">暂无模型用量数据</div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { configAPI } from '../services/api'

const data = ref(null)
const loading = ref(false)

const totalTokens = computed(() => Number(data.value?.totalPromptTokens || 0) + Number(data.value?.totalCompletionTokens || 0))
const averageTokens = computed(() => data.value?.totalRequests ? totalTokens.value / data.value.totalRequests : 0)
const cacheRate = computed(() => {
  const total = Number(data.value?.totalCacheHit || 0) + Number(data.value?.totalCacheMiss || 0)
  return total ? ((Number(data.value.totalCacheHit || 0) / total) * 100).toFixed(0) : '0'
})
const dailyStats = computed(() => data.value?.dailyStats || [])
const maxDailyCost = computed(() => Math.max(0, ...dailyStats.value.map(item => Number(item.cost || 0))))

// 模型用量分页（每页 4 个，左右按钮切换）
const MODELS_PER_PAGE = 4
const modelsPage = ref(1)
const totalModelPages = computed(() => Math.max(1, Math.ceil((data.value?.modelStats || []).length / MODELS_PER_PAGE)))
const visibleModels = computed(() => {
  const all = data.value?.modelStats || []
  const start = (modelsPage.value - 1) * MODELS_PER_PAGE
  return all.slice(start, start + MODELS_PER_PAGE)
})
function prevModelsPage() { if (modelsPage.value > 1) modelsPage.value-- }
function nextModelsPage() { if (modelsPage.value < totalModelPages.value) modelsPage.value++ }
watch(dailyStats, async () => {
  modelsPage.value = 1
  await nextTick()
  scrollToLatest()
})

// 热力图默认定位到最右侧（最新数据），resize/数据变化时保持一致
const heatmapScroll = ref(null)
function scrollToLatest() {
  if (heatmapScroll.value) heatmapScroll.value.scrollLeft = heatmapScroll.value.scrollWidth
}
onMounted(() => window.addEventListener('resize', scrollToLatest))
onBeforeUnmount(() => window.removeEventListener('resize', scrollToLatest))
const dateRange = computed(() => {
  const values = dailyStats.value
  if (!values.length) return ''
  return `${values[0].date} - ${values[values.length - 1].date}`
})

const summaryCards = computed(() => [
  { label: '累计 tokens', value: formatCompact(totalTokens.value), color: 'blue', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 19V5M4 19h16M8 16v-5M12 16V7M16 16v-9"/></svg>' },
  { label: '单日峰值 tokens', value: formatCompact(Math.max(0, ...dailyStats.value.map(item => item.totalTokens || 0))), color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 12 4-4 3 3 5-7 6 6"/></svg>' },
  { label: '活跃天数', value: `${data.value?.activeDays || 0} 天`, color: 'purple', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/></svg>' },
  { label: '缓存命中率', value: `${cacheRate.value}%`, color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3c4 4 6 6.5 6 10a6 6 0 0 1-12 0c0-2.3 1-4.2 3-6"/><path d="M10 15c.4 1 1.1 1.5 2 1.5 1.2 0 2-.8 2-2"/></svg>' },
  { label: '总请求次数', value: formatCompact(data.value?.totalRequests || 0), color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>' }
])

const CELL = { size: 16, gap: 4 }
const weekdayOffset = (dateStr) => {
  const d = new Date(`${dateStr}T00:00:00`)
  return (d.getDay() + 6) % 7
}
const heatmapCols = computed(() => {
  const stats = dailyStats.value
  if (!stats.length) return 0
  return Math.ceil((weekdayOffset(stats[0].date) + stats.length) / 7)
})
// 最小宽度：每列 16px + 4px 间距，容器更窄时横向滚动；列宽上限 22px，多余宽度由列间隙吸收
const heatmapMinWidth = computed(() => Math.max(0, heatmapCols.value * (CELL.size + CELL.gap) - CELL.gap))

const heatmapCells = computed(() => {
  const stats = dailyStats.value
  if (!stats.length) return []
  const offset = weekdayOffset(stats[0].date)
  const max = Math.max(1, ...stats.map(item => item.totalTokens || 0))
  const cells = Array.from({ length: offset }, () => ({ level: 'empty', title: '' }))
  stats.forEach(item => {
    const ratio = (item.totalTokens || 0) / max
    const level = item.totalTokens <= 0 ? 'level-0' : ratio < .2 ? 'level-1' : ratio < .45 ? 'level-2' : ratio < .7 ? 'level-3' : 'level-4'
    cells.push({ level, title: `${item.date} · ${formatCompact(item.totalTokens || 0)} tokens` })
  })
  return cells
})

const heatmapMonths = computed(() => {
  const stats = dailyStats.value
  if (!stats.length) return []
  const offset = weekdayOffset(stats[0].date)
  const months = []
  stats.forEach((item, index) => {
    const date = new Date(`${item.date}T00:00:00`)
    if (date.getDate() <= 7 || index === 0) {
      const key = `${date.getFullYear()}-${date.getMonth()}`
      if (!months.some(month => month.key === key)) {
        months.push({ key, label: `${date.getMonth() + 1}月`, col: Math.floor((offset + index) / 7) })
      }
    }
  })
  // 相邻月份列距不足 2 列时（开头月份不足一个月），隐藏前一个，从完整月份开始显示
  const result = []
  for (let i = 0; i < months.length; i++) {
    const next = months[i + 1]
    if (next && next.col - months[i].col < 2) continue
    result.push(months[i])
  }
  return result
})

function formatCompact(value) {
  const n = Number(value || 0)
  if (n >= 100000000) return `${(n / 100000000).toFixed(1)}亿`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}千`
  return Math.round(n).toLocaleString('zh-CN')
}

function formatCost(value) {
  const n = Number(value || 0)
  return n < 0.01 && n > 0 ? n.toFixed(4) : n.toFixed(2)
}

async function fetchData() {
  loading.value = true
  try {
    const response = await configAPI.getDashboard(365)
    if (response.success) data.value = response.data
  } catch (error) {
    console.error('获取数据面板失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
defineExpose({ refresh: fetchData })
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 28px; color: var(--fg); font-size: 14px; }
.dashboard-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; }
.dashboard-header h1 { margin: 0; color: var(--fg); font-size: 25px; line-height: 1.15; letter-spacing: -.02em; }
.dashboard-header p { margin: 8px 0 0; color: var(--fg-3); font-size: 15px; }
.dashboard-actions { display: flex; gap: 8px; align-items: center; }
.refresh-button { height: 34px; border: 1px solid var(--border); border-radius: 7px; color: var(--fg-2); background: var(--bg); font: inherit; cursor: pointer; }
.range-hint { color: var(--fg-3); font-size: 13px; }
.refresh-button { display: flex; align-items: center; gap: 6px; padding: 0 10px; font-size: 13px; }
.refresh-button:hover:not(:disabled), .panel-refresh:hover:not(:disabled) { color: var(--accent); border-color: var(--accent); }
.refresh-button:disabled, .panel-refresh:disabled { opacity: .6; cursor: wait; }
.dashboard-state { padding: 70px 0; text-align: center; color: var(--fg-3); }
.summary-cards { display: grid; grid-template-columns: repeat(5, 1fr); border: 1px solid var(--border); border-radius: 12px; overflow: hidden; background: var(--bg); }
.summary-card { min-height: 106px; padding: 18px 20px; border-right: 1px solid var(--border); }
.summary-card:last-child { border-right: 0; }
.summary-card-icon { width: 21px; height: 21px; margin-bottom: 15px; color: var(--fg-3); }
.summary-card-icon svg { width: 100%; height: 100%; }
.summary-card-icon.blue { color: #667085; }.summary-card-icon.orange { color: #a66b49; }.summary-card-icon.purple { color: #766c8e; }.summary-card-icon.green { color: #4e806e; }.summary-card-icon.gray { color: #71717a; }
.summary-card-value { font-size: 25px; font-weight: 600; line-height: 1; letter-spacing: -.03em; }
.summary-card-label { margin-top: 10px; color: var(--fg-3); font-size: 12px; }
.panel { border: 1px solid var(--border); border-radius: 12px; background: var(--bg); }
.activity-panel { padding: 25px 24px 22px; }
.panel-heading { display: flex; justify-content: space-between; align-items: flex-start; }
.panel-heading h2, .metric-panel h2, .cost-panel h2, .models-panel h2 { margin: 0; font-size: 15px; font-weight: 600; }
.panel-heading p { margin: 5px 0 0; color: var(--fg-3); font-size: 13px; }
.panel-refresh { display: flex; align-items: center; gap: 7px; padding: 3px 0; border: 0; background: transparent; color: var(--fg-2); font: inherit; cursor: pointer; }
.heatmap-wrap { margin-top: 25px; }
.heatmap-scroll { overflow-x: auto; padding-bottom: 2px; }
.month-labels { display: grid; align-items: end; height: 20px; width: 100%; justify-content: space-between; color: var(--fg-3); font-size: 12px; }
.month-labels span { white-space: nowrap; overflow: visible; }
.heatmap-grid { display: grid; grid-auto-flow: column; gap: 4px; width: 100%; justify-content: space-between; margin-top: 2px; }
.heatmap-cell { aspect-ratio: 1 / 1; border-radius: 4px; background: var(--bg-3); }
.heatmap-cell.empty { visibility: hidden; }.heatmap-cell.level-0 { background: color-mix(in srgb, var(--fg-4) 8%, var(--bg)); }.heatmap-cell.level-1 { background: #dce5f0; }.heatmap-cell.level-2 { background: #b6c7db; }.heatmap-cell.level-3 { background: #8aa5c4; }.heatmap-cell.level-4 { background: #4a6c94; }
.heatmap-legend { display: flex; justify-content: flex-end; align-items: center; gap: 5px; margin-top: 12px; color: var(--fg-4); font-size: 11px; }.heatmap-legend i { width: 12px; height: 12px; border-radius: 3px; }.heatmap-legend .level-0 { background: color-mix(in srgb, var(--fg-4) 8%, var(--bg)); }.heatmap-legend .level-1 { background: #dce5f0; }.heatmap-legend .level-2 { background: #b6c7db; }.heatmap-legend .level-3 { background: #8aa5c4; }.heatmap-legend .level-4 { background: #4a6c94; }
.metric-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 24px; }.metric-panel { min-height: 182px; padding: 25px 24px; }.metric-values { display: grid; grid-template-columns: 1fr 1fr; gap: 27px 20px; margin-top: 24px; }.metric-values span { display: block; color: var(--fg-3); font-size: 14px; }.metric-values strong { display: block; margin-top: 9px; font-size: 22px; font-weight: 500; }.metric-values.two-columns { grid-template-columns: 1fr 1fr; }
.bottom-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; grid-auto-rows: 420px; }.cost-panel, .models-panel { height: 100%; padding: 25px 24px; box-sizing: border-box; }.cost-panel { display: flex; flex-direction: column; justify-content: flex-start; gap: 24px; }.cost-heading { align-items: center; }.cost-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }.cost-stat { padding: 20px 22px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); }.cost-stat span { display: block; color: var(--fg-3); font-size: 13px; }.cost-stat strong { display: block; margin-top: 12px; color: var(--fg); font-size: 26px; font-weight: 600; letter-spacing: -.02em; }.compact-heading { align-items: center; }.pagination { display: flex; align-items: center; gap: 2px; color: var(--fg-3); font-size: 12px; }.pagination span { margin-right: 4px; }.pagination button { display: flex; align-items: center; justify-content: center; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--fg-3); cursor: pointer; }.pagination button:hover:not(:disabled) { color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, transparent); }.pagination button:disabled { opacity: .35; cursor: not-allowed; }.model-list { margin-top: 25px; }.model-item { padding: 12px 0; border-bottom: 1px solid var(--border); }.model-item:last-child { border-bottom: 0; }.model-main { display: inline-flex; flex-direction: column; gap: 3px; max-width: 60%; }.model-main strong { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.model-main small, .model-total small { color: var(--fg-3); font-size: 11px; }.model-total { float: right; display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }.model-total strong { font-size: 15px; }.model-breakdown { display: flex; justify-content: space-between; gap: 10px; clear: both; padding-top: 10px; color: var(--fg-3); font-size: 11px; }.model-breakdown b { margin-left: 7px; color: var(--fg-2); font-size: 12px; font-weight: 500; }.model-empty { padding: 45px 0; text-align: center; color: var(--fg-3); }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 800px) { .summary-cards { grid-template-columns: repeat(2, 1fr); }.summary-card:nth-child(2n) { border-right: 0; }.summary-card:last-child { grid-column: span 2; border-top: 1px solid var(--border); }.metric-grid, .bottom-grid { grid-template-columns: 1fr; grid-auto-rows: auto; }.dashboard { gap: 16px; }.activity-panel, .metric-panel, .cost-panel, .models-panel { padding: 18px; }.model-breakdown { flex-wrap: wrap; } }
</style>
