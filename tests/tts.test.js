const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { record, wanted, audioId } = require('../tools/tts.js');
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
    const t = l.startsWith('또, ') ? l.slice(3) : l;
    assert.ok(lines.includes(t), `원고 줄이 빠짐: ${t}`);
  }
  assert.ok(lines.includes('또,'));
  assert.ok(lines.some((t) => t.startsWith('가 기사의 자세한 내용')), '요약');
  assert.ok(lines.some((t) => t.endsWith('본문 세 번째 문장까지 이어집니다.')), '본문');
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
  const batch = (jobs, voice) => {
    assert.equal(voice, 'ko-KR-SunHiNeural');
    const res = {};
    jobs.forEach((j, i) => { if (i === 1) res[j.id] = 'WSServerHandshakeError: 403'; else { fs.writeFileSync(j.file, 'mp3'); res[j.id] = null; } });
    return res;
  };
  const log = [];
  const a = await record(B, { out, engine: 'edge', log, run, batch });
  assert.equal(a.voice, 'ko-KR-SunHiNeural');
  assert.equal(a.ids.length, wanted(B).length - 1);
  assert.match(log.join('\n'), /음성 녹음 실패 1줄 \(예: WSServerHandshakeError: 403\)/);
  assert.equal(fs.readdirSync(path.join(out, 'audio')).length, a.ids.length);
  const log2 = [];
  assert.equal(await record(B, { out: tmp(), engine: 'edge', log: log2, run, batch: () => { throw new Error('No module named edge_tts'); } }), null);
  assert.match(log2.join('\n'), /음성 녹음\(edge\)을 못 함: No module named edge_tts/);
  assert.equal(await record(B, { out: tmp(), engine: 'off', key: 'k', log: [], run }), null);
});
