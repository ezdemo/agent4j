// 引导页（Onboarding）文件级 IPC：扫描外部 Agent 目录、导入 Skills（复制/硬链接）、
// 迁移会话与 AGENTS.md、解析外部 MCP 配置文件。
// 所有业务操作（模型配置/MCP 注册/Skills 刷新/会话列表）由渲染层复用服务端 REST 接口，
// 本模块仅负责渲染层无权访问的本地文件系统操作。
const { app, dialog, ipcMain } = require('electron')
const fs = require('fs')
const path = require('path')

// 限制常量
const MAX_SESSION_FILE_BYTES = 50 * 1024 * 1024 // 单个会话文件最大 50MB
const MAX_MCP_CONFIG_BYTES = 8 * 1024 * 1024 // MCP 配置文件最大 8MB（~/.claude.json 可能较大）
const MAX_TEXT_PREVIEW_BYTES = 256 * 1024 // 文本预览最大 256KB
const MAX_SCAN_DEPTH = 5 // 目录递归扫描深度

// 常见外部 Agent 工具目录候选（引导页快捷选择）
const CANDIDATE_AGENT_DIRS = [
  { label: 'Claude Code', dir: '~/.claude' },
  { label: 'Codex CLI', dir: '~/.codex' },
  { label: 'Cursor', dir: '~/.cursor' },
  { label: 'Windsurf', dir: '~/.windsurf' },
  { label: 'Gemini CLI', dir: '~/.gemini' }
]

// 用户主目录下的 MCP 配置文件候选（位于 ~/.claude 之外，单独提供）
function candidateHomeMcpConfigs(homeDir) {
  return [
    { label: 'Claude Code 全局配置 (~/.claude.json)', path: path.join(homeDir, '.claude.json') },
    { label: '~/.mcp.json', path: path.join(homeDir, '.mcp.json') }
  ]
}

// 常见 Agent 的全局规则文件候选（AGENTS.md / CLAUDE.md，位于 agent 配置目录内）
function candidateAgentRuleFiles(homeDir) {
  return [
    { label: 'Codex 全局规则', path: path.join(homeDir, '.codex', 'AGENTS.md') },
    { label: 'Codex 全局规则 (agents.md)', path: path.join(homeDir, '.codex', 'agents.md') },
    { label: 'Claude Code 全局规则', path: path.join(homeDir, '.claude', 'CLAUDE.md') },
    { label: 'Claude Code 全局规则 (claude.md)', path: path.join(homeDir, '.claude', 'claude.md') }
  ]
}

function loopraDirs() {
  const homeDir = app.getPath('home')
  const configDir = path.join(homeDir, '.loopra')
  return {
    configDir,
    skillsDir: path.join(configDir, 'skills'),
    sessionsDir: path.join(configDir, 'sessions'),
    homeDir
  }
}

function expandHome(relPath) {
  return String(relPath || '').replace(/^~(?=[\\/]|$)/, app.getPath('home'))
}

// ==================== 目录与文件选择 ====================

async function pickDirectory(title) {
  const result = await dialog.showOpenDialog({
    title: title || '选择目录',
    properties: ['openDirectory', 'createDirectory']
  })
  return result.canceled ? '' : (result.filePaths[0] || '')
}

async function pickFile(title, filters) {
  const result = await dialog.showOpenDialog({
    title: title || '选择文件',
    properties: ['openFile'],
    filters: filters && filters.length ? filters : undefined
  })
  return result.canceled ? '' : (result.filePaths[0] || '')
}

// ==================== 目录扫描 ====================

