/*
 * 기사 요약: 기본 언론사 원고에 든 기사마다 본문을 받아 '자세히' 내용을 미리 만들어 둔다.
 * 앱은 헤드라인을 읽는 중에 '자세히'를 누르면 이 내용을, '전체 듣기'를 누르면 본문 앞부분(body)을 읽는다.
 *  - lead (기본, 무료): 기사 첫 두세 문장(리드). 외국 기사는 구글 번역으로 옮긴다
 *  - claude: Claude가 본문을 우리말 3~4문장으로 요약 (ANTHROPIC_API_KEY 필요, 유료)
 */
const Anthropic = require('@anthropic-ai/sdk');

const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36';
const MODEL = 'claude-opus-5-5';

const SYSTEM = [
  '당신은 아침 출근길 차 안에서 듣는 뉴스 브리핑의 요약 작성자입니다.',
  '기사 본문을 우리말 세 문장에서 네 문장으로 요약합니다. 외국어 기사도 우리말로 씁니다.',
  '귀로 듣기 좋은 평서문으로만 쓰고, 목록·괄호·기호·마크다운은 쓰지 않습니다.',
  '숫자와 고유명사는 본문 그대로 정확히 옮기고, 본문에 없는 내용은 덧붙이지 않습니다.',
  '본문이 기사가 아니라 로그인·구독 안내나 오류 페이지라면 SKIP 한 단어만 씁니다.',
].join('\n');

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

// 기사 페이지에서 본문 문단만 뽑는다: <article> 안의 <p>, 없으면 페이지의 <p>, 그래도 짧으면 meta description
function articleText(html, decodeEntities) {
  const clean = html.replace(/<(script|style|noscript|svg|iframe|form|nav|footer|header|aside)[\s\S]*?<\/\1>/gi, ' ');
  const paras = (scope) => (scope.match(/<p[\s>][\s\S]*?<\/p>/gi) || [])
    .map((p) => decodeEntities(p.replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim())
    .filter((t) => t.length >= 25);
  // 네이버 기사 본문(dic_area)은 문단이 <p>가 아니라 <br>로 나뉜다. 사진 설명은 뺀다
  const dic = clean.match(/<article[^>]*id="dic_area"[^>]*>([\s\S]*?)<\/article>/i);
  if (dic) {
    const lines = dic[1].replace(/<em class="img_desc"[\s\S]*?<\/em>/gi, ' ').replace(/<br\s*\/?>|<\/(?:p|div)>/gi, '\n').replace(/<[^>]+>/g, ' ');
    const out = decodeEntities(lines).split('\n').map((t) => t.replace(/\s+/g, ' ').trim()).filter((t) => t.length >= 25).join('\n');
    if (out.length >= 120) return out.slice(0, 12000);
  }
  const art = clean.match(/<article[\s\S]*?<\/article>/i);
  let text = (art ? paras(art[0]) : []).join('\n');
  if (text.length < 200) text = paras(clean).join('\n');
  if (text.length < 200) {
    const meta = clean.match(/<meta[^>]+(?:property="og:description"|name="description")[^>]+content="([^"]+)"/i);
    if (meta) text = decodeEntities(meta[1]);
  }
  return text.slice(0, 12000); // 기사 본문은 이 안에 든다. 이보다 길면 목록·댓글이 섞인 페이지
}

// 본문 문단에서 기자 이름·통신사 머리말·사진 설명·전자우편·주소를 뺀다
function cleanParas(text) {
  return text.split('\n').map((p) => p
    .replace(/^\s*[\[(【][^\])】]{1,40}[\])】]\s*/, '') // [서울=뉴시스], (사진=연합뉴스)
    .replace(/^[가-힣]{2,4}\s*(?:기자|특파원|객원기자)\s*=?\s*/, '')
    .replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, '')
    .replace(/\([^)]{0,30}(?:사진|제공|=)[^)]{0,30}\)/g, '')
    .replace(/https?:\/\/\S+/g, '')
    .trim()).filter((p) => p.length >= 40 && !/무단\s*전재|재배포\s*금지|저작권자|Copyright|ⓒ|©/i.test(p));
}

