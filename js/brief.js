/*
 * 브리핑 원고 만들기: 서버(tools/collect.js)와 웹 화면이 같이 쓴다. 안드로이드 앱의 Brief.java가 같은 규칙을 따른다.
 * briefing.json에는 언론사(source)마다 기사가 들어 있고, 사람마다 고른 언론사로 섹션과 원고를 만든다.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Brief = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const DAYS = ['일', '월', '화', '수', '목', '금', '토'];

  function koreanDate(now) {
    const kst = new Date(now + 9 * 3600e3);
    return `${kst.getUTCMonth() + 1}월 ${kst.getUTCDate()}일 ${DAYS[kst.getUTCDay()]}요일`;
  }

  // 문장 끝에 마침표를 붙이되, 번역문처럼 이미 . ? ! 로 끝나면 그대로 둔다
  function sentence(s) {
    return /[.?!]$/.test(s) ? s : `${s}.`;
  }

  const ORDINAL = ['먼저', '다음은', '이어서', '마지막으로'];

  // 차에서 들을 원고. 문장 끝마다 마침표를 넣어 음성이 잠깐 쉬게 한다
  function buildScript(sections, now) {
    const lines = [`좋은 아침입니다. ${koreanDate(now)} 아침 뉴스 브리핑입니다.`];
    sections.forEach((sec, i) => {
      const lead = i === sections.length - 1 && sections.length > 1 ? ORDINAL[3] : ORDINAL[Math.min(i, 2)];
      lines.push('');
      lines.push(`${lead} ${sec.title}입니다.`);
      if (!sec.items.length) {
        lines.push('오늘은 새 소식이 없습니다.');
        return;
      }
      let last = null;
      for (const it of sec.items) {
        if (sec.perSourceLabel && it.source !== last) lines.push(`${it.source}.`);
        last = it.source;
        lines.push(sentence(it.spoken));
      }
    });
    lines.push('');
    lines.push('이상으로 오늘 아침 브리핑을 마칩니다. 오늘도 안전 운전하세요.');
    return lines.join('\n') + '\n';
  }

  function defaults(b) {
    const ids = [];
    for (const sec of b.sections) for (const src of sec.sources) if (src.default) ids.push(src.id);
    return ids;
  }

  // 고른 언론사(없으면 기본값)로 섹션을 만든다. 고른 언론사가 하나도 없는 섹션은 뺀다.
  // 같은 사건(group)은 처음 나온 곳에서 한 번만, 언론사마다 take건, 섹션마다 limit건까지
  function select(b, enabled) {
    const on = new Set(enabled || defaults(b));
    const groups = new Set();
    const out = [];
    for (const sec of b.sections) {
      const srcs = sec.sources.filter((s) => on.has(s.id));
      if (!srcs.length) continue;
      const items = [];
      for (const src of srcs) {
        let taken = 0;
        for (const it of src.items) {
          if (items.length >= sec.limit || taken >= src.take) break;
          if (groups.has(it.group)) continue;
          groups.add(it.group);
          items.push(it);
          taken++;
        }
      }
      out.push({ id: sec.id, title: sec.title, perSourceLabel: !!sec.perSourceLabel, items });
    }
    return out;
  }

  function script(b, enabled) {
    const now = Date.parse(b.generatedAt);
    const sections = select(b, enabled);
    if (!sections.some((s) => s.items.length)) return `좋은 아침입니다. ${koreanDate(now)}입니다. 오늘은 뉴스를 가져오지 못했습니다. 안전 운전하세요.\n`;
    return buildScript(sections, now);
  }

  return { koreanDate, sentence, buildScript, defaults, select, script };
});