// 解析 SKILL.md frontmatter（--- name/description ---），失败时回退目录名
function parseSkillMeta(skillMdPath, fallbackName) {
  let content = ''
  try {
    content = fs.readFileSync(skillMdPath, 'utf8').slice(0, 4096)
  } catch {
    return { name: fallbackName, description: '' }
  }
  const match = content.match(/^\uFEFF?---\r?\n([\s\S]*?)\r?\n---/)
  const meta = { name: fallbackName, description: '' }
  if (match) {
    for (const line of match[1].split(/\r?\n/)) {
      const idx = line.indexOf(':')
      if (idx <= 0) continue
      const key = line.slice(0, idx).trim()
      const value = line.slice(idx + 1).trim().replace(/^["']|["']$/g, '')
      if (key === 'name') meta.name = value || fallbackName
      else if (key === 'description') meta.description = value
    }
  }
  return meta
}

// 递归扫描目录：Skills（SKILL.md）、会话（*.jsonl）、AGENTS.md、MCP 配置
function scanAgentDir(rootDir) {
  if (!rootDir || typeof rootDir !== 'string' || !fs.existsSync(rootDir) || !fs.statSync(rootDir).isDirectory()) {
    throw new Error('所选目录不存在或不可读')
  }
  const skills = []
  const sessions = []
  const agentsMd = []
  const mcpConfigs = []
  const seenSkills = new Set()

  const visit = (dir, depth) => {
    if (depth > MAX_SCAN_DEPTH) return
    let entries
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue
        // 优先定位 SKILL.md 所在目录
        const skillMd = path.join(fullPath, 'SKILL.md')
        const skillMdLower = path.join(fullPath, 'skill.md')
        const skillMdPath = fs.existsSync(skillMd) ? skillMd : (fs.existsSync(skillMdLower) ? skillMdLower : '')
        if (skillMdPath) {
          const key = fullPath.toLowerCase()
          if (!seenSkills.has(key)) {
            seenSkills.add(key)
            const meta = parseSkillMeta(skillMdPath, entry.name)
            skills.push({ name: meta.name, description: meta.description, sourceDir: fullPath })
          }
          continue
        }
        visit(fullPath, depth + 1)
        continue
      }
      if (!entry.isFile()) continue
      const lower = entry.name.toLowerCase()
      if (lower.endsWith('.jsonl')) {
        let size = 0
        try { size = fs.statSync(fullPath).size } catch {}
        if (size > 0 && size <= MAX_SESSION_FILE_BYTES) {
          sessions.push({ name: entry.name.replace(/\.jsonl$/i, ''), path: fullPath, size, mtime: fileMtime(fullPath) })
        }
      } else if (lower === 'agents.md' || lower === 'agent.md' || lower === 'claude.md') {
        agentsMd.push({ name: entry.name, path: fullPath })
      } else if (lower === 'mcp.json' || lower === '.mcp.json') {
        mcpConfigs.push({ name: entry.name, path: fullPath })
      }
    }
  }

  visit(rootDir, 0)
  // 目录为 ~/.claude 时补充 home 下的 .claude.json
  const homeDir = app.getPath('home')
  if (path.resolve(rootDir) === path.resolve(homeDir, '.claude')) {
    const claudeJson = path.join(homeDir, '.claude.json')
    if (fs.existsSync(claudeJson) && fs.statSync(claudeJson).size <= MAX_MCP_CONFIG_BYTES) {
      mcpConfigs.push({ name: '.claude.json', path: claudeJson })
    }
  }
  sessions.sort((a, b) => (b.mtime || 0) - (a.mtime || 0))
  return { skills, sessions, agentsMd, mcpConfigs }
}

function fileMtime(filePath) {
  try { return fs.statSync(filePath).mtimeMs } catch { return 0 }
}

// ==================== 导入 Skills（复制/硬链接） ====================

// 目录内文件逐个硬链接（Windows 目录不支持 linkSync），跨卷抛 EXDEV
function hardLinkDirContents(srcDir, destDir) {
  fs.mkdirSync(destDir, { recursive: true })
  const entries = fs.readdirSync(srcDir, { withFileTypes: true })
  for (const entry of entries) {
    const srcPath = path.join(srcDir, entry.name)
    const destPath = path.join(destDir, entry.name)
    if (entry.isDirectory()) {
      hardLinkDirContents(srcPath, destPath)
    } else if (entry.isSymbolicLink()) {
      const target = fs.readlinkSync(srcPath)
      fs.symlinkSync(target, destPath)
    } else if (entry.isFile()) {
      fs.linkSync(srcPath, destPath)
    }
  }
}

function copyDirContents(srcDir, destDir) {
  fs.cpSync(srcDir, destDir, { recursive: true, errorOnExist: false })
}

