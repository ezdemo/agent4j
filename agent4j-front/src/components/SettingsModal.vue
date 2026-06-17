<template>
  <Teleport to="body">
    <div v-if="show" class="modal-mask" @click.self="close">
      <div class="modal modal-settings">
        <div class="modal-head">
          <span>设置</span>
          <button class="btn-icon-sm" @click="close">×</button>
        </div>
        <div class="modal-body">
          <SettingsView @auto-update="$emit('autoUpdate')" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import SettingsView from '../views/Settings.vue'

const props = defineProps({
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'autoUpdate'])

function close() {
  emit('update:show', false)
}
</script>
