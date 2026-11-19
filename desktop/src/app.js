const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
let DATA = null, STATUS = 'nocode', CFG = { widgets: {} }, PAGE = 'home', RANGE = 30, unsub = () => {};
const WL = [['steps', 'Pas du jour', 'var(--acc)'], ['kcal', 'Calories', 'var(--cal)'], ['weight', 'Poids', 'var(--wgt)'], ['week', 'Semaine', '#7C8CFF'], ['streak', 'Série', '#FF8A3D']];
const SLOTS = { breakfast: 'Petit-déj', lunch: 'Midi', dinner: 'Soir', snack: 'Collations' };
const ICO = {
  steps: '<path d="M4 16c0-3 1.5-6 3.5-6S10 12 10 14s-1 4-3 4-3-.5-3-2zM14 8c0-3 1.5-6 3.5-6S20 4 20 6s-1 4-3 4-3-.5-3-2z"/>',
  fire: '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>',
  scale: '<rect x="3" y="4" width="18" height="16" rx="4"/><path d="M8 9a5 5 0 0 1 8 0M12 9l1.5-2"/>',
  food: '<path d="M7 2v20M4 2v6a3 3 0 0 0 6 0V2M17 22V2c-2.5 1-4 3.5-4 7v5h4"/>',
  gym: '<path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"/>',
  trend: '<path d="M3 17l6-6 4 4 8-8M14 7h7v7"/>',
  cal: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M3 9h18M8 2v4M16 2v4"/>',
  trophy: '<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4"/>'
};
const ic = k => `<svg viewBox="0 0 24 24">${ICO[k]}</svg>`;
const ago = t => { const m = Math.round((Date.now() - t) / 60000); return m < 1 ? "à l'instant" : m < 60 ? `il y a ${m} min` : m < 1440 ? `il y a ${Math.round(m / 60)} h` : `il y a ${Math.round(m / 1440)} j`; };
const dayLbl = k => parseKey(k).toLocaleDateString('fr-BE', { weekday: 'short' }).replace('.', '');
const dShort = k => parseKey(k).toLocaleDateString('fr-BE', { day: 'numeric', month: 'short' }).replace('.', '');
const mavg = (a, n = 7) => a.map((_, i) => { const w = a.slice(Math.max(0, i - n + 1), i + 1).filter(x => x); return w.length ? w.reduce((x, y) => x + y, 0) / w.length : null; });
const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
const pct = (a, b) => b ? (a - b) / b * 100 : 0;
const trend = (a, b, inv) => { if (!b) return ''; const v = pct(a, b), good = inv ? v <= 0 : v >= 0; return `<span class="${good ? 'up' : 'dn'}">${v >= 0 ? '+' : '−'}${Math.abs(Math.round(v))} %</span>`; };

/* graphiques SVG */
function bars(vals, labels, o = {}) {
  const W = o.w || 640, H = o.h || 220, pad = 22, n = vals.length, mx = Math.max(o.goal || 0, ...vals, 1) * 1.08;
  const bw = Math.min(42, (W - 20) / n * .62), step = (W - 20) / n;
  let s = `<svg class="chart" viewBox="0 0 ${W} ${H + pad}"><defs><linearGradient id="bg${o.id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--acc)"/><stop offset="1" stop-color="var(--acc2)"/></linearGradient></defs>`;
  vals.forEach((v, i) => { const h = Math.max(bw, v / mx * H), x = 10 + i * step + (step - bw) / 2, y = H - h, hi = o.hi ? o.hi(i) : i === n - 1;
    s += `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${bw.toFixed(1)}" height="${h.toFixed(1)}" rx="${(bw / 2).toFixed(1)}" fill="${hi ? `url(#bg${o.id})` : v >= (o.goal || Infinity) ? 'color-mix(in srgb,var(--acc) 35%,#26262B)' : '#26262B'}" data-t="${o.tip ? o.tip(i) : fmt(v)}"><animate attributeName="height" from="0" to="${h.toFixed(1)}" dur=".6s" fill="freeze" calcMode="spline" keySplines=".2 .8 .2 1" keyTimes="0;1"/><animate attributeName="y" from="${H}" to="${y.toFixed(1)}" dur=".6s" fill="freeze" calcMode="spline" keySplines=".2 .8 .2 1" keyTimes="0;1"/></rect>`;
    if (labels[i]) s += `<text x="${(x + bw / 2).toFixed(1)}" y="${H + 16}" text-anchor="middle">${labels[i]}</text>`; });
  if (o.goal) { const y = H - o.goal / mx * H; s += `<line x1="6" x2="${W - 6}" y1="${y}" y2="${y}" stroke="var(--acc)" stroke-opacity=".45" stroke-dasharray="4 5"/><text x="${W - 8}" y="${y - 6}" text-anchor="end" style="fill:var(--acc);opacity:.7">objectif ${fmt(o.goal)}</text>`; }
  return s + '</svg>';
}
function smooth(pts) { if (pts.length < 2) return ''; let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) { const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2, t = .18;
    d += ` C${(p1[0] + (p2[0] - p0[0]) * t).toFixed(1)},${(p1[1] + (p2[1] - p0[1]) * t).toFixed(1)} ${(p2[0] - (p3[0] - p1[0]) * t).toFixed(1)},${(p2[1] - (p3[1] - p1[1]) * t).toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`; }
  return d; }