// 导入单个 Skill 目录到 ~/.loopra/skills/<name>
// mode: 'hardlink'（优先硬链接，跨卷/失败回退复制）| 'copy'
function importSkill(sourceDir, name, mode, skillsDir) {
  const safeName = sanitizeName(name) || path.basename(sourceDir)
  if (!safeName) return { ok: false, name: name || path.basename(sourceDir), error: 'Skill 名称无效' }
  const targetDir = path.join(skillsDir, safeName)
  if (fs.existsSync(targetDir)) {
    return { ok: false, name: safeName, error: '已存在同名 Skill，请先移除或选择其他名称' }
  }
  if (mode === 'hardlink') {
    try {
      hardLinkDirContents(sourceDir, targetDir)
      return { ok: true, name: safeName, mode: 'hardlink' }
    } catch (error) {
      // 跨卷等场景回退复制
      try {
        if (fs.existsSync(targetDir)) fs.rmSync(targetDir, { recursive: true, force: true })
        copyDirContents(sourceDir, targetDir)
        return { ok: true, name: safeName, mode: 'copy', fallback: true, error: `硬链接失败（${error.code || error.message || '未知原因'}），已改为复制` }
      } catch (copyError) {
        return { ok: false, name: safeName, error: copyError.message || '复制失败' }
      }
    }
  }
  try {
    copyDirContents(sourceDir, targetDir)
    return { ok: true, name: safeName, mode: 'copy' }
  } catch (error) {
    return { ok: false, name: safeName, error: error.message || '复制失败' }
  }
}

// ==================== 迁移会话 ====================

// ==================== 会话格式识别与转换 ====================

// 常见 Agent 会话格式与 Loopra 消息格式的转换支持：
// - Loopra 原生：每行 { role, content|contentParts, tool_calls, tool_call_id, timestamp(毫秒) }
// - Claude Code（~/.claude/projects/**/*.jsonl）：每行 { type, message:{role,content}, timestamp(ISO) }
// - Codex CLI（~/.codex/sessions/*.jsonl）：文本行 { timestamp, role, content } 或 { type:'response_item', payload }

const MAX_SESSION_LINES = 50000 // 单会话转换行数上限，超出拒绝导入
const FORMAT_SAMPLE_LINES = 20 // 格式检测采样行数

function detectSessionFormat(lines) {
  const counts = { loopra: 0, claude: 0, codex: 0, unknown: 0 }
  const samples = lines.slice(0, FORMAT_SAMPLE_LINES)
  for (const line of samples) {
    let obj
    try {
      obj = JSON.parse(line)
    } catch {
      counts.unknown += 1
      continue
    }
    if (!obj || typeof obj !== 'object') {
      counts.unknown += 1
      continue
    }
    // Claude Code：type(user/assistant/system) + message 对象
    if (typeof obj.type === 'string' && ['user', 'assistant', 'system'].includes(obj.type) && obj.message && typeof obj.message === 'object') {
      counts.claude += 1
      continue
    }
    // Codex：response_item 事件，或带 ISO 时间戳的 role 文本行
    if (obj.type === 'response_item' && obj.payload && typeof obj.payload === 'object') {
      counts.codex += 1
      continue
    }
    if (typeof obj.role === 'string' && typeof obj.content === 'string' && typeof obj.timestamp === 'string') {
      counts.codex += 1
      continue
    }
    // Loopra 原生：role + content（时间戳为毫秒数字或缺失）
    if (typeof obj.role === 'string' && (typeof obj.content === 'string' || Array.isArray(obj.content))) {
      counts.loopra += 1
      continue
    }
    counts.unknown += 1
  }
  const best = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]
  return best && best[1] > 0 ? best[0] : 'unknown'
}

const FORMAT_NAMES = { loopra: 'Loopra', claude: 'Claude Code', codex: 'Codex' }

// 提取消息 content 中的纯文本（处理 string / parts 数组）
function extractTextContent(content) {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  const texts = []
  for (const part of content) {
    if (!part || typeof part !== 'object') continue
    if (typeof part.text === 'string') texts.push(part.text)
  }
  return texts.join('\n')
}

