#!/usr/bin/env node
// 영문 약어·이름·단위 읽기 사전(data/pron-dict.json)을 js/pron.js와 Pron.java의 '사전' 칸에 똑같이 써 넣는다.
// 사전만 고치고 `node tools/pron-dict.js`를 돌리면 웹·앱이 같은 소리로 읽는다. `--check`는 두 파일이 사전과 같은지만 본다(시험용).
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const DATA = path.join(root, 'data', 'pron-dict.json');
const JS = path.join(root, 'js', 'pron.js');
const JAVA = path.join(root, 'android', 'app', 'src', 'main', 'java', 'io', 'github', 'kimyh1981', 'newsclipping', 'Pron.java');
const START = '// <사전: data/pron-dict.json에서 tools/pron-dict.js가 만든다. 손으로 고치지 말 것>';
const END = '// </사전>';

/** 사전을 읽어 겹침을 없애고 긴 것부터 줄 세운다 (짧은 것이 긴 것의 일부를 먼저 바꾸지 않게) */
function load(file = DATA) {
  const d = JSON.parse(fs.readFileSync(file, 'utf8'));
  const words = new Map();
  for (const [w, say] of d.words || []) if (w && say && !words.has(w)) words.set(w, say);
  const units = new Map();
  for (const [u, say, alone, pre] of d.units || []) if (u && say && !units.has(u)) units.set(u, [say, alone ? 1 : 0, pre || '']);
  const byLen = (a, b) => b[0].length - a[0].length || (a[0] < b[0] ? -1 : 1);
  return {
    words: [...words].sort(byLen),
    units: [...units].map(([u, [say, alone, pre]]) => [u, say, alone, pre]).sort(byLen),
  };
}

const q = (s) => JSON.stringify(s);

function jsBlock({ words, units }) {
  const lines = [START, `  const DICT_WORDS = [`];
  for (let i = 0; i < words.length; i += 6) lines.push('    ' + words.slice(i, i + 6).map(([w, s]) => `[${q(w)}, ${q(s)}]`).join(', ') + ',');
  lines.push('  ];', '  // 단위: [쓴 글, 숫자 뒤에서 읽는 말, 숫자 없이도 바꿀지(1), 숫자 앞에 붙는 말(시속·리터당)]', '  const DICT_UNITS = [');
  for (let i = 0; i < units.length; i += 5) lines.push('    ' + units.slice(i, i + 5).map(([u, s, a, p]) => `[${q(u)}, ${q(s)}, ${a}, ${q(p)}]`).join(', ') + ',');
  lines.push('  ];', '  ' + END);
  return '  ' + lines.join('\n');
}

// 자바는 큰 표를 배열 상수로 쓰면 클래스 초기화 코드가 64KB 한도에 걸린다: 줄마다 '쓴 글\t소리'로 이은 글 덩이(150줄씩)를 실행 때 나눈다
function javaBlock({ words, units }) {
  const chunks = (rows) => {
    const out = [];
    for (let i = 0; i < rows.length; i += 150) out.push('        ' + q(rows.slice(i, i + 150).map((r) => r.join('\t')).join('\n')) + ',');
    return out;
  };
  const lines = [START, '    private static final String[] DICT_WORDS_SRC = {'];
  lines.push(...chunks(words));
  lines.push('    };', '    /** 단위: 쓴 글, 숫자 뒤에서 읽는 말, 숫자 없이도 바꿀지("1"), 숫자 앞에 붙는 말(시속·리터당) */', '    private static final String[] DICT_UNITS_SRC = {');
  lines.push(...chunks(units.map(([u, s, a, p]) => [u, s, String(a), p])));
  lines.push('    };', '    private static final String[][] DICT_WORDS = rows(DICT_WORDS_SRC, 2), DICT_UNITS = rows(DICT_UNITS_SRC, 4);', '    ' + END);
  return '    ' + lines.join('\n');
}

/** 파일의 '사전' 칸을 새 내용으로 바꾼 글 (칸이 없으면 오류) */
function fill(text, block, indent) {
  const a = text.indexOf(START);
  const b = text.indexOf(END, a);
  if (a < 0 || b < 0) throw new Error('사전 칸 표시가 없다');
  const lineStart = text.lastIndexOf('\n', a) + 1;
  return text.slice(0, lineStart) + block + text.slice(b + END.length);
}

function run(check) {
  const dict = load();
  let stale = [];
  for (const [file, block] of [[JS, jsBlock(dict)], [JAVA, javaBlock(dict)]]) {
    const old = fs.readFileSync(file, 'utf8');
    const now = fill(old, block);
    if (now !== old) {
      stale.push(path.relative(root, file));
      if (!check) fs.writeFileSync(file, now, 'utf8');
    }
  }
  return { stale, words: dict.words.length, units: dict.units.length };
}

if (require.main === module) {
  const check = process.argv.includes('--check');
  const r = run(check);
  console.log(`사전: 낱말 ${r.words}개, 단위 ${r.units}개` + (r.stale.length ? (check ? ` · 사전과 다름: ${r.stale.join(', ')}` : ` · 고쳐 씀: ${r.stale.join(', ')}`) : ' · 두 파일 모두 사전과 같음'));
  if (check && r.stale.length) process.exit(1);
}

module.exports = { load, run, jsBlock, javaBlock };
