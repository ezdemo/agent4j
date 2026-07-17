const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('loopraElementInspector', {
  report: (payload) => ipcRenderer.send('element-inspected', payload)
})
