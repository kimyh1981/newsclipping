const test = require('node:test');
const assert = require('node:assert');
const { regionOf, withRegion } = require('../tools/region.js');

test('해외 뉴스: 제목에서 어느 나라 이야기인지 찾아 첫머리에 붙인다', () => {
  assert.strictEqual(regionOf('트럼프, 관세 인상 발표'), '미국');
  assert.strictEqual(regionOf('지부티 해안에서 이주 보트 전복'), '지부티');
  assert.strictEqual(regionOf('인도네시아 쌀 수출 늘어'), '인도네시아'); // 인도가 아니라
  assert.strictEqual(regionOf('트럼프, 시진핑과 통화', '중국'), '중국'); // 섹션 나라가 제목에 있으면 그 나라
  assert.strictEqual(regionOf('주식 시장에서 주목할 6가지', '미국'), '미국'); // 못 찾으면 언론사·섹션 나라
  assert.strictEqual(regionOf('주식 시장에서 주목할 6가지'), '');
  assert.strictEqual(withRegion('연준이 금리를 동결했다', '미국'), '미국, 연준이 금리를 동결했다');
  assert.strictEqual(withRegion('미국 연준이 금리를 동결했다', '미국'), '미국 연준이 금리를 동결했다');
  assert.strictEqual(withRegion('주목할 6가지', ''), '주목할 6가지');
});

test('해외 언론 기사는 제목에 나라가 없어도 언론사 나라로, 우리나라 이야기면 한국으로 시작한다', () => {
  assert.strictEqual(regionOf('비료 가격, 공급 우려에 급등', '영국'), '영국'); // 로이터
  assert.strictEqual(regionOf('삼성전자 3분기 이익 급증', '영국'), '한국'); // 로이터의 우리나라 기사
  assert.strictEqual(regionOf('트럼프, 한국산 철강에 관세', '영국'), '미국'); // 먼저 나오는 나라
  // 해외 섹션의 해외 언론사는 모두 나라가 정해져 있다 (구글 뉴스 한국판은 우리 언론이라 빼고)
  const feeds = require('../feeds.json');
  const missing = feeds.sections.filter((s) => s.region && !s.country)
    .flatMap((s) => s.sources).filter((x) => !x.country && !/hl=ko/.test(x.url || '')).map((x) => x.id);
  assert.deepStrictEqual(missing, []);
});

test('원문 주소의 나라 도메인으로 해외 언론 기사의 나라를 안다 (.com·구글 뉴스 주소는 모름)', () => {
  const { countryOfLink } = require('../tools/region.js');
  assert.equal(countryOfLink('https://vietnamnews.vn/economy/123.html'), '베트남');
  assert.equal(countryOfLink('https://www.thehindu.co.in/news/a'), '인도');
  assert.equal(countryOfLink('https://www.bbc.co.uk/news/x'), '영국');
  assert.equal(countryOfLink('https://www.chosun.com/x'), '');
  assert.equal(countryOfLink('https://www.yna.co.kr/x'), '');
  assert.equal(countryOfLink('https://news.google.com/rss/articles/abc'), '');
  assert.equal(countryOfLink('not a url'), '');
});

test('해외 뉴스 가운데 한국 이야기는 한국으로 (주인 요청 2026-10-18)', () => {
  assert.equal(regionOf('대한민국 정부, 베트남과 녹색 농업 협력', '베트남'), '베트남'); // 섹션 나라가 제목에 있으면 그 나라
  assert.equal(regionOf('기아, 인도 공장 증설 발표', '미국'), '한국');
  assert.equal(regionOf('K-푸드 수출 사상 최대', '미국'), '한국');
});
