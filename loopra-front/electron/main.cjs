const { app, BrowserWindow, WebContentsView, dialog, ipcMain, Menu, shell } = require('electron')
const http = require('http')
const path = require('path')
const { spawn, execFile, execSync } = require('child_process')
const { promisify } = require('util')
const fs = require('fs')
const net = require('net')

// ==================== Squirrel 安装事件处理 ====================
// Squirrel 安装/卸载时会以特定参数启动应用，需要处理并立即退出
const handleSquirrelEvent = () => {
  if (process.argv.length === 1) return false
  const appFolder = path.resolve(process.execPath, '..')
  const rootAtomFolder = path.resolve(appFolder, '..')
  const updateDotExe = path.resolve(rootAtomFolder, 'Update.exe')
  const exeName = path.basename(process.execPath)

  const spawnProcess = (command, args) => {
    try {
      return spawn(command, args, { detached: true })
    } catch { return null }
  }

  const squirrelEvent = process.argv[1]
  switch (squirrelEvent) {
    case '--squirrel-install':
    case '--squirrel-updated':
      // 创建开始菜单和桌面快捷方式
      spawnProcess(updateDotExe, ['--createShortcut', exeName])
      setTimeout(app.quit, 1000)
      return true
    case '--squirrel-uninstall':
      // 移除快捷方式
      spawnProcess(updateDotExe, ['--removeShortcut', exeName])
      setTimeout(app.quit, 1000)
      return true
    case '--squirrel-obsolete':
      app.quit()
      return true
  }
  return false
}

if (handleSquirrelEvent()) {
  // Squirrel 事件已处理，应用将在短暂延迟后退出
  // 这里不需要执行任何其他逻辑
}

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged
const isWin = process.platform === 'win32'
const shouldOpenDevTools = process.env.LOOPRA_OPEN_DEVTOOLS === '1'

let mainWindow = null
let loopraWebProcess = null
let currentPort = 0
const loopraWebWindows = new Map()
let elementWebView = null
let elementInspectorWindow = null
let elementInspectorReady = false
let elementInspectorPendingUrl = ''
let aiBrowserWindow = null
let aiBrowserActiveTabId = null
let aiBrowserNextTabId = 1
let aiBrowserBridge = null
let aiBrowserBridgeReady = null
let aiBrowserBridgeAddress = ''
let aiBrowserActivity = { state: 'idle', message: '等待 AI 操作', timestamp: Date.now() }
let desktopPetWindow = null
const aiBrowserTabs = new Map()
const desktopChatTabs = new Map()
let desktopChatActiveTabId = null
const AI_BROWSER_BRIDGE_PREFERRED_PORT = Number(process.env.LOOPRA_BROWSER_BRIDGE_PORT || 0)
const AI_BROWSER_TAB_CLEANUP_THRESHOLD = 16
const AI_BROWSER_MAX_TABS = 20
const execFileAsync = promisify(execFile)
const appIconPath = path.join(__dirname, 'favicon.png')

if (isWin) app.setAppUserModelId('com.loopra.desktop')

function parsePort(commandLine) {
  const match = String(commandLine || '').match(/--server\.port=(\d+)/i)
  return match ? Number(match[1]) : 0
}

function parseElapsedSeconds(value) {
  const match = String(value || '').trim().match(/(?:(\d+)-)?(?:(\d+):)?(\d+):(\d+)/)
  if (!match) return 0
  return Number(match[1] || 0) * 86400 + Number(match[2] || 0) * 3600 + Number(match[3]) * 60 + Number(match[4])
}

function openLoopraWebWindow(port) {
  const existing = loopraWebWindows.get(port)
  if (existing && !existing.isDestroyed()) {
    if (existing.isMinimized()) existing.restore()
    existing.focus()
    return
  }

  const serviceWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 900,
    minHeight: 640,
    title: `Loopra 服务 (${port})`,
    icon: appIconPath,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false
    }
  })
  loopraWebWindows.set(port, serviceWindow)
  serviceWindow.on('closed', () => loopraWebWindows.delete(port))
  serviceWindow.loadURL(`http://127.0.0.1:${port}/index.html`)
}

async function listLoopraJavaProcesses() {
  if (isWin) {
    const script = [
      '$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()',
      "$items = Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -match '(?i)loopra-web\\.jar' } | ForEach-Object {",
      '  $native = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue',
      '  [PSCustomObject]@{',
      '    pid = [int]$_.ProcessId',
      '    parentPid = [int]$_.ParentProcessId',
      '    name = [string]$_.Name',
      '    commandLine = [string]$_.CommandLine',
      '    executablePath = [string]$_.ExecutablePath',
      "    startedAt = if ($native) { $native.StartTime.ToUniversalTime().ToString('o') } else { $null }",
      '    memoryBytes = if ($native) { [long]$native.WorkingSet64 } else { [long]0 }',
      '  }',
      '}',
      'if ($items) { @($items) | ConvertTo-Json -Compress } else { Write-Output "[]" }'
    ].join('\n')
    const { stdout } = await execFileAsync('powershell', [
      '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', script
    ], { windowsHide: true, maxBuffer: 1024 * 1024 })
    const items = JSON.parse(stdout.trim() || '[]')
    const managedPid = loopraWebProcess?.pid || 0
    return (Array.isArray(items) ? items : [items]).map((item) => ({
      ...item,
      port: parsePort(item.commandLine),
      managed: item.parentPid === managedPid || parsePort(item.commandLine) === currentPort
    }))
  }

  const { stdout } = await execFileAsync('ps', ['-axo', 'pid=,ppid=,rss=,etime=,comm=,args='], {
    maxBuffer: 1024 * 1024
  })
  const managedPid = loopraWebProcess?.pid || 0
  return stdout.split('\n').flatMap((line) => {
    const match = line.trim().match(/^(\d+)\s+(\d+)\s+(\d+)\s+(\S+)\s+(\S+)\s+(.+)$/)
    if (!match) return []
    const [, pid, parentPid, rssKb, elapsed, name, commandLine] = match
    if (!/java/i.test(name) || !/loopra-web\.jar/i.test(commandLine)) return []
    return [{
      pid: Number(pid),
      parentPid: Number(parentPid),
      name,
      commandLine,
      executablePath: name,
      memoryBytes: Number(rssKb) * 1024,
      uptimeSeconds: parseElapsedSeconds(elapsed),
      port: parsePort(commandLine),
      managed: Number(parentPid) === managedPid || Number(pid) === managedPid || parsePort(commandLine) === currentPort
    }]
  })
}

// 获取随机可用端口
async function getRandomPort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.listen(0, () => {
      const port = server.address().port
      server.close(() => resolve(port))
    })
    server.on('error', reject)
  })
}

async function getDefaultPort() {
  // 获取随机可用端口，避免占用固定端口
  const port = await getRandomPort()
  return port
}

async function healthCheck(port) {
  try {
    const resp = await fetch(`http://127.0.0.1:${port}/api/system/health`, {
      signal: AbortSignal.timeout(3000)
    })
    return resp.ok
  } catch { return false }
}

// 杀掉整个进程树（包括 java 子进程）
function killProcessTree(child) {
  if (!child) return
  const pid = child.pid
  if (!pid) return

  if (isWin) {
    // Windows: taskkill /T /F 同步杀掉整个进程树
    try {
      execSync(`taskkill /pid ${pid} /t /f`, { stdio: 'ignore' })
    } catch { /* 进程可能已退出 */ }
  } else {
    // macOS/Linux: 杀掉整个进程组，同步等待
    try { process.kill(-pid, 'SIGTERM') } catch { return }
    // 同步等待进程退出，最多 5 秒
    const deadline = Date.now() + 5000
    while (Date.now() < deadline) {
      try {
        process.kill(-pid, 0) // 检查进程是否还在
        execSync('sleep 0.2')
      } catch {
        return // 已退出
      }
    }
    // 还没死，强杀
    try { process.kill(-pid, 'SIGKILL') } catch { /* already dead */ }
  }
}

function getLoopraBinPath() {
  const home = app.getPath('home')
  const binDir = path.join(home, '.loopra', 'bin')
  const binName = isWin ? 'loopra.ps1' : 'loopra'
  return { binDir, binPath: path.join(binDir, binName) }
}

// 启动 loopra web <port>
function startLoopraWeb(port) {
  const { binDir, binPath } = getLoopraBinPath()

  if (!fs.existsSync(binPath)) {
    throw new Error(`loopra not found: ${binPath}`)
  }

  console.log(`Starting: ${binPath} web ${port}`)

  let child
  if (isWin) {
    // Windows: 用 PowerShell 执行 .ps1 脚本
    child = spawn('powershell', [
      '-ExecutionPolicy', 'Bypass',
      '-NoProfile',
      '-File', binPath,
      'web', String(port)
    ], {
      cwd: binDir,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    })
  } else {
    // macOS/Linux: 直接执行
    child = spawn(binPath, ['web', String(port)], {
      cwd: binDir,
      detached: true,
      stdio: ['ignore', 'pipe', 'pipe']
    })
  }

  child.stdout.on('data', (d) => console.log(`[loopra-web] ${d}`))
  child.stderr.on('data', (d) => console.error(`[loopra-web] ${d}`))

  child.on('exit', (code) => {
    console.log(`loopra-web exited with code ${code}`)
    loopraWebProcess = null
    currentPort = 0
  })

  child.on('error', (err) => {
    console.error('loopra-web spawn error:', err)
    loopraWebProcess = null
    currentPort = 0
  })

  loopraWebProcess = child
  return child
}

// 统一清理
function cleanupLoopraWeb() {
  if (loopraWebProcess) {
    killProcessTree(loopraWebProcess)
    loopraWebProcess = null
    currentPort = 0
  }
}

