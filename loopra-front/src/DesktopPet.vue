<template>
  <main class="desktop-pet" :class="{ ready: spritesheetUrl }">
    <PetSprite
      v-if="spritesheetUrl"
      :key="spriteKey"
      :spritesheet-url="spritesheetUrl"
      state="idle"
      :initial-size-index="sizeIndex"
      external-drag
      @drag-move="moveWindow"
      @interactive-change="setInteractive"
      @size-change="saveSize"
    />
  </main>
</template>

<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import PetSprite from './components/PetSprite.vue'
import {petAPI} from './services/api'

const spritesheetUrl = ref('')
const sizeIndex = ref(1)
const spriteKey = ref(0)
const activePetKey = ref('')
let refreshTimer = null
let removeRefreshListener = null

async function loadPet() {
  try {
    const response = await petAPI.getActive()
    const pet = response?.data
    if (!response?.success || !pet?.active || !(pet.spritesheetUrl || pet.spritesheetPath)) {
      spritesheetUrl.value = ''
      activePetKey.value = ''
      return
    }
    const url = pet.spritesheetUrl || pet.spritesheetPath
    const petKey = pet.name || url
    if (activePetKey.value !== petKey) {
      activePetKey.value = petKey
      spritesheetUrl.value = `${url}?t=${Date.now()}`
      spriteKey.value++
    }
    if (typeof pet.sizeIndex === 'number') sizeIndex.value = pet.sizeIndex
  } catch {
    spritesheetUrl.value = ''
  }
}

function moveWindow(delta) {
  window.electronAPI?.desktopPet?.moveBy(delta)
}

function setInteractive(interactive) {
  window.electronAPI?.desktopPet?.setInteractive(interactive)
}

async function saveSize(nextSizeIndex) {
  sizeIndex.value = nextSizeIndex
  try {
    await petAPI.savePosition({sizeIndex: nextSizeIndex})
  } catch {
  }
}

onMounted(() => {
  window.electronAPI?.desktopPet?.setInteractive(false)
  loadPet()
  refreshTimer = setInterval(loadPet, 4000)
  removeRefreshListener = window.electronAPI?.desktopPet?.onRefresh(loadPet)
})

onBeforeUnmount(() => {
  window.electronAPI?.desktopPet?.setInteractive(true)
  if (refreshTimer) clearInterval(refreshTimer)
  removeRefreshListener?.()
})
</script>

<style scoped>
:global(html),
:global(body),
:global(#app) {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: transparent !important;
}

.desktop-pet {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  opacity: 0;
  transition: opacity 160ms ease;
}

.desktop-pet.ready {
  opacity: 1;
}
</style>
