const MODEL_SELECTIONS_KEY = 'loopra.session-model-selections'
const REASONING_EFFORTS_KEY = 'loopra.session-reasoning-efforts'

const readObject = (key) => {
  try {
    const stored = JSON.parse(localStorage.getItem(key) || '{}')
    return stored && typeof stored === 'object' ? stored : {}
  } catch {
    return {}
  }
}

const writeObject = (key, value) => {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // localStorage 不可用时由后端会话设置继续提供持久化。
  }
}

const removeEntry = (key, sessionKey) => {
  const values = readObject(key)
  if (!Object.prototype.hasOwnProperty.call(values, sessionKey)) return
  delete values[sessionKey]
  writeObject(key, values)
}

/**
 * 会话设置的旧版 localStorage 兼容层。
 * 新代码应以服务端会话元数据为准；这里只负责导入旧值并在导入成功后清理。
 */
export const legacySessionSettingsStorage = {
  load() {
    return {
      modelSelections: readObject(MODEL_SELECTIONS_KEY),
      reasoningEfforts: readObject(REASONING_EFFORTS_KEY)
    }
  },

  removeModelSelection(sessionKey) {
    removeEntry(MODEL_SELECTIONS_KEY, sessionKey)
  },

  removeReasoningEffort(sessionKey) {
    removeEntry(REASONING_EFFORTS_KEY, sessionKey)
  }
}
