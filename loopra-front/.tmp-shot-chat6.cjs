// 临时脚本：确认底部缝像素是阴影（白色+灰）而非消息透出
const {app, BrowserWindow} = require('electron')

const URL = process.argv[process.argv.length - 1]

app.commandLine.appendSwitch('disable-gpu')
app.commandLine.appendSwitch('no-sandbox')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1440, height: 900, show: false,
    webPreferences: {offscreen: true, javascript: true, images: true, webSecurity: true, backgroundThrottling: false}
  })
  await win.loadURL(URL)
  await sleep(6000)
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = Math.round((m.scrollHeight - m.clientHeight) * 0.5); m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)

  const img = await win.webContents.capturePage()
  const bmp = img.toBitmap()
  const W = img.getSize().width

  // 采样行：顶部缝 y=767、卡片 y=800、底部缝 y=890、y=896、消息区空白 y=500
  const rows = [500, 767, 800, 890, 894, 898]
  for (const y of rows) {
    const samples = []
    for (const x of [400, 720, 1000, 1440]) {
      const i = (y * W + x) * 4
      samples.push([bmp[i], bmp[i + 1], bmp[i + 2]])
    }
    console.log(`y=${y}: ${JSON.stringify(samples)}`)
  }
  app.quit()
  setTimeout(() => process.exit(0), 500)
})
