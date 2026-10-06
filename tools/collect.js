#!/usr/bin/env node
/*
 * 아침 뉴스 브리핑 수집. 배포 작업이 매일 새벽 이 스크립트로 RSS를 모아
 * briefing.json(화면용)과 briefing.txt(앱이 읽는 음성 원고)를 만든다.
 * 사용: node tools/collect.js <출력 폴더>
 */
const fs = require('fs');
const path = require('path');
const rss = require('./rss.js');
const brief = require('../js/brief.js');
const holidays = require('./holidays.js');
const { summarize } = require('./summarize.js');
const tts = require('./tts.js');
const naver = require('./naver.js');

const SPARE = 2; // 언론사마다 take보다 2건 더 모은다: 다른 언론사와 겹치는 기사를 빼고도 take건을 채우도록
const UA = 'Mozilla/5.0 (compatible; news-briefing/1.0; +https://github.com/kimyh1981/Personal-Project)';

// 구글 뉴스 검색. lang에 따라 그 나라판(그 나라 말 기사)에서 찾는다
const EDITIONS = { ko: ['ko', 'KR', 'KR:ko'], vi: ['vi', 'VN', 'VN:vi'], en: ['en-US', 'US', 'US:en'], ja: ['ja', 'JP', 'JP:ja'], zh: ['zh-CN', 'CN', 'CN:zh-Hans'] };

function googleUrl(q, when = '1d', lang = 'ko') {
  const [hl, gl, ceid] = EDITIONS[lang] || EDITIONS.ko;
  return `https://news.google.com/rss/search?q=${encodeURIComponent(`${q} when:${when}`)}&hl=${hl}&gl=${gl}&ceid=${ceid}`;
}

// 외국어 제목을 우리말로 옮긴다 (키 없는 무료 번역). 구글 번역이 429로 막으면 잠깐 쉬었다가 다시 보내고,
// 그래도 막히면 그날은 그 번역기를 건너뛰고 다음 번역기로 넘어간다
const RETRY = { waits: [3e3] };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const unwrap = (d) => { while (Array.isArray(d)) d = d[0]; return typeof d === 'string' ? d : ''; };

const ENGINES = [
  {
    name: 'google',
    url: (q, sl, tl) => `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${sl}&tl=${tl}&dt=t&q=${encodeURIComponent(q)}`,
    read: (d) => (Array.isArray(d?.[0]) ? d[0] : []).map((seg) => seg?.[0] || '').join(''),
  },
  {
    name: 'google-dict',
    url: (q, sl, tl) => `https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=${sl}&tl=${tl}&q=${encodeURIComponent(q)}`,
    read: unwrap,
  },
  {
    name: 'mymemory',
    url: (q, sl, tl) => `https://api.mymemory.translated.net/get?q=${encodeURIComponent(q)}&langpair=${sl}|${tl}`,
    read: (d) => (Number(d?.responseStatus) === 200 && !/^MYMEMORY WARNING/.test(d?.responseData?.translatedText || '') ? d.responseData.translatedText : ''),
  },
];
const blocked = new Set(); // 이번 수집에서 429로 막힌 번역기
const used = {}; // 번역기별 성공 횟수 (수집 기록용)

async function translateWith(engine, text, sl, tl) {
  for (let i = 0; ; i++) {
    const res = await fetch(engine.url(text, sl, tl), { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(10e3) });
    if (res.status === 429 && i < RETRY.waits.length) { await sleep(RETRY.waits[i]); continue; }
    if (res.status === 429) blocked.add(engine.name);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const out = engine.read(await res.json()).trim();
    if (!out) throw new Error('빈 번역');
    return out;
  }
}

async function translate(text, from, to = 'ko') {
  const sl = from === 'zh' ? 'zh-CN' : from;
  let last = new Error('번역기 없음');
  for (const engine of ENGINES) {
    if (blocked.has(engine.name)) continue;
    try {
      const out = await translateWith(engine, text, sl, to);
      used[engine.name] = (used[engine.name] || 0) + 1;
      return out;
    } catch (err) {
      last = err;
    }
  }
  throw last;
}

// 한 언론사의 제목들을 줄바꿈으로 이어 한 번에 번역한다 (언론사 수만큼만 요청). 줄 수가 안 맞으면 한 건씩 다시 번역한다
async function translateAll(texts, from) {
  if (texts.length > 1) {
    const lines = (await translate(texts.join('\n'), from)).split('\n').map((t) => t.trim());
    if (lines.length === texts.length && lines.every(Boolean)) return lines;
  }
  const out = [];
  for (const t of texts) out.push(await translate(t, from));
  return out;
}

// 번역은 한꺼번에 너무 많이 보내지 않는다 (동시에 n건)
function limiter(n) {
  let active = 0;
  const wait = [];
  return async (job) => {
    while (active >= n) await new Promise((r) => wait.push(r));
    active++;
    try { return await job(); } finally { active--; (wait.shift() || (() => {}))(); }
  };
}