// Claude Code 行 → Loopra ChatMessage（无有效内容返回 null）
function convertClaudeLine(obj) {
  const msg = obj.message
  if (!msg || typeof msg !== 'object') return null
  const role = msg.role
  const parts = Array.isArray(msg.content) ? msg.content
    : (typeof msg.content === 'string' ? [{ type: 'text', text: msg.content }] : [])
  if (parts.length === 0) return null

  const out = { role: ['user', 'assistant', 'system'].includes(role) ? role : 'user' }
  if (typeof obj.timestamp === 'string') out.timestamp = obj.timestamp

  if (out.role === 'assistant') {
    const text = extractTextContent(parts)
    const toolUses = parts.filter((p) => p && p.type === 'tool_use')
    if (toolUses.length) {
      out.content = text
      out.tool_calls = toolUses.map((p) => ({
        id: p.id || 'unknown',
        type: 'function',
        function: {
          name: p.name || 'unknown',
          arguments: typeof p.input === 'string' ? p.input : JSON.stringify(p.input || {})
        }
      }))
      return out
    }
    if (text) {
      out.content = text
      return out
    }
    return null
  }

  if (out.role === 'user') {
    const toolResults = parts.filter((p) => p && p.type === 'tool_result')
    if (toolResults.length) {
      // 每条工具结果生成一条 user 消息（携带 tool_call_id），多个结果拆多条保持原语义
      const converted = []
      for (const p of toolResults) {
        const resultText = typeof p.content === 'string' ? p.content : extractTextContent(p.content)
        if (!resultText) continue
        const item = { role: 'user', content: resultText }
        if (p.tool_use_id) item.tool_call_id = p.tool_use_id
        converted.push(item)
      }
      return converted.length ? converted : null
    }
    const text = extractTextContent(parts)
    if (text) {
      out.content = text
      return out
    }
    return null
  }

  // system
  const text = extractTextContent(parts)
  if (text) {
    out.content = text
    return out
  }
  return null
}

// Codex 行 → Loopra ChatMessage（无有效内容返回 null）
function convertCodexLine(obj) {
  // 文本行：{ timestamp(ISO), role, content }
  if (typeof obj.role === 'string' && typeof obj.content === 'string' && obj.content.trim()) {
    return { role: obj.role === 'assistant' || obj.role === 'system' ? obj.role : 'user', content: obj.content, timestamp: obj.timestamp }
  }
  if (obj.type !== 'response_item' || !obj.payload || typeof obj.payload !== 'object') return null
  const p = obj.payload
  if (p.type === 'message') {
    const text = extractTextContent(p.content)
    if (!text) return null
    const out = { role: p.role === 'user' ? 'user' : 'assistant', content: text }
    if (typeof obj.timestamp === 'string') out.timestamp = obj.timestamp
    return out
  }
  if (p.type === 'function_call') {
    const out = {
      role: 'assistant',
      content: '',
      tool_calls: [{
        id: p.call_id || p.id || 'unknown',
        type: 'function',
        function: {
          name: p.name || 'unknown',
          arguments: typeof p.arguments === 'string' ? p.arguments : JSON.stringify(p.arguments || {})
        }
      }]
    }
    if (typeof obj.timestamp === 'string') out.timestamp = obj.timestamp
    return out
  }
  if (p.type === 'function_call_output') {
    const text = typeof p.output === 'string' ? p.output : JSON.stringify(p.output || '')
    if (!text) return null
    const out = { role: 'user', content: text }
    if (p.call_id) out.tool_call_id = p.call_id
    if (typeof obj.timestamp === 'string') out.timestamp = obj.timestamp
    return out
  }
  return null // reasoning / local_shell_call 等事件跳过
}

// 转换整份会话内容，返回可写行的数组与统计
function convertSessionLines(lines, format) {
  const output = []
  let converted = 0
  let skipped = 0
  for (const line of lines) {
    let obj
    try {
      obj = JSON.parse(line)
    } catch {
      skipped += 1
      continue
    }
    const result = format === 'claude' ? convertClaudeLine(obj) : convertCodexLine(obj)
    if (!result) {
      skipped += 1
      continue
    }
    if (Array.isArray(result)) {
      for (const item of result) {
        output.push(JSON.stringify(item))
        converted += 1
      }
    } else {
      output.push(JSON.stringify(result))
      converted += 1
    }
  }
  return { lines: output, converted, skipped }
}