// ==================== 窗口 ====================

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200, height: 800,
    minWidth: 800, minHeight: 600,
    frame: false,
    titleBarStyle: 'hidden',
    icon: appIconPath,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  // 开发模式：加载 Vite dev server；生产模式：加载打包后的 renderer
  if (isDev) {
    mainWindow.loadURL('http://localhost:3000/?desktopShell=1')
  } else {
    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'), { query: { desktopShell: '1' } })
  }

  if (shouldOpenDevTools) mainWindow.webContents.openDevTools()

  mainWindow.webContents.on('context-menu', (event, params) => {
    const menu = Menu.buildFromTemplate([
      { label: '检查元素', click: () => mainWindow.webContents.inspectElement(params.x, params.y) },
      { type: 'separator' },
      { role: 'reload', label: '刷新' },
      { role: 'forceReload', label: '强制刷新' },
      { role: 'toggleDevTools', label: '开发者工具' }
    ])
    menu.popup()
  })

  // macOS: 隐藏原生窗口控制按钮（红绿灯），应用内使用自定义标题栏按钮
  if (process.platform === 'darwin') {
    mainWindow.setWindowButtonVisibility(false)
  }

  mainWindow.on('closed', () => {
    if (elementInspectorWindow && !elementInspectorWindow.isDestroyed()) elementInspectorWindow.close()
    if (aiBrowserWindow && !aiBrowserWindow.isDestroyed()) aiBrowserWindow.close()
    if (desktopPetWindow && !desktopPetWindow.isDestroyed()) desktopPetWindow.close()
    if (elementWebView && !elementWebView.webContents.isDestroyed()) elementWebView.webContents.close()
    destroyDesktopChatTabs()
    elementWebView = null
    mainWindow = null
  })
}

function openDesktopPetWindow() {
  if (desktopPetWindow && !desktopPetWindow.isDestroyed()) {
    desktopPetWindow.showInactive()
    return desktopPetWindow
  }

  desktopPetWindow = new BrowserWindow({
    width: 240,
    height: 240,
    x: Math.max(0, (mainWindow?.getBounds().x || 0) + (mainWindow?.getBounds().width || 800) - 260),
    y: Math.max(0, (mainWindow?.getBounds().y || 0) + (mainWindow?.getBounds().height || 600) - 300),
    frame: false,
    transparent: true,
    resizable: false,
    movable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    focusable: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      backgroundThrottling: false
    }
  })
  desktopPetWindow.setAlwaysOnTop(true, 'floating')
  desktopPetWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  desktopPetWindow.setIgnoreMouseEvents(true, { forward: true })
  desktopPetWindow.on('closed', () => { desktopPetWindow = null })
  desktopPetWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  desktopPetWindow.once('ready-to-show', () => desktopPetWindow?.showInactive())

  if (isDev) {
    desktopPetWindow.loadURL('http://localhost:3000/?desktopPet=1')
  } else {
    desktopPetWindow.loadFile(path.join(__dirname, '../renderer/index.html'), { query: { desktopPet: '1' } })
  }
  return desktopPetWindow
}

app.whenReady().then(() => {
  // The renderer provides its own toolbar; do not expose Electron's default menu bar.
  Menu.setApplicationMenu(null)
  startAiBrowserBridge().catch((error) => console.error('[ai-browser] failed to start bridge:', error.message))
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  cleanupLoopraWeb()
  app.quit()
})

app.on('before-quit', () => {
  cleanupLoopraWeb()
  stopAiBrowserBridge()
})

// ==================== IPC ====================

ipcMain.handle('get_loopra_web_port', async () => currentPort)

ipcMain.handle('get_electron_version', async () => {
  return app.getVersion()
})

ipcMain.handle('get_loopra_web_status', async () => ({
  installed: fs.existsSync(getLoopraBinPath().binPath),
  running: loopraWebProcess !== null,
  install_dir: path.join(app.getPath('home'), '.loopra')
}))

ipcMain.handle('list_loopra_java_processes', async () => {
  try {
    return { processes: await listLoopraJavaProcesses() }
  } catch (error) {
    console.error('Failed to list Loopra Java processes:', error)
    return { processes: [], error: error.message }
  }
})

ipcMain.handle('terminate_loopra_java_process', async (event, rawPid) => {
  const pid = Number(rawPid)
  if (!Number.isSafeInteger(pid) || pid <= 0) throw new Error('Invalid process id')

  const processes = await listLoopraJavaProcesses()
  if (!processes.some((item) => item.pid === pid)) {
    throw new Error('Loopra backend process no longer exists')
  }

  if (isWin) {
    await execFileAsync('taskkill', ['/pid', String(pid), '/t', '/f'], { windowsHide: true })
  } else {
    process.kill(pid, 'SIGTERM')
  }

  if (loopraWebProcess && (loopraWebProcess.pid === pid || processes.find((item) => item.pid === pid)?.managed)) {
    loopraWebProcess = null
    currentPort = 0
  }
  return { success: true }
})

ipcMain.handle('open_loopra_java_process', async (event, rawPid) => {
  const pid = Number(rawPid)
  if (!Number.isSafeInteger(pid) || pid <= 0) throw new Error('Invalid process id')

  const processes = await listLoopraJavaProcesses()
  const processInfo = processes.find((item) => item.pid === pid)
  if (!processInfo) throw new Error('Loopra backend process no longer exists')
  if (!Number.isInteger(processInfo.port) || processInfo.port < 1 || processInfo.port > 65535) {
    throw new Error('No HTTP port was found for this Loopra backend process')
  }

  openLoopraWebWindow(processInfo.port)
  return { success: true, port: processInfo.port }
})

ipcMain.handle('get_resource_dir', async () => {
  if (app.isPackaged) return path.join(process.resourcesPath, 'resources')
  return path.join(__dirname, '../resources')
})

ipcMain.handle('check_install_needed', async () => {
  const installed = fs.existsSync(getLoopraBinPath().binPath)
  return { needed: !installed, reason: installed ? '' : 'not_installed' }
})
ipcMain.handle('install_loopra_web', async () => ({ success: true, steps: ['electron_mock_install'] }))

ipcMain.handle('start_loopra_web', async () => {
  if (loopraWebProcess) {
    await closeOtherLoopraJavaProcesses(currentPort)
    return currentPort
  }

  // 优先探测固定端口 4567：若已有健康服务则直接复用，不再启动新进程
  const preferredPort = 4567
  if (await healthCheck(preferredPort)) {
    console.log(`Loopra Web already running on port ${preferredPort}, reusing`)
    currentPort = preferredPort
    await closeOtherLoopraJavaProcesses(currentPort)
    return preferredPort
  }

  // 4567 无可用服务，回退到随机端口启动新进程
  const port = await getDefaultPort()

  // 先检查服务是否已在运行
  if (await healthCheck(port)) {
    console.log(`Loopra Web already running on port ${port}`)
    currentPort = port
    await closeOtherLoopraJavaProcesses(currentPort)
    return port
  }

  // 未运行，启动
  try {
    startLoopraWeb(port)
    currentPort = port
    await closeOtherLoopraJavaProcesses(currentPort)
    return port
  } catch (error) {
    throw new Error(`Failed to start loopra web: ${error.message}`)
  }
})

// 推送安装日志到前端
function sendInstallLog(line) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('install-output', { type: 'log', line })
  }
}

// 在线一键安装 loopra（走远程脚本）
ipcMain.handle('install_loopra_web_online', async () => {
  sendInstallLog('='.repeat(50))
  sendInstallLog('  Loopra 在线一键安装')
  sendInstallLog('='.repeat(50))
  sendInstallLog('')

  return new Promise((resolve, reject) => {
    let child
    if (isWin) {
      // Windows: irm ... | iex
      sendInstallLog('>> 检测到 Windows 系统，使用 PowerShell 安装...')
      const psCmd = [
        '-ExecutionPolicy', 'Bypass', '-NoProfile', '-Command',
        'irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex'
      ]
      child = spawn('powershell', psCmd, {
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true
      })
      sendInstallLog('>> 执行: irm setup.ps1 | iex')
    } else {
      // macOS/Linux: curl ... | bash
      sendInstallLog('>> 检测到 Unix 系统，使用 curl 安装...')
      // 下载脚本并管道给 bash 执行，-fsSL = 静默+显示错误+跟随跳转
      child = spawn('bash', ['-c',
        'curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh | bash'
      ], { stdio: ['ignore', 'pipe', 'pipe'] })
      sendInstallLog('>> 执行: curl setup.sh | bash')
    }

    sendInstallLog('>> 安装进程已启动，等待输出...')
    sendInstallLog('')

    let installOutputBuffer = ''

    function onInstallOutput(data) {
      installOutputBuffer += data.toString()
      const lines = installOutputBuffer.split('\n')
      // 保留最后一段（可能不完整），其余发送
      installOutputBuffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.replace(/\r$/, '')
        if (trimmed.length > 0) {
          sendInstallLog(trimmed)
        }
      }
    }

    child.stdout.on('data', onInstallOutput)
    child.stderr.on('data', onInstallOutput)
    child.on('exit', (code) => {
      // 刷出 buffer 中剩余的内容
      if (installOutputBuffer.length > 0) {
        const trimmed = installOutputBuffer.replace(/\r$/, '')
        if (trimmed.length > 0) {
          sendInstallLog(trimmed)
        }
      }
      installOutputBuffer = ''
      sendInstallLog('')
      sendInstallLog(`>> 安装进程已退出，退出码: ${code}`)
      if (code === 0) {
        sendInstallLog('>> ✅ Loopra 安装成功！')
        resolve({ success: true })
      } else {
        sendInstallLog('>> ❌ 安装失败，请检查网络连接后重试')
        reject(new Error(`安装失败，退出码: ${code}`))
      }
    })
    child.on('error', (err) => {
      sendInstallLog(`>> ❌ 启动安装进程失败: ${err.message}`)
      reject(new Error(`安装进程启动失败: ${err.message}`))
    })
  })
})

ipcMain.handle('stop_loopra_web', async () => {
  cleanupLoopraWeb()
})

ipcMain.handle('window-minimize', () => { if (mainWindow) mainWindow.minimize() })
ipcMain.handle('window-maximize', () => {
  if (mainWindow) mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize()
})
ipcMain.handle('window-close', () => { if (mainWindow) mainWindow.close() })
ipcMain.handle('window-is-maximized', () => mainWindow ? mainWindow.isMaximized() : false)
ipcMain.handle('desktop-pet-open', (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop pet request')
  openDesktopPetWindow()
  return true
})
ipcMain.handle('desktop-pet-close', (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop pet request')
  if (desktopPetWindow && !desktopPetWindow.isDestroyed()) desktopPetWindow.close()
  return false
})
ipcMain.handle('desktop-pet-is-visible', (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop pet request')
  return Boolean(desktopPetWindow && !desktopPetWindow.isDestroyed() && desktopPetWindow.isVisible())
})
ipcMain.handle('desktop-pet-refresh', (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop pet request')
  if (desktopPetWindow && !desktopPetWindow.isDestroyed()) desktopPetWindow.webContents.send('desktop-pet-refresh')
})
ipcMain.handle('desktop-pet-move-by', (event, delta) => {
  if (event.sender !== desktopPetWindow?.webContents) throw new Error('Unauthorized desktop pet move')
  const dx = Number(delta?.x)
  const dy = Number(delta?.y)
  if (!Number.isFinite(dx) || !Number.isFinite(dy) || Math.abs(dx) > 2000 || Math.abs(dy) > 2000) {
    throw new Error('Invalid desktop pet move')
  }
  const bounds = desktopPetWindow.getBounds()
  desktopPetWindow.setPosition(Math.round(bounds.x + dx), Math.round(bounds.y + dy))
})
ipcMain.on('desktop-pet-set-interactive', (event, interactive) => {
  if (event.sender !== desktopPetWindow?.webContents || !desktopPetWindow || desktopPetWindow.isDestroyed()) return
  desktopPetWindow.setIgnoreMouseEvents(!interactive, { forward: true })
})
ipcMain.handle('pick_loopra_workspace_folder', async (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized folder picker request')
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '选择项目文件夹',
    properties: ['openDirectory', 'createDirectory']
  })
  return result.canceled ? '' : (result.filePaths[0] || '')
})

