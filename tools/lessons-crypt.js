/**
 * 바이오플랜 제품 공부·농업영어 원고 잠그기 (주인 결정 2026-10-17: 퇴사자가 못 보게, 사내 공용 코드 A안).
 * 저장소와 공개 원고(briefing.json)에는 암호화된 것만 둔다. 원문은 주인 PC의 _lessons/ (git 제외).
 *
 * 방식: AES-256-GCM, 열쇠 = PBKDF2-HMAC-SHA256(사내 코드, salt, 150000번). 앱(LessonLock.java)이 같은 방식으로 푼다.
 * 꼴: { v: 1, iter, salt, iv, data } (모두 base64, data = 암호문 + 16바이트 꼬리표)
 *
 * 쓰는 법 (사내 코드는 환경 변수 LESSON_CODE 또는 --code-file 파일):
 *   node tools/lessons-crypt.js seal  --code-file _keys/lesson-code.txt   # _lessons/*.json → lessons/lessons.enc.json
 *   node tools/lessons-crypt.js open  --code-file _keys/lesson-code.txt   # lessons/lessons.enc.json → _lessons/*.json
 * 사내 코드를 바꿀 때(퇴사자): 새 코드로 seal 하고, 저장소 비밀값 LESSON_CODE를 새 코드로 바꾸고, 직원들에게 새 코드를 알린다.
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const ITER = 150000;
const ROOT = path.join(__dirname, '..');
const ENC = path.join(ROOT, 'lessons', 'lessons.enc.json');
const PLAIN = path.join(ROOT, '_lessons');

function key(code, salt, iter) {
  return crypto.pbkdf2Sync(Buffer.from(String(code).trim(), 'utf8'), salt, iter, 32, 'sha256');
}

/** obj → 잠긴 꼴. salt·iv는 시험할 때만 정해 준다 (보통은 매번 새로) */
function seal(obj, code, { salt = crypto.randomBytes(16), iv = crypto.randomBytes(12), iter = ITER } = {}) {
  if (!String(code || '').trim()) throw new Error('사내 코드가 없습니다');
  const c = crypto.createCipheriv('aes-256-gcm', key(code, salt, iter), iv);
  const data = Buffer.concat([c.update(Buffer.from(JSON.stringify(obj), 'utf8')), c.final(), c.getAuthTag()]);
  return { v: 1, iter, salt: salt.toString('base64'), iv: iv.toString('base64'), data: data.toString('base64') };
}

/** 잠긴 꼴 → obj. 코드가 틀리면 오류 */
function open(box, code) {
  const salt = Buffer.from(box.salt, 'base64'), iv = Buffer.from(box.iv, 'base64'), all = Buffer.from(box.data, 'base64');
  const d = crypto.createDecipheriv('aes-256-gcm', key(code, salt, box.iter || ITER), iv);
  d.setAuthTag(all.subarray(all.length - 16));
  return JSON.parse(Buffer.concat([d.update(all.subarray(0, all.length - 16)), d.final()]).toString('utf8'));
}

function codeFrom(args) {
  const i = args.indexOf('--code-file');
  if (i >= 0) return fs.readFileSync(path.resolve(args[i + 1]), 'utf8').trim();
  return (process.env.LESSON_CODE || '').trim();
}

if (require.main === module) {
  const [cmd, ...args] = process.argv.slice(2);
  const code = codeFrom(args);
  if (cmd === 'seal') {
    const obj = {};
    for (const name of ['product', 'english']) obj[name] = JSON.parse(fs.readFileSync(path.join(PLAIN, `${name}.json`), 'utf8'));
    fs.writeFileSync(ENC, JSON.stringify(seal(obj, code)) + '\n');
    console.log(`잠금: ${path.relative(ROOT, ENC)} (제품 공부 ${obj.product.episodes.length}회, 농업영어 ${(obj.english.episodes || []).length}회)`);
  } else if (cmd === 'open') {
    const obj = open(JSON.parse(fs.readFileSync(ENC, 'utf8')), code);
    fs.mkdirSync(PLAIN, { recursive: true });
    for (const [name, v] of Object.entries(obj)) fs.writeFileSync(path.join(PLAIN, `${name}.json`), JSON.stringify(v, null, 1) + '\n');
    console.log(`풀기: ${path.relative(ROOT, PLAIN)}/ (${Object.keys(obj).join(', ')})`);
  } else {
    console.log('node tools/lessons-crypt.js seal|open [--code-file 파일]  (또는 LESSON_CODE 환경 변수)');
    process.exit(1);
  }
}

module.exports = { seal, open, ITER };
