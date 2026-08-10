// 临时脚本：验证滚动到底部后最后一条消息是否被输入框遮挡
const {app, BrowserWindow} = require('electron')
const fs = require('fs')
const path = require('path')

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

  const measure = async (label) => {
    const info = await win.webContents.executeJavaScript(`(() => {
      const q = (s) => document.querySelector(s)
      const rect = (el) => el ? {top: Math.round(el.getBoundingClientRect().top), bottom: Math.round(el.getBoundingClientRect().bottom)} : null
      const messages = q('.messages')
      const inputArea = q('.input-area')
      const inputBox = q('.input-box')
      const items = [...document.querySelectorAll('.virtual-message-item')]
      const last = items[items.length - 1]
      // 找最后一条真实消息（排除占位符）
      const lastMsg = last ? rect(last) : null
      return {
        scrollTop: Math.round(messages.scrollTop),
        maxScroll: messages.scrollHeight - messages.clientHeight,
        scrollHeight: messages.scrollHeight,
        clientHeight: messages.clientHeight,
        paddingBottom: getComputedStyle(messages).paddingBottom,
        inputArea: rect(inputArea),
        inputBox: rect(inputBox),
        itemCount: items.length,
        lastMsg: lastMsg,
        // 输入框后面的可见消息（部分在 inputArea 内）
        coveredByInput: items.filter(el => {
          const r = el.getBoundingClientRect()
          const ia = inputArea.getBoundingClientRect()
          return r.bottom > ia.top && r.top < ia.bottom
        }).map(el => {
          const r = el.getBoundingClientRect()
          const ia = inputArea.getBoundingClientRect()
          const visible = Math.max(0, Math.min(r.bottom, ia.bottom) - Math.max(r.top, ia.top))
          return {top: Math.round(r.top), bottom: Math.round(r.bottom), visibleInInputArea: Math.round(visible)}
        })
      }
    })()`)
    console.log(`--- ${label} ---`)
    console.log(JSON.stringify(info, null, 2))
    return info
  }

  // 1. 滚动到底部
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = m.scrollHeight; m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)
  await measure('bottom')
  const img1 = await win.webContents.capturePage()
  fs.writeFileSync(path.join(__dirname, '.tmp-shot-bottom.png'), img1.toPNG())

  // 2. 滚动到中部
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) { m.scrollTop = Math.round((m.scrollHeight - m.clientHeight) * 0.5); m.dispatchEvent(new Event('scroll')) }
  })()`)
  await sleep(1200)
  await measure('mid')
  const img2 = await win.webContents.capturePage()
  fs.writeFileSync(path.join(__dirname, '.tmp-shot-mid.png'), img2.toPNG())

  app.quit()
})
