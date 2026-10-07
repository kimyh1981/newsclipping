const test = require('node:test');
const assert = require('node:assert/strict');
const { collect, decode, googleUrl, earlierKeys, itemKeys } = require('../tools/collect.js');
const brief = require('../js/brief.js');

const NOW = Date.parse('2026-10-05T21:00:00Z');
const item = (t, d = 'Mon, 05 Oct 2026 20:00:00 GMT') => `<item><title>${t}</title><link>https://x/${encodeURIComponent(t)}</link><pubDate>${d}</pubDate></item>`;
const feed = (...titles) => `<?xml version="1.0" encoding="UTF-8"?><rss><channel>${titles.map((t) => item(t)).join('')}</channel></rss>`;

test('EUC-KR RSS도 한글로 푼다', () => {
  const xml = '<?xml version="1.0" encoding="EUC-KR"?><rss><title>';
  const euc = Buffer.concat([Buffer.from(xml), Buffer.from([0xb3, 0xf3, 0xbe, 0xf7]), Buffer.from('</title></rss>')]); // 농업
  assert.match(decode(new Uint8Array(euc)), /<title>농업<\/title>/);
  assert.match(decode(new Uint8Array(Buffer.from('<rss>비료</rss>'))), /비료/);
});

test('언론사 RSS가 막히면 구글 뉴스로 대신하고, 섹션끼리 겹치는 기사는 한 번만 읽는다', async (t) => {
  const pages = {
    'https://paper.kr/rss': null, // 403
    [googleUrl('site:paper.kr')]: feed('국회, 예산안 처리 - 신문사'),
    'https://farm.kr/rss': feed('[포토] 들녘', '벼 수확 한창', '쌀값 반등'),
    [googleUrl('비료')]: feed('비료 가격 안정 대책', '국회, 예산안 처리'),
  };
  t.mock.method(globalThis, 'fetch', async (url) => {
    const body = pages[url];
    if (body === undefined) throw new Error('예상 못한 주소 ' + url);
    if (body === null) return new Response('forbidden', { status: 403 });
    return new Response(body, { headers: { 'content-type': 'application/rss+xml' } });
  });
  const config = { sections: [
    { id: 'papers', title: '주요 신문 헤드라인', perSourceLabel: true, limit: 5, sources: [{ id: 'paper', name: '신문사', url: 'https://paper.kr/rss', google: 'site:paper.kr', take: 2 }] },
    { id: 'farm', title: '농업 신문 헤드라인', perSourceLabel: true, limit: 5, sources: [{ id: 'farm', name: '농업신문', url: 'https://farm.kr/rss', take: 1 }] },
    { id: 'fertilizer', title: '비료 관련 뉴스', limit: 5, sources: [{ id: 'fert', name: '구글 뉴스', google: '비료', take: 5 }] },
  ] };
  const b = await collect(config, NOW);
  const sel = brief.select(b);
  assert.deepEqual(sel.map((s) => s.items.map((i) => i.title)), [['국회, 예산안 처리'], ['벼 수확 한창'], ['비료 가격 안정 대책']]);
  assert.equal(sel[0].items[0].source, '신문사');
  assert.ok(b.log.includes('신문사: RSS 실패 (HTTP 403)'));
  assert.ok(b.log.includes('신문사: 구글 1건'));
  assert.match(b.script, /^좋은 아침입니다\. 10월 6일 화요일, 출근길/);
  assert.match(b.script, /농업신문 소식입니다\.\n벼 수확 한창\./);
});

test('하나도 못 가져오면 그 사실을 말해 준다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('offline'); });
  const b = await collect({ sections: [{ id: 'a', title: '가', limit: 3, sources: [{ id: 'a', name: '가', url: 'https://a' }] }] }, NOW);
  assert.match(b.script, /뉴스를 가져오지 못했습니다/);
});

