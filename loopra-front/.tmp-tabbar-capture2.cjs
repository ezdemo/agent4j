// 临时验证脚本（补充2）：只做 getComputedStyle 与 hover 模拟，不遍历样式表
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

  // 到 8 个 tab：窄模式
  await clickAdd(8)
  const narrow = await win.webContents.executeJavaScript(`(() => {
    const t = document.querySelector('.desktop-tab')
    const s = getComputedStyle(t)
    const title = t.querySelector('.desktop-tab-title')
    return {
      count: document.querySelectorAll('.desktop-tab').length,
      width: t.offsetWidth,
      padding: s.padding,
      gap: s.gap,
      titleWidth: title.offsetWidth,
      titleOverflow: getComputedStyle(title).overflow,
      titleText: title.textContent
    }
  })()`)
  console.log('NARROW:', JSON.stringify(narrow))

  // hover 模拟：mouseenter 后再读 reload 的 display（hover 规则 display: inline-flex 会被窄模式 !important 覆盖）
  const hover = await win.webContents.executeJavaScript(`(() => {
    const t = document.querySelector('.desktop-tab')
    t.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }))
    const d = getComputedStyle(t.querySelector('.desktop-tab-reload')).display
    t.dispatchEvent(new MouseEvent('mouseleave', { bubbles: true }))
    return d
  })()`)
  console.log('NARROW hover reload display:', hover)

  // 窄模式点击关闭一个 tab，验证按钮可点击且数量减少
  await win.webContents.executeJavaScript(`document.querySelector('.desktop-tab-close').click()`, true)
  await sleep(800)
  const afterClose = await win.webContents.executeJavaScript(`document.querySelectorAll('.desktop-tab').length`)
  console.log('count after close:', afterClose)

  app.exit(0)
})
