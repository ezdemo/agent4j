/**
 * highlight.js 主题动态切换
 * 根据应用主题（light/dark/retro/retro-yellow）自动加载对应的语法高亮 CSS
 * retro 主题是浅绿风格，使用亮色背景
 */
import githubDarkCss from 'highlight.js/styles/github-dark.css?inline'
import githubCss from 'highlight.js/styles/github.css?inline'

// 主题 → CSS 映射：亮背景用 github.css，暗背景用 github-dark.css
const themeStyles = {
  light: githubCss,
  gray: githubCss,
  'retro-yellow': githubCss,
  dark: githubDarkCss,
  retro: githubCss,  // 浅绿 是亮色背景
}

let styleEl = null

/**
 * 切换 highlight.js 语法高亮主题
 * @param {string} theme 应用主题名：light | dark | retro | retro-yellow
 */
export function applyHljsTheme(theme) {
  if (!styleEl) {
    styleEl = document.createElement('style')
    styleEl.id = 'hljs-theme-style'
    document.head.appendChild(styleEl)
  }
  const css = themeStyles[theme] || githubDarkCss
  styleEl.textContent = css
}

// 页面加载时立即应用（在 JS 模块解析阶段就已确定初始主题）
const initialTheme = localStorage.getItem('loopra-theme')
  || document.documentElement.getAttribute('data-theme')
  || 'light'
applyHljsTheme(initialTheme)
