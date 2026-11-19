const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
let DATA = null, STATUS = 'nocode', CFG = { widgets: {} }, PAGE = 'home', unsub = () => {};
const ST = { trend: 30, steps: { m: 'week', o: 0, sel: null }, food: { m: 'week', o: 0, sel: null }, wr: 90, cal: 0, csel: null, wsel: 0, ex: '' };
const WL = [['steps', 'Pas du jour', 'var(--acc)'], ['kcal', 'Calories', 'var(--cal)'], ['weight', 'Poids', 'var(--wgt)'], ['week', 'Semaine', '#7C8CFF'], ['streak', 'Série', '#FF8A3D']];
const SLOTS = { breakfast: 'Petit-déj', lunch: 'Midi', dinner: 'Soir', snack: 'Collations' };
const MOIS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ago = t => { const m = Math.round((Date.now() - t) / 60000); return m < 1 ? "à l'instant" : m < 60 ? `il y a ${m} min` : m < 1440 ? `il y a ${Math.round(m / 60)} h` : `il y a ${Math.round(m / 1440)} j`; };
const dayLbl = k => parseKey(k).toLocaleDateString('fr-BE', { weekday: 'short' }).replace('.', '');
const dShort = k => parseKey(k).toLocaleDateString('fr-BE', { day: 'numeric', month: 'short' }).replace('.', '');
const dLong = k => { const s = parseKey(k).toLocaleDateString('fr-BE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }); return s[0].toUpperCase() + s.slice(1); };
const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
const sum = a => a.reduce((x, y) => x + y, 0);
const mavg = (a, n = 7) => a.map((_, i) => { const w = a.slice(Math.max(0, i - n + 1), i + 1).filter(x => x); return w.length ? avg(w) : null; });
const trend = (a, b, inv) => { if (!b) return ''; const v = (a - b) / b * 100, good = inv ? v <= 0 : v >= 0; return `<span class="${good ? 'up' : 'dn'}">${v >= 0 ? '+' : '−'}${Math.abs(Math.round(v))} %</span>`; };
const lastN = n => Array.from({ length: n }, (_, i) => addDays(today(), i - n + 1));
const monday = k => { const d = parseKey(k); d.setDate(d.getDate() - (d.getDay() + 6) % 7); return key(d); };
const vol = w => sum((w.ex || []).map(e => sum((e[2] || []).map(s => s[0] * s[1]))));
const nsets = w => w.s || sum((w.ex || []).map(e => (e[2] || []).length));
const wVol = w => w.v || vol(w);
const PAL = ['#B88CFF', '#5AB8FF', '#FF7AB8', '#4FE3D0', '#FFC44A', '#FF8A4C', '#7BE07E', '#7C8CFF', '#FF5C8A'];
const catColor = c => { let h = 0; for (const ch of String(c)) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return PAL[h % PAL.length]; };
const ARR = d => `<svg viewBox="0 0 24 24"><path d="${d ? 'M9 6l6 6-6 6' : 'M15 6l-6 6 6 6'}"/></svg>`;

/* périodes : semaine, mois, année (barres par mois) */
function period(m, o) {
  const t = parseKey(today());
  if (m === 'week') { const a = parseKey(monday(today())); a.setDate(a.getDate() - o * 7); const ks = Array.from({ length: 7 }, (_, i) => { const x = new Date(a); x.setDate(a.getDate() + i); return key(x); });
    return { ks, buckets: ks.map(k => [k]), labels: ks.map(k => dayLbl(k).toUpperCase()), title: o === 0 ? 'Cette semaine' : o === 1 ? 'Semaine passée' : `${dShort(ks[0])} – ${dShort(ks[6])}` }; }
  if (m === 'month') { const a = new Date(t.getFullYear(), t.getMonth() - o, 1), n = new Date(a.getFullYear(), a.getMonth() + 1, 0).getDate();
    const ks = Array.from({ length: n }, (_, i) => key(new Date(a.getFullYear(), a.getMonth(), i + 1)));
    return { ks, buckets: ks.map(k => [k]), labels: ks.map((k, i) => (i + 1) % 5 === 1 || i === n - 1 ? String(i + 1) : ''), title: `${MOIS[a.getMonth()]} ${a.getFullYear()}`.replace(/^./, c => c.toUpperCase()) }; }
  const y = t.getFullYear() - o, buckets = Array.from({ length: 12 }, (_, mo) => { const n = new Date(y, mo + 1, 0).getDate(); return Array.from({ length: n }, (_, i) => key(new Date(y, mo, i + 1))); });
  return { ks: buckets.flat(), buckets, labels: MOIS.map(x => x.replace('.', '')), title: String(y), year: true };
}
const pnav = (id, s) => `<div class="pnav"><div class="seg" data-pm="${id}">${[['week', 'Semaine'], ['month', 'Mois'], ['year', 'Année']].map(([k, l]) => `<button data-m="${k}" class="${s.m === k ? 'on' : ''}">${l}</button>`).join('')}</div>
  <button class="arr" data-po="${id}" data-d="1">${ARR(0)}</button><b>${period(s.m, s.o).title}</b><button class="arr" data-po="${id}" data-d="-1" ${s.o ? '' : 'disabled'}>${ARR(1)}</button></div>`;

/* graphiques dessinés à la taille réelle du cadre */
const CH = {};
function bars(el, o) {
  const W = el.clientWidth, H = el.clientHeight, pb = 20, pt = 8, h = H - pb - pt, n = o.vals.length; if (W < 20 || h < 20) return;
  const tot = o.vals.map(v => Array.isArray(v) ? sum(v) : v), mx = Math.max(o.goal || 0, ...tot, 1) * 1.08, step = W / n, bw = Math.max(3, Math.min(38, step * .62));
  let s = `<svg viewBox="0 0 ${W} ${H}"><defs><linearGradient id="g${o.id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--acc)"/><stop offset="1" stop-color="var(--acc2)"/></linearGradient></defs>`;
  o.vals.forEach((v, i) => { const x = i * step + (step - bw) / 2, hi = o.sel === i || (o.sel == null && o.hi && o.hi(i)), r = Math.min(bw / 2, 10);
    if (Array.isArray(v)) { let y = pt + h; v.forEach((p, j) => { const bh = p / mx * h; if (bh <= 0) return; y -= bh; s += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" fill="${o.colors[j]}" opacity="${o.sel == null || hi ? 1 : .45}" rx="${j === v.length - 1 || v.slice(j + 1).every(z => !z) ? Math.min(r, bh) : 0}"/>`; }); }
    else { const bh = Math.max(v ? bw * .6 : 4, v / mx * h), y = pt + h - bh;
      s += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" rx="${Math.min(r, bh / 2)}" fill="${hi ? `url(#g${o.id})` : o.goal && v >= o.goal ? 'color-mix(in srgb,var(--acc) 40%,#26262B)' : '#26262B'}"/>`; }
    s += `<rect x="${i * step}" y="0" width="${step}" height="${H}" fill="transparent" data-i="${i}" data-t="${o.tip(i)}" style="cursor:${o.pick ? 'pointer' : 'default'}"/>`;
    if (o.labels[i]) s += `<text x="${x + bw / 2}" y="${H - 5}" text-anchor="middle">${o.labels[i]}</text>`; });
  if (o.goal) { const y = pt + h - o.goal / mx * h; s += `<line x1="0" x2="${W}" y1="${y}" y2="${y}" stroke="var(--acc)" stroke-opacity=".5" stroke-dasharray="4 5" pointer-events="none"/>`; }
  el.innerHTML = s + '</svg>';
  if (o.pick) el.onclick = e => { const i = e.target.dataset.i; if (i != null) o.pick(+i); };
}
function smooth(p) { if (p.length < 2) return ''; let d = `M${p[0][0]},${p[0][1]}`;
  for (let i = 0; i < p.length - 1; i++) { const a = p[i - 1] || p[i], b = p[i], c = p[i + 1], e = p[i + 2] || c, t = .18;
    d += ` C${b[0] + (c[0] - a[0]) * t},${b[1] + (c[1] - a[1]) * t} ${c[0] - (e[0] - b[0]) * t},${c[1] - (e[1] - b[1]) * t} ${c[0]},${c[1]}`; } return d; }
function lines(el, o) {
  const W = el.clientWidth, H = el.clientHeight, pb = 20, h = H - pb - 10, n = o.labels.length; if (W < 20 || h < 20) return;
  let s = `<svg viewBox="0 0 ${W} ${H}"><defs>`;
  o.series.forEach((se, j) => { s += `<linearGradient id="l${o.id}${j}" x1="0" x2="1"><stop offset="0" stop-color="${se.c[0]}"/><stop offset="1" stop-color="${se.c[1]}"/></linearGradient><linearGradient id="a${o.id}${j}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${se.c[1]}" stop-opacity=".25"/><stop offset="1" stop-color="${se.c[1]}" stop-opacity="0"/></linearGradient><filter id="f${o.id}${j}" x="-5%" y="-30%" width="110%" height="160%"><feGaussianBlur stdDeviation="3.5"/></filter>`; });
  s += '</defs>'; for (let g = 0; g <= 3; g++) { const y = 10 + g * h / 3; s += `<line class="gl" x1="0" x2="${W}" y1="${y}" y2="${y}"/>`; }
  const X = i => n < 2 ? W / 2 : 6 + i / (n - 1) * (W - 12);
  o.series.forEach((se, j) => { const ok = se.vals.filter(x => x != null); if (ok.length < 2) return;
    let mn = Math.min(...ok, se.target ?? Infinity), mx = Math.max(...ok, se.target ?? -Infinity); const pad = (mx - mn) * .12 || 1; mn -= pad; mx += pad;
    const Y = v => 10 + (1 - (v - mn) / (mx - mn)) * h, pts = se.vals.map((v, i) => v == null ? null : [X(i), Y(v)]).filter(Boolean), d = smooth(pts);
    if (o.area) s += `<path d="${d} L${pts.at(-1)[0]},${10 + h} L${pts[0][0]},${10 + h} Z" fill="url(#a${o.id}${j})"/>`;
    s += `<path d="${d}" stroke="url(#l${o.id}${j})" stroke-width="4" opacity=".5" filter="url(#f${o.id}${j})"/><path d="${d}" stroke="url(#l${o.id}${j})" stroke-width="2.4"/>`;
    if (se.target != null) { const y = Y(se.target); s += `<line x1="0" x2="${W}" y1="${y}" y2="${y}" stroke="${se.c[1]}" stroke-opacity=".55" stroke-dasharray="4 5"/><text x="${W - 4}" y="${y - 5}" text-anchor="end" style="fill:${se.c[1]}">objectif ${f1(se.target)}</text>`; }
    if (se.dots) pts.forEach(p => s += `<circle cx="${p[0]}" cy="${p[1]}" r="2.6" fill="${se.c[1]}"/>`); });
  const ev = Math.ceil(n / Math.max(2, Math.floor(W / 70)));
  o.labels.forEach((l, i) => { if (i % ev === 0 || (i === n - 1 && (n - 1) % ev > ev / 2)) s += `<text x="${X(i)}" y="${H - 5}" text-anchor="${i === n - 1 ? 'end' : i === 0 ? 'start' : 'middle'}">${l}</text>`;
    s += `<rect x="${X(i) - W / n / 2}" y="0" width="${W / n}" height="${H}" fill="transparent" data-t="${o.tip(i)}"/>`; });
  el.innerHTML = s + '</svg>';
}
const ring = (v, c, sz = 110, sw = 10) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r;
  return `<svg viewBox="0 0 ${sz} ${sz}"><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.07)" stroke-width="${sw}"/><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="${c}" stroke-width="${sw}" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - Math.min(1, Math.max(0, v)))}"/></svg>`; };
const donut = (parts, sz = 110, sw = 14) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r, t = sum(parts.map(p => p[0])) || 1; let off = 0;
  return `<svg viewBox="0 0 ${sz} ${sz}" style="width:${sz}px;height:${sz}px;transform:rotate(-90deg);flex-shrink:0"><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.06)" stroke-width="${sw}"/>${parts.map(([v, c]) => { const l = v / t * C, el = `<circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="${c}" stroke-width="${sw}" stroke-dasharray="${Math.max(0, l - 2)} ${C}" stroke-dashoffset="${-off}"/>`; off += l; return el; }).join('')}</svg>`; };

/* détail d'un jour (réutilisé partout) */
function dayDetail(d, k) {
  const x = D(d, k), G = d.goals, ms = (d.ml && d.ml[k]) || (k === today() ? (d.meals || []).map(m => [m.s, m.n, m.k, m.p, m.c, m.f]) : null);
  const wos = (d.wos || []).filter(w => key(new Date(w.t)) === k), w = x.weight;
  return `<div class="dt">${dLong(k)}</div>
  <div class="kv"><div><small>Pas</small><b class="num">${fmt(x.steps)}</b> ${x.steps >= G.steps ? '<span class="tag ok">objectif</span>' : ''}</div>
    <div><small>Calories</small><b class="num">${x.kcal ? fmt(x.kcal) : '—'}</b></div>
    <div><small>Poids</small><b class="num">${w ? f1(w) + ' kg' : '—'}</b></div>
    <div><small>Prot · Gluc · Lip</small><b class="num" style="font-size:14px">${x.p || 0} · ${x.c || 0} · ${x.f || 0} g</b></div></div>
  ${x.off ? '<div style="margin-top:10px"><span class="tag">Jour off</span></div>' : ''}
  ${wos.length ? `<div class="slot">Séance</div>${wos.map(w => `<div class="li" style="padding:6px 0"><div><b>${esc(w.n)}</b><small>${w.m} min · ${nsets(w)} séries · ${fmt(wVol(w))} kg</small></div></div>`).join('')}` : x.wo === 0 ? '<div class="slot">Séance</div><span class="tag no">Manquée</span>' : ''}
  ${ms && ms.length ? `<div class="slot">Repas</div>${Object.entries(SLOTS).map(([s, l]) => { const a = ms.filter(m => m[0] === s); return a.length ? a.map(m => `<div class="li" style="padding:5px 0"><div><b>${esc(m[1])}</b><small>${l} · P ${m[3]} · G ${m[4]} · L ${m[5]}</small></div><div class="r"><b class="num">${fmt(m[2])}</b><small>kcal</small></div></div>`).join('') : ''; }).join('')}` : ms === null && x.kcal ? '<div class="sub">Détail des repas disponible sur les 60 derniers jours.</div>' : ''}`;
}

/* pages */
function pHome(d) {
  const G = d.goals, T = today(), t = D(d, T), y = D(d, addDays(T, -1)), lw = lastWeight(d), wk = lastN(7), rng = lastN(ST.trend);
  const mac = (l, v, g, c) => `<div><b class="num">${fmt(v)} g</b><small>${l}</small><i><s style="width:${Math.min(100, v / (g || 1) * 100)}%;background:${c}"></s></i></div>`;
  const wd = lw && G.start ? lw - G.start : null, wp = G.start && G.target && lw ? Math.max(0, Math.min(1, (G.start - lw) / (G.start - G.target))) : 0;
  CH.hw = el => bars(el, { id: 'hw', vals: wk.map(k => D(d, k).steps), labels: wk.map(k => dayLbl(k).toUpperCase()), goal: G.steps, hi: i => i === 6, tip: i => `${dShort(wk[i])} · <b>${fmt(D(d, wk[i]).steps)}</b> pas`, pick: i => { ST.csel = wk[i]; ST.cal = 0; go('hist'); } });
  CH.ht = el => lines(el, { id: 'ht', labels: rng.map(dShort), series: [{ vals: mavg(rng.map(k => D(d, k).steps)), c: ['var(--acc2)', 'var(--acc)'] }, { vals: mavg(rng.map(k => D(d, k).kcal)), c: ['#FFC44A', '#FF5C8A'] }, { vals: rng.map(k => D(d, k).weight), c: ['#4FE3D0', 'var(--wgt)'] }],
    tip: i => `${dShort(rng[i])} · <b>${fmt(D(d, rng[i]).steps)}</b> pas · <b>${fmt(D(d, rng[i]).kcal)}</b> kcal${D(d, rng[i]).weight ? ` · <b>${f1(D(d, rng[i]).weight)}</b> kg` : ''}` });
  const wos = (d.wos || []).slice(0, 6);
  return `<div class="row" style="grid-template-columns:repeat(4,1fr)">
    <div class="card s glow"><div class="lab">Pas du jour</div><div class="big num">${fmt(t.steps)}<small>/ ${fmt(G.steps)}</small></div><div class="sub">${trend(t.steps, y.steps)} vs hier</div><div class="pbar"><i style="width:${Math.min(100, t.steps / G.steps * 100)}%"></i></div></div>
    <div class="card s" style="--g:var(--cal)"><div class="lab">Calories</div><div class="big num">${fmt(t.kcal)}<small>/ ${fmt(G.kcal)} kcal</small></div><div class="macs">${mac('Protéines', t.p, G.protein || 100, 'var(--pro)')}${mac('Glucides', t.c, G.kcal * .45 / 4, 'var(--carb)')}${mac('Lipides', t.f, G.kcal * .3 / 9, 'var(--fat)')}</div></div>
    <div class="card s" style="--g:var(--wgt)"><div class="lab">Poids</div><div class="big num">${lw ? f1(lw) : '—'}<small>kg</small></div><div class="sub">${wd != null ? `<span class="${wd <= 0 ? 'up' : 'dn'}">${wd <= 0 ? '−' : '+'}${f1(Math.abs(wd))} kg</span> depuis le départ` : 'Aucune pesée'}</div><div class="pbar"><i style="width:${wp * 100}%;background:linear-gradient(90deg,var(--wgt),var(--acc))"></i></div></div>
    <div class="card s" style="--g:#FF8A3D"><div class="lab">Série</div><div class="big num" style="display:flex;align-items:center;gap:8px"><svg class="flame" viewBox="0 0 24 24"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z"/></svg>${d.streak || 0}<small>jour${d.streak > 1 ? 's' : ''}</small></div><div class="sub">${fmt(d.total)} pas au total</div></div></div>
  <div class="row" style="grid-template-columns:2fr 1fr">
    <div class="card fx glow"><div class="hdr2"><div class="lab">Pas · 7 derniers jours</div><b class="num" style="font-size:20px">${fmt(avg(wk.map(k => D(d, k).steps)))} <small style="font-size:12px;color:var(--mut)">/ jour</small></b></div><div class="ch" data-ch="hw"></div></div>
    <div class="card fx" style="--g:var(--cal)"><div class="lab">Repas du jour</div><div class="scroll">${mealsList(d)}</div></div></div>
  <div class="row" style="grid-template-columns:2fr 1fr">
    <div class="card fx"><div class="hdr2"><div class="lab">Tendance <span style="color:var(--dim)">· moyenne 7 j</span></div><div class="legend"><span><i style="background:var(--acc)"></i>Pas</span><span><i style="background:var(--cal)"></i>Calories</span><span><i style="background:var(--wgt)"></i>Poids</span></div>
      <div class="seg" id="rng">${[30, 90, 365].map(n => `<button data-n="${n}" class="${ST.trend === n ? 'on' : ''}">${n === 365 ? '1 an' : n + ' j'}</button>`).join('')}</div></div><div class="ch" data-ch="ht"></div></div>
    <div class="card fx" style="--g:#7896FF"><div class="lab">Dernières séances</div><div class="scroll">${wos.length ? wos.map((w, i) => woRow(w, i, false)).join('') : '<div class="empty">Aucune séance</div>'}</div></div></div>`;
}
function mealsList(d) {
  const ms = d.meals || []; if (!ms.length) return '<div class="empty">Aucun repas noté aujourd\'hui</div>';
  return Object.entries(SLOTS).map(([s, l]) => { const a = ms.filter(m => m.s === s); if (!a.length) return '';
    return `<div class="slot" style="margin-top:4px">${l} · ${fmt(sum(a.map(m => m.k)))} kcal</div>${a.map(m => `<div class="li" style="padding:6px 0"><div><b>${esc(m.n)}</b><small>P ${m.p} · G ${m.c} · L ${m.f}</small></div><div class="r"><b class="num">${fmt(m.k)}</b><small>kcal</small></div></div>`).join('')}`; }).join('');
}
const woRow = (w, i, sel) => { const dt = new Date(w.t); return `<div class="wo ${sel ? 'sel' : ''}" data-wi="${i}"><div class="d"><b class="num">${dt.getDate()}</b><small>${MOIS[dt.getMonth()].replace('.', '')}</small></div><div class="m"><b>${esc(w.n)}</b><small>${w.m} min · ${nsets(w)} séries</small></div><b class="num" style="font-size:13px">${wVol(w) >= 1000 ? f1(wVol(w) / 1000) + ' t' : fmt(wVol(w)) + ' kg'}</b></div>`; };

function pSteps(d) {
  const G = d.goals, s = ST.steps, P = period(s.m, s.o), T = today(), v = P.buckets.map(b => sum(b.map(k => D(d, k).steps)));
  const days = P.ks.filter(k => k <= T), dv = days.map(k => D(d, k).steps), Pp = period(s.m, s.o + 1), prev = sum(Pp.ks.map(k => D(d, k).steps));
  const best = days.reduce((b, k) => D(d, k).steps > (b ? D(d, b).steps : -1) ? k : b, null), sel = s.sel && P.ks.includes(s.sel) ? s.sel : (P.ks.includes(T) ? T : best);
  CH.sb = el => bars(el, { id: 'sb', vals: v, labels: P.labels, goal: P.year ? null : G.steps, sel: P.year ? null : P.ks.indexOf(sel), tip: i => P.year ? `${P.labels[i]} · <b>${fmt(v[i])}</b> pas` : `${dShort(P.ks[i])} · <b>${fmt(v[i])}</b> pas`,
    pick: i => { if (P.year) { s.m = 'month'; s.o = (new Date().getFullYear() - +P.title) * 12 + new Date().getMonth() - i; } else s.sel = P.ks[i]; render(); } });
  const all = Object.keys(d.days || {}).sort(), wkT = {}, moT = {}; all.forEach(k => { const x = D(d, k).steps; wkT[monday(k)] = (wkT[monday(k)] || 0) + x; moT[k.slice(0, 7)] = (moT[k.slice(0, 7)] || 0) + x; });
  const bD = all.reduce((b, k) => D(d, k).steps > (b ? D(d, b).steps : -1) ? k : b, null), bW = Object.entries(wkT).sort((a, b) => b[1] - a[1])[0], bM = Object.entries(moT).sort((a, b) => b[1] - a[1])[0];
  const hk = lastN(53 * 7).slice(-(52 * 7 + (new Date().getDay() + 6) % 7 + 1)), hmx = G.steps * 1.6;
  return `<div class="row" style="grid-template-columns:repeat(5,1fr)">
    <div class="card s glow"><div class="lab">Total · ${P.title.toLowerCase()}</div><div class="big num">${fmt(sum(dv))}</div><div class="sub">${trend(sum(dv), prev)} vs période précédente</div></div>
    <div class="card s"><div class="lab">Moyenne / jour</div><div class="big num">${fmt(avg(dv))}</div><div class="sub">sur ${days.length} jour${days.length > 1 ? 's' : ''}</div></div>
    <div class="card s"><div class="lab">Objectif atteint</div><div class="big num">${dv.filter(x => x >= G.steps).length}<small>/ ${days.length} j</small></div><div class="pbar"><i style="width:${days.length ? dv.filter(x => x >= G.steps).length / days.length * 100 : 0}%"></i></div></div>
    <div class="card s"><div class="lab">Meilleur jour</div><div class="big num">${best ? fmt(D(d, best).steps) : '—'}</div><div class="sub">${best ? dShort(best) : ''}</div></div>
    <div class="card s"><div class="lab">Distance</div><div class="big num">${fmt(sum(dv) * .00075)}<small>km</small></div><div class="sub">≈ ${fmt(sum(dv) * .04)} kcal brûlées</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx glow"><div class="hdr2"><div class="lab">Pas</div>${pnav('steps', s)}</div><div class="ch" data-ch="sb"></div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${sel ? dayDetail(d, sel) : '<div class="empty">Choisis un jour</div>'}</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx"><div class="lab">Une année de pas</div><div class="heat" style="flex:1;min-height:0;grid-template-rows:repeat(7,minmax(0,1fr));grid-auto-columns:minmax(0,1fr)">${hk.map(k => { const x = D(d, k).steps, a = Math.min(1, x / hmx);
      return `<i data-t="${dShort(k)} · <b>${fmt(x)}</b> pas" style="aspect-ratio:auto;border-radius:3px;${x ? `background:color-mix(in srgb,var(--acc) ${Math.round(15 + a * 85)}%,#1A1A1D)` : ''}"></i>`; }).join('')}</div></div>
    <div class="card fx"><div class="lab">Records</div><div class="list">
      <div class="li"><div><b>Meilleur jour</b><small>${bD ? dLong(bD) : '—'}</small></div><div class="r"><b class="num">${bD ? fmt(D(d, bD).steps) : '—'}</b></div></div>
      <div class="li"><div><b>Meilleure semaine</b><small>${bW ? 'du ' + dShort(bW[0]) : ''}</small></div><div class="r"><b class="num">${bW ? fmt(bW[1]) : '—'}</b></div></div>
      <div class="li"><div><b>Meilleur mois</b><small>${bM ? MOIS[+bM[0].slice(5) - 1] + ' ' + bM[0].slice(0, 4) : ''}</small></div><div class="r"><b class="num">${bM ? fmt(bM[1]) : '—'}</b></div></div>
      <div class="li"><div><b>Depuis le début</b></div><div class="r"><b class="num">${fmt(d.total)}</b></div></div></div></div></div>`;
}
function pFood(d) {
  const G = d.goals, s = ST.food, P = period(s.m, s.o), T = today(), days = P.ks.filter(k => k <= T && D(d, k).kcal), n = P.year ? 1 : 0;
  const agg = b => { const z = b.filter(k => D(d, k).kcal); return z.length ? [avg(z.map(k => D(d, k).p)) * 4, avg(z.map(k => D(d, k).c)) * 4, avg(z.map(k => D(d, k).f)) * 9] : [0, 0, 0]; };
  const v = P.buckets.map(b => P.year ? agg(b) : (x => [x.p * 4, x.c * 4, x.f * 9])(D(d, b[0])));
  const sel = s.sel && P.ks.includes(s.sel) ? s.sel : (P.ks.includes(T) ? T : days.at(-1));
  CH.fb = el => bars(el, { id: 'fb', vals: v, colors: ['var(--pro)', 'var(--carb)', 'var(--fat)'], labels: P.labels, goal: G.kcal, sel: P.year ? null : P.ks.indexOf(sel),
    tip: i => `${P.year ? P.labels[i] + ' (moy.)' : dShort(P.ks[i])} · <b>${fmt(P.year ? sum(v[i]) : D(d, P.ks[i]).kcal)}</b> kcal`, pick: i => { if (P.year) { s.m = 'month'; s.o = (new Date().getFullYear() - +P.title) * 12 + new Date().getMonth() - i; } else s.sel = P.ks[i]; render(); } });
  const kc = days.map(k => D(d, k).kcal), pr = days.map(k => D(d, k).p), ok = kc.filter(x => x <= G.kcal * 1.05).length;
  const mp = avg(days.map(k => D(d, k).p)) * 4, mc = avg(days.map(k => D(d, k).c)) * 4, mf = avg(days.map(k => D(d, k).f)) * 9, mt = mp + mc + mf || 1;
  const rng = lastN(60); CH.fl = el => lines(el, { id: 'fl', area: true, labels: rng.map(dShort), series: [{ vals: mavg(rng.map(k => D(d, k).kcal)), c: ['#FFC44A', '#FF6B4A'], target: G.kcal }], tip: i => `${dShort(rng[i])} · <b>${fmt(D(d, rng[i]).kcal)}</b> kcal` });
  return `<div class="row" style="grid-template-columns:repeat(4,1fr)">
    <div class="card s" style="--g:var(--cal)"><div class="lab">Moyenne · ${P.title.toLowerCase()}</div><div class="big num">${fmt(avg(kc))}<small>kcal / jour</small></div><div class="sub">objectif ${fmt(G.kcal)}</div></div>
    <div class="card s" style="--g:var(--pro)"><div class="lab">Protéines</div><div class="big num">${fmt(avg(pr))}<small>g / jour</small></div><div class="sub">${G.protein ? `objectif atteint ${pr.filter(x => x >= G.protein).length} / ${days.length} j` : ''}</div></div>
    <div class="card s" style="--g:var(--ok)"><div class="lab">Dans l'objectif</div><div class="big num">${ok}<small>/ ${days.length} j notés</small></div><div class="pbar"><i style="width:${days.length ? ok / days.length * 100 : 0}%;background:var(--ok)"></i></div></div>
    <div class="card s" style="--g:var(--carb)"><div class="lab">Répartition moyenne</div><div style="display:flex;gap:12px;font-size:12px;margin-top:4px">${[['Prot.', mp, 'var(--pro)'], ['Gluc.', mc, 'var(--carb)'], ['Lip.', mf, 'var(--fat)']].map(([l, x, c]) => `<div><b class="num" style="font-size:20px;color:${c}">${Math.round(x / mt * 100)} %</b><div class="sub" style="margin-top:2px">${l}</div></div>`).join('')}</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx" style="--g:var(--cal)"><div class="hdr2"><div class="lab">Calories et macros</div><div style="display:flex;gap:14px;align-items:center"><div class="legend"><span><i style="background:var(--pro)"></i>Prot.</span><span><i style="background:var(--carb)"></i>Gluc.</span><span><i style="background:var(--fat)"></i>Lip.</span></div>${pnav('food', s)}</div></div><div class="ch" data-ch="fb"></div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${sel ? dayDetail(d, sel) : '<div class="empty">Choisis un jour</div>'}</div></div></div>
  <div class="row" style="grid-template-columns:1fr"><div class="card fx" style="--g:var(--cal)"><div class="lab">Calories · 60 jours <span style="color:var(--dim)">· moyenne 7 j</span></div><div class="ch" data-ch="fl"></div></div></div>`;
}
function pWeight(d) {
  const G = d.goals, all = Object.keys(d.days || {}).filter(k => d.days[k][2]).sort(), lw = lastWeight(d), ks = ST.wr ? lastN(ST.wr) : (all.length ? (() => { const a = []; let k = all[0]; while (k <= today()) { a.push(k); k = addDays(k, 1); } return a; })() : []);
  const v = ks.map(k => D(d, k).weight), ok = v.filter(x => x), w7 = avg(all.slice(-7).map(k => d.days[k][2])), w7p = avg(all.filter(k => k <= addDays(today(), -7)).slice(-7).map(k => d.days[k][2]));
  const rate = all.length > 1 ? (() => { const a = all.filter(k => k >= addDays(today(), -28)); if (a.length < 2) return 0; return (d.days[a.at(-1)][2] - d.days[a[0]][2]) / ((parseKey(a.at(-1)) - parseKey(a[0])) / 864e5) * 7; })() : 0;
  const eta = lw && G.target && rate && Math.sign(G.target - lw) === Math.sign(rate) ? (() => { const x = new Date(); x.setDate(x.getDate() + Math.round((G.target - lw) / rate * 7)); return x.toLocaleDateString('fr-BE', { day: 'numeric', month: 'long', year: 'numeric' }); })() : null;
  CH.wl = el => lines(el, { id: 'wl', area: true, labels: ks.map(dShort), series: [{ vals: v, c: ['#4FE3D0', 'var(--wgt)'], target: G.target || null, dots: true }], tip: i => `${dShort(ks[i])}${v[i] ? ` · <b>${f1(v[i])} kg</b>` : ''}` });
  return `<div class="row" style="grid-template-columns:repeat(5,1fr)">
    <div class="card s" style="--g:var(--wgt)"><div class="lab">Actuel</div><div class="big num">${lw ? f1(lw) : '—'}<small>kg</small></div><div class="sub">${lw && G.start ? `${lw <= G.start ? '−' : '+'}${f1(Math.abs(lw - G.start))} kg depuis ${f1(G.start)}` : ''}</div></div>
    <div class="card s"><div class="lab">Moyenne 7 pesées</div><div class="big num">${w7 ? f1(w7) : '—'}<small>kg</small></div><div class="sub">${w7p ? `${w7 <= w7p ? '<span class="up">−' : '<span class="dn">+'}${f1(Math.abs(w7 - w7p))} kg</span> vs semaine passée` : ''}</div></div>
    <div class="card s"><div class="lab">Rythme</div><div class="big num">${rate ? (rate > 0 ? '+' : '−') + f1(Math.abs(rate)) : '—'}<small>kg / sem.</small></div><div class="sub">sur 4 semaines</div></div>
    <div class="card s"><div class="lab">Objectif</div><div class="big num">${G.target ? f1(G.target) : '—'}<small>kg</small></div><div class="sub">${lw && G.target ? f1(Math.abs(lw - G.target)) + ' kg restants' : ''}</div></div>
    <div class="card s"><div class="lab">Date estimée</div><div class="big num" style="font-size:20px;margin-top:6px">${eta || '—'}</div><div class="sub">au rythme actuel</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx" style="--g:var(--wgt)"><div class="hdr2"><div class="lab">Courbe</div><div class="seg" id="wr">${[[30, '1 mois'], [90, '3 mois'], [180, '6 mois'], [365, '1 an'], [0, 'Tout']].map(([n, l]) => `<button data-n="${n}" class="${ST.wr === n ? 'on' : ''}">${l}</button>`).join('')}</div></div>
      ${ok.length > 1 ? '<div class="ch" data-ch="wl"></div>' : '<div class="empty">Pas assez de pesées sur cette période</div>'}</div>
    <div class="card fx"><div class="lab">Pesées</div><div class="scroll"><div class="list">${all.slice().reverse().slice(0, 60).map((k, i, a) => { const w = d.days[k][2], p = a[i + 1] ? d.days[a[i + 1]][2] : null, df = p ? w - p : null;
      return `<div class="li" style="padding:7px 0"><div><b>${dShort(k)}</b><small>${dayLbl(k)}</small></div><div class="r"><b class="num">${f1(w)} kg</b>${df != null ? `<small class="${df <= 0 ? 'up' : 'dn'}">${df > 0 ? '+' : df < 0 ? '−' : '±'}${f1(Math.abs(df))}</small>` : ''}</div></div>`; }).join('') || '<div class="empty">Aucune pesée</div>'}</div></div></div></div>`;
}
function pHist(d) {
  const t = parseKey(today()), a = new Date(t.getFullYear(), t.getMonth() - ST.cal, 1), n = new Date(a.getFullYear(), a.getMonth() + 1, 0).getDate(), lead = (a.getDay() + 6) % 7, G = d.goals;
  const ks = Array.from({ length: n }, (_, i) => key(new Date(a.getFullYear(), a.getMonth(), i + 1))), T = today();
  const sel = ST.csel && ks.includes(ST.csel) ? ST.csel : ks.includes(T) ? T : ks.at(-1);
  const wosM = (d.wos || []).filter(w => { const x = new Date(w.t); return x.getMonth() === a.getMonth() && x.getFullYear() === a.getFullYear(); });
  const done = ks.filter(k => k <= T), st = done.map(k => D(d, k).steps), kc = done.map(k => D(d, k).kcal).filter(Boolean), wts = done.map(k => D(d, k).weight).filter(Boolean);
  const cells = Array(lead).fill('<button class="out"></button>').join('') + ks.map(k => { const x = D(d, k), hasW = (d.wos || []).some(w => key(new Date(w.t)) === k);
    return `<button class="${k === sel ? 'sel' : ''} ${k === T ? 'today' : ''} ${k > T ? 'out' : ''}" data-k="${k}"><div class="fill" style="height:${Math.min(100, x.steps / G.steps * 100)}%"></div><b>${+k.slice(8)}</b>
      <span class="ic2">${hasW ? '<i style="background:#7896FF"></i>' : x.wo === 0 ? '<i style="background:var(--bad)"></i>' : ''}${x.weight ? '<i style="background:var(--wgt)"></i>' : ''}${x.kcal ? '<i style="background:var(--cal)"></i>' : ''}</span>
      <small class="num" style="position:relative">${x.steps ? fmt(x.steps) : ''}</small></button>`; }).join('');
  return `<div class="row" style="grid-template-columns:repeat(5,1fr)">
    <div class="card s glow"><div class="lab">Pas du mois</div><div class="big num">${fmt(sum(st))}</div><div class="sub">${fmt(avg(st))} / jour</div></div>
    <div class="card s" style="--g:#7896FF"><div class="lab">Séances</div><div class="big num">${wosM.length}</div><div class="sub">${fmt(sum(wosM.map(w => w.m)))} min au total</div></div>
    <div class="card s" style="--g:var(--cal)"><div class="lab">Calories moyennes</div><div class="big num">${kc.length ? fmt(avg(kc)) : '—'}<small>kcal</small></div><div class="sub">${kc.length} jours notés</div></div>
    <div class="card s" style="--g:var(--wgt)"><div class="lab">Poids</div><div class="big num">${wts.length ? f1(wts.at(-1)) : '—'}<small>kg</small></div><div class="sub">${wts.length > 1 ? `${wts.at(-1) <= wts[0] ? '<span class="up">−' : '<span class="dn">+'}${f1(Math.abs(wts.at(-1) - wts[0]))} kg</span> ce mois` : ''}</div></div>
    <div class="card s"><div class="lab">Objectif de pas</div><div class="big num">${st.filter(x => x >= G.steps).length}<small>/ ${done.length} j</small></div><div class="pbar"><i style="width:${done.length ? st.filter(x => x >= G.steps).length / done.length * 100 : 0}%"></i></div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx"><div class="hdr2"><div class="lab">Calendrier</div><div class="pnav"><div class="legend" style="margin-right:12px"><span><i style="background:#7896FF"></i>Séance</span><span><i style="background:var(--wgt)"></i>Pesée</span><span><i style="background:var(--cal)"></i>Repas</span></div>
      <button class="arr" id="calP">${ARR(0)}</button><b>${(MOIS[a.getMonth()] + ' ' + a.getFullYear()).replace(/^./, c => c.toUpperCase())}</b><button class="arr" id="calN" ${ST.cal ? '' : 'disabled'}>${ARR(1)}</button></div></div>
      <div class="cal">${['lun', 'mar', 'mer', 'jeu', 'ven', 'sam', 'dim'].map(x => `<div class="h">${x}</div>`).join('')}${cells}</div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${dayDetail(d, sel)}</div></div></div>`;
}
function pGym(d) {
  const wos = d.wos || [], now = new Date(), mon = parseKey(monday(today())).getTime(), m0 = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const wk = wos.filter(w => w.t >= mon), mo = wos.filter(w => w.t >= m0), sel = wos[Math.min(ST.wsel, wos.length - 1)];
  const weeks = Array.from({ length: 12 }, (_, i) => mon - (11 - i) * 7 * 864e5), wv = weeks.map(a => sum(wos.filter(w => w.t >= a && w.t < a + 7 * 864e5).map(wVol)));
  CH.gv = el => bars(el, { id: 'gv', vals: wv, labels: weeks.map((a, i) => i % 2 ? '' : dShort(key(new Date(a)))), hi: i => i === 11, tip: i => `Semaine du ${dShort(key(new Date(weeks[i])))} · <b>${fmt(wv[i])} kg</b> · ${wos.filter(w => w.t >= weeks[i] && w.t < weeks[i] + 7 * 864e5).length} séance(s)` });
  const cats = {}; wos.filter(w => w.t >= Date.now() - 30 * 864e5).forEach(w => (w.ex || []).forEach(e => { const c = e[1] || 'Autre'; cats[c] = (cats[c] || 0) + (e[2] || []).length; }));
  const cl = Object.entries(cats).sort((a, b) => b[1] - a[1]).slice(0, 7), cmx = Math.max(1, ...cl.map(c => c[1]));
  const exs = {}; wos.forEach(w => (w.ex || []).forEach(e => { if (!(e[2] || []).length) return; (exs[e[0]] = exs[e[0]] || []).push({ t: w.t, kg: Math.max(...e[2].map(s => s[0])), e1: Math.max(...e[2].map(s => s[0] * (1 + s[1] / 30))) }); }));
  const exNames = Object.keys(exs).sort((a, b) => exs[b].length - exs[a].length); if (!exs[ST.ex]) ST.ex = exNames[0] || '';
  const pts = (exs[ST.ex] || []).slice().sort((a, b) => a.t - b.t), rec = pts.length ? Math.max(...pts.map(p => p.kg)) : 0;
  CH.gp = el => lines(el, { id: 'gp', area: true, labels: pts.map(p => dShort(key(new Date(p.t)))), series: [{ vals: pts.map(p => p.kg), c: ['#7C8CFF', '#B88CFF'], dots: true }], tip: i => `${dShort(key(new Date(pts[i].t)))} · <b>${f1(pts[i].kg)} kg</b> · 1RM estimé ${f1(pts[i].e1)} kg` });
  return `<div class="row" style="grid-template-columns:repeat(4,1fr)">
    <div class="card s" style="--g:#7896FF"><div class="lab">Cette semaine</div><div class="big num">${wk.length}<small>séance${wk.length > 1 ? 's' : ''}</small></div><div class="sub">${fmt(sum(wk.map(wVol)))} kg soulevés</div></div>
    <div class="card s" style="--g:#7896FF"><div class="lab">Ce mois-ci</div><div class="big num">${mo.length}<small>séances</small></div><div class="sub">${Math.floor(sum(mo.map(w => w.m)) / 60)} h ${sum(mo.map(w => w.m)) % 60} min</div></div>
    <div class="card s" style="--g:#B88CFF"><div class="lab">Volume du mois</div><div class="big num">${f1(sum(mo.map(wVol)) / 1000)}<small>t</small></div><div class="sub">${fmt(sum(mo.map(nsets)))} séries</div></div>
    <div class="card s"><div class="lab">Durée moyenne</div><div class="big num">${fmt(avg(wos.slice(0, 20).map(w => w.m)))}<small>min</small></div><div class="sub">20 dernières séances</div></div></div>
  <div class="row" style="grid-template-columns:1fr 1.25fr 1fr">
    <div class="card fx" style="--g:#7896FF"><div class="lab">Séances</div><div class="scroll" id="woL">${wos.map((w, i) => woRow(w, i, w === sel)).join('') || '<div class="empty">Aucune séance</div>'}</div></div>
    <div class="card fx" style="--g:#7896FF">${sel ? `<div class="hdr2"><div class="dt" style="margin:0">${esc(sel.n)}</div><span class="tag">${new Date(sel.t).toLocaleDateString('fr-BE', { weekday: 'long', day: 'numeric', month: 'long' })}</span></div>
      <div class="kv" style="grid-template-columns:repeat(3,1fr);margin-bottom:10px"><div><small>Durée</small><b class="num">${sel.m} min</b></div><div><small>Séries</small><b class="num">${nsets(sel)}</b></div><div><small>Volume</small><b class="num">${fmt(wVol(sel))} kg</b></div></div>
      <div class="scroll"><div class="exl">${(sel.ex || []).map(e => `<div class="exr"><div class="t"><i style="background:${catColor(e[1] || e[0])}"></i>${esc(e[0])}<span>${esc(e[1] || '')}</span></div><div class="sets">${(e[2] || []).map(s => `<span>${s[0] ? f1(s[0]).replace(',0', '') + ' kg × ' : ''}${s[1]}</span>`).join('') || '<span>—</span>'}</div></div>`).join('') || '<div class="empty">Détail des exercices indisponible</div>'}</div></div>` : '<div class="empty">Aucune séance</div>'}</div>
    <div class="card fx"><div class="lab">Séries par muscle · 30 j</div><div class="hb">${cl.map(([c, x]) => `<div><span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(c)}</span><i><s style="width:${x / cmx * 100}%;background:${catColor(c)}"></s></i><b class="num">${x}</b></div>`).join('') || '<div class="empty">Pas de données</div>'}</div></div></div>
  <div class="row" style="grid-template-columns:1fr 1fr">
    <div class="card fx" style="--g:#B88CFF"><div class="lab">Volume par semaine</div><div class="ch" data-ch="gv"></div></div>
    <div class="card fx" style="--g:#B88CFF"><div class="hdr2"><div class="lab">Progression</div><div style="display:flex;gap:8px;align-items:center"><span class="tag">record ${rec ? f1(rec) + ' kg' : '—'}</span><select class="sel2" id="exSel">${exNames.map(n => `<option ${n === ST.ex ? 'selected' : ''}>${esc(n)}</option>`).join('')}</select></div></div>
      ${pts.length > 1 ? '<div class="ch" data-ch="gp"></div>' : '<div class="empty">Pas assez de séances pour cet exercice</div>'}</div></div>`;
}
const PAGES = { home: ['Aperçu', pHome, 'fit-home'], hist: ['Historique', pHist, 'fit-2'], steps: ['Pas', pSteps, 'fit-3'], food: ['Nutrition', pFood, 'fit-3'], weight: ['Poids', pWeight, 'fit-2'], gym: ['Séances', pGym, 'fit-3'] };

function go(p) { PAGE = p; render(); }
function render() {
  const [t, f, cls] = PAGES[PAGE]; $('#crumb b').textContent = t; $('#crumb svg').innerHTML = $(`nav [data-p="${PAGE}"] svg`).innerHTML;
  $('#crumb em') ? null : $('#crumb').insertAdjacentHTML('beforeend', '<em></em>'); $('#crumb em').textContent = '· ' + dLong(today()).replace(/ \d{4}$/, '');
  $$('nav button').forEach(b => b.classList.toggle('on', b.dataset.p === PAGE));
  const pg = $('#page');
  if (!DATA) { pg.className = 'page'; pg.innerHTML = `<div class="card"><div class="empty">${STATUS === 'noserver' ? 'Serveur de synchro pas encore configuré.' : STATUS === 'nocode' ? 'Connecte ton téléphone pour voir tes données.' : 'En attente des données du téléphone…'}</div></div>`; return; }
  const scroll = {}; $$('#page .scroll').forEach((s, i) => scroll[i] = s.scrollTop);
  pg.className = 'page fit ' + cls; for (const k in CH) delete CH[k]; pg.innerHTML = f(DATA);
  $$('#page .scroll').forEach((s, i) => s.scrollTop = scroll[i] || 0);
  drawCharts(); bind();
}
const drawCharts = () => $$('[data-ch]').forEach(el => CH[el.dataset.ch] && CH[el.dataset.ch](el));
function bind() {
  $$('#rng button').forEach(b => b.onclick = () => { ST.trend = +b.dataset.n; render(); });
  $$('#wr button').forEach(b => b.onclick = () => { ST.wr = +b.dataset.n; render(); });
  $$('[data-pm] button').forEach(b => b.onclick = () => { const s = ST[b.parentNode.dataset.pm]; s.m = b.dataset.m; s.o = 0; s.sel = null; render(); });
  $$('[data-po]').forEach(b => b.onclick = () => { const s = ST[b.dataset.po]; s.o = Math.max(0, s.o + +b.dataset.d); s.sel = null; render(); });
  $$('.cal button[data-k]').forEach(b => b.onclick = () => { ST.csel = b.dataset.k; render(); });
  if ($('#calP')) { $('#calP').onclick = () => { ST.cal++; ST.csel = null; render(); }; $('#calN').onclick = () => { ST.cal = Math.max(0, ST.cal - 1); ST.csel = null; render(); }; }
  $$('[data-wi]').forEach(b => b.onclick = () => { ST.wsel = +b.dataset.wi; if (PAGE !== 'gym') go('gym'); else render(); });
  if ($('#exSel')) $('#exSel').onchange = e => { ST.ex = e.target.value; render(); };
}
function side() {
  const d = DATA;
  $('#nSteps').textContent = d ? fmt(D(d, today()).steps) : ''; $('#nFood').textContent = d ? fmt(D(d, today()).kcal) : '';
  $('#nGym').textContent = d ? (d.wos || []).filter(w => w.t >= parseKey(monday(today())).getTime()).length || '' : '';
  $('#meName').textContent = (d && d.name) || 'Focus';
  const av = $('#av'); if (CFG.photo) { av.style.backgroundImage = `url(${CFG.photo})`; av.textContent = ''; } else { av.style.backgroundImage = ''; av.textContent = ((d && d.name) || 'F')[0].toUpperCase(); }
  const ms = $('#meSync'); ms.className = STATUS === 'live' ? 'live' : '';
  ms.lastChild.textContent = STATUS === 'live' ? 'Synchronisé' : STATUS === 'nocode' ? 'Pas connecté' : STATUS === 'noserver' ? 'Serveur non configuré' : 'Hors ligne';
  if (d) { const a = ACC[d.accent] || ACC.lime; document.documentElement.style.setProperty('--acc', a[0]); document.documentElement.style.setProperty('--acc2', a[1]); }
  const on = WL.filter(([k]) => CFG.widgets[k] && CFG.widgets[k].on).length; $('#wddN').textContent = on ? `${on} actif${on > 1 ? 's' : ''}` : 'aucun';
  $('#wddP').innerHTML = WL.map(([k, l, c]) => `<div class="wrow"><i style="background:${c}"></i>${l}<button class="sw ${CFG.widgets[k] && CFG.widgets[k].on ? 'on' : ''}" data-w="${k}"></button></div>`).join('') +
    `<hr><div class="wrow" style="color:var(--mut)">Au premier plan<button class="sw ${CFG.onTop ? 'on' : ''}" id="wTop"></button></div><div class="wrow" style="color:var(--mut)">Verrouiller la position<button class="sw ${CFG.locked ? 'on' : ''}" id="wLock"></button></div>
    <hr><div class="slot" style="margin:4px 8px 2px">Disposition</div><div class="lays">${[['right', 'Colonne à droite', '<rect x="15" y="3" width="6" height="5" rx="1"/><rect x="15" y="10" width="6" height="5" rx="1"/><rect x="15" y="17" width="6" height="4" rx="1"/>'], ['left', 'Colonne à gauche', '<rect x="3" y="3" width="6" height="5" rx="1"/><rect x="3" y="10" width="6" height="5" rx="1"/><rect x="3" y="17" width="6" height="4" rx="1"/>'], ['top', 'Ligne en haut', '<rect x="3" y="3" width="5" height="5" rx="1"/><rect x="9.5" y="3" width="5" height="5" rx="1"/><rect x="16" y="3" width="5" height="5" rx="1"/>'], ['bottom', 'Ligne en bas', '<rect x="3" y="16" width="5" height="5" rx="1"/><rect x="9.5" y="16" width="5" height="5" rx="1"/><rect x="16" y="16" width="5" height="5" rx="1"/>'], ['grid', 'Grille à droite', '<rect x="10" y="3" width="5" height="5" rx="1"/><rect x="16" y="3" width="5" height="5" rx="1"/><rect x="10" y="10" width="5" height="5" rx="1"/><rect x="16" y="10" width="5" height="5" rx="1"/>']].map(([k, l, i]) => `<button data-lay="${k}" data-t="${l}"><svg viewBox="0 0 24 24"><rect x="1.5" y="1.5" width="21" height="21" rx="3" opacity=".35"/>${i}</svg></button>`).join('')}</div>`;
  $$('#wddP [data-w]').forEach(b => b.onclick = e => { e.stopPropagation(); fapi.setWidget(b.dataset.w, !b.classList.contains('on')); });
  $('#wTop').onclick = e => { e.stopPropagation(); fapi.setOnTop(!CFG.onTop); };
  $('#wLock').onclick = e => { e.stopPropagation(); fapi.setLocked(!CFG.locked); };
  $$('#wddP [data-lay]').forEach(b => b.onclick = e => { e.stopPropagation(); fapi.layout(b.dataset.lay); });
}
function connect() {
  unsub(); if (!CFG.code) { STATUS = 'nocode'; DATA = null; side(); render(); dlgCode(); return; }
  unsub = subscribe(CFG.code, (d, st) => { if (d) DATA = d; STATUS = st; side(); render(); });
}
function dlgCode(err) {
  $('#ov').classList.remove('hidden');
  $('#ov').innerHTML = `<div class="dlg"><h2>Connecte ton téléphone</h2><p>Dans l'app Focus : Réglages › <b>Synchro PC</b> › active l'envoi, puis tape ici le code affiché. Chaque téléphone a son propre code.</p>
    <input class="in" id="codeIn" maxlength="16" placeholder="XXXX XXXX XXXX" value="${esc(CFG.code || '')}" spellcheck="false"><div class="err">${err || ''}</div>
    <button class="btn ghost" id="codePaste">Coller le code copié</button><button class="btn" id="codeOk">Connecter</button>${CFG.code ? '<button class="btn ghost" id="codeX">Annuler</button>' : ''}</div>`;
  const ci = $('#codeIn'); setTimeout(() => ci.focus(), 50);
  const ok = async () => { const c = ci.value.replace(/[^A-Z0-9]/gi, '').toUpperCase(); if (c.length < 8) return dlgCode('Code trop court');
    CFG = await fapi.setCode(c); $('#ov').classList.add('hidden'); connect(); };
  $('#codeOk').onclick = ok;
  ci.addEventListener('keydown', e => { if (e.key === 'Enter') ok(); });
  $('#codePaste').onclick = async () => { try { ci.value = (await navigator.clipboard.readText()).trim().toUpperCase(); } catch (e) { dlgCode('Presse-papiers inaccessible'); } };
  if ($('#codeX')) $('#codeX').onclick = () => $('#ov').classList.add('hidden');
}
function dlgSet() {
  $('#ov').classList.remove('hidden');
  $('#ov').innerHTML = `<div class="dlg"><h2>Réglages</h2>
    <div class="srow"><div>Photo de profil<small>Visible seulement sur ce PC</small></div><div style="display:flex;gap:6px"><button class="seg" id="ph"><span style="padding:4px 8px;font-size:11px;font-weight:800">Choisir</span></button>${CFG.photo ? '<button class="seg" id="phX"><span style="padding:4px 8px;font-size:11px;font-weight:800">Retirer</span></button>' : ''}</div></div>
    <div class="srow"><div>Code de synchro<small>${CFG.code ? CFG.code.replace(/(.{4})(?=.)/g, '$1 ') : 'Aucun'}</small></div><button class="seg" id="chg"><span style="padding:4px 8px;font-size:11px;font-weight:800">Changer</span></button></div>
    <div class="srow"><div>Widgets au premier plan<small>Toujours visibles au-dessus des fenêtres</small></div><button class="sw ${CFG.onTop ? 'on' : ''}" id="sTop"></button></div>
    <div class="srow" style="border:0"><div>Lancer au démarrage de Windows<small>Les widgets s'affichent dès l'allumage</small></div><button class="sw ${CFG.autostart ? 'on' : ''}" id="sAuto"></button></div>
    <button class="btn" id="setX">Fermer</button></div>`;
  $('#ph').onclick = async () => { CFG = await fapi.pickPhoto(); side(); dlgSet(); };
  if ($('#phX')) $('#phX').onclick = async () => { CFG = await fapi.clearPhoto(); side(); dlgSet(); };
  $('#chg').onclick = () => dlgCode(); $('#setX').onclick = () => $('#ov').classList.add('hidden');
  $('#sTop').onclick = e => { e.currentTarget.classList.toggle('on'); fapi.setOnTop(e.currentTarget.classList.contains('on')); };
  $('#sAuto').onclick = e => { e.currentTarget.classList.toggle('on'); fapi.setAutostart(e.currentTarget.classList.contains('on')); };
}

/* interactions */
$$('nav button').forEach(b => b.onclick = () => go(b.dataset.p));
$('#setBtn').onclick = dlgSet;
$('#refBtn').onclick = async () => { const b = $('#refBtn'); if (b.classList.contains('spin')) return; b.classList.add('spin');
  const ok = window.focusRefresh ? await window.focusRefresh() : false; setTimeout(() => b.classList.remove('spin'), 600);
  b.dataset.t = ok ? 'Données à jour' : 'Impossible de joindre le serveur'; };
$('#av').onclick = async () => { CFG = await fapi.pickPhoto(); side(); };
$('#wddB').onclick = e => { e.stopPropagation(); $('#wdd').classList.toggle('open'); };
addEventListener('click', e => { if (!e.target.closest('#wdd')) $('#wdd').classList.remove('open'); });
$('#q').addEventListener('input', () => { const q = $('#q').value.trim().toLowerCase(); $$('nav button').forEach(b => b.classList.toggle('dimq', !!q && !b.textContent.toLowerCase().includes(q))); });
$('#q').addEventListener('keydown', e => { if (e.key === 'Enter') { const b = $$('nav button').find(x => !x.classList.contains('dimq')); if (b) b.click(); $('#q').value = ''; $('#q').dispatchEvent(new Event('input')); $('#q').blur(); } });
addEventListener('keydown', e => { if (e.ctrlKey && (e.key || '').toLowerCase() === 'k') { e.preventDefault(); $('#q').focus(); } if (e.key === 'Escape') $('#ov').classList.add('hidden'); });
let rzT; addEventListener('resize', () => { clearTimeout(rzT); rzT = setTimeout(drawCharts, 80); });
const tip = document.createElement('div'); tip.className = 'tip'; document.body.appendChild(tip);
addEventListener('mousemove', e => { const t = e.target.closest && e.target.closest('[data-t]'); if (!t) { tip.classList.remove('on'); return; }
  tip.innerHTML = t.dataset.t; tip.classList.add('on'); tip.style.left = Math.min(innerWidth - tip.offsetWidth - 10, e.clientX + 14) + 'px'; tip.style.top = Math.max(6, e.clientY - 40) + 'px'; });
fapi.on('cfg', c => { const code = CFG.code; CFG = c; side(); if (c.code !== code) connect(); });
setInterval(side, 30000);
fapi.cfg().then(c => { CFG = c; side(); connect(); });
