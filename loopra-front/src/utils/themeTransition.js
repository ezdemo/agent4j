/**
 * 主题切换动画 — 从屏幕中心扩散的圆形遮罩
 * 遮罩先以目标主题背景色从中心铺满全屏，铺满瞬间再真正切换主题，
 * 实现「从中间往周围变黑/变白」的无缝视觉过渡
 */

// 各主题背景色，与 assets/styles/main.css 中对应主题的 --bg 保持一致
const THEME_BG = {
  gray: '#ffffff',
  dark: '#18181b',
}

const DURATION_MS = 600
const FALLBACK_MS = 800 // 兜底：transitionend 极端情况下未触发

let activeOverlay = null

/**
 * 以中心扩散动画切换主题
 * @param {string} target 目标主题名（gray / dark）
 * @param {(theme: string) => void} apply 动画完成后执行的真实切换
 */
export function switchThemeWithReveal(target, apply) {
  // 快速连点时，先取消上一次未完成的动画，避免遮罩叠加
  if (activeOverlay) {
    activeOverlay.remove()
    activeOverlay = null
  }
  // 用户偏好减少动效时直接切换
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    apply(target)
    return
  }

  const overlay = document.createElement('div')
  overlay.style.cssText =
    'position:fixed;inset:0;z-index:2147483647;pointer-events:none;' +
    `background:${THEME_BG[target] || '#ffffff'};clip-path:circle(0% at 50% 50%)`
  document.body.appendChild(overlay)
  activeOverlay = overlay

  const onEnd = () => {
    if (activeOverlay !== overlay) return
    activeOverlay = null
    overlay.remove()
    apply(target)
  }
  overlay.addEventListener('transitionend', onEnd)

  // 双 rAF：确保初始 clip-path 已生效后再触发过渡
  requestAnimationFrame(() => requestAnimationFrame(() => {
    overlay.style.transition = `clip-path ${DURATION_MS}ms cubic-bezier(0.4, 0, 0.2, 1)`
    overlay.style.clipPath = 'circle(150% at 50% 50%)'
  }))

  setTimeout(onEnd, DURATION_MS + FALLBACK_MS)
}