// ==================== Desktop Chat Tabs ====================

ipcMain.handle('desktop-chat-tab-create', async (event, rawTab) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const tabId = String(rawTab?.id || '').trim()
  const sessionName = String(rawTab?.sessionName || '').trim()
  const workspaceHash = String(rawTab?.workspaceHash || '').trim()
  const theme = rawTab?.theme === 'dark' ? 'dark' : 'gray'
  if (!tabId || !sessionName || tabId.length > 240 || sessionName.length > 240 || workspaceHash.length > 240) {
    throw new Error('Invalid desktop chat tab')
  }
  try {
    await getOrCreateDesktopChatTab(tabId, sessionName, workspaceHash, theme)
    return { success: true, tabId }
  } catch (error) {
    const tab = desktopChatTabs.get(tabId)
    if (tab && !tab.view.webContents.isDestroyed()) tab.view.webContents.close()
    desktopChatTabs.delete(tabId)
    throw error
  }
})

ipcMain.handle('desktop-chat-tab-show', async (event, tabId, rawBounds) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const tab = desktopChatTabs.get(String(tabId || ''))
  if (!tab) throw new Error('Desktop chat tab no longer exists')
  if (!tab.attached) {
    mainWindow.contentView.addChildView(tab.view)
    tab.attached = true
  }
  tab.view.setBounds(normalizeDesktopChatBounds(rawBounds))
  tab.view.setVisible(true)
  tab.visible = true
  hideDesktopChatViews(tab.id)
  desktopChatActiveTabId = tab.id
  return { success: true }
})

ipcMain.handle('desktop-chat-tab-hide', async (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  hideDesktopChatViews()
  return { success: true }
})

ipcMain.handle('desktop-chat-tab-close', (event, tabId) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const tab = desktopChatTabs.get(String(tabId || ''))
  if (!tab) return { success: true }
  tab.view.setVisible(false)
  tab.visible = false
  if (!tab.view.webContents.isDestroyed()) tab.view.webContents.close()
  desktopChatTabs.delete(tab.id)
  if (desktopChatActiveTabId === tab.id) desktopChatActiveTabId = null
  return { success: true }
})

ipcMain.handle('desktop-chat-tab-reload', (event, tabId) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const tab = desktopChatTabs.get(String(tabId || ''))
  if (!tab || tab.view.webContents.isDestroyed()) throw new Error('Desktop chat tab no longer exists')
  tab.view.webContents.send('desktop-chat-tab-refresh-history')
  return { success: true }
})

ipcMain.handle('desktop-chat-tab-toggle-right-panel', (event, tabId) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const tab = desktopChatTabs.get(String(tabId || ''))
  if (!tab || tab.view.webContents.isDestroyed()) throw new Error('Desktop chat tab no longer exists')
  tab.view.webContents.send('desktop-chat-tab-toggle-right-panel')
  return { success: true }
})

ipcMain.handle('desktop-chat-tab-set-theme', (event, rawTheme) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized desktop chat tab request')
  const theme = rawTheme === 'dark' ? 'dark' : 'gray'
  for (const tab of desktopChatTabs.values()) {
    if (!tab.view.webContents.isDestroyed()) tab.view.webContents.send('desktop-chat-tab-theme', theme)
  }
  return { success: true }
})

ipcMain.on('desktop-chat-tab-report-title', (event, payload) => {
  const tab = [...desktopChatTabs.values()].find((item) => item.view.webContents === event.sender)
  const title = String(payload?.title || '').trim()
  if (!tab || !title || title.length > 120 || !mainWindow || mainWindow.isDestroyed()) return
  mainWindow.webContents.send('desktop-chat-tab-title', { tabId: tab.id, title })
})

ipcMain.on('desktop-chat-tab-report-workspace', (event, payload) => {
  const tab = [...desktopChatTabs.values()].find((item) => item.view.webContents === event.sender)
  const workspaceHash = String(payload?.workspaceHash || '')
  if (!tab || !workspaceHash || !mainWindow || mainWindow.isDestroyed()) return
  mainWindow.webContents.send('desktop-chat-tab-workspace', { tabId: tab.id, workspaceHash })
})

ipcMain.on('desktop-chat-tab-open-home', (event) => {
  const tab = [...desktopChatTabs.values()].find((item) => item.view.webContents === event.sender)
  if (!tab || !mainWindow || mainWindow.isDestroyed()) return
  mainWindow.webContents.send('desktop-shell-open-home')
})

ipcMain.on('desktop-chat-tab-open-model-channels', (event) => {
  const tab = [...desktopChatTabs.values()].find((item) => item.view.webContents === event.sender)
  if (!tab || !mainWindow || mainWindow.isDestroyed()) return
  mainWindow.webContents.send('desktop-shell-open-model-channels')
})

// ==================== AI Browser ====================

function normalizeAiBrowserUrl(rawUrl, allowBlank = false) {
  const value = String(rawUrl || '').trim()
  if (!value && allowBlank) return 'about:blank'
  const withScheme = /^[a-z][a-z0-9+.-]*:/i.test(value)
    ? value
    : (value.startsWith('localhost') || value.startsWith('127.0.0.1') ? `http://${value}` : `https://${value}`)
  const url = new URL(withScheme)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Only HTTP(S) URLs are supported')
  return url.href
}

function hideDesktopChatViews(exceptTabId = null) {
  const targets = [...desktopChatTabs.values()].filter((tab) => tab.id !== exceptTabId && tab.visible)
  for (const tab of targets) {
    tab.view.setVisible(false)
    tab.visible = false
  }
}

function destroyDesktopChatTabs() {
  for (const tab of desktopChatTabs.values()) {
    tab.view.setVisible(false)
    tab.visible = false
    if (!tab.view.webContents.isDestroyed()) tab.view.webContents.close()
  }
  desktopChatTabs.clear()
  desktopChatActiveTabId = null
}

function normalizeDesktopChatBounds(rawBounds) {
  if (!mainWindow || !rawBounds || typeof rawBounds !== 'object') throw new Error('Invalid desktop chat view bounds')
  const contentBounds = mainWindow.getContentBounds()
  const values = ['x', 'y', 'width', 'height'].map((key) => Math.round(Number(rawBounds[key])))
  if (!values.every(Number.isFinite)) throw new Error('Invalid desktop chat view bounds')
  const x = Math.max(0, Math.min(values[0], contentBounds.width - 1))
  const y = Math.max(0, Math.min(values[1], contentBounds.height - 1))
  return { x, y, width: Math.max(1, Math.min(values[2], contentBounds.width - x)), height: Math.max(1, Math.min(values[3], contentBounds.height - y)) }
}

async function getOrCreateDesktopChatTab(tabId, sessionName, workspaceHash, theme = 'gray') {
  const existing = desktopChatTabs.get(tabId)
  if (existing) {
    if (!existing.view.webContents.isDestroyed()) existing.view.webContents.send('desktop-chat-tab-theme', theme)
    return existing
  }
  if (!mainWindow || mainWindow.isDestroyed()) throw new Error('Main window is not available')
  const view = new WebContentsView({
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      backgroundThrottling: false
    }
  })
  view.setBackgroundColor(theme === 'dark' ? '#141518' : '#ffffff')
  const tab = { id: tabId, sessionName, workspaceHash, view, attached: false, visible: false }
  desktopChatTabs.set(tabId, tab)
  view.setVisible(false)
  view.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
  view.webContents.on('destroyed', () => {
    desktopChatTabs.delete(tabId)
    if (desktopChatActiveTabId === tabId) desktopChatActiveTabId = null
  })
  const query = new URLSearchParams({ desktopChatTab: '1', sessionName, workspaceHash: workspaceHash || '', theme }).toString()
  if (isDev) await view.webContents.loadURL(`http://localhost:3000/?${query}`)
  else await view.webContents.loadFile(path.join(__dirname, '../renderer/index.html'), { query: { desktopChatTab: '1', sessionName, workspaceHash: workspaceHash || '', theme } })
  return tab
}

async function stopLoopraJavaProcess(pid) {
  if (isWin) {
    await execFileAsync('taskkill', ['/pid', String(pid), '/t', '/f'], { windowsHide: true })
  } else {
    process.kill(pid, 'SIGTERM')
  }
}

async function closeOtherLoopraJavaProcesses(keepPort) {
  if (!Number.isSafeInteger(keepPort) || keepPort <= 0) return
  try {
    const processes = await listLoopraJavaProcesses()
    for (const processInfo of processes.filter((item) => item.port !== keepPort)) {
      try {
        await stopLoopraJavaProcess(processInfo.pid)
        console.log(`Closed stale loopra-web process ${processInfo.pid} on port ${processInfo.port || 'unknown'}`)
      } catch (error) {
        console.warn(`Failed to close stale loopra-web process ${processInfo.pid}: ${error.message}`)
      }
    }
  } catch (error) {
    console.warn('Failed to clean up stale loopra-web processes:', error.message)
  }
}

function aiBrowserTabSummary(tab) {
  const contents = tab.view.webContents
  return {
    id: tab.id,
    url: contents.isDestroyed() ? tab.url : (contents.getURL() || tab.url),
    title: tab.title || '新标签页',
    loading: tab.loading,
    canGoBack: !contents.isDestroyed() && contents.canGoBack(),
    canGoForward: !contents.isDestroyed() && contents.canGoForward()
  }
}

function sendAiBrowserState() {
  if (!aiBrowserWindow || aiBrowserWindow.isDestroyed()) return
  aiBrowserWindow.webContents.send('ai-browser-state', {
    activeTabId: aiBrowserActiveTabId,
    tabs: [...aiBrowserTabs.values()].map(aiBrowserTabSummary)
  })
}

