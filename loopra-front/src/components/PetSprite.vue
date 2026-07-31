<template>
  <div class="pet-sprite" :class="{ 'pet-hidden': !loaded, 'pet-dragging': dragging }"
       :style="wrapStyle"
       @pointerenter="setInteractive(true)" @pointerleave="setInteractive(false)"
       @pointerdown="onPointerDown" @click.prevent="onClick" @contextmenu.prevent="toggleSizeControl">
    <div v-if="showSizeControl" class="pet-size-control" :class="{ 'has-bubble': bubbleText }"
         @pointerdown.stop="keepSizeControlOpen" @click.stop @contextmenu.prevent.stop>
      <input type="range" :min="MIN_PET_SCALE" :max="MAX_PET_SCALE" step="0.01"
             :value="petScale" aria-label="宠物大小" @input="updateScale" @change="commitScale">
    </div>
    <div v-if="bubbleText" class="pet-reply-bubble" role="status"
         @pointerdown.stop @click.stop @contextmenu.prevent.stop @wheel.stop>{{ bubbleText }}</div>
    <div class="pet-sprite-frame" :style="spriteStyle" />
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const ATLAS = { width: 1536, height: 1872, cellWidth: 192, cellHeight: 208 }

const PET_ANIMATIONS = {
  idle:            { row: 0, frameDurationsMs: [280, 110, 110, 140, 140, 320] },
  'running-right': { row: 1, frameDurationsMs: [120, 120, 120, 120, 120, 120, 120, 220] },
  'running-left':  { row: 2, frameDurationsMs: [120, 120, 120, 120, 120, 120, 120, 220] },
  waving:          { row: 3, frameDurationsMs: [140, 140, 140, 280] },
  jumping:         { row: 4, frameDurationsMs: [140, 140, 140, 140, 280] },
  failed:          { row: 5, frameDurationsMs: [140, 140, 140, 140, 140, 140, 140, 240] },
  waiting:         { row: 6, frameDurationsMs: [150, 150, 150, 150, 150, 260] },
  running:         { row: 7, frameDurationsMs: [120, 120, 120, 120, 120, 220] },
  review:          { row: 8, frameDurationsMs: [150, 150, 150, 150, 150, 280] },
}

const ACTION_LABELS = { idle: '空闲', 'running-right': '向右跑', 'running-left': '向左跑', waving: '挥手', jumping: '跳跃', waiting: '等待中', running: '调用工具', review: '思考中', failed: '失败了' }

// 空闲时随机播放的动画池（目前未在状态映射中使用的）
const IDLE_SELF_PLAY_POOL = ['running-right', 'running-left', 'waving', 'jumping']

const MIN_PET_SCALE = 0.25
const MAX_PET_SCALE = 1.25

// 兼容旧版离散大小配置，新尺寸用连续比例保存。
const SIZE_LEVELS = [
  { label: '小', scale: 0.4 },
  { label: '中', scale: 0.55 },
  { label: '大', scale: 0.75 },
  { label: '超大', scale: 1.0 },
]

const DRAG_THRESHOLD = 4

const props = defineProps({
  spritesheetUrl: { type: String, default: '' },
  state: { type: String, default: 'idle' },
  initialX: { type: Number, default: 0 },
  initialY: { type: Number, default: 0 },
  initialSizeIndex: { type: Number, default: 1 },
  initialScale: { type: Number, default: null },
  bubbleText: { type: String, default: '' },
  externalDrag: { type: Boolean, default: false },
})

const emit = defineEmits(['position-change', 'drag-move', 'interactive-change', 'scale-change', 'activate'])

const scaleForIndex = (index) => SIZE_LEVELS[index]?.scale || SIZE_LEVELS[1].scale
const normalizeScale = (value, fallback) => {
  const scale = Number(value)
  return Number.isFinite(scale) ? Math.min(MAX_PET_SCALE, Math.max(MIN_PET_SCALE, scale)) : fallback
}

const elapsedMs = ref(0)
const loaded = ref(false)
const petScale = ref(normalizeScale(props.initialScale, scaleForIndex(props.initialSizeIndex)))
const showSizeControl = ref(false)
let sizeControlTimer = null
const renderScale = computed(() => petScale.value * 0.75)
let frameId = 0
let animStart = 0

// ── 拖动 ──
const dragging = ref(false)
const dragDirection = ref(null)
const offsetX = ref(props.initialX)
const offsetY = ref(props.initialY)
let dragStartX = 0, dragStartY = 0
let dragStartScreenX = 0, dragStartScreenY = 0
let lastScreenX = 0, lastScreenY = 0
let hasDragged = false
let startOffsetX = 0, startOffsetY = 0

