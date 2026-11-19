const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
let DATA = null, STATUS = 'nocode', CFG = { widgets: {} }, PAGE = 'home', unsub = () => {};
const ST = { showV: (() => { try { return localStorage.getItem('focus.showV') !== '0'; } catch (e) { return true; } })(), hw: 0, gvm: 'week', oldEx: false, trend: 30, steps: { m: 'week', o: 0, sel: null }, food: { m: 'week', o: 0, sel: null }, wr: 90, cal: 0, csel: null, wsel: 0, ex: '' };
const WL = [['steps', 'Pas du jour', 'var(--acc)'], ['kcal', 'Calories', 'var(--cal)'], ['weight', 'Poids', 'var(--wgt)'], ['week', 'Semaine', '#7C8CFF'], ['streak', 'Série', '#FF8A3D'], ['all', 'Tableau de bord', '#B88CFF']];
const SLOTS = { breakfast: 'Petit-déj', lunch: 'Midi', dinner: 'Soir', snack: 'Collations' }, SLOTC = { breakfast: '#FFC44A', lunch: '#FF8A4C', dinner: '#B88CFF', snack: '#FF7AB8' };
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
const nice = mx => { const raw = mx / 4, p = Math.pow(10, Math.floor(Math.log10(raw || 1))), f = raw / p, st = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * p; return { st, max: Math.ceil(mx / st) * st || st }; };
const kfmt = v => v >= 10000 ? Math.round(v / 1000) + 'k' : v >= 1000 ? (Math.round(v / 100) / 10).toString().replace('.', ',') + 'k' : String(Math.round(v * 10) / 10).replace('.', ',');
function bars(el, o) {
  const W = el.clientWidth, H = el.clientHeight, pb = 20, pt = 10, pl = o.axis === false ? 0 : 38, h = H - pb - pt, n = o.vals.length; if (W < 40 || h < 20) return;
  const tot = o.vals.map(v => Array.isArray(v) ? sum(v) : v), { st, max: mx } = nice(Math.max(o.goal || 0, ...tot, ...(o.line || []).filter(x => x != null), 1) * 1.05);
  const cw = W - pl, step = cw / n, bw = Math.max(3, Math.min(36, step * .62)), Y = v => pt + h - v / mx * h;
  let s = `<svg viewBox="0 0 ${W} ${H}"><defs><linearGradient id="g${o.id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--acc)"/><stop offset="1" stop-color="var(--acc2)"/></linearGradient></defs>`;
  if (pl) for (let v = 0; v <= mx + 1e-9; v += st) { const y = Y(v); s += `<line class="gl" x1="${pl}" x2="${W}" y1="${y}" y2="${y}"/><text x="${pl - 8}" y="${y + 3}" text-anchor="end">${kfmt(v)}</text>`; }
  o.vals.forEach((v, i) => { const x = pl + i * step + (step - bw) / 2, hi = o.sel === i || (o.sel == null && o.hi && o.hi(i)), r = Math.min(bw / 2, 9);
    if (Array.isArray(v)) { let y = pt + h; const top = v.map((z, j) => z > 0 ? j : -1).filter(j => j >= 0).pop();
      v.forEach((p, j) => { const bh = p / mx * h; if (bh <= 0) return; y -= bh; s += `<path d="${j === top ? `M${x},${y + bh}V${y + Math.min(r, bh)}Q${x},${y} ${x + Math.min(r, bw / 2)},${y}H${x + bw - Math.min(r, bw / 2)}Q${x + bw},${y} ${x + bw},${y + Math.min(r, bh)}V${y + bh}Z` : `M${x},${y}h${bw}v${bh}h${-bw}Z`}" style="fill:${o.colors[j]}" opacity="${o.sel == null || hi ? 1 : .4}"/>`; }); }
    else { const bh = Math.max(v ? 4 : 2, v / mx * h), y = pt + h - bh;
      s += `<rect x="${x}" y="${y}" width="${bw}" height="${bh}" rx="${Math.min(r, bh / 2)}" fill="${hi ? `url(#g${o.id})` : o.goal && v >= o.goal ? 'color-mix(in srgb,var(--acc) 42%,#26262B)' : o.dim ? '#26262B' : 'color-mix(in srgb,var(--acc) 22%,#26262B)'}"/>`;
      if (v && (o.allv || (o.vlab && hi))) s += `<text x="${x + bw / 2}" y="${y - 6}" text-anchor="middle" style="fill:${hi ? 'var(--txt)' : 'var(--mut)'};font-size:${o.allv && n > 14 ? 8.5 : 10}px">${kfmt(v)}</text>`; }
    s += `<rect x="${pl + i * step}" y="0" width="${step}" height="${H}" fill="transparent" data-i="${i}" data-t="${o.tip(i)}" style="cursor:${o.pick ? 'pointer' : 'default'}"/>`;
    if (o.labels[i]) s += `<text x="${x + bw / 2}" y="${H - 5}" text-anchor="middle">${o.labels[i]}</text>`; });
  if (o.line) { const pts = o.line.map((v, i) => v == null ? null : [pl + i * step + step / 2, Y(v)]).filter(Boolean); if (pts.length > 1) s += `<path d="${smooth(pts)}" stroke="${o.lineC || 'var(--txt)'}" stroke-width="2.2" fill="none" opacity=".9" pointer-events="none"/>`; }
  if (o.goal) { const y = Y(o.goal); s += `<line x1="${pl}" x2="${W}" y1="${y}" y2="${y}" stroke="${o.goalC || 'var(--acc)'}" stroke-opacity=".6" stroke-dasharray="4 5" pointer-events="none"/>`; }
  if (o.avgLine) { const y = Y(o.avgLine); s += `<line x1="${pl}" x2="${W}" y1="${y}" y2="${y}" stroke="var(--txt)" stroke-opacity=".35" stroke-dasharray="2 4" pointer-events="none"/><text x="${pl + 4}" y="${y - 5}" text-anchor="start" style="fill:var(--mut)">moy. ${kfmt(o.avgLine)}</text>`; }
  el.innerHTML = s + '</svg>';
  if (o.pick) el.onclick = e => { const i = e.target.dataset.i; if (i != null) o.pick(+i); };
}

function smooth(p) { if (p.length < 2) return ''; let d = `M${p[0][0]},${p[0][1]}`;
  for (let i = 0; i < p.length - 1; i++) { const a = p[i - 1] || p[i], b = p[i], c = p[i + 1], e = p[i + 2] || c, t = .18;
    d += ` C${b[0] + (c[0] - a[0]) * t},${b[1] + (c[1] - a[1]) * t} ${c[0] - (e[0] - b[0]) * t},${c[1] - (e[1] - b[1]) * t} ${c[0]},${c[1]}`; } return d; }
function lines(el, o) {
  const W = el.clientWidth, H = el.clientHeight, pb = 20, pt = 10, h = H - pb - pt, n = o.labels.length, pl = o.axis ? 38 : 0; if (W < 40 || h < 20) return;
  let s = `<svg viewBox="0 0 ${W} ${H}"><defs>`;
  o.series.forEach((se, j) => { s += `<linearGradient id="l${o.id}${j}" x1="0" x2="1"><stop offset="0" stop-color="${se.c[0]}"/><stop offset="1" stop-color="${se.c[1]}"/></linearGradient><linearGradient id="a${o.id}${j}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${se.c[1]}" stop-opacity=".25"/><stop offset="1" stop-color="${se.c[1]}" stop-opacity="0"/></linearGradient><filter id="f${o.id}${j}" x="-5%" y="-30%" width="110%" height="160%"><feGaussianBlur stdDeviation="3.5"/></filter>`; });
  s += '</defs>';
  const X = i => n < 2 ? pl + (W - pl) / 2 : pl + 6 + i / (n - 1) * (W - pl - 12);
  o.series.forEach((se, j) => { const ok = se.vals.filter(x => x != null); if (ok.length < 2) return;
    let mn = Math.min(...ok, se.target ?? Infinity), mx = Math.max(...ok, se.target ?? -Infinity);
    if (o.axis) { const span = (mx - mn) || 1, st = nice(span).st; mn = Math.floor((mn - span * .08) / st) * st; mx = Math.ceil((mx + span * .08) / st) * st;
      if (j === 0) for (let v = mn; v <= mx + 1e-9; v += st) { const y = pt + (1 - (v - mn) / (mx - mn)) * h; s += `<line class="gl" x1="${pl}" x2="${W}" y1="${y}" y2="${y}"/><text x="${pl - 8}" y="${y + 3}" text-anchor="end">${kfmt(v)}</text>`; } }
    else { const pad = (mx - mn) * .12 || 1; mn -= pad; mx += pad; if (j === 0) for (let g = 0; g <= 3; g++) { const y = pt + g * h / 3; s += `<line class="gl" x1="0" x2="${W}" y1="${y}" y2="${y}"/>`; } }
    const Y = v => pt + (1 - (v - mn) / (mx - mn)) * h, pts = se.vals.map((v, i) => v == null ? null : [X(i), Y(v)]).filter(Boolean), d = smooth(pts);
    if (o.area) s += `<path d="${d} L${pts.at(-1)[0]},${pt + h} L${pts[0][0]},${pt + h} Z" fill="url(#a${o.id}${j})"/>`;
    s += `<path d="${d}" stroke="url(#l${o.id}${j})" stroke-width="4" opacity=".5" filter="url(#f${o.id}${j})"/><path d="${d}" stroke="url(#l${o.id}${j})" stroke-width="2.4"/>`;
    if (se.target != null) { const y = Y(se.target); s += `<line x1="${pl}" x2="${W}" y1="${y}" y2="${y}" stroke="${se.c[1]}" stroke-opacity=".55" stroke-dasharray="4 5"/><text x="${W - 4}" y="${y - 5}" text-anchor="end" style="fill:${se.c[1]}">objectif ${f1(se.target)}</text>`; }
    if (se.dots) pts.forEach(p => s += `<circle cx="${p[0]}" cy="${p[1]}" r="2.6" fill="${se.c[1]}"/>`); });
  const ev = Math.ceil(n / Math.max(2, Math.floor((W - pl) / 70)));
  o.labels.forEach((l, i) => { if (i % ev === 0 || (i === n - 1 && (n - 1) % ev > ev / 2)) s += `<text x="${X(i)}" y="${H - 5}" text-anchor="${i === n - 1 ? 'end' : i === 0 && !pl ? 'start' : 'middle'}">${l}</text>`;
    s += `<rect x="${X(i) - (W - pl) / n / 2}" y="0" width="${(W - pl) / n}" height="${H}" fill="transparent" data-t="${o.tip(i)}"/>`; });
  el.innerHTML = s + '</svg>';
}

const ring = (v, c, sz = 110, sw = 10) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r;
  return `<svg viewBox="0 0 ${sz} ${sz}"><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.07)" stroke-width="${sw}"/><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="${c}" stroke-width="${sw}" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - Math.min(1, Math.max(0, v)))}"/></svg>`; };
