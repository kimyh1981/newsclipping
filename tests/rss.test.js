const test = require('node:test');
const assert = require('node:assert/strict');
const rss = require('../tools/rss.js');

const NOW = Date.parse('2026-10-05T21:00:00Z'); // 한국 시간 10월 6일 화요일 오전 6시

const RSS = `<?xml version="1.0" encoding="UTF-8"?><rss><channel><title>채널</title>
<item><title><![CDATA[[단독] 정부, 비료 가격 안정 대책 발표…농가 부담 줄인다]]></title><link>https://a.kr/1</link><pubDate>Mon, 05 Oct 2026 20:00:00 GMT</pubDate></item>
<item><title>[포토] 가을 들녘</title><link>https://a.kr/2</link><pubDate>Mon, 05 Oct 2026 20:10:00 GMT</pubDate></item>
<item><title>한&#183;미 정상 &quot;통상 협력&quot; 합의(종합2보)</title><link>https://a.kr/3</link><pubDate>Mon, 05 Oct 2026 19:00:00 GMT</pubDate></item>
<item><title>지난주 기사</title><link>https://a.kr/4</link><pubDate>Mon, 28 Sep 2026 19:00:00 GMT</pubDate></item>
</channel></rss>`;

const ATOM = `<feed><entry><title type="html">환율 1,380원&lt;b&gt;↑&lt;/b&gt;</title><link href="https://b.kr/1"/><updated>2026-10-05T22:00:00+09:00</updated></entry></feed>`;

const GOOGLE = `<rss><channel><item><title>코스피 3,000 회복 - 한국경제</title><link>https://news.google.com/x</link><pubDate>Mon, 05 Oct 2026 18:00:00 GMT</pubDate><source url="https://hankyung.com">한국경제</source></item></channel></rss>`;

test('RSS 2.0: CDATA, 엔티티, 날짜를 읽는다', () => {
  const items = rss.parseFeed(RSS);
  assert.equal(items.length, 4);
  assert.equal(items[0].title, '[단독] 정부, 비료 가격 안정 대책 발표…농가 부담 줄인다');
  assert.equal(items[0].link, 'https://a.kr/1');
  assert.equal(items[0].publishedAt, '2026-10-05T20:00:00.000Z');
  assert.equal(items[2].title, '한·미 정상 "통상 협력" 합의(종합2보)');
});

test('Atom: href 링크와 HTML이 섞인 제목', () => {
  const [it] = rss.parseFeed(ATOM);
  assert.equal(it.title, '환율 1,380원 ↑');
  assert.equal(it.link, 'https://b.kr/1');
  assert.equal(it.publishedAt, '2026-10-05T13:00:00.000Z');
});

test('읽기용 제목: 말머리·(종합)·따옴표·말줄임표·기호를 다듬는다', () => {
  assert.equal(rss.spoken('[단독] 정부, 비료 가격 안정 대책 발표…농가 부담 줄인다'), '정부, 비료 가격 안정 대책 발표, 농가 부담 줄인다');
  assert.equal(rss.spoken('한·미 정상 "통상 협력" 합의(종합2보)'), '한미 정상 통상 협력 합의');
  assert.equal(rss.spoken('비료·농약 가격 들썩'), '비료, 농약 가격 들썩');
  assert.equal(rss.spoken('여·야·정 협의체'), '여야정 협의체');
  assert.equal(rss.spoken('환율 1,380원↑'), '환율 1,380원 상승');
  assert.equal(rss.spoken('쌀값 3~5% 오를 듯'), '쌀값 3에서 5% 오를 듯');
  assert.equal(rss.spoken('대상 볶음깍두기 출시→오리온 신제품'), '대상 볶음깍두기 출시, 오리온 신제품');
  assert.equal(rss.spoken('롯데百 매출, 트럼프의 女참모'), '롯데백화점 매출, 트럼프의 여성참모');
  assert.equal(rss.spoken('트럼프의 \uF981참모'), '트럼프의 여성참모', '호환용 한자');
});

