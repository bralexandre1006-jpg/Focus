const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('fapi', {
  cfg: () => ipcRenderer.invoke('cfg:get'),
  setCode: c => ipcRenderer.invoke('cfg:code', c),
  setWidget: (t, on) => ipcRenderer.invoke('widget:set', t, on),
  setOnTop: v => ipcRenderer.invoke('widget:ontop', v),
  setAutostart: v => ipcRenderer.invoke('autostart', v),
  win: a => ipcRenderer.send('win', a),
  hideWidget: t => ipcRenderer.send('widget:hide', t),
  on: (ch, f) => ipcRenderer.on(ch, (e, v) => f(v))
});