watch(() => props.initialX, v => { offsetX.value = v })
watch(() => props.initialY, v => { offsetY.value = v })
watch(() => props.initialScale, scale => {
  if (Number.isFinite(scale)) petScale.value = normalizeScale(scale, petScale.value)
})
watch(() => props.initialSizeIndex, index => {
  if (!Number.isFinite(props.initialScale)) petScale.value = scaleForIndex(index)
})

// ── 空闲自播放 ──
const randomAnim = ref(null)
let idleTimer = null

function scheduleIdleAction() {
  clearTimeout(idleTimer)
  idleTimer = setTimeout(() => {
    if (props.state === 'idle') {
      const pick = IDLE_SELF_PLAY_POOL[Math.floor(Math.random() * IDLE_SELF_PLAY_POOL.length)]
      randomAnim.value = pick
      animStart = performance.now()
      elapsedMs.value = 0
    }
  }, 4000 + Math.random() * 8000) // 4-12 秒随机间隔
}

// state prop → 动画 ID 映射
const animId = computed(() => {
  if (dragging.value && dragDirection.value) return `running-${dragDirection.value}`
  // 空闲自播放优先
  if (props.state === 'idle' && randomAnim.value) return randomAnim.value
  const s = props.state
  if (s === 'thinking')   return 'review'
  if (s === 'tool_call')  return 'running'
  if (s === 'content')    return 'review'
  if (s === 'waiting')    return 'waiting'
  if (s === 'failed')     return 'failed'
  return 'idle'
})

const actionLabel = computed(() => ACTION_LABELS[animId.value] || '空闲')
const anim = computed(() => PET_ANIMATIONS[animId.value])

const displayW = computed(() => Math.ceil(ATLAS.cellWidth * renderScale.value))
const displayH = computed(() => Math.ceil(ATLAS.cellHeight * renderScale.value))

const wrapStyle = computed(() => ({
  transform: `translate(${offsetX.value}px, ${offsetY.value}px)`,
}))

function getFrame(elapsed, animation) {
  const durations = animation.frameDurationsMs
  const total = durations.reduce((s, v) => s + v, 0)
  const cursor = ((elapsed % total) + total) % total
  let consumed = 0
  for (let i = 0; i < durations.length; i++) {
    consumed += durations[i]
    if (cursor < consumed) return i
  }
  return durations.length - 1
}

const spriteStyle = computed(() => {
  if (!loaded.value) return { width: displayW.value + 'px', height: displayH.value + 'px' }
  const frame = getFrame(elapsedMs.value, anim.value)
  const bgW = ATLAS.width * renderScale.value
  const bgH = ATLAS.height * renderScale.value
  return {
    width: displayW.value + 'px',
    height: displayH.value + 'px',
    backgroundImage: `url("${props.spritesheetUrl}")`,
    backgroundSize: `${bgW}px ${bgH}px`,
    backgroundPosition: `${-(frame * ATLAS.cellWidth * renderScale.value)}px ${-(anim.value.row * ATLAS.cellHeight * renderScale.value)}px`,
  }
})

// ── 拖动 ──
let saveTimer = null

function setInteractive(interactive) {
  if (props.externalDrag && (!dragging.value || interactive)) emit('interactive-change', interactive)
}

function onPointerDown(e) {
  if (e.button !== 0) return
  setInteractive(true)
  dragging.value = true
  dragDirection.value = null
  hasDragged = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragStartScreenX = lastScreenX = e.screenX
  dragStartScreenY = lastScreenY = e.screenY
  startOffsetX = offsetX.value
  startOffsetY = offsetY.value
  e.target.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e) {
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  const screenDx = e.screenX - dragStartScreenX
  const screenDy = e.screenY - dragStartScreenY
  if (!hasDragged && Math.abs(screenDx) + Math.abs(screenDy) > DRAG_THRESHOLD) hasDragged = true
  if (hasDragged) {
    if (screenDx !== 0) dragDirection.value = screenDx < 0 ? 'left' : 'right'
    if (props.externalDrag) {
      const moveX = e.screenX - lastScreenX
      const moveY = e.screenY - lastScreenY
      lastScreenX = e.screenX
      lastScreenY = e.screenY
      if (moveX || moveY) emit('drag-move', { x: moveX, y: moveY })
      return
    }
    offsetX.value = startOffsetX + dx
    offsetY.value = startOffsetY + dy
  }
}

