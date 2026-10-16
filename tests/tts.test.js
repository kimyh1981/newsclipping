const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { record, wanted, audioId, shape } = require('../tools/tts.js');
const { spawnSync } = require('child_process');
const brief = require('../js/brief.js');

const B = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'briefing.json'), 'utf8'));
const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), 'tts-'));
const run = (f) => f();

test('녹음 파일 이름은 줄 글자의 SHA-1 앞 16자리 (앱 Brief.audioId와 같은 값)', () => {
  assert.equal(audioId('좋은 아침입니다.'), '69cc2877f7639e2b'); // BriefTest.audioIdMatchesServer와 같은 값
  assert.equal(audioId(' 좋은 아침입니다. '), '69cc2877f7639e2b');
});

test('녹음할 줄: 앱 원고의 모든 줄(인사·연결 말·언론사 소개·헤드라인)과 요약·본문', () => {
  const lines = wanted(B).map(([t]) => t);
  for (const l of brief.script(B).split('\n').map((x) => x.trim()).filter(Boolean)) {
    assert.ok(lines.includes(l), `원고 줄이 빠짐: ${l}`);
  }
  for (const s of B.sections[0].sources[0].items[0].say) assert.ok(lines.includes(s), `전체 듣기 문장이 빠짐: ${s}`);
  assert.equal(new Set(lines).size, lines.length);
});

test('키가 없으면 아무것도 녹음하지 않는다', async () => {
  assert.equal(await record(B, { out: tmp(), key: '', log: [], run }), null);
});

test('한도 안에서 녹음하고, 캐시에 있는 줄은 다시 녹음하지 않으며, 어제 파일은 캐시에서 지운다', async () => {
  const out = tmp();
  const cache = tmp();
  fs.writeFileSync(path.join(cache, 'v-old.mp3'), 'x');
  const said = [];
  const synth = async (text) => { said.push(text); return Buffer.from(text); };
  const log = [];
  const a = await record(B, { out, key: 'k', cache, voice: 'v', log, run, synth });
  const all = wanted(B);
  assert.equal(said.length, all.length);
  assert.equal(a.ids.length, all.length);
  assert.ok(fs.existsSync(path.join(out, 'audio', `${audioId(all[0][0])}.mp3`)));
  assert.ok(!fs.existsSync(path.join(cache, 'v-old.mp3')));
  said.length = 0;
  const again = await record(B, { out: tmp(), key: 'k', cache, voice: 'v', log, run, synth });
  assert.equal(said.length, 0, '두 번째 배포는 캐시만 쓴다');
  assert.deepEqual(again.ids, a.ids);
  said.length = 0;
  const small = await record(B, { out: tmp(), key: 'k', voice: 'v', budget: 60, log: [], run, synth });
  assert.ok(said.join('').length <= 60);
  assert.ok(small.ids.length < all.length);
});

test('키가 틀리면(403) 바로 멈추고 기록만 남긴다', async () => {
  let calls = 0;
  const synth = async () => { calls++; const e = new Error('HTTP 403 PERMISSION_DENIED'); e.status = 403; throw e; };
  const log = [];
  let chain = Promise.resolve();
  const serial = (f) => (chain = chain.then(f)); // 실제로는 limiter(4)로 몇 개씩 차례로 보낸다
  const a = await record(B, { out: tmp(), key: 'k', voice: 'v', log, run: serial, synth });
  assert.equal(a, null);
  assert.equal(calls, 1);
  assert.match(log.join('\n'), /키나 설정 문제로 멈춤 \(HTTP 403 PERMISSION_DENIED\)/);
});

