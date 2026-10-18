const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { seal, open } = require('../tools/lessons-crypt.js');
const { lessons } = require('../tools/collect.js');

const fixture = (f) => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', f), 'utf8'));

// 제품 공부 잠그기 (퇴사자가 못 보게 사내 공용 코드로, 2026-10-17)
test('제품 공부는 사내 코드로만 풀린다: 틀린 코드는 거부, 암호문에 원문이 보이지 않는다', () => {
  const sample = fixture('lessons-sample.json');
  const box = seal(sample, 'BP-TEST-CODE-1234', { iter: 1000 });
  assert.deepEqual(open(box, 'BP-TEST-CODE-1234'), sample);
  assert.deepEqual(open(box, '  BP-TEST-CODE-1234\n'), sample); // 앞뒤 공백은 무시
  assert.throws(() => open(box, 'BP-TEST-CODE-9999'));
  assert.ok(!JSON.stringify(box).includes('샘플'));
  assert.throws(() => seal(sample, ''), /사내 코드/);
});

test('앱 시험용 잠긴 원고(tests/fixtures/lessons-enc.json)는 test-code로 풀리고 샘플과 같다 (LessonLock.java와 같은 방식)', () => {
  assert.deepEqual(open(fixture('lessons-enc.json'), 'test-code'), fixture('lessons-sample.json'));
});

test('저장소의 제품 공부는 잠긴 것만 있다: 원문 파일이 없고, 코드가 없으면 녹음할 원문도 없다', () => {
  const dir = path.join(__dirname, '..', 'lessons');
  assert.deepEqual(fs.readdirSync(dir), ['lessons.enc.json']);
  assert.ok(!fs.existsSync(path.join(__dirname, '..', 'tools', 'make_english.py')), '농업영어 원문 스크립트도 저장소에 두지 않는다');
  const { box, plain } = lessons('');
  assert.equal(box.v, 1);
  assert.equal(plain, undefined);
  assert.equal(lessons('BP-WRONG-CODE-0000').plain, undefined); // 틀린 코드면 풀지 않고 배포는 계속
});