test('베트남 섹션: 베트남판 구글 뉴스에서 찾아 제목을 우리말로 옮기고, 영상·엉뚱한 기사는 거른다', async (t) => {
  const q = googleUrl('site:dantri.com.vn phân bón', '3d', 'vi');
  assert.match(q, /hl=vi&gl=VN&ceid=VN:vi$/);
  const ko = { 'Giá phân bón tăng mạnh': '비료 가격 급등', 'Nông dân trúng mùa lúa': '농민들 벼 풍작' };
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url === q) return new Response(feed('Giá phân bón tăng mạnh - Báo Dân trí', 'Video: Bão số 5 đổ bộ - Báo Dân trí', 'Nông dân trúng mùa lúa - Báo Dân trí', 'Bóng đá: đội tuyển thắng - Báo Dân trí'));
    if (url.startsWith('https://translate.googleapis.com/')) {
      const src = new URL(url).searchParams.get('q').split('\n');
      assert.equal(new URL(url).searchParams.get('sl'), 'vi');
      calls++;
      if (!src.every((t) => ko[t])) return new Response('busy', { status: 503 });
      // 구글 번역처럼 줄마다 조각으로 돌려준다
      return Response.json([src.map((t, i) => [ko[t] + (i < src.length - 1 ? '\n' : ''), t, null, null])]);
    }
    throw new Error('예상 못한 주소 ' + url);
  });
  const config = { sections: [{ id: 'vietnam', title: '베트남 농업과 비료 뉴스', lang: 'vi', limit: 5, hours: 72, must: 'phân bón|nông dân|lúa', sources: [{ id: 'dantri', name: '단찌', google: 'site:dantri.com.vn phân bón', when: '3d', take: 3 }] }] };
  const b = await collect(config, NOW);
  const items = brief.select(b)[0].items;
  assert.deepEqual(items.map((i) => i.title), ['비료 가격 급등', '농민들 벼 풍작']);
  assert.equal(items[0].original, 'Giá phân bón tăng mạnh');
  assert.equal(items[0].source, '단찌');
  assert.match(b.script, /끝으로|먼저/);
  assert.match(b.script, /비료 가격 급등\.\n농민들 벼 풍작\./);
  assert.doesNotMatch(b.script, /[ăâđêôơư]/i);
  assert.equal(calls, 1, '한 언론사의 제목은 한 번에 번역한다');
});

test('구글 번역이 429면 쉬었다가 다시 보내고, 기본 언론사부터 번역한다', async (t) => {
  const { RETRY } = require('../tools/collect.js');
  const saved = RETRY.waits;
  RETRY.waits = [0, 0];
  t.after(() => { RETRY.waits = saved; });
  const order = [];
  let busy = 1;
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (!url.startsWith('https://translate.googleapis.com/')) return new Response(feed(url.includes('extra') ? 'Extra news' : 'Main news'));
    const q = new URL(url).searchParams.get('q');
    if (busy-- > 0) return new Response('', { status: 429 });
    order.push(q);
    return Response.json([[[`번역 ${q}`, q]]]);
  });
  const b = await collect({ sections: [{ id: 'w', title: '국제', lang: 'en', limit: 5, sources: [
    { id: 'x', name: '추가', url: 'https://extra', default: false },
    { id: 'm', name: '기본', url: 'https://main' },
  ] }] }, NOW);
  assert.deepEqual(order, ['Main news', 'Extra news']);
  assert.deepEqual(b.sections[0].sources.map((s) => s.items.map((i) => i.title)), [['번역 Extra news'], ['번역 Main news']]);
});

test('번역이 막히면 그 기사는 읽지 않고 기록만 남긴다', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => url === 'https://vn' ? new Response(feed('Giá phân bón tăng mạnh')) : new Response('', { status: 503 }));
  const b = await collect({ sections: [{ id: 'vn', title: '베트남', lang: 'vi', limit: 3, sources: [{ id: 'vn', name: '가', url: 'https://vn' }] }] }, NOW);
  assert.equal(b.sections[0].sources[0].items.length, 0);
  assert.ok(b.log.some((l) => l.includes('번역 실패 (HTTP 503)')));
});

