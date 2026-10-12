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
