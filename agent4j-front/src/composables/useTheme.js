import { ref, watch } from 'vue'

const theme = ref('light')

function loadTheme() {
  const saved = localStorage.getItem('agent4j-theme')
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return saved || (prefersDark ? 'dark' : 'light')
}

function applyTheme(val) {
  document.documentElement.setAttribute('data-theme', val)
  localStorage.setItem('agent4j-theme', val)
  theme.value = val
}

// 初始化
applyTheme(loadTheme())

// 监听系统主题变化
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
  if (!localStorage.getItem('agent4j-theme')) {
    applyTheme(e.matches ? 'dark' : 'light')
  }
})

export function useTheme() {
  return { theme, applyTheme }
}