test('구글 번역이 계속 429면 그날은 건너뛰고 다른 번역기로 옮긴다', async (t) => {
  const { RETRY } = require('../tools/collect.js');
  const saved = RETRY.waits;
  RETRY.waits = [0];
  t.after(() => { RETRY.waits = saved; });
  const hits = { google: 0, dict: 0 };
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url.startsWith('https://translate.googleapis.com/')) { hits.google++; return new Response('', { status: 429 }); }
    if (url.startsWith('https://clients5.google.com/')) {
      hits.dict++;
      return Response.json([[`번역 ${new URL(url).searchParams.get('q')}`, 'en']]);
    }
    return new Response(feed(url.includes('a') ? 'Alpha news' : 'Beta news'));
  });
  const b = await collect({ sections: [{ id: 'w', title: '국제', lang: 'en', limit: 5, sources: [
    { id: 'a', name: '가', url: 'https://a' },
    { id: 'b', name: '나', url: 'https://b', default: false },
  ] }] }, NOW);
  assert.deepEqual(b.sections[0].sources.map((s) => s.items.map((i) => i.title)), [['번역 Alpha news'], ['번역 Beta news']]);
  assert.equal(hits.google, 2, '429가 두 번 오면 그 뒤로는 구글 번역에 보내지 않는다');
  assert.equal(hits.dict, 2);
  assert.ok(b.log.includes('번역기: google-dict 2번 (429로 막힘: google)'), b.log.join('\n'));
});

test('언론사마다 기사를 따로 담고, 고른 언론사만으로 원고를 만든다 (같은 사건은 한 번만)', async (t) => {
  const pages = {
    'https://a/rss': feed('정부, 비료 가격 안정 대책 발표', '국회 예산안 처리'),
    'https://b/rss': feed('[속보] 정부 비료가격 안정대책 발표', '쌀값 반등', '벼 수확 한창'),
  };
  t.mock.method(globalThis, 'fetch', async (url) => new Response(pages[url] || feed()));
  const config = { sections: [
    { id: 'p', title: '주요 신문 헤드라인', perSourceLabel: true, limit: 5, sources: [{ id: 'a', name: '가신문', url: 'https://a/rss', take: 2 }] },
    { id: 'f', title: '농업 신문 헤드라인', perSourceLabel: true, limit: 5, sources: [{ id: 'b', name: '나신문', url: 'https://b/rss', take: 1, default: false }] },
  ] };
  const b = await collect(config, NOW);
  assert.equal(b.version, 2);
  assert.deepEqual(b.sections[1].sources[0].items.map((i) => i.title), ['[속보] 정부 비료가격 안정대책 발표', '쌀값 반등', '벼 수확 한창'], '언론사마다 take보다 넉넉히 모은다');
  assert.equal(b.sections[1].sources[0].items[0].group, b.sections[0].sources[0].items[0].group);
  assert.deepEqual(brief.defaults(b), ['a']);
  assert.doesNotMatch(b.script, /농업 신문/, '기본값에 없는 섹션은 원고에서 빠진다');
  const both = brief.select(b, ['a', 'b']);
  assert.deepEqual(both[1].items.map((i) => i.title), ['쌀값 반등'], '겹치는 기사는 건너뛰고 take건');
  const onlyB = brief.select(b, ['b']);
  assert.deepEqual(onlyB.map((s) => s.items.map((i) => i.title)), [['[속보] 정부 비료가격 안정대책 발표']]);
  assert.match(brief.script(b, ['b']), /^좋은 아침입니다\. 10월 6일 화요일, 출근길 뉴스 브리핑입니다\.\n\n먼저 농업 신문 헤드라인부터 전해 드립니다\.\n나신문 소식입니다\./);
  assert.match(brief.script(b, []), /뉴스를 가져오지 못했습니다/);
  assert.equal(brief.piece(b, 'b'), '나신문 소식입니다.\n정부 비료가격 안정대책 발표.\n', '아이폰 조각은 언론사 하나만, take건');
  assert.equal(brief.piece(b, 'a'), '가신문 소식입니다.\n정부, 비료 가격 안정 대책 발표.\n국회 예산안 처리.\n');
  assert.equal(brief.piece(b, 'zz'), '');
  assert.equal(brief.iosCode(b), 'open a close');
  assert.equal(brief.iosCode(b, ['b', 'a']), 'open a b close', '화면 순서대로');
});

