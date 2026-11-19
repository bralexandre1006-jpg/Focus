/* Lecture en direct des données envoyées par l'app (Firebase, flux temps réel) */
const FB_URL = 'https://focus-74945-default-rtdb.europe-west1.firebasedatabase.app';
const ACC = { lime: ['#D7FF3A', '#7BE07E'], bleu: ['#5AB8FF', '#7C8CFF'], violet: ['#B88CFF', '#FF7AD9'], orange: ['#FFB347', '#FF6B4A'], rose: ['#FF7AB8', '#FFB86B'], cyan: ['#4FE3D0', '#5AB8FF'], mono: ['#FFFFFF', '#BDBDC6'] };
const fmt = n => String(Math.round(n || 0)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
const f1 = n => (Math.round((n || 0) * 10) / 10).toLocaleString('fr-BE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const key = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const today = () => key(new Date());
const addDays = (k, n) => { const [y, m, d] = k.split('-').map(Number), x = new Date(y, m - 1, d); x.setDate(x.getDate() + n); return key(x); };
const parseKey = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
const D = (data, k) => { const a = data && data.days && data.days[k]; return a ? { steps: a[0], kcal: a[1], weight: a[2] || null, wo: a[3], p: a[4], c: a[5], f: a[6], off: a[7] } : { steps: 0, kcal: 0, weight: null, wo: -1, p: 0, c: 0, f: 0, off: 0 }; };
const lastWeight = (data, before) => { const ks = Object.keys((data && data.days) || {}).filter(k => (!before || k <= before) && data.days[k][2]).sort(); return ks.length ? data.days[ks.at(-1)][2] : null; };

async function fetchOnce(code) { const r = await fetch(`${FB_URL}/focus/${code}.json`, { cache: 'no-store' }); if (!r.ok) throw new Error(r.status); return r.json(); }
function subscribe(code, cb) {
  const cacheKey = 'focus.cache.' + code;
  try { const c = JSON.parse(localStorage.getItem(cacheKey)); if (c) cb(c, 'cache'); } catch (e) {}
  if (!code || FB_URL.startsWith('__')) { cb(null, FB_URL.startsWith('__') ? 'noserver' : 'nocode'); return () => {}; }
  let data = null, es = null, stop = false, retry = 0;
  const set = (obj, p, v) => { if (p === '/') return v; const ks = p.split('/').filter(Boolean); obj = obj || {}; let o = obj;
    ks.slice(0, -1).forEach(x => { o[x] = o[x] || {}; o = o[x]; }); if (v === null) delete o[ks.at(-1)]; else o[ks.at(-1)] = v; return obj; };
  const open = () => {
    if (stop) return;
    es = new EventSource(`${FB_URL}/focus/${code}.json`);
    const on = (ev, patch) => { try { const m = JSON.parse(ev.data); retry = 0;
      if (patch) Object.entries(m.data || {}).forEach(([k, v]) => { data = set(data, (m.path === '/' ? '' : m.path) + '/' + k, v); });
      else data = set(data, m.path, m.data);
      if (data) localStorage.setItem(cacheKey, JSON.stringify(data)); cb(data, 'live'); } catch (e) {} };
    es.addEventListener('put', e => on(e)); es.addEventListener('patch', e => on(e, true));
    es.onerror = () => { es.close(); cb(data, 'offline'); setTimeout(open, Math.min(30000, 2000 * ++retry)); };
  };
  open();
  /* relecture complète toutes les 3 minutes, au cas où le flux en direct décroche */
  const pull = async () => { try { const r = await fetch(`${FB_URL}/focus/${code}.json`, { cache: 'no-store' }); if (!r.ok) return false; const v = await r.json(); if (v) { data = v; localStorage.setItem(cacheKey, JSON.stringify(v)); cb(data, 'live'); } return true; } catch (e) { return false; } };
  window.focusRefresh = pull;
  const poll = setInterval(pull, 180000);
  return () => { stop = true; clearInterval(poll); es && es.close(); };
}
