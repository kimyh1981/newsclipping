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
const pron = require('../js/pron.js');
const holidays = require('./holidays.js');
const { resolveLinks } = require('./summarize.js');
const lock = require('./lessons-crypt.js');
const tts = require('./tts.js');
const naver = require('./naver.js');
const region = require('./region.js');

const SPARE = 2; // 언론사마다 take보다 2건 더 모은다: 다른 언론사와 겹치는 기사를 빼고도 take건을 채우도록
const UA = 'Mozilla/5.0 (compatible; news-briefing/1.0; +https://github.com/kimyh1981/Personal-Project)';

// 구글 뉴스 검색. lang에 따라 그 나라판(그 나라 말 기사)에서 찾는다. 같은 말을 쓰는 다른 나라는 출처의 edition(IN, GB, BR)으로
const EDITIONS = {
  ko: ['ko', 'KR', 'KR:ko'], vi: ['vi', 'VN', 'VN:vi'], en: ['en-US', 'US', 'US:en'], ja: ['ja', 'JP', 'JP:ja'], zh: ['zh-CN', 'CN', 'CN:zh-Hans'],
  pt: ['pt-BR', 'BR', 'BR:pt-419'], ru: ['ru', 'RU', 'RU:ru'], id: ['id', 'ID', 'ID:id'], th: ['th', 'TH', 'TH:th'], IN: ['en-IN', 'IN', 'IN:en'], GB: ['en-GB', 'GB', 'GB:en'], MY: ['en-MY', 'MY', 'MY:en'], BR: ['pt-BR', 'BR', 'BR:pt-419'],
};

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

/** 언론사 누리집 주소(앱 홈 화면의 로고용). site가 있으면 그것, 아니면 구글 검색어의 site:. 여러 언론사 모음(mix)은 빈칸 */
function siteOf(source) {
  if (source.site) return source.site;
  if (source.mix) return '';
  const m = /site:([^\s)]+)/.exec(source.google || '');
  return m ? m[1] : '';
}

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

async function get(url, { ua = UA, timeout = 15e3 } = {}) {
  const res = await fetch(url, { headers: { 'User-Agent': ua, Accept: 'application/rss+xml, application/xml, text/xml, text/html, */*' }, signal: AbortSignal.timeout(timeout), redirect: 'follow' });
  if (!res.ok) { const e = new Error(`HTTP ${res.status}`); e.status = res.status; throw e; }
  return decode(new Uint8Array(await res.arrayBuffer()), res.headers.get('content-type') || '');
}

// RSS·검색 결과: 연결이 안 되거나 시간 초과면 쉬었다가 한 번 더 (2026-10-17: 영농자재신문 RSS가 새벽에만 시간 초과)
async function getFeed(url) {
  try {
    return await get(url);
  } catch (err) {
    if (err.status) throw err;
    await sleep(RETRY.waits[0]);
    return get(url, { timeout: 25e3 });
  }
}

// 구글 뉴스 기사 화면(원래 기사 주소 찾기용, 본문은 받지 않는다): 브라우저처럼 받고, 시간 초과·연결 끊김·5xx·429면 쉬었다가 두 번 더 (2026-10-14: 네이버 기사 17건이 시간 초과)
const BROWSER = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36';
const PAGE_WAITS = [2e3, 6e3];
async function getPage(url) {
  for (let i = 0; ; i++) {
    try {
      return await get(url, { ua: i === 2 ? UA : BROWSER, timeout: 25e3 });
    } catch (err) {
      const again = !err.status || err.status >= 500 || err.status === 429 || (i === 0 && err.status === 403);
      if (!again || i >= PAGE_WAITS.length) throw err;
      await sleep(PAGE_WAITS[i]);
    }
  }
}

// 네이버의 1면·주요 뉴스(언론사가 고른 헤드라인)를 먼저, 안 되면 언론사 RSS, 그것도 막히거나 비었으면 구글 뉴스 검색으로 대신한다
// 오후 원고: 서울 시각 낮 12시부터는 오후 원고 (하루 두 번 수집, 주인 요청 2026-10-14)
function editionOf(now) {
  return new Date(now + 9 * 3600e3).getUTCHours() >= 12 ? 'evening' : 'morning';
}

