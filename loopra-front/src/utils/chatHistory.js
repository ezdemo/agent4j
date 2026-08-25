/**
 * 将原始 ChatMessage 事件（history/events 接口返回）组装成 Web 展示项。
 * 主消息列表与“查看原始记录”弹窗共用同一套转换，保证工具、思考的折叠体验一致。
 */

const now = () => {
  const d = new Date()
  return d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit'})
}

export const hasEncryptedReasoning = (value) => {
  if (!value) return false
  try {
    const item = typeof value === 'string' ? JSON.parse(value) : value
    return item?.type === 'reasoning' && typeof item.encrypted_content === 'string' && item.encrypted_content.length > 0
  } catch {
    return false
  }
}

export const formatTimestamp = (timestamp) => {
  if (!timestamp) return now()
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit'})
}

export const mergeFileChanges = (blocks, changes) => {
  if (!Array.isArray(changes) || changes.length === 0) return
  let summary = blocks.find(block => block.type === 'file_changes')
  if (!summary) {
    summary = {type: 'file_changes', changes: []}
    blocks.push(summary)
  }
  const byPath = new Map(summary.changes.map(change => [change.path, {...change}]))
  for (const change of changes) {
    if (!change?.path) continue
    const existing = byPath.get(change.path)
    byPath.set(change.path, existing ? {
      ...existing,
      additions: Number(existing.additions || 0) + Number(change.additions || 0),
      deletions: Number(existing.deletions || 0) + Number(change.deletions || 0),
      created: Boolean(existing.created || change.created),
      diff: [existing.diff, change.diff].filter(Boolean).join('\n')
    } : {...change})
  }
  summary.changes = [...byPath.values()]
}

export const moveFileChangesToEnd = (blocks) => {
  const changes = blocks.filter(block => block.type === 'file_changes')
  if (changes.length === 0) return
  const summary = changes[0]
  const rest = blocks.filter(block => block.type !== 'file_changes')
  blocks.splice(0, blocks.length, ...rest, summary)
}

/**
 * 组装历史展示项。
 * @param raw ChatMessage 数组（tool result 是独立的 tool role 消息）
 * @param includeWebHidden 是否保留 web_hidden 用户消息（原始记录审计需要）
 * @returns {items, unmergedToolResults}
 */
export const buildHistoryItems = (raw = [], includeWebHidden = false) => {
  const events = Array.isArray(raw) ? raw : []
  const toolResults = {}
  const unmergedToolResults = []
  const matchedToolIds = new Set()
  for (const m of events) {
    if (m.role === 'tool' && m.tool_call_id) {
      toolResults[m.tool_call_id] = {
        content: m.content || '',
        durationMs: m.tool_duration_ms ?? m.toolDurationMs ?? null,
        startedAt: m.tool_started_at ?? m.toolStartedAt ?? null,
        finishedAt: m.tool_finished_at ?? m.toolFinishedAt ?? null
      }
    }
  }

  const items = []
  let lastAssistantItem = null
  let idCounter = 0
  for (const m of events) {
    if (m.role === 'user') {
      if (m.web_hidden || m.webHidden) {
        if (!includeWebHidden) {
          lastAssistantItem = null
          continue
        }
        const item = {id: Date.now() + idCounter++, role: 'user', time: formatTimestamp(m.timestamp), blocks: [], webHidden: true}
        const parts = m.contentParts || (Array.isArray(m.content) ? m.content : null)
        if (parts && parts.length > 0) {
          const texts = []
          const imgs = []
          for (const part of parts) {
            if (part.type === 'text' && part.text) texts.push(part.text)
            if (part.type === 'image_url') {
              const url = part.image_url?.url || part.imageUrl?.url
              if (url) imgs.push(url)
            }
          }
          item.content = texts.join('\n')
          if (imgs.length > 0) item.images = imgs
        } else {
          item.content = m.content || ''
        }
        if (m.snapshot_id) item.snapshotId = m.snapshot_id
        item.rollbackId = m.rollback_id || m.snapshot_id || null
        item.rollbackTimestamp = m.timestamp || null
        items.push(item)
        lastAssistantItem = null
        continue
      }
      const item = {id: Date.now() + idCounter++, role: 'user', time: formatTimestamp(m.timestamp), blocks: []}
      const parts = m.contentParts || (Array.isArray(m.content) ? m.content : null)
      if (parts && parts.length > 0) {
        const texts = []
        const imgs = []
        for (const part of parts) {
          if (part.type === 'text' && part.text) texts.push(part.text)
          if (part.type === 'image_url') {
            const url = part.image_url?.url || part.imageUrl?.url
            if (url) imgs.push(url)
          }
        }
        item.content = texts.join('\n')
        if (imgs.length > 0) item.images = imgs
      } else {
        item.content = m.content || ''
      }
      if (m.snapshot_id) item.snapshotId = m.snapshot_id
      item.rollbackId = m.rollback_id || m.snapshot_id || null
      item.rollbackTimestamp = m.timestamp || null
      items.push(item)
      lastAssistantItem = null
    } else if (m.role === 'tool') {
      if (!m.tool_call_id || !Object.hasOwn(toolResults, m.tool_call_id)) unmergedToolResults.push(m)
      continue
    } else if (m.role === 'assistant') {
      if (!lastAssistantItem) {
        lastAssistantItem = {id: Date.now() + idCounter++, role: 'assistant', time: formatTimestamp(m.timestamp), blocks: []}
        items.push(lastAssistantItem)
      } else {
        lastAssistantItem.time = formatTimestamp(m.timestamp)
      }
      if (m.reasoning_content) lastAssistantItem.blocks.push({
        type: 'reasoning',
        content: m.reasoning_content,
        showContent: false
      })
      if (hasEncryptedReasoning(m.response_reasoning || m.responseReasoning)) {
        lastAssistantItem.blocks.push({type: 'reasoning_started', showContent: false})
      }
      if (m.tool_calls) for (const tc of m.tool_calls) {
        let name = tc.function?.name || tc.name || ''
        let args = tc.function?.arguments || tc.arguments || ''
        if (typeof args === 'string') {
          try {
            args = JSON.parse(args)
          } catch {
          }
        }
        const toolResult = toolResults[tc.id]
        const hasResult = Object.hasOwn(toolResults, tc.id)
        if (hasResult) matchedToolIds.add(tc.id)
        lastAssistantItem.blocks.push({
          type: 'tool_call',
          name,
          status: hasResult ? '成功' : '执行中',
          args,
          result: toolResult?.content || '',
          toolDurationMs: toolResult?.durationMs,
          toolStartedAt: toolResult?.startedAt || m.timestamp,
          toolFinishedAt: toolResult?.finishedAt,
          expanded: !hasResult
        })
      }
      // 纯空白正文（思考间隙流出的 \n\n）不建块，与流式行为一致
      if (m.content && m.content.trim()) lastAssistantItem.blocks.push({type: 'content', content: m.content})
      const fileChanges = m.file_changes || m.fileChanges
      if (Array.isArray(fileChanges) && fileChanges.length > 0) {
        mergeFileChanges(lastAssistantItem.blocks, fileChanges)
      }
    }
  }
  for (const item of items) {
    if (item.role === 'assistant') moveFileChangesToEnd(item.blocks)
  }
  for (const m of events) {
    if (m.role === 'tool' && m.tool_call_id && Object.hasOwn(toolResults, m.tool_call_id) && !matchedToolIds.has(m.tool_call_id)) {
      unmergedToolResults.push(m)
    }
  }
  return {items, unmergedToolResults}
}
