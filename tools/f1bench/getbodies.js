// 오늘 원고의 국내 기사 몇 건 본문을 원문에서 받아(측정 전용, 저장소 밖) Pron.say를 거친 '읽을 글'로 만든다
const fs = require('fs');
const P = require('../../js/pron.js');
const b = require('./briefing.json');
const pick = [];
for (const s of b.sections) {
  if (!['papers', 'farm'].includes(s.id)) continue;
  for (const src of s.sources) for (const it of src.items) if (it.link && /^https?:/.test(it.link) && !/news\.google/.test(it.link)) pick.push({ src: src.name, title: it.title, link: it.link });
}
const decode = (s) => s.replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&#(\d+);/g, (m, n) => String.fromCharCode(+n));
function body(html) {
  html = html.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>|<!--[\s\S]*?-->/g, ' ');
  // 기사 본문 후보: 한글이 많은 단락
  const paras = html.split(/<\/p>|<br\s*\/?>\s*<br\s*\/?>|<\/div>/i).map((x) => decode(x.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim())
    .filter((t) => (t.match(/[가-힣]/g) || []).length > 40 && /[다요]\.$|[다]\.?["”]?$/.test(t) && !/저작권|무단|구독|기자\s*=|Copyright|ⓒ|©/.test(t));
  return [...new Set(paras)].join('\n\n');
}
(async () => {
  const out = [];
  const lines = fs.readFileSync('briefing.txt', 'utf8').split('\n').map((x) => x.trim()).filter(Boolean);
  lines.slice(0, 40).forEach((l, i) => out.push({ id: 'h' + String(i).padStart(2, '0'), kind: 'headline', said: P.say(l), src: l }));
  let got = 0;
  for (const p of pick) {
    if (got >= 6) break;
    try {
      const r = await fetch(p.link, { headers: { 'User-Agent': 'Mozilla/5.0' }, redirect: 'follow', signal: AbortSignal.timeout(15000) });
      const t = body(await r.text());
      if (t.length < 400) continue;
      const text = t.slice(0, 1400);
      out.push({ id: 'b' + got, kind: 'body', title: p.title, src: text, said: P.say(text) });
      got++;
      console.log('body', p.src, t.length);
    } catch (e) { console.log('fail', p.src, String(e).slice(0, 80)); }
  }
  fs.writeFileSync('said.json', JSON.stringify(out, null, 1));
  console.log('items', out.length);
})();
