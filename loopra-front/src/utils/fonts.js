/**
 * 中文字体切换（仅桌面端，设置 → 外观 → 中文字体）。
 * 英文/数字统一使用 JetBrains Mono Variable，font 为选中的系统字体名；
 * 'system' 或空表示不指定中文字体（跟随系统，如微软雅黑/苹方）。
 */
export const DEFAULT_FONT = 'system'

const SANS_TAIL = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif"
const MONO_TAIL = "'SF Mono', 'Cascadia Code', 'Fira Code', Consolas, monospace"

/**
 * 根据字体名生成 --sans / --mono 完整值并应用到根元素内联样式
 * （内联样式优先级高于 main.css 的 :root 定义）。
 */
export const applyFontPreset = (font) => {
  let cn = ''
  if (typeof font === 'string' && font.trim() && font !== 'system') {
    // 系统字体名按字面量使用，去除引号防注入
    cn = `'${font.trim().replace(/'/g, '')}', `
  }
  document.documentElement.style.setProperty('--sans', `'JetBrains Mono Variable', ${cn}${SANS_TAIL}`)
  document.documentElement.style.setProperty('--mono', `'JetBrains Mono Variable', ${cn}${MONO_TAIL}`)
}
