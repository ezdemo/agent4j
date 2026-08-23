import {ref} from 'vue'
import {applyHighlightTheme} from '../utils/highlight'

const theme = ref('light')

function loadTheme() {
  const saved = localStorage.getItem('loopra-theme')
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return saved || (prefersDark ? 'dark' : 'light')
}

function applyTheme(val) {
  document.documentElement.setAttribute('data-theme', val)
  localStorage.setItem('loopra-theme', val)
  theme.value = val
  // 同步切换 Shiki 语法高亮主题（已渲染消息会经 highlightVersion 自动重渲染）
  applyHighlightTheme(val)
}

// 初始化
applyTheme(loadTheme())

// 监听系统主题变化
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
  if (!localStorage.getItem('loopra-theme')) {
    applyTheme(e.matches ? 'dark' : 'light')
  }
})

export function useTheme() {
  return { theme, applyTheme }
}
