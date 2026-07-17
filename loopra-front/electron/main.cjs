const { app, BrowserWindow, ipcMain, Menu, shell } = require('electron')
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

let mainWindow = null
let loopraWebProcess = null
let currentPort = 0
const loopraWebWindows = new Map()
const execFileAsync = promisify(execFile)

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
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
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
      managed: item.parentPid === managedPid
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
      managed: Number(parentPid) === managedPid || Number(pid) === managedPid
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

// 启动 loopra web <port>
function startLoopraWeb(port) {
  const home = app.getPath('home')
  const binDir = path.join(home, '.loopra', 'bin')
  const binName = isWin ? 'loopra.ps1' : 'loopra'
  const binPath = path.join(binDir, binName)

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
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  // 开发模式：加载 Vite dev server；生产模式：加载打包后的 renderer
  if (isDev) {
    mainWindow.loadURL('http://localhost:3000')
  } else {
    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))
  }

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

  // macOS: 隐藏原生窗口控制按钮（红绿灯），应用内使用自定义标题栏按钮
  if (process.platform === 'darwin') {
    mainWindow.setWindowButtonVisibility(false)
  }

  mainWindow.on('closed', () => { mainWindow = null })
}

app.whenReady().then(() => {
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
})

// ==================== IPC ====================

ipcMain.handle('get_loopra_web_port', async () => currentPort)

ipcMain.handle('get_electron_version', async () => {
  return app.getVersion()
})

ipcMain.handle('get_loopra_web_status', async () => ({
  installed: true,
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

ipcMain.handle('check_install_needed', async () => ({ needed: false }))
ipcMain.handle('install_loopra_web', async () => ({ success: true, steps: ['electron_mock_install'] }))

ipcMain.handle('start_loopra_web', async () => {
  if (loopraWebProcess) return currentPort

  const port = await getDefaultPort()

  // 先检查服务是否已在运行
  if (await healthCheck(port)) {
    console.log(`Loopra Web already running on port ${port}`)
    currentPort = port
    return port
  }

  // 未运行，启动
  try {
    startLoopraWeb(port)
    currentPort = port
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
// ==================== Element Inspector (跨域穿透) ====================

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
ipcMain.handle('inspector-inject', async () => {
  if (!mainWindow) return { success: false, reason: 'no_window' }

  const iframeFrame = findIframeFrame(mainWindow.webContents.mainFrame)
  if (!iframeFrame) return { success: false, reason: 'no_iframe' }

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

      window.parent.postMessage({
        type:'loopra-element-click',
        tag:el.tagName?el.tagName.toLowerCase():'?',
        text:text,
        selector:selector,
        attrs:attrs,
        id:el.id||'',
        vueComponent:vueInfo,
        path:path,
        children:vueInfo?vueInfo.children:[]
      }, '*');
    }catch(pe){
      try{
        window.parent.postMessage({
          type:'loopra-element-click',
          tag:el.tagName?el.tagName.toLowerCase():'?',
          text:(el.textContent||'').trim().substring(0,60),
          selector:'',
          attrs:[],
          id:el.id||'',
          vueComponent:{name:'\u6dfb\u52a0\u7ec4\u4ef6',props:{},file:'',children:[]},
          path:[],
          children:[]
        }, '*');
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
    await iframeFrame.executeJavaScript(code)
    return { success: true }
  } catch (e) {
    return { success: false, reason: e.message }
  }
})

/** 移除 iframe 中的检测脚本 */
ipcMain.handle('inspector-remove', async () => {
  if (!mainWindow) return { success: false, reason: 'no_window' }

  const iframeFrame = findIframeFrame(mainWindow.webContents.mainFrame)
  if (!iframeFrame) return { success: false, reason: 'no_iframe' }

  try {
    await iframeFrame.executeJavaScript(`
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
