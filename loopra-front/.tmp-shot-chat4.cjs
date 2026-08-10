// 临时脚本：验证输入框区域改为背景色后的效果（仅 light，带逐步日志）
const {app, BrowserWindow} = require('electron')
const fs = require('fs')
const path = require('path')

const URL_BASE = process.argv[process.argv.length - 1]
const log = (s) => fs.appendFileSync(path.join(__dirname, '.tmp-shot-log.txt'), new Date().toISOString() + ' ' + s + '\n')

app.commandLine.appendSwitch('disable-gpu')
app.commandLine.appendSwitch('no-sandbox')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  log('whenReady')
  const win = new BrowserWindow({
    width: 1440, height: 900, show: false,
    webPreferences: {offscreen: true, javascript: true, images: true, webSecurity: true, backgroundThrottling: false}
  })
  log('window created')
  await win.loadURL(URL_BASE)
  log('loaded')
  await sleep(6000)
  log('waited 6s')

  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = Math.round((m.scrollHeight - m.clientHeight) * 0.5); m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)
  log('scrolled mid')

  const info = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const rect = (el) => el ? {top: Math.round(el.getBoundingClientRect().top), bottom: Math.round(el.getBoundingClientRect().bottom)} : null
    const messages = q('.messages')
    const inputArea = q('.input-area')
    const inputBox = q('.input-box')
    const items = [...document.querySelectorAll('.virtual-message-item')]
    const last = items[items.length - 1]
    const covered = items.filter(el => {
      const r = el.getBoundingClientRect()
      const ia = inputArea.getBoundingClientRect()
      return r.bottom > ia.top && r.top < ia.bottom
    }).length
    return {
      inputAreaBg: getComputedStyle(inputArea).background,
      inputArea: rect(inputArea),
      inputBox: rect(inputBox),
      paddingBottom: getComputedStyle(messages).paddingBottom,
      coveredByInput: covered,
      lastMsgRect: rect(last)
    }
  })()`)
  log('DOM info: ' + JSON.stringify(info))

  const img = await win.webContents.capturePage()
  log('captured')
  const bmp = img.toBitmap()
  const W = img.getSize().width
  log('bitmap W=' + W)
  const bgIdx = 0
  const bg = [bmp[bgIdx * 4], bmp[bgIdx * 4 + 1], bmp[bgIdx * 4 + 2]]
  const diffRegions = [
    {name: 'top-padding', y0: 760, y1: 774},
    {name: 'card', y0: 774, y1: 886},
    {name: 'bottom-padding', y0: 886, y1: 902}
  ]
  const results = []
  for (const r of diffRegions) {
    let diff = 0, total = 0
    for (let y = r.y0; y < r.y1; y++) {
      for (let x = 0; x < W; x += 16) {
        const i = (y * W + x) * 4
        const d = Math.abs(bmp[i] - bg[0]) + Math.abs(bmp[i + 1] - bg[1]) + Math.abs(bmp[i + 2] - bg[2])
        if (d > 24) diff++
        total++
      }
    }
    results.push({region: r.name, ratio: (diff / total * 100).toFixed(2) + '%'})
  }
  log('PIXEL: bg=' + JSON.stringify(bg) + ' regions=' + JSON.stringify(results))
  fs.writeFileSync(path.join(__dirname, '.tmp-shot-light.png'), img.toPNG())
  log('saved png')

  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = m.scrollHeight; m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)
  const bottomInfo = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const ia = q('.input-area').getBoundingClientRect()
    const items = [...document.querySelectorAll('.virtual-message-item')]
    const last = items[items.length - 1]
    const r = last ? last.getBoundingClientRect() : null
    return {lastMsgBottom: r ? Math.round(r.bottom) : null, inputAreaTop: Math.round(ia.top), fullyVisible: r ? r.bottom <= ia.top + 1 : false}
  })()`)
  log('BOTTOM: ' + JSON.stringify(bottomInfo))
  app.quit()
  log('quit called')
}).catch(e => {
  log('ERROR: ' + (e && e.stack || e))
  app.quit()
})
