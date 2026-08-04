<template>
  <SplashScreen @ready="onReady" @error="onError" />
</template>

<script setup>
import SplashScreen from './components/SplashScreen.vue'

// 启动窗口：承载检测 / 安装确认 / 在线安装 / 服务启动全流程。
// 全部完成后通知主进程：关闭本启动窗口并创建主窗口。
async function onReady() {
  try {
    await window.electronAPI?.splash?.ready()
  } catch (error) {
    console.error('[DesktopSplash] failed to notify ready:', error)
  }
}

function onError(error) {
  // SplashScreen 内部已展示错误界面（重试 / 一键安装 / 退出），此处仅记录
  console.error('[DesktopSplash] startup failed:', error)
}
</script>
