/* 아침 뉴스 브리핑 화면: briefing.json에서 고른 언론사의 기사를 보여 주고, 원고를 한 줄씩 한국어 음성으로 읽는다 (원고 규칙은 js/brief.js) */
(function () {
  const $ = (id) => document.getElementById(id);
  const synth = window.speechSynthesis;
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
  let data = null; // 오늘 briefing.json
  let enabled = null; // 고른 언론사 id 목록, null이면 기본값
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

  function render(b) {
    const age = (Date.now() - Date.parse(b.generatedAt)) / 3600e3;
    $('meta').textContent = `${b.dateLabel} · ${kstTime(b.generatedAt)} 수집` + (age > 20 ? ' · 어제 소식일 수 있어요' : '') +
      (b.autoPlay && !b.autoPlay.play ? ` · 오늘은 ${b.autoPlay.reason}이라 차에서 자동 재생은 쉬어요` : '');
    const sections = Brief.select(b, enabled);
    $('list').innerHTML = sections.map((s, si) => `<section><h2>${esc(s.title)}</h2>` + (s.items.length
      ? `<ol>${s.items.map((it, ii) => `<li id="i${si}-${ii}"><a href="${esc(it.link)}" target="_blank" rel="noopener">${esc(it.title)}</a><small>${esc(it.source)}${it.original ? ` · 원문: ${esc(it.original)}` : ''}</small>${it.summary ? `<details><summary>자세히</summary><p class="sum">${esc(it.summary)}</p></details>` : ''}</li>`).join('')}</ol>`
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
    pos = 0;
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
  }

  function speak() {
    if (!playing) return;
    if (pos >= queue.length) { pos = 0; mark(null); setPlaying(false); return; }
    const line = queue[pos];
    mark(line.el);
    const g = ++gen;
    const u = new SpeechSynthesisUtterance(line.text);
    u.lang = 'ko-KR';
    if (voice) u.voice = voice;
    u.rate = rate;
    u.onend = () => { if (playing && g === gen) { pos++; speak(); } };
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
    synth.cancel();
    setPlaying(true);
    mark(line.el);
    const g = ++gen;
    const u = new SpeechSynthesisUtterance(line.summary);
    u.lang = 'ko-KR';
    if (voice) u.voice = voice;
    u.rate = rate;
    u.onend = () => { if (playing && g === gen) { pos = i + 1; speak(); } };
    synth.speak(u);
  }

  function play() {
    if (!synth || !queue.length) return;
    synth.cancel();
    setPlaying(true);
    speak();
  }

  function stop() {
    setPlaying(false);
    if (synth) synth.cancel();
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
    if (playing) play(); else { mark(queue[pos] && queue[pos].el); setPlaying(false); }
  }

  $('play').onclick = () => (playing ? stop() : play());
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
  else $('iosLink').innerHTML = '<span class="meta">단축어 링크를 준비 중입니다. 그동안은 README의 "아이폰 단축어 만들기"대로 직접 만들 수 있습니다.</span>';

  if (!synth) $('play').disabled = true;
  else { pickVoice(); synth.onvoiceschanged = pickVoice; }

  fetch('briefing.json', { cache: 'no-store' })
    .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
    .then((b) => {
      data = b;
      render(b);
      renderPicker(b);
      if (new URLSearchParams(location.search).has('autoplay')) play();
    })
    .catch(() => { $('meta').textContent = '오늘 브리핑을 아직 만들지 못했어요. 잠시 후 다시 열어 주세요.'; });
})();
