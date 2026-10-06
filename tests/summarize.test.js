const test = require('node:test');
const assert = require('node:assert/strict');
const { summarize, resolveGoogle, articleText, lead, body } = require('../tools/summarize.js');
const rss = require('../tools/rss.js');

const ARTICLE = `<html><head><meta name="description" content="요약 설명"><script>var x = "<p>스크립트 속 문단은 빼야 합니다 아주 길게 써 둔 문장</p>";</script></head><body>
<nav><p>메뉴 메뉴 메뉴 메뉴 메뉴 메뉴 메뉴 메뉴 메뉴 메뉴</p></nav>
<article><h1>제목</h1>
<p>[서울=뉴시스] 홍길동 기자 = 정부가 내년부터 화학비료 사용을 줄이는 농가에 직불금을 더 주기로 했다. 농림축산식품부는 6일 이런 내용의 친환경 농업 지원 계획을 발표했다.</p>
<p>지원 대상은 미생물 비료와 유기질 비료를 함께 쓰는 농가로, 면적당 지원 단가가 30% 오른다. hong@newsis.com</p>
<p>짧은 줄</p>
<p>농식품부는 올해 안에 세부 기준을 마련해 현장 설명회를 열 계획이다. 업계는 미생물 비료 수요가 늘 것으로 내다봤다.</p>
</article><footer><p>저작권자 무단 전재 및 재배포 금지 저작권자 무단 전재</p></footer></body></html>`;

test('기사 페이지에서 본문 문단만 뽑고, 리드는 첫 두세 문장에서 머리말·기자 이름·전자우편을 뺀다', () => {
  const text = articleText(ARTICLE, rss.decodeEntities);
  assert.doesNotMatch(text, /스크립트|메뉴|저작권|짧은 줄/);
  assert.match(text, /^\[서울=뉴시스\]/);
  const l = lead(text);
  assert.ok(l.startsWith('정부가 내년부터 화학비료'), l);
  assert.doesNotMatch(l, /뉴시스|홍길동|@/);
  assert.ok(l.length <= 480 && l.length >= 60, l);
  assert.match(l, /발표했다\.$|오른다\.$/);
});

test('전체 듣기 본문: 문장 단위로 앞에서부터 한도까지, 머리말·전자우편은 뺀다', () => {
  const text = articleText(ARTICLE, rss.decodeEntities);
  const b = body(text);
  assert.ok(b.startsWith('정부가 내년부터 화학비료'), b);
  assert.match(b, /현장 설명회를 열 계획이다\. 업계는 미생물 비료 수요가 늘 것으로 내다봤다\.$/);
  assert.doesNotMatch(b, /뉴시스|홍길동|@|저작권/);
  const short = body(text, 120);
  assert.ok(short.length <= 160 && /[.]$/.test(short), short);
});

test('구글 뉴스 기사 주소는 서명값으로 원래 기사 주소를 받는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(url, 'https://news.google.com/_/DotsSplashUi/data/batchexecute');
    assert.ok(decodeURIComponent(init.body).includes('\\"CBMiABC\\",1700000000,\\"SIG\\"'));
    const inner = JSON.stringify(['garturlres', 'https://www.nongmin.com/article/123', 1]);
    return new Response(`)]}'\n\n${JSON.stringify([['wrb.fr', 'Fbv4je', inner, null, null, null, 'generic'], ['di', 10], ['af.httprm', 10, '', 1]])}`);
  });
  const get = async (url) => {
    assert.equal(url, 'https://news.google.com/rss/articles/CBMiABC');
    return '<c-wiz><div jscontroller="x" data-n-a-sg="SIG" data-n-a-ts="1700000000"></div></c-wiz>';
  };
  assert.equal(await resolveGoogle('https://news.google.com/rss/articles/CBMiABC?oc=5', get), 'https://www.nongmin.com/article/123');
  assert.equal(await resolveGoogle('https://www.yna.co.kr/view/AKR1', get), 'https://www.yna.co.kr/view/AKR1');
});

test('리드 요약: 키 없이 기사마다 summary를 채우고, 외국 기사는 번역하며, 못 읽은 기사는 기록만 남긴다', async () => {
  const items = [
    { title: '친환경 직불금 확대', source: '뉴시스', link: 'https://a/1' },
    { title: '비료 가격 급등', original: 'Giá phân bón tăng', lang: 'vi', source: '단찌', link: 'https://b/2' },
    { title: '로그인 필요', source: '가', link: 'https://c/3' },
  ];
  const pages = {
    'https://a/1': ARTICLE,
    'https://b/2': '<article><p>Giá phân bón tăng mạnh trong tháng 10 do nguồn cung khan hiếm trên thị trường thế giới. Nông dân lo lắng.</p><p>Nhiều đại lý cho biết giá urê đã tăng 15% so với tháng trước, và dự kiến còn tăng tiếp.</p><p>Bộ Nông nghiệp đang theo dõi tình hình thị trường phân bón để có biện pháp bình ổn.</p></article>',
    'https://c/3': '<p>로그인</p>',
  };
  const log = [];
  await summarize(items, {
    get: async (u) => pages[u],
    translate: async (s, lang) => { assert.equal(lang, 'vi'); return '세계 시장 공급 부족으로 10월 비료 가격이 크게 올랐다. 농민들이 걱정하고 있다.'; },
    decodeEntities: rss.decodeEntities, log, run: (f) => f(), key: 'unused-in-lead-mode',
  });
  assert.match(items[0].summary, /^정부가 내년부터/);
  assert.match(items[1].summary, /^세계 시장 공급 부족/);
  assert.equal(items[2].summary, undefined);
  assert.ok(log.some((l) => l.includes('본문 없음')));
  assert.ok(log.includes('요약(lead): 3건 중 2건'));
});
