const { ipcMain } = require('electron')
const os = require('os')
const { execFileSync } = require('child_process')
const pty = require('node-pty')

// ==================== 终端（node-pty）管理 ====================
// 每个终端实例分配自增 id，渲染进程通过 id 读写对应 PTY
let nextTerminalId = 1
const terminals = new Map()

// 检测命令是否可用（Windows 用 where，其余平台用 which）
function commandExists(command) {
  try {
    execFileSync(process.platform === 'win32' ? 'where' : 'which', [command], {
      stdio: 'ignore',
      windowsHide: true
    })
    return true
  } catch {
    return false
  }
}

// 按系统检测可用 shell：Windows 优先 PowerShell，其次 CMD，可选 pwsh；
// 其余平台使用 $SHELL 或 bash。返回 [{ id, name, command }] 供渲染进程下拉选择
function listAvailableShells() {
  if (os.platform() === 'win32') {
    const shells = [{ id: 'powershell', name: 'PowerShell', command: 'powershell.exe' }]
    if (process.env.ComSpec) {
      shells.push({ id: 'cmd', name: 'CMD', command: process.env.ComSpec })
    }
    if (commandExists('pwsh')) {
      shells.push({ id: 'pwsh', name: 'PowerShell 7 (pwsh)', command: 'pwsh.exe' })
    }
    return shells
  }
  return [{ id: 'default', name: '默认 Shell', command: process.env.SHELL || 'bash' }]
}

// 解析渲染进程请求的 shell：未指定或不可识别时回退到系统默认（列表第一项）
function resolveShell(shellId, fallback) {
  const shells = listAvailableShells()
  const match = shells.find((item) => item.id === shellId)
  return match ? match.command : (fallback || shells[0].command)
}

// 注册终端相关 IPC 通道，供渲染进程创建/读写/关闭 PTY
function registerTerminalIpc() {
  ipcMain.handle('terminal:create', (event, options = {}) => {
    const id = String(nextTerminalId++)
    const cols = Math.max(2, Number(options.cols) || 80)
    const rows = Math.max(2, Number(options.rows) || 24)
    const cwd = typeof options.cwd === 'string' && options.cwd ? options.cwd : os.homedir()

    const term = pty.spawn(resolveShell(options.shell), [], {
      name: 'xterm-256color',
      cols,
      rows,
      cwd,
      env: process.env
    })

    terminals.set(id, term)

    // PTY 输出 → 渲染进程
    term.onData((data) => {
      if (!event.sender.isDestroyed()) {
        event.sender.send('terminal:data', { id, data })
      }
    })

    // PTY 退出 → 通知渲染进程并清理实例
    term.onExit(({ exitCode }) => {
      terminals.delete(id)
      if (!event.sender.isDestroyed()) {
        event.sender.send('terminal:exit', { id, exitCode })
      }
    })

    return { id, pid: term.pid }
  })

  // 查询当前系统可用的 shell 列表（cmd / powershell / pwsh，按平台检测）
  ipcMain.handle('terminal:list-shells', () => listAvailableShells())

  // 渲染进程键盘输入 → PTY
  ipcMain.on('terminal:input', (event, payload = {}) => {
    const term = terminals.get(String(payload.id))
    if (term && typeof payload.data === 'string') {
      term.write(payload.data)
    }
  })

  // 渲染进程尺寸变化 → PTY
  ipcMain.on('terminal:resize', (event, payload = {}) => {
    const term = terminals.get(String(payload.id))
    const cols = Math.floor(Number(payload.cols))
    const rows = Math.floor(Number(payload.rows))
    if (term && cols > 0 && rows > 0) {
      term.resize(cols, rows)
    }
  })

  // 关闭 PTY（面板收起/组件卸载时调用）
  ipcMain.handle('terminal:kill', (event, id) => {
    const term = terminals.get(String(id))
    if (term) {
      try {
        term.kill()
      } catch (error) {
        console.warn('[terminal] kill 失败:', error.message)
      }
      terminals.delete(String(id))
    }
    return true
  })
}

// 应用退出时统一清理所有 PTY，避免残留 shell 进程
function killAllTerminals() {
  for (const [id, term] of terminals) {
    try {
      term.kill()
    } catch (error) {
      // 进程可能已退出，忽略
    }
    terminals.delete(id)
  }
}

module.exports = { registerTerminalIpc, killAllTerminals }