test('사진·부고 기사는 건너뛰고, 36시간 넘은 기사도 뺀다', () => {
  assert.ok(rss.skip('[포토] 가을 들녘'));
  assert.ok(rss.skip('[부고] 홍길동씨 별세'));
  assert.ok(!rss.skip('비료값 내린다'));
  const items = rss.parseFeed(RSS);
  assert.ok(rss.fresh(items[0], NOW));
  assert.ok(!rss.fresh(items[3], NOW));
});

test('구글 뉴스 제목 끝의 언론사 이름을 떼고, 출처로 쓴다', () => {
  const sec = { title: '국제 정세와 경제', limit: 5 };
  const items = rss.pick(sec, [{ source: { name: '구글 뉴스', take: 3 }, items: rss.parseFeed(GOOGLE).map((i) => ({ ...i, viaGoogle: true })) }], NOW);
  assert.deepEqual(items.map((i) => [i.title, i.source]), [['코스피 3,000 회복', '한국경제']]);
  assert.equal(rss.splitGoogleSource('국회 - 예산안 처리 - 신문사', ''), '국회 - 예산안 처리');
});

test('비슷한 제목은 섹션을 넘어서도 한 번만, 출처별 take와 섹션 limit을 지킨다', () => {
  assert.ok(rss.similar('정부, 비료 가격 안정 대책 발표', '[속보] 정부 비료가격 안정대책 발표'));
  assert.ok(!rss.similar('정부, 비료 가격 안정 대책 발표', '코스피 3,000 회복'));
  const seen = [];
  const a = rss.pick({ limit: 10 }, [{ source: { name: '가', take: 5 }, items: rss.parseFeed(RSS) }], NOW, seen);
  assert.equal(a.length, 2, '포토와 지난주 기사는 빠진다');
  const dup = [{ title: '정부 비료가격 안정대책 발표', link: 'x', publishedAt: null }, { title: '새 소식 하나', link: 'y', publishedAt: null }, { title: '새 소식 둘', link: 'z', publishedAt: null }];
  const b = rss.pick({ limit: 10 }, [{ source: { name: '나', take: 1 }, items: dup }], NOW, seen);
  assert.deepEqual(b.map((i) => i.title), ['새 소식 하나']);
  const c = rss.pick({ limit: 1 }, [{ source: { name: '다', take: 5 }, items: [{ title: '첫째 소식입니다' }, { title: '둘째 소식입니다' }] }], NOW, []);
  assert.equal(c.length, 1);
});

test('원고: 요일 인사, 섹션마다 다른 연결 말, 언론사 소개, 빈 섹션 안내, 맺음말', () => {
  assert.equal(rss.koreanDate(NOW), '10월 6일 화요일');
  const sections = [
    { title: '주요 신문 헤드라인', perSourceLabel: true, items: [{ source: '조선일보', spoken: '가 기사' }, { source: '조선일보', spoken: '나 기사' }, { source: '한겨레', spoken: '다 기사' }] },
    { title: '농업 신문 헤드라인', perSourceLabel: true, items: [{ source: '농민신문', spoken: '마 기사' }] },
    { title: '비료 관련 뉴스', items: [] },
    { title: '국제 정세와 경제', items: [{ source: '연합뉴스', spoken: '라 기사' }] },
  ];
  assert.equal(rss.buildScript(sections, NOW), [
    '좋은 아침입니다. 10월 6일 화요일, 출근길 뉴스 브리핑입니다.', '',
    '먼저 주요 신문 헤드라인부터 전해 드립니다.', '조선일보 소식입니다.', '가 기사.', '나 기사.', '한겨레 소식입니다.', '다 기사.', '',
    '다음은 농업 신문 헤드라인입니다.', '농민신문 소식입니다.', '마 기사.', '',
    '이어서 비료 관련 뉴스입니다.', '오늘은 새로 들어온 소식이 없습니다.', '',
    '끝으로 국제 정세와 경제입니다.', '라 기사.', '',
    '지금까지 10월 6일 화요일 아침 뉴스였습니다. 오늘도 안전 운전하시고, 좋은 하루 보내세요.', '',
  ].join('\n'));
  const DAY = 24 * 3600e3;
  assert.match(rss.buildScript(sections, NOW - DAY), /^좋은 아침입니다\. 10월 5일 월요일, 출근길 뉴스 브리핑입니다\. 새로운 한 주, 힘차게 시작해 보시죠\.\n/);
  assert.match(rss.buildScript(sections, NOW + 3 * DAY), /^좋은 아침입니다\. 10월 9일 금요일, 출근길 뉴스 브리핑입니다\. 한 주의 마무리, 오늘도 힘내세요\.\n/);
});

