const SETI_ICONS = {
  default: [0xE021, '#6d8086'],
  vue: [0xE094, '#8dc149'],
  javascript: [0xE04D, '#cbcb41'],
  react: [0xE077, '#519aba'],
  typescript: [0xE091, '#519aba'],
  java: [0xE04C, '#cc3e44'],
  kotlin: [0xE054, '#e37933'],
  json: [0xE051, '#cbcb41'],
  markdown: [0xE05C, '#519aba'],
  css: [0xE01B, '#519aba'],
  sass: [0xE07D, '#f55385'],
  html: [0xE044, '#e37933'],
  xml: [0xE09C, '#e37933'],
  yaml: [0xE09E, '#a074c4'],
  python: [0xE075, '#519aba'],
  go: [0xE037, '#519aba'],
  rust: [0xE07B, '#6d8086'],
  php: [0xE06C, '#a074c4'],
  shell: [0xE082, '#4d5a5e'],
  powershell: [0xE06F, '#519aba'],
  database: [0xE020, '#f55385'],
  image: [0xE048, '#a074c4'],
  svg: [0xE089, '#a074c4'],
  pdf: [0xE069, '#cc3e44'],
  archive: [0xE09F, '#cc3e44'],
  config: [0xE017, '#6d8086'],
  git: [0xE032, '#41535b'],
  npm: [0xE063, '#41535b'],
  docker: [0xE023, '#519aba'],
  maven: [0xE05D, '#cc3e44'],
  gradle: [0xE038, '#519aba']
}

const EXTENSION_ICONS = {
  vue: 'vue', js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'react',
  ts: 'typescript', mts: 'typescript', cts: 'typescript', tsx: 'react', java: 'java',
  kt: 'kotlin', kts: 'kotlin', json: 'json', jsonc: 'json', md: 'markdown', mdx: 'markdown',
  css: 'css', scss: 'sass', sass: 'sass', less: 'css', html: 'html', htm: 'html',
  xml: 'xml', yml: 'yaml', yaml: 'yaml', py: 'python', pyw: 'python', go: 'go', rs: 'rust',
  php: 'php', sh: 'shell', bash: 'shell', zsh: 'shell', ps1: 'powershell', sql: 'database',
  db: 'database', sqlite: 'database', png: 'image', jpg: 'image', jpeg: 'image', gif: 'image',
  webp: 'image', ico: 'image', svg: 'svg', pdf: 'pdf', zip: 'archive', rar: 'archive',
  '7z': 'archive', tar: 'archive', gz: 'archive', toml: 'config', ini: 'config', conf: 'config'
}

function iconKind(name) {
  const lower = String(name || '').toLowerCase()
  if (/^(package(-lock)?|pnpm-lock|npm-shrinkwrap)\.json$/.test(lower)) return 'npm'
  if (lower === 'pom.xml') return 'maven'
  if (/^(build|settings)\.gradle(\.kts)?$/.test(lower)) return 'gradle'
  if (lower === 'dockerfile' || lower.startsWith('docker-compose.')) return 'docker'
  if (/^\.git(ignore|attributes|modules)?$/.test(lower)) return 'git'
  if (lower === '.env' || lower.startsWith('.env.')) return 'config'
  const dot = lower.lastIndexOf('.')
  return EXTENSION_ICONS[dot >= 0 ? lower.slice(dot + 1) : ''] || 'default'
}

export function fileIconFor(name) {
  const kind = iconKind(name)
  const [codePoint, color] = SETI_ICONS[kind]
  return {kind, color, glyph: String.fromCodePoint(codePoint)}
}