async function readSource(src, log, lang, now = Date.now(), edition = 'morning') {
  // 오후에는 언론사가 고른 1면·주요 뉴스 대신 그 언론사에서 가장 많이 본 뉴스. 안 되면 아침처럼 1면·주요 뉴스로
  if (src.naver && edition === 'evening') {
    try {
      const items = await naver.read(src, get, now, { ranking: true });
      log.push(`${src.name}: 네이버 많이 본 뉴스 ${items.length}건`);
      return items;
    } catch (err) {
      log.push(`${src.name}: 네이버 많이 본 뉴스 실패 (${err.cause?.code || err.message})`);
    }
  }
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
  if (src.google) tries.push(['구글', googleUrl(src.google, src.when, src.edition || lang)]);
  for (const [kind, url] of tries) {
    try {
      let items = rss.parseFeed(await getFeed(url)).map((it) => ({ ...it, viaGoogle: url.startsWith('https://news.google.com/') }));
      // 같은 제목이 세 번 이상이면 기사가 아니라 사이트 첫 화면이다 (구글 뉴스의 '한국무역신문, 주간무역' 18건)
      const count = {};
      for (const it of items) count[titleKey(it.title)] = (count[titleKey(it.title)] || 0) + 1;
      const home = items.filter((it) => count[titleKey(it.title)] >= 3).length;
      if (home) {
        items = items.filter((it) => count[titleKey(it.title)] < 3);
        log.push(`${src.name}: 같은 제목이 반복된 ${home}건(사이트 화면)을 뺌`);
      }
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

// 전날(과 그 전날) 원고에 나온 기사: 링크나 제목이 같으면 오늘은 빼고 그다음 기사로 채운다.
// 1면이 아직 안 올라온 신문이나 하루에 몇 건 안 쓰는 전문지·RSS가 어제 기사를 그대로 내놓아 겹쳐 들렸다 (2026-10-08)
const SITE = 'https://kimyh1981.github.io/newsclipping/';
const titleKey = (t) => 't:' + String(t || '').normalize('NFC').replace(/[^\p{L}\p{N}]/gu, '').toLowerCase();

function itemKeys(b) {
  const keys = new Set();
  for (const sec of b.sections || []) for (const src of sec.sources || []) for (const it of src.items || []) {
    if (it.link) keys.add('l:' + it.link);
    keys.add(titleKey(it.original || it.title));
  }
  return [...keys];
}

// 지금 사이트에 올라가 있는 원고(live)에서 오늘 이전 날들의 기사 열쇠를 이어 받는다 (최근 days일).
// 같은 날 두 번째 배포라면 그날 것은 넣지 않는다: 새벽 첫 배포와 같은 기사를 다시 빼면 안 되니까.
// 다만 오후 원고는 그날 아침 원고에 나온 기사를 빼고 새 기사로 채운다 (열쇠 이름 <날짜>am)
function earlierKeys(live, now, days = 2) {
  const today = naver.seoulDate(now);
  const evening = editionOf(now) === 'evening';
  const out = { ...(live && live.earlier) };
  if (live && live.generatedAt) {
    const d = naver.seoulDate(Date.parse(live.generatedAt));
    if (d !== today) out[d] = itemKeys(live);
    else if (evening && live.edition !== 'evening') out[`${d}am`] = itemKeys(live);
  }
  const past = Object.keys(out).filter((d) => d < today);
  const dates = [...new Set(past.map((d) => d.slice(0, 8)))].sort().slice(-days); // 지난 이틀 (그날 아침·오후 원고 모두)
  const keep = past.filter((d) => dates.includes(d.slice(0, 8)));
  if (evening && out[`${today}am`]) keep.push(`${today}am`);
  return Object.fromEntries(keep.map((d) => [d, out[d]]));
}

// 지난 원고 보관: archive/<서울 날짜>.json을 최근 ARCHIVE_DAYS일치 남기고 archive/index.json에 날짜 목록을 둔다.
// 사이트는 배포마다 통째로 바뀌므로, 지금 사이트에 있는 지난 날짜 파일을 받아 다시 올린다 (웹 화면에서 날짜를 골라 본다)
const ARCHIVE_DAYS = 7; // 아침·오후 원고를 따로 남긴다 (오후 원고는 archive/<날짜>pm.json)
// 기사 본문(예전 원고의 summary·body·say)은 지난 원고에서도 지운다: 사이트 어디에도 본문을 남기지 않는다 (저작권, 2026-10-18)
const BODY_FIELDS = ['summary', 'body', 'say', 'bodyElsewhere'];
const noBody = (b) => ({
  ...b,
  sections: (b.sections || []).map((sec) => ({ ...sec, sources: (sec.sources || []).map((src) => ({ ...src, items: (src.items || []).map((it) => Object.fromEntries(Object.entries(it).filter(([k]) => !BODY_FIELDS.includes(k)))) })) })),
});
const slim = (b) => { const { audio, earlier, lessons: _, lessonsEnc: __, ...rest } = b; return noBody(rest); }; // 녹음은 그날 것만 남으니 빼고, 겹침 열쇠와 제품 공부 원고(날마다 같음)도 뺀다

// 제품 공부(product)와 같은 회의 농업영어(english) 원고. 저장소에는 잠긴 것(lessons/lessons.enc.json)만 있다 (퇴사자가 못 보게, 2026-10-17).
// box: 공개 원고에 그대로 넣는 잠긴 꼴(앱이 사내 코드로 푼다). plain: 녹음하려고 저장소 비밀값 LESSON_CODE로 푼 원문 (코드가 없으면 없음)
function lessons(code = process.env.LESSON_CODE || '') {
  let box;
  try {
    box = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'lessons', 'lessons.enc.json'), 'utf8'));
  } catch (err) {
    console.log(`제품 공부 원고를 못 읽음: ${err.message}`);
    return {};
  }
  if (!code) { console.log('LESSON_CODE가 없어 제품 공부는 녹음하지 않음 (앱은 사내 코드로 풀어 폰 음성으로 읽는다)'); return { box }; }
  try {
    return { box, plain: lock.open(box, code) };
  } catch (err) {
    console.log(`제품 공부 원고를 풀지 못함 (LESSON_CODE가 맞는지 확인): ${err.message}`);
    return { box };
  }
}

