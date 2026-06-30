<template>
  <div class="wf">
    <div class="wf-head">
      <span class="wf-title">{{ data?.title || '工作流' }}</span>
      <span class="wf-badge" :class="data?.status?.toLowerCase()">{{ statusText }}</span>
      <span class="wf-progress" v-if="data?.totalSteps">{{ data.currentStepIndex }}/{{ data.totalSteps }}</span>
    </div>

    <div class="wf-body">
      <div v-for="(step, i) in data?.steps" :key="step.id" class="wf-row"
           :class="[step.status?.toLowerCase(), { current: i === (data?.currentStepIndex || 1) - 1 }]">
        <div class="wf-track">
          <div class="wf-dot" :class="step.status?.toLowerCase()"></div>
          <div v-if="i < data.steps.length - 1" class="wf-line" :class="step.status === 'DONE' ? 'done' : ''"></div>
        </div>
        <div class="wf-body-text">
          <div class="wf-desc">{{ step.description }}</div>
          <div class="wf-meta">
            <span v-if="step.kind === 'HITL'" class="wf-tag">人工审批</span>
            <span v-else-if="step.kind === 'FORK'" class="wf-tag">分支</span>
            <span v-if="step.result && step.status === 'DONE'" class="wf-result">{{ step.result }}</span>
            <span v-else-if="step.result && step.status === 'FAILED'" class="wf-err">{{ step.result }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ data: Object })

const STATUS_MAP = { DRAFT: '草稿', ACTIVE: '进行中', PAUSED: '暂停', COMPLETED: '已完成', FAILED: '失败' }

const statusText = computed(() => STATUS_MAP[props.data?.status] || props.data?.status || '')
</script>

<style scoped>
.wf {
  font-size: 12px;
  color: var(--fg);
}

.wf-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 6px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--glass-border);
}

.wf-title {
  font-weight: 500;
  font-size: 12px;
  color: var(--fg);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--bg-3);
  color: var(--fg-3);
  line-height: 1.5;
}
.wf-badge.active    { background: var(--accent-bg); color: var(--accent); }
.wf-badge.completed { background: var(--green-bg, #e8f5e9); color: var(--green, #2e7d32); }
.wf-badge.failed    { background: var(--red-bg, #ffebee); color: var(--red, #c62828); }
.wf-badge.paused    { background: var(--yellow-bg, #fff8e1); color: var(--yellow, #f57f17); }

.wf-progress {
  font-size: 10px;
  color: var(--fg-4);
  font-family: var(--mono);
}

.wf-body {
  display: flex;
  flex-direction: column;
}

.wf-row {
  display: flex;
  gap: 8px;
  opacity: 0.4;
  transition: opacity 0.2s;
}
.wf-row.current,
.wf-row.done,
.wf-row.failed { opacity: 1; }
.wf-row.skipped { opacity: 0.35; }

.wf-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  flex-shrink: 0;
}

.wf-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-2);
  flex-shrink: 0;
  margin-top: 4px;
  transition: background 0.2s;
}
.wf-dot.done    { background: var(--green, #2e7d32); }
.wf-dot.failed  { background: var(--red, #c62828); }
.wf-dot.skipped { background: var(--fg-4); }

.wf-row.current .wf-dot {
  width: 10px;
  height: 10px;
  margin-top: 3px;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.wf-line {
  width: 1px;
  flex: 1;
  min-height: 12px;
  background: var(--border);
  margin: 3px 0;
}
.wf-line.done { background: var(--green, #2e7d32); }

.wf-body-text {
  flex: 1;
  min-width: 0;
  padding-bottom: 10px;
}

.wf-desc {
  font-size: 12px;
  color: var(--fg);
  line-height: 1.5;
  word-break: break-word;
}

.wf-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 1px;
}

.wf-tag {
  font-size: 9px;
  padding: 0 4px;
  border-radius: 2px;
  background: var(--bg-3);
  color: var(--fg-3);
  line-height: 1.6;
}

.wf-result {
  font-size: 10px;
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.wf-err {
  font-size: 10px;
  color: var(--red, #c62828);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
</style>
