/**
 * Markdown 渲染 + 语法高亮 — 基于 markdown-it + Shiki（VS Code 同款 TextMate 引擎）
 * - markdown-it：GFM（表格/任务列表）、texmath（KaTeX 公式）、GitHub 风格告警框（> [!NOTE] 与 :::note）
 * - Shiki：预加载常用语言后 codeToHtml 可同步调用；未加载语言按需加载并触发全局重渲染
 * - 主题切换：按应用主题选择 shiki 主题并 bump highlightVersion，所有已渲染内容重渲染
 */
import MarkdownIt from 'markdown-it'
import container from 'markdown-it-container'
import taskLists from 'markdown-it-task-lists'
import texmath from 'markdown-it-texmath'
import katex from 'katex'
import {ref} from 'vue'
import {createHighlighterCore} from 'shiki/core'
import {createOnigurumaEngine} from 'shiki/engine/oniguruma'
import {LRUCache} from './cache'
import {CHEVRON_DOWN_ICON, CODE_ICON} from './icons'

// 主题（应用主题映射值；preload 后 codeToHtml 同步可用）
import githubLight from '@shikijs/themes/github-light'
import githubDark from '@shikijs/themes/github-dark'

// 预载语言（静态导入 → vite 合并进同一 chunk，避免运行时逐个网络请求）
import langJavascript from '@shikijs/langs/javascript'
import langTypescript from '@shikijs/langs/typescript'
import langJsx from '@shikijs/langs/jsx'
import langTsx from '@shikijs/langs/tsx'
import langVue from '@shikijs/langs/vue'
import langHtml from '@shikijs/langs/html'
import langCss from '@shikijs/langs/css'
import langScss from '@shikijs/langs/scss'
import langLess from '@shikijs/langs/less'
import langJson from '@shikijs/langs/json'
import langPython from '@shikijs/langs/python'
import langJava from '@shikijs/langs/java'
import langKotlin from '@shikijs/langs/kotlin'
import langGo from '@shikijs/langs/go'
import langRust from '@shikijs/langs/rust'
import langC from '@shikijs/langs/c'
import langCpp from '@shikijs/langs/cpp'
import langCsharp from '@shikijs/langs/csharp'
import langRuby from '@shikijs/langs/ruby'
import langPhp from '@shikijs/langs/php'
import langSwift from '@shikijs/langs/swift'
import langBash from '@shikijs/langs/bash'
import langYaml from '@shikijs/langs/yaml'
import langXml from '@shikijs/langs/xml'
import langSql from '@shikijs/langs/sql'
import langMarkdown from '@shikijs/langs/markdown'
import langDockerfile from '@shikijs/langs/dockerfile'
import langIni from '@shikijs/langs/ini'
import langToml from '@shikijs/langs/toml'
import langPowershell from '@shikijs/langs/powershell'
import langLua from '@shikijs/langs/lua'
import langDart from '@shikijs/langs/dart'
import langGraphql from '@shikijs/langs/graphql'
import langScala from '@shikijs/langs/scala'
import langHaskell from '@shikijs/langs/haskell'
import langClojure from '@shikijs/langs/clojure'
import langElixir from '@shikijs/langs/elixir'
import langErlang from '@shikijs/langs/erlang'
import langDiff from '@shikijs/langs/diff'

const PRELOAD_LANGS = [
  langJavascript, langTypescript, langJsx, langTsx, langVue,
  langHtml, langCss, langScss, langLess, langJson,
  langPython, langJava, langKotlin, langGo, langRust,
  langC, langCpp, langCsharp, langRuby, langPhp, langSwift,
  langBash, langYaml, langXml, langSql, langMarkdown,
  langDockerfile, langIni, langToml, langPowershell,
  langLua, langDart, langGraphql, langScala, langHaskell,
  langClojure, langElixir, langErlang, langDiff
]