function sanitizeName(name) {
  return String(name || '').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').trim().slice(0, 120)
}

// 导入外部会话 JSONL 到 ~/.loopra/sessions/<workspaceHash>/，同名自动加后缀。
// 自动识别格式：Loopra 原生原样复制；Claude Code / Codex 转换为 Loopra 消息格式。
function importSessionFile(srcPath, workspaceHash, sessionName) {
  const hash = sanitizeName(workspaceHash)
  if (!hash) return { ok: false, name: sessionName, error: '工作区标识无效' }
  if (!srcPath || !fs.existsSync(srcPath) || !fs.statSync(srcPath).isFile()) {
    return { ok: false, name: sessionName, error: '会话文件不存在' }
  }
  const size = fs.statSync(srcPath).size
  if (size > MAX_SESSION_FILE_BYTES) {
    return { ok: false, name: sessionName, error: `会话文件超过 ${MAX_SESSION_FILE_BYTES / 1024 / 1024}MB 限制` }
  }

  // 读取并检测格式，非 Loopra 格式自动转换
  const raw = fs.readFileSync(srcPath, 'utf8')
  const lines = raw.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) {
    return { ok: false, name: sessionName, error: '会话文件为空' }
  }
  if (lines.length > MAX_SESSION_LINES) {
    return { ok: false, name: sessionName, error: `会话行数超过 ${MAX_SESSION_LINES} 行限制` }
  }
  const format = detectSessionFormat(lines)
  let content = ''
  let converted = 0
  let skipped = 0
  if (format === 'loopra') {
    content = lines.join('\n')
  } else if (format === 'claude' || format === 'codex') {
    const result = convertSessionLines(lines, format)
    content = result.lines.join('\n')
    converted = result.converted
    skipped = result.skipped
    if (!content) {
      return { ok: false, name: sessionName, error: `未能从 ${FORMAT_NAMES[format]} 会话中提取有效消息` }
    }
  } else {
    return { ok: false, name: sessionName, error: '无法识别的会话格式（支持 Loopra / Claude Code / Codex）' }
  }
  const dir = path.join(loopraDirs().sessionsDir, hash)
  fs.mkdirSync(dir, { recursive: true })
  let targetName = sanitizeName(sessionName) || 'imported-session'
  let targetPath = path.join(dir, `${targetName}.jsonl`)
  let counter = 2
  while (fs.existsSync(targetPath)) {
    targetName = `${sanitizeName(sessionName) || 'imported-session'}(${counter})`
    targetPath = path.join(dir, `${targetName}.jsonl`)
    counter += 1
  }
  try {
    fs.writeFileSync(targetPath, content, 'utf8')
    return { ok: true, name: targetName, path: targetPath, format, converted, skipped }
  } catch (error) {
    return { ok: false, name: targetName, error: error.message || '写入失败' }
  }
}

// ==================== 迁移 AGENTS.md ====================

function importAgentsMd(srcPath, targetDir, overwrite) {
  if (!srcPath || !fs.existsSync(srcPath) || !fs.statSync(srcPath).isFile()) {
    return { ok: false, error: '源文件不存在' }
  }
  if (!targetDir || !fs.existsSync(targetDir)) {
    return { ok: false, error: '目标工作区目录不存在' }
  }
  const fileName = path.basename(srcPath) || 'AGENTS.md'
  const targetPath = path.join(targetDir, fileName)
  if (fs.existsSync(targetPath) && !overwrite) {
    return { ok: false, error: `目标工作区已存在 ${fileName}（可勾选覆盖后重试）` }
  }
  try {
    fs.copyFileSync(srcPath, targetPath)
    return { ok: true, path: targetPath }
  } catch (error) {
    return { ok: false, error: error.message || '复制失败' }
  }
}

// ==================== 文本读取 ====================

function readTextFile(filePath, maxBytes) {
  if (!filePath || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    throw new Error('文件不存在')
  }
  const size = fs.statSync(filePath).size
  const limit = Number.isFinite(maxBytes) && maxBytes > 0 ? maxBytes : MAX_TEXT_PREVIEW_BYTES
  if (size > limit) {
    throw new Error(`文件过大（${(size / 1024).toFixed(1)}KB），仅支持预览 ${Math.floor(limit / 1024)}KB 以内的文件`)
  }
  return fs.readFileSync(filePath, 'utf8')
}

