// 临时脚本：用 Electron 离屏渲染加载桌面聊天页并截图，用于排查输入框悬浮遮挡问题
const {app, BrowserWindow} = require('electron')
const fs = require('fs')
const path = require('path')

const URL = process.argv[process.argv.length - 1]

app.commandLine.appendSwitch('disable-gpu')
app.commandLine.appendSwitch('no-sandbox')

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    show: false,
    webPreferences: {
      offscreen: true,
      javascript: true,
      images: true,
      webSecurity: true,
      backgroundThrottling: false
    }
  })

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

  await win.loadURL(URL)
  await sleep(6000) // 等待历史加载与渲染

  // 页面自检信息：滚动容器、输入区、消息区尺寸
  const info = await win.webContents.executeJavaScript(`(() => {
    const q = (s) => document.querySelector(s)
    const rect = (el) => el ? {top: Math.round(el.getBoundingClientRect().top), bottom: Math.round(el.getBoundingClientRect().bottom), height: Math.round(el.getBoundingClientRect().height)} : null
    const messages = q('.messages')
    const inputArea = q('.input-area')
    const inputBox = q('.input-box')
    const chat = q('.chat')
    return {
      viewport: {w: innerWidth, h: innerHeight},
      chat: rect(chat),
      messages: {...rect(messages), scrollTop: messages && Math.round(messages.scrollTop), scrollHeight: messages && messages.scrollHeight, clientHeight: messages && messages.clientHeight, paddingBottom: messages ? getComputedStyle(messages).paddingBottom : null},
      inputArea: rect(inputArea),
      inputBox: rect(inputBox),
      inputAreaBg: inputArea ? getComputedStyle(inputArea).background : null,
      inputBoxBg: inputBox ? getComputedStyle(inputBox).background : null,
      msgCount: document.querySelectorAll('.virtual-message-item').length,
      lastMsg: rect(document.querySelector('.virtual-message-item:last-child')),
      welcome: !!q('.welcome-screen')
    }
  })()`)
  console.log('INFO:', JSON.stringify(info, null, 2))

  const shot = async (name) => {
    const img = await win.webContents.capturePage()
    fs.writeFileSync(path.join(__dirname, name), img.toPNG())
    console.log('SAVED:', name)
  }

  await shot('.tmp-shot-chat-1-top.png')

  // 滚动到底部
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) m.scrollTop = m.scrollHeight
  })()`)
  await sleep(800)
  await shot('.tmp-shot-chat-2-bottom.png')

  // 滚动到中部（让消息位于输入框后方）
  await win.webContents.executeJavaScript(`(() => {
    const m = document.querySelector('.messages')
    if (m) m.scrollTop = Math.round(m.scrollHeight * 0.55)
  })()`)
  await sleep(800)
  await shot('.tmp-shot-chat-3-mid.png')

  app.quit()
})
