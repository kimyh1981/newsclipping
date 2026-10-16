/*
 * 원고 줄을 신경망 음성으로 미리 녹음해 둔다. 녹음하는 곳(engine)은 둘 중 하나:
 *  - edge (기본, 무료·키 없음): MS 엣지 '소리 내어 읽기' 음성(edge-tts, tools/edge_tts_batch.py). 비공식이라 막히면 그날은 폰 음성
 *  - google: 구글 클라우드 Text-to-Speech(Chirp 3 HD), 저장소 비밀값 GOOGLE_TTS_KEY가 있을 때 (카드 등록 필요)
 * 앱은 원고 줄(과 전체 듣기 문장 say)마다 그 글자 그대로 녹음된 파일이 있으면 그 파일을, 없으면 폰 음성으로 읽는다.
 *  - 파일 이름은 줄 글자의 SHA-1 앞 16자리 (앱 Brief.audioId와 같은 규칙)
 *  - 하루 글자 수 한도(TTS_DAILY_CHARS) 안에서 인사·연결 말 → 헤드라인 → 기본 언론사 전체 듣기 문장 순으로 녹음한다
 *  - 지난 실행에서 녹음한 파일(캐시)은 다시 녹음하지 않는다: 하루 두 번 배포해도 새 기사만 돈이 든다
 * TTS_ENGINE=off면 아무것도 하지 않는다.
 */
const { spawnSync } = require('child_process');
const os = require('os');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const brief = require('../js/brief.js');

const pron = require('../js/pron.js');
const VOICE = 'ko-KR-Chirp3-HD-Aoede';
// 다국어 신경망 음성: 우리말은 자연스럽게, 기사 속 영어는 미국식 영어 발음으로, 다른 나라 말은 그 나라 말로 읽는다
// (주인 요청 2026-10-14: 유튜브 AI 음성처럼 자연스럽게, 영어는 미국식으로). 이 음성이 막히면 인준 음성으로 다시 녹음한다
const EDGE_VOICE = 'ko-KR-HyunsuMultilingualNeural';
const EDGE_FALLBACK = 'ko-KR-InJoonNeural'; // 차분한 남성 뉴스 톤 (주인이 고름, 2026-10-14)
const multilingual = (v) => /Multilingual/.test(v);
const EN_VOICE = 'en-US-AndrewNeural'; // 농업영어의 영어 줄 (edge만, 배우는 사람에게 맞게 조금 천천히)
const EN_RATE = '-10%';
const ENDPOINT = 'https://texttospeech.googleapis.com/v1/text:synthesize';

// 녹음 뒤 다듬기(ffmpeg): 차 스피커에서 'ㅅ' 소리가 날카롭지 않게 고음을 낮추고 목소리 몸통을 조금 올린 뒤 스테레오로.
// 녹음은 한쪽 채널(모노)이라 폰에서 효과를 걸면 왼쪽 스피커로만 나오는 차가 있었다 (2026-10-08). 바꾸면 SHAPE를 올린다
const SHAPE = 's1';
const SHAPE_FILTER = 'equalizer=f=250:t=o:w=1.3:g=2,highshelf=f=2500:g=-4.5,highshelf=f=9000:g=-4.5';

function shape(file) {
  const tmp = `${file}.shape.mp3`;
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', file, '-af', SHAPE_FILTER, '-ac', '2', '-ar', '24000', '-b:a', '64k', tmp], { timeout: 30e3 });
  if (r.status === 0 && fs.existsSync(tmp) && fs.statSync(tmp).size > 0) { fs.renameSync(tmp, file); return true; }
  fs.rmSync(tmp, { force: true });
  return false;
}

const audioId = (text) => crypto.createHash('sha1').update(text.trim(), 'utf8').digest('hex').slice(0, 16);