// ==================== MCP 配置解析 ====================

// 将外部 Agent 的 MCP server 条目规范化为 McpServerDTO 兼容对象
function normalizeMcpServer(name, raw) {
  if (!raw || typeof raw !== 'object') return null
  const type = normalizeMcpType(raw.type, raw)
  return {
    name: sanitizeName(name) || 'mcp-server',
    type,
    enabled: raw.enabled !== false,
    command: typeof raw.command === 'string' ? raw.command : '',
    args: Array.isArray(raw.args) ? raw.args.map(String).filter(Boolean) : [],
    env: raw.env && typeof raw.env === 'object' && !Array.isArray(raw.env) ? { ...raw.env } : {},
    url: typeof raw.url === 'string' ? raw.url : '',
    headers: raw.headers && typeof raw.headers === 'object' && !Array.isArray(raw.headers) ? { ...raw.headers } : {},
    timeout: Number.isFinite(Number(raw.timeout)) && Number(raw.timeout) > 0 ? Number(raw.timeout) : 60
  }
}

function normalizeMcpType(type, raw) {
  const t = String(type || '').toLowerCase()
  if (t === 'sse') return 'sse'
  if (t === 'streamable' || t === 'http' || t === 'streamable-http' || (raw && raw.url && !raw.command)) return 'streamable'
  return 'stdio'
}

// 解析 JSON 形式的 MCP 配置（.claude.json / mcp.json / .mcp.json）
function parseJsonMcpConfig(content) {
  let data
  try {
    data = JSON.parse(content)
  } catch {
    throw new Error('JSON 解析失败，请确认文件格式')
  }
  const servers = []
  // 形式 1：{ mcpServers: { name: {...} } }（Claude Code / Cursor）
  if (data && typeof data.mcpServers === 'object' && !Array.isArray(data.mcpServers)) {
    for (const [name, raw] of Object.entries(data.mcpServers)) {
      const server = normalizeMcpServer(name, raw)
      if (server) servers.push(server)
    }
  }
  // 形式 2：{ servers: [ { name, type, command, args, env, url, headers } ] }（VS Code 风格）
  if (Array.isArray(data?.servers)) {
    for (const raw of data.servers) {
      if (!raw || typeof raw !== 'object') continue
      const name = raw.name || raw.id
      if (!name) continue
      const server = normalizeMcpServer(String(name), raw)
      if (server) servers.push(server)
    }
  }
  return servers
}

