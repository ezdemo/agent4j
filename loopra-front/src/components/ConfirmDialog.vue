<template>
  <ActionConfirmDialog
      :model-value="visible"
      :title="title"
      :message="message"
      :actions="actions"
      @update:model-value="handleVisibility"
      @action="handleAction"
  />
</template>

<script setup>
import {computed} from 'vue'
import {useConfirm} from '../composables/useConfirm'
import ActionConfirmDialog from './ActionConfirmDialog.vue'

const { visible, title, message, okText, cancelText, ok, cancel } = useConfirm()

const actions = computed(() => [
  { key: 'cancel', label: cancelText.value },
  { key: 'confirm', label: okText.value, variant: 'danger' }
])

const handleVisibility = value => {
  if (!value) cancel()
}

const handleAction = key => {
  if (key === 'confirm') ok()
  else cancel()
}
</script>