// 长尾语言按需加载（代码块中出现时再拉取，完成后自动重渲染）
const LAZY_LANGS = {
  r: () => import('@shikijs/langs/r'),
  perl: () => import('@shikijs/langs/perl'),
  bat: () => import('@shikijs/langs/bat'),
  cmake: () => import('@shikijs/langs/cmake'),
  makefile: () => import('@shikijs/langs/makefile'),
  json5: () => import('@shikijs/langs/json5'),
  solidity: () => import('@shikijs/langs/solidity'),
  hcl: () => import('@shikijs/langs/hcl'),
  protobuf: () => import('@shikijs/langs/protobuf'),
  groovy: () => import('@shikijs/langs/groovy'),
  elm: () => import('@shikijs/langs/elm'),
  fsharp: () => import('@shikijs/langs/fsharp'),
  julia: () => import('@shikijs/langs/julia'),
  nginx: () => import('@shikijs/langs/nginx'),
  svelte: () => import('@shikijs/langs/svelte'),
  astro: () => import('@shikijs/langs/astro'),
  mdx: () => import('@shikijs/langs/mdx'),
  regex: () => import('@shikijs/langs/regex'),
  tcl: () => import('@shikijs/langs/tcl'),
  vb: () => import('@shikijs/langs/vb'),
  'objective-c': () => import('@shikijs/langs/objective-c')
}

// ═══════════════════════════════════════════
// 主题映射：应用主题 → shiki 主题（亮色系统一用 github-light，暗色用 github-dark）
// ═══════════════════════════════════════════
const THEME_MAP = {
  light: 'github-light',
  gray: 'github-light',
  'retro-yellow': 'github-light',
  dark: 'github-dark',
  retro: 'github-light'
}
const DEFAULT_THEME = 'github-light'

// ═══════════════════════════════════════════
// Shiki 单例：预加载常用语言后 codeToHtml 同步可用
// ═══════════════════════════════════════════
let highlighter = null
let initPromise = null
const pendingLangs = new Set() // 加载中的语言
const unknownLangs = new Set() // 无语法定义的语言（避免反复尝试）
let currentTheme = DEFAULT_THEME

/** 渲染版本信号：主题切换 / 按需语言加载完成后 bump，触发所有已渲染内容刷新 */
export const highlightVersion = ref(0)

function bumpVersion() {
  highlightVersion.value++
}

/** 初始化 Shiki（异步预载主题与常用语言），可重复调用、全局单例 */
export function initHighlight() {
  if (initPromise) return initPromise
  initPromise = createHighlighterCore({
    themes: [githubLight, githubDark],
    langs: PRELOAD_LANGS,
    engine: createOnigurumaEngine(import('shiki/wasm'))
  })
    .then((h) => {
      highlighter = h
      bumpVersion() // 高亮就绪后刷新已渲染内容（此前代码块为纯文本回退）
    })
    .catch((error) => {
      console.error('[highlight] shiki init failed, code blocks will render as plain text:', error)
      initPromise = null
    })
  return initPromise
}

/** 应用主题切换（应用主题名：light/dark/retro/retro-yellow/gray） */
export function applyHighlightTheme(appTheme) {
  const next = THEME_MAP[appTheme] || DEFAULT_THEME
  if (next !== currentTheme) {
    currentTheme = next
    bumpVersion()
  }
}

// 文件扩展名 → shiki 语言标识映射（shiki 别名如 js/ts 也可直接用，这里归一化到正式名称）
const EXT_MAP = {
  js: 'javascript', jsx: 'jsx', mjs: 'javascript', cjs: 'javascript',
  ts: 'typescript', tsx: 'tsx',
  vue: 'vue',
  html: 'html', htm: 'html',
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
  toml: 'toml',
  ini: 'ini', cfg: 'ini', conf: 'ini',
  env: 'bash',
  bat: 'bat', cmd: 'bat', ps1: 'powershell',
  lua: 'lua',
  r: 'r',
  dart: 'dart',
  proto: 'protobuf',
  graphql: 'graphql', gql: 'graphql',
  cs: 'csharp',
  scala: 'scala',
  hs: 'haskell',
  clj: 'clojure', cljs: 'clojure',
  ex: 'elixir', exs: 'elixir',
  erl: 'erlang',
  diff: 'diff'
}

// 别名归一（fence 里常见写法 → shiki 正式名，便于 getLoadedLanguages 命中）
const ALIAS_MAP = {
  js: 'javascript', ts: 'typescript', mjs: 'javascript', cjs: 'javascript',
  py: 'python', rs: 'rust', rb: 'ruby', sh: 'bash', shell: 'bash',
  yml: 'yaml', md: 'markdown', kt: 'kotlin', kts: 'kotlin',
  bat: 'bat', cmd: 'bat', ps1: 'powershell', cs: 'csharp',
  vue: 'vue', text: 'plaintext', plain: 'plaintext', txt: 'plaintext',
  gql: 'graphql', yaml: 'yaml', docker: 'dockerfile',
  'c++': 'cpp', hpp: 'cpp', h: 'c', cxx: 'cpp'
}