test('한자 약칭은 우리말로 읽는다', () => {
  assert.equal(rss.spoken('日, 10년물 국채 금리 인상'), '일본, 10년물 국채 금리 인상');
  assert.equal(rss.spoken('美·中 정상회담…與野 반응'), '미국, 중국 정상회담, 여야 반응');
});

test('일본어판 기사와 한글이 거의 없는 제목은 건너뛴다', () => {
  assert.ok(rss.skip('韓国の科学技術院4校、随時募集志願者が初の3万人超'));
  assert.ok(rss.skip('Samsung unveils new chip'));
  assert.ok(!rss.skip('삼성, 새 반도체 공개'));
});

test('제목이 달라도 같은 사건이면 한 번만 읽고, 다른 사건은 그대로 둔다 (실제 수집 결과)', () => {
  assert.ok(rss.similar('누보, 브라질 콩 옥수수 비료시장 진출 교두보 확보', '농진청 누보, 브라질 스마트 농자재 시장 공략, 콩 옥수수 비료 공동개발'));
  const same = ['7일 개막 J-AGRI TOKYO 국내 농기자재 14개사 참여', '치바 농산업박람회에 한국기업 14개사 참가, K-농기자재 수출시장 개척', 'K농기자재, 日시장 공략, 치바 농산업박람회에 14개사 참가'];
  for (let i = 0; i < same.length; i++) for (let j = i + 1; j < same.length; j++) assert.ok(rss.similar(same[i], same[j]), `${same[i]} / ${same[j]}`);
  const diff = ['강원농업기술원, 비료 토양 분석 신뢰성 입증', '예멘, 원유 수출 길목 모카 탈환, 튀르키예 파키스탄도 파병키로', '내달 미 중간선거, 연방대법원도 변수, 이의제기 소송 봇물 예고', '트럼프, 러시아 병원체 유출에 강도 높게 살펴보고 있다', '시타델 미 국채 금리 상승은 강력한 경제 성장 때문', '청주시의회, 해외연수 취소 약속 어기고 두달만에 재추진', '공수처 중수청이 대통령 등 수사 맡으면 견제 구조 퇴보', '4년간 부동산업 위반 농업법인 300건 육박, 해산청구 강화해야', '동횡성농협, 더덕 판로 넓히고 영농비 부담 낮춘다', '농업용 저수지 물 부족, 373곳 저수율 평년 절반 이하', '농진청, 반려식물 마음돌봄 프로그램 성과 나타나', '가축사육농가 5년새 급감, 양계농가는 반토막', '한국육계협회, 2026 전국 육계인 상생 전진대회 성료', '뇌 심혈관 질환 산재 사망 5년여간 2350명, 한달 36명꼴', '美국방 전문가, 대만군 상주 추진, 대만 미국 무기판매 확신', '대만 中, 부커상 수상 작가 양솽쯔 독일 행사 취소 요구'];
  for (let i = 0; i < diff.length; i++) for (let j = i + 1; j < diff.length; j++) assert.ok(!rss.similar(diff[i], diff[j]), `${diff[i]} / ${diff[j]}`);
});

