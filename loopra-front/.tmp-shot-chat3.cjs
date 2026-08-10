// 临时脚本：验证输入框区域改为背景色后的效果（亮色 + 深色）
const {app, BrowserWindow} = require('electron')
const fs = require('fs')
const path = require('path')

const URL_BASE = process.argv[process.argv.length - 2]
const THEMES = ['light', 'dark']

app.commandLine.appendSwitch('disable-gpu')
app.commandLine.appendSwitch('no-sandbox')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

app.whenReady().then(async () => {
  for (const theme of THEMES) {
    const win = new BrowserWindow({
      width: 1440, height: 900, show: false,
      webPreferences: {offscreen: true, javascript: true, images: true, webSecurity: true, backgroundThrottling: false}
    })
    await win.loadURL(URL_BASE + '&theme=' + theme)
    await sleep(6000)

    // 滚动到中部（消息经过输入框后方）
    await win.webContents.executeJavaScript(`(() => {
      const m = document.querySelector('.messages')
      if (m) { m.scrollTop = Math.round((m.scrollHeight - m.clientHeight) * 0.5); m.dispatchEvent(new Event('scroll')) }
    })()`)
    await sleep(1200)

    const info = await win.webContents.executeJavaScript(`(() => {
      const q = (s) => document.querySelector(s)
      const rect = (el) => el ? {top: Math.round(el.getBoundingClientRect().top), bottom: Math.round(el.getBoundingClientRect().bottom), height: Math.round(el.getBoundingClientRect().height)} : null
      const messages = q('.messages')
      const inputArea = q('.input-area')
      const inputBox = q('.input-box')
      const items = [...document.querySelectorAll('.virtual-message-item')]
      const last = items[items.length - 1]
      // input-area 区域内是否有消息元素透出（被覆盖的可见部分）
      const covered = items.filter(el => {
        const r = el.getBoundingClientRect()
        const ia = inputArea.getBoundingClientRect()
        return r.bottom > ia.top && r.top < ia.bottom
      }).length
      return {
        theme: document.documentElement.getAttribute('data-theme') || document.body.getAttribute('data-theme'),
        inputAreaBg: getComputedStyle(inputArea).background,
        inputArea: rect(inputArea),
        inputBox: rect(inputBox),
        paddingBottom: getComputedStyle(messages).paddingBottom,
        scrollTop: Math.round(messages.scrollTop),
        maxScroll: messages.scrollHeight - messages.clientHeight,
        coveredByInput: covered,
        lastMsgRect: last ? rect(last) : null,
        // 像素验证：input-area 顶部/底部缝与卡片中心区域的平均色
        colors: await (async () => {
          const canvas = document.createElement('canvas')
          const ctx = canvas.getContext('2d')
          const img = new Image()
          // 直接读不了，改用背景色计算（DOM 方式足够）
          return null
        })()
      }
    })()`)

    // 像素级验证：截取 input-area 区域，统计非背景色像素比例
    const img = await win.webContents.capturePage()
    const bmp = img.toBitmap()
    const W = img.getSize().width
    const H = img.getSize().height
    // 读取背景色（页面顶部角落）
    const bgIdx = 0 // top-left pixel
    const bg = [bmp[bgIdx * 4], bmp[bgIdx * 4 + 1], bmp[bgIdx * 4 + 2]]

    // 统计 input-area 覆盖区域（760~902）中与背景色差异>24 的像素占比
    const diffCount = {top: 0, card: 0, bottom: 0, total: 0}
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
      results.push({region: r.name, diffPixels: diff, totalPixels: total, ratio: (diff / total * 100).toFixed(2) + '%'})
    }

    console.log(`=== theme: ${theme} ===`)
    console.log('DOM:', JSON.stringify(info, null, 2))
    console.log('PIXEL:', JSON.stringify({background: bg, regions: results}, null, 2))

    // 滚动到底部验证最后消息可见
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
      return {
        lastMsgBottom: r ? Math.round(r.bottom) : null,
        inputAreaTop: Math.round(ia.top),
        fullyVisible: r ? r.bottom <= ia.top + 1 : false,
        scrollTop: Math.round(q('.messages').scrollTop),
        maxScroll: q('.messages').scrollHeight - q('.messages').clientHeight
      }
    })()`)
    console.log('BOTTOM:', JSON.stringify(bottomInfo))

    fs.writeFileSync(path.join(__dirname, `.tmp-shot-${theme}.png`), img.toPNG())
    win.destroy()
  }
  app.quit()
})