async function archive(out, b, live, now, getText) {
  const today = naver.seoulDate(now) + (b.edition === 'evening' ? 'pm' : '');
  const days = { [today]: slim(b) };
  const index = await getText(`${SITE}archive/index.json`).then(JSON.parse).catch(() => null);
  const past = ((index && index.days) || []).map((d) => d.ymd).filter((d) => d < today).sort().reverse().slice(0, ARCHIVE_DAYS * 2);
  for (const d of past) {
    const old = await getText(`${SITE}archive/${d}.json`).then(JSON.parse).catch(() => null);
    if (old) days[d] = slim(old);
  }
  // 보관을 처음 시작할 때: 지금 사이트의 원고가 지난 날 것이면 그것부터 남긴다
  if (live && live.generatedAt) {
    const d = naver.seoulDate(Date.parse(live.generatedAt)) + (live.edition === 'evening' ? 'pm' : '');
    if (d < today && !days[d]) days[d] = slim(live);
  }
  const dates = [...new Set(Object.keys(days).map((d) => d.slice(0, 8)))].sort().reverse().slice(0, ARCHIVE_DAYS);
  const keep = Object.keys(days).filter((d) => dates.includes(d.slice(0, 8))).sort().reverse();
  fs.mkdirSync(path.join(out, 'archive'), { recursive: true });
  for (const d of keep) fs.writeFileSync(path.join(out, 'archive', `${d}.json`), JSON.stringify(days[d]));
  const list = keep.map((d) => ({ ymd: d, label: days[d].dateLabel + (days[d].edition === 'evening' ? ' 오후' : '') }));
  fs.writeFileSync(path.join(out, 'archive', 'index.json'), JSON.stringify({ days: list }));
  return list;
}

// 섹션의 boost(정규식 목록, 앞일수록 중요): 제목에 그 말이 든 기사를 언론사 안에서 앞으로 올린다.
// 농업 기사는 비료·탄소중립·벼·과수 기사를 먼저 듣고 싶다 (2026-10-08)
function boostScore(patterns, title) {
  const i = patterns.findIndex((p) => p.test(title));
  return i < 0 ? 0 : patterns.length - i;
}

function boosted(patterns, items) {
  if (!patterns.length) return items;
  return items.map((it, i) => [boostScore(patterns, it.title), i, it]).sort((a, b) => b[0] - a[0] || a[1] - b[1]).map((x) => x[2]);
}

// 아이폰 단축어가 읽는 briefing.txt: 주말·공휴일에는 비워 두어 아무것도 읽지 않게 한다
function speechText(b) {
  return b.autoPlay.play ? b.script : '';
}

function iosPieces(b) {
  const ids = ['open', 'close', ...b.sections.flatMap((sec) => sec.sources.map((x) => x.id))];
  return Object.fromEntries(ids.map((id) => [id, b.autoPlay.play ? pron.say(brief.piece(b, id)) : '']));
}

// 아이폰 단축어(링크로 추가하는 기본 단축어)가 읽는 ios/at/<시>.txt: 지금 시각의 파일을 받아 읽는다.
// 앱과 같은 읽기 시간(6~8시)에만 오늘 원고를 넣고, 다른 시간과 쉬는 날에는 빈 파일이라 아무것도 읽지 않는다
const IOS_HOURS = [6, 7];
function iosHours(b) {
  const text = pron.say(speechText(b));
  return Object.fromEntries([...Array(24).keys()].map((h) => [h, IOS_HOURS.includes(h) ? text : '']));
}

