const { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, screen } = require('electron');
const path = require('path');
const fs = require('fs');

const CFG = path.join(app.getPath('userData'), 'config.json');
const WIDGETS = {
  steps: { w: 200, h: 214, label: 'Pas du jour' },
  kcal: { w: 320, h: 150, label: 'Calories' },
  weight: { w: 200, h: 214, label: 'Poids' },
  week: { w: 340, h: 190, label: 'Semaine' },
  streak: { w: 220, h: 110, label: 'Série' }
};
const load = () => { try { return JSON.parse(fs.readFileSync(CFG, 'utf8')); } catch (e) { return {}; } };
let cfg = Object.assign({ code: '', widgets: {}, onTop: false, autostart: true }, load());
const saveCfg = () => { try { fs.writeFileSync(CFG, JSON.stringify(cfg, null, 2)); } catch (e) {} };

let main = null, tray = null; const wins = {};
const ICON = path.join(__dirname, 'src', 'icon.png');

if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => showMain());

function createMain() {
  main = new BrowserWindow({
    width: 1240, height: 800, minWidth: 980, minHeight: 640, titleBarStyle: 'hidden', titleBarOverlay: { color: '#0B0B0F', symbolColor: '#C9C9D0', height: 46 }, backgroundColor: '#0B0B0F',
    show: false, icon: ICON, title: 'Focus',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  main.loadFile(path.join(__dirname, 'src', 'index.html'));
  main.once('ready-to-show', () => { if (!process.argv.includes('--hidden')) { main.show(); main.focus(); main.webContents.focus(); } });
  main.on('close', e => { if (!app.isQuitting) { e.preventDefault(); main.hide(); } });
  main.on('maximize', () => main.webContents.send('max', true));
  main.on('unmaximize', () => main.webContents.send('max', false));
}
const showMain = () => { if (!main) return; main.show(); main.focus(); };

function openWidget(type) {
  if (wins[type] || !WIDGETS[type]) return;
  const d = WIDGETS[type], st = cfg.widgets[type] || {}, wa = screen.getPrimaryDisplay().workArea;
  const n = Object.keys(wins).length;
  const w = new BrowserWindow({
    width: d.w, height: d.h, x: st.x ?? wa.x + wa.width - d.w - 24, y: st.y ?? wa.y + 24 + n * (d.h + 16),
    frame: false, transparent: true, resizable: false, skipTaskbar: true, alwaysOnTop: !!cfg.onTop, hasShadow: false,
    focusable: true, icon: ICON, backgroundColor: '#00000000',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  w.loadFile(path.join(__dirname, 'src', 'widget.html'), { query: { type } });
  w.on('moved', () => { const [x, y] = w.getPosition(); cfg.widgets[type] = { ...(cfg.widgets[type] || {}), on: true, x, y }; saveCfg(); });
  w.on('closed', () => { delete wins[type]; });
  wins[type] = w;
}
function setWidget(type, on) {
  cfg.widgets[type] = { ...(cfg.widgets[type] || {}), on };
  saveCfg();
  if (on) openWidget(type); else if (wins[type]) wins[type].close();
  refreshTray(); broadcast('cfg', cfg);
}
const broadcast = (ch, v) => [main, ...Object.values(wins)].forEach(w => w && !w.isDestroyed() && w.webContents.send(ch, v));

function refreshTray() {
  if (!tray) return;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Ouvrir Focus', click: showMain },
    { type: 'separator' },
    ...Object.entries(WIDGETS).map(([k, d]) => ({ label: d.label, type: 'checkbox', checked: !!(cfg.widgets[k] && cfg.widgets[k].on), click: m => setWidget(k, m.checked) })),
    { type: 'separator' },
    { label: 'Widgets au premier plan', type: 'checkbox', checked: !!cfg.onTop, click: m => setOnTop(m.checked) },
    { label: 'Quitter', click: () => { app.isQuitting = true; app.quit(); } }
  ]));
}
function setOnTop(v) { cfg.onTop = v; saveCfg(); Object.values(wins).forEach(w => w.setAlwaysOnTop(v)); refreshTray(); broadcast('cfg', cfg); }
function setAutostart(v) { cfg.autostart = v; saveCfg(); app.setLoginItemSettings({ openAtLogin: v, args: ['--hidden'] }); broadcast('cfg', cfg); }

app.whenReady().then(() => {
  app.setAppUserModelId('com.alexandre.focus.desktop');
  createMain();
  tray = new Tray(nativeImage.createFromPath(ICON).resize({ width: 32, height: 32, quality: 'best' }));
  tray.setToolTip('Focus'); tray.on('click', showMain); refreshTray();
  Object.entries(cfg.widgets).forEach(([k, v]) => v && v.on && openWidget(k));
  app.setLoginItemSettings({ openAtLogin: !!cfg.autostart, args: ['--hidden'] });
});
app.on('window-all-closed', e => e.preventDefault());

ipcMain.handle('cfg:get', () => cfg);
ipcMain.handle('cfg:code', (e, code) => { cfg.code = String(code || '').replace(/[^A-Z0-9]/gi, '').toUpperCase(); saveCfg(); broadcast('cfg', cfg); return cfg; });
ipcMain.handle('widget:set', (e, type, on) => setWidget(type, on));
ipcMain.handle('widget:ontop', (e, v) => setOnTop(v));
ipcMain.handle('autostart', (e, v) => setAutostart(v));
ipcMain.on('win', (e, a) => {
  const w = BrowserWindow.fromWebContents(e.sender); if (!w) return;
  if (a === 'min') w.minimize(); else if (a === 'max') w.isMaximized() ? w.unmaximize() : w.maximize();
  else if (a === 'close') w.close();
  else if (a === 'open') showMain();
});
ipcMain.on('widget:hide', (e, type) => setWidget(type, false));
