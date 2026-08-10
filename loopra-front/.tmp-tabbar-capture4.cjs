// 临时验证脚本（最终修正）：真实鼠标 hover 到 tab 上，验证 reload 显隐
const { app, BrowserWindow } = require('electron')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1920,
    height: 720,
    show: true,
    autoHideMenuBar: true,
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  })
  await win.loadURL('http://localhost:3001/?desktopShell=1')
  await sleep(4000)

  async function clickAdd(times) {
    for (let i = 0; i < times; i++) {
      await win.webContents.executeJavaScript(`document.querySelector('.desktop-tab-add').click()`, true)
      await sleep(1200)
    }
  }

  // 鼠标移到第一个 tab 中心（reload 是 tab 子元素，:hover 会传播到 reload 的父级规则）
  async function hoverTab() {
    const rect = await win.webContents.executeJavaScript(`(() => {
      const r = document.querySelector('.desktop-tab').getBoundingClientRect()
      return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) }
    })()`)
    win.webContents.sendInputEvent({ type: 'mouseMove', x: rect.x, y: rect.y })
    await sleep(350)
    const d = await win.webContents.executeJavaScript(`getComputedStyle(document.querySelector('.desktop-tab .desktop-tab-reload')).display`)
    win.webContents.sendInputEvent({ type: 'mouseMove', x: 5, y: 5 })
    await sleep(200)
    return d
  }

  async function probe(name) {
    const info = await win.webContents.executeJavaScript(`(() => {
      const t = document.querySelector('.desktop-tab')
      const title = t.querySelector('.desktop-tab-title')
      return {
        count: document.querySelectorAll('.desktop-tab').length,
        width: t.offsetWidth,
        titleMaxWidth: getComputedStyle(title).maxWidth,
        titleWidth: title.offsetWidth
      }
    })()`)
    const hoverDisplay = await hoverTab()
    console.log(name + ':', JSON.stringify(info), '| hover reload display:', hoverDisplay)
  }

  await clickAdd(3)
  await probe('WIDE(3 tabs, expect hover reload=inline-flex)')

  await clickAdd(7)
  await probe('MID(10 tabs, expect hover reload=none, titleMaxWidth=none)')

  await clickAdd(4)
  await probe('NARROW(14 tabs, expect hover reload=none, titleMaxWidth=28px)')

  app.exit(0)
})