async function collect(config, now = Date.now(), key = '', opts = {}) {
  const log = [];
  const earlier = opts.earlier || {};
  const old = new Set(Object.values(earlier).flat());
  let repeated = 0;
  const autoPlay = await holidays.playDay(now, key);
  const edition = opts.edition || editionOf(now);
  const sections = await Promise.all(config.sections.map(async (sec) => {
    const boost = (sec.boost || []).map((p) => new RegExp(p, 'i'));
    // promo: 행사·대회·기부·협약식 같은 홍보성 기사는 아예 뺀다 (주인 요청 2026-10-12)
    const promo = (sec.promo || []).map((p) => new RegExp(p, 'i'));
    // 오후에는 네이버 언론사 섹션(주요 신문·경제 신문 헤드라인)이 많이 본 뉴스가 된다
    const ranked = edition === 'evening' && sec.sources.some((x) => x.naver);
    return {
      id: sec.id,
      title: ranked ? sec.title.replace(/헤드라인$/, '많이 본 뉴스') : sec.title,
      perSourceLabel: !!sec.perSourceLabel,
      limit: sec.limit,
      sources: await Promise.all(sec.sources.map(async (source) => {
        const lang = source.lang || sec.lang || 'ko';
        const take = source.take || 3;
        const read = await readSource(source, log, lang, now, edition);
        // latest: 검색 결과(관련도 순)를 최신 기사부터
        if (source.latest || sec.latest) read.sort((a, b) => (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0));
        const picked = boosted(boost, rss.pick({ ...sec, limit: 99 }, [{ source: { ...source, lang, take: take + SPARE + 10 }, items: read }], now, []));
        const isOld = (it) => old.has('l:' + it.link) || old.has(titleKey(it.title));
        const isPromo = (it) => promo.some((p) => p.test(it.title));
        repeated += picked.slice(0, take + SPARE).filter(isOld).length;
        const items = picked.filter((it) => !isOld(it) && !isPromo(it)).slice(0, take + SPARE);
        return { id: source.id, name: source.name, lang, default: source.default !== false, take, items, country: source.country || sec.country || '', site: siteOf(source) };
      })),
    };
  }));
  // 번역: 기본 언론사부터, 두 곳씩 차례로 (한꺼번에 보내면 구글 번역이 429로 막는다)
  blocked.clear();
  for (const k of Object.keys(used)) delete used[k];
  const foreign = sections.flatMap((sec) => sec.sources).filter((src) => src.lang !== 'ko');
  const tr = limiter(2);
  for (const batch of [foreign.filter((s) => s.default), foreign.filter((s) => !s.default)]) {
    await Promise.all(batch.map((src) => tr(async () => { src.items = await translateItems(src.name, src.lang, src.items, log); })));
  }
  // 해외 뉴스는 어느 나라 이야기인지 첫머리에 붙여 읽는다 (주인 요청 2026-10-13): "미국, 연준이 금리를 동결했다"
  sections.forEach((sec, i) => {
    if (!config.sections[i].region) return;
    for (const src of sec.sources) {
      for (const it of src.items) {
        it.region = region.regionOf(it.spoken, src.country); // 읽을 제목(美→미국 같은 한자 약칭을 푼 것)에서 찾는다
        it.spoken = region.withRegion(it.spoken, it.region);
      }
      delete src.country;
    }
  });
  for (const sec of sections) for (const src of sec.sources) delete src.country;
  if (old.size) log.push(`전날과 겹친 기사 ${repeated}건을 빼고 다음 기사로 채움`);
  if (foreign.length) log.push(`번역기: ${Object.entries(used).map(([k, n]) => `${k} ${n}번`).join(', ') || '없음'}${blocked.size ? ` (429로 막힘: ${[...blocked].join(', ')})` : ''}`);
  group(sections);
  const b = { version: 2, generatedAt: new Date(now).toISOString(), dateLabel: rss.koreanDate(now), edition, autoPlay, sections, log, earlier };
  // 원고에 들 기사(기본 언론사, 그리고 언론사를 다 골랐을 때)의 구글 뉴스 주소를 원래 기사 주소로 바꾼다.
  // 본문은 받지 않는다: 앱이 들을 때 원래 기사에서 직접 받는다 (저작권, 주인 결정 2026-10-18)
  if (opts.linkLimit) {
    const all = sections.flatMap((s) => s.sources.map((x) => x.id));
    const toResolve = [...new Set([...brief.select(b), ...brief.select(b, all)].flatMap((s) => s.items))];
    await resolveLinks(toResolve, { get: opts.getPage || getPage, log, run: limiter(6), limit: opts.linkLimit });
  }
  b.script = brief.script(b); // 기본 언론사로 만든 원고: briefing.txt(아이폰 단축어, 옛 앱)
  return b;
}