function lines(series, labels, o = {}) {
  const W = o.w || 640, H = o.h || 200, pad = 22, n = labels.length;
  let s = `<svg class="chart" viewBox="0 0 ${W} ${H + pad}"><defs>`;
  series.forEach((se, j) => { s += `<linearGradient id="lg${o.id}${j}" x1="0" x2="1"><stop offset="0" stop-color="${se.c[0]}"/><stop offset="1" stop-color="${se.c[1]}"/></linearGradient>
    <linearGradient id="la${o.id}${j}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${se.c[1]}" stop-opacity=".22"/><stop offset="1" stop-color="${se.c[1]}" stop-opacity="0"/></linearGradient>
    <filter id="gw${o.id}${j}" x="-10%" y="-30%" width="120%" height="160%"><feGaussianBlur stdDeviation="4"/></filter>`; });
  s += '</defs>';
  for (let g = 0; g <= 4; g++) { const y = 6 + g * (H - 12) / 4; s += `<line class="gl" x1="0" x2="${W}" y1="${y}" y2="${y}"/>`; }
  series.forEach((se, j) => { const v = se.vals, ok = v.filter(x => x != null), mn = o.zero ? 0 : Math.min(...ok), mx = Math.max(...ok), rg = (mx - mn) || 1;
    const pts = v.map((x, i) => x == null ? null : [i / Math.max(1, n - 1) * (W - 8) + 4, 8 + (1 - (x - mn) / rg) * (H - 20)]).filter(Boolean);
    if (pts.length < 2) return; const d = smooth(pts);
    if (o.area !== false) s += `<path d="${d} L${pts.at(-1)[0]},${H} L${pts[0][0]},${H} Z" fill="url(#la${o.id}${j})"/>`;
    s += `<path d="${d}" stroke="url(#lg${o.id}${j})" stroke-width="4" opacity=".55" filter="url(#gw${o.id}${j})"/><path d="${d}" stroke="url(#lg${o.id}${j})" stroke-width="2.4" pathLength="1" stroke-dasharray="1" stroke-dashoffset="1"><animate attributeName="stroke-dashoffset" from="1" to="0" dur="1s" fill="freeze" calcMode="spline" keySplines=".2 .8 .2 1" keyTimes="0;1"/></path>`;
    if (se.target != null) { const y = 8 + (1 - (se.target - mn) / rg) * (H - 20); if (y > 0 && y < H) s += `<line x1="0" x2="${W}" y1="${y}" y2="${y}" stroke="${se.c[1]}" stroke-opacity=".5" stroke-dasharray="4 5"/>`; }
    v.forEach((x, i) => { if (x == null) return; const p = [i / Math.max(1, n - 1) * (W - 8) + 4, 8 + (1 - (x - mn) / rg) * (H - 20)];
      s += `<circle cx="${p[0]}" cy="${p[1]}" r="9" fill="transparent" data-t="${o.tip ? o.tip(j, i) : x}"/>`; }); });
  const every = Math.ceil(n / 7); labels.forEach((l, i) => { if (i % every === 0 || i === n - 1) s += `<text x="${(i / Math.max(1, n - 1) * (W - 8) + 4).toFixed(1)}" y="${H + 16}" text-anchor="middle">${l}</text>`; });
  return s + '</svg>';
}
const ring = (v, c, sz = 120, sw = 10) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r;
  return `<svg viewBox="0 0 ${sz} ${sz}"><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.07)" stroke-width="${sw}"/><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="${c}" stroke-width="${sw}" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - Math.min(1, v))}" style="transition:stroke-dashoffset .9s cubic-bezier(.2,.8,.2,1)"/></svg>`; };

