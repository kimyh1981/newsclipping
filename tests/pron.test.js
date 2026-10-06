const test = require('node:test');
const assert = require('node:assert');
const Pron = require('../js/pron.js');
const cases = require('./fixtures/pron.json');

// 안드로이드 PronTest도 같은 파일로 맞춰 본다
test('들리는 대로 고친다 (경음화, ㄴ첨가, 띄어 읽기)', () => {
  for (const [input, want] of cases) assert.strictEqual(Pron.say(input), want, input);
});

test('된소리로 바꾸기', () => {
  assert.strictEqual(Pron.tense('수'), '쑤');
  assert.strictEqual(Pron.tense('것'), '껏');
  assert.strictEqual(Pron.tense('나'), '나');
});
