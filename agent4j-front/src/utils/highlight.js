/**
 * 语法高亮工具 — 基于 highlight.js
 * 用于聊天代码块和 Git diff 面板
 */
import hljs from 'highlight.js'
import { Marked, Renderer, marked } from 'marked'

// ═══════════════════════════════════════════
// 文件扩展名 → highlight.js 语言标识映射
// ═══════════════════════════════════════════
const EXT_MAP = {
  js: 'javascript', jsx: 'javascript', mjs: 'javascript', cjs: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  vue: 'xml',
  html: 'xml', htm: 'xml',
  css: 'css', scss: 'scss', less: 'less',
  json: 'json', jsonc: 'json',
  py: 'python', pyw: 'python',
  java: 'java', kt: 'kotlin', kts: 'kotlin',
  go: 'go',
  rs: 'rust',
  c: 'c', h: 'c', cpp: 'cpp', cxx: 'cpp', hpp: 'cpp',
  rb: 'ruby',
  php: 'php',
  swift: 'swift',
  sh: 'bash', bash: 'bash', zsh: 'bash',
  yml: 'yaml', yaml: 'yaml',
  xml: 'xml', svg: 'xml',
  sql: 'sql',
  md: 'markdown', markdown: 'markdown',
  dockerfile: 'dockerfile',
  toml: 'ini',
  ini: 'ini', cfg: 'ini', conf: 'ini',
  env: 'bash',
  bat: 'dos', cmd: 'dos', ps1: 'powershell',
  lua: 'lua',
  r: 'r',
  dart: 'dart',
  proto: 'protobuf',
  graphql: 'graphql', gql: 'graphql',
  cs: 'csharp',
  scala: 'scala',
  elm: 'elm',
  hs: 'haskell',
  clj: 'clojure', cljs: 'clojure',
  ex: 'elixir', exs: 'elixir',
  erl: 'erlang',
  fs: 'fsharp', fsi: 'fsharp',
  groovy: 'groovy',
  jl: 'julia',
  pl: 'perl', pm: 'perl',
  sol: 'solidity',
  tf: 'hcl', tfvars: 'hcl',
}

/**
 * HTML 转义（高亮失败时的回退）
 */
function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 高亮代码块
 * @param {string} code 原始代码
 * @param {string|null} language 语言标识（可选）
 * @returns {string} 高亮后的 HTML
 */
export function highlightCode(code, language) {
  if (!code) return ''
  try {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language }).value
    }
    const result = hljs.highlightAuto(code)
    return result.value
  } catch {
    return escapeHtml(code)
  }
}

/**
 * 根据文件路径推断语言
 * @param {string} filePath 文件路径
 * @returns {string|null} 语言标识
 */
export function detectLanguage(filePath) {
  if (!filePath) return null
  const parts = filePath.split('.')
  if (parts.length < 2) return null
  const ext = parts[parts.length - 1].toLowerCase()
  return EXT_MAP[ext] || null
}

// ═══════════════════════════════════════════
// 共享的 marked 实例 — 语法高亮 + 复制按钮
// ═══════════════════════════════════════════
const COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'

const renderer = new Renderer()
renderer.code = (code, language) => {
  const highlighted = highlightCode(code, language || null)
  const lang = language ? ` class="language-${language}"` : ''
  return `<div class="code-block-wrap">
    <pre><code${lang}>${highlighted}</code><button class="code-copy-btn" onclick="window.copyCode && window.copyCode(this)" title="复制代码">${COPY_ICON}</button></pre>
  </div>`
}

export const md = new Marked({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false,
  renderer
})

// 兼容旧的全局 marked() 调用
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false,
  renderer
})
