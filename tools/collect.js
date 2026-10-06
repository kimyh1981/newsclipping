#!/usr/bin/env node
/*
 * 아침 뉴스 브리핑 수집. 배포 작업이 매일 새벽 이 스크립트로 RSS를 모아
 * briefing.json(화면용)과 briefing.txt(앱이 읽는 음성 원고)를 만든다.
 * 사용: node tools/collect.js <출력 폴더>
 */
const fs = require('fs');
const path = require('path');
const rss = require('./rss.js');
const holidays = require('./holidays.js');

const UA = 'Mozilla/5.0 (compatible; news-briefing/1.0; +https://github.com/kimyh1981/Personal-Project)';

// 구글 뉴스 검색. lang이 vi면 베트남판(베트남어 기사)에서 찾는다
const REGION = { ko: 'KR', vi: 'VN' };

function googleUrl(q, when = '1d', lang = 'ko') {
  const gl = REGION[lang] || 'KR';
  return `https://news.google.com/rss/search?q=${encodeURIComponent(`${q} when:${when}`)}&hl=${lang}&gl=${gl}&ceid=${gl}:${lang}`;
}

// 외국어 제목을 우리말로 옮긴다 (구글 번역 무료 주소, 키 없음)
async function translate(text, from, to = 'ko') {
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${from}&tl=${to}&dt=t&q=${encodeURIComponent(text)}`;
  const res = await fetch(url, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(10e3) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  const out = (Array.isArray(data?.[0]) ? data[0] : []).map((seg) => seg?.[0] || '').join('').trim();
  if (!out) throw new Error('빈 번역');
  return out;
}

// 번역 섹션: 고른 기사 제목을 우리말로 바꾸고 원문은 original에 남긴다. 번역에 실패한 기사는 읽을 수 없으니 뺀다
async function translateItems(sec, items, log) {
  const done = await Promise.all(items.map(async (it) => {
    try {
      const title = await translate(it.title, sec.lang);
      return { ...it, title, original: it.title, spoken: rss.spoken(title) };
    } catch (err) {
      log.push(`${sec.title}: 번역 실패 (${err.cause?.code || err.message}) ${it.title}`);
      return null;
    }
  }));
  return done.filter((it) => it && it.spoken);
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

// 언론사 RSS를 먼저 읽고, 막히거나 비었으면 구글 뉴스 검색으로 대신한다
async function readSource(src, log, lang) {
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

// 자동 재생(아이폰 단축어·MacroDroid·Tasker)이 읽는 briefing.txt: 주말·공휴일에는 비워 두어 아무것도 읽지 않게 한다
function speechText(b) {
  return b.autoPlay.play ? b.script : '';
}

async function collect(config, now = Date.now(), key = '') {
  const log = [];
  const autoPlay = await holidays.playDay(now, key);
  const fetched = await Promise.all(config.sections.map((sec) => Promise.all(sec.sources.map(async (source) => ({ source, items: await readSource(source, log, sec.lang) })))));
  const seen = [];
  const sections = await Promise.all(config.sections.map(async (sec, i) => {
    let items = rss.pick(sec, fetched[i], now, seen);
    if (sec.lang && sec.lang !== 'ko') items = await translateItems(sec, items, log);
    return { id: sec.id, title: sec.title, perSourceLabel: !!sec.perSourceLabel, items };
  }));
  const total = sections.reduce((n, s) => n + s.items.length, 0);
  const script = total ? rss.buildScript(sections, now) : `좋은 아침입니다. ${rss.koreanDate(now)}입니다. 오늘은 뉴스를 가져오지 못했습니다. 안전 운전하세요.\n`;
  return { generatedAt: new Date(now).toISOString(), dateLabel: rss.koreanDate(now), autoPlay, sections, script, log };
}

if (require.main === module) {
  (async () => {
    const out = path.resolve(process.argv[2] || 'dist');
    fs.mkdirSync(out, { recursive: true });
    const config = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'feeds.json'), 'utf8'));
    const b = await collect(config, Date.now(), process.env.DATA_GO_KR_KEY || '');
    fs.writeFileSync(path.join(out, 'briefing.json'), JSON.stringify(b, null, 1));
    fs.writeFileSync(path.join(out, 'briefing.txt'), speechText(b));
    console.log(`뉴스 브리핑 ${b.dateLabel}: ` + b.sections.map((s) => `${s.title} ${s.items.length}건`).join(' · '));
    b.log.forEach((l) => console.log('  ' + l));
    console.log(`자동 재생: ${b.autoPlay.play ? '함' : '안 함'} (${b.autoPlay.ymd} ${b.autoPlay.reason}, ${b.autoPlay.source})`);
    console.log(`원고 ${b.script.length}자 (약 ${Math.ceil(b.script.length / 330)}분)`);
    if (b.script.length > 3900) console.log('  경고: 안드로이드 음성 엔진(MacroDroid·Tasker)은 약 4,000자까지만 읽습니다. feeds.json의 limit을 줄이세요.');
    process.exit(0); // 남은 연결이 있어도 배포를 막지 않는다
  })();
}

module.exports = { collect, decode, googleUrl, translate, speechText };