test('must: 검색에 섞여 든 엉뚱한 기사를 거른다', () => {
  const items = [{ title: '전력수급기본계획을 위한 원전 공론화위원회 출범' }, { title: '강원농업기술원, 비료 토양 분석 신뢰성 입증' }];
  const got = rss.pick({ limit: 5, must: '비료|요소' }, [{ source: { name: '구글 뉴스', take: 5 }, items }], NOW, []);
  assert.deepEqual(got.map((i) => i.title), ['강원농업기술원, 비료 토양 분석 신뢰성 입증']);
});

test('이미 . ? ! 로 끝나는 제목(번역문)에는 마침표를 또 붙이지 않는다', () => {
  assert.equal(rss.sentence('쌀 가격 급등'), '쌀 가격 급등.');
  assert.equal(rss.sentence('경쟁력은 어떻습니까?'), '경쟁력은 어떻습니까?');
  assert.equal(rss.sentence('채권 매각을 중단했습니다.'), '채권 매각을 중단했습니다.');
});

test('출처마다 hours를 따로 줄 수 있다', () => {
  const old = { title: '일주일 전 비료 소식입니다', publishedAt: new Date(NOW - 100 * 3600e3).toISOString() };
  const sec = { limit: 5 };
  assert.equal(rss.pick(sec, [{ source: { name: '가', take: 5 }, items: [old] }], NOW, []).length, 0);
  assert.equal(rss.pick(sec, [{ source: { name: '가', take: 5, hours: 168 }, items: [old] }], NOW, []).length, 1);
});

test('출처마다 must를 따로 줄 수 있다', () => {
  const items = [{ title: 'Giá lúa tăng mạnh' }, { title: 'Giá phân bón giảm' }];
  const got = rss.pick({ limit: 5, lang: 'vi', must: 'lúa|phân bón' }, [{ source: { name: '가', take: 5, must: 'phân bón' }, items }], NOW, []);
  assert.deepEqual(got.map((i) => i.title), ['Giá phân bón giảm']);
});

test('옮긴 말(큰따옴표) 앞뒤에서 잠깐 쉬고, 짧은 강조 낱말은 그냥 읽는다', () => {
  assert.equal(rss.spoken('北 "韓, 자작극으로 책임전가" 거듭발뺌…"영토침범시 즉각대응"(종합)'), '북한, 한국, 자작극으로 책임전가, 거듭발뺌, 영토침범시 즉각대응');
  assert.equal(rss.spoken('추미애·임은정 때리기에 이 대통령 “사심 버려야” 전면전'), '추미애, 임은정 때리기에 이 대통령, 사심 버려야, 전면전');
  assert.equal(rss.spoken('윤호중, 秋 주장에 "그랬다면 제청 안 했을 것"(종합2보)'), '윤호중, 추 주장에, 그랬다면 제청 안 했을 것');
  assert.equal(rss.spoken('중국, 두리안 수확 : 규모와 경쟁력은?'), '중국, 두리안 수확, 규모와 경쟁력은?');
});

test('전체 듣기 문장: 기자 머리말·괄호 덧붙임·한자·따옴표를 다듬어 문장마다 나눈다', () => {
  const body = '수장 없는 중수청 출범엔 "대단히 송구"…공소청, 사건 280건 이첩 예정 양정우 차민지 기자 = 윤호중 장관은 "최선을 다했지만, 대단히 송구하다"고 밝혔다. '
    + '북한은 비무장지대(DMZ) 폭발을 한국의 \'자작극\'이라고 부인했다. 이재명(얼굴) 대통령이 ‘친윤 검사’라며 비판했다. 軍은 30~50세 사원·대리급을 늘린다';
  assert.deepEqual(rss.speechSentences(body), [
    '수장 없는 중수청 출범엔 대단히 송구, 공소청, 사건 280건 이첩 예정.',
    '윤호중 장관은, 최선을 다했지만, 대단히 송구하다고 밝혔다.',
    '북한은 비무장지대 폭발을 한국의 자작극이라고 부인했다.',
    '이재명 대통령이 친윤 검사라며 비판했다.',
    '군은 30에서 50세 사원, 대리급을 늘린다.',
  ]);
  assert.deepEqual(rss.speechSentences(''), []);
});
