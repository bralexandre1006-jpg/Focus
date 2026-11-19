const TYPE = new URLSearchParams(location.search).get('type') || 'steps';
const g = document.getElementById('g');
const FIRE = '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>';
const T = t => `<div class="t"><i></i>${t}</div>`;
const arc = (p, sz, sw, grad, glow = true) => { const r = (sz - sw) / 2, C = 2 * Math.PI * r;
  return `<svg viewBox="0 0 ${sz} ${sz}"><defs><linearGradient id="${grad}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--acc)"/><stop offset="1" stop-color="var(--acc2)"/></linearGradient></defs>
    <circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="rgba(255,255,255,.14)" stroke-width="${sw}"/>
    <circle cx="${sz / 2}" cy="${sz / 2}" r="${r}" stroke="url(#${grad})" stroke-width="${sw}" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - Math.min(1, p))}" style="${glow ? 'filter:drop-shadow(0 0 7px color-mix(in srgb,var(--acc) 70%,transparent));' : ''}transition:stroke-dashoffset 1.1s cubic-bezier(.2,.8,.2,1)"/></svg>`; };
const R = {
  steps: d => { const G = d.goals, s = D(d, today()).steps, p = s / G.steps;
    return `${T('Pas du jour')}<div class="sr"><div class="ring">${arc(p, 128, 11, 'a1')}<div class="c"><b class="big num">${fmt(s)}</b><small>${p >= 1 ? 'Objectif atteint' : 'sur ' + fmt(G.steps)}</small></div></div>
      <div class="sub">≈ ${f1(s * .00075)} km · ${fmt(s * .04)} kcal</div></div>`; },
  kcal: d => { const G = d.goals, t = D(d, today()), p = t.kcal / G.kcal, left = G.kcal - t.kcal;
    const m = (l, v, gl, c) => `<div>${l}<span class="bar"><i style="width:${Math.min(100, v / gl * 100)}%;background:${c}"></i></span><b class="num">${fmt(v)} g</b></div>`;
    return `<div class="wh">${T('Nutrition')}<span class="pill ${left >= 0 ? 'ok' : 'bad'}">${left >= 0 ? 'reste ' + fmt(left) : '+' + fmt(-left)} kcal</span></div>
      <div class="kc"><div class="mini">${arc(p, 74, 8, 'a2', false).replace(/var\(--acc\)/, '#FFC44A').replace(/var\(--acc2\)/, '#FF6B4A')}<b class="num">${fmt(t.kcal)}</b></div>
      <div class="macs">${m('Prot.', t.p, G.protein || 100, '#FF5C8A')}${m('Gluc.', t.c, G.kcal * .45 / 4, '#FFC44A')}${m('Lip.', t.f, G.kcal * .3 / 9, '#5AB8FF')}</div></div>`; },
  weight: d => { const G = d.goals, w = lastWeight(d), df = w && G.start ? w - G.start : null, p = G.start && G.target && w ? Math.max(0, Math.min(1, (G.start - w) / (G.start - G.target))) : 0;
    return `${T('Poids')}<div class="big num" style="font-size:42px;margin-top:22px">${w ? f1(w) : '—'}<small>kg</small></div>
      <div style="margin-top:8px">${df != null ? `<span class="pill ${df <= 0 ? 'ok' : 'bad'}">${df <= 0 ? '−' : '+'}${f1(Math.abs(df))} kg</span>` : ''}</div>
      <div class="bar" style="margin-top:16px"><i style="width:${p * 100}%;background:linear-gradient(90deg,#4FE3D0,#6BD36E)"></i></div>
      <div class="sub">${G.target && w ? `${f1(Math.abs(w - G.target))} kg avant ${f1(G.target)} kg` : ''}</div>`; },
  week: d => { const ks = Array.from({ length: 7 }, (_, i) => addDays(today(), i - 6)), v = ks.map(k => D(d, k).steps), mx = Math.max(d.goals.steps, ...v, 1);
    const av = v.reduce((a, b) => a + b, 0) / 7;
    return `<div class="wh">${T('Pas · 7 jours')}<div style="text-align:right"><b class="num" style="font-size:18px">${fmt(av)}</b><div class="sub" style="margin-top:0;font-size:10px">par jour</div></div></div>
      <div class="wk">${ks.map((k, i) => `<div><i class="${i === 6 ? 'now' : v[i] >= d.goals.steps ? 'ok' : ''}" style="height:${Math.max(10, v[i] / mx * 70)}px" title="${fmt(v[i])}"></i><span class="${i === 6 ? 'now' : ''}">${parseKey(k).toLocaleDateString('fr-BE', { weekday: 'narrow' }).toUpperCase()}</span></div>`).join('')}</div>`; },
  streak: d => `<div class="st"><div class="fl"><svg viewBox="0 0 24 24">${FIRE}</svg></div><div><div class="t" style="margin-bottom:6px"><i></i>Série</div><div class="big num" style="font-size:36px">${d.streak || 0}<small>jour${d.streak > 1 ? 's' : ''}</small></div></div></div>`
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
const lock = c => document.body.classList.toggle('locked', !!c.locked);
fapi.cfg().then(c => { CODE = c.code; lock(c); start(c.code); });
fapi.on('cfg', c => { lock(c); if (c.code !== CODE) { CODE = c.code; start(c.code); } });
