"use strict";
const require$$0 = require("electron");
function getAugmentedNamespace(n) {
  if (n.__esModule) return n;
  var f = n.default;
  if (typeof f == "function") {
    var a = function a2() {
      if (this instanceof a2) {
        return Reflect.construct(f, arguments, this.constructor);
      }
      return f.apply(this, arguments);
    };
    a.prototype = f.prototype;
  } else a = {};
  Object.defineProperty(a, "__esModule", { value: true });
  Object.keys(n).forEach(function(k) {
    var d = Object.getOwnPropertyDescriptor(n, k);
    Object.defineProperty(a, k, d.get ? d : {
      enumerable: true,
      get: function() {
        return n[k];
      }
    });
  });
  return a;
}
var main = {};
const __viteBrowserExternal = {};
const __viteBrowserExternal$1 = /* @__PURE__ */ Object.freeze(/* @__PURE__ */ Object.defineProperty({
  __proto__: null,
  default: __viteBrowserExternal
}, Symbol.toStringTag, { value: "Module" }));
const require$$3 = /* @__PURE__ */ getAugmentedNamespace(__viteBrowserExternal$1);
const { app, BrowserWindow, ipcMain, Menu, shell } = require$$0;
const path = require$$3;
const { spawn, execSync } = require$$3;
const fs = require$$3;
const handleSquirrelEvent = () => {
  if (process.argv.length === 1) return false;
  const appFolder = path.resolve(process.execPath, "..");
  const rootAtomFolder = path.resolve(appFolder, "..");
  const updateDotExe = path.resolve(rootAtomFolder, "Update.exe");
  const exeName = path.basename(process.execPath);
  const spawnProcess = (command, args) => {
    try {
      return spawn(command, args, { detached: true });
    } catch {
      return null;
    }
  };
  const squirrelEvent = process.argv[1];
  switch (squirrelEvent) {
    case "--squirrel-install":
    case "--squirrel-updated":
      spawnProcess(updateDotExe, ["--createShortcut", exeName]);
      setTimeout(app.quit, 1e3);
      return true;
    case "--squirrel-uninstall":
      spawnProcess(updateDotExe, ["--removeShortcut", exeName]);
      setTimeout(app.quit, 1e3);
      return true;
    case "--squirrel-obsolete":
      app.quit();
      return true;
  }
  return false;
};
if (handleSquirrelEvent()) ;
const isDev = process.env.NODE_ENV === "development" || !app.isPackaged;
const isWin = process.platform === "win32";
let mainWindow = null;
let agent4jWebProcess = null;
let currentPort = 0;
function getDefaultPort() {
  try {
    const candidates = [];
    if (isDev) {
      candidates.push(path.join(__dirname, "..", "..", "public", "config.json"));
    } else {
      candidates.push(path.join(process.resourcesPath, ".vite", "renderer", "main_window", "config.json"));
    }
    for (const cfgPath of candidates) {
      if (fs.existsSync(cfgPath)) {
        const cfg = JSON.parse(fs.readFileSync(cfgPath, "utf-8"));
        if (cfg.apiBase) {
          const url = new URL(cfg.apiBase);
          return parseInt(url.port, 10) || 4567;
        }
      }
    }
  } catch {
  }
  return 4567;
}
async function healthCheck(port) {
  try {
    const resp = await fetch(`http://127.0.0.1:${port}/api/system/health`, {
      signal: AbortSignal.timeout(3e3)
    });
    return resp.ok;
  } catch {
    return false;
  }
}
function killProcessTree(child) {
  if (!child) return;
  const pid = child.pid;
  if (!pid) return;
  if (isWin) {
    try {
      execSync(`taskkill /pid ${pid} /t /f`, { stdio: "ignore" });
    } catch {
    }
  } else {
    try {
      process.kill(-pid, "SIGTERM");
    } catch {
      return;
    }
    const deadline = Date.now() + 5e3;
    while (Date.now() < deadline) {
      try {
        process.kill(-pid, 0);
        execSync("sleep 0.2");
      } catch {
        return;
      }
    }
    try {
      process.kill(-pid, "SIGKILL");
    } catch {
    }
  }
}
function startAgent4jWeb(port) {
  const home = app.getPath("home");
  const binDir = path.join(home, ".agent4j", "bin");
  const binName = isWin ? "agent4j.ps1" : "agent4j";
  const binPath = path.join(binDir, binName);
  if (!fs.existsSync(binPath)) {
    throw new Error(`agent4j not found: ${binPath}`);
  }
  console.log(`Starting: ${binPath} web ${port}`);
  let child;
  if (isWin) {
    child = spawn("powershell", [
      "-ExecutionPolicy",
      "Bypass",
      "-NoProfile",
      "-File",
      binPath,
      "web",
      String(port)
    ], {
      cwd: binDir,
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true
    });
  } else {
    child = spawn(binPath, ["web", String(port)], {
      cwd: binDir,
      detached: true,
      stdio: ["ignore", "pipe", "pipe"]
    });
  }
  child.stdout.on("data", (d) => console.log(`[agent4j-web] ${d}`));
  child.stderr.on("data", (d) => console.error(`[agent4j-web] ${d}`));
  child.on("exit", (code) => {
    console.log(`agent4j-web exited with code ${code}`);
    agent4jWebProcess = null;
    currentPort = 0;
  });
  child.on("error", (err) => {
    console.error("agent4j-web spawn error:", err);
    agent4jWebProcess = null;
    currentPort = 0;
  });
  agent4jWebProcess = child;
  return child;
}
function cleanupAgent4jWeb() {
  if (agent4jWebProcess) {
    killProcessTree(agent4jWebProcess);
    agent4jWebProcess = null;
    currentPort = 0;
  }
}
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: false,
    titleBarStyle: "hidden",
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });
  if (isDev) {
    mainWindow.loadURL("http://localhost:5173");
  } else {
    mainWindow.loadFile(path.join(__dirname, "../renderer/index.html"));
  }
  if (isDev) mainWindow.webContents.openDevTools();
  mainWindow.webContents.on("context-menu", (event, params) => {
    const menu = Menu.buildFromTemplate([
      { label: "检查元素", click: () => mainWindow.webContents.inspectElement(params.x, params.y) },
      { type: "separator" },
      { role: "reload", label: "刷新" },
      { role: "forceReload", label: "强制刷新" },
      { role: "toggleDevTools", label: "开发者工具" }
    ]);
    menu.popup();
  });
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}
app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});
app.on("window-all-closed", () => {
  cleanupAgent4jWeb();
  if (process.platform !== "darwin") app.quit();
});
app.on("before-quit", () => {
  cleanupAgent4jWeb();
});
ipcMain.handle("get_agent4j_web_port", async () => currentPort);
ipcMain.handle("get_agent4j_web_status", async () => ({
  installed: true,
  running: agent4jWebProcess !== null,
  install_dir: path.join(app.getPath("home"), ".agent4j")
}));
ipcMain.handle("get_resource_dir", async () => {
  if (app.isPackaged) return path.join(process.resourcesPath, "resources");
  return path.join(__dirname, "../resources");
});
ipcMain.handle("check_install_needed", async () => ({ needed: false, reason: "electron_mock" }));
ipcMain.handle("install_agent4j_web", async () => ({ success: true, steps: ["electron_mock_install"] }));
ipcMain.handle("start_agent4j_web", async () => {
  if (agent4jWebProcess) return currentPort;
  const port = getDefaultPort();
  if (await healthCheck(port)) {
    console.log(`Agent4j Web already running on port ${port}`);
    currentPort = port;
    return port;
  }
  try {
    startAgent4jWeb(port);
    currentPort = port;
    return port;
  } catch (error) {
    throw new Error(`Failed to start agent4j web: ${error.message}`);
  }
});
ipcMain.handle("stop_agent4j_web", async () => {
  cleanupAgent4jWeb();
});
ipcMain.handle("check_java_quick", async () => ({ found: true, version: "17.0.0", source: "electron_mock" }));
ipcMain.handle("start_java_download", async () => "started");
ipcMain.handle("window-minimize", () => {
  if (mainWindow) mainWindow.minimize();
});
ipcMain.handle("window-maximize", () => {
  if (mainWindow) mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize();
});
ipcMain.handle("window-close", () => {
  if (mainWindow) mainWindow.close();
});
ipcMain.handle("window-is-maximized", () => mainWindow ? mainWindow.isMaximized() : false);
function findIframeFrame(frame) {
  if (!frame || !frame.frames) return null;
  for (const child of frame.frames) {
    if (child.url && child.url !== "about:blank") {
      return child;
    }
  }
  for (const child of frame.frames) {
    const found = findIframeFrame(child);
    if (found) return found;
  }
  return null;
}
ipcMain.handle("inspector-inject", async () => {
  if (!mainWindow) return { success: false, reason: "no_window" };
  const iframeFrame = findIframeFrame(mainWindow.webContents.mainFrame);
  if (!iframeFrame) return { success: false, reason: "no_iframe" };
  const code = `
(function(){
  // 已注入则跳过（但允许重新激活）
  if(window.__agent4jInspectorInjected) return;
  window.__agent4jInspectorInjected = true;

  var oldStyle = document.getElementById('__agent4j_elem_style');
  if(oldStyle) oldStyle.remove();

  // ---- 注入高亮样式 ----
  var style = document.createElement('style');
  style.id = '__agent4j_elem_style';
  style.textContent = '*.__agent4j-highlight{outline:2px dashed #2563eb !important;outline-offset:2px !important;background:rgba(37,99,235,0.08) !important;cursor:crosshair !important}';
  document.head.appendChild(style);

  // ---- 工具函数：安全序列化值（postMessage 要求可结构化克隆） ----
  function safeProp(v){
    if(v===null||v===undefined) return '';
    var t=typeof v;
    if(t==='string'||t==='number'||t==='boolean') return v;
    if(t==='function') return 'ƒ()';
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
      for(var j=0;j<keep.length;j++){ if(n===keep[j]||n.indexOf(keep[j])===0){ r.push({key:n, val:v.length>40?v.substring(0,40)+'…':v}); break; } }
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
        var cls=cur.className.trim().split(/\\s+/).filter(function(c){return c&&c.indexOf('__agent4j')!==0&&c!=='active'&&c!=='selected'&&c!=='hover';}).slice(0,2);
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
  function __agent4jClickHandler(e){
    // 仅在设计模式激活时阻止事件
    if(!window.__agent4jInspectorInjected) return;
    e.stopPropagation();
    e.preventDefault();
    var el=e.target; if(!el) return;
    try{
      var vueInfo=getVueInfo(el);
      var attrs=getAttrs(el);
      var selector=getSelector(el);
      var text=(el.textContent||'').trim();
      if(text.length>60) text=text.substring(0,60)+'…';
      var path=buildCompPath(el, vueInfo);

      window.parent.postMessage({
        type:'agent4j-element-click',
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
          type:'agent4j-element-click',
          tag:el.tagName?el.tagName.toLowerCase():'?',
          text:(el.textContent||'').trim().substring(0,60),
          selector:'',
          attrs:[],
          id:el.id||'',
          vueComponent:{name:'添加组件',props:{},file:'',children:[]},
          path:[],
          children:[]
        }, '*');
      }catch(e2){}
    }
  }

  function __agent4jMouseoverHandler(e){
    // 仅在设计模式激活时高亮
    if(!window.__agent4jInspectorInjected) return;
    try{
      [].forEach.call(document.querySelectorAll('.__agent4j-highlight'), function(el){el.classList.remove('__agent4j-highlight');});
      e.target.classList.add('__agent4j-highlight');
    }catch(ex){}
  }

  function __agent4jMouseoutHandler(e){
    // 仅在设计模式激活时移除高亮
    if(!window.__agent4jInspectorInjected) return;
    try{ e.target.classList.remove('__agent4j-highlight'); }catch(ex){}
  }

  // 存储引用，便于 remove 时精确移除
  window.__agent4jHandlers = {
    click: __agent4jClickHandler,
    mouseover: __agent4jMouseoverHandler,
    mouseout: __agent4jMouseoutHandler
  };

  document.addEventListener('click', __agent4jClickHandler, true);
  document.addEventListener('mouseover', __agent4jMouseoverHandler, true);
  document.addEventListener('mouseout', __agent4jMouseoutHandler, true);
})();
`;
  try {
    await iframeFrame.executeJavaScript(code);
    return { success: true };
  } catch (e) {
    return { success: false, reason: e.message };
  }
});
ipcMain.handle("inspector-remove", async () => {
  if (!mainWindow) return { success: false, reason: "no_window" };
  const iframeFrame = findIframeFrame(mainWindow.webContents.mainFrame);
  if (!iframeFrame) return { success: false, reason: "no_iframe" };
  try {
    await iframeFrame.executeJavaScript(`
      (function(){
        // 精确移除事件监听器
        if(window.__agent4jHandlers){
          try{document.removeEventListener('click', window.__agent4jHandlers.click, true);}catch(e){}
          try{document.removeEventListener('mouseover', window.__agent4jHandlers.mouseover, true);}catch(e){}
          try{document.removeEventListener('mouseout', window.__agent4jHandlers.mouseout, true);}catch(e){}
          window.__agent4jHandlers = null;
        }
        // 清理样式和高亮
        var s=document.getElementById('__agent4j_elem_style');
        if(s) s.remove();
        [].forEach.call(document.querySelectorAll('.__agent4j-highlight'),function(el){el.classList.remove('__agent4j-highlight');});
        window.__agent4jInspectorInjected = false;
      })();
    `);
    console.log("[Main] inspector-remove: 成功移除检测脚本");
    return { success: true };
  } catch (e) {
    console.error("[Main] inspector-remove: 移除失败:", e.message);
    return { success: false, reason: e.message };
  }
});
ipcMain.handle("open-external", async (event, url) => {
  try {
    await shell.openExternal(url);
    return { success: true };
  } catch (e) {
    console.error("Failed to open external URL:", e);
    return { success: false, error: e.message };
  }
});
module.exports = main;
//# sourceMappingURL=main.cjs.map
