<template>
  <div class="gl">
    <div class="gl-head">
      <span class="gl-title">{{ data?.title || 'Goal' }}</span>
      <span class="gl-badge" :class="data?.status?.toLowerCase()">{{ statusText }}</span>
      <span class="gl-progress" v-if="data?.totalSteps">{{ data.doneSteps }}/{{ data.totalSteps }}</span>
    </div>
    <div v-if="data?.totalSteps" class="gl-progress-track" aria-hidden="true">
      <span :style="{ width: `${progressPercent}%` }"></span>
    </div>

    <div v-if="data?.blockedReason" class="gl-blocked">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>
      </svg>
      {{ data.blockedReason }}
    </div>

    <div class="gl-body">
      <div v-for="(step, i) in data?.steps" :key="step.index" class="gl-row"
           :class="[step.status?.toLowerCase(), { current: isCurrent(step, i) }]">
        <div class="gl-track">
          <div class="gl-dot" :class="step.status?.toLowerCase()"></div>
          <div v-if="i < data.steps.length - 1" class="gl-line" :class="step.status === 'DONE' || step.status === 'SKIPPED' ? 'done' : ''"></div>
        </div>
        <div class="gl-body-text">
          <div class="gl-desc">{{ step.description }}</div>
          <div class="gl-meta" v-if="step.evidence">
            <span class="gl-evidence" :class="{ 'gl-err': step.status === 'BLOCKED' }">{{ step.evidence }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="data?.verifyCommand" class="gl-verify">
      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
      </svg>
      {{ data.verifyCommand }}
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'

const props = defineProps({ data: Object })

const STATUS_MAP = {
  ACTIVE: '进行中',
  PAUSED: '暂停',
  BLOCKED: '阻塞',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const statusText = computed(() => STATUS_MAP[props.data?.status] || props.data?.status || '')
const progressPercent = computed(() => {
  const total = Number(props.data?.totalSteps) || 0
  const done = Number(props.data?.doneSteps) || 0
  return total ? Math.min(100, Math.max(0, done / total * 100)) : 0
})

const isCurrent = (step, index) => {
  if (step.status === 'IN_PROGRESS') return true
  if (step.status !== 'PENDING') return false
  // 第一个 PENDING 且前面没有 IN_PROGRESS 的视为当前
  const steps = props.data?.steps || []
  return !steps.some(s => s.status === 'IN_PROGRESS') &&
    steps.findIndex(s => s.status === 'PENDING') === index
}
</script>

<style scoped>
.gl {
  font-size: 12px;
  color: var(--fg);
}

.gl-head {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.gl-title {
  font-weight: 500;
  font-size: 12px;
  color: var(--fg);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gl-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-3);
  color: var(--fg-3);
  line-height: 1.5;
}
.gl-badge.active    { background: var(--accent-bg); color: var(--accent); }
.gl-badge.completed { background: var(--green-bg, #e8f5e9); color: var(--green, #2e7d32); }
.gl-badge.cancelled { background: var(--bg-3); color: var(--fg-4); }
.gl-badge.blocked   { background: var(--red-bg, #ffebee); color: var(--red, #c62828); }
.gl-badge.paused    { background: var(--yellow-bg, #fff8e1); color: var(--yellow, #f57f17); }

.gl-progress {
  font-size: 10px;
  color: var(--fg-4);
  font-family: var(--mono);
}

.gl-progress-track {
  height: 3px;
  margin: 7px 0 9px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--bg-3);
}

.gl-progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 0.25s ease;
}

.gl-blocked {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 11px;
  color: var(--red, #c62828);
  background: var(--red-bg, #ffebee);
  border-radius: 4px;
  padding: 5px 8px;
  margin-bottom: 8px;
  line-height: 1.4;
  word-break: break-word;
}
.gl-blocked svg { flex-shrink: 0; margin-top: 1px; }

.gl-body {
  display: flex;
  flex-direction: column;
}

.gl-row {
  display: flex;
  gap: 8px;
  opacity: 0.38;
  transition: opacity 0.2s;
}
.gl-row.current,
.gl-row.done,
.gl-row.in_progress,
.gl-row.blocked { opacity: 1; }
.gl-row.skipped { opacity: 0.35; }

.gl-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  flex-shrink: 0;
}

.gl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-2);
  flex-shrink: 0;
  margin-top: 4px;
  transition: background 0.2s;
}
.gl-dot.done    { background: var(--green, #2e7d32); }
.gl-dot.skipped { background: var(--fg-4); }
.gl-dot.blocked { background: var(--red, #c62828); }
.gl-dot.in_progress { background: var(--accent); }

.gl-row.current .gl-dot {
  width: 10px;
  height: 10px;
  margin-top: 3px;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.gl-line {
  width: 1px;
  flex: 1;
  min-height: 12px;
  background: var(--border);
  margin: 3px 0;
}
.gl-line.done { background: var(--green, #2e7d32); }

.gl-body-text {
  flex: 1;
  min-width: 0;
  padding-bottom: 9px;
}

.gl-desc {
  font-size: 12px;
  color: var(--fg);
  line-height: 1.5;
  word-break: break-word;
}

.gl-row.current .gl-desc {
  color: var(--fg);
  font-weight: 600;
}

.gl-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 1px;
}

.gl-evidence {
  font-size: 10px;
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.gl-evidence.gl-err { color: var(--red, #c62828); }

.gl-verify {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--fg-4);
  font-family: var(--mono);
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid var(--border);
}
.gl-verify svg { flex-shrink: 0; }
</style>
