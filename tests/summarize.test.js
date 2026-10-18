const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveLinks, resolveGoogle } = require('../tools/summarize.js');

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

test('원래 기사 주소 찾기: 구글 뉴스 기사만 요청하고, 못 찾으면 구글 주소를 그대로 두며, 본문은 붙이지 않는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    const id = decodeURIComponent(init.body).match(/\\"(CBMi\w+)\\"/)[1];
    const inner = JSON.stringify(['garturlres', `https://origin.kr/${id}`, 1]);
    return new Response(`)]}'\n\n${JSON.stringify([['wrb.fr', 'Fbv4je', inner]])}`);
  });
  const asked = [];
  const get = async (url) => {
    asked.push(url);
    if (url.endsWith('CBMiBAD')) return '<html>서명 없음</html>';
    return '<div data-n-a-sg="S" data-n-a-ts="1"></div>';
  };
  const items = [
    { title: '구글 기사', source: '가', link: 'https://news.google.com/rss/articles/CBMiOK?oc=5' },
    { title: '막힌 구글 기사', source: '나', link: 'https://news.google.com/rss/articles/CBMiBAD?oc=5' },
    { title: '네이버 기사', source: '다', link: 'https://n.news.naver.com/article/001/1' },
  ];
  const log = [];
  await resolveLinks(items, { get, log, run: (f) => f() });
  assert.deepEqual(items.map((i) => i.link), ['https://origin.kr/CBMiOK', 'https://news.google.com/rss/articles/CBMiBAD?oc=5', 'https://n.news.naver.com/article/001/1']);
  assert.deepEqual(asked, ['https://news.google.com/rss/articles/CBMiOK', 'https://news.google.com/rss/articles/CBMiBAD']);
  assert.ok(items.every((i) => !('summary' in i) && !('body' in i) && !('say' in i)));
  assert.match(log.join('\n'), /원래 기사 주소 못 찾음 \(구글 뉴스 서명 없음\) 나 막힌 구글 기사/);
  assert.ok(log.includes('원래 기사 주소: 구글 뉴스 기사 2건 중 1건'));
});
