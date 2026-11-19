const TYPE = new URLSearchParams(location.search).get('type') || 'steps';
const g = document.getElementById('g');
const ICO = {
  steps: '<path d="M4 16c0-3 1.5-6 3.5-6S10 12 10 14s-1 4-3 4-3-.5-3-2zM14 8c0-3 1.5-6 3.5-6S20 4 20 6s-1 4-3 4-3-.5-3-2z"/>',
  fire: '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>',
  scale: '<rect x="3" y="4" width="18" height="16" rx="4"/><path d="M8 9a5 5 0 0 1 8 0M12 9l1.5-2"/>',
  food: '<path d="M7 2v20M4 2v6a3 3 0 0 0 6 0V2M17 22V2c-2.5 1-4 3.5-4 7v5h4"/>',
  cal: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M3 9h18M8 2v4M16 2v4"/>'
};
const lab = (k, t) => `<div class="lab"><svg viewBox="0 0 24 24">${ICO[k]}</svg>${t}</div>`;
const R = {
  steps: d => { const G = d.goals, s = D(d, today()).steps, p = Math.min(1, s / G.steps), r = 50, C = 2 * Math.PI * r;
    return `${lab('steps', 'Pas du jour')}<div class="ring"><svg viewBox="0 0 118 118"><defs><linearGradient id="rg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--acc)"/><stop offset="1" stop-color="var(--acc2)"/></linearGradient></defs>
      <circle cx="59" cy="59" r="${r}" stroke="rgba(255,255,255,.18)" stroke-width="10"/><circle cx="59" cy="59" r="${r}" stroke="url(#rg)" stroke-width="10" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - p)}" style="transition:stroke-dashoffset .9s;filter:drop-shadow(0 0 6px var(--acc))"/></svg>
      <div><b class="big num">${fmt(s)}</b><span class="sub">${Math.round(p * 100)} % · ${fmt(G.steps)}</span></div></div>`; },
  kcal: d => { const G = d.goals, t = D(d, today()), p = Math.min(1, t.kcal / G.kcal);
    const m = (l, v, gl, c) => `<div>${l}<b class="num">${fmt(v)} g</b><i><s style="width:${Math.min(100, v / gl * 100)}%;background:${c}"></s></i></div>`;
    return `${lab('food', 'Calories')}<div class="big num" style="margin-top:8px">${fmt(t.kcal)}<small>/ ${fmt(G.kcal)} kcal</small></div>
      <div class="bar"><i style="width:${p * 100}%;background:linear-gradient(90deg,#FFC44A,#FF6B4A)"></i></div>
      <div class="macs">${m('Prot.', t.p, G.protein || 100, '#FF5C8A')}${m('Gluc.', t.c, G.kcal * .45 / 4, '#FFC44A')}${m('Lip.', t.f, G.kcal * .3 / 9, '#5AB8FF')}</div>`; },
  weight: d => { const G = d.goals, w = lastWeight(d), df = w && G.start ? w - G.start : null, p = G.start && G.target && w ? Math.max(0, Math.min(1, (G.start - w) / (G.start - G.target))) : 0;
    return `${lab('scale', 'Poids')}<div class="big num" style="margin-top:26px;font-size:38px">${w ? f1(w) : '—'}<small>kg</small></div>
      <div class="sub">${df != null ? `${df <= 0 ? '−' : '+'}${f1(Math.abs(df))} kg depuis le départ` : ''}</div>
      <div class="bar" style="margin-top:16px"><i style="width:${p * 100}%;background:linear-gradient(90deg,#4FE3D0,#6BD36E)"></i></div>
      <div class="sub">${G.target && w ? `${f1(Math.abs(w - G.target))} kg restants` : ''}</div>`; },
  week: d => { const ks = Array.from({ length: 7 }, (_, i) => addDays(today(), i - 6)), v = ks.map(k => D(d, k).steps), mx = Math.max(d.goals.steps, ...v, 1);
    return `${lab('cal', 'Pas · 7 jours')}<div class="wk">${ks.map((k, i) => `<div><i class="${v[i] >= d.goals.steps || i === 6 ? 'on' : ''}" style="height:${Math.max(8, v[i] / mx * 72)}px" title="${fmt(v[i])}"></i><span>${parseKey(k).toLocaleDateString('fr-BE', { weekday: 'narrow' })}</span></div>`).join('')}</div>`; },
  streak: d => `<div class="row"><svg class="flame" viewBox="0 0 24 24">${ICO.fire}</svg><div><div class="big num" style="font-size:34px">${d.streak || 0}<small>jour${d.streak > 1 ? 's' : ''}</small></div><div class="sub">de série</div></div></div>`
};
const TINT = { steps: 'var(--acc)', kcal: '#FF6B4A', weight: '#6BD36E', week: '#7C8CFF', streak: '#FF8A3D' };
g.style.setProperty('--tint', TINT[TYPE]);
document.getElementById('x').onclick = () => fapi.hideWidget(TYPE);
g.ondblclick = () => fapi.win('open');
let unsub = () => {};
const start = code => { unsub(); unsub = subscribe(code, (d, st) => {
  if (!d) { g.innerHTML = `<div class="off">${st === 'nocode' ? 'Ouvre Focus pour<br>connecter ton téléphone' : st === 'noserver' ? 'Synchro non configurée' : 'Connexion…'}</div>`; return; }
  const a = ACC[d.accent] || ACC.lime; document.documentElement.style.setProperty('--acc', a[0]); document.documentElement.style.setProperty('--acc2', a[1]);
  g.innerHTML = R[TYPE](d); }); };
let CODE = null;
fapi.cfg().then(c => { CODE = c.code; start(c.code); });
fapi.on('cfg', c => { if (c.code !== CODE) { CODE = c.code; start(c.code); } });
