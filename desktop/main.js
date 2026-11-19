const { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, screen, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

const CFG = path.join(app.getPath('userData'), 'config.json');
const WIDGETS = {
  steps: { w: 214, h: 214, label: 'Pas du jour' },
  kcal: { w: 214, h: 214, label: 'Calories' },
  weight: { w: 214, h: 214, label: 'Poids' },
  week: { w: 214, h: 214, label: 'Semaine' },
  streak: { w: 214, h: 214, label: 'Série' },
  all: { w: 1600, h: 250, label: 'Tableau de bord' }
};
const load = () => { try { return JSON.parse(fs.readFileSync(CFG, 'utf8')); } catch (e) { return {}; } };
let cfg = Object.assign({ code: '', widgets: {}, onTop: false, autostart: true, locked: false }, load());
const saveCfg = () => { try { fs.writeFileSync(CFG, JSON.stringify(cfg, null, 2)); } catch (e) {} };

let main = null, tray = null; const wins = {};
const ICON = path.join(__dirname, 'src', process.platform === 'win32' ? 'icon.ico' : 'icon.png');

if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => showMain());

function createMain() {
  main = new BrowserWindow({
    width: 1240, height: 800, minWidth: 900, minHeight: 620, frame: false, backgroundColor: '#0B0B0F',
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
    width: st.w || d.w, height: st.h || d.h, x: st.x ?? (type === 'all' ? wa.x + Math.round((wa.width - d.w) / 2) : wa.x + wa.width - d.w - 24), y: st.y ?? (type === 'all' ? wa.y + 24 : wa.y + 24 + n * (d.h + 16)),
    frame: false, transparent: true, resizable: type === 'all', thickFrame: false, minWidth: type === 'all' ? 560 : undefined, minHeight: type === 'all' ? 170 : undefined, movable: !cfg.locked, skipTaskbar: true, alwaysOnTop: !!cfg.onTop, hasShadow: false,
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
    { label: 'Verrouiller les widgets', type: 'checkbox', checked: !!cfg.locked, click: m => setLocked(m.checked) },
    { label: 'Disposition', submenu: Object.entries(LAYOUTS).map(([k, l]) => ({ label: l, click: () => applyLayout(k) })) },
    { label: 'Quitter', click: () => { app.isQuitting = true; app.quit(); } }
  ]));
}
const LAYOUTS = { right: 'Colonne à droite', left: 'Colonne à gauche', top: 'Ligne en haut', bottom: 'Ligne en bas', grid: 'Grille à droite' };
function applyLayout(name) {
  let on = Object.keys(WIDGETS).filter(k => k !== 'all' && cfg.widgets[k] && cfg.widgets[k].on); if (!on.length) on = Object.keys(WIDGETS).filter(k => k !== 'all');
  const wa = screen.getPrimaryDisplay().workArea, m = 24, g = 14, pos = {};
  if (name === 'right' || name === 'left') { let y = wa.y + m; on.forEach(k => { const d = WIDGETS[k]; pos[k] = [name === 'right' ? wa.x + wa.width - m - d.w : wa.x + m, y]; y += d.h + g; }); }
  else if (name === 'top' || name === 'bottom') { const tw = on.reduce((t, k) => t + WIDGETS[k].w, 0) + g * (on.length - 1), mh = Math.max(...on.map(k => WIDGETS[k].h)); let x = wa.x + Math.round((wa.width - tw) / 2);
    on.forEach(k => { const d = WIDGETS[k]; pos[k] = [x, name === 'top' ? wa.y + m : wa.y + wa.height - m - mh + (mh - d.h)]; x += d.w + g; }); }
  else { const cw = Math.max(...on.map(k => WIDGETS[k].w)), cols = [[], []]; on.forEach((k, i) => cols[i % 2].push(k));
    cols.forEach((c, ci) => { let y = wa.y + m; c.forEach(k => { const d = WIDGETS[k]; pos[k] = [wa.x + wa.width - m - (2 - ci) * cw - (1 - ci) * g + (cw - d.w), y]; y += d.h + g; }); }); }
  on.forEach(k => { cfg.widgets[k] = { ...(cfg.widgets[k] || {}), on: true, x: pos[k][0], y: pos[k][1] }; if (wins[k]) wins[k].setPosition(pos[k][0], pos[k][1]); else openWidget(k); });
  saveCfg(); refreshTray(); broadcast('cfg', cfg);
}
function setLocked(v) { cfg.locked = v; saveCfg(); Object.values(wins).forEach(w => w.setMovable(!v)); refreshTray(); broadcast('cfg', cfg); }
function setOnTop(v) { cfg.onTop = v; saveCfg(); Object.values(wins).forEach(w => w.setAlwaysOnTop(v)); refreshTray(); broadcast('cfg', cfg); }
function setAutostart(v) { cfg.autostart = v; saveCfg(); app.setLoginItemSettings({ openAtLogin: v, args: ['--hidden'] }); broadcast('cfg', cfg); }

app.whenReady().then(() => {
  { const wa = screen.getPrimaryDisplay().workArea; WIDGETS.all.w = Math.min(1760, wa.width - 140); WIDGETS.all.h = Math.min(260, Math.round(wa.height * .26)); }
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
ipcMain.handle('widget:lock', (e, v) => setLocked(v));
ipcMain.handle('widget:style', (e, v) => { cfg.wstyle = v; saveCfg(); broadcast('cfg', cfg); });
ipcMain.handle('widget:layout', (e, n) => applyLayout(n));
ipcMain.on('widget:size', (e, type, w, h, done) => { const win = wins[type]; if (!win) return; w = Math.max(560, Math.min(2600, Math.round(w))); h = Math.max(170, Math.min(560, Math.round(h)));
  win.setBounds({ width: w, height: h }); if (done) { cfg.widgets[type] = { ...(cfg.widgets[type] || {}), w, h }; saveCfg(); } });
ipcMain.handle('friend:add', (e, f) => { cfg.friends = [f, ...(cfg.friends || []).filter(x => x.code !== f.code)].slice(0, 8); saveCfg(); return cfg; });
ipcMain.handle('autostart', (e, v) => setAutostart(v));
ipcMain.on('win', (e, a) => {
  const w = BrowserWindow.fromWebContents(e.sender); if (!w) return;
  if (a === 'min') w.minimize(); else if (a === 'max') w.isMaximized() ? w.unmaximize() : w.maximize();
  else if (a === 'close') w.close();
  else if (a === 'open') showMain();
});
ipcMain.on('widget:hide', (e, type) => setWidget(type, false));
ipcMain.handle('photo:pick', async () => {
  const r = await dialog.showOpenDialog(main, { title: 'Photo de profil', properties: ['openFile'], filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp', 'bmp', 'gif'] }] });
  if (r.canceled || !r.filePaths[0]) return cfg;
  const im = nativeImage.createFromPath(r.filePaths[0]); if (im.isEmpty()) return cfg;
  const { width, height } = im.getSize(), s = Math.min(width, height);
  cfg.photo = im.crop({ x: Math.round((width - s) / 2), y: Math.round((height - s) / 2), width: s, height: s }).resize({ width: 160, height: 160, quality: 'best' }).toDataURL();
  saveCfg(); broadcast('cfg', cfg); return cfg;
});
ipcMain.handle('photo:clear', () => { delete cfg.photo; saveCfg(); broadcast('cfg', cfg); return cfg; });
