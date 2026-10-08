/* 아침 뉴스 브리핑 화면: briefing.json에서 고른 언론사의 기사를 보여 주고, 원고를 한 줄씩 한국어 음성으로 읽는다 (원고 규칙은 js/brief.js) */
(function () {
  const $ = (id) => document.getElementById(id);
  const synth = window.speechSynthesis;
  // 서버가 녹음해 둔 자연스러운 음성(audio/<id>.mp3)을 하나의 audio로 차례로 튼다. 녹음이 없는 줄만 브라우저 음성으로 읽는다.
  // 아이폰 사파리는 처음 한 번 사람이 눌렀을 때 소리를 허락하므로, 같은 audio를 계속 쓴다
  const player = new Audio();
  player.preload = 'auto';
  let unlocked = false;
  const RATES = [0.9, 1.0, 1.15, 1.3];
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* 저장 못 해도 동작에는 지장 없음 */ } },
  };
  let rate = Number(store.get('news.rate')) || 1.0;
  let queue = []; // { text, sec, el }
  let pos = 0;
  let playing = false;
  let gen = 0; // 말하기를 새로 시작할 때마다 바뀐다: 취소된 문장의 onend가 뒤늦게 와도 무시한다
  let voice = null;
  let data = null; // 보고 있는 날의 원고 (오늘 briefing.json 또는 archive/<날짜>.json)
  let today = null; // 오늘 briefing.json
  let enabled = null; // 고른 언론사 id 목록, null이면 기본값
  // 안드로이드 앱의 '오늘 기사 목록'으로 열었다(?app): 재생은 앱의 자막 화면이 맡는다
  const inApp = (() => {
    try { if (new URLSearchParams(location.search).has('app')) sessionStorage.setItem('news.app', '1'); return sessionStorage.getItem('news.app') === '1'; } catch { return false; }
  })();
  const appLink = (title) => `intent://play${title ? `?t=${encodeURIComponent(title)}` : ''}#Intent;scheme=newsclipping;package=io.github.kimyh1981.newsclipping;end`;
  try { enabled = JSON.parse(store.get('news.sources')); } catch { enabled = null; }

  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // 가장 자연스러운 한국어 음성: 신경망 음성(엣지의 'Natural', 애플의 '고품질·Premium')을 먼저, 그다음 구글 음성
  const voiceScore = (v) => (/natural|neural/i.test(v.name) ? 8 : 0) + (/premium|enhanced|고품질/i.test(v.name) ? 4 : 0) + (/google/i.test(v.name) ? 2 : 0) + (v.localService ? 1 : 0);
  function pickVoice() {
    const ko = synth ? synth.getVoices().filter((v) => /^ko/i.test(v.lang)) : [];
    voice = ko.sort((a, b) => voiceScore(b) - voiceScore(a))[0] || null;
  }

  function kstTime(iso) {
    return new Date(iso).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  }

  // 녹음 파일 이름: 줄 글자(앞뒤 공백 뺌)의 SHA-1 앞 16자리 (서버 tools/tts.js, 앱 Brief.audioId와 같은 규칙)
  async function audioId(text) {
    const h = new Uint8Array(await crypto.subtle.digest('SHA-1', new TextEncoder().encode(text.trim())));
    return [...h.slice(0, 8)].map((x) => x.toString(16).padStart(2, '0')).join('');
  }

  async function attachClips(b, lines) {
    const ids = new Set((b.audio && b.audio.ids) || []);
    if (!ids.size || !window.crypto || !crypto.subtle) return;
    const base = (b.audio && b.audio.base) || 'audio/';
    await Promise.all(lines.map(async (line) => {
      const id = await audioId(line.text);
      if (ids.has(id)) line.clip = `${base}${id}.mp3`;
    }));
  }

  function render(b) {
    const age = (Date.now() - Date.parse(b.generatedAt)) / 3600e3;
    $('meta').textContent = `${b.dateLabel} · ${kstTime(b.generatedAt)} 수집` + (b !== today ? ' · 지난 기사' : age > 20 ? ' · 어제 소식일 수 있어요' : '') +
      (b.autoPlay && !b.autoPlay.play ? ` · 오늘은 ${b.autoPlay.reason}이라 차에서 자동 재생은 쉬어요` : '');
    const sections = Brief.select(b, enabled);
    $('list').innerHTML = sections.map((s, si) => `<section><h2>${esc(s.title)}</h2>` + (s.items.length
      ? `<ol>${s.items.map((it, ii) => `<li id="i${si}-${ii}"><a href="${esc(it.link)}" target="_blank" rel="noopener">${esc(it.title)}</a><small>${esc(it.source)}${it.original ? ` · 원문: ${esc(it.original)}` : ''}</small><div class="acts"><button class="go" type="button" data-t="${esc(it.title)}" aria-label="이 기사부터 듣기">▶ 여기부터</button>${it.summary ? `<details><summary>자세히</summary><p class="sum">${esc(it.summary)}</p></details>` : ''}</div></li>`).join('')}</ol>`
      : '<p class="empty">새 소식 없음</p>') + '</section>').join('') || '<p class="empty">고른 언론사가 없어요. 아래에서 언론사를 골라 주세요.</p>';

    // 원고 줄을 화면의 섹션·기사와 짝지어, 읽는 중인 기사를 표시하고 섹션 단위로 건너뛴다
    let sec = -1;
    queue = Brief.script(b, enabled).split('\n').map((t) => t.trim()).filter(Boolean).map((text) => {
      const hs = sections.findIndex((s, i) => i > sec && text.endsWith(`${s.title}입니다.`));
      if (hs >= 0) sec = hs;
      const s = sections[sec];
      const ii = s ? s.items.findIndex((it) => text === Brief.sentence(it.spoken)) : -1;
      return { text, sec, el: ii >= 0 ? $(`i${sec}-${ii}`) : null, summary: ii >= 0 ? s.items[ii].summary || '' : '' };
    });
    attachClips(b, queue).catch(() => {}); // 녹음을 못 찾으면 브라우저 음성으로 읽는다
    // 같은 날 원고를 멈췄던 줄이 있으면 거기서 이어 듣는다 (페이지를 다시 열어도)
    const saved = savedAt(b);
    const at = saved ? queue.findIndex((q) => q.text === saved) : -1;
    pos = at > 0 ? at : 0;
  }

  // 멈춘 줄: 원고(만든 시각)마다 하나, 이 기기에 저장한다. 끝까지 들으면 지운다
  function savedAt(b) {
    try { const v = JSON.parse(store.get('news.at')); return v && v.gen === b.generatedAt ? v.text : null; } catch { return null; }
  }
  function saveAt() {
    if (!data) return;
    store.set('news.at', JSON.stringify({ gen: data.generatedAt, text: queue[pos] && pos > 0 ? queue[pos].text : '' }));
  }

  // 언론사 체크 목록: 섹션마다 언론사를 보여 주고, 고른 것을 이 기기에 저장한다
  function renderPicker(b) {
    const on = new Set(enabled || Brief.defaults(b));
    $('sources').innerHTML = b.sections.map((s) => `<fieldset><legend>${esc(s.title)}</legend>` + s.sources.map((x) =>
      `<label><input type="checkbox" value="${esc(x.id)}"${on.has(x.id) ? ' checked' : ''}> ${esc(x.name)}${x.lang !== 'ko' ? ' <small>번역</small>' : ''}<small>${x.items.length ? '' : ' · 오늘 기사 없음'}</small></label>`).join('') + '</fieldset>').join('');
    $('iosCode').textContent = Brief.iosCode(b, enabled);
    $('pickNote').textContent = enabled ? `${enabled.length}곳을 골랐어요.` : '기본 언론사를 듣고 있어요.';
  }

  function choose(ids) {
    enabled = ids;
    if (ids) store.set('news.sources', JSON.stringify(ids)); else store.set('news.sources', '');
    stop();
    render(data);
    renderPicker(data);
    setPlaying(false);
  }

  $('sources').addEventListener('change', () => choose([...document.querySelectorAll('#sources input:checked')].map((x) => x.value)));
  $('resetSources').onclick = () => choose(null);
  $('copyCode').onclick = () => {
    const code = $('iosCode').textContent;
    const done = () => { $('copyCode').textContent = '복사됨'; setTimeout(() => { $('copyCode').textContent = '복사'; }, 1500); };
    if (navigator.clipboard) navigator.clipboard.writeText(code).then(done, () => {});
  };

  function mark(el) {
    document.querySelectorAll('li.now').forEach((x) => x.classList.remove('now'));
    if (el) { el.classList.add('now'); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
  }

  function setPlaying(on) {
    playing = on;
    $('play').textContent = on ? '⏸ 멈춤' : pos > 0 && pos < queue.length ? '▶ 이어 듣기' : '▶ 듣기';
    $('carPlay').textContent = on ? '⏸ 멈춤' : pos > 0 && pos < queue.length ? '▶ 눌러서 이어 듣기' : '▶ 눌러서 듣기';
  }

  function caption(text) {
    $('capText').textContent = text;
  }

  // 사람이 누른 순간에 audio를 한 번 틀었다 멈춰, 이후 줄을 차례로 틀 수 있게 한다 (아이폰 사파리)
  function unlock() {
    if (unlocked) return;
    unlocked = true;
    const first = queue.find((q) => q.clip);
    if (!first || (queue[pos] && queue[pos].clip)) { unlocked = !!first; return; } // 첫 줄이 녹음이면 바로 그 줄을 트는 것으로 충분
    player.src = first.clip;
    player.muted = true;
    const p = player.play();
    if (p && p.then) p.then(() => { if (player.muted) { player.pause(); player.muted = false; } }, () => { player.muted = false; unlocked = false; });
    else player.muted = false;
  }

  function speak() {
    if (!playing) return;
    if (pos >= queue.length) { pos = 0; saveAt(); mark(null); setPlaying(false); return; }
    const line = queue[pos];
    mark(line.el);
    caption(line.text);
    const g = ++gen;
    const next = () => { if (playing && g === gen) { pos++; speak(); } };
    if (line.clip) {
      player.onended = next;
      player.onerror = () => { if (g === gen) { line.clip = null; speak(); } }; // 녹음을 못 받으면 이 줄은 브라우저 음성으로
      player.muted = false;
      player.src = line.clip;
      player.playbackRate = rate;
      const p = player.play();
      if (p && p.catch) p.catch((e) => { if (g === gen && e && e.name === 'NotAllowedError') setPlaying(false); });
      return;
    }
    if (!synth) { next(); return; }
    const u = new SpeechSynthesisUtterance(Pron.say(line.text));
    u.lang = 'ko-KR';
    if (voice) u.voice = voice;
    u.rate = rate;
    u.onend = next;
    u.onerror = (e) => {
      if (e.error === 'interrupted' || e.error === 'canceled') return;
      setPlaying(false); // 'not-allowed': 화면을 한 번 눌러야 소리를 낼 수 있는 브라우저
    };
    synth.speak(u);
  }

  // '자세히': 지금(또는 방금) 읽은 기사의 요약을 읽고, 다음 헤드라인으로 이어 간다
  function more() {
    let i = pos;
    while (i > 0 && !queue[i].summary && !queue[i].el) i--;
    const line = queue[i];
    if (!synth || !line || !line.summary) return;
    player.pause();
    synth.cancel();
    setPlaying(true);
    mark(line.el);
    caption(line.summary);
    const g = ++gen;
    const u = new SpeechSynthesisUtterance(Pron.say(line.summary));
    u.lang = 'ko-KR';
    if (voice) u.voice = voice;
    u.rate = rate;
    u.onend = () => { if (playing && g === gen) { pos = i + 1; speak(); } };
    synth.speak(u);
  }

  function play() {
    if (!queue.length) return;
    unlock();
    player.pause();
    if (synth) synth.cancel();
    setPlaying(true);
    speak();
  }

  function stop() {
    setPlaying(false);
    gen++;
    player.pause();
    if (synth) synth.cancel();
    saveAt();
  }

  // 자막 화면(큰 글씨)을 띄운다. '목록 보기'로 닫아도 읽기는 이어진다
  function openCar() {
    $('capMeta').textContent = data ? `${data.dateLabel} 뉴스` : '';
    if (queue[pos]) caption(queue[pos].text);
    $('car').hidden = false;
  }

  // 기사 옆 ▶: 그 기사부터 자막 화면에서 읽는다. 앱에서 연 목록(오늘 원고)이면 앱의 자막 화면으로 넘긴다
  function playFrom(li, title) {
    if (inApp && data === today) { location.href = appLink(title); return; }
    let i = queue.findIndex((q) => q.el === li);
    if (i < 0) return;
    if (i > 0 && !queue[i - 1].el && queue[i - 1].text.endsWith('소식입니다.')) i--; // 그 언론사 소개부터
    pos = i;
    openCar();
    play();
  }
  $('list').addEventListener('click', (e) => {
    const b = e.target.closest('button.go');
    if (b) playFrom(b.closest('li'), b.dataset.t);
  });

  function listen() {
    if (playing) { stop(); return; }
    if (inApp && data === today) { location.href = appLink(''); return; }
    openCar();
    play();
  }

  function jump(dir) {
    const cur = queue[pos] ? queue[pos].sec : -1;
    let target;
    if (dir > 0) target = queue.findIndex((q, i) => i > pos && q.sec > cur);
    else {
      const start = queue.findIndex((q) => q.sec === cur);
      const prevSec = start === pos || start < 0 ? cur - 1 : cur;
      target = queue.findIndex((q) => q.sec === prevSec);
    }
    pos = target < 0 ? (dir > 0 ? queue.length : 0) : target;
    if (playing) play(); else { mark(queue[pos] && queue[pos].el); setPlaying(false); saveAt(); }
  }

  $('play').onclick = listen;
  $('carPlay').onclick = () => (playing ? stop() : play());
  $('carNext').onclick = () => jump(1);
  $('carClose').onclick = () => { $('car').hidden = true; };
  // 운전 중에도 쉽게: 차 화면 아무 곳이나 누르면 시작한다 (버튼은 각자 동작)
  $('car').onclick = (e) => { if (!e.target.closest('button') && !playing) play(); };
  $('next').onclick = () => jump(1);
  $('more').onclick = more;
  $('back').onclick = () => jump(-1);
  const showRate = () => { $('rate').textContent = `${rate}×`; };
  showRate();
  $('rate').onclick = () => {
    rate = RATES[(RATES.indexOf(rate) + 1) % RATES.length] || 1.0;
    store.set('news.rate', String(rate));
    showRate();
    if (playing) play();
  };
  // 아이폰 단축어 iCloud 링크: 아이폰에서 한 번 만들어 공유 링크를 여기에 넣는다 (README 참고)
  const SHORTCUT_URL = '';
  if (SHORTCUT_URL) $('shortcutLink').href = SHORTCUT_URL;
  else $('iosLink').innerHTML = '<span class="meta">단축어 링크는 준비 중이에요. 아래대로 직접 만들 수 있어요.</span>';

  if (synth) { pickVoice(); synth.onvoiceschanged = pickVoice; }

  // 차 화면(?car): 평일 아침 6~8시이고 쉬는 날이 아니면 큰 '눌러서 듣기' 화면을 띄운다. 다른 때에는 보통 화면
  function carTime(b) {
    const kst = new Date(Date.now() + 9 * 3600e3);
    const h = kst.getUTCHours();
    return (!b.autoPlay || b.autoPlay.play) && h >= 6 && h < 8;
  }

  // 지난 7일 원고: 서버가 archive/에 남겨 둔 날짜를 골라 그날 기사 목록을 본다 (첫 줄이 오늘)
  function show(b) {
    stop();
    data = b;
    render(b);
    renderPicker(b);
    setPlaying(false);
  }

  function loadDays() {
    fetch('archive/index.json', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((idx) => {
        const days = (idx && idx.days) || [];
        if (!days.length) return;
        $('day').innerHTML = days.map((d, i) => `<option value="${i ? esc(d.ymd) : ''}">${esc(d.label)}${i ? '' : ' (오늘)'}</option>`).join('');
        $('dayNote').textContent = days.length < 2 ? '지난 날 기사는 내일부터 하루씩 쌓여요 (최근 7일)' : '최근 7일';
        $('days').hidden = false;
      })
      .catch(() => {});
    $('day').onchange = () => {
      const ymd = $('day').value;
      if (!ymd) { show(today); return; }
      fetch(`archive/${ymd}.json`, { cache: 'no-store' })
        .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
        .then(show)
        .catch(() => { $('meta').textContent = '그날 기사를 불러오지 못했어요.'; });
    };
  }

  fetch('briefing.json', { cache: 'no-store' })
    .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
    .then((b) => {
      data = b;
      today = b;
      loadDays();
      render(b);
      renderPicker(b);
      setPlaying(false);
      const q = new URLSearchParams(location.search);
      if (q.has('car') && carTime(b)) {
        $('capMeta').textContent = `${b.dateLabel} 아침 뉴스`;
        $('car').hidden = false;
      }
      if (q.has('autoplay')) play();
    })
    .catch(() => { $('meta').textContent = '오늘 브리핑을 아직 만들지 못했어요. 잠시 후 다시 열어 주세요.'; });
})();
