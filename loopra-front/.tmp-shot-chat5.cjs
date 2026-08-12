// 临时脚本：验证输入框上方透明（消息可透出）、下方遮住（背景色）的效果
const {app, BrowserWindow} = require('electron')
const fs = require('fs')
const path = require('path')

const URL = process.argv[process.argv.length - 1]
const log = (s) => fs.appendFileSync(path.join(__dirname, '.tmp-shot-log.txt'), new Date().toISOString() + ' ' + s + '\n')

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

  // 滚动到中部
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = Math.round((m.scrollHeight - m.clientHeight) * 0.5); m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)

  // 找一条同时覆盖"顶部缝"和"底部缝"的消息（长消息），如果没有则微调滚动
  const adjust = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const m = q('.messages')
    const ia = q('.input-area').getBoundingClientRect()
    const tryFind = () => [...document.querySelectorAll('.virtual-message-item')].find(el => {
      const r = el.getBoundingClientRect()
      return r.top < ia.top + 14 && r.bottom > ia.bottom - 16 && r.bottom - r.top > 200
    })
    let found = tryFind()
    let tries = 0
    while (!found && tries < 10) {
      m.scrollTop -= 300
      m.dispatchEvent(new Event('scroll'))
      tries++
      found = tryFind()
    }
    return found ? 'found' : 'not-found:' + tries
  })()`)
  log('adjust: ' + adjust)
  await sleep(1000)

  const info = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const rect = (el) => el ? {top: Math.round(el.getBoundingClientRect().top), bottom: Math.round(el.getBoundingClientRect().bottom)} : null
    const ia = q('.input-area')
    const items = [...document.querySelectorAll('.virtual-message-item')]
    const covering = items.filter(el => {
      const r = el.getBoundingClientRect()
      const a = ia.getBoundingClientRect()
      return r.bottom > a.top && r.top < a.bottom
    }).map(el => {
      const r = el.getBoundingClientRect()
      return {top: Math.round(r.top), bottom: Math.round(r.bottom), h: Math.round(r.bottom - r.top)}
    })
    return {
      inputAreaBg: getComputedStyle(ia).background,
      inputArea: rect(ia),
      covering
    }
  })()`)
  log('DOM: ' + JSON.stringify(info))

  const img = await win.webContents.capturePage()
  const bmp = img.toBitmap()
  const W = img.getSize().width
  log('bitmap W=' + W)

  // 基准色：亮色主题 --bg = #ffffff；从 .chat 计算
  const baseColor = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const cs = getComputedStyle(q('.chat'))
    return cs.backgroundColor
  })()`)
  log('chat bg: ' + baseColor)
  const m = baseColor.match(/(\d+),\s*(\d+),\s*(\d+)/)
  const bg = [parseInt(m[1]), parseInt(m[2]), parseInt(m[3])]

  const analyze = (y0, y1, threshold) => {
    let diff = 0, total = 0
    for (let y = y0; y < y1; y++) {
      for (let x = 0; x < W; x += 8) {
        const i = (y * W + x) * 4
        const d = Math.abs(bmp[i] - bg[0]) + Math.abs(bmp[i + 1] - bg[1]) + Math.abs(bmp[i + 2] - bg[2])
        if (d > threshold) diff++
        total++
      }
    }
    return (diff / total * 100).toFixed(2) + '%'
  }

  const a = info.inputArea
  log(`PIXEL (threshold=18): top-gap[${a.top}~${a.top + 14}] diff=${analyze(a.top, a.top + 14, 18)} | card[${a.top + 14}~${a.bottom - 16}] diff=${analyze(a.top + 14, a.bottom - 16, 18)} | bottom-gap[${a.bottom - 16}~${a.bottom}] diff=${analyze(a.bottom - 16, a.bottom, 18)}`)

  fs.writeFileSync(path.join(__dirname, '.tmp-shot-verify.png'), img.toPNG())
  log('saved')

  // 滚动到底部，确认最后消息完整可见
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
  setTimeout(() => process.exit(0), 500)
}).catch(e => {
  log('ERROR: ' + (e && e.stack || e))
  app.quit()
  setTimeout(() => process.exit(0), 500)
})