const donut = (parts, sz = 110, sw = 14) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r, t = sum(parts.map(p => p[0])) || 1; let off = 0;
  return `<svg viewBox="0 0 ${sz} ${sz}" style="width:${sz}px;height:${sz}px;transform:rotate(-90deg);flex-shrink:0"><circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.06)" stroke-width="${sw}"/>${parts.map(([v, c]) => { const l = v / t * C, el = `<circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="${c}" stroke-width="${sw}" stroke-dasharray="${Math.max(0, l - 2)} ${C}" stroke-dashoffset="${-off}"/>`; off += l; return el; }).join('')}</svg>`; };

/* détail d'un jour (réutilisé partout) */
function dayDetail(d, k, noMeals) {
  const x = D(d, k), G = d.goals, ms = noMeals ? [] : (d.ml && d.ml[k]) || (k === today() ? (d.meals || []).map(m => [m.s, m.n, m.k, m.p, m.c, m.f]) : null);
  const wos = (d.wos || []).filter(w => key(new Date(w.t)) === k), w = x.weight;
  return `<div class="dt">${dLong(k)}</div>
  <div class="kv"><div><small>Pas</small><b class="num">${fmt(x.steps)}</b> ${x.steps >= G.steps ? '<span class="tag ok">objectif</span>' : ''}</div>
    <div><small>Calories</small><b class="num">${x.kcal ? fmt(x.kcal) : '—'}</b></div>
    <div><small>Poids</small><b class="num">${w ? f1(w) + ' kg' : '—'}</b></div>
    <div><small>Prot · Gluc · Lip</small><b class="num" style="font-size:14px">${x.p || 0} · ${x.c || 0} · ${x.f || 0} g</b></div></div>
  ${x.off ? '<div style="margin-top:10px"><span class="tag">Jour off</span></div>' : ''}
  ${wos.length ? `<div class="slot">Séance</div>${wos.map(w => `<div class="meal" style="--c:#7896FF"><i><svg viewBox="0 0 24 24" style="width:15px;height:15px"><path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"/></svg></i><div><b>${esc(w.n)}</b><small>${w.m} min · ${nsets(w)} séries</small></div><div class="k"><b class="num">${fmt(wVol(w))}</b><small>kg</small></div></div>`).join('')}` : x.wo === 0 ? '<div class="slot">Séance</div><span class="tag no">Manquée</span>' : ''}
  ${ms && ms.length ? `<div class="slot">Repas · ${fmt(sum(ms.map(m => m[2])))} kcal</div>${Object.entries(SLOTS).map(([s, l]) => { const a = ms.filter(m => m[0] === s), c = SLOTC[s]; return a.map(m => `<div class="meal" style="--c:${c}"><i>${l.slice(0, 2)}</i><div><b>${esc(m[1])}</b><small>${l} · P ${m[3]} · G ${m[4]} · L ${m[5]}</small></div><div class="k"><b class="num">${fmt(m[2])}</b><small>kcal</small></div></div>`).join(''); }).join('')}` : ms === null && x.kcal ? '<div class="sub">Détail des repas disponible sur les 60 derniers jours.</div>' : ''}`;
}