function sendAiBrowserActivity(state, message, details = {}) {
  aiBrowserActivity = { state, message, timestamp: Date.now(), ...details }
  if (!aiBrowserWindow || aiBrowserWindow.isDestroyed()) return
  aiBrowserWindow.webContents.send('ai-browser-activity', aiBrowserActivity)
}

function hideAiBrowserViews() {
  for (const tab of aiBrowserTabs.values()) tab.view.setVisible(false)
}

function closeAiBrowserTab(tabId) {
  const tab = aiBrowserTabs.get(tabId)
  if (!tab) return false
  tab.view.setVisible(false)
  if (!tab.view.webContents.isDestroyed()) tab.view.webContents.close()
  aiBrowserTabs.delete(tabId)
  if (aiBrowserActiveTabId === tabId) aiBrowserActiveTabId = aiBrowserTabs.keys().next().value || null
  return true
}

function destroyAiBrowserTabs() {
  for (const id of [...aiBrowserTabs.keys()]) closeAiBrowserTab(id)
  aiBrowserActiveTabId = null
}

function getAiBrowserTab(tabId) {
  const tab = aiBrowserTabs.get(String(tabId || ''))
  if (!tab) throw new Error(`Unknown browser tab: ${tabId}`)
  return tab
}

async function createAiBrowserTab(rawUrl = 'about:blank') {
  if (aiBrowserTabs.size >= AI_BROWSER_MAX_TABS) {
    throw new Error(`BROWSER_TAB_LIMIT: 已达到 ${AI_BROWSER_MAX_TABS} 个标签页硬上限。请先调用 browser_tabs，关闭不再需要的非活动标签页，再创建新标签页。`)
  }
  const url = normalizeAiBrowserUrl(rawUrl, true)
  const id = `tab-${aiBrowserNextTabId++}`
  const view = new WebContentsView({
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  const tab = { id, url, title: '新标签页', loading: false, lastLoadError: null, snapshotVersion: 0, snapshotId: null, view, attached: false }
  aiBrowserTabs.set(id, tab)
  view.setVisible(false)
  view.webContents.setWindowOpenHandler(({ url: targetUrl }) => {
    createAiBrowserTab(targetUrl)
      .then((newTab) => activateAiBrowserTab(newTab.id))
      .catch((error) => sendAiBrowserActivity('failed', `无法打开新标签页：${error.message}`))
    return { action: 'deny' }
  })
  view.webContents.on('page-title-updated', (event, title) => {
    event.preventDefault()
    tab.title = String(title || '').trim().slice(0, 160) || '新标签页'
    sendAiBrowserState()
  })
  view.webContents.on('did-start-loading', () => {
    tab.loading = true
    sendAiBrowserState()
  })
  view.webContents.on('did-start-navigation', (event, targetUrl, isInPlace, isMainFrame) => {
    if (!isMainFrame || isInPlace) return
    tab.loading = true
    tab.lastLoadError = null
    tab.url = targetUrl || tab.url
    sendAiBrowserState()
  })
  view.webContents.on('did-finish-load', () => {
    tab.loading = false
    tab.lastLoadError = null
    tab.url = view.webContents.getURL() || tab.url
    sendAiBrowserState()
  })
  view.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3) return
    tab.loading = false
    tab.lastLoadError = { errorCode, errorDescription, url: validatedURL || tab.url }
    sendAiBrowserState()
  })
  view.webContents.on('did-stop-loading', () => {
    tab.loading = false
    tab.url = view.webContents.getURL() || tab.url
    sendAiBrowserState()
  })
  view.webContents.on('did-navigate', (event, targetUrl) => {
    tab.url = targetUrl
    sendAiBrowserState()
  })
  view.webContents.on('did-navigate-in-page', (event, targetUrl) => {
    tab.url = targetUrl
    sendAiBrowserState()
  })
  view.webContents.on('destroyed', () => {
    aiBrowserTabs.delete(id)
    if (aiBrowserActiveTabId === id) aiBrowserActiveTabId = aiBrowserTabs.keys().next().value || null
    sendAiBrowserState()
  })
  aiBrowserActiveTabId = id
  sendAiBrowserState()
  if (url !== 'about:blank') {
    try {
      await view.webContents.loadURL(url)
    } catch (error) {
      tab.loading = false
      tab.title = '页面加载失败'
      sendAiBrowserState()
      throw error
    }
  }
  return aiBrowserTabSummary(tab)
}

function activateAiBrowserTab(tabId) {
  getAiBrowserTab(tabId)
  aiBrowserActiveTabId = tabId
  hideAiBrowserViews()
  sendAiBrowserState()
  return { activeTabId: aiBrowserActiveTabId }
}

function openAiBrowserWindow() {
  if (aiBrowserWindow && !aiBrowserWindow.isDestroyed()) {
    aiBrowserWindow.show()
    aiBrowserWindow.focus()
    return aiBrowserWindow
  }
  aiBrowserWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 860,
    minHeight: 560,
    title: 'Loopra AI 浏览器',
    icon: appIconPath,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })
  aiBrowserWindow.on('closed', () => {
    destroyAiBrowserTabs()
    aiBrowserWindow = null
  })
  aiBrowserWindow.webContents.on('did-finish-load', async () => {
    if (!aiBrowserTabs.size) {
      try {
        await createAiBrowserTab('about:blank')
      } catch (error) {
        sendAiBrowserActivity('failed', `创建浏览器标签失败：${error.message}`)
      }
    }
    sendAiBrowserState()
    aiBrowserWindow?.webContents.send('ai-browser-activity', aiBrowserActivity)
  })
  if (isDev) {
    aiBrowserWindow.loadURL('http://localhost:3000/?aiBrowser=1')
  } else {
    aiBrowserWindow.loadFile(path.join(__dirname, '../renderer/index.html'), { query: { aiBrowser: '1' } })
  }
  return aiBrowserWindow
}

async function aiBrowserNewTab(rawUrl) {
  if (!aiBrowserWindow || aiBrowserWindow.isDestroyed()) openAiBrowserWindow()
  const tab = await createAiBrowserTab(rawUrl || 'about:blank')
  const tabCount = aiBrowserTabs.size
  const cleanupRecommended = tabCount > AI_BROWSER_TAB_CLEANUP_THRESHOLD
  return {
    tab,
    activeTabId: aiBrowserActiveTabId,
    tabCount,
    cleanupRecommended,
    warning: cleanupRecommended
      ? `当前已有 ${tabCount} 个标签页。请在完成当前步骤后调用 browser_tabs，并关闭不再需要的非活动标签页；达到 ${AI_BROWSER_MAX_TABS} 个后将无法新建。`
      : undefined
  }
}

async function aiBrowserNavigate(tabId, rawUrl) {
  const tab = getAiBrowserTab(tabId)
  const url = normalizeAiBrowserUrl(rawUrl)
  tab.loading = true
  sendAiBrowserState()
  await tab.view.webContents.loadURL(url)
  tab.url = tab.view.webContents.getURL() || url
  return aiBrowserTabSummary(tab)
}

function aiBrowserHistory(tabId, action) {
  const tab = getAiBrowserTab(tabId)
  const contents = tab.view.webContents
  if (action === 'back' && contents.canGoBack()) contents.goBack()
  else if (action === 'forward' && contents.canGoForward()) contents.goForward()
  else if (action === 'reload') contents.reload()
  return aiBrowserTabSummary(tab)
}

function normalizeAiBrowserBounds(rawBounds) {
  if (!aiBrowserWindow || !rawBounds || typeof rawBounds !== 'object') throw new Error('Invalid native view bounds')
  const contentBounds = aiBrowserWindow.getContentBounds()
  const values = ['x', 'y', 'width', 'height'].map((key) => Math.round(Number(rawBounds[key])))
  if (!values.every(Number.isFinite) || values[2] < 1 || values[3] < 1) throw new Error('Invalid native view bounds')
  const x = Math.max(0, Math.min(values[0], contentBounds.width - 1))
  const y = Math.max(0, Math.min(values[1], contentBounds.height - 1))
  return { x, y, width: Math.max(1, Math.min(values[2], contentBounds.width - x)), height: Math.max(1, Math.min(values[3], contentBounds.height - y)) }
}

function waitForAiBrowserPageLoad(tab, timeoutMs = 30_000) {
  const contents = tab.view.webContents
  if (contents.isDestroyed()) return Promise.reject(new Error('Browser tab was closed while loading'))
  if (tab.lastLoadError) {
    return Promise.reject(new Error(`Page load failed: ${tab.lastLoadError.errorDescription} (${tab.lastLoadError.errorCode})`))
  }
  if (!tab.loading && !contents.isLoadingMainFrame()) return Promise.resolve()

  return new Promise((resolve, reject) => {
    const cleanup = () => {
      clearTimeout(timeout)
      contents.removeListener('did-finish-load', handleFinish)
      contents.removeListener('did-fail-load', handleFailure)
      contents.removeListener('destroyed', handleDestroyed)
    }
    const handleFinish = () => {
      cleanup()
      resolve()
    }
    const handleFailure = (event, errorCode, errorDescription, validatedURL, isMainFrame) => {
      if (!isMainFrame || errorCode === -3) return
      cleanup()
      reject(new Error(`Page load failed: ${errorDescription} (${errorCode})`))
    }
    const handleDestroyed = () => {
      cleanup()
      reject(new Error('Browser tab was closed while loading'))
    }
    const timeout = setTimeout(() => {
      cleanup()
      reject(new Error(`Page load timed out after ${Math.round(timeoutMs / 1000)} seconds`))
    }, timeoutMs)
    contents.once('did-finish-load', handleFinish)
    contents.on('did-fail-load', handleFailure)
    contents.once('destroyed', handleDestroyed)
  })
}

