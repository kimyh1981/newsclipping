/*
 * 원고 줄을 신경망 음성으로 미리 녹음해 둔다. 녹음하는 곳(engine)은 둘 중 하나:
 *  - edge (기본, 무료·키 없음): MS 엣지 '소리 내어 읽기' 음성(edge-tts, tools/edge_tts_batch.py). 비공식이라 막히면 그날은 폰 음성
 *  - google: 구글 클라우드 Text-to-Speech(Chirp 3 HD), 저장소 비밀값 GOOGLE_TTS_KEY가 있을 때 (카드 등록 필요)
 * 앱은 원고 줄마다 그 글자 그대로 녹음된 파일이 있으면 그 파일을, 없으면 폰 음성으로 읽는다 (TextToSpeech.addSpeech).
 *  - 파일 이름은 줄 글자의 SHA-1 앞 16자리 (앱 Brief.audioId와 같은 규칙)
 *  - 하루 글자 수 한도(TTS_DAILY_CHARS) 안에서 인사·연결 말 → 헤드라인 → 기본 언론사 요약 → 본문 순으로 녹음한다
 *  - 지난 실행에서 녹음한 파일(캐시)은 다시 녹음하지 않는다: 하루 두 번 배포해도 새 기사만 돈이 든다
 * TTS_ENGINE=off면 아무것도 하지 않는다.
 */
const { spawnSync } = require('child_process');
const os = require('os');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const brief = require('../js/brief.js');

const VOICE = 'ko-KR-Chirp3-HD-Aoede';
const EDGE_VOICE = 'ko-KR-SunHiNeural';
const ENDPOINT = 'https://texttospeech.googleapis.com/v1/text:synthesize';

const audioId = (text) => crypto.createHash('sha1').update(text.trim(), 'utf8').digest('hex').slice(0, 16);

// 앱이 읽을 수 있는 줄을 중요한 순서대로: [글자, 묶음] (묶음별로 한도를 나눠 쓴다)
function wanted(b) {
  const date = b.dateLabel;
  const out = [];
  const add = (text, kind) => { if (text && text.trim()) out.push([text.trim(), kind]); };
  add(brief.opening(date), 'fixed');
  add(brief.closing(date), 'fixed');
  add(brief.EMPTY, 'fixed');
  add('또,', 'fixed');
  add(`좋은 아침입니다. ${date}입니다. 오늘은 뉴스를 가져오지 못했습니다. 안전 운전하세요.`, 'fixed');
  for (const sec of b.sections) {
    add(brief.intro(sec.title, 0, 3), 'fixed'); // 먼저 ~부터 전해 드립니다.
    add(brief.intro(sec.title, 1, 3), 'fixed'); // 다음은 ~입니다.
    add(brief.intro(sec.title, 2, 4), 'fixed'); // 이어서 ~입니다.
    add(brief.intro(sec.title, 2, 3), 'fixed'); // 끝으로 ~입니다.
  }
  const items = b.sections.flatMap((sec) => sec.sources.flatMap((src) => src.items.map((it) => ({ sec, src, it }))));
  for (const { sec, it } of items) if (sec.perSourceLabel) add(`${it.source} 소식입니다.`, 'fixed');
  for (const { it } of items) add(brief.sentence(it.spoken), 'headline');
  const picked = brief.select(b).flatMap((s) => s.items);
  for (const it of picked) add(it.summary, 'summary');
  for (const it of picked) add(it.body, 'body');
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

// edge-tts로 여러 줄을 한 번에 녹음: { id: null(성공) | 오류 }. 파이썬이나 edge-tts가 없으면 예외
function edgeBatch(jobs, voice, rate = '+0%') {
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'edge-')), 'jobs.json');
  fs.writeFileSync(tmp, JSON.stringify(jobs));
  const r = spawnSync('python3', [path.join(__dirname, 'edge_tts_batch.py'), tmp, voice, rate], { encoding: 'utf8', maxBuffer: 16 << 20, timeout: 6 * 60e3 });
  if (r.status !== 0) throw new Error((r.stderr || r.error?.message || `종료 코드 ${r.status}`).trim().split('\n').pop());
  return JSON.parse(r.stdout);
}

