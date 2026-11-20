const test = require('node:test');
const assert = require('node:assert');
const { decide } = require('../tools/fresh-check.js');

// 늦게 온 예약 실행: 그날 그 판이 이미 있으면 건너뛰고, 없으면(알람 실패) 늦게라도 만든다 (2026-11-20)
const kst = (s) => Date.parse(`${s}+09:00`);
const made = (s, edition) => ({ generatedAt: new Date(kst(s)).toISOString(), edition });

test('오전 10시에 온 예약: 오늘 아침판이 4시 28분에 이미 있으면 건너뛴다', () => {
  assert.equal(decide(made('2026-11-20T04:28:00', 'morning'), kst('2026-11-20T10:56:00')).skip, true);
});

test('알람이 실패해 오늘 아침판이 없으면(어제 오후판뿐) 늦게라도 만든다', () => {
  assert.equal(decide(made('2026-11-19T16:27:00', 'evening'), kst('2026-11-20T10:56:00')).skip, false);
  assert.equal(decide(null, kst('2026-11-20T10:56:00')).skip, false); // 사이트 원고 시각을 모르면 만든다
});

test('오후: 오늘 오후판이 있으면 건너뛰고, 아침판만 있으면 만든다', () => {
  assert.equal(decide(made('2026-11-20T16:27:00', 'evening'), kst('2026-11-20T17:40:00')).skip, true);
  assert.equal(decide(made('2026-11-20T04:28:00', 'morning'), kst('2026-11-20T17:05:00')).skip, false);
});

test('새벽 4시 전(늦게 온 오후 예약이 새벽 2시에)은 만들지 않는다: 아침판은 알람이 만든다', () => {
  assert.equal(decide(made('2026-11-19T16:27:00', 'evening'), kst('2026-11-20T02:14:00')).skip, true);
});