/* pages */
function pHome(d) {
  const G = d.goals, T = today(), t = D(d, T), y = D(d, addDays(T, -1)), lw = lastWeight(d), wk = lastN(7), rng = lastN(ST.trend);
  const mac = (l, v, g, c) => `<div><b class="num">${fmt(v)} g</b><small>${l}</small><i><s style="width:${Math.min(100, v / (g || 1) * 100)}%;background:${c}"></s></i></div>`;
  const wd = lw && G.start ? lw - G.start : null, wp = G.start && G.target && lw ? Math.max(0, Math.min(1, (G.start - lw) / (G.start - G.target))) : 0;
  CH.hw = el => bars(el, { id: 'hw', vals: wk.map(k => D(d, k).steps), labels: wk.map(k => dayLbl(k).toUpperCase()), goal: G.steps, hi: i => i === 6, tip: i => `${dShort(wk[i])} · <b>${fmt(D(d, wk[i]).steps)}</b> pas`, pick: i => { ST.csel = wk[i]; ST.hw = 0; go('hist'); } });
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
  CH.sb = el => bars(el, { id: 'sb', allv: ST.showV, vals: v, labels: P.labels, goal: P.year ? null : G.steps, sel: P.year ? null : P.ks.indexOf(sel), tip: i => P.year ? `${P.labels[i]} · <b>${fmt(v[i])}</b> pas` : `${dShort(P.ks[i])} · <b>${fmt(v[i])}</b> pas`,
    pick: i => { if (P.year) { s.m = 'month'; s.o = (new Date().getFullYear() - +P.title) * 12 + new Date().getMonth() - i; } else s.sel = P.ks[i]; render(); } });
  const all = Object.keys(d.days || {}).sort(), wkT = {}, moT = {}; all.forEach(k => { const x = D(d, k).steps; wkT[monday(k)] = (wkT[monday(k)] || 0) + x; moT[k.slice(0, 7)] = (moT[k.slice(0, 7)] || 0) + x; });
  const bD = all.reduce((b, k) => D(d, k).steps > (b ? D(d, b).steps : -1) ? k : b, null), bW = Object.entries(wkT).sort((a, b) => b[1] - a[1])[0], bM = Object.entries(moT).sort((a, b) => b[1] - a[1])[0];
  const k90 = lastN(30), wdA = [0, 1, 2, 3, 4, 5, 6].map(i => avg(k90.filter(k => (parseKey(k).getDay() + 6) % 7 === i).map(k => D(d, k).steps))), wdN = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  CH.swd = el => bars(el, { id: 'swd', vals: wdA, labels: wdN, goal: G.steps, hi: i => wdA[i] === Math.max(...wdA), vlab: true, tip: i => `${wdN[i]} · <b>${fmt(wdA[i])}</b> pas en moyenne` });
  const s90 = k90.map(k => D(d, k).steps), dist = [['< 5 000', s90.filter(x => x < 5000).length, '#5E5E6C'], ['5 – 10 000', s90.filter(x => x >= 5000 && x < 10000).length, 'color-mix(in srgb,var(--acc) 45%,#333)'], ['10 – 15 000', s90.filter(x => x >= 10000 && x < 15000).length, 'var(--acc)'], ['15 000 +', s90.filter(x => x >= 15000).length, 'var(--acc2)']];
  const gs = (() => { let cur = 0, best = 0, run = 0; all.forEach(k => { if (D(d, k).steps >= G.steps) { run++; best = Math.max(best, run); } else run = 0; }); for (let k = today(); D(d, k).steps >= G.steps || (k === today() && cur === 0 && D(d, k).steps < G.steps && false); k = addDays(k, -1)) cur++; if (!cur) for (let k = addDays(today(), -1); D(d, k).steps >= G.steps; k = addDays(k, -1)) cur++; return { cur, best }; })();
  return `<div class="row" style="grid-template-columns:repeat(5,1fr)">
    <div class="card s glow"><div class="lab">Total · ${P.title.toLowerCase()}</div><div class="big num">${fmt(sum(dv))}</div><div class="sub">${trend(sum(dv), prev)} vs période précédente</div></div>
    <div class="card s"><div class="lab">Moyenne / jour</div><div class="big num">${fmt(avg(dv))}</div><div class="sub">sur ${days.length} jour${days.length > 1 ? 's' : ''}</div></div>
    <div class="card s"><div class="lab">Objectif atteint</div><div class="big num">${dv.filter(x => x >= G.steps).length}<small>/ ${days.length} j</small></div><div class="pbar"><i style="width:${days.length ? dv.filter(x => x >= G.steps).length / days.length * 100 : 0}%"></i></div></div>
    <div class="card s"><div class="lab">Meilleur jour</div><div class="big num">${best ? fmt(D(d, best).steps) : '—'}</div><div class="sub">${best ? dShort(best) : ''}</div></div>
    <div class="card s"><div class="lab">Distance</div><div class="big num">${fmt(sum(dv) * .00075)}<small>km</small></div><div class="sub">≈ ${fmt(sum(dv) * .04)} kcal brûlées</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx glow"><div class="hdr2"><div style="display:flex;align-items:center;gap:8px"><div class="lab">Pas</div><button class="eye ${ST.showV ? 'on' : ''}" id="eyeV" aria-label="Afficher les valeurs"><svg viewBox="0 0 24 24">${ST.showV ? '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>' : '<path d="M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.2M6.6 6.6A17 17 0 0 0 2 12s3.5 7 10 7a10 10 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2"/>'}</svg></button></div>${pnav('steps', s)}</div><div class="ch" data-ch="sb"></div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${sel ? dayDetail(d, sel, true) : '<div class="empty">Choisis un jour</div>'}</div></div></div>
  <div class="row" style="grid-template-columns:1.3fr 1fr 1fr">
    <div class="card fx glow"><div class="hdr2"><div class="lab">Par jour de la semaine <span style="color:var(--dim)">· 30 j</span></div></div><div class="ch" data-ch="swd"></div></div>
    <div class="card fx"><div class="lab">Tes journées <span style="color:var(--dim)">· 30 j</span></div><div class="hb" style="justify-content:space-around;flex:1">${dist.map(([l, x, c]) => `<div><span>${l}</span><i><s style="width:${x / Math.max(1, ...dist.map(z => z[1])) * 100}%;background:${c}"></s></i><b class="num">${x}</b></div>`).join('')}</div>
      <div class="sub">Série d'objectifs : <b style="color:var(--txt)">${gs.cur} j</b> en cours · record <b style="color:var(--txt)">${gs.best} j</b></div></div>
    <div class="card fx"><div class="lab">Records</div><div class="tiles">
      <div><small>Meilleur jour</small><b class="num">${bD ? fmt(D(d, bD).steps) : '—'}</b><em>${bD ? dShort(bD) + ' ' + bD.slice(0, 4) : ''}</em></div>
      <div><small>Meilleure semaine</small><b class="num">${bW ? fmt(bW[1]) : '—'}</b><em>${bW ? 'du ' + dShort(bW[0]) : ''}</em></div>
      <div><small>Meilleur mois</small><b class="num">${bM ? fmt(bM[1]) : '—'}</b><em>${bM ? MOIS[+bM[0].slice(5) - 1] + ' ' + bM[0].slice(0, 4) : ''}</em></div>
      <div><small>Depuis le début</small><b class="num">${fmt(d.total)}</b><em>≈ ${fmt(d.total * .00075)} km</em></div></div></div></div>`;
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
  const rng = lastN(60), rv = rng.map(k => D(d, k).kcal), rm = mavg(rv);
  CH.fl = el => bars(el, { id: 'fl', vals: rv, labels: rng.map((k, i) => i % 7 === 0 ? dShort(k) : ''), goal: G.kcal, goalC: '#FF8A4C', line: rm, lineC: '#FFC44A', dim: true, hi: i => rv[i] && rv[i] <= G.kcal * 1.05, tip: i => `${dShort(rng[i])} · <b>${rv[i] ? fmt(rv[i]) + ' kcal' : 'rien noté'}</b>${rm[i] ? ` · moy. 7 j ${fmt(rm[i])}` : ''}` });
  return `<div class="row" style="grid-template-columns:repeat(4,1fr)">
    <div class="card s" style="--g:var(--cal)"><div class="lab">Moyenne · ${P.title.toLowerCase()}</div><div class="big num">${fmt(avg(kc))}<small>kcal / jour</small></div><div class="sub">objectif ${fmt(G.kcal)}</div></div>
    <div class="card s" style="--g:var(--pro)"><div class="lab">Protéines</div><div class="big num">${fmt(avg(pr))}<small>g / jour</small></div><div class="sub">${G.protein ? `objectif atteint ${pr.filter(x => x >= G.protein).length} / ${days.length} j` : ''}</div></div>
    <div class="card s" style="--g:var(--ok)"><div class="lab">Dans l'objectif</div><div class="big num">${ok}<small>/ ${days.length} j notés</small></div><div class="pbar"><i style="width:${days.length ? ok / days.length * 100 : 0}%;background:var(--ok)"></i></div></div>
    <div class="card s" style="--g:var(--carb)"><div class="lab">Répartition moyenne</div><div style="display:flex;gap:12px;font-size:12px;margin-top:4px">${[['Prot.', mp, 'var(--pro)'], ['Gluc.', mc, 'var(--carb)'], ['Lip.', mf, 'var(--fat)']].map(([l, x, c]) => `<div><b class="num" style="font-size:20px;color:${c}">${Math.round(x / mt * 100)} %</b><div class="sub" style="margin-top:2px">${l}</div></div>`).join('')}</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx" style="--g:var(--cal)"><div class="hdr2"><div class="lab">Macros</div><div style="display:flex;gap:14px;align-items:center"><div class="legend"><span><i style="background:var(--pro)"></i>Prot.</span><span><i style="background:var(--carb)"></i>Gluc.</span><span><i style="background:var(--fat)"></i>Lip.</span></div>${pnav('food', s)}</div></div><div class="ch" data-ch="fb"></div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${sel ? dayDetail(d, sel) : '<div class="empty">Choisis un jour</div>'}</div></div></div>
  <div class="row" style="grid-template-columns:1fr"><div class="card fx" style="--g:var(--cal)"><div class="hdr2"><div class="lab">Calories · 60 jours</div><div class="legend"><span><i style="background:var(--acc)"></i>Dans l'objectif</span><span><i style="background:#3A3A42"></i>Au-dessus</span><span><i style="background:#FFC44A"></i>Moyenne 7 j</span><span><i style="background:#FF8A4C"></i>Objectif ${fmt(G.kcal)}</span></div></div><div class="ch" data-ch="fl"></div></div></div>`;
}
function pWeight(d) {
  const G = d.goals, all = Object.keys(d.days || {}).filter(k => d.days[k][2]).sort(), lw = lastWeight(d), ks = ST.wr ? lastN(ST.wr) : (all.length ? (() => { const a = []; let k = all[0]; while (k <= today()) { a.push(k); k = addDays(k, 1); } return a; })() : []);
  const v = ks.map(k => D(d, k).weight), ok = v.filter(x => x), w7 = avg(all.slice(-7).map(k => d.days[k][2])), w7p = avg(all.filter(k => k <= addDays(today(), -7)).slice(-7).map(k => d.days[k][2]));
  const rate = all.length > 1 ? (() => { const a = all.filter(k => k >= addDays(today(), -28)); if (a.length < 2) return 0; return (d.days[a.at(-1)][2] - d.days[a[0]][2]) / ((parseKey(a.at(-1)) - parseKey(a[0])) / 864e5) * 7; })() : 0;
  const eta = lw && G.target && rate && Math.sign(G.target - lw) === Math.sign(rate) ? (() => { const x = new Date(); x.setDate(x.getDate() + Math.round((G.target - lw) / rate * 7)); return `${String(x.getDate()).padStart(2, '0')}/${String(x.getMonth() + 1).padStart(2, '0')}/${String(x.getFullYear()).slice(2)}`; })() : null;
  CH.wl = el => lines(el, { id: 'wl', area: true, axis: true, labels: ks.map(dShort), series: [{ vals: v, c: ['#4FE3D0', 'var(--wgt)'], target: G.target || null, dots: true }], tip: i => `${dShort(ks[i])}${v[i] ? ` · <b>${f1(v[i])} kg</b>` : ''}` });
  return `<div class="row" style="grid-template-columns:repeat(5,1fr)">
    <div class="card s" style="--g:var(--wgt)"><div class="lab">Actuel</div><div class="big num">${lw ? f1(lw) : '—'}<small>kg</small></div><div class="sub">${lw && G.start ? `${lw <= G.start ? '−' : '+'}${f1(Math.abs(lw - G.start))} kg depuis ${f1(G.start)}` : ''}</div></div>
    <div class="card s"><div class="lab">Moyenne 7 pesées</div><div class="big num">${w7 ? f1(w7) : '—'}<small>kg</small></div><div class="sub">${w7p ? `${w7 <= w7p ? '<span class="up">−' : '<span class="dn">+'}${f1(Math.abs(w7 - w7p))} kg</span> vs semaine passée` : ''}</div></div>
    <div class="card s"><div class="lab">Rythme</div><div class="big num">${rate ? (rate > 0 ? '+' : '−') + f1(Math.abs(rate)) : '—'}<small>kg / sem.</small></div><div class="sub">sur 4 semaines</div></div>
    <div class="card s"><div class="lab">Objectif</div><div class="big num">${G.target ? f1(G.target) : '—'}<small>kg</small></div><div class="sub">${lw && G.target ? f1(Math.abs(lw - G.target)) + ' kg restants' : ''}</div></div>
    <div class="card s"><div class="lab">Date estimée</div><div class="big num">${eta || '—'}</div><div class="sub">au rythme actuel</div></div></div>
  <div class="row" style="grid-template-columns:2.2fr 1fr">
    <div class="card fx" style="--g:var(--wgt)"><div class="hdr2"><div class="lab">Courbe</div><div class="seg" id="wr">${[[30, '1 mois'], [90, '3 mois'], [180, '6 mois'], [365, '1 an'], [0, 'Tout']].map(([n, l]) => `<button data-n="${n}" class="${ST.wr === n ? 'on' : ''}">${l}</button>`).join('')}</div></div>
      ${ok.length > 1 ? '<div class="ch" data-ch="wl"></div>' : '<div class="empty">Pas assez de pesées sur cette période</div>'}</div>
    <div class="card fx"><div class="lab">Pesées</div><div class="scroll"><div class="list">${all.slice().reverse().slice(0, 60).map((k, i, a) => { const w = d.days[k][2], p = a[i + 1] ? d.days[a[i + 1]][2] : null, df = p ? w - p : null;
      return `<div class="li" style="padding:7px 0"><div><b>${dShort(k)}</b><small>${dayLbl(k)}</small></div><div class="r"><b class="num">${f1(w)} kg</b>${df != null ? `<small class="${df <= 0 ? 'up' : 'dn'}">${df > 0 ? '+' : df < 0 ? '−' : '±'}${f1(Math.abs(df))}</small>` : ''}</div></div>`; }).join('') || '<div class="empty">Aucune pesée</div>'}</div></div></div></div>`;
}
function pHist(d) {
  const T = today(), G = d.goals, a = parseKey(monday(T)); a.setDate(a.getDate() - ST.hw * 7);
  const ks = Array.from({ length: 7 }, (_, i) => { const x = new Date(a); x.setDate(a.getDate() + i); return key(x); }), past = ks.filter(k => k <= T);
  const sel = ST.csel && ks.includes(ST.csel) ? ST.csel : (ks.includes(T) ? T : ks[6]);
  const wts = Object.keys(d.days || {}).filter(k => d.days[k][2]).sort(), prevW = k => { const x = wts.filter(y => y < k); return x.length ? d.days[x.at(-1)][2] : null; };
  const st = past.map(k => D(d, k).steps), kc = past.map(k => D(d, k).kcal).filter(Boolean), pr = past.map(k => D(d, k).p).filter(Boolean);
  const wosW = (d.wos || []).filter(w => ks.includes(key(new Date(w.t)))), ww = past.filter(k => D(d, k).weight), w0 = ww.length ? (prevW(ww[0]) ?? D(d, ww[0]).weight) : null, wdf = ww.length && w0 != null ? D(d, ww.at(-1)).weight - w0 : null;
  const b = new Date(a); b.setDate(a.getDate() + 6);
  const lbl = ST.hw === 0 ? 'Cette semaine' : ST.hw === 1 ? 'Semaine passée' : `${a.getDate()} ${MOIS[a.getMonth()]} – ${b.getDate()} ${MOIS[b.getMonth()]} ${b.getFullYear()}`;
  const day = k => { const x = D(d, k), hasW = wosW.some(w => key(new Date(w.t)) === k), pw = x.weight ? prevW(k) : null, dk = pw != null ? x.weight - pw : null;
    const c = k > T ? '#5E5E6C' : x.off ? '#8E8E9E' : hasW || x.wo === 1 ? '#7896FF' : x.wo === 0 ? '#FF5C70' : x.steps >= G.steps ? 'var(--acc)' : '#5E5E6C';
    const bdg = x.off ? ['Jour off', 'rgba(255,255,255,.08)', 'var(--mut)'] : hasW || x.wo === 1 ? ['Séance', 'rgba(120,150,255,.18)', '#9DB0FF'] : x.wo === 0 ? ['Manquée', 'rgba(255,92,112,.15)', '#FF8A99'] : x.steps >= G.steps ? ['Objectif', 'color-mix(in srgb,var(--acc) 20%,transparent)', 'var(--acc)'] : null;
    return `<div class="dc ${k === sel ? 'sel' : ''} ${k > T ? 'fut' : ''}" data-k="${k}" style="--c:${c}"><div class="dd"><b class="num">${+k.slice(8)}</b><small>${dayLbl(k)}</small></div>
      <div class="v"><small>Pas</small><b class="num">${fmt(x.steps)}</b><i><s style="width:${Math.min(100, x.steps / G.steps * 100)}%"></s></i></div>
      <div class="v"><small>Kcal</small><b class="num" style="${x.kcal > G.kcal * 1.05 ? 'color:var(--bad)' : ''}">${x.kcal ? fmt(x.kcal) : '—'}</b></div>
      <div class="v"><small>Prot.</small><b class="num" style="${G.protein && x.p >= G.protein ? 'color:var(--pro)' : ''}">${x.p ? x.p + ' g' : '—'}</b></div>
      <div class="v"><small>Poids</small><b class="num">${x.weight ? f1(x.weight) : '—'}</b>${dk != null ? ` <span style="font-size:11px" class="${dk > 0 ? 'dn' : dk < 0 ? 'up' : ''}">${dk > 0 ? '+' : dk < 0 ? '−' : '±'}${f1(Math.abs(dk))}</span>` : ''}</div>
      ${bdg ? `<span class="bdg" style="background:${bdg[1]};color:${bdg[2]}">${bdg[0]}</span>` : '<span></span>'}</div>`; };
  const lst = ks.filter(k => k <= T).reverse();
  return `<div class="row" style="grid-template-columns:1.55fr 1fr;height:100%">
    <div class="card fx"><div class="hdr2"><div class="lab">Historique</div><div class="pnav"><button class="arr" id="hwP">${ARR(0)}</button><b>${lbl}</b><button class="arr" id="hwN" ${ST.hw ? '' : 'disabled'}>${ARR(1)}</button></div></div>
      <div class="wsum"><div style="--c:var(--acc)"><small>Pas / jour</small><b class="num">${fmt(avg(st))}</b></div><div style="--c:var(--acc2)"><small>Total</small><b class="num">${fmt(sum(st))}</b></div>
        <div style="--c:#7896FF"><small>Séances</small><b class="num">${wosW.length}</b></div><div style="--c:var(--cal)"><small>Kcal / jour</small><b class="num">${kc.length ? fmt(avg(kc)) : '—'}</b></div>
        <div style="--c:var(--wgt)"><small>Poids</small><b class="num ${wdf > 0 ? 'dn' : wdf < 0 ? 'up' : ''}">${wdf == null ? '—' : (wdf > 0 ? '+' : wdf < 0 ? '−' : '±') + f1(Math.abs(wdf)) + ' kg'}</b></div></div>
      <div class="days">${lst.map(day).join('')}${Array(7 - lst.length).fill('<div class="dc fut" style="--c:transparent;border-color:transparent;background:none"></div>').join('')}</div></div>
    <div class="card fx"><div class="lab">Détail du jour</div><div class="scroll">${dayDetail(d, sel)}</div></div></div>`;
}