// out 폴더에 audio/<id>.mp3를 만들고, 녹음된 줄의 id 목록을 돌려준다 (briefing.json의 audio.ids)
async function record(b, opts) {
  const { out, key = '', cache, log, run, synth = synthesize, batch = edgeBatch } = opts;
  const engine = opts.engine || (key ? 'google' : 'off');
  if (engine === 'off' || (engine === 'google' && !key)) return null;
  const voice = opts.voice || (engine === 'edge' ? EDGE_VOICE : VOICE);
  const budget = opts.budget || (engine === 'edge' ? 80000 : 25000); // edge는 돈이 들지 않지만 배포 시간을 넘기지 않게
  const dir = path.join(out, 'audio');
  fs.mkdirSync(dir, { recursive: true });
  if (cache) fs.mkdirSync(cache, { recursive: true });
  const ids = [];
  let spent = 0;
  let fresh = 0;
  let stop = '';
  const todo = [];
  for (const [text, kind] of wanted(b)) {
    const id = audioId(text);
    const cached = cache && path.join(cache, `${voice}-${id}.mp3`);
    if (cached && fs.existsSync(cached)) {
      fs.copyFileSync(cached, path.join(dir, `${id}.mp3`));
      ids.push(id);
      continue;
    }
    if (spent + text.length > budget) { if (kind === 'fixed' || kind === 'headline') stop = '한도'; continue; }
    spent += text.length;
    todo.push({ id, text, file: path.join(dir, `${id}.mp3`), cached });
  }
  const done = (job) => {
    if (job.cached) fs.copyFileSync(job.file, job.cached);
    ids.push(job.id);
    fresh++;
  };
  if (engine === 'edge' && todo.length) {
    try {
      const res = batch(todo.map(({ id, text, file }) => ({ id, text, file })), voice);
      const errors = [];
      for (const job of todo) {
        if (res[job.id] === null && fs.existsSync(job.file) && fs.statSync(job.file).size > 0) done(job);
        else { fs.rmSync(job.file, { force: true }); errors.push(res[job.id] || '파일 없음'); }
      }
      if (errors.length) log.push(`음성 녹음 실패 ${errors.length}줄 (예: ${errors[0]})`);
    } catch (err) {
      log.push(`음성 녹음(edge)을 못 함: ${err.message}`);
    }
  } else if (engine === 'google') {
    await Promise.all(todo.map((job) => run(async () => {
      if (stop === '키') return;
      try {
        fs.writeFileSync(job.file, await synth(job.text, key, voice));
        done(job);
      } catch (err) {
        if (err.status === 400 || err.status === 401 || err.status === 403) {
          if (stop !== '키') log.push(`음성 녹음: 키나 설정 문제로 멈춤 (${err.message})`);
          stop = '키';
        } else log.push(`음성 녹음 실패 (${err.message}): ${job.text.slice(0, 20)}`);
      }
    })));
  }
  // 캐시에는 오늘 쓰는 파일만 남긴다 (어제 기사 녹음은 지운다)
  if (cache) {
    const keep = new Set(ids.map((id) => `${voice}-${id}.mp3`));
    for (const f of fs.readdirSync(cache)) if (!keep.has(f)) fs.rmSync(path.join(cache, f), { force: true });
  }
  log.push(`음성 녹음(${engine} ${voice}): ${ids.length}줄 (새로 ${fresh}줄, ${spent}자)${stop === '한도' ? ' · 한도에 걸려 일부는 폰 음성' : ''}`);
  return ids.length ? { voice, base: 'audio/', ids: ids.sort() } : null;
}

module.exports = { record, wanted, audioId, synthesize, edgeBatch, VOICE, EDGE_VOICE };
