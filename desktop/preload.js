const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('fapi', {
  cfg: () => ipcRenderer.invoke('cfg:get'),
  setCode: c => ipcRenderer.invoke('cfg:code', c),
  setWidget: (t, on) => ipcRenderer.invoke('widget:set', t, on),
  setOnTop: v => ipcRenderer.invoke('widget:ontop', v),
  setLocked: v => ipcRenderer.invoke('widget:lock', v),
  layout: n => ipcRenderer.invoke('widget:layout', n),
  addFriend: f => ipcRenderer.invoke('friend:add', f),
  resizeWidget: (t, w, h, done) => ipcRenderer.send('widget:size', t, w, h, done),
  setAutostart: v => ipcRenderer.invoke('autostart', v),
  win: a => ipcRenderer.send('win', a),
  hideWidget: t => ipcRenderer.send('widget:hide', t),
  pickPhoto: () => ipcRenderer.invoke('photo:pick'),
  clearPhoto: () => ipcRenderer.invoke('photo:clear'),
  on: (ch, f) => ipcRenderer.on(ch, (e, v) => f(v))
});
