<template>
  <Teleport to="body">
    <div v-if="visible" class="confirm-mask" @click.self="cancel">
      <div class="confirm-box">
        <div class="confirm-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <div class="confirm-title">{{ title }}</div>
        <div class="confirm-body">{{ message }}</div>
        <div class="confirm-actions">
          <button class="btn btn-secondary" @click="cancel">{{ cancelText }}</button>
          <button class="btn btn-danger" @click="ok">{{ okText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { useConfirm } from '../composables/useConfirm'

const { visible, title, message, okText, cancelText, ok, cancel } = useConfirm()
</script>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 400;
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-box {
  width: min(380px, 90vw);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: 24px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.confirm-icon {
  color: var(--yellow);
  margin-bottom: 12px;
}

.confirm-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 8px;
}

.confirm-body {
  font-size: 13px;
  color: var(--fg-3);
  line-height: 1.5;
  margin-bottom: 20px;
  white-space: pre-wrap;
}

.confirm-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.confirm-actions .btn {
  min-width: 80px;
}

.btn-danger {
  background: var(--red);
  color: #fff;
  border: none;
  padding: 7px 16px;
  border-radius: var(--r);
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-danger:hover { opacity: 0.85; }

[data-theme="dark"] .confirm-box {
  background: var(--bg-2);
}
</style>
