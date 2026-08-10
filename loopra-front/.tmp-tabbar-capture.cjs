// 临时验证脚本：加载 DesktopShell 页面，创建多个会话，截图并输出 tab 布局诊断信息
// 验证后删除本文件
const { app, BrowserWindow } = require('electron')
const path = require('path')
const fs = require('fs')

const OUT = path.join(__dirname, '.tmp-tabbar-shots')
fs.mkdirSync(OUT, { recursive: true })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    show: true,
    autoHideMenuBar: true,
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  })
  await win.loadURL('http://localhost:3001/?desktopShell=1')
  await sleep(4500)

  async function snap(name) {
    const info = await win.webContents.executeJavaScript(`(() => {
      const nav = document.querySelector('.desktop-tabs')
      const tabs = [...document.querySelectorAll('.desktop-tab')]
      return {
        count: tabs.length,
        widths: tabs.map((t) => t.offsetWidth),
        titles: tabs.map((t) => t.querySelector('.desktop-tab-title')?.textContent || ''),
        titleWidths: tabs.map((t) => t.querySelector('.desktop-tab-title')?.offsetWidth || 0),
        reloadDisplays: tabs.map((t) => getComputedStyle(t.querySelector('.desktop-tab-reload')).display),
        closeVisible: tabs.map((t) => getComputedStyle(t.querySelector('.desktop-tab-close')).display),
        monogramCount: tabs.filter((t) => t.querySelector('.desktop-tab-monogram')).length,
        navClientWidth: nav?.clientWidth || 0,
        navScrollWidth: nav?.scrollWidth || 0,
        error: document.querySelector('.desktop-error')?.textContent?.trim() || ''
      }
    })()`)
    console.log('=== ' + name + ' ===')
    console.log(JSON.stringify(info, null, 2))
    const img = await win.webContents.capturePage()
    fs.writeFileSync(path.join(OUT, name + '.png'), img.toPNG())
  }

  await snap('s0-home')

  // 3 个会话 -> 宽模式
  for (let i = 0; i < 3; i++) {
    await win.webContents.executeJavaScript(`document.querySelector('.desktop-tab-add').click()`, true)
    await sleep(1300)
  }
  await snap('s1-three')

  // 共 7 个会话 -> 中模式
  for (let i = 0; i < 4; i++) {
    await win.webContents.executeJavaScript(`document.querySelector('.desktop-tab-add').click()`, true)
    await sleep(1300)
  }
  await snap('s2-seven')

  // 共 12 个会话 -> 窄模式
  for (let i = 0; i < 5; i++) {
    await win.webContents.executeJavaScript(`document.querySelector('.desktop-tab-add').click()`, true)
    await sleep(1300)
  }
  await snap('s3-twelve')

  app.exit(0)
})