// ═══════════════════════════════════════════
// 同步高亮（缓存 + 按需加载语言）
// ═══════════════════════════════════════════
const codeCache = new LRUCache(500)

// 无需语法定义的语言（当作纯文本展示，避免反复尝试加载）
const PLAIN_LANGS = new Set(['', 'text', 'txt', 'plaintext', 'plain'])

/**
 * 提取 shiki codeToHtml 输出中 <code> 内部的 token span HTML
 * （对外只返回 token 内联 span，便于 DiffViewer 等复用现有容器结构）
 */
function shikiTokenHtml(code, lang) {
  if (!highlighter) return ''
  const useLang = lang || 'plaintext'
  if (PLAIN_LANGS.has(useLang)) return ''
  const loaded = highlighter.getLoadedLanguages()
  if (!loaded.includes(useLang)) {
    // 语言未加载：后台按需加载（存在语法定义时），完成后重渲染
    if (!pendingLangs.has(useLang) && !unknownLangs.has(useLang)) {
      const loader = LAZY_LANGS[useLang]
      if (loader) {
        pendingLangs.add(useLang)
        highlighter.loadLanguage(loader())
          .then(() => { pendingLangs.delete(useLang); bumpVersion() })
          .catch(() => { pendingLangs.delete(useLang); unknownLangs.add(useLang) })
      } else {
        unknownLangs.add(useLang)
      }
    }
    return ''
  }
  try {
    const html = highlighter.codeToHtml(code, { lang: useLang, theme: currentTheme })
    const m = /<code[^>]*>([\s\S]*)<\/code>/.exec(html)
    return m ? m[1] : ''
  } catch (error) {
    console.warn('[highlight] codeToHtml failed, fallback to plain text:', error)
    return ''
  }
}

/**
 * 语法高亮：返回 token 内联 span HTML（与 markdown-it fence 共用缓存）
 * 高亮未就绪 / 语言未加载时返回空字符串（调用方回退为纯文本）
 */
export function highlightCode(code, language) {
  if (!code) return ''
  const lang = language ? normalizeLang(language) : ''
  const cacheKey = `${currentTheme}|${lang}|${code}`
  const cached = codeCache.get(cacheKey)
  if (cached !== undefined) return cached
  const html = shikiTokenHtml(code, lang)
  codeCache.set(cacheKey, html)
  return html
}

/** 根据文件路径推断语言（shiki 语言标识） */
export function detectLanguage(filePath) {
  if (!filePath) return null
  const parts = filePath.split('.')
  if (parts.length < 2) return null
  const ext = parts[parts.length - 1].toLowerCase()
  return EXT_MAP[ext] || null
}

/** fence 语言标识归一化（别名 → 正式名，未知返回原值） */
function normalizeLang(lang) {
  if (!lang) return ''
  const trimmed = String(lang).trim().toLowerCase()
  return ALIAS_MAP[trimmed] || trimmed
}