async function aiBrowserSnapshot(tabId) {
  const tab = getAiBrowserTab(tabId)
  if (tab.loading || tab.view.webContents.isLoadingMainFrame()) {
    sendAiBrowserActivity('running', 'AI 正在等待页面加载完成', { method: 'screenshot', tabId: tab.id })
  }
  await waitForAiBrowserPageLoad(tab)
  await tab.view.webContents.executeJavaScript(`
    new Promise((resolve) => {
      const afterPaint = () => requestAnimationFrame(() => requestAnimationFrame(resolve));
      if (document.readyState === 'complete') afterPaint();
      else window.addEventListener('load', afterPaint, { once: true });
    })
  `, true)
  await tab.view.webContents.executeJavaScript(`
    new Promise((resolve) => {
      let settled = false;
      const finish = () => {
        if (settled) return;
        settled = true;
        observer.disconnect();
        clearTimeout(maxWait);
        clearTimeout(quietWait);
        resolve();
      };
      const observer = new MutationObserver(() => {
        clearTimeout(quietWait);
        quietWait = setTimeout(finish, 250);
      });
      let quietWait = setTimeout(finish, 250);
      const maxWait = setTimeout(finish, 1500);
      observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    })
  `, true)
  const snapshot = await tab.view.webContents.executeJavaScript(`
    (() => {
      const MAX_NODES = 180;
      const MAX_DEPTH = 7;
      const MAX_ELEMENTS = 120;
      let count = 0;
      let targetIndex = 0;
      const targetIds = new WeakMap();
      const clean = (value, max = 240) => String(value || '').replace(/\\s+/g, ' ').trim().slice(0, max);
      const styleCache = new WeakMap();
      const hiddenCache = new WeakMap();
      const styleOf = (el) => {
        let style = styleCache.get(el);
        if (!style) {
          style = getComputedStyle(el);
          styleCache.set(el, style);
        }
        return style;
      };
      const parentOf = (el) => el.parentElement || el.getRootNode?.().host || null;
      const hidden = (el) => {
        if (hiddenCache.has(el)) return hiddenCache.get(el);
        let result = false;
        for (let node = el; node; node = parentOf(node)) {
          const style = styleOf(node);
          if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0' || node.getAttribute('aria-hidden') === 'true') {
            result = true;
            break;
          }
        }
        hiddenCache.set(el, result);
        return result;
      };
      const ignored = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'META', 'LINK', 'HEAD', 'SVG', 'PATH']);
      const structuralInteractive = (el) => el.matches('a[href],button,input,textarea,select,summary,[role="button"],[role="link"],[role="checkbox"],[role="radio"],[role="switch"],[role="tab"],[role="menuitem"],[role="option"],[role="combobox"],[role="textbox"],[role="slider"],[role="treeitem"],[contenteditable="true"],[onclick],[aria-expanded],[aria-haspopup],[tabindex]:not([tabindex="-1"])');
      const interactive = (el) => structuralInteractive(el) || styleOf(el).cursor === 'pointer';
      const collectElements = (root, result = []) => {
        for (const el of root.querySelectorAll('*')) {
          result.push(el);
          if (el.shadowRoot) collectElements(el.shadowRoot, result);
        }
        return result;
      };
      const allElements = collectElements(document);
      const labelledBy = (el) => clean(String(el.getAttribute('aria-labelledby') || '').split(/\\s+/)
        .map((id) => document.getElementById(id)?.innerText || '')
        .join(' '), 180);
      const labelText = (el) => clean([
        el.getAttribute('aria-label'),
        labelledBy(el),
        ...(el.labels ? Array.from(el.labels).map((label) => label.innerText) : []),
        el.closest('label')?.innerText,
        el.getAttribute('placeholder'),
        el.getAttribute('title'),
        el.getAttribute('alt')
      ].find(Boolean), 180);
      const attrs = (el) => {
        const out = {};
        for (const name of ['aria-label', 'aria-expanded', 'aria-haspopup', 'aria-invalid', 'placeholder', 'title', 'alt', 'role', 'type', 'href']) {
          const value = clean(el.getAttribute(name), 180);
          if (value) out[name] = value;
        }
        if (el instanceof HTMLInputElement && el.type !== 'password' && el.value) out.value = clean(el.value, 120);
        return out;
      };
      const textOf = (el, max = 180) => clean(labelText(el) || el.innerText || el.getAttribute('alt') || el.getAttribute('title'), max);
      const directText = (el) => clean(Array.from(el.childNodes)
        .filter((node) => node.nodeType === Node.TEXT_NODE)
        .map((node) => node.textContent)
        .join(' '), 180);
      const actionsFor = (el) => el.matches('input,textarea,[contenteditable="true"]')
        ? ['fill', 'press', 'scroll']
        : (el.matches('select') ? ['select', 'scroll'] : ['click', 'scroll']);
      const stateFor = (el) => {
        const state = {};
        const name = labelText(el);
        if (name) state.name = name;
        for (const name of ['aria-expanded', 'aria-checked', 'aria-selected', 'aria-disabled', 'aria-invalid', 'aria-required']) {
          const value = el.getAttribute(name);
          if (value != null) state[name.slice(5)] = value;
        }
        if ('disabled' in el) state.disabled = Boolean(el.disabled);
        if ('required' in el && el.required) state.required = true;
        if (el instanceof HTMLInputElement) {
          state.inputType = el.type;
          if (el.type === 'checkbox' || el.type === 'radio') state.checked = el.checked;
          if (el.type === 'password') state.sensitive = true;
          else if (el.value) state.value = clean(el.value, 120);
        } else if (el instanceof HTMLTextAreaElement && el.value) {
          state.value = clean(el.value, 120);
        } else if (el instanceof HTMLSelectElement) {
          state.value = clean(el.value, 120);
          state.selectedText = clean(el.selectedOptions[0]?.text, 120);
          state.options = Array.from(el.options).slice(0, 30).map((option) => ({ value: clean(option.value, 120), text: clean(option.text, 120), selected: option.selected }));
        }
        return state;
      };
      const closestActionTarget = (el) => {
        for (let node = el; node && node !== document.body; node = parentOf(node)) {
          if (!ignored.has(node.tagName) && !hidden(node) && interactive(node)) return node;
        }
        return null;
      };
      const overlayRole = (el) => /^(dialog|alertdialog|listbox|menu|tree|grid)$/.test(el.getAttribute('role') || '') || el.matches('dialog[open],[aria-modal="true"],[popover]:not([popover="manual"])');
      const overlayOf = (el) => {
        for (let node = el; node && node !== document.body; node = parentOf(node)) if (overlayRole(node)) return node;
        return null;
      };
      const visibleInViewport = (rect) => rect.bottom > 0 && rect.right > 0 && rect.top < window.innerHeight && rect.left < window.innerWidth;
      const occluded = (el, rect) => {
        if (!visibleInViewport(rect)) return false;
        const x = Math.max(0, Math.min(window.innerWidth - 1, rect.left + Math.min(rect.width / 2, 12)));
        const y = Math.max(0, Math.min(window.innerHeight - 1, rect.top + Math.min(rect.height / 2, 12)));
        const hit = document.elementFromPoint(x, y);
        return Boolean(hit && hit !== el && !el.contains(hit) && !hit.contains(el));
      };
      const targetIdFor = (el) => {
        let id = targetIds.get(el);
        if (!id) {
          id = 'e' + (++targetIndex);
          targetIds.set(el, id);
          el.setAttribute('data-loopra-ai-id', id);
        }
        return id;
      };
      // IDs are a snapshot-scoped contract. Remove stale IDs before assigning the next set.
      allElements.filter((el) => el.hasAttribute('data-loopra-ai-id')).forEach((el) => el.removeAttribute('data-loopra-ai-id'));
      const depthOf = (el) => {
        let depth = 0;
        for (let node = parentOf(el); node; node = parentOf(node)) depth++;
        return depth;
      };
      const candidateByTarget = new Map();
      for (const source of allElements) {
        if (ignored.has(source.tagName) || hidden(source) || (!interactive(source) && source.children.length > 0)) continue;
        const target = closestActionTarget(source);
        if (!target) continue;
        const rect = target.getBoundingClientRect();
        const text = textOf(source) || textOf(target);
        if (rect.width < 2 || rect.height < 2 || (!text && !target.matches('input,textarea,select'))) continue;
        const candidate = { el: target, rect, text, overlay: overlayOf(target) };
        const existing = candidateByTarget.get(target);
        if (!existing || (text.length && text.length < existing.text.length)) candidateByTarget.set(target, candidate);
      }
      const candidateElements = [...candidateByTarget.values()]
        .sort((a, b) => {
          const aOverlay = Boolean(a.overlay);
          const bOverlay = Boolean(b.overlay);
          if (aOverlay !== bOverlay) return aOverlay ? -1 : 1;
          const aVisible = visibleInViewport(a.rect);
          const bVisible = visibleInViewport(b.rect);
          if (aVisible !== bVisible) return aVisible ? -1 : 1;
          const aDepth = depthOf(a.el);
          const bDepth = depthOf(b.el);
          if (aDepth !== bDepth) return bDepth - aDepth;
          return a.rect.top - b.rect.top || a.rect.left - b.rect.left;
        });
      const selectedTargets = [];
      for (const item of candidateElements) {
        if (selectedTargets.length >= MAX_ELEMENTS) break;
        // The list is leaf-first. Keep the specific child control and skip its broader wrapper.
        if (selectedTargets.some((existing) => existing.text === item.text && item.el.contains(existing.el))) continue;
        selectedTargets.push(item);
      }
      const elements = selectedTargets.map(({ el, rect, text, overlay }) => ({
        id: targetIdFor(el),
        tag: el.tagName.toLowerCase(),
        text,
        attrs: attrs(el),
        state: stateFor(el),
        actions: actionsFor(el),
        context: overlay ? 'overlay' : (el.closest('main,[role="main"]') ? 'main' : 'page'),
        inViewport: visibleInViewport(rect),
        occluded: occluded(el, rect),
        rect: {
          x: Math.round(rect.left), y: Math.round(rect.top),
          width: Math.round(rect.width), height: Math.round(rect.height)
        }
      }));
      const build = (el, depth) => {
        if (!el || count >= MAX_NODES || depth > MAX_DEPTH || ignored.has(el.tagName) || hidden(el)) return null;
        const children = [];
        for (const child of el.children) {
          const item = build(child, depth + 1);
          if (item) children.push(item);
          if (count >= MAX_NODES) break;
        }
        const id = targetIds.get(el);
        const text = children.length ? directText(el) : textOf(el, 240);
        const semantic = /^(H[1-6]|P|LI|MAIN|ARTICLE|SECTION|NAV|IMG|LABEL|TABLE|TR|TD|TH)$/.test(el.tagName);
        const meaningful = Boolean(id) || children.length > 0 || semantic;
        if (!meaningful || (!text && !id && children.length === 0)) return null;
        if (!id && !semantic && !text && children.length === 1) return children[0];
        const node = { tag: el.tagName.toLowerCase() };
        if (text) node.text = text;
        const nodeAttrs = attrs(el);
        if (Object.keys(nodeAttrs).length) node.attrs = nodeAttrs;
        if (id) {
          node.id = id;
          node.actions = actionsFor(el);
        }
        if (children.length) node.children = children;
        count++;
        return node;
      };
      const body = build(document.body, 0);
      const overlays = allElements.filter((el) => !hidden(el) && overlayRole(el)).slice(0, 8).map((el) => {
        const rect = el.getBoundingClientRect();
        return { role: el.getAttribute('role') || el.tagName.toLowerCase(), text: textOf(el, 220), inViewport: visibleInViewport(rect) };
      });
      const notices = allElements.filter((el) => !hidden(el) && (el.getAttribute('role') === 'alert' || el.hasAttribute('aria-live')))
        .slice(0, 8).map((el) => ({ text: textOf(el, 220), level: el.getAttribute('aria-live') || 'alert' })).filter((notice) => notice.text);
      const sensitiveInputs = allElements.filter((el) => {
        if (!(el instanceof HTMLInputElement)) return false;
        const hint = [el.type, el.name, el.id, el.autocomplete, el.getAttribute('aria-label') || ''].join(' ').toLowerCase();
        return el.type === 'password' || /(captcha|verification|verify|otp|one-time|passcode|password)/.test(hint);
      });
      return {
        title: clean(document.title, 160),
        url: location.href,
        truncated: count >= MAX_NODES,
        nodeCount: count,
        viewport: { width: window.innerWidth, height: window.innerHeight, scrollX: Math.round(window.scrollX), scrollY: Math.round(window.scrollY), documentWidth: Math.round(document.documentElement.scrollWidth), documentHeight: Math.round(document.documentElement.scrollHeight) },
        overlays,
        notices,
        userActionRequired: sensitiveInputs.length ? { recommended: true, reason: '检测到登录或验证相关输入框，请调用 browser_request_user_action 让用户手动完成。' } : undefined,
        elements,
        html: body || { tag: 'body', text: clean(document.body && document.body.innerText, 240) }
      };
    })()
  `, true)
  tab.snapshotId = `${tab.id}-s${++tab.snapshotVersion}`
  return { tabId: tab.id, snapshotId: tab.snapshotId, ...snapshot }
}