test('edge(무료): 키 없이 한꺼번에 녹음하고, 실패한 줄은 빼며, 파이썬이 없으면 기록만 남긴다', async () => {
  const out = tmp();
  const calls = [];
  const batch = (jobs, voice) => {
    calls.push({ voice, jobs });
    const res = {};
    jobs.forEach((j, i) => { if (i === 1 || voice === 'ko-KR-InJoonNeural') res[j.id] = 'WSServerHandshakeError: 403'; else { fs.writeFileSync(j.file, 'mp3'); res[j.id] = null; } });
    return res;
  };
  const log = [];
  const a = await record(B, { out, engine: 'edge', log, run, batch });
  assert.equal(calls[0].voice, 'ko-KR-HyunsuMultilingualNeural');
  assert.equal(calls[1].voice, 'ko-KR-InJoonNeural'); // 다국어 음성으로 못 한 줄은 인준 음성으로 다시
  assert.equal(calls[1].jobs.length, 1);
  assert.equal(a.voice, 'ko-KR-HyunsuMultilingualNeural');
  assert.equal(a.ids.length, wanted(B).length - 1);
  assert.match(log.join('\n'), /음성 녹음 실패 1줄 \(예: WSServerHandshakeError: 403\)/);
  assert.equal(fs.readdirSync(path.join(out, 'audio')).length, a.ids.length);
  const log2 = [];
  assert.equal(await record(B, { out: tmp(), engine: 'edge', log: log2, run, batch: () => { throw new Error('No module named edge_tts'); } }), null);
  assert.match(log2.join('\n'), /음성 녹음\(edge\)을 못 함: No module named edge_tts/);
  assert.equal(await record(B, { out: tmp(), engine: 'off', key: 'k', log: [], run }), null);
});

test('다국어 음성: 영문은 그대로 두어 미국식으로 읽게 하고, 막힌 줄은 인준 음성과 우리말 규칙으로 다시 녹음한다', async () => {
  const b = { ...B, sections: [{ id: 's', title: '경제', sources: [{ id: 'x', items: [{ title: 't', spoken: 'NATO와 AI에 $5억 투자' }] }] }] };
  const calls = [];
  const batch = (jobs, voice) => {
    calls.push({ voice, jobs });
    const res = {};
    for (const j of jobs) {
      if (voice === 'ko-KR-HyunsuMultilingualNeural' && j.text.includes('NATO')) res[j.id] = 'NoAudioReceived';
      else { fs.writeFileSync(j.file, 'mp3'); res[j.id] = null; }
    }
    return res;
  };
  const log = [];
  const cache = tmp();
  const a = await record(b, { out: tmp(), engine: 'edge', cache, log, run, batch });
  assert.ok(calls[0].jobs.some((j) => j.text.includes('NATO와 AI에 오억달러 투자')));
  assert.ok(calls[1].jobs.some((j) => j.text.includes('나토와 에이아이에 오억달러 투자')));
  assert.ok(a.ids.includes(audioId(brief.sentence('NATO와 AI에 $5억 투자'))));
  assert.match(log.join('\n'), /인준 음성으로 녹음/);
  assert.ok(!fs.readdirSync(cache).some((f) => f.includes(audioId(brief.sentence('NATO와 AI에 $5억 투자')))));
});

test('녹음 다듬기: 한쪽 채널(모노) 녹음을 양쪽 스피커로 나오게 스테레오로 바꾼다 (ffmpeg가 없으면 그대로)', (t) => {
  if (spawnSync('ffmpeg', ['-version']).status !== 0) { t.skip('ffmpeg 없음'); return; }
  const file = path.join(tmp(), 'a.mp3');
  spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'lavfi', '-i', 'sine=f=440:d=1', '-ac', '1', '-ar', '24000', file]);
  assert.ok(shape(file));
  const probe = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'stream=channels', '-of', 'csv=p=0', file], { encoding: 'utf8' });
  assert.equal(probe.stdout.trim(), '2');
  const bad = path.join(tmp(), 'b.mp3');
  fs.writeFileSync(bad, 'not audio');
  assert.equal(shape(bad), false);
  assert.equal(fs.readFileSync(bad, 'utf8'), 'not audio');
});

test('제품 공부와 농업영어: 영어 줄은 영어 목소리로, 한국어 읽기 규칙 없이 녹음한다', async () => {
  const b = { ...B, lessons: require('../tools/collect.js').lessons() };
  const all = wanted(b);
  const en = all.filter(([, , e]) => e);
  assert.ok(en.length > 100);
  assert.ok(en.some(([t]) => t.includes('Ek-toh-sheen')));
  let jobs = [];
  const batch = (js) => { jobs = js; for (const j of js) fs.writeFileSync(j.file, 'x'); return {}; };
  const a = await record(b, { out: tmp(), engine: 'edge', voice: 'v', log: [], run, batch });
  const job = jobs.find((j) => j.text === 'What is Ek-toh-sheen made of?');
  assert.equal(job.voice, 'en-US-AndrewNeural');
  assert.equal(job.rate, '-10%');
  assert.ok(a.ids.includes(audioId('What is Ek-toh-sheen made of?')));
  const ko = jobs.find((j) => j.id === audioId(brief.lessonIntro(1, b.lessons.product.episodes[0].title)));
  assert.equal(ko.voice, undefined);
});

