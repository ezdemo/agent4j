const { app, BrowserWindow, ipcMain, Menu } = require('electron')
const path = require('path')
const { spawn, execSync } = require('child_process')
const fs = require('fs')

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged
const isWin = process.platform === 'win32'

let mainWindow = null
let agent4jWebProcess = null
let currentPort = 0

function getDefaultPort() {
  try {
    const cfgPath = isDev
      ? path.join(__dirname, '../dist/config.json')
      : path.join(process.resourcesPath, 'dist/config.json')
    const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf-8'))
    if (cfg.apiBase) {
      const url = new URL(cfg.apiBase)
      return parseInt(url.port, 10) || 4567
    }
  } catch { /* ignore */ }
  return 4567
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

// 启动 agent4j web <port>
function startAgent4jWeb(port) {
  const home = app.getPath('home')
  const binDir = path.join(home, '.agent4j', 'bin')
  const binName = isWin ? 'agent4j.ps1' : 'agent4j'
  const binPath = path.join(binDir, binName)

  if (!fs.existsSync(binPath)) {
    throw new Error(`agent4j not found: ${binPath}`)
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

  child.stdout.on('data', (d) => console.log(`[agent4j-web] ${d}`))
  child.stderr.on('data', (d) => console.error(`[agent4j-web] ${d}`))

  child.on('exit', (code) => {
    console.log(`agent4j-web exited with code ${code}`)
    agent4jWebProcess = null
    currentPort = 0
  })

  child.on('error', (err) => {
    console.error('agent4j-web spawn error:', err)
    agent4jWebProcess = null
    currentPort = 0
  })

  agent4jWebProcess = child
  return child
}

// 统一清理
function cleanupAgent4jWeb() {
  if (agent4jWebProcess) {
    killProcessTree(agent4jWebProcess)
    agent4jWebProcess = null
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
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))

  if (isDev) mainWindow.webContents.openDevTools()

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

  mainWindow.on('closed', () => { mainWindow = null })
}

app.whenReady().then(() => {
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  cleanupAgent4jWeb()
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  cleanupAgent4jWeb()
})

// ==================== IPC ====================

ipcMain.handle('get_agent4j_web_port', async () => currentPort)

ipcMain.handle('get_agent4j_web_status', async () => ({
  installed: true,
  running: agent4jWebProcess !== null,
  install_dir: path.join(app.getPath('home'), '.agent4j')
}))

ipcMain.handle('get_resource_dir', async () => {
  if (app.isPackaged) return path.join(process.resourcesPath, 'resources')
  return path.join(__dirname, '../resources')
})

ipcMain.handle('check_install_needed', async () => ({ needed: false, reason: 'electron_mock' }))
ipcMain.handle('install_agent4j_web', async () => ({ success: true, steps: ['electron_mock_install'] }))

ipcMain.handle('start_agent4j_web', async () => {
  if (agent4jWebProcess) return currentPort

  const port = getDefaultPort()

  // 先检查服务是否已在运行
  if (await healthCheck(port)) {
    console.log(`Agent4j Web already running on port ${port}`)
    currentPort = port
    return port
  }

  // 未运行，启动
  try {
    startAgent4jWeb(port)
    currentPort = port
    return port
  } catch (error) {
    throw new Error(`Failed to start agent4j web: ${error.message}`)
  }
})

ipcMain.handle('stop_agent4j_web', async () => {
  cleanupAgent4jWeb()
})

ipcMain.handle('check_java_quick', async () => ({ found: true, version: '17.0.0', source: 'electron_mock' }))
ipcMain.handle('start_java_download', async () => 'started')

ipcMain.handle('window-minimize', () => { if (mainWindow) mainWindow.minimize() })
ipcMain.handle('window-maximize', () => {
  if (mainWindow) mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize()
})
ipcMain.handle('window-close', () => { if (mainWindow) mainWindow.close() })
ipcMain.handle('window-is-maximized', () => mainWindow ? mainWindow.isMaximized() : false)