async function aiBrowserAct(tabId, targetId, action, value, snapshotId) {
  const tab = getAiBrowserTab(tabId)
  const normalizedTargetId = String(targetId || '').trim().toLowerCase()
  const normalizedAction = String(action || '').trim().toLowerCase()
  if (!/^e\d+$/.test(normalizedTargetId)) throw new Error(`Invalid browser target id: ${targetId || '(empty)'}`)
  if (snapshotId && snapshotId !== tab.snapshotId) throw new Error('Snapshot is stale. Call browser_screenshot again.')
  if (!['click', 'fill', 'select', 'press', 'scroll'].includes(normalizedAction)) throw new Error('Unsupported browser action')
  const result = await tab.view.webContents.executeJavaScript(`
    (() => {
      const id = ${JSON.stringify(normalizedTargetId)};
      const action = ${JSON.stringify(normalizedAction)};
      const value = ${JSON.stringify(value == null ? '' : String(value))};
      const el = document.querySelector('[data-loopra-ai-id="' + id + '"]');
      if (!el) throw new Error('Target no longer exists. Call browser_screenshot again.');
      let style = document.getElementById('__loopra_ai_action_style');
      if (!style) {
        style = document.createElement('style');
        style.id = '__loopra_ai_action_style';
        style.textContent = '[data-loopra-ai-active="true"]{outline:3px solid #0d9488!important;outline-offset:3px!important;box-shadow:0 0 0 6px rgba(13,148,136,.18)!important}#__loopra_ai_action_badge{position:fixed;z-index:2147483647;padding:5px 9px;border-radius:5px;background:#0f766e;color:#fff;font:600 12px system-ui;pointer-events:none;box-shadow:0 4px 14px rgba(0,0,0,.25)}';
        document.documentElement.appendChild(style);
      }
      document.querySelectorAll('[data-loopra-ai-active="true"]').forEach((node) => node.removeAttribute('data-loopra-ai-active'));
      document.getElementById('__loopra_ai_action_badge')?.remove();
      el.setAttribute('data-loopra-ai-active', 'true');
      const rect = el.getBoundingClientRect();
      const badge = document.createElement('div');
      badge.id = '__loopra_ai_action_badge';
      badge.textContent = ({ click: 'AI 点击', fill: 'AI 输入', select: 'AI 选择', press: 'AI 按键', scroll: 'AI 定位' })[action] || 'AI 操作';
      badge.style.left = Math.max(8, Math.min(window.innerWidth - 90, rect.left)) + 'px';
      badge.style.top = Math.max(8, rect.top - 32) + 'px';
      document.documentElement.appendChild(badge);
      setTimeout(() => {
        el.removeAttribute('data-loopra-ai-active');
        badge.remove();
      }, 1600);
      el.scrollIntoView({ block: 'center', inline: 'nearest' });
      if (action === 'scroll') return { action, targetId: id };
      if (action === 'click') { el.click(); return { action, targetId: id }; }
      if (action === 'fill') {
        if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el.isContentEditable)) throw new Error('Target cannot accept text');
        el.focus();
        if (el.isContentEditable) el.textContent = value;
        else {
          const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set;
          setter.call(el, value);
        }
        el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return { action, targetId: id };
      }
      if (action === 'select') {
        if (!(el instanceof HTMLSelectElement)) throw new Error('Target is not a select element');
        el.value = value;
        if (el.value !== value) throw new Error('Option not found');
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return { action, targetId: id };
      }
      el.focus();
      el.dispatchEvent(new KeyboardEvent('keydown', { key: value || 'Enter', bubbles: true }));
      el.dispatchEvent(new KeyboardEvent('keyup', { key: value || 'Enter', bubbles: true }));
      return { action, targetId: id };
    })()
  `, true)
  return result
}

function readBridgeBody(request) {
  return new Promise((resolve, reject) => {
    let body = ''
    request.setEncoding('utf8')
    request.on('data', (chunk) => {
      body += chunk
      if (body.length > 128 * 1024) reject(new Error('Request body too large'))
    })
    request.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}) } catch { reject(new Error('Invalid JSON body')) }
    })
    request.on('error', reject)
  })
}

function writeBridgeResponse(response, status, payload) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
  response.end(JSON.stringify(payload))
}

function startAiBrowserBridge() {
  if (aiBrowserBridgeReady) return aiBrowserBridgeReady
  aiBrowserBridgeReady = new Promise((resolve, reject) => {
    aiBrowserBridge = http.createServer(async (request, response) => {
    if (request.socket.remoteAddress && !['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(request.socket.remoteAddress)) {
      writeBridgeResponse(response, 403, { success: false, error: 'Local requests only' })
      return
    }
    if (request.method === 'GET' && request.url === '/health') {
      writeBridgeResponse(response, 200, { success: true, data: { service: 'loopra-ai-browser' } })
      return
    }
    if (request.method !== 'POST' || !request.url?.startsWith('/browser/')) {
      writeBridgeResponse(response, 404, { success: false, error: 'Not found' })
      return
    }
    try {
      const payload = await readBridgeBody(request)
      const method = request.url.slice('/browser/'.length).split('?')[0]
      const targetTabId = String(payload.tabId || aiBrowserActiveTabId || '')
      const actionLabels = { click: '点击元素', fill: '输入内容', select: '选择选项', press: '发送按键', scroll: '定位元素' }
      const runningMessages = {
        'new-tab': 'AI 正在打开新标签页',
        tabs: 'AI 正在查看标签页',
        navigate: 'AI 正在跳转页面',
        screenshot: 'AI 正在读取页面结构',
        act: `AI 正在${actionLabels[payload.action] || '操作页面'}`,
        'request-user-action': 'AI 正在请求你手动完成浏览器操作',
        'close-tab': 'AI 正在关闭标签页'
      }
      if (targetTabId && ['navigate', 'screenshot', 'act', 'request-user-action', 'close-tab'].includes(method) && aiBrowserTabs.has(targetTabId)) {
        activateAiBrowserTab(targetTabId)
      }
      if (method === 'request-user-action') {
        const browserWindow = openAiBrowserWindow()
        if (browserWindow.isMinimized()) browserWindow.restore()
        browserWindow.show()
        browserWindow.focus()
      }
      sendAiBrowserActivity('running', runningMessages[method] || 'AI 正在操作浏览器', {
        method,
        tabId: targetTabId || null,
        targetId: payload.targetId || null,
        action: payload.action || null
      })
      let data
      if (method === 'new-tab') data = await aiBrowserNewTab(payload.url)
      else if (method === 'tabs') data = { activeTabId: aiBrowserActiveTabId, tabs: [...aiBrowserTabs.values()].map(aiBrowserTabSummary) }
      else if (method === 'navigate') data = await aiBrowserNavigate(payload.tabId, payload.url)
      else if (method === 'screenshot') data = await aiBrowserSnapshot(payload.tabId || aiBrowserActiveTabId)
      else if (method === 'act') data = await aiBrowserAct(payload.tabId || aiBrowserActiveTabId, payload.targetId, payload.action, payload.value, payload.snapshotId)
      else if (method === 'request-user-action') {
        data = { activeTabId: aiBrowserActiveTabId }
      }
      else if (method === 'close-tab') {
        const closed = closeAiBrowserTab(String(payload.tabId || aiBrowserActiveTabId || ''))
        sendAiBrowserState()
        data = { closed, activeTabId: aiBrowserActiveTabId }
      } else throw new Error('Unknown browser method')
      const completedMessages = {
        'new-tab': 'AI 已打开新标签页',
        tabs: 'AI 已读取标签页列表',
        navigate: 'AI 已完成页面跳转',
        screenshot: 'AI 已读取页面结构',
        act: `AI 已完成${actionLabels[payload.action] || '页面操作'}`,
        'close-tab': 'AI 已关闭标签页'
      }
      const isUserTakeover = method === 'request-user-action'
      sendAiBrowserActivity(isUserTakeover ? 'waiting' : 'completed', isUserTakeover
        ? `等待你手动完成：${String(payload.message || '请在浏览器中完成操作').slice(0, 180)}`
        : (completedMessages[method] || 'AI 操作已完成'), {
        method,
        tabId: targetTabId || aiBrowserActiveTabId,
        targetId: payload.targetId || null,
        action: payload.action || null
      })
      writeBridgeResponse(response, 200, { success: true, data })
    } catch (error) {
      sendAiBrowserActivity('failed', `AI 操作失败：${error.message || '未知错误'}`)
      writeBridgeResponse(response, 400, { success: false, error: error.message || 'Browser request failed' })
    }
    })

    const listen = (port) => {
      const handleStartError = (error) => {
        if (error.code === 'EADDRINUSE' && port !== 0) {
          console.warn(`[ai-browser] port ${port} is busy; falling back to a dynamic port`)
          listen(0)
          return
        }
        aiBrowserBridge = null
        aiBrowserBridgeReady = null
        aiBrowserBridgeAddress = ''
        reject(error)
      }
      aiBrowserBridge.once('error', handleStartError)
      aiBrowserBridge.listen(port, '127.0.0.1', () => {
        aiBrowserBridge.removeListener('error', handleStartError)
        const address = aiBrowserBridge.address()
        aiBrowserBridgeAddress = `http://127.0.0.1:${address.port}`
        aiBrowserBridge.on('error', (error) => console.error('[ai-browser] local bridge failed:', error.message))
        console.log(`[ai-browser] local bridge listening on ${aiBrowserBridgeAddress}`)
        resolve(aiBrowserBridgeAddress)
      })
    }

    listen(Number.isInteger(AI_BROWSER_BRIDGE_PREFERRED_PORT) && AI_BROWSER_BRIDGE_PREFERRED_PORT > 0
      ? AI_BROWSER_BRIDGE_PREFERRED_PORT
      : 0)
  })
  return aiBrowserBridgeReady
}

function stopAiBrowserBridge() {
  if (aiBrowserBridge) aiBrowserBridge.close()
  aiBrowserBridge = null
  aiBrowserBridgeReady = null
  aiBrowserBridgeAddress = ''
}

ipcMain.handle('open-ai-browser-window', (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized browser request')
  openAiBrowserWindow()
  return { success: true }
})

ipcMain.handle('get-ai-browser-bridge-address', async (event) => {
  if (event.sender !== mainWindow?.webContents) throw new Error('Unauthorized browser request')
  await startAiBrowserBridge()
  if (!aiBrowserBridgeAddress) throw new Error('AI browser bridge is not listening')
  return aiBrowserBridgeAddress
})

ipcMain.handle('ai-browser-new-tab', async (event, rawUrl) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  return aiBrowserNewTab(rawUrl)
})

