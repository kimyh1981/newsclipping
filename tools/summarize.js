/*
 * 원래 기사 주소 찾기: 서버는 기사 본문을 받거나 저장·녹음·공개하지 않는다 (저작권, 주인 결정 2026-10-18).
 * 기사마다 제목·언론사·원래 기사 주소·시각만 두고, 본문은 앱이 들을 때 원래 기사에서 직접 받아 읽는다.
 * 구글 뉴스 기사는 주소가 news.google.com으로 감춰져 있어 원래 기사 주소로 바꿔 둔다 (본문은 받지 않는다).
 */
const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36';
const GOOGLE = /^https?:\/\/news\.google\.com\//;

// 구글 뉴스 기사 주소(news.google.com/rss/articles/…)는 원래 기사 주소를 숨긴다: 구글 뉴스 화면의 서명값으로 원래 주소를 받는다
async function resolveGoogle(link, get) {
  const m = link.match(/news\.google\.com\/(?:rss\/)?articles\/([^?/#]+)/);
  if (!m) return link;
  const id = m[1];
  const page = await get(`https://news.google.com/rss/articles/${id}`);
  const sg = (page.match(/data-n-a-sg="([^"]+)"/) || [])[1];
  const ts = (page.match(/data-n-a-ts="([^"]+)"/) || [])[1];
  if (!sg || !ts) throw new Error('구글 뉴스 서명 없음');
  const req = [[['Fbv4je', `["garturlreq",[["X","X",["X","X"],null,null,1,1,"US:en",null,1,null,null,null,null,null,0,1],"X","X",1,[1,1,1],1,1,null,0,0,null,0],"${id}",${ts},"${sg}"]`]]];
  const res = await fetch('https://news.google.com/_/DotsSplashUi/data/batchexecute', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded;charset=UTF-8', 'User-Agent': UA },
    body: `f.req=${encodeURIComponent(JSON.stringify(req))}`,
    signal: AbortSignal.timeout(15e3),
  });
  if (!res.ok) throw new Error(`구글 뉴스 HTTP ${res.status}`);
  const body = (await res.text()).split('\n\n')[1];
  const url = JSON.parse(JSON.parse(body)[0][2])[1];
  if (!/^https?:\/\//.test(url)) throw new Error('구글 뉴스 주소 풀기 실패');
  return url;
}

// items: 원고에 들 기사(같은 객체의 link를 원래 기사 주소로 바꾼다). get: (url) => 텍스트. 구글 뉴스 주소인 기사만 요청한다
async function resolveLinks(items, { get, log, run, limit = 300 }) {
  const todo = items.filter((it) => GOOGLE.test(it.link || '')).slice(0, limit);
  if (!todo.length) return;
  let ok = 0;
  await Promise.all(todo.map((it) => run(async () => {
    try {
      it.link = await resolveGoogle(it.link, get);
      ok++;
    } catch (err) {
      log.push(`원래 기사 주소 못 찾음 (${err.cause?.code || err.message}) ${it.source} ${it.title}`); // 구글 뉴스 주소 그대로 둔다
    }
  })));
  log.push(`원래 기사 주소: 구글 뉴스 기사 ${todo.length}건 중 ${ok}건`);
}

module.exports = { resolveLinks, resolveGoogle };
