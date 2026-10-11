// 임시: 비어 있는 언론사의 실제 주소·RSS를 찾는다 (PR 검사에서만 돌린다)
const UA = { 'user-agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36' };
async function get(url) {
  try {
    const r = await fetch(url, { headers: UA, redirect: 'follow', signal: AbortSignal.timeout(15000) });
    return { status: r.status, url: r.url, body: await r.text() };
  } catch (e) { return { status: 'ERR ' + (e.cause?.code || e.message), body: '' }; }
}
const names = ['한국농업신문', '축산신문', '농기자재신문', '농업인신문', '농촌여성신문'];
const intl = [['农民日报', 'zh-CN', 'CN', 'CN:zh-Hans'], ['AgroPages', 'en-US', 'US', 'US:en']];
const sites = ['https://www.chuksannews.co.kr/', 'https://www.newsam.co.kr/', 'https://www.n-news.co.kr/', 'http://www.n-news.co.kr/', 'https://news.agropages.com/', 'https://www.farmer.com.cn/'];
const paths = ['rss/allArticle.xml', 'rss/S1N1.xml', 'rss', 'rss.xml', 'feed', 'rss/rss.xml'];
(async () => {
  for (const n of names) {
    const r = await get(`https://news.google.com/rss/search?q=${encodeURIComponent(`"${n}"`)}&hl=ko&gl=KR&ceid=KR:ko`);
    const src = [...r.body.matchAll(/<source url="([^"]+)">([^<]+)<\/source>/g)].map((m) => `${m[2]}=${m[1]}`);
    const count = {}; for (const s of src) count[s] = (count[s] || 0) + 1;
    console.log(`[구글 이름검색] ${n}:`, Object.entries(count).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, v]) => `${k} (${v})`).join(' | ') || r.status);
  }
  for (const [n, hl, gl, ceid] of intl) {
    const r = await get(`https://news.google.com/rss/search?q=${encodeURIComponent(n)}&hl=${hl}&gl=${gl}&ceid=${ceid}`);
    const src = [...r.body.matchAll(/<source url="([^"]+)">([^<]+)<\/source>/g)].map((m) => `${m[2]}=${m[1]}`);
    const count = {}; for (const s of src) count[s] = (count[s] || 0) + 1;
    console.log(`[구글 이름검색] ${n}:`, Object.entries(count).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, v]) => `${k} (${v})`).join(' | ') || r.status);
  }
  for (const s of sites) {
    const h = await get(s);
    const links = [...h.body.matchAll(/href="([^"]*(?:rss|feed|xml)[^"]*)"/gi)].map((m) => m[1]).slice(0, 8);
    console.log(`[홈] ${s} → ${h.status} ${h.url || ''} 제목:${(h.body.match(/<title>([^<]{0,60})/i) || [])[1] || ''} RSS링크: ${links.join(' ')}`);
    for (const p of paths) {
      const r = await get(s + p);
      const n = (r.body.match(/<item[\s>]/g) || []).length;
      if (n) console.log(`   ${s + p} → ${r.status} 기사 ${n}건`);
    }
  }
})();