ipcMain.handle('ai-browser-navigate', async (event, tabId, rawUrl) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  return aiBrowserNavigate(tabId, rawUrl)
})

ipcMain.handle('ai-browser-history', (event, tabId, action) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  return aiBrowserHistory(tabId, action)
})

ipcMain.handle('ai-browser-activate-tab', (event, tabId) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  return activateAiBrowserTab(tabId)
})

ipcMain.handle('ai-browser-close-tab', (event, tabId) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  const closed = closeAiBrowserTab(tabId)
  sendAiBrowserState()
  return { closed, activeTabId: aiBrowserActiveTabId }
})

ipcMain.handle('ai-browser-get-state', (event) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  return { activeTabId: aiBrowserActiveTabId, tabs: [...aiBrowserTabs.values()].map(aiBrowserTabSummary) }
})

ipcMain.handle('ai-browser-view-show', (event, tabId, rawBounds) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  const tab = getAiBrowserTab(tabId)
  if (!tab.attached) {
    aiBrowserWindow.contentView.addChildView(tab.view)
    tab.attached = true
  }
  hideAiBrowserViews()
  tab.view.setBounds(normalizeAiBrowserBounds(rawBounds))
  tab.view.setVisible(true)
  return { success: true }
})

ipcMain.handle('ai-browser-view-hide', (event) => {
  if (event.sender !== aiBrowserWindow?.webContents) throw new Error('Unauthorized browser request')
  hideAiBrowserViews()
  return { success: true }
})

// ==================== Element Inspector ====================

function openElementInspectorWindow(initialUrl = '') {
  if (elementInspectorWindow && !elementInspectorWindow.isDestroyed()) {
    elementInspectorWindow.show()
    elementInspectorWindow.focus()
    if (initialUrl) {
      if (elementInspectorReady) elementInspectorWindow.webContents.send('element-inspector-load-url', initialUrl)
      else elementInspectorPendingUrl = initialUrl
    }
    return elementInspectorWindow
  }

  elementInspectorReady = false
  elementInspectorPendingUrl = initialUrl

  elementInspectorWindow = new BrowserWindow({
    width: 1180,
    height: 720,
    minWidth: 840,
    minHeight: 540,
    title: 'Loopra 元素检查',
    icon: appIconPath,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })
  elementInspectorWindow.on('closed', () => {
    if (elementWebView && !elementWebView.webContents.isDestroyed()) elementWebView.webContents.close()
    elementWebView = null
    elementInspectorWindow = null
    elementInspectorReady = false
    elementInspectorPendingUrl = ''
  })
  if (isDev) {
    elementInspectorWindow.loadURL('http://localhost:3000/?elementInspector=1')
  } else {
    elementInspectorWindow.loadFile(path.join(__dirname, '../renderer/index.html'), { query: { elementInspector: '1' } })
  }
  return elementInspectorWindow
}

ipcMain.handle('open-element-inspector-window', (event, rawUrl) => {
  const isMainWindow = event.sender === mainWindow?.webContents
  const isDesktopChatTab = [...desktopChatTabs.values()].some((tab) => tab.view.webContents === event.sender)
  if (!isMainWindow && !isDesktopChatTab) throw new Error('Unauthorized inspector request')
  const url = rawUrl ? validateInspectableUrl(rawUrl) : ''
  openElementInspectorWindow(url)
  return { success: true }
})

ipcMain.on('element-inspector-ready', (event) => {
  if (event.sender !== elementInspectorWindow?.webContents) return
  elementInspectorReady = true
  if (!elementInspectorPendingUrl) return
  event.sender.send('element-inspector-load-url', elementInspectorPendingUrl)
  elementInspectorPendingUrl = ''
})