// ═══════════════════════════════════════════
// HTML 转义与辅助
// ═══════════════════════════════════════════
function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function escapeAttribute(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

const COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>'

const isLocalFilePath = href => /^(?:[A-Za-z]:\/)?(?:[\w@.-]+\/)+[\w@.-]+\.[\w-]+(?::\d+)?$/i.test(String(href).replace(/\\/g, '/'))

// ═══════════════════════════════════════════
// markdown-it 实例（GFM + KaTeX + 告警框 + 任务列表 + 自定义链接/代码块渲染）
// ═══════════════════════════════════════════
export const md = new MarkdownIt({
  breaks: true,
  html: true,
  linkify: false,
  typographer: false
})

// KaTeX 数学公式（$...$ 行内 / $$...$$ 块级）
md.use(texmath, {
  engine: katex,
  delimiters: 'dollars',
  katexOptions: { throwOnError: false, output: 'html' }
})

// GFM 任务列表（- [x] / - [ ]）
md.use(taskLists, { enabled: true, label: true })

// GitHub 风格告警框（> [!NOTE] 等），复用 markdown-alert 样式
const ALERT_KEYWORDS = ['note', 'tip', 'important', 'warning', 'caution']
function findAlertType(tokens, idx) {
  const depth = tokens[idx].level
  for (let i = idx + 1; i < tokens.length; i++) {
    const token = tokens[i]
    if (token.level < depth) break
    if (token.type === 'inline' && token.children) {
      for (const child of token.children) {
        if (child.type === 'text') {
          const m = /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/i.exec(child.content)
          if (m) {
            child.content = child.content.slice(m[0].length)
            return m[1].toLowerCase()
          }
          return null
        }
        if (child.type === 'softbreak' || child.type === 'hardbreak') return null
      }
      return null
    }
  }
  return null
}

const defaultBlockquoteOpen = md.renderer.rules.blockquote_open
const defaultBlockquoteClose = md.renderer.rules.blockquote_close

md.renderer.rules.blockquote_open = (tokens, idx, options, env, self) => {
  const type = findAlertType(tokens, idx)
  if (type) {
    if (!env._alertStack) env._alertStack = []
    env._alertStack.push(type)
    return `<div class="markdown-alert markdown-alert-${type}">\n`
  }
  return defaultBlockquoteOpen ? defaultBlockquoteOpen(tokens, idx, options, env, self) : '<blockquote>\n'
}

md.renderer.rules.blockquote_close = (tokens, idx, options, env, self) => {
  if (env._alertStack && env._alertStack.length > 0) {
    env._alertStack.pop()
    return '</div>\n'
  }
  return defaultBlockquoteClose ? defaultBlockquoteClose(tokens, idx, options, env, self) : '</blockquote>\n'
}

// Obsidian 风格容器（:::note / :::tip / :::important / :::warning / :::caution）
for (const type of ALERT_KEYWORDS) {
  md.use(container, type, {
    validate: (params) => params.trim().split(/\s+/)[0] === type,
    render: (tokens, idx) => tokens[idx].nesting === 1
      ? `<div class="markdown-alert markdown-alert-${type}">\n`
      : '</div>\n'
  })
}

// 链接：本地文件路径 → ai-file-link（前端拦截打开预览）；其余新窗口打开
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet('href') || ''
  if (isLocalFilePath(href)) {
    token.attrSet('href', '#')
    token.attrSet('class', 'ai-link ai-file-link')
    token.attrSet('data-file-path', href)
    token.attrSet('title', '打开文件预览')
  } else {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener')
    token.attrSet('class', 'ai-link')
  }
  return self.renderToken(tokens, idx, options)
}

// 代码块：Shiki 高亮 + [代码图标] 语言 行数 头部 + 复制按钮；超过 6 行默认折叠（底部渐隐 + 点击头部展开）
md.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const code = token.content
  const rawLang = token.info ? token.info.trim().split(/\s+/)[0] : ''
  const lang = normalizeLang(rawLang)
  const cacheKey = `${currentTheme}|${lang}|${code}`
  let spans = codeCache.get(cacheKey)
  if (spans === undefined) {
    spans = shikiTokenHtml(code, lang)
    codeCache.set(cacheKey, spans)
  }
  const langLabel = rawLang ? escapeHtml(rawLang) : ''
  const langBadge = langLabel ? `<span class="code-block-lang">${langLabel}</span>` : ''
  const langClass = langLabel ? ` class="language-${escapeAttribute(rawLang)}"` : ''
  const lineCount = code.replace(/\n$/, '').split('\n').length
  const collapsible = lineCount > 6
  // 顶部条：与执行折叠块 (.tool-head) 同构 —— [代码图标] 语言 行数 + [复制] + chevron
  // 所有代码块都展示；只有 >6 行的可点击整行展开/收起（点击头部无箭头时不生效）
  const codeIcon = `<span class="code-block-icon" aria-hidden="true">${CODE_ICON}</span>`
  const lineLabel = `<span class="code-block-meta">${lineCount} 行</span>`
  const chevron = collapsible ? `<span class="code-block-chevron">${CHEVRON_DOWN_ICON}</span>` : ''
  const copyBtn = `<button class="code-copy-btn" type="button" title="复制代码">${COPY_ICON}</button>`
  const topbar = `<div class="code-block-top">${codeIcon}${langBadge}${lineLabel}${chevron}${copyBtn}</div>`
  const hasTopClass = ' has-top'
  return `<div class="code-block-wrap${hasTopClass}${collapsible ? ' collapsible' : ''}">\n`
    + `${topbar}<pre><code${langClass}>${spans || escapeHtml(code)}</code></pre>\n</div>\n`
}

// 自动启动 Shiki 加载（不阻塞页面渲染；未就绪时先回退纯文本，就绪后 bumpVersion 自动刷新）
initHighlight()