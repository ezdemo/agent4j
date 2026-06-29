<template>
  <div class="pet-sprite" :class="{ 'pet-hidden': !loaded, 'pet-dragging': dragging }"
       :style="wrapStyle"
       @pointerdown="onPointerDown" @click.prevent="onClick">
    <div class="pet-sprite-frame" :style="spriteStyle" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

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

// 大小档位：[label, scale]
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
})

const emit = defineEmits(['position-change', 'size-change'])

const elapsedMs = ref(0)
const loaded = ref(false)
const sizeIndex = ref(props.initialSizeIndex)
const renderScale = computed(() => SIZE_LEVELS[sizeIndex.value].scale * 0.75)
let frameId = 0
let animStart = 0

// ── 拖动 ──
const dragging = ref(false)
const offsetX = ref(props.initialX)
const offsetY = ref(props.initialY)
let dragStartX = 0, dragStartY = 0
let hasDragged = false
let startOffsetX = 0, startOffsetY = 0

watch(() => props.initialX, v => { offsetX.value = v })
watch(() => props.initialY, v => { offsetY.value = v })
watch(() => props.initialSizeIndex, v => { sizeIndex.value = v })

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

function onPointerDown(e) {
  dragging.value = true
  hasDragged = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  startOffsetX = offsetX.value
  startOffsetY = offsetY.value
  e.target.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e) {
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (!hasDragged && Math.abs(dx) + Math.abs(dy) > DRAG_THRESHOLD) hasDragged = true
  if (hasDragged) {
    offsetX.value = startOffsetX + dx
    offsetY.value = startOffsetY + dy
  }
}

function onPointerUp() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  if (hasDragged) {
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => {
      emit('position-change', { x: offsetX.value, y: offsetY.value })
    }, 300)
  }
}

function onClick() {
  if (hasDragged) return
  sizeIndex.value = (sizeIndex.value + 1) % SIZE_LEVELS.length
  emit('size-change', sizeIndex.value)
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
  cursor: grab;
  user-select: none;
  transition: opacity 0.3s ease, filter 0.15s ease;
  flex-shrink: 0;
  touch-action: none;
}
.pet-sprite.pet-hidden { opacity: 0; }
.pet-sprite.pet-dragging { cursor: grabbing; opacity: 0.85; }
.pet-sprite-frame {
  background-repeat: no-repeat;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}
</style>