// 앱이 읽을 수 있는 줄을 중요한 순서대로: [글자, 묶음, 영어 줄이면 true] (묶음별로 한도를 나눠 쓴다)
function wanted(b) {
  const date = b.dateLabel;
  const out = [];
  const add = (text, kind, en = false) => { if (text && text.trim()) out.push([text.trim(), kind, en]); };
  // 앱은 듣는 시각에 따라 아침(출근길)·저녁(퇴근길) 인사를 고른다: 둘 다 녹음해 둔다
  const evening = b.edition === 'evening';
  add(brief.opening(date, evening), 'fixed');
  add(brief.closing(date, evening), 'fixed');
  add(brief.opening(date, !evening), 'fixed');
  add(brief.closing(date, !evening), 'fixed');
  add(brief.EMPTY, 'fixed');
  // 다음 날 서버 실행이 늦거나 빠지면 앱이 이 원고를 다음 날 날짜로 읽는다: 그 인사·맺음말도 녹음해 둔다
  const next = brief.koreanDate(Date.parse(b.generatedAt) + 24 * 3600e3);
  for (const e of [false, true]) { add(brief.opening(next, e), 'fixed'); add(brief.closing(next, e), 'fixed'); }
  add(brief.STALE, 'fixed');
  add(brief.noNews(date, false), 'fixed');
  add(brief.noNews(date, true), 'fixed');
  for (const sec of b.sections) {
    add(brief.intro(sec.title, 0, 3), 'fixed'); // 먼저 ~부터 전해 드립니다.
    add(brief.intro(sec.title, 1, 3), 'fixed'); // 다음은 ~입니다.
    add(brief.intro(sec.title, 2, 4), 'fixed'); // 이어서 ~입니다.
    add(brief.intro(sec.title, 2, 3), 'fixed'); // 끝으로 ~입니다.
  }
  const items = b.sections.flatMap((sec) => sec.sources.flatMap((src) => src.items.map((it) => ({ sec, src, it }))));
  for (const { sec, it } of items) if (sec.perSourceLabel) add(`${it.source} 소식입니다.`, 'fixed');
  for (const { it } of items) add(brief.sentence(it.spoken), 'headline');
  // 전체 듣기 본문: 기본 언론사 기사부터, 그다음 언론사를 다 골랐을 때 원고에 들 기사까지 모두 녹음한다.
  // 본문 일부만 녹음돼 있으면 앱이 그 기사를 폰 음성으로 읽어 목소리가 바뀐다 (2026-10-13 주인 요청: 같은 목소리로)
  const all = b.sections.flatMap((s) => s.sources.map((x) => x.id));
  const picked = [...new Set([...brief.select(b), ...brief.select(b, all)].flatMap((s) => s.items))];
  for (const it of picked) for (const s of it.say || []) add(s, 'body');
  // 제품 공부: 회차마다 기기가 고르므로 모든 회를 녹음해 둔다 (한 번 녹음하면 캐시로 계속 쓴다)
  for (const ep of (b.lessons && b.lessons.product && b.lessons.product.episodes) || []) {
    add(brief.lessonIntro(ep.n, ep.title), 'lesson');
    for (const line of ep.lines) add(line, 'lesson');
  }
  // 농업영어: 영어 줄은 영어 목소리로, 한국어 풀이 줄은 같은 한국어 목소리로
  for (const ep of (b.lessons && b.lessons.english && b.lessons.english.episodes) || []) {
    for (const line of ep.lines) add(line.en || line.ko, 'lesson', !!line.en);
  }
  const seen = new Set();
  return out.filter(([t]) => (seen.has(t) ? false : seen.add(t)));
}