test('유료 음성이 한도(429)를 넘기면 쉬었다가 다시 보내고, 계속 막히거나 한도·시간을 넘긴 줄은 엣지로 채운다 (2026-10-16 구글 203줄 사고)', async () => {
  const all = wanted(B).filter(([, , en]) => !en);
  let calls = 0;
  const synth = async (text) => { calls++; if (calls % 3 === 0) { const e = new Error('HTTP 429 RESOURCE_EXHAUSTED'); e.status = 429; throw e; } return Buffer.from(text); };
  const edged = [];
  const batch = (jobs, voice) => { for (const j of jobs) { edged.push({ voice, j }); fs.writeFileSync(j.file, 'mp3'); } return {}; };
  const waits = [];
  const log = [];
  const a = await record(B, { out: tmp(), key: 'k', engine: 'google', voice: 'v', budget: 400, fallback: 'edge', log, run, synth, batch, wait: async (ms) => { waits.push(ms); } });
  assert.ok(waits.length > 0, '429면 쉬었다가 다시');
  assert.ok(edged.length > 0 && edged.every((e) => e.voice === 'ko-KR-HyunsuMultilingualNeural'));
  assert.equal(a.ids.length, wanted(B).length, '모든 줄이 녹음됨 (유료 + 엣지)');
  assert.match(log.join('\n'), /엣지 음성으로 채운 줄 \d+/);
  assert.ok(all.length > edged.length);
});

test('타입캐스트: 이름으로 목소리를 찾고, 크레딧이 떨어지면(402) 멈춰 남은 줄을 엣지로, 읽을 수 없는 줄(422)은 그 줄만 엣지로', async () => {
  let found = '';
  const findVoice = async (key, name) => { found = name; return 'tc_jain'; };
  const voices = [];
  let n = 0;
  const synth = async (text, key, v) => {
    voices.push(v); n++;
    if (n === 2) { const e = new Error('HTTP 422 TEXT_NOT_SYNTHESIZABLE'); e.status = 422; throw e; }
    if (n === 6) { const e = new Error('HTTP 402 Insufficient credit'); e.status = 402; throw e; }
    return Buffer.from(text);
  };
  const edged = [];
  const batch = (jobs) => { for (const j of jobs) { edged.push(j); fs.writeFileSync(j.file, 'mp3'); } return {}; };
  const log = [];
  let chain = Promise.resolve();
  const serial = (f) => (chain = chain.then(f));
  const a = await record(B, { out: tmp(), key: 'k', engine: 'typecast', fallback: 'edge', log, run: serial, synth, batch, findVoice, wait: async () => {} });
  assert.equal(found, '자인');
  assert.ok(voices.every((v) => v === 'tc_jain'));
  assert.equal(n, 6, '402 뒤로는 타입캐스트에 더 보내지 않는다');
  assert.equal(a.ids.length, wanted(B).length);
  assert.match(log.join('\n'), /키나 설정 문제로 멈춤 \(HTTP 402/);
  assert.match(log.join('\n'), /음성 녹음 실패 \(HTTP 422/);
  assert.equal(a.voice, 'tc_jain');
});

test('타입캐스트: 목소리를 못 찾으면 전부 엣지로, 정해진 시간이 지나면 남은 줄을 엣지로', async () => {
  const batch = (jobs) => { for (const j of jobs) fs.writeFileSync(j.file, 'mp3'); return {}; };
  const log = [];
  let calls = 0;
  const findVoice = async () => { const e = new Error("목소리 '자인'를 목록에서 못 찾음"); e.status = 404; throw e; };
  const a = await record(B, { out: tmp(), key: 'k', engine: 'typecast', fallback: 'edge', log, run, batch, findVoice, synth: async () => { calls++; return Buffer.from('x'); } });
  assert.equal(calls, 0);
  assert.equal(a.ids.length, wanted(B).length);
  assert.match(log.join('\n'), /목소리를 못 정함/);
  const log2 = [];
  const b2 = await record(B, { out: tmp(), key: 'k', engine: 'typecast', fallback: 'edge', minutes: -1, log: log2, run, batch, findVoice: async () => 'tc_x', synth: async () => { calls++; return Buffer.from('x'); } });
  assert.equal(calls, 0, '시간이 이미 지났으면 타입캐스트에 보내지 않는다');
  assert.equal(b2.ids.length, wanted(B).length);
  assert.match(log2.join('\n'), /분을 넘겨 남은 줄은 엣지로/);
});
