const test = require('node:test');
const assert = require('node:assert');
const report = require('../tools/report.js');
const lock = require('../tools/lessons-crypt.js');
const fixture = require('./fixtures/report-enc.json');

// 세계 비료시장 주간 브리핑: 주인 PC가 잠가 올린 월요일 원고를 그 주에만 싣는다 (2026-11-20)
const at = (iso) => Date.parse(iso); // 서울 시각으로 적은 순간

test('이번 주(월요일부터 6일 안) 원고만 싣는다', () => {
  assert.equal(report.fresh(fixture, at('2026-11-23T05:00:00+09:00')), true); // 그날 아침
  assert.equal(report.fresh(fixture, at('2026-11-29T23:00:00+09:00')), true); // 그 주 일요일
  assert.equal(report.fresh(fixture, at('2026-11-30T05:00:00+09:00')), false); // 다음 주
  assert.equal(report.fresh(fixture, at('2026-11-22T22:00:00+09:00')), false); // 아직 이른 날
  assert.equal(report.fresh({ date: '2026-11-23' }, at('2026-11-23T05:00:00+09:00')), false); // 잠긴 내용 없음
});

test('잠근 원고는 사내 코드로만 풀리고, 근거 표(facts)는 올리지 않는다', () => {
  const doc = { title: '세계 비료시장 주간 브리핑', date: '2026-11-30', segments: [{ name: '가격판', text: '요소는 올랐습니다.', extra: 1 }], facts: [{ what: '내부 메모' }] };
  const sealed = report.sealReport(doc, 'test-code');
  assert.equal(sealed.date, '2026-11-30');
  assert.deepEqual(lock.open(sealed.box, 'test-code'), { title: doc.title, date: doc.date, segments: [{ name: '가격판', text: '요소는 올랐습니다.' }] });
  assert.throws(() => lock.open(sealed.box, 'wrong'));
  assert.throws(() => report.sealReport({ date: '2026-11-30', segments: [] }, 'test-code'));
  assert.ok(!JSON.stringify(sealed).includes('요소'), '원문이 드러나면 안 된다');
});

test('받기: 이번 주 것만 돌려주고, 없거나 오래되면 기록만 남긴다', async () => {
  const log = [];
  const ok = await report.fetchReport(async () => JSON.stringify(fixture), at('2026-11-23T05:00:00+09:00'), log);
  assert.deepEqual(ok, { date: fixture.date, box: fixture.box });
  assert.equal(await report.fetchReport(async () => JSON.stringify(fixture), at('2026-12-01T05:00:00+09:00'), log), null);
  assert.equal(await report.fetchReport(async () => { const e = new Error('HTTP 404'); e.status = 404; throw e; }, at('2026-11-23T05:00:00+09:00'), log), null);
  assert.deepEqual(log, ['비료시장 주간 브리핑: 2026-11-23분 실음', '비료시장 주간 브리핑: 2026-11-23분이라 싣지 않음', '비료시장 주간 브리핑 없음 (404)']);
});