const sentencesOf = (paras) => paras.join(' ').match(/[^.?!。]+[.?!。]+["'”’)]*\s*/g) || [];

// '전체 듣기'용 본문: 앞에서부터 문장 단위로 max자까지 (사이트가 공개라 기사 전문을 그대로 옮기지는 않는다)
function body(text, max = 1200) {
  let out = '';
  for (const sen of sentencesOf(cleanParas(text))) {
    if (out && out.length + sen.length > max) break;
    out += sen;
  }
  out = out.replace(/\s+/g, ' ').trim();
  return out.length > max * 1.3 ? `${out.slice(0, max)}.` : out;
}

// 리드: 본문 첫 문장들
function lead(text, max = 320) {
  const sentences = sentencesOf(cleanParas(text));
  let out = '';
  for (const sen of sentences.slice(0, 3)) { // 두세 문장
    if (out && out.length + sen.length > max) break;
    out += sen;
    if (out.length >= max * 0.6) break;
  }
  out = out.trim();
  return out.length > max * 1.5 ? `${out.slice(0, max)}.` : out;
}

async function summarizeOne(client, it, text) {
  const res = await client.beta.messages.create({
    model: MODEL,
    max_tokens: 2000,
    betas: ['server-side-fallback-2026-07-01'],
    fallbacks: 'default', // 안전 분류기가 거절하면 서버가 다른 모델로 이어서 답한다
    output_config: { effort: 'low' }, // 짧은 요약이라 깊게 생각할 필요가 없다
    system: SYSTEM,
    messages: [{ role: 'user', content: `제목: ${it.original || it.title}\n언론사: ${it.source}\n\n본문:\n${text}` }],
  });
  if (res.stop_reason === 'refusal') return null;
  const out = res.content.filter((b) => b.type === 'text').map((b) => b.text).join('').trim();
  if (!out || out === 'SKIP' || out.length < 30) return null;
  return out.replace(/\s*\n+\s*/g, ' ');
}

// items: 요약할 기사(같은 객체에 summary를 채운다). get: (url) => 텍스트 (문자 코드를 맞춰 푼다), translate: (text, lang) => 우리말
async function summarize(items, { mode = 'lead', get, translate, decodeEntities, log, run, key = '', limit = 40 }) {
  const useClaude = mode === 'claude';
  if (useClaude && !key) { log.push('요약: claude로 정했지만 ANTHROPIC_API_KEY가 없어 리드로 대신함'); }
  const client = useClaude && key ? new Anthropic({ apiKey: key, maxRetries: 3 }) : null;
  let ok = 0;
  let badKey = false;
  await Promise.all(items.slice(0, limit).map((it) => run(async () => {
    try {
      const url = await resolveGoogle(it.link, get);
      const text = articleText(await get(url), decodeEntities);
      if (text.length < 120) throw new Error('본문 없음');
      let s = null;
      if (client && !badKey) s = await summarizeOne(client, it, text);
      else {
        s = lead(text);
        if (s && it.lang && it.lang !== 'ko') s = await translate(s, it.lang);
      }
      if (!s || s.length < 30) throw new Error('요약할 본문 아님');
      it.summary = s;
      let full = body(text);
      if (full.length > s.length + 40) {
        if (it.lang && it.lang !== 'ko') full = await translate(full, it.lang).catch(() => '');
        if (full) it.body = full;
      }
      ok++;
    } catch (err) {
      if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
        if (!badKey) log.push(`요약: API 키를 쓸 수 없음 (${err.status})`);
        badKey = true;
        return;
      }
      if (err instanceof Anthropic.APIError) { log.push(`요약 실패 (API ${err.status}) ${it.source} ${it.title}`); return; }
      log.push(`요약 실패 (${err.cause?.code || err.message}) ${it.source} ${it.title}`);
    }
  })));
  log.push(`요약(${client ? 'claude' : 'lead'}): ${Math.min(items.length, limit)}건 중 ${ok}건`);
}

module.exports = { summarize, resolveGoogle, articleText, lead, body, MODEL };