test('feeds.json: 언론사 id가 모두 있고 겹치지 않으며, 외국 언론은 번역할 언어가 정해져 있다', () => {
  const cfg = JSON.parse(require('fs').readFileSync(require('path').join(__dirname, '..', 'feeds.json'), 'utf8'));
  const ids = cfg.sections.flatMap((s) => s.sources.map((x) => x.id));
  assert.ok(ids.every((id) => /^[a-z0-9-]+$/.test(id)), ids.join());
  assert.equal(new Set(ids).size, ids.length);
  for (const s of cfg.sections) for (const x of s.sources) {
    assert.ok(x.url || x.google, x.id);
    const lang = x.lang || s.lang || 'ko';
    assert.ok(['ko', 'en', 'vi', 'zh', 'ja'].includes(lang), x.id);
  }
  assert.equal(cfg.sections.at(-1).id, 'vietnam', '베트남 농업·비료는 맨 마지막');
});

// 안드로이드 앱(BriefTest.java)도 같은 파일로 같은 원고가 나오는지 확인한다
test('원고 규칙 고정: tests/fixtures의 브리핑으로 기본값·고른 언론사 원고가 기대한 그대로 나온다', () => {
  const fs = require('fs');
  const dir = require('path').join(__dirname, 'fixtures');
  const b = JSON.parse(fs.readFileSync(`${dir}/briefing.json`, 'utf8'));
  for (const [name, sel] of [['default', null], ['b-c-d', ['b', 'c', 'd']], ['none', []]]) {
    const want = `${dir}/script-${name}.txt`;
    if (process.env.UPDATE_FIXTURES) fs.writeFileSync(want, brief.script(b, sel));
    assert.equal(brief.script(b, sel), fs.readFileSync(want, 'utf8'), name);
  }
});

test('전날 원고에 나온 기사(같은 링크나 제목)는 빼고 그다음 기사로 채운다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(feed('어제 1면 기사', '오늘 새 기사', '또 다른 새 기사')));
  const config = { sections: [{ id: 'a', title: '가', perSourceLabel: true, limit: 5, sources: [{ id: 'a', name: '가', url: 'https://a', take: 2 }] }] };
  const yesterday = { generatedAt: '2026-10-04T21:00:00Z', sections: [{ sources: [{ items: [{ title: '어제 1면 기사!', link: 'https://other' }] }] }] };
  const earlier = earlierKeys(yesterday, NOW);
  assert.deepEqual(Object.keys(earlier), ['20261005']);
  const b = await collect(config, NOW, '', { earlier });
  assert.deepEqual(b.sections[0].sources[0].items.map((i) => i.title), ['오늘 새 기사', '또 다른 새 기사']);
  assert.ok(b.log.includes('전날과 겹친 기사 1건을 빼고 다음 기사로 채움'));
  assert.deepEqual(b.earlier, earlier); // 다음 날로 넘겨준다
});

test('지난 원고 넘겨받기: 같은 날 다시 배포하면 그날 것은 빼고, 최근 이틀만 남긴다', () => {
  const sameDay = { generatedAt: '2026-10-05T20:00:00Z', earlier: { 20261003: ['t:a'], 20261004: ['t:b'], 20261005: ['t:c'] }, sections: [{ sources: [{ items: [{ title: '오늘 것', link: 'l' }] }] }] };
  assert.deepEqual(earlierKeys(sameDay, NOW), { 20261004: ['t:b'], 20261005: ['t:c'] });
  const prevDay = { generatedAt: '2026-10-05T00:30:00Z', earlier: { 20261004: ['t:b'] }, sections: [{ sources: [{ items: [{ title: 'Hello', original: 'Hello!', link: 'x' }] }] }] };
  assert.deepEqual(earlierKeys(prevDay, NOW), { 20261004: ['t:b'], 20261005: ['l:x', 't:hello'] });
  assert.deepEqual(earlierKeys(null, NOW), {});
  assert.deepEqual(itemKeys({}), []);
});