/* pages */
const lastN = n => Array.from({ length: n }, (_, i) => addDays(today(), i - n + 1));
function pHome(d) {
  const G = d.goals, T = today(), t = D(d, T), y = D(d, addDays(T, -1)), lw = lastWeight(d), name = d.name || '';
  const hr = new Date().getHours(), hello = hr < 6 ? 'Bonne nuit' : hr < 18 ? 'Bonjour' : 'Bonsoir';
  const cG = G.kcal * .45 / 4, fG = G.kcal * .3 / 9, wkKs = lastN(7), rng = lastN(RANGE);
  const mac = (l, v, g, c) => `<div><b class="num">${fmt(v)} g</b><small>${l}</small><i><s style="width:${Math.min(100, v / (g || 1) * 100)}%;background:${c}"></s></i></div>`;
  const wd = lw && G.start ? lw - G.start : null, wleft = lw && G.target ? Math.abs(lw - G.target) : null, wp = G.start && G.target && lw ? Math.max(0, Math.min(1, (G.start - lw) / (G.start - G.target))) : 0;
  const wos = (d.wos || []).slice(0, 4);
  return `<h1>${hello}${name ? ', ' + esc(name) : ''} !</h1>
  <div class="grid g4">
    <div class="card glow"><div class="lab">${ic('steps')}Pas du jour</div><div class="big num">${fmt(t.steps)}<small>/ ${fmt(G.steps)}</small></div>
      <div class="sub">${trend(t.steps, y.steps)} vs hier</div><div class="pbar"><i style="width:${Math.min(100, t.steps / G.steps * 100)}%"></i></div></div>
    <div class="card glow" style="--g:var(--cal)"><div class="lab">${ic('food')}Calories</div><div class="big num">${fmt(t.kcal)}<small>/ ${fmt(G.kcal)} kcal</small></div>
      <div class="macs">${mac('Protéines', t.p, G.protein || 100, 'var(--pro)')}${mac('Glucides', t.c, cG, 'var(--carb)')}${mac('Lipides', t.f, fG, 'var(--fat)')}</div></div>
    <div class="card glow" style="--g:var(--wgt)"><div class="lab">${ic('scale')}Poids</div><div class="big num">${lw ? f1(lw) : '—'}<small>kg</small></div>
      <div class="sub">${wd != null ? `<span class="${wd <= 0 ? 'up' : 'dn'}">${wd <= 0 ? '−' : '+'}${f1(Math.abs(wd))} kg</span> depuis le départ` : 'Aucune pesée'}${wleft != null ? ` · ${f1(wleft)} kg restants` : ''}</div>
      <div class="pbar"><i style="width:${wp * 100}%;background:linear-gradient(90deg,var(--wgt),var(--acc))"></i></div></div>
    <div class="card glow" style="--g:#FF8A3D"><div class="lab">${ic('fire')}Série</div><div class="big num" style="display:flex;align-items:center;gap:8px"><svg class="flame" viewBox="0 0 24 24">${ICO.fire}</svg>${d.streak || 0}<small>jour${d.streak > 1 ? 's' : ''}</small></div>
      <div class="sub">${fmt(d.total)} pas depuis le début</div></div>
  </div>
  <div class="grid g21" style="margin-top:12px">
    <div class="card" style="--g:var(--acc)"><div class="hdr"><div><div class="lab">${ic('cal')}Pas · 7 derniers jours</div><div class="big num">${fmt(avg(wkKs.map(k => D(d, k).steps)))}<small>pas / jour</small></div></div></div>
      <div style="margin-top:10px">${bars(wkKs.map(k => D(d, k).steps), wkKs.map(k => dayLbl(k).toUpperCase()), { id: 'w', goal: G.steps, h: 200, tip: i => `${dayLbl(wkKs[i])} · <b>${fmt(D(d, wkKs[i]).steps)}</b> pas` })}</div></div>
    <div class="card" style="--g:var(--cal)"><div class="lab">${ic('food')}Repas du jour</div>${mealsList(d)}</div>
  </div>
  <div class="grid g21" style="margin-top:12px">
    <div class="card"><div class="hdr"><div><div class="lab">${ic('trend')}Tendance <span style="color:var(--dim)">· moyenne sur 7 jours</span></div><div class="legend"><span><i style="background:var(--acc)"></i>Pas</span><span><i style="background:var(--cal)"></i>Calories</span><span><i style="background:var(--wgt)"></i>Poids</span></div></div>
      <div class="seg" id="rng">${[30, 90, 365].map(n => `<button data-n="${n}" class="${RANGE === n ? 'on' : ''}">${n === 365 ? '1 an' : n + ' j'}</button>`).join('')}</div></div>
      <div style="margin-top:12px">${lines([
        { vals: mavg(rng.map(k => D(d, k).steps)), c: ['var(--acc2)', 'var(--acc)'] },
        { vals: mavg(rng.map(k => D(d, k).kcal)), c: ['#FFC44A', '#FF5C8A'] },
        { vals: rng.map(k => D(d, k).weight), c: ['#4FE3D0', 'var(--wgt)'] }], rng.map(dShort),
        { id: 't', h: 210, area: false, tip: (j, i) => `${dShort(rng[i])} · <b>${j === 0 ? fmt(D(d, rng[i]).steps) + ' pas' : j === 1 ? fmt(D(d, rng[i]).kcal) + ' kcal' : f1(D(d, rng[i]).weight) + ' kg'}</b>` })}</div></div>
    <div class="card" style="--g:#7896FF"><div class="lab">${ic('gym')}Dernières séances</div>${wos.length ? `<div class="list">${wos.map(woLi).join('')}</div>` : '<div class="empty">Aucune séance</div>'}</div>
  </div>`;
}
const woLi = w => `<div class="li"><span class="ic">${ic('gym')}</span><div><b>${esc(w.n)}</b><small>${new Date(w.t).toLocaleDateString('fr-BE', { weekday: 'short', day: 'numeric', month: 'short' })} · ${w.s} séries</small></div><div class="r"><b class="num">${w.m} min</b><small>${w.v >= 1000 ? f1(w.v / 1000) + ' t' : fmt(w.v) + ' kg'}</small></div></div>`;
function mealsList(d) {
  const ms = d.meals || []; if (!ms.length) return '<div class="empty">Aucun repas noté aujourd\'hui</div>';
  return Object.entries(SLOTS).map(([s, l]) => { const a = ms.filter(m => m.s === s); if (!a.length) return '';
    return `<div class="slot">${l} · ${fmt(a.reduce((t, m) => t + m.k, 0))} kcal</div><div class="list">${a.map(m => `<div class="li" style="padding:7px 0"><div><b>${esc(m.n)}</b><small>P ${m.p} · G ${m.c} · L ${m.f}</small></div><div class="r"><b class="num">${fmt(m.k)}</b><small>kcal</small></div></div>`).join('')}</div>`; }).join('');
}
function pSteps(d) {
  const G = d.goals, ks = lastN(30), v = ks.map(k => D(d, k).steps), all = Object.keys(d.days || {}).sort();
  const wk = {}, mo = {}; all.forEach(k => { const x = parseKey(k); x.setDate(x.getDate() - (x.getDay() + 6) % 7); const w = key(x); wk[w] = (wk[w] || 0) + D(d, k).steps; mo[k.slice(0, 7)] = (mo[k.slice(0, 7)] || 0) + D(d, k).steps; });
  const bestD = all.reduce((b, k) => D(d, k).steps > (b ? D(d, b).steps : 0) ? k : b, null), bw = Math.max(0, ...Object.values(wk)), bm = Math.max(0, ...Object.values(mo));
  const hk = lastN(26 * 7), hmx = Math.max(G.steps * 1.5, 1);
  return `<h1>Pas</h1><div class="grid g4">
    <div class="card glow"><div class="lab">${ic('steps')}Aujourd'hui</div><div class="big num">${fmt(v.at(-1))}</div><div class="pbar"><i style="width:${Math.min(100, v.at(-1) / G.steps * 100)}%"></i></div></div>
    <div class="card"><div class="lab">${ic('cal')}Moyenne 7 jours</div><div class="big num">${fmt(avg(v.slice(-7)))}</div><div class="sub">${trend(avg(v.slice(-7)), avg(v.slice(-14, -7)))} vs semaine passée</div></div>
    <div class="card"><div class="lab">${ic('cal')}Moyenne 30 jours</div><div class="big num">${fmt(avg(v))}</div><div class="sub">Objectif atteint ${v.filter(x => x >= G.steps).length} j / 30</div></div>
    <div class="card"><div class="lab">${ic('trophy')}Depuis le début</div><div class="big num">${fmt(d.total)}</div><div class="sub">${(d.total / 1312).toFixed(0)} km environ</div></div></div>
  <div class="card glow" style="margin-top:12px"><div class="lab">${ic('cal')}30 derniers jours</div>${bars(v, ks.map((k, i) => i % 3 === 2 || i === 29 ? dShort(k) : ''), { id: 's', goal: G.steps, h: 220, tip: i => `${dShort(ks[i])} · <b>${fmt(v[i])}</b> pas` })}</div>
  <div class="grid g21" style="margin-top:12px">
    <div class="card"><div class="lab">${ic('cal')}26 dernières semaines</div><div class="heat">${hk.map(k => { const s = D(d, k).steps, a = Math.min(1, s / hmx);
      return `<i data-t="${dShort(k)} · <b>${fmt(s)}</b> pas" style="${s ? `background:color-mix(in srgb,var(--acc) ${Math.round(15 + a * 85)}%,#1A1A1D)` : ''}"></i>`; }).join('')}</div></div>
    <div class="card"><div class="lab">${ic('trophy')}Records</div><div class="list">
      <div class="li"><div><b>Meilleur jour</b><small>${bestD ? dShort(bestD) : '—'}</small></div><div class="r"><b class="num">${bestD ? fmt(D(d, bestD).steps) : '—'}</b></div></div>
      <div class="li"><div><b>Meilleure semaine</b></div><div class="r"><b class="num">${fmt(bw)}</b></div></div>
      <div class="li"><div><b>Meilleur mois</b></div><div class="r"><b class="num">${fmt(bm)}</b></div></div></div></div></div>`;
}
function pFood(d) {
  const G = d.goals, t = D(d, today()), ks = lastN(14), v = ks.map(k => D(d, k).kcal);
  return `<h1>Nutrition</h1><div class="grid g21">
    <div class="card glow" style="--g:var(--cal)"><div class="lab">${ic('food')}Aujourd'hui</div><div style="display:flex;align-items:center;gap:26px">
      <div class="ring">${ring(t.kcal / G.kcal, 'var(--cal)')}<b class="num">${Math.round(t.kcal / G.kcal * 100)} %</b></div>
      <div style="flex:1"><div class="big num">${fmt(t.kcal)}<small>/ ${fmt(G.kcal)} kcal</small></div><div class="sub">Reste ${fmt(Math.max(0, G.kcal - t.kcal))} kcal</div>
      <div class="macs">${[['Protéines', t.p, G.protein || 100, 'var(--pro)'], ['Glucides', t.c, G.kcal * .45 / 4, 'var(--carb)'], ['Lipides', t.f, G.kcal * .3 / 9, 'var(--fat)']].map(([l, x, g, c]) => `<div><b class="num">${fmt(x)}${l === 'Protéines' && G.protein ? ' / ' + G.protein : ''} g</b><small>${l}</small><i><s style="width:${Math.min(100, x / g * 100)}%;background:${c}"></s></i></div>`).join('')}</div></div></div></div>
    <div class="card" style="--g:var(--cal)"><div class="lab">${ic('food')}Repas du jour</div>${mealsList(d)}</div></div>
  <div class="card" style="margin-top:12px;--g:var(--cal)"><div class="lab">${ic('cal')}Calories · 14 jours</div>${bars(v, ks.map(dayLbl), { id: 'k', goal: G.kcal, h: 220, tip: i => `${dShort(ks[i])} · <b>${fmt(v[i])}</b> kcal` })}</div>`;
}
function pWeight(d) {
  const G = d.goals, ks = lastN(90), v = ks.map(k => D(d, k).weight), lw = lastWeight(d), all = Object.keys(d.days || {}).filter(k => d.days[k][2]).sort().reverse().slice(0, 12);
  return `<h1>Poids</h1><div class="grid g4">
    <div class="card glow" style="--g:var(--wgt)"><div class="lab">${ic('scale')}Actuel</div><div class="big num">${lw ? f1(lw) : '—'}<small>kg</small></div></div>
    <div class="card"><div class="lab">${ic('scale')}Départ</div><div class="big num">${G.start ? f1(G.start) : '—'}<small>kg</small></div></div>
    <div class="card"><div class="lab">${ic('trophy')}Objectif</div><div class="big num">${G.target ? f1(G.target) : '—'}<small>kg</small></div></div>
    <div class="card"><div class="lab">${ic('trend')}Restant</div><div class="big num">${lw && G.target ? f1(Math.abs(lw - G.target)) : '—'}<small>kg</small></div></div></div>
  <div class="grid g21" style="margin-top:12px"><div class="card" style="--g:var(--wgt)"><div class="lab">${ic('trend')}90 derniers jours</div>${v.some(x => x) ? lines([{ vals: v, c: ['#4FE3D0', 'var(--wgt)'], target: G.target || null }], ks.map(dShort), { id: 'p', h: 240, tip: (j, i) => `${dShort(ks[i])} · <b>${f1(v[i])} kg</b>` }) : '<div class="empty">Aucune pesée</div>'}</div>
    <div class="card"><div class="lab">${ic('cal')}Dernières pesées</div><div class="list">${all.map((k, i) => { const w = d.days[k][2], p = all[i + 1] ? d.days[all[i + 1]][2] : null, df = p ? w - p : null;
      return `<div class="li" style="padding:8px 0"><div><b>${dShort(k)}</b></div><div class="r"><b class="num">${f1(w)} kg</b>${df != null ? `<small class="${df <= 0 ? 'up' : 'dn'}">${df > 0 ? '+' : df < 0 ? '−' : '±'}${f1(Math.abs(df))}</small>` : ''}</div></div>`; }).join('') || '<div class="empty">Aucune pesée</div>'}</div></div></div>`;
}
function pGym(d) {
  const wos = d.wos || [], now = new Date(), mon = new Date(now); mon.setHours(0, 0, 0, 0); mon.setDate(mon.getDate() - (mon.getDay() + 6) % 7);
  const m0 = new Date(now.getFullYear(), now.getMonth(), 1).getTime(), wkN = wos.filter(w => w.t >= mon.getTime()).length, mo = wos.filter(w => w.t >= m0);
  const weeks = Array.from({ length: 12 }, (_, i) => { const a = new Date(mon); a.setDate(a.getDate() - (11 - i) * 7); return a; });
  const cnt = weeks.map(a => wos.filter(w => w.t >= a.getTime() && w.t < a.getTime() + 7 * 864e5).length);
  return `<h1>Muscu</h1><div class="grid g4">
    <div class="card glow" style="--g:#7C8CFF"><div class="lab">${ic('gym')}Cette semaine</div><div class="big num">${wkN}<small>séance${wkN > 1 ? 's' : ''}</small></div></div>
    <div class="card"><div class="lab">${ic('cal')}Ce mois-ci</div><div class="big num">${mo.length}<small>séances</small></div></div>
    <div class="card"><div class="lab">${ic('cal')}Temps ce mois</div><div class="big num">${Math.floor(mo.reduce((t, w) => t + w.m, 0) / 60)}<small>h</small> ${mo.reduce((t, w) => t + w.m, 0) % 60}<small>min</small></div></div>
    <div class="card"><div class="lab">${ic('trophy')}Volume ce mois</div><div class="big num">${f1(mo.reduce((t, w) => t + w.v, 0) / 1000)}<small>t</small></div></div></div>
  <div class="grid g21" style="margin-top:12px"><div class="card" style="--g:#7896FF"><div class="lab">${ic('cal')}Séances par semaine</div>${bars(cnt, weeks.map(a => dShort(key(a))), { id: 'g', h: 220, tip: i => `Semaine du ${dShort(key(weeks[i]))} · <b>${cnt[i]}</b>` })}</div>
    <div class="card" style="--g:#7896FF"><div class="lab">${ic('gym')}Historique</div>${wos.length ? `<div class="list">${wos.slice(0, 12).map(woLi).join('')}</div>` : '<div class="empty">Aucune séance</div>'}</div></div>`;
}
const PAGES = { home: ['Aperçu', pHome], steps: ['Pas', pSteps], food: ['Nutrition', pFood], weight: ['Poids', pWeight], gym: ['Muscu', pGym] };
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function render() {
  const [t, f] = PAGES[PAGE]; $('#crumb b').textContent = t; $('#crumb svg').innerHTML = $(`nav [data-p="${PAGE}"] svg`).innerHTML;
  $$('nav button').forEach(b => b.classList.toggle('on', b.dataset.p === PAGE));
  if (!DATA) { $('#page').innerHTML = `<h1>${t}</h1><div class="card"><div class="empty">${STATUS === 'noserver' ? 'Serveur de synchro pas encore configuré.' : 'En attente des données du téléphone…'}</div></div>`; return; }
  const sc = $('#page').scrollTop; $('#page').innerHTML = f(DATA); $('#page').scrollTop = sc;
  $$('#rng button').forEach(b => b.onclick = () => { RANGE = +b.dataset.n; render(); });
}
function side() {
  const d = DATA, wkS = d ? Math.round(avg(lastN(7).map(k => D(d, k).steps))) : 0;
  $('#nSteps').textContent = d ? fmt(D(d, today()).steps) : ''; $('#nFood').textContent = d ? fmt(D(d, today()).kcal) : '';
  $('#nGym').textContent = d ? (d.wos || []).filter(w => Date.now() - w.t < 7 * 864e5).length || '' : '';
  $('#meName').textContent = (d && d.name) || 'Focus'; $('#av').textContent = ((d && d.name) || 'F')[0].toUpperCase();
  const ms = $('#meSync'); ms.className = STATUS === 'live' ? 'live' : '';
  ms.lastChild.textContent = STATUS === 'live' && d ? `Synchronisé · ${ago(d.at)}` : STATUS === 'nocode' ? 'Pas connecté' : STATUS === 'noserver' ? 'Serveur non configuré' : d ? `Hors ligne · ${ago(d.at)}` : 'Hors ligne';
  if (d) { const a = ACC[d.accent] || ACC.lime; document.documentElement.style.setProperty('--acc', a[0]); document.documentElement.style.setProperty('--acc2', a[1]); }
  $('#wlist').innerHTML = WL.map(([k, l, c]) => `<div class="wrow"><i style="background:${c}"></i>${l}<button class="sw ${CFG.widgets[k] && CFG.widgets[k].on ? 'on' : ''}" data-w="${k}"></button></div>`).join('');
  $$('#wlist .sw').forEach(b => b.onclick = () => fapi.setWidget(b.dataset.w, !b.classList.contains('on')));
}
function connect() {
  unsub(); if (!CFG.code) { STATUS = 'nocode'; DATA = null; side(); render(); dlgCode(); return; }
  unsub = subscribe(CFG.code, (d, st) => { if (d) DATA = d; STATUS = st; side(); render(); });
}
function dlgCode(err) {
  $('#ov').classList.remove('hidden');
  $('#ov').innerHTML = `<div class="dlg"><h2>Connecte ton téléphone</h2><p>Dans l'app Focus : Réglages › <b>Synchro PC</b> › active l'envoi, puis tape ici le code affiché.</p>
    <input class="in" id="codeIn" maxlength="14" placeholder="XXXX XXXX XXXX" value="${esc(CFG.code || '')}"><div class="err">${err || ''}</div>
    <button class="btn" id="codeOk">Connecter</button>${CFG.code ? '<button class="btn ghost" id="codeX">Annuler</button>' : ''}</div>`;
  const ci = $('#codeIn'); setTimeout(() => ci.focus(), 50); $('#ov').onmousedown = e => { if (e.target === ci) return; if (!e.target.closest('button')) { e.preventDefault(); ci.focus(); } };
  const go = async () => { const c = $('#codeIn').value.replace(/[^A-Z0-9]/gi, '').toUpperCase(); if (c.length < 8) return dlgCode('Code trop court');
    CFG = await fapi.setCode(c); $('#ov').classList.add('hidden'); connect(); };
  $('#codeOk').onclick = go; $('#codeIn').onkeydown = e => e.key === 'Enter' && go();
  if ($('#codeX')) $('#codeX').onclick = () => $('#ov').classList.add('hidden');
}
function dlgSet() {
  $('#ov').classList.remove('hidden');
  $('#ov').innerHTML = `<div class="dlg"><h2>Réglages</h2>
    <div class="srow"><div>Code de synchro<small>${CFG.code ? CFG.code.replace(/(.{4})(?=.)/g, '$1 ') : 'Aucun'}</small></div><button class="seg" id="chg"><span style="padding:4px 8px;font-size:11px;font-weight:600">Changer</span></button></div>
    <div class="srow"><div>Widgets au premier plan<small>Toujours visibles au-dessus des fenêtres</small></div><button class="sw ${CFG.onTop ? 'on' : ''}" id="sTop"></button></div>
    <div class="srow" style="border:0"><div>Lancer au démarrage de Windows<small>Les widgets s'affichent dès l'allumage</small></div><button class="sw ${CFG.autostart ? 'on' : ''}" id="sAuto"></button></div>
    <button class="btn" id="setX">Fermer</button></div>`;
  $('#chg').onclick = () => dlgCode(); $('#setX').onclick = () => $('#ov').classList.add('hidden');
  $('#sTop').onclick = e => { e.target.classList.toggle('on'); fapi.setOnTop(e.target.classList.contains('on')); };
  $('#sAuto').onclick = e => { e.target.classList.toggle('on'); fapi.setAutostart(e.target.classList.contains('on')); };
}