function pGym(d) {
  const wos = d.wos || [], now = new Date(), mon = parseKey(monday(today())).getTime(), m0 = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const wk = wos.filter(w => w.t >= mon), mo = wos.filter(w => w.t >= m0), sel = wos[Math.min(ST.wsel, wos.length - 1)];
  const now0 = new Date(); let VB;
  if (ST.gvm === 'day') VB = lastN(30).map(k => { const a = parseKey(k).getTime(); return [a, a + 864e5, dShort(k)]; });
  else if (ST.gvm === 'month') VB = Array.from({ length: 12 }, (_, i) => { const a = new Date(now0.getFullYear(), now0.getMonth() - 11 + i, 1), b = new Date(a.getFullYear(), a.getMonth() + 1, 1); return [a.getTime(), b.getTime(), MOIS[a.getMonth()].replace('.', '')]; });
  else VB = Array.from({ length: 12 }, (_, i) => { const a = mon - (11 - i) * 7 * 864e5; return [a, a + 7 * 864e5, dShort(key(new Date(a)))]; });
  const weeks = VB.map(x => x[0]), wv = VB.map(([a, b]) => sum(wos.filter(w => w.t >= a && w.t < b).map(wVol)));
  const wn = VB.map(([a, b]) => wos.filter(w => w.t >= a && w.t < b).length), wavg = avg(wv.filter(Boolean)), hN = ST.gvm === 'day' ? 7 : ST.gvm === 'month' ? 3 : 4, l4 = sum(wv.slice(-hN)), p4 = sum(wv.slice(-2 * hN, -hN));
  CH.gv = el => bars(el, { id: 'gv', vals: wv, labels: VB.map((x, i) => ST.gvm === 'day' ? (i % 5 === 4 ? x[2] : '') : ST.gvm === 'month' ? x[2] : i % 2 ? '' : x[2]), hi: i => i === VB.length - 1, vlab: true, avgLine: wavg || null, dim: false, tip: i => `${ST.gvm === 'week' ? 'Semaine du ' : ''}${VB[i][2]} · <b>${fmt(wv[i])} kg</b> · ${wn[i]} séance${wn[i] > 1 ? 's' : ''}` });
  const cats = {}; wos.filter(w => w.t >= Date.now() - 30 * 864e5).forEach(w => (w.ex || []).forEach(e => { const c = e[1] || 'Autre'; cats[c] = (cats[c] || 0) + (e[2] || []).length; }));
  const cl = Object.entries(cats).sort((a, b) => b[1] - a[1]).slice(0, 7), cmx = Math.max(1, ...cl.map(c => c[1]));
  const exs = {}; wos.forEach(w => (w.ex || []).forEach(e => { const z = e[2] || []; if (!z.length) return; const bs = z.reduce((b, x) => x[0] > b[0] || (x[0] === b[0] && x[1] > b[1]) ? x : b, z[0]);
    (exs[e[0]] = exs[e[0]] || []).push({ t: w.t, kg: bs[0], rp: bs[1], mr: Math.max(...z.map(x => x[1])), vol: sum(z.map(x => x[0] * x[1])), e1: Math.max(...z.map(x => x[0] * (1 + x[1] / 30))), n: z.length }); }));
  const hid = hiddenEx(), old = n => Math.max(...exs[n].map(p => p.t)) < Date.now() - 60 * 864e5, exAll = Object.keys(exs).sort((a, b) => exs[b].length - exs[a].length), nOld = exAll.filter(n => !hid.includes(n) && old(n)).length, exNames = exAll.filter(n => !hid.includes(n) && (ST.oldEx || !old(n))); if (!exNames.includes(ST.ex)) ST.ex = exNames[0] || '';
  const top = {}; wos.filter(w => w.t >= Date.now() - 30 * 864e5).forEach(w => (w.ex || []).forEach(e => { const t = top[e[0]] = top[e[0]] || { c: e[1], n: 0, kg: 0 }; t.n += (e[2] || []).length; t.kg = Math.max(t.kg, ...(e[2] || []).map(x => x[0]), 0); }));
  const topL = Object.entries(top).sort((a, b) => b[1].n - a[1].n).slice(0, 5);
  const pts = (exs[ST.ex] || []).slice().sort((a, b) => a.t - b.t), rec = pts.length ? pts.reduce((b, p) => p.kg > b.kg || (p.kg === b.kg && p.rp > b.rp) ? p : b, pts[0]) : null;
  const PM = { kg: ['Charge', p => p.kg, 'kg'], rp: ['Reps', p => p.mr, 'reps'], vol: ['Volume', p => p.vol, 'kg'], e1: ['1RM', p => p.e1, 'kg'] }, pm = PM[ST.pm] ? ST.pm : 'kg';
  CH.gp = el => lines(el, { id: 'gp', area: true, axis: true, labels: pts.map(p => dShort(key(new Date(p.t)))), series: [{ vals: pts.map(PM[pm][1]), c: ['#7C8CFF', '#B88CFF'], dots: true }], tip: i => { const p = pts[i]; return `${dShort(key(new Date(p.t)))} · meilleure série <b>${f1(p.kg).replace(',0', '')} kg × ${p.rp}</b> · ${p.n} séries · volume ${fmt(p.vol)} kg · 1RM ${f1(p.e1)} kg`; } });
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
    <div class="card fx"><div class="lab">Séries par muscle · 30 j</div><div class="hb">${cl.slice(0, 6).map(([c, x]) => `<div><span style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(c)}</span><i><s style="width:${x / cmx * 100}%;background:${catColor(c)}"></s></i><b class="num">${x}</b></div>`).join('') || '<div class="empty">Pas de données</div>'}</div>
      <div class="sep"></div><div class="lab" style="margin-bottom:4px">Exercices les plus faits · 30 j</div><div class="scroll"><div class="tex">${topL.map(([n, t]) => `<div><i style="background:${catColor(t.c || n)}"></i><span>${esc(n)}</span><small>${t.n} séries</small><b class="num">${t.kg ? f1(t.kg).replace(',0', '') + ' kg' : '—'}</b></div>`).join('') || '<div class="empty">Pas de données</div>'}</div></div></div></div>
  <div class="row" style="grid-template-columns:1fr 1fr">
    <div class="card fx" style="--g:#B88CFF"><div class="hdr2"><div class="lab">Volume</div><div style="display:flex;gap:8px;align-items:center"><div class="seg" id="gvm">${[['day', 'Jour'], ['week', 'Semaine'], ['month', 'Mois']].map(([k, l]) => `<button data-m="${k}" class="${ST.gvm === k ? 'on' : ''}">${l}</button>`).join('')}</div><span class="tag">moy. ${wavg >= 1000 ? f1(wavg / 1000) + ' t' : fmt(wavg) + ' kg'}</span><span class="tag ${l4 >= p4 ? 'ok' : 'no'}" data-t="Période récente vs précédente">${p4 ? (l4 >= p4 ? '+' : '−') + Math.abs(Math.round((l4 - p4) / p4 * 100)) + ' %' : '—'}</span></div></div><div class="ch" data-ch="gv"></div></div>
    <div class="card fx" style="--g:#B88CFF"><div class="hdr2"><div class="lab">Progression</div><div style="display:flex;gap:8px;align-items:center"><span class="rec"><small>Record</small><b class="num">${rec ? f1(rec.kg).replace(',0', '') + ' kg × ' + rec.rp : '—'}</b></span><div class="seg" id="pmS">${Object.entries(PM).map(([k, v]) => `<button data-m="${k}" class="${pm === k ? 'on' : ''}">${v[0]}</button>`).join('')}</div><select class="sel2" id="exSel">${exNames.map(n => `<option ${n === ST.ex ? 'selected' : ''}>${esc(n)}</option>`).join('')}</select>
      ${ST.ex ? '<button class="chip ic" id="exHide" data-t="Retirer cet exercice de la liste"><svg viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/></svg></button>' : ''}${nOld ? `<button class="chip ${ST.oldEx ? 'on' : ''}" id="exOld" data-t="Exercices pas faits depuis 2 mois">Anciens (${nOld})</button>` : ''}</div></div>
      ${pts.length > 1 ? '<div class="ch" data-ch="gp"></div>' : '<div class="empty">Pas assez de séances pour cet exercice</div>'}</div></div>`;
}
const hiddenEx = () => { try { return JSON.parse(localStorage.getItem('focus.hiddenEx')) || []; } catch (e) { return []; } };
/* duel avec un ami */
let DUEL = null, DUELT = [];
let DPER = 'week';
const duelStats = (d, per) => { const T = today(), wos = d.wos || [], lw = lastWeight(d), G = d.goals || {}, gs = G.steps || 10000;
  const ks = per === 'day' ? [T] : period(per, 0).ks.filter(k => k <= T), t0 = parseKey(ks[0]).getTime(), t1 = parseKey(T).getTime() + 864e5;
  const st = ks.map(k => D(d, k).steps), pw = wos.filter(w => w.t >= t0 && w.t < t1);
  const prog = G.start && G.target && lw && G.start !== G.target ? Math.max(0, Math.min(100, (G.start - lw) / (G.start - G.target) * 100)) : 0;
  const r = [['Pas', sum(st), fmt]];
  if (per !== 'day') r.push(['Moyenne / jour', avg(st), fmt], ['Meilleur jour', Math.max(0, ...st), fmt], ['Objectif de pas', st.filter(x => x >= gs).length, v => v + ' j']);
  else r.push(['Distance', sum(st) * .00075, v => f1(v) + ' km'], ['Objectif de pas', st[0] >= gs ? 1 : 0, v => v ? 'atteint' : 'non']);
  r.push(['Séances', pw.length, v => v], ['Volume soulevé', sum(pw.map(wVol)), v => v >= 1000 ? f1(v / 1000) + ' t' : fmt(v) + ' kg'], ['Temps de muscu', sum(pw.map(w => w.m)), v => v >= 60 ? Math.floor(v / 60) + ' h ' + (v % 60) + ' min' : v + ' min']);
  r.push(['Série en cours', d.streak || 0, v => v + ' j'], ['Objectif poids', prog, v => Math.round(v) + ' %']);
  return r; };
function pDuel(d) {
  const fr = CFG.friends || [], me = d.name || 'Moi';
  if (!DUEL) return `<div class="row" style="grid-template-columns:1fr;height:100%"><div class="card fx" style="justify-content:center;align-items:center;text-align:center;gap:14px">
    <svg viewBox="0 0 24 24" style="width:54px;height:54px;color:var(--acc)"><path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4M5 21l-2-2"/></svg>
    <div class="dt" style="font-size:26px;margin:0">Défie un ami</div><div class="sub" style="max-width:420px;line-height:1.5">Demande-lui son code de synchro (Réglages › Synchro PC dans son app) et compare vos pas, séances, séries et progrès.</div>
    <div class="dfr"><input id="dCode" placeholder="CODE DE TON AMI" spellcheck="false" maxlength="16"><button class="btn sm" id="dGo">Lancer le duel</button></div><div class="err" id="dErr"></div>
    ${fr.length ? `<div class="dfr" style="flex-wrap:wrap;justify-content:center">${fr.map(f => `<button class="chip" data-fr="${esc(f.code)}">${esc(f.name || f.code)}</button>`).join('')}</div>` : ''}</div></div>`;
  const o = DUEL.data, A = duelStats(d, DPER), B = duelStats(o, DPER), on = DUEL.step;
  let sa = 0, sb = 0; A.slice(0, on).forEach((x, i) => { if (x[1] > B[i][1]) sa++; else if (B[i][1] > x[1]) sb++; });
  const av = (nm, ph, c1, c2) => `<i style="--c1:${c1};--c2:${c2};${ph ? `background-image:url(${ph})` : ''}">${ph ? '' : esc((nm || '?')[0].toUpperCase())}</i>`;
  return `<div class="duel">
    <div class="card dh"><div class="dp">${av(me, CFG.photo, 'var(--acc)', 'var(--acc2)')}<div><b>${esc(me)}</b><small>Toi</small></div></div>
      <div class="ds num"><b class="${on >= A.length && sa > sb ? 'win' : ''}">${sa}</b><span>–</span><b class="${on >= A.length && sb > sa ? 'win' : ''}">${sb}</b></div>
      <div class="dp r">${av(o.name, null, '#5AB8FF', '#7C8CFF')}<div><b>${esc(o.name || 'Ami')}</b><small>${on >= A.length ? (sa > sb ? 'Perdu' : sb > sa ? 'Gagné' : 'Égalité') : '…'}</small></div></div></div>
    <div class="sub" style="text-align:center;margin:-2px 0 2px">${{ day: "Duel du jour", week: 'Duel de la semaine', month: 'Duel du mois', year: "Duel de l'année" }[DPER]}</div>
    <div class="drs">${A.map(([l, va, f], i) => { const vb = B[i][1], mx = Math.max(va, vb, 1), w = va > vb ? 'wl' : vb > va ? 'wr' : 'eq', shown = i < on;
      return `<div class="drw ${shown ? 'on ' + w : ''}"><div class="vl num">${f(va)}</div><div class="bl"><s style="width:${shown ? va / mx * 100 : 0}%"></s></div>
        <div class="c"><b>${l}</b><em>${w === 'wl' ? `1 – 0 pour ${esc(me)}` : w === 'wr' ? `1 – 0 pour ${esc(o.name || 'Ami')}` : 'Égalité'}</em></div>
        <div class="br"><s style="width:${shown ? vb / mx * 100 : 0}%"></s></div><div class="vr num">${f(vb)}</div></div>`; }).join('')}</div>
    <div class="dfr" style="justify-content:center"><div class="seg" id="dPer">${[['day', "Aujourd'hui"], ['week', 'Semaine'], ['month', 'Mois'], ['year', 'Année']].map(([k, l]) => `<button data-p="${k}" class="${DPER === k ? 'on' : ''}">${l}</button>`).join('')}</div><button class="btn ghost sm" id="dAgain">Rejouer</button><button class="btn ghost sm" id="dNew">Autre ami</button></div></div>`;
}
const startDuel = async code => { code = String(code || '').replace(/[^A-Z0-9]/gi, '').toUpperCase(); if (code.length < 8) { $('#dErr').textContent = 'Code trop court'; return; }
  if (code === CFG.code) { $('#dErr').textContent = "C'est ton propre code"; return; }
  try { const o = await fetchOnce(code); if (!o || !o.days) { $('#dErr').textContent = 'Aucune donnée pour ce code'; return; }
    CFG = await fapi.addFriend({ code, name: o.name || code }); DUEL = { code, data: o, step: 0 }; animDuel(); } catch (e) { $('#dErr').textContent = 'Impossible de joindre le serveur'; } };
const animDuel = () => { DUELT.forEach(clearTimeout); DUEL.step = 0; render();
  DUELT = Array.from({ length: 10 }, (_, i) => setTimeout(() => { if (!DUEL || PAGE !== 'duel') return; DUEL.step = i + 1; render(); }, 500 + i * 600)); };
const PAGES = { home: ['Aperçu', pHome, 'fit-home'], hist: ['Historique', pHist, 'fit-1'], steps: ['Pas', pSteps, 'fit-3'], food: ['Nutrition', pFood, 'fit-3'], weight: ['Poids', pWeight, 'fit-2'], gym: ['Séances', pGym, 'fit-3'], duel: ['Duel', pDuel, 'fit-2'] };

let STACK = [];
function stepNotif() {
  if (!DATA) return; const T = today(), s = D(DATA, T).steps, lv = Math.floor(s / 5000); let last = {};
  try { last = JSON.parse(localStorage.getItem('focus.stepN')) || {}; } catch (e) {}
  if (last.d !== T) last = { d: T, l: lv };
  else if (lv > last.l) { last.l = lv; try { new Notification('Focus', { body: `🎉 ${fmt(lv * 5000)} pas atteints aujourd'hui !${lv * 5000 >= DATA.goals.steps && (lv - 1) * 5000 < DATA.goals.steps ? ' Objectif du jour validé.' : ''}`, icon: 'icon.png', silent: false }); } catch (e) {} }
  try { localStorage.setItem('focus.stepN', JSON.stringify(last)); } catch (e) {}
}
function go(p, back) { if (p === PAGE) return; if (!back) STACK.push(PAGE); PAGE = p; render(true); }
const goBack = () => { if (STACK.length) go(STACK.pop(), true); };
function render(anim) {
  const [t, f, cls] = PAGES[PAGE]; $('#crumb b').textContent = t; $('#crumb svg').innerHTML = $(`nav [data-p="${PAGE}"] svg`).innerHTML;
  $('#crumb em') ? null : $('#crumb').insertAdjacentHTML('beforeend', '<em></em>'); $('#crumb em').textContent = '· ' + dLong(today()).replace(/ \d{4}$/, '');
  $$('nav button').forEach(b => b.classList.toggle('on', b.dataset.p === PAGE));
  const pg = $('#page');
  if (!DATA) { pg.className = 'page'; pg.innerHTML = `<div class="card"><div class="empty">${STATUS === 'noserver' ? 'Serveur de synchro pas encore configuré.' : STATUS === 'nocode' ? 'Connecte ton téléphone pour voir tes données.' : 'En attente des données du téléphone…'}</div></div>`; return; }
  const scroll = {}; $$('#page .scroll').forEach((s, i) => scroll[i] = s.scrollTop);
  pg.className = 'page fit ' + cls + (anim ? ' anim' : ''); for (const k in CH) delete CH[k]; pg.innerHTML = f(DATA);
  $('#backB').classList.toggle('hidden', !STACK.length);
  $$('#page .scroll').forEach((s, i) => s.scrollTop = scroll[i] || 0);
  const TC = ['#5AB8FF', '#4FE3D0', '#FFC44A', '#FF7AB8', '#7C8CFF', '#3DDC84', '#FF8A4C', '#B88CFF']; let ti = 0;
  $$('#page .card').forEach(c => { if (!c.classList.contains('glow') && !c.style.getPropertyValue('--g')) c.style.setProperty('--g', TC[ti++ % TC.length]); });
  stepNotif();
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
  if ($('#exHide')) $('#exHide').onclick = () => { localStorage.setItem('focus.hiddenEx', JSON.stringify([...hiddenEx(), ST.ex])); ST.ex = ''; render(); };
  if ($('#exShow')) $('#exShow').onclick = () => { localStorage.removeItem('focus.hiddenEx'); render(); };
  $$('.dr[data-k],.dc[data-k]').forEach(b => b.onclick = () => { ST.csel = b.dataset.k; render(); });
  if ($('#hwP')) { $('#hwP').onclick = () => { ST.hw++; ST.csel = null; render(); }; $('#hwN').onclick = () => { ST.hw = Math.max(0, ST.hw - 1); ST.csel = null; render(); }; }
  if ($('#eyeV')) $('#eyeV').onclick = () => { ST.showV = !ST.showV; try { localStorage.setItem('focus.showV', ST.showV ? '1' : '0'); } catch (e) {} render(); };
  $$('#gvm button').forEach(b => b.onclick = () => { ST.gvm = b.dataset.m; render(); });
  if ($('#exOld')) $('#exOld').onclick = () => { ST.oldEx = !ST.oldEx; render(); };
  $$('#pmS button').forEach(b => b.onclick = () => { ST.pm = b.dataset.m; render(); });
  if ($('#dGo')) { const ci = $('#dCode'); $('#dGo').onclick = () => startDuel(ci.value); ci.addEventListener('keydown', e => { if (e.key === 'Enter') startDuel(ci.value); }); }
  $$('[data-fr]').forEach(b => b.onclick = () => startDuel(b.dataset.fr));
  if ($('#dAgain')) { $('#dAgain').onclick = animDuel; $('#dNew').onclick = () => { DUEL = null; render(); }; $$('#dPer button').forEach(b => b.onclick = () => { DPER = b.dataset.p; animDuel(); }); }
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
    <hr><div class="slot" style="margin:4px 8px 2px">Style</div><div class="wst">${[['glass', 'Verre'], ['dark', 'Sombre'], ['light', 'Clair'], ['neon', 'Néon'], ['minimal', 'Minimal']].map(([k, l]) => `<button data-ws="${k}" class="${(CFG.wstyle || 'glass') === k ? 'on' : ''}">${l}</button>`).join('')}</div>
    <div class="slot" style="margin:6px 8px 2px">Disposition</div><div class="lays">${[['right', 'Colonne à droite', '<rect x="15" y="3" width="6" height="5" rx="1"/><rect x="15" y="10" width="6" height="5" rx="1"/><rect x="15" y="17" width="6" height="4" rx="1"/>'], ['left', 'Colonne à gauche', '<rect x="3" y="3" width="6" height="5" rx="1"/><rect x="3" y="10" width="6" height="5" rx="1"/><rect x="3" y="17" width="6" height="4" rx="1"/>'], ['top', 'Ligne en haut', '<rect x="3" y="3" width="5" height="5" rx="1"/><rect x="9.5" y="3" width="5" height="5" rx="1"/><rect x="16" y="3" width="5" height="5" rx="1"/>'], ['bottom', 'Ligne en bas', '<rect x="3" y="16" width="5" height="5" rx="1"/><rect x="9.5" y="16" width="5" height="5" rx="1"/><rect x="16" y="16" width="5" height="5" rx="1"/>'], ['grid', 'Grille à droite', '<rect x="10" y="3" width="5" height="5" rx="1"/><rect x="16" y="3" width="5" height="5" rx="1"/><rect x="10" y="10" width="5" height="5" rx="1"/><rect x="16" y="10" width="5" height="5" rx="1"/>']].map(([k, l, i]) => `<button data-lay="${k}" data-t="${l}"><svg viewBox="0 0 24 24"><rect x="1.5" y="1.5" width="21" height="21" rx="3" opacity=".35"/>${i}</svg></button>`).join('')}</div>`;
  $$('#wddP [data-w]').forEach(b => b.onclick = e => { e.stopPropagation(); fapi.setWidget(b.dataset.w, !b.classList.contains('on')); });
  $('#wTop').onclick = e => { e.stopPropagation(); fapi.setOnTop(!CFG.onTop); };
  $('#wLock').onclick = e => { e.stopPropagation(); fapi.setLocked(!CFG.locked); };
  $$('#wddP [data-ws]').forEach(b => b.onclick = e => { e.stopPropagation(); fapi.setWStyle(b.dataset.ws); });
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
$$('nav button').forEach(b => { b.onclick = () => { STACK = []; go(b.dataset.p); }; });
$('#backB').onclick = goBack;
$$('.lights button').forEach(b => b.onclick = () => fapi.win(b.dataset.w));
/* barre latérale : largeur réglable à la souris, repliée en icônes sous 150 px */
const setSW = w => { document.body.classList.toggle('mini', w < 150); document.body.classList.toggle('narrow', w >= 150 && w < 215); if (w >= 150) document.documentElement.style.setProperty('--sw', w + 'px'); else document.documentElement.style.removeProperty('--sw'); };
try { const w = +localStorage.getItem('focus.sw'); if (w) setSW(w); } catch (e) {}
$('#rsz').onmousedown = e => { e.preventDefault(); const r = $('#rsz'); r.classList.add('on'); document.body.classList.add('resizing'); let w = 0;
  const mv = ev => { w = Math.max(76, Math.min(340, ev.clientX)); setSW(w); };
  const up = () => { removeEventListener('mousemove', mv); removeEventListener('mouseup', up); r.classList.remove('on'); document.body.classList.remove('resizing'); if (w) { const f = w < 150 ? 76 : w; setSW(f); try { localStorage.setItem('focus.sw', f); } catch (e) {} } drawCharts(); };
  addEventListener('mousemove', mv); addEventListener('mouseup', up); };
addEventListener('mouseup', e => { if (e.button === 3) goBack(); });
$('#rsz').ondblclick = () => $('#miniB').onclick();
$('#miniB').onclick = () => { const m = !document.body.classList.contains('mini'), w = m ? 76 : 248; setSW(w); try { localStorage.setItem('focus.sw', w); } catch (e) {} setTimeout(drawCharts, 280); };
$('#setBtn').onclick = dlgSet;
$('#refBtn').onclick = async () => { const b = $('#refBtn'); if (b.classList.contains('spin')) return; b.classList.add('spin');
  const ok = window.focusRefresh ? await window.focusRefresh() : false; setTimeout(() => b.classList.remove('spin'), 600);
  };
$('#av').onclick = async () => { CFG = await fapi.pickPhoto(); side(); };
$('#wddB').onclick = e => { e.stopPropagation(); $('#wdd').classList.toggle('open'); };
addEventListener('click', e => { if (!e.target.closest('#wdd')) $('#wdd').classList.remove('open'); });
addEventListener('keydown', e => { if (e.key === 'Escape') $('#ov').classList.add('hidden'); if (e.altKey && e.key === 'ArrowLeft') goBack(); });
let rzT; addEventListener('resize', () => { clearTimeout(rzT); rzT = setTimeout(drawCharts, 80); });
const tip = document.createElement('div'); tip.className = 'tip'; document.body.appendChild(tip);
addEventListener('mousemove', e => { const t = e.target.closest && e.target.closest('[data-t]'); if (!t) { tip.classList.remove('on'); return; }
  tip.innerHTML = t.dataset.t; tip.classList.add('on'); tip.style.left = Math.min(innerWidth - tip.offsetWidth - 10, e.clientX + 14) + 'px'; tip.style.top = Math.max(6, e.clientY - 40) + 'px'; });
fapi.on('cfg', c => { const code = CFG.code; CFG = c; side(); if (c.code !== code) connect(); });
setInterval(side, 30000);
fapi.cfg().then(c => { CFG = c; side(); connect(); });