// 외국 언론: 고른 기사 제목을 우리말로 바꾸고 원문은 original에 남긴다. 번역에 실패하면 읽을 수 없으니 빼고 기록만 남긴다
async function translateItems(name, lang, items, log) {
  if (!items.length) return items;
  try {
    const titles = await translateAll(items.map((it) => it.title), lang);
    return items.map((it, i) => ({ ...it, title: titles[i], original: it.title, spoken: rss.spoken(titles[i]), lang })).filter((it) => it.spoken);
  } catch (err) {
    log.push(`${name}: 번역 실패 (${err.cause?.code || err.message}) ${items.length}건`);
    return [];
  }
}

// 같은 사건을 다룬 기사는 같은 group: 화면·앱이 고른 언론사 중 처음 나온 것만 읽는다
function group(sections) {
  const seen = [];
  for (const sec of sections) for (const src of sec.sources) for (const it of src.items) {
    const hit = seen.find((g) => rss.similar(g.title, it.title));
    if (hit) it.group = hit.id;
    else { it.group = `g${seen.length}`; seen.push({ id: it.group, title: it.title }); }
  }
}

// 국내 언론 RSS 일부는 EUC-KR이다: XML 선언이나 Content-Type의 charset을 보고 푼다
function decode(buf, contentType = '') {
  const head = Buffer.from(buf.slice(0, 200)).toString('latin1');
  const cs = (head.match(/encoding=["']([\w-]+)["']/i) || contentType.match(/charset=([\w-]+)/i) || [])[1] || 'utf-8';
  try {
    return new TextDecoder(cs.toLowerCase()).decode(buf);
  } catch {
    return new TextDecoder('utf-8').decode(buf);
  }
}

async function get(url) {
  const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/rss+xml, application/xml, text/xml, */*' }, signal: AbortSignal.timeout(15e3), redirect: 'follow' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return decode(new Uint8Array(await res.arrayBuffer()), res.headers.get('content-type') || '');
}

// 네이버의 1면·주요 뉴스(언론사가 고른 헤드라인)를 먼저, 안 되면 언론사 RSS, 그것도 막히거나 비었으면 구글 뉴스 검색으로 대신한다
async function readSource(src, log, lang, now = Date.now()) {
  if (src.naver) {
    const kind = src.front ? '네이버 1면' : '네이버 주요 뉴스';
    try {
      const items = await naver.read(src, get, now);
      log.push(`${src.name}: ${kind} ${items.length}건`);
      return items;
    } catch (err) {
      log.push(`${src.name}: ${kind} 실패 (${err.cause?.code || err.message})`);
    }
  }
  const tries = [];
  if (src.url) tries.push(['RSS', src.url]);
  if (src.google) tries.push(['구글', googleUrl(src.google, src.when, lang)]);
  for (const [kind, url] of tries) {
    try {
      const items = rss.parseFeed(await get(url)).map((it) => ({ ...it, viaGoogle: url.startsWith('https://news.google.com/') }));
      if (items.length) {
        log.push(`${src.name}: ${kind} ${items.length}건`);
        return items;
      }
      log.push(`${src.name}: ${kind} 비어 있음`);
    } catch (err) {
      log.push(`${src.name}: ${kind} 실패 (${err.cause?.code || err.message})`);
    }
  }
  return [];
}

// 아이폰 단축어가 읽는 briefing.txt: 주말·공휴일에는 비워 두어 아무것도 읽지 않게 한다
function speechText(b) {
  return b.autoPlay.play ? b.script : '';
}

function iosPieces(b) {
  const ids = ['open', 'close', ...b.sections.flatMap((sec) => sec.sources.map((x) => x.id))];
  return Object.fromEntries(ids.map((id) => [id, b.autoPlay.play ? brief.piece(b, id) : '']));
}

async function collect(config, now = Date.now(), key = '', opts = {}) {
  const log = [];
  const autoPlay = await holidays.playDay(now, key);
  const sections = await Promise.all(config.sections.map(async (sec) => ({
    id: sec.id,
    title: sec.title,
    perSourceLabel: !!sec.perSourceLabel,
    limit: sec.limit,
    sources: await Promise.all(sec.sources.map(async (source) => {
      const lang = source.lang || sec.lang || 'ko';
      const take = source.take || 3;
      const read = await readSource(source, log, lang, now);
      const items = rss.pick({ ...sec, limit: 99 }, [{ source: { ...source, lang, take: take + SPARE }, items: read }], now, []);
      return { id: source.id, name: source.name, lang, default: source.default !== false, take, items };
    })),
  })));
  // 번역: 기본 언론사부터, 두 곳씩 차례로 (한꺼번에 보내면 구글 번역이 429로 막는다)
  blocked.clear();
  for (const k of Object.keys(used)) delete used[k];
  const foreign = sections.flatMap((sec) => sec.sources).filter((src) => src.lang !== 'ko');
  const tr = limiter(2);
  for (const batch of [foreign.filter((s) => s.default), foreign.filter((s) => !s.default)]) {
    await Promise.all(batch.map((src) => tr(async () => { src.items = await translateItems(src.name, src.lang, src.items, log); })));
  }
  if (foreign.length) log.push(`번역기: ${Object.entries(used).map(([k, n]) => `${k} ${n}번`).join(', ') || '없음'}${blocked.size ? ` (429로 막힘: ${[...blocked].join(', ')})` : ''}`);
  group(sections);
  const b = { version: 2, generatedAt: new Date(now).toISOString(), dateLabel: rss.koreanDate(now), autoPlay, sections, log };
  // 기본 언론사 원고에 든 기사부터 요약한다 (같은 기사 객체에 summary가 붙는다)
  const run = limiter(4);
  if (opts.summaryLimit) await summarize(brief.select(b).flatMap((s) => s.items), { mode: opts.summaryMode, get, translate, decodeEntities: rss.decodeEntities, log, run, key: opts.summaryKey, limit: opts.summaryLimit });
  // '전체 듣기'로 읽을 문장들: 소리 내어 읽기 좋게 다듬어 문장마다 나눈다 (앱 자막·녹음 단위)
  for (const sec of sections) for (const src of sec.sources) for (const it of src.items) {
    const say = rss.speechSentences(it.body || it.summary);
    if (say.length) it.say = say;
  }
  b.script = brief.script(b); // 기본 언론사로 만든 원고: briefing.txt(아이폰 단축어, 옛 앱)
  return b;
}

if (require.main === module) {
  (async () => {
    const out = path.resolve(process.argv[2] || 'dist');
    fs.mkdirSync(out, { recursive: true });
    const config = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'feeds.json'), 'utf8'));
    const b = await collect(config, Date.now(), process.env.DATA_GO_KR_KEY || '', {
      summaryMode: process.env.SUMMARY_MODE || 'lead', // 저장소 Variables에서 SUMMARY_MODE=claude로 바꾸면 Claude 요약
      summaryKey: process.env.ANTHROPIC_API_KEY || '',
      summaryLimit: process.env.SUMMARY_LIMIT === undefined ? 40 : Number(process.env.SUMMARY_LIMIT),
    });
    // 원고 줄을 신경망 음성으로 미리 녹음 (기본: 무료 MS 엣지 음성, GOOGLE_TTS_KEY가 있으면 구글, TTS_ENGINE=off면 안 함). 녹음이 없으면 앱이 폰 음성으로 읽는다
    const key = process.env.GOOGLE_TTS_KEY || '';
    b.audio = await tts.record(b, {
      out, key, engine: process.env.TTS_ENGINE || (key ? 'google' : 'edge'), cache: process.env.TTS_CACHE || '', voice: process.env.TTS_VOICE || '',
      budget: Number(process.env.TTS_DAILY_CHARS) || 0, log: b.log, run: limiter(4),
    });
    fs.writeFileSync(path.join(out, 'briefing.json'), JSON.stringify(b, null, 1));
    fs.writeFileSync(path.join(out, 'briefing.txt'), speechText(b));
    // 아이폰 단축어가 고른 언론사만 읽도록 언론사마다 조각 원고 (쉬는 날에는 모두 빈 파일)
    fs.mkdirSync(path.join(out, 'ios'), { recursive: true });
    for (const [id, text] of Object.entries(iosPieces(b))) fs.writeFileSync(path.join(out, 'ios', `${id}.txt`), text);
    console.log(`뉴스 브리핑 ${b.dateLabel} (기본 언론사): ` + brief.select(b).map((s) => `${s.title} ${s.items.length}건`).join(' · '));
    console.log(`언론사 ${b.sections.reduce((n, s) => n + s.sources.length, 0)}곳, 기사 ${b.sections.reduce((n, s) => n + s.sources.reduce((m, x) => m + x.items.length, 0), 0)}건`);
    b.log.forEach((l) => console.log('  ' + l));
    console.log(`자동 재생: ${b.autoPlay.play ? '함' : '안 함'} (${b.autoPlay.ymd} ${b.autoPlay.reason}, ${b.autoPlay.source})`);
    const sums = b.sections.flatMap((s) => s.sources.flatMap((x) => x.items)).filter((i) => i.summary);
    if (sums.length) console.log(`요약 ${sums.length}건, 예: ${sums[0].title} → ${sums[0].summary}`);
    console.log(`원고 ${b.script.length}자 (약 ${Math.ceil(b.script.length / 330)}분)`);
    process.exit(0); // 남은 연결이 있어도 배포를 막지 않는다
  })();
}

module.exports = { collect, decode, googleUrl, translate, speechText, iosPieces, RETRY };