/* interactions */
$$('.lights button').forEach(b => b.onclick = () => fapi.win(b.dataset.w));
$$('nav button').forEach(b => b.onclick = () => { PAGE = b.dataset.p; render(); $('#page').scrollTop = 0; });
$('#setBtn').onclick = dlgSet;
$('#q').oninput = () => { const q = $('#q').value.trim().toLowerCase(); $$('nav button').forEach(b => b.classList.toggle('dimq', !!q && !b.textContent.toLowerCase().includes(q))); };
$('#q').onkeydown = e => { if (e.key === 'Enter') { const b = $$('nav button').find(x => !x.classList.contains('dimq')); if (b) b.click(); $('#q').value = ''; $('#q').oninput(); $('#q').blur(); } };
addEventListener('keydown', e => { if (e.ctrlKey && e.key.toLowerCase() === 'k') { e.preventDefault(); $('#q').focus(); } if (e.key === 'Escape') $('#ov').classList.add('hidden'); });
const tip = document.createElement('div'); tip.className = 'tip'; document.body.appendChild(tip);
addEventListener('mousemove', e => { const t = e.target.closest && e.target.closest('[data-t]'); if (!t) { tip.classList.remove('on'); return; }
  tip.innerHTML = t.dataset.t; tip.classList.add('on'); tip.style.left = Math.min(innerWidth - tip.offsetWidth - 10, e.clientX + 14) + 'px'; tip.style.top = (e.clientY - 38) + 'px'; });
fapi.on('max', m => document.body.classList.toggle('max', m));
fapi.on('cfg', c => { const code = CFG.code; CFG = c; side(); if (c.code !== code) connect(); });
setInterval(side, 30000);
fapi.cfg().then(c => { CFG = c; side(); connect(); });
