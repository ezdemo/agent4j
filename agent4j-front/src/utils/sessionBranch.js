/**
 * Maps each rendered assistant turn to the exclusive end offset in raw history.
 * Tool results belong to the assistant turn that started them.
 */
export const getAssistantTurnBoundaries = (rawHistory = []) => {
  const boundaries = []
  let activeAssistantTurn = -1

  rawHistory.forEach((message, index) => {
    if (message.role === 'user') {
      activeAssistantTurn = -1
      return
    }
    if (message.role === 'assistant' && activeAssistantTurn === -1) {
      activeAssistantTurn = boundaries.length
      boundaries.push(index + 1)
      return
    }
    if (activeAssistantTurn >= 0 && (message.role === 'assistant' || message.role === 'tool')) {
      boundaries[activeAssistantTurn] = index + 1
    }
  })

  return boundaries
}
