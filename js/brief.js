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

  // 아나운서처럼: 요일에 맞춘 인사, 섹션마다 다른 연결 말, 언론사 소개, 맺음말 (안드로이드 Brief.java와 같은 문장)
  function opening(date) {
    const extra = date.endsWith('월요일') ? ' 새로운 한 주, 힘차게 시작해 보시죠.' : date.endsWith('금요일') ? ' 한 주의 마무리, 오늘도 힘내세요.' : '';
    return `좋은 아침입니다. ${date}, 출근길 뉴스 브리핑입니다.${extra}`;
  }

  function intro(title, i, n) {
    if (i === 0) return `먼저 ${title}부터 전해 드립니다.`;
    if (i === n - 1) return `끝으로 ${title}입니다.`;
    return `${i % 2 ? '다음은' : '이어서'} ${title}입니다.`;
  }

  const EMPTY = '오늘은 새로 들어온 소식이 없습니다.';
  const closing = (date) => `지금까지 ${date} 아침 뉴스였습니다. 오늘도 안전 운전하시고, 좋은 하루 보내세요.`;

  // 차에서 들을 원고. 문장 끝마다 마침표를 넣어 음성이 잠깐 쉬게 한다
  function buildScript(sections, now) {
    const date = koreanDate(now);
    const lines = [opening(date)];
    sections.forEach((sec, i) => {
      lines.push('');
      lines.push(intro(sec.title, i, sections.length));
      if (!sec.items.length) {
        lines.push(EMPTY);
        return;
      }
      let last = null;
      for (const it of sec.items) {
        const same = sec.perSourceLabel && it.source === last;
        if (sec.perSourceLabel && !same) lines.push(`${it.source} 소식입니다.`);
        last = it.source;
        lines.push((same ? '또, ' : '') + sentence(it.spoken));
      }
    });
    lines.push('');
    lines.push(closing(date));
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

  // 아이폰 단축어용 조각 원고: 단축어가 고른 언론사 조각을 차례로 받아 읽는다 (ios/<id>.txt, 앞뒤에 open·close)
  // 조각마다 따로 만들어서, 같은 사건이 두 언론사에 다 나오면 두 번 읽힐 수 있다
  function piece(b, id) {
    const date = koreanDate(Date.parse(b.generatedAt));
    if (id === 'open') return opening(date) + '\n';
    if (id === 'close') return closing(date) + '\n';
    for (const sec of b.sections) {
      const src = sec.sources.find((x) => x.id === id);
      if (!src) continue;
      const groups = new Set();
      const items = [];
      for (const it of src.items) {
        if (items.length >= src.take) break;
        if (groups.has(it.group)) continue;
        groups.add(it.group);
        items.push(it);
      }
      if (!items.length) return '';
      const head = sec.perSourceLabel ? `${src.name} 소식입니다.` : `${sec.title}입니다.`;
      return [head, ...items.map((it, i) => (i ? '또, ' : '') + sentence(it.spoken))].join('\n') + '\n';
    }
    return '';
  }

  // 단축어에 붙여 넣을 코드: 시작 인사, 고른 언론사(화면 순서), 맺음말
  function iosCode(b, enabled) {
    const on = new Set(enabled || defaults(b));
    const ids = b.sections.flatMap((sec) => sec.sources.filter((x) => on.has(x.id)).map((x) => x.id));
    return ['open', ...ids, 'close'].join(' ');
  }

  return { koreanDate, sentence, buildScript, defaults, select, script, piece, iosCode };
});