if (require.main === module) {
  (async () => {
    const out = path.resolve(process.argv[2] || 'dist');
    fs.mkdirSync(out, { recursive: true });
    const config = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'feeds.json'), 'utf8'));
    let live = null;
    try { live = JSON.parse(await get(`${SITE}briefing.json`)); } catch (err) { console.log(`지난 원고를 못 받음: ${err.message}`); }
    const b = await collect(config, Date.now(), process.env.DATA_GO_KR_KEY || '', {
      earlier: earlierKeys(live, Date.now()),
      linkLimit: process.env.LINK_LIMIT === undefined ? 300 : Number(process.env.LINK_LIMIT), // 원래 기사 주소를 찾을 구글 뉴스 기사 수
    });
    // 제품 공부 원고(평일 뉴스 뒤 한 회씩, 어느 회를 읽을지는 기기가 진도에 따라 고른다). 녹음에만 원문을 쓰고, 공개 원고에는 잠긴 것만
    const { box, plain } = lessons();
    b.lessons = plain;
    // 원고 줄을 신경망 음성으로 미리 녹음 (기본: 무료 MS 엣지 음성, GOOGLE_TTS_KEY가 있으면 구글, TTS_ENGINE=off면 안 함). 녹음이 없으면 앱이 폰 음성으로 읽는다
    const key = process.env.GOOGLE_TTS_KEY || '';
    b.audio = await tts.record(b, {
      out, key, engine: process.env.TTS_ENGINE || (key ? 'google' : 'edge'), cache: process.env.TTS_CACHE || '', voice: process.env.TTS_VOICE || '',
      budget: Number(process.env.TTS_DAILY_CHARS) || 0, log: b.log, run: limiter(4),
    });
    delete b.lessons; // 원문은 절대 올리지 않는다
    if (box) b.lessonsEnc = box;
    fs.writeFileSync(path.join(out, 'briefing.json'), JSON.stringify(b, null, 1));
    fs.writeFileSync(path.join(out, 'briefing.txt'), speechText(b));
    // 앱의 '당겨서 새로 받기'가 먼저 확인하는 작은 파일: 원고 시각만 (1.3MB 원고를 받기 전에 새 원고가 있는지 안다)
    fs.writeFileSync(path.join(out, 'latest.json'), JSON.stringify({ generatedAt: b.generatedAt, edition: b.edition }));
    const kept = await archive(out, b, live, Date.now(), get);
    console.log(`보관한 원고 ${kept.length}일: ${kept.map((d) => d.ymd).join(', ')}`);
    // 아이폰 단축어가 고른 언론사만 읽도록 언론사마다 조각 원고 (쉬는 날에는 모두 빈 파일)
    fs.mkdirSync(path.join(out, 'ios'), { recursive: true });
    for (const [id, text] of Object.entries(iosPieces(b))) fs.writeFileSync(path.join(out, 'ios', `${id}.txt`), text);
    fs.mkdirSync(path.join(out, 'ios', 'at'), { recursive: true });
    for (const [h, text] of Object.entries(iosHours(b))) fs.writeFileSync(path.join(out, 'ios', 'at', `${h}.txt`), text);
    console.log(`뉴스 브리핑 ${b.dateLabel} (기본 언론사): ` + brief.select(b).map((s) => `${s.title} ${s.items.length}건`).join(' · '));
    console.log(`언론사 ${b.sections.reduce((n, s) => n + s.sources.length, 0)}곳, 기사 ${b.sections.reduce((n, s) => n + s.sources.reduce((m, x) => m + x.items.length, 0), 0)}건`);
    b.log.forEach((l) => console.log('  ' + l));
    console.log(`자동 재생: ${b.autoPlay.play ? '함' : '안 함'} (${b.autoPlay.ymd} ${b.autoPlay.reason}, ${b.autoPlay.source})`);
    console.log(`원고 ${b.script.length}자 (약 ${Math.ceil(b.script.length / 330)}분)`);
    process.exit(0); // 남은 연결이 있어도 배포를 막지 않는다
  })();
}

module.exports = { EDITIONS, editionOf, collect, archive, slim, lessons, boosted, earlierKeys, itemKeys, titleKey, decode, googleUrl, translate, speechText, iosPieces, iosHours, RETRY };
