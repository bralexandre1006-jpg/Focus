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
    const m = (l, v, gl, c) => `<div>${l}<span class="bar"><i style="width:${Math.min(100, v / gl * 100)}%;background:${c}"></i></span><b class="num">${fmt(v)}</b></div>`;
    return `${T('Calories')}<div class="kc"><div class="mini">${arc(p, 82, 9, 'a2', false).replace(/var\(--acc\)/, '#FFC44A').replace(/var\(--acc2\)/, '#FF6B4A')}<b class="num">${fmt(t.kcal)}</b></div>
      <div><span class="pill ${left >= 0 ? 'ok' : 'bad'}">${left >= 0 ? 'reste ' + fmt(left) : '+' + fmt(-left)}</span><div class="sub">sur ${fmt(G.kcal)} kcal</div></div></div>
      <div class="macs">${m('Prot.', t.p, G.protein || 100, '#FF5C8A')}${m('Gluc.', t.c, G.kcal * .45 / 4, '#FFC44A')}${m('Lip.', t.f, G.kcal * .3 / 9, '#5AB8FF')}</div>`; },
  weight: d => { const G = d.goals, w = lastWeight(d), df = w && G.start ? w - G.start : null, p = G.start && G.target && w ? Math.max(0, Math.min(1, (G.start - w) / (G.start - G.target))) : 0;
    return `${T('Poids')}<div class="big num" style="font-size:42px;margin-top:22px">${w ? f1(w) : '—'}<small>kg</small></div>
      <div style="margin-top:8px">${df != null ? `<span class="pill ${df <= 0 ? 'ok' : 'bad'}">${df <= 0 ? '−' : '+'}${f1(Math.abs(df))} kg</span>` : ''}</div>
      <div class="bar" style="margin-top:16px"><i style="width:${p * 100}%;background:linear-gradient(90deg,#4FE3D0,#6BD36E)"></i></div>
      <div class="sub">${G.target && w ? `${f1(Math.abs(w - G.target))} kg avant ${f1(G.target)} kg` : ''}</div>`; },
  week: d => { const ks = Array.from({ length: 7 }, (_, i) => addDays(today(), i - 6)), v = ks.map(k => D(d, k).steps), mx = Math.max(d.goals.steps, ...v, 1);
    return `${T('Pas · 7 jours')}<div class="big num" style="font-size:26px;margin-top:10px">${fmt(v.reduce((a, b) => a + b, 0) / 7)}<small>/ jour</small></div>
      <div class="wk" style="height:96px">${ks.map((k, i) => `<div><i class="${i === 6 ? 'now' : v[i] >= d.goals.steps ? 'ok' : ''}" style="height:${Math.max(8, v[i] / mx * 74)}px"></i><span class="${i === 6 ? 'now' : ''}">${parseKey(k).toLocaleDateString('fr-BE', { weekday: 'narrow' }).toUpperCase()}</span></div>`).join('')}</div>`; },
  streak: d => `${T('Série')}<div class="stq"><div class="fl"><svg viewBox="0 0 24 24">${FIRE}</svg></div><div class="big num" style="font-size:44px">${d.streak || 0}</div><div class="sub" style="margin:0">jour${d.streak > 1 ? 's' : ''} d'affilée</div></div>`,
  all: d => { const G = d.goals, t = D(d, today()), w = lastWeight(d), ks = Array.from({ length: 7 }, (_, i) => addDays(today(), i - 6)), v = ks.map(k => D(d, k).steps), mx = Math.max(G.steps, ...v, 1), df = w && G.start ? w - G.start : null;
    return `<div class="wh">${T('Focus · aujourd\'hui')}<span class="pill"><svg viewBox="0 0 24 24" style="width:12px;height:12px;color:#FFC07A;stroke-width:2.4">${FIRE}</svg>${d.streak || 0} j</span></div>
      <div class="allg"><div class="ring" style="width:112px;height:112px">${arc(t.steps / G.steps, 112, 10, 'a3')}<div class="c"><b class="big num" style="font-size:22px">${fmt(t.steps)}</b><small>pas</small></div></div>
        <div class="allc"><div><div class="t" style="font-size:9.5px"><i style="background:#FF6B4A;box-shadow:0 0 8px #FF6B4A"></i>Calories</div><b class="big num" style="font-size:24px">${fmt(t.kcal)}<small>/ ${fmt(G.kcal)}</small></b>
          <div class="bar" style="margin-top:6px"><i style="width:${Math.min(100, t.kcal / G.kcal * 100)}%;background:linear-gradient(90deg,#FFC44A,#FF6B4A)"></i></div>
          <div class="sub" style="font-size:10.5px">P ${t.p} · G ${t.c} · L ${t.f} g</div></div>
          <div><div class="t" style="font-size:9.5px"><i style="background:#6BD36E;box-shadow:0 0 8px #6BD36E"></i>Poids</div><b class="big num" style="font-size:24px">${w ? f1(w) : '—'}<small>kg</small></b>${df != null ? ` <span class="pill ${df <= 0 ? 'ok' : 'bad'}">${df <= 0 ? '−' : '+'}${f1(Math.abs(df))}</span>` : ''}</div></div></div>
      <div class="wk" style="height:96px;margin-top:10px">${ks.map((k, i) => `<div><i class="${i === 6 ? 'now' : v[i] >= G.steps ? 'ok' : ''}" style="height:${Math.max(6, v[i] / mx * 70)}px;border-radius:8px"></i><span class="${i === 6 ? 'now' : ''}">${parseKey(k).toLocaleDateString('fr-BE', { weekday: 'narrow' }).toUpperCase()}</span></div>`).join('')}</div>`; }
};
const TINT = { steps: 'var(--acc)', kcal: '#FF6B4A', weight: '#6BD36E', week: '#7C8CFF', streak: '#FF8A3D', all: 'var(--acc)' };
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