function onPointerUp(e) {
  dragging.value = false
  dragDirection.value = null
  if (props.externalDrag) {
    const target = document.elementFromPoint(e.clientX, e.clientY)
    if (!target?.closest('.pet-sprite')) setInteractive(false)
  }
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  if (hasDragged && !props.externalDrag) {
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      emit('position-change', { x: offsetX.value, y: offsetY.value })
    }, 300)
  }
}

function onClick() {
  if (!hasDragged && props.externalDrag) emit('activate')
}

function toggleSizeControl() {
  showSizeControl.value = !showSizeControl.value
  if (showSizeControl.value) keepSizeControlOpen()
  else clearTimeout(sizeControlTimer)
}

function keepSizeControlOpen() {
  clearTimeout(sizeControlTimer)
  sizeControlTimer = setTimeout(() => { showSizeControl.value = false }, 2000)
}

function updateScale(event) {
  petScale.value = normalizeScale(event.target.value, petScale.value)
  keepSizeControlOpen()
}

function commitScale() {
  emit('scale-change', petScale.value)
  keepSizeControlOpen()
}

// ── 动画 ──
function tick(now) {
  elapsedMs.value = now - animStart

  // 空闲自播动画播完一轮后恢复 idle 并排下一轮
  if (randomAnim.value) {
    const anim = PET_ANIMATIONS[randomAnim.value]
    const total = anim.frameDurationsMs.reduce((s, v) => s + v, 0)
    if (elapsedMs.value >= total) {
      randomAnim.value = null
      animStart = now
      elapsedMs.value = 0
      scheduleIdleAction()
    }
  }

  frameId = requestAnimationFrame(tick)
}

// state 变化时重置动画计时
watch(animId, () => {
  animStart = performance.now()
  elapsedMs.value = 0
  // 非 idle 时取消自播计时
  if (props.state !== 'idle') {
    randomAnim.value = null
    clearTimeout(idleTimer)
  } else if (!randomAnim.value) {
    // 回到 idle 时启动自播
    scheduleIdleAction()
  }
})

function startAnimation() {
  loaded.value = true
  animStart = performance.now()
  elapsedMs.value = 0
  frameId = requestAnimationFrame(tick)
  scheduleIdleAction()
}

onMounted(() => {
  if (!props.spritesheetUrl) return
  const img = new Image()
  img.onload = startAnimation
  img.src = props.spritesheetUrl
})

onBeforeUnmount(() => {
  if (frameId) cancelAnimationFrame(frameId)
  clearTimeout(saveTimer)
  clearTimeout(idleTimer)
  clearTimeout(sizeControlTimer)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
})

watch(() => props.spritesheetUrl, (url) => {
  if (!url) return
  if (frameId) cancelAnimationFrame(frameId)
  loaded.value = false
  const img = new Image()
  img.onload = startAnimation
  img.src = url
})
</script>

<style scoped>
.pet-sprite {
  position: relative;
  cursor: grab;
  user-select: none;
  transition: opacity 0.3s ease, filter 0.15s ease;
  flex-shrink: 0;
  touch-action: none;
}
.pet-sprite.pet-hidden { opacity: 0; }
.pet-sprite.pet-dragging { cursor: grabbing; opacity: 0.85; }
.pet-size-control {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  z-index: 3;
  width: 164px;
  padding: 8px 11px;
  box-sizing: border-box;
  background: #fffdf7;
  border: 1px solid rgba(83, 63, 39, 0.2);
  border-radius: 8px;
  box-shadow: 0 5px 18px rgba(35, 25, 15, 0.18);
  transform: translateX(-50%);
}
.pet-size-control.has-bubble { bottom: calc(100% + 118px); }
.pet-size-control input {
  display: block;
  width: 100%;
  margin: 0;
  accent-color: #5b7c51;
  cursor: ew-resize;
}
.pet-reply-bubble {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  z-index: 2;
  width: min(200px, 78vw);
  max-height: 92px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 10px 13px;
  box-sizing: border-box;
  color: #24201b;
  background: #fffdf7;
  border: 1px solid rgba(83, 63, 39, 0.2);
  border-radius: 12px;
  box-shadow: 0 5px 18px rgba(35, 25, 15, 0.18);
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  cursor: default;
  pointer-events: auto;
  touch-action: pan-y;
  transform: translateX(-50%);
}
.pet-reply-bubble::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -7px;
  width: 12px;
  height: 12px;
  background: #fffdf7;
  border-right: 1px solid rgba(83, 63, 39, 0.2);
  border-bottom: 1px solid rgba(83, 63, 39, 0.2);
  transform: translateX(-50%) rotate(45deg);
}
.pet-sprite-frame {
  background-repeat: no-repeat;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}
</style>
