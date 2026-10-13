// 실제 원고에서 숫자·기호가 든 문장을 소리 낼 글로 바꿔 보여 준다 (읽는 법 점검용: node tools/pron-audit.js out/briefing.json)
const Pron = require('../js/pron.js');
const b = require(require('path').resolve(process.argv[2] || 'out/briefing.json'));
const seen = new Set();
for (const s of b.sections) for (const x of s.sources) for (const i of x.items) {
  for (const t of [i.spoken || i.title, ...(i.say || [])]) {
    if (!t || seen.has(t) || !/[\d%$€¥£~:+\-/·()▲■●↑↓&]/.test(t)) continue;
    seen.add(t);
    console.log(`${t}\n  → ${Pron.say(t)}`);
  }
}
