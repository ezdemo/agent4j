// 临时诊断脚本：验证 @container 规则生效状态（computed maxWidth / padding / gap）
const { app, BrowserWindow } = require('electron')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    show: false,
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

  async function probe(name) {
    const info = await win.webContents.executeJavaScript(`(() => {
      const t = document.querySelector('.desktop-tab')
      if (!t) return { count: 0 }
      const s = getComputedStyle(t)
      const title = t.querySelector('.desktop-tab-title')
      return {
        count: document.querySelectorAll('.desktop-tab').length,
        width: t.offsetWidth,
        padding: s.padding,
        gap: s.gap,
        titleComputedMaxWidth: getComputedStyle(title).maxWidth,
        titleWidth: title.offsetWidth
      }
    })()`)
    console.log(name + ':', JSON.stringify(info))
  }

  await probe('s0')
  await clickAdd(2)
  await probe('after-2 (expect wide)')
  await clickAdd(5)
  await probe('after-7 (expect narrow)')
  await clickAdd(5)
  await probe('after-12 (expect narrow+scroll)')

  app.exit(0)
})
