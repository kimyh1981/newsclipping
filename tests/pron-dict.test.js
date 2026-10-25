const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const dict = require('../tools/pron-dict.js');
const pron = require('../js/pron.js');

// 영문 약어·이름·단위 사전은 data/pron-dict.json 하나에서 웹(js/pron.js)과 앱(Pron.java)에 똑같이 들어간다
test('사전: js/pron.js와 Pron.java의 사전 칸이 data/pron-dict.json과 같다 (고쳤으면 node tools/pron-dict.js)', () => {
  const root = path.join(__dirname, '..');
  const { words, units } = dict.load();
  const js = fs.readFileSync(path.join(root, 'js', 'pron.js'), 'utf8');
  const java = fs.readFileSync(path.join(root, 'android', 'app', 'src', 'main', 'java', 'io', 'github', 'kimyh1981', 'newsclipping', 'Pron.java'), 'utf8');
  assert.ok(js.includes(dict.jsBlock({ words, units })), 'js/pron.js 사전 칸이 낡았다');
  assert.ok(java.includes(dict.javaBlock({ words, units })), 'Pron.java 사전 칸이 낡았다');
});

test('사전: 같은 표기가 두 번 없고, 소리는 한글로만', () => {
  const d = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'pron-dict.json'), 'utf8'));
  for (const key of ['words', 'units']) {
    const seen = new Set();
    for (const [w, say] of d[key]) {
      assert.ok(!seen.has(w), `${key} 겹침: ${w}`);
      seen.add(w);
      assert.match(say, /^[가-힣 ]+$/, `${w}의 소리에 한글 밖 글자: ${say}`);
    }
  }
});

test('사전에 없는 대문자 약어는 낱자로, 사전 낱말은 방송 소리로, A/B는 B당', () => {
  assert.equal(pron.say('USDA는 Google과'), '유에스디에이는 구글과');
  assert.equal(pron.say('쌀값 3만 동/kg'), '쌀값 킬로그램당 삼만 동');
  assert.equal(pron.say('질산염 500mg/L'), '질산염 리터당 오백 밀리그램');
  assert.equal(pron.say('풍속 15m/s'), '풍속 초속 십오 미터');
  assert.equal(pron.say('1,200원/kg'), '킬로그램당 천이백 원');
  assert.equal(pron.say('하루 2000kcal'), '하루 이천 킬로칼로리');
});