ipcMain.on('element-inspector-send', (event, payload) => {
  if (event.sender !== elementInspectorWindow?.webContents || !payload || typeof payload !== 'object') return
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (mainWindow?.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow?.focus()
  const activeDesktopTab = desktopChatTabs.get(desktopChatActiveTabId)
  if (activeDesktopTab && !activeDesktopTab.view.webContents.isDestroyed()) {
    activeDesktopTab.view.webContents.send('desktop-chat-tab-element-inspection', payload)
    return
  }
  mainWindow.webContents.send('element-inspector-draft', payload)
})

function getOrCreateElementWebView() {
  if (elementWebView && !elementWebView.webContents.isDestroyed()) return elementWebView

  elementWebView = new WebContentsView({
    webPreferences: {
      preload: path.join(__dirname, 'element-preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  elementWebView.setVisible(false)
  elementWebView.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
  elementWebView.webContents.on('did-finish-load', () => {
    elementInspectorWindow?.webContents.send('element-webview-loaded', { url: elementWebView.webContents.getURL() })
  })
  elementWebView.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL, isMainFrame) => {
    if (isMainFrame) elementInspectorWindow?.webContents.send('element-webview-failed', { errorCode, errorDescription, url: validatedURL })
  })
  elementWebView.webContents.on('destroyed', () => { elementWebView = null })
  return elementWebView
}

function normalizeElementViewBounds(rawBounds) {
  if (!elementInspectorWindow || !rawBounds || typeof rawBounds !== 'object') throw new Error('Invalid native view bounds')
  const contentBounds = elementInspectorWindow.getContentBounds()
  const values = ['x', 'y', 'width', 'height'].map((key) => Math.round(Number(rawBounds[key])))
  if (!values.every(Number.isFinite) || values[2] < 1 || values[3] < 1) throw new Error('Invalid native view bounds')
  const x = Math.max(0, Math.min(values[0], contentBounds.width - 1))
  const y = Math.max(0, Math.min(values[1], contentBounds.height - 1))
  return {
    x,
    y,
    width: Math.max(1, Math.min(values[2], contentBounds.width - x)),
    height: Math.max(1, Math.min(values[3], contentBounds.height - y))
  }
}

function validateInspectableUrl(rawUrl) {
  const url = new URL(String(rawUrl || ''))
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Only HTTP(S) URLs are supported')
  return url.href
}

ipcMain.handle('element-webview-load', async (event, rawUrl) => {
  if (event.sender !== elementInspectorWindow?.webContents) throw new Error('Unauthorized native view request')
  const view = getOrCreateElementWebView()
  const url = validateInspectableUrl(rawUrl)
  await view.webContents.loadURL(url)
  return { success: true, url }
})

ipcMain.handle('element-webview-show', (event, rawBounds) => {
  if (event.sender !== elementInspectorWindow?.webContents) throw new Error('Unauthorized native view request')
  const view = getOrCreateElementWebView()
  elementInspectorWindow.contentView.addChildView(view)
  view.setBounds(normalizeElementViewBounds(rawBounds))
  view.setVisible(true)
  return { success: true }
})

ipcMain.handle('element-webview-hide', (event) => {
  if (event.sender !== elementInspectorWindow?.webContents) throw new Error('Unauthorized native view request')
  elementWebView?.setVisible(false)
  return { success: true }
})

ipcMain.on('element-inspected', (event, payload) => {
  if (event.sender !== elementWebView?.webContents || !payload || typeof payload !== 'object') return
  elementInspectorWindow?.webContents.send('element-inspected', payload)
})

/** 在主窗口的 child frame 中寻找 ElementPanel 的 iframe */
function findIframeFrame(frame) {
  if (!frame || !frame.frames) return null
  // 优先找第一个 loaded 的直接子 frame
  for (const child of frame.frames) {
    if (child.url && child.url !== 'about:blank') {
      return child
    }
  }
  // 没有直接子 frame，递归查找
  for (const child of frame.frames) {
    const found = findIframeFrame(child)
    if (found) return found
  }
  return null
}

/**
 * 注入元素检测脚本到 iframe
 * 利用 Electron 主进程权限，跨域 iframe 也能执行 JS
 */
ipcMain.handle('inspector-inject', async (event) => {
  if (event.sender !== elementInspectorWindow?.webContents) throw new Error('Unauthorized inspector request')
  if (!elementInspectorWindow) return { success: false, reason: 'no_window' }

  const inspectorTarget = elementWebView?.webContents || findIframeFrame(elementInspectorWindow.webContents.mainFrame)
  if (!inspectorTarget) return { success: false, reason: 'no_preview' }

  const code = `
(function(){
  // 已注入则跳过（但允许重新激活）
  if(window.__loopraInspectorInjected) return;
  window.__loopraInspectorInjected = true;

  var oldStyle = document.getElementById('__loopra_elem_style');
  if(oldStyle) oldStyle.remove();

  // ---- 注入高亮样式 ----
  var style = document.createElement('style');
  style.id = '__loopra_elem_style';
  style.textContent = '*.__loopra-highlight{outline:2px dashed #2563eb !important;outline-offset:2px !important;background:rgba(37,99,235,0.08) !important;cursor:crosshair !important}';
  document.head.appendChild(style);

  function report(payload){
    if(window.loopraElementInspector && typeof window.loopraElementInspector.report === 'function'){
      window.loopraElementInspector.report(payload);
    }else if(window.parent){
      window.parent.postMessage(payload, '*');
    }
  }

  // ---- 工具函数：安全序列化值（postMessage 要求可结构化克隆） ----
  function safeProp(v){
    if(v===null||v===undefined) return '';
    var t=typeof v;
    if(t==='string'||t==='number'||t==='boolean') return v;
    if(t==='function') return '\u0192()';
    if(t==='symbol') return v.toString();
    try{ return JSON.parse(JSON.stringify(v)); }catch(e){ return String(v); }
  }

  // ---- 工具函数：提取 Vue 组件信息 ----
  function getVueInfo(el){
    try{
      var vn = el.__vueParentComponent;
      if(vn){
        var type = vn.type;
        var name = typeof type === 'object' ? (type.name || type.__name || type.displayName || 'Anonymous') : '' + type;
        var props = {};
        if(vn.props) for(var k in vn.props){
          if(vn.props.hasOwnProperty(k) && k.indexOf('on')!==0 && k.indexOf('$')!==0)
            props[k] = safeProp(vn.props[k]);
        }
        var children = [];
        try{ if(type.components) children = Object.keys(type.components).filter(function(x){return x!=='Fragment'&&x!=='Teleport'&&x!=='Suspense'}); }catch(e){}
        return {name:name, props:props, file:type.__file||type.__source||'', children:children};
      }
      // 尝试 __vnode
      var vnd = el.__vnode;
      if(vnd && vnd.component){
        var comp = vnd.component, type2 = comp.type;
        var name2 = typeof type2 === 'object' ? (type2.name||type2.__name||type2.displayName||'Anonymous') : ''+type2;
        var props2 = {};
        if(comp.props) for(var k2 in comp.props){ if(k2.indexOf('on')!==0) props2[k2] = safeProp(comp.props[k2]); }
        var children2 = [];
        try{ if(type2.components) children2 = Object.keys(type2.components).filter(function(x){return x!=='Fragment'&&x!=='Teleport'&&x!=='Suspense'}); }catch(e){}
        return {name:name2, props:props2, file:type2.__file||type2.__source||'', children:children2};
      }
    }catch(e){}
    return null;
  }

  // ---- 工具函数：提取元素属性 ----
  function getAttrs(el){
    if(!el||!el.attributes) return [];
    var keep = ['id','class','type','name','value','placeholder','href','src','alt','title','role','for','data-v-','aria-'];
    var r = [];
    for(var i=0;i<el.attributes.length;i++){
      var a=el.attributes[i], n=a.name, v=a.value;
      if(!v&&v!=='') continue;
      if(n==='style'||n==='class') continue;
      if(v.length>50) continue;
      for(var j=0;j<keep.length;j++){ if(n===keep[j]||n.indexOf(keep[j])===0){ r.push({key:n, val:v.length>40?v.substring(0,40)+'\u2026':v}); break; } }
    }
    return r;
  }

  // ---- 工具函数：构建 CSS 选择器 ----
  function getSelector(el){
    var parts=[], cur=el, max=10;
    while(cur&&cur!==document.body&&max-->0){
      var seg=cur.tagName.toLowerCase();
      if(cur.id){ parts.unshift('#'+cur.id); break; }
      if(cur.className&&typeof cur.className==='string'){
        var cls=cur.className.trim().split(/\\s+/).filter(function(c){return c&&c.indexOf('__loopra')!==0&&c!=='active'&&c!=='selected'&&c!=='hover';}).slice(0,2);
        if(cls.length) seg+='.'+cls.join('.');
      }
      var p=cur.parentElement;
      if(p){
        var sib=[].filter.call(p.children,function(s){return s.tagName===cur.tagName;});
        if(sib.length>1){ var idx=sib.indexOf(cur)+1; seg+=':nth-child('+idx+')'; }
      }
      parts.unshift(seg);
      cur=cur.parentElement;
    }
    return parts.join(' > ');
  }

  // ---- 组件路径（向上查找） ----
  function buildCompPath(el, vueInfo){
    var path=[];
    if(vueInfo&&vueInfo.name&&vueInfo.name!=='Anonymous'){
      var cur=el.parentElement;
      while(cur&&cur!==document.body){
        var pinfo=getVueInfo(cur);
        if(pinfo&&pinfo.name!=='Anonymous'&&pinfo.name!=='Transition'&&pinfo.name!=='KeepAlive'&&pinfo.name.indexOf('V')!==0){
          if(path.indexOf(pinfo.name)===-1) path.unshift(pinfo.name);
        }
        cur=cur.parentElement;
      }
      if(path.indexOf(vueInfo.name)===-1) path.push(vueInfo.name);
    }
    return path;
  }

  // ---- 点击元素（具名函数，便于 removeEventListener） ----
  function __loopraClickHandler(e){
    // 仅在设计模式激活时阻止事件
    if(!window.__loopraInspectorInjected) return;
    e.stopPropagation();
    e.preventDefault();
    var el=e.target; if(!el) return;
    try{
      var vueInfo=getVueInfo(el);
      var attrs=getAttrs(el);
      var selector=getSelector(el);
      var text=(el.textContent||'').trim();
      if(text.length>60) text=text.substring(0,60)+'\u2026';
      var path=buildCompPath(el, vueInfo);

      report({
        type:'loopra-element-click',
        tag:el.tagName?el.tagName.toLowerCase():'?',
        text:text,
        selector:selector,
        attrs:attrs,
        id:el.id||'',
        vueComponent:vueInfo,
        path:path,
        children:vueInfo?vueInfo.children:[]
      });
    }catch(pe){
      try{
        report({
          type:'loopra-element-click',
          tag:el.tagName?el.tagName.toLowerCase():'?',
          text:(el.textContent||'').trim().substring(0,60),
          selector:'',
          attrs:[],
          id:el.id||'',
          vueComponent:{name:'\u6dfb\u52a0\u7ec4\u4ef6',props:{},file:'',children:[]},
          path:[],
          children:[]
        });
      }catch(e2){}
    }
  }

  function __loopraMouseoverHandler(e){
    // 仅在设计模式激活时高亮
    if(!window.__loopraInspectorInjected) return;
    try{
      [].forEach.call(document.querySelectorAll('.__loopra-highlight'), function(el){el.classList.remove('__loopra-highlight');});
      e.target.classList.add('__loopra-highlight');
    }catch(ex){}
  }

  function __loopraMouseoutHandler(e){
    // 仅在设计模式激活时移除高亮
    if(!window.__loopraInspectorInjected) return;
    try{ e.target.classList.remove('__loopra-highlight'); }catch(ex){}
  }

  // 存储引用，便于 remove 时精确移除
  window.__loopraHandlers = {
    click: __loopraClickHandler,
    mouseover: __loopraMouseoverHandler,
    mouseout: __loopraMouseoutHandler
  };

  document.addEventListener('click', __loopraClickHandler, true);
  document.addEventListener('mouseover', __loopraMouseoverHandler, true);
  document.addEventListener('mouseout', __loopraMouseoutHandler, true);
})();
`

  try {
    await inspectorTarget.executeJavaScript(code)
    return { success: true }
  } catch (e) {
    return { success: false, reason: e.message }
  }
})

/** 移除 iframe 中的检测脚本 */
ipcMain.handle('inspector-remove', async (event) => {
  if (event.sender !== elementInspectorWindow?.webContents) throw new Error('Unauthorized inspector request')
  if (!elementInspectorWindow) return { success: false, reason: 'no_window' }

  const inspectorTarget = elementWebView?.webContents || findIframeFrame(elementInspectorWindow.webContents.mainFrame)
  if (!inspectorTarget) return { success: false, reason: 'no_preview' }

  try {
    await inspectorTarget.executeJavaScript(`
      (function(){
        // 精确移除事件监听器
        if(window.__loopraHandlers){
          try{document.removeEventListener('click', window.__loopraHandlers.click, true);}catch(e){}
          try{document.removeEventListener('mouseover', window.__loopraHandlers.mouseover, true);}catch(e){}
          try{document.removeEventListener('mouseout', window.__loopraHandlers.mouseout, true);}catch(e){}
          window.__loopraHandlers = null;
        }
        // 清理样式和高亮
        var s=document.getElementById('__loopra_elem_style');
        if(s) s.remove();
        [].forEach.call(document.querySelectorAll('.__loopra-highlight'),function(el){el.classList.remove('__loopra-highlight');});
        window.__loopraInspectorInjected = false;
      })();
    `)
    console.log('[Main] inspector-remove: 成功移除检测脚本')
    return { success: true }
  } catch (e) {
    console.error('[Main] inspector-remove: 移除失败:', e.message)
    return { success: false, reason: e.message }
  }
})

// 打开外部链接
ipcMain.handle('open-external', async (event, url) => {
  try {
    await shell.openExternal(url)
    return { success: true }
  } catch (e) {
    console.error('Failed to open external URL:', e)
    return { success: false, error: e.message }
  }
})

// 打开本地文件
ipcMain.handle('open-file', async (event, filePath) => {
  try {
    // 路径验证
    if (!filePath || typeof filePath !== 'string') {
      return { success: false, error: '无效的文件路径' }
    }
    // 防止路径遍历攻击
    if (filePath.includes('..') || filePath.includes('~')) {
      return { success: false, error: '文件路径包含非法字符' }
    }
    // 确保路径是绝对路径
    const absolutePath = path.resolve(filePath)
    // 检查文件是否存在
    if (!fs.existsSync(absolutePath)) {
      return { success: false, error: '文件不存在' }
    }
    // 检查是否是文件（不是目录）
    const stat = fs.statSync(absolutePath)
    if (!stat.isFile()) {
      return { success: false, error: '路径不是文件' }
    }
    await shell.openPath(absolutePath)
    return { success: true }
  } catch (e) {
    console.error('Failed to open file:', e)
    return { success: false, error: e.message }
  }
})
