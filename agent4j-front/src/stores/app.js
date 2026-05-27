import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const connectionStatus = ref('disconnected')
  const currentSession = ref(null)
  const messages = ref([])
  const settings = ref({
    language: 'zh-CN',
    theme: 'retro-green',
    fontSize: 14
  })

  const setConnectionStatus = (status) => {
    connectionStatus.value = status
  }

  const setCurrentSession = (session) => {
    currentSession.value = session
  }

  const addMessage = (message) => {
    messages.value.push(message)
  }

  const clearMessages = () => {
    messages.value = []
  }

  const updateSettings = (newSettings) => {
    settings.value = { ...settings.value, ...newSettings }
  }

  return {
    connectionStatus,
    currentSession,
    messages,
    settings,
    setConnectionStatus,
    setCurrentSession,
    addMessage,
    clearMessages,
    updateSettings
  }
})