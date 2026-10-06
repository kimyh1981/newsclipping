/*
 * 네이버 뉴스에서 언론사가 직접 고른 헤드라인을 읽는다 (순위·검색이 아니다).
 *  - 신문: '신문보기'의 그날 지면 1면(A1면) 기사
 *  - 지면이 없는 곳(통신사·방송): 언론사 홈의 '주요 뉴스'(언론사가 편집한 맨 위 묶음)
 * 페이지 모양이 바뀌면 빈 목록을 돌려주고, 수집은 RSS·구글 뉴스로 넘어간다.
 */
const { decodeEntities } = require('./rss.js');

const clean = (s) => decodeEntities(s.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();

// 페이지 제목 '한겨레 :: 네이버뉴스'의 언론사 이름. 다른 언론사 번호를 잘못 적었으면 쓰지 않으려고 본다
function pressName(html) {
  const m = html.match(/<title[^>]*>([^<]*)<\/title>/i);
  return m ? clean(m[1]).split('::')[0].trim() : '';
}

const squash = (s) => s.replace(/\s+/g, '').toLowerCase();
function samePress(a, b) {
  const x = squash(a);
  const y = squash(b);
  return !!x && !!y && (x.includes(y) || y.includes(x));
}

function links(block, re) {
  const out = [];
  for (const m of block.matchAll(/<a\s[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g)) {
    if (!re.test(m[1])) continue;
    const t = m[2].match(/<strong[^>]*>([\s\S]*?)<\/strong>/);
    const title = clean(t ? t[1] : m[2]);
    if (title && !out.some((o) => o.link === m[1])) out.push({ title, link: decodeEntities(m[1]) });
  }
  return out;
}

// 신문보기 페이지: 면마다 <h3><span class="page_notation"><em>A1</em>면</span></h3> 다음에 기사 목록이 온다. 1면(A1, 1)만 고른다
function frontPage(html) {
  const pages = [...html.matchAll(/<span class="page_notation"><em>([^<]+)<\/em>\s*면<\/span>/g)];
  const first = pages.find((p) => /^A?0*1$/i.test(p[1].trim()));
  if (!first) return [];
  const next = pages.find((p) => p.index > first.index);
  const block = html.slice(first.index, next ? next.index : first.index + 20000);
  return links(block, /n\.news\.naver\.com\/article\//);
}

// 언론사 홈: 맨 위 '주요 뉴스' 묶음 (press_main_news as_representation)
function mainNews(html) {
  const i = html.search(/class="press_main_news as_representation/);
  if (i < 0) return [];
  const end = html.indexOf('</ul>', i);
  return links(html.slice(i, end < 0 ? i + 20000 : end), /n\.news\.naver\.com\/article\//);
}

// 서울 날짜 20261007
function seoulDate(now) {
  return new Date(now + 9 * 3600e3).toISOString().slice(0, 10).replace(/-/g, '');
}

// src.naver: 네이버 언론사 번호, src.front: 신문이면 true (1면), 아니면 주요 뉴스.
// 돌려주는 기사: { title, link }. 실패하면 이유를 담은 Error
async function read(src, get, now = Date.now()) {
  const base = `https://media.naver.com/press/${src.naver}`;
  const tries = src.front ? [`${base}/newspaper?date=${seoulDate(now)}`, `${base}/newspaper`] : [base];
  let why = '비어 있음';
  for (const url of tries) {
    const html = await get(url);
    const name = pressName(html);
    if (!samePress(name, src.name)) throw new Error(`언론사 이름이 다름 (${name || '없음'})`);
    const items = src.front ? frontPage(html) : mainNews(html);
    if (items.length) return items;
    why = src.front ? '1면 없음' : '주요 뉴스 없음';
  }
  throw new Error(why);
}

module.exports = { read, frontPage, mainNews, pressName, samePress, seoulDate };
