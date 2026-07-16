<template>
  <div class="cl">
    <div class="cl-head">
      <span class="cl-title">{{ data?.title || '清单' }}</span>
      <span class="cl-badge" :class="data?.status?.toLowerCase()">{{ statusText }}</span>
      <span class="cl-progress" v-if="data?.totalSteps">{{ data.currentStepIndex }}/{{ data.totalSteps }}</span>
    </div>
    <div v-if="data?.totalSteps" class="cl-progress-track" aria-hidden="true">
      <span :style="{ width: `${progressPercent}%` }"></span>
    </div>

    <div class="cl-body">
      <div v-for="(step, i) in data?.steps" :key="step.id" class="cl-row"
           :class="[step.status?.toLowerCase(), { current: i === (data?.currentStepIndex || 1) - 1 }]">
        <div class="cl-track">
          <div class="cl-dot" :class="step.status?.toLowerCase()"></div>
          <div v-if="i < data.steps.length - 1" class="cl-line" :class="step.status === 'DONE' ? 'done' : ''"></div>
        </div>
        <div class="cl-body-text">
          <div class="cl-desc">{{ step.description }}</div>
          <div class="cl-meta">
            <span v-if="step.kind === 'HITL'" class="cl-tag">人工审批</span>
            <span v-else-if="step.kind === 'FORK'" class="cl-tag">分支</span>
            <span v-if="step.result && step.status === 'DONE'" class="cl-result">{{ step.result }}</span>
            <span v-else-if="step.result && step.status === 'FAILED'" class="cl-err">{{ step.result }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'

const props = defineProps({ data: Object })

const STATUS_MAP = { DRAFT: '草稿', ACTIVE: '进行中', PAUSED: '暂停', COMPLETED: '已完成', FAILED: '失败' }

const statusText = computed(() => STATUS_MAP[props.data?.status] || props.data?.status || '')
const progressPercent = computed(() => {
  const total = Number(props.data?.totalSteps) || 0
  const current = Number(props.data?.currentStepIndex) || 0
  return total ? Math.min(100, Math.max(0, current / total * 100)) : 0
})
</script>

<style scoped>
.cl {
  font-size: 12px;
  color: var(--fg);
}

.cl-head {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.cl-title {
  font-weight: 500;
  font-size: 12px;
  color: var(--fg);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cl-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-3);
  color: var(--fg-3);
  line-height: 1.5;
}
.cl-badge.active    { background: var(--accent-bg); color: var(--accent); }
.cl-badge.completed { background: var(--green-bg, #e8f5e9); color: var(--green, #2e7d32); }
.cl-badge.failed    { background: var(--red-bg, #ffebee); color: var(--red, #c62828); }
.cl-badge.paused    { background: var(--yellow-bg, #fff8e1); color: var(--yellow, #f57f17); }

.cl-progress {
  font-size: 10px;
  color: var(--fg-4);
  font-family: var(--mono);
}

.cl-progress-track {
  height: 3px;
  margin: 7px 0 9px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--bg-3);
}

.cl-progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 0.25s ease;
}

.cl-body {
  display: flex;
  flex-direction: column;
}

.cl-row {
  display: flex;
  gap: 8px;
  opacity: 0.38;
  transition: opacity 0.2s;
}
.cl-row.current,
.cl-row.done,
.cl-row.failed { opacity: 1; }
.cl-row.skipped { opacity: 0.35; }

.cl-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  flex-shrink: 0;
}

.cl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-2);
  flex-shrink: 0;
  margin-top: 4px;
  transition: background 0.2s;
}
.cl-dot.done    { background: var(--green, #2e7d32); }
.cl-dot.failed  { background: var(--red, #c62828); }
.cl-dot.skipped { background: var(--fg-4); }

.cl-row.current .cl-dot {
  width: 10px;
  height: 10px;
  margin-top: 3px;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.cl-line {
  width: 1px;
  flex: 1;
  min-height: 12px;
  background: var(--border);
  margin: 3px 0;
}
.cl-line.done { background: var(--green, #2e7d32); }

.cl-body-text {
  flex: 1;
  min-width: 0;
  padding-bottom: 9px;
}

.cl-desc {
  font-size: 12px;
  color: var(--fg);
  line-height: 1.5;
  word-break: break-word;
}

.cl-row.current .cl-desc {
  color: var(--fg);
  font-weight: 600;
}

.cl-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 1px;
}

.cl-tag {
  font-size: 9px;
  padding: 0 4px;
  border-radius: 2px;
  background: var(--bg-3);
  color: var(--fg-3);
  line-height: 1.6;
}

.cl-result {
  font-size: 10px;
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.cl-err {
  font-size: 10px;
  color: var(--red, #c62828);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
</style>