async function synthesize(text, key, voice) {
  const res = await fetch(`${ENDPOINT}?key=${encodeURIComponent(key)}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ input: { text }, voice: { languageCode: 'ko-KR', name: voice }, audioConfig: { audioEncoding: 'MP3', sampleRateHertz: 24000 } }),
    signal: AbortSignal.timeout(30e3),
  });
  if (!res.ok) {
    const msg = ((await res.json().catch(() => null))?.error?.status) || '';
    const err = new Error(`HTTP ${res.status}${msg ? ' ' + msg : ''}`);
    err.status = res.status;
    throw err;
  }
  const data = await res.json();
  if (!data.audioContent) throw new Error('음성 없음');
  return Buffer.from(data.audioContent, 'base64');
}

// 타입캐스트(유료, 주인이 한국어 블라인드 비교에서 고름 2026-10-16): 비밀값 TYPECAST_API_KEY. 1글자 = 1크레딧
const TC_VOICE = '자인'; // 이름으로 적으면 목소리 목록에서 voice_id(tc_...)를 찾는다
const TC_MODEL = 'ssfm-v30';
const TC_API = 'https://api.typecast.ai';

async function tcError(res) {
  const body = await res.text().catch(() => '');
  const err = new Error(`HTTP ${res.status}${body ? ' ' + body.slice(0, 80) : ''}`);
  err.status = res.status;
  return err;
}

async function synthTypecast(text, key, voiceId) {
  const res = await fetch(`${TC_API}/v1/text-to-speech`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'X-API-KEY': key },
    body: JSON.stringify({ text, model: TC_MODEL, voice_id: voiceId, language: 'kor', output: { audio_format: 'mp3' } }),
    signal: AbortSignal.timeout(60e3),
  });
  if (!res.ok) throw await tcError(res);
  const buf = Buffer.from(await res.arrayBuffer());
  if (!buf.length) throw new Error('음성 없음');
  return buf;
}

// 목소리 이름(한국어·영어) → voice_id. 이미 id면 그대로
async function typecastVoice(key, name) {
  if (/^(tc|uc)_/.test(name)) return name;
  const res = await fetch(`${TC_API}/v3/voices?model=${TC_MODEL}`, { headers: { 'X-API-KEY': key }, signal: AbortSignal.timeout(30e3) });
  if (!res.ok) throw await tcError(res);
  const list = await res.json();
  const hit = (Array.isArray(list) ? list : list.voices || []).find((v) => v.voice_name && (v.voice_name.kor === name || v.voice_name.eng === name));
  if (!hit) { const err = new Error(`목소리 '${name}'를 목록에서 못 찾음`); err.status = 404; throw err; }
  return hit.voice_id;
}

// edge-tts로 여러 줄을 한 번에 녹음: { id: null(성공) | 오류 }. 파이썬이나 edge-tts가 없으면 예외
function edgeBatch(jobs, voice, rate = '+0%') {
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'edge-')), 'jobs.json');
  fs.writeFileSync(tmp, JSON.stringify(jobs));
  const r = spawnSync('python3', [path.join(__dirname, 'edge_tts_batch.py'), tmp, voice, rate], { encoding: 'utf8', maxBuffer: 16 << 20, timeout: 12 * 60e3 });
  if (r.error?.code === 'ETIMEDOUT') return {}; // 시간이 다 되면 그때까지 끝난 파일만 쓴다
  if (r.status !== 0) throw new Error((r.stderr || r.error?.message || `종료 코드 ${r.status}`).trim().split('\n').pop());
  return JSON.parse(r.stdout);
}

// out 폴더에 audio/<id>.mp3를 만들고, 녹음된 줄의 id 목록을 돌려준다 (briefing.json의 audio.ids)
// fallback: 'edge'면 유료 음성(구글·타입캐스트)이 한도·거절·시간 초과로 못 한 줄(과 영어 줄)을 엣지 음성으로 채운다.
// 2026-10-16: 구글 키를 넣자 2만 5천 자 한도와 HTTP 429(분당 요청 한도)로 203줄만 녹음되고 나머지 2천여 줄이 폰 음성이 됐다
async function record(b, opts) {
  const { out, key = '', cache, log, run, batch = edgeBatch, wait = (ms) => new Promise((r) => setTimeout(r, ms)) } = opts;
  const engine = opts.engine || (key ? 'google' : 'off');
  const cloud = engine === 'google' || engine === 'typecast';
  if (engine === 'off' || (cloud && !key)) return null;
  const synth = opts.synth || (engine === 'typecast' ? synthTypecast : synthesize);
  let voice = opts.voice || (engine === 'edge' ? EDGE_VOICE : engine === 'typecast' ? TC_VOICE : VOICE);
  // 타입캐스트는 한 판(약 10만 자)을 다 녹음할 수 있게. 돈이 드는 만큼 TTS_DAILY_CHARS로 줄일 수 있다
  const budget = opts.budget || (engine === 'edge' ? 250000 : engine === 'typecast' ? 120000 : 25000); // edge는 돈이 들지 않지만 배포 시간을 넘기지 않게
  const fill = cloud && opts.fallback === 'edge';
  // 유료 음성이 느려도 새벽 원고가 늦지 않게: 이 시간이 지나면 남은 줄은 엣지로
  const deadline = Date.now() + (opts.minutes || 20) * 60e3;
  let stop = '';
  if (engine === 'typecast') {
    try { voice = await (opts.findVoice || typecastVoice)(key, voice); } catch (err) {
      log.push(`음성 녹음: 타입캐스트 목소리를 못 정함 (${err.message})`);
      if (!fill) return null;
      stop = '키';
    }
  }
  const dir = path.join(out, 'audio');
  fs.mkdirSync(dir, { recursive: true });
  if (cache) fs.mkdirSync(cache, { recursive: true });
  const ids = [];
  let spent = 0;
  let fresh = 0;
  const todo = [];
  const rest = []; // 구글이 못 하는 줄: [글자, 영어 줄이면 true] → 엣지로
  const keep = new Set();
  const nameOf = (v, id, en) => (en ? `${EN_VOICE}-${SHAPE}-${id}.mp3` : `${v}-p${pron.VERSION}-${SHAPE}-${id}.mp3`); // 읽는 규칙을 바꾸면 다시 녹음 (영어 줄은 규칙을 안 거친다)
  const fromCache = (name, id) => {
    const cached = cache && path.join(cache, name);
    if (!cached || !fs.existsSync(cached)) return false;
    keep.add(name);
    fs.copyFileSync(cached, path.join(dir, `${id}.mp3`));
    ids.push(id);
    return true;
  };
  for (const [text, kind, en] of wanted(b)) {
    const id = audioId(text);
    if (en && engine !== 'edge') { if (fill && !fromCache(nameOf(EDGE_VOICE, id, true), id)) rest.push([text, true]); continue; } // 영어 목소리는 edge에만 있다: 없으면 앱이 폰 영어 음성으로 읽는다
    const name = nameOf(voice, id, en);
    const cached = cache && path.join(cache, name);
    keep.add(name);
    if (fromCache(name, id)) continue;
    if (spent + text.length > budget) {
      if (fill) { if (!fromCache(nameOf(EDGE_VOICE, id, false), id)) rest.push([text, false]); continue; }
      if (kind === 'fixed' || kind === 'headline') stop = '한도';
      continue;
    }
    spent += text.length;
    todo.push(en
      ? { id, text, file: path.join(dir, `${id}.mp3`), cached, voice: EN_VOICE, rate: EN_RATE }
      : { id, text: pron.say(text, { english: multilingual(voice) }), orig: text, file: path.join(dir, `${id}.mp3`), cached }); // 이름은 쓴 글로, 녹음은 들리는 대로
  }
  let unshaped = 0;
  const done = (job) => {
    if (!shape(job.file)) unshaped++;
    if (job.cached) fs.copyFileSync(job.file, job.cached);
    ids.push(job.id);
    fresh++;
  };
  const viaEdge = (jobs, v) => {
    try {
      const res = batch(jobs.map(({ id, text, file, voice: jv, rate }) => ({ id, text, file, ...(jv ? { voice: jv, rate } : {}) })), v);
      const errors = [];
      const failed = [];
      const ok = (job) => fs.existsSync(job.file) && fs.statSync(job.file).size > 0;
      for (const job of jobs) {
        if (ok(job) && !res[job.id]) done(job);
        else { fs.rmSync(job.file, { force: true }); failed.push(job); }
      }
      // 다국어 음성으로 못 한 줄은 인준 음성(우리말 읽기 규칙)으로 다시: 다음 날 다국어 음성으로 다시 해 보도록 캐시에는 넣지 않는다
      const retry = multilingual(v) ? failed.filter((j) => !j.voice) : [];
      const res2 = retry.length ? batch(retry.map(({ id, orig, file }) => ({ id, text: pron.say(orig), file })), EDGE_FALLBACK) : {};
      let fellBack = 0;
      for (const job of failed) {
        if (retry.includes(job) && ok(job) && !res2[job.id]) { done({ ...job, cached: null }); fellBack++; continue; }
        fs.rmSync(job.file, { force: true });
        errors.push(res2[job.id] || res[job.id] || '시간 초과');
      }
      if (fellBack) log.push(`다국어 음성으로 못 한 ${fellBack}줄은 인준 음성으로 녹음`);
      if (errors.length) log.push(`음성 녹음 실패 ${errors.length}줄 (예: ${errors[0]})`);
      for (const f of fs.readdirSync(dir)) if (f.endsWith('.part')) fs.rmSync(path.join(dir, f), { force: true }); // 받다 만 파일은 올리지 않는다
    } catch (err) {
      log.push(`음성 녹음(edge)을 못 함: ${err.message}`);
    }
  };
  if (engine === 'edge' && todo.length) {
    viaEdge(todo, voice);
  } else if (cloud) {
    let busy = 0;
    const who = engine === 'typecast' ? '타입캐스트' : '구글';
    await Promise.all(todo.map((job) => run(async () => {
      if (!stop && Date.now() > deadline) { stop = '시간'; log.push(`음성 녹음: ${who} 녹음이 ${opts.minutes || 20}분을 넘겨 남은 줄은 엣지로`); }
      if (stop === '키' || stop === '거절' || stop === '시간') { rest.push([job.orig, false]); return; }
      // 429(분당 한도)·5xx는 잠깐 쉬었다가 다시. 그래도 안 되면 남은 줄은 더 보내지 않는다. 그 밖의 오류(타입캐스트 422 = 읽을 수 없는 글자)는 그 줄만 엣지로
      for (let n = 0; ; n++) {
        try {
          fs.writeFileSync(job.file, await synth(job.text, key, voice));
          done(job);
          return;
        } catch (err) {
          if (err.status === 400 || err.status === 401 || err.status === 402 || err.status === 403 || err.status === 404) { // 402 = 타입캐스트 크레딧 부족
            if (stop !== '키') log.push(`음성 녹음: 키나 설정 문제로 멈춤 (${err.message})`);
            stop = '키';
          } else if ((err.status === 429 || err.status >= 500) && n < 4 && stop !== '거절') {
            busy++;
            await wait(15e3 * (n + 1));
            continue;
          } else {
            if (err.status === 429 && stop !== '거절') { log.push(`음성 녹음: ${who}이(가) 계속 거절해 멈춤 (${err.message})`); stop = '거절'; }
            else if (err.status !== 429) log.push(`음성 녹음 실패 (${err.message}): ${job.text.slice(0, 20)}`);
          }
          fs.rmSync(job.file, { force: true });
          rest.push([job.orig, false]);
          return;
        }
      }
    })));
    if (busy) log.push(`${who} 음성: 한도(429 등)에 걸려 쉬었다가 다시 보낸 횟수 ${busy}번`);
  }
  let filled = 0;
  if (fill && rest.length) {
    const before = ids.length;
    viaEdge(rest.map(([text, en]) => {
      const id = audioId(text);
      const name = nameOf(EDGE_VOICE, id, en);
      keep.add(name);
      const cached = cache && path.join(cache, name);
      return en
        ? { id, text, file: path.join(dir, `${id}.mp3`), cached, voice: EN_VOICE, rate: EN_RATE }
        : { id, text: pron.say(text, { english: multilingual(EDGE_VOICE) }), orig: text, file: path.join(dir, `${id}.mp3`), cached };
    }), EDGE_VOICE);
    filled = ids.length - before;
  }
  // 캐시에는 오늘 쓰는 파일만 남긴다 (어제 기사 녹음은 지운다)
  if (cache) {
    for (const f of fs.readdirSync(cache)) if (!keep.has(f)) fs.rmSync(path.join(cache, f), { force: true });
  }
  if (unshaped) log.push(`녹음 다듬기(ffmpeg)를 못 한 줄 ${unshaped}개: 모노 그대로`);
  log.push(`음성 녹음(${engine} ${voice}): ${ids.length}줄 (새로 ${fresh}줄, ${spent}자)${stop === '한도' ? ' · 한도에 걸려 일부는 폰 음성' : ''}${fill ? ` · 엣지 음성으로 채운 줄 ${filled}` : ''}`);
  return ids.length ? { voice, base: 'audio/', ids: ids.sort() } : null;
}

module.exports = { record, wanted, audioId, synthesize, synthTypecast, typecastVoice, edgeBatch, shape, VOICE, EDGE_VOICE, EDGE_FALLBACK, EN_VOICE };
