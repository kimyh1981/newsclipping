// 임시: 비어 있는 언론사의 실제 주소·RSS를 찾는다 (PR 검사에서만 돌린다)
const UA = { 'user-agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36' };
async function get(url) {
  try {
    const r = await fetch(url, { headers: UA, redirect: 'follow', signal: AbortSignal.timeout(15000) });
    return { status: r.status, url: r.url, body: await r.text() };
  } catch (e) { return { status: 'ERR ' + (e.cause?.code || e.message), body: '' }; }
}
const rss = [
  'https://www.chuksannews.co.kr/data/rss/news.xml', 'https://www.newsam.co.kr/data/rss/news.xml',
  'https://www.newsfarm.co.kr/rss/allArticle.xml', 'https://www.newsfarm.co.kr/data/rss/news.xml', 'http://www.newsfarm.co.kr/rss/allArticle.xml',
  'https://news.agropages.com/Rss/News.xml', 'https://news.agropages.com/rss', 'https://news.agropages.com/Feed', 'https://cn.agropages.com/rss',
  'https://www.farmer.com.cn/rss', 'http://www.farmer.com.cn/', 'https://szb.farmer.com.cn/', 'https://www.moa.gov.cn/xw/zwdt/',
];
const google = [
  ['site:newsfarm.co.kr', 'ko', 'KR', 'KR:ko'], ['site:newsfarm.co.kr when:7d', 'ko', 'KR', 'KR:ko'],
  ['site:agropages.com', 'en-US', 'US', 'US:en'], ['agropages', 'en-US', 'US', 'US:en'], ['site:news.agropages.com', 'en-US', 'US', 'US:en'],
  ['site:farmer.com.cn', 'zh-CN', 'CN', 'CN:zh-Hans'], ['农民日报 when:7d', 'zh-CN', 'CN', 'CN:zh-Hans'], ['中国农网', 'zh-CN', 'CN', 'CN:zh-Hans'],
  ['site:farmer.com.cn', 'ko', 'KR', 'KR:ko'],
];
(async () => {
  for (const u of rss) {
    const r = await get(u);
    const n = (r.body.match(/<item[\s>]/g) || []).length;
    const first = (r.body.match(/<item[\s\S]*?<title>(?:<!\[CDATA\[)?([^<\]]{0,50})/) || [])[1] || '';
    const date = (r.body.match(/<pubDate>([^<]+)/) || [])[1] || '';
    console.log(`[RSS] ${u} → ${r.status} ${r.url || ''} 기사 ${n}건 ${date} ${first} ${n ? '' : (r.body.match(/<title>([^<]{0,50})/i) || [])[1] || ''}`);
  }
  for (const [q, hl, gl, ceid] of google) {
    const r = await get(`https://news.google.com/rss/search?q=${encodeURIComponent(q)}&hl=${hl}&gl=${gl}&ceid=${ceid}`);
    const items = [...r.body.matchAll(/<item>[\s\S]*?<pubDate>([^<]+)[\s\S]*?<source url="([^"]+)">/g)];
    console.log(`[구글] ${q} (${gl}) → ${r.status} ${items.length}건 최신:${items[0]?.[1] || ''} ${[...new Set(items.map((m) => m[2]))].slice(0, 3).join(' ')}`);
  }
})();
