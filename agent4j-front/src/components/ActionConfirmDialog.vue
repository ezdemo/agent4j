<template>
  <Teleport to="body">
    <div v-if="modelValue" class="action-confirm-mask" @click.self="close">
      <div class="action-confirm-dialog" role="alertdialog" aria-modal="true" :aria-labelledby="titleId">
        <div class="action-confirm-heading">
          <div class="action-confirm-icon" :class="`tone-${tone}`">
            <slot name="icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </slot>
          </div>
          <div :id="titleId" class="action-confirm-title">{{ title }}</div>
        </div>
        <p class="action-confirm-copy">{{ message }}</p>
        <div class="action-confirm-actions" :style="{ '--action-count': actions.length }">
          <button
              v-for="action in actions"
              :key="action.key"
              class="action-confirm-btn"
              :class="`variant-${action.variant || 'default'}`"
              :disabled="pending || action.disabled"
              type="button"
              @click="$emit('action', action.key)"
          >
            {{ action.label }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
const props = defineProps({
  modelValue: {type: Boolean, default: false},
  title: {type: String, required: true},
  message: {type: String, required: true},
  actions: {type: Array, required: true},
  tone: {type: String, default: 'warning'},
  pending: {type: Boolean, default: false}
})

const emit = defineEmits(['update:modelValue', 'action'])
const titleId = `action-confirm-title-${Math.random().toString(36).slice(2)}`

const close = () => {
  if (!props.pending) emit('update:modelValue', false)
}
</script>

<style scoped>
.action-confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.42);
}

.action-confirm-dialog {
  width: min(460px, 100%);
  padding: 22px;
  border: 1px solid color-mix(in srgb, var(--yellow) 45%, var(--border));
  border-radius: var(--r-lg);
  background: var(--bg);
  box-shadow: 0 16px 42px rgba(0, 0, 0, 0.28);
}

.action-confirm-icon {
  display: inline-grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
}

.action-confirm-icon.tone-warning {
  background: color-mix(in srgb, var(--yellow) 16%, transparent);
  color: var(--yellow);
}

.action-confirm-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-confirm-title {
  color: var(--fg);
  font-size: 16px;
  font-weight: 700;
}

.action-confirm-copy {
  margin: 7px 0 20px;
  color: var(--fg-3);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.action-confirm-actions {
  display: grid;
  grid-template-columns: repeat(var(--action-count), minmax(0, 1fr));
  gap: 8px;
}

.action-confirm-btn {
  min-height: 36px;
  padding: 7px 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--bg-2);
  color: var(--fg-2);
  cursor: pointer;
  font-family: var(--sans);
  font-size: 12px;
  font-weight: 600;
  transition: background var(--t), border-color var(--t), color var(--t), box-shadow var(--t);
}

.action-confirm-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--fg-3) 55%, var(--border));
  background: var(--bg-3);
  box-shadow: 0 2px 8px color-mix(in srgb, #000000 10%, transparent);
}

.action-confirm-btn:disabled {
  cursor: wait;
  opacity: 0.6;
}

.action-confirm-btn.variant-accent {
  border-color: color-mix(in srgb, var(--accent) 36%, var(--border));
  background: color-mix(in srgb, var(--accent) 8%, var(--bg));
  color: color-mix(in srgb, var(--accent) 82%, var(--fg));
}

.action-confirm-btn.variant-accent:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--accent) 58%, var(--border));
  background: color-mix(in srgb, var(--accent) 13%, var(--bg));
}

.action-confirm-btn.variant-danger {
  border-color: color-mix(in srgb, var(--red) 38%, var(--border));
  background: color-mix(in srgb, var(--red) 9%, var(--bg));
  color: color-mix(in srgb, var(--red) 80%, var(--fg));
}

.action-confirm-btn.variant-danger:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--red) 58%, var(--border));
  background: color-mix(in srgb, var(--red) 14%, var(--bg));
  color: color-mix(in srgb, var(--red) 88%, var(--fg));
}

@media (max-width: 640px) {
  .action-confirm-actions {
    grid-template-columns: 1fr;
  }
}
</style>