// 简易 TOML 解析（仅支持 [section] 与 key = value / 数组 / 内联表）
function parseTomlSimple(content) {
  const result = {}
  let current = null
  for (const rawLine of String(content || '').split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const sectionMatch = line.match(/^\[(.+)\]$/)
    if (sectionMatch) {
      current = sectionMatch[1].trim()
      result[current] = {}
      continue
    }
    if (!current) continue
    const eq = line.indexOf('=')
    if (eq < 0) continue
    const key = line.slice(0, eq).trim()
    let value = line.slice(eq + 1).trim()
    if (value.startsWith('[') && value.endsWith(']')) {
      try {
        value = JSON.parse(value)
      } catch {
        value = value.slice(1, -1).split(',').map((s) => s.trim().replace(/^["']|["']$/g, '')).filter(Boolean)
      }
    } else if (value.startsWith('{') && value.endsWith('}')) {
      try {
        value = JSON.parse(value)
      } catch {
        const obj = {}
        for (const pair of value.slice(1, -1).split(',')) {
          const idx = pair.indexOf('=')
          if (idx < 0) continue
          const k = pair.slice(0, idx).trim().replace(/^["']|["']$/g, '')
          const v = pair.slice(idx + 1).trim().replace(/^["']|["']$/g, '')
          if (k) obj[k] = v
        }
        value = obj
      }
    } else {
      value = value.replace(/^["']|["']$/g, '')
    }
    result[current][key] = value
  }
  return result
}

// 解析 TOML 形式的 MCP 配置（Codex CLI config.toml：mcp_servers.xxx 段）
function parseTomlMcpConfig(content) {
  const parsed = parseTomlSimple(content)
  const servers = []
  for (const [section, values] of Object.entries(parsed)) {
    const match = section.match(/^mcp_servers\.(.+)$/)
    if (!match) continue
    const server = normalizeMcpServer(match[1], values)
    if (server) servers.push(server)
  }
  return servers
}

function parseMcpConfig(filePath) {
  if (!filePath || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    throw new Error('MCP 配置文件不存在')
  }
  const size = fs.statSync(filePath).size
  if (size > MAX_MCP_CONFIG_BYTES) {
    throw new Error(`配置文件超过 ${Math.floor(MAX_MCP_CONFIG_BYTES / 1024 / 1024)}MB 限制`)
  }
  const content = fs.readFileSync(filePath, 'utf8')
  const ext = path.extname(filePath).toLowerCase()
  const servers = ext === '.toml' ? parseTomlMcpConfig(content) : parseJsonMcpConfig(content)
  return servers
}

// ==================== IPC 注册 ====================

function registerOnboardingIpc(ipcMainRef) {
  // 引导页所需本地目录信息 + 外部 Agent 候选目录
  ipcMainRef.handle('onboarding-get-dirs', () => {
    const { configDir, skillsDir, sessionsDir, homeDir } = loopraDirs()
    return {
      configDir,
      skillsDir,
      sessionsDir,
      homeDir,
      candidateAgentDirs: CANDIDATE_AGENT_DIRS.map((item) => {
        const fullPath = expandHome(item.dir)
        let exists = false
        try { exists = fs.existsSync(fullPath) } catch {}
        return { label: item.label, dir: item.dir, path: fullPath, exists }
      }),
      candidateMcpConfigs: candidateHomeMcpConfigs(homeDir).map((item) => {
        let exists = false
        try { exists = fs.existsSync(item.path) } catch {}
        return { ...item, exists }
      }),
      candidateRuleFiles: candidateAgentRuleFiles(homeDir).map((item) => {
        let exists = false
        try { exists = fs.existsSync(item.path) } catch {}
        return { ...item, exists }
      })
    }
  })

  ipcMainRef.handle('onboarding-pick-dir', async (event, title) => {
    return pickDirectory(title)
  })

  ipcMainRef.handle('onboarding-pick-file', async (event, options = {}) => {
    return pickFile(options.title, options.filters)
  })

  ipcMainRef.handle('onboarding-scan-dir', (event, rootDir) => {
    return scanAgentDir(String(rootDir || ''))
  })

  // 导入 Skills：{ items: [{sourceDir, name}], mode: 'hardlink'|'copy' }
  ipcMainRef.handle('onboarding-import-skills', (event, payload = {}) => {
    const items = Array.isArray(payload.items) ? payload.items : []
    const mode = payload.mode === 'copy' ? 'copy' : 'hardlink'
    const { skillsDir } = loopraDirs()
    fs.mkdirSync(skillsDir, { recursive: true })
    return items.map((item) => importSkill(
      String(item?.sourceDir || ''),
      String(item?.name || ''),
      mode,
      skillsDir
    ))
  })

  // 迁移会话：{ files: [{path, name}], workspaceHash }
  ipcMainRef.handle('onboarding-import-sessions', (event, payload = {}) => {
    const files = Array.isArray(payload.files) ? payload.files : []
    const workspaceHash = String(payload.workspaceHash || '')
    return files.map((file) => importSessionFile(
      String(file?.path || ''),
      workspaceHash,
      String(file?.name || '')
    ))
  })

  // 迁移 AGENTS.md：{ sourcePath, targetDir, overwrite }
  ipcMainRef.handle('onboarding-import-agents-md', (event, payload = {}) => {
    return importAgentsMd(
      String(payload.sourcePath || ''),
      String(payload.targetDir || ''),
      payload.overwrite === true
    )
  })

  ipcMainRef.handle('onboarding-read-text-file', (event, payload = {}) => {
    return readTextFile(String(payload.path || ''), payload.maxBytes)
  })

  // 解析外部 MCP 配置文件 → 规范化服务器列表
  ipcMainRef.handle('onboarding-parse-mcp-config', (event, filePath) => {
    return parseMcpConfig(String(filePath || ''))
  })
}

module.exports = { registerOnboardingIpc }
