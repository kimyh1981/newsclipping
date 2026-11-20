/**
 * 세계 비료시장 주간 브리핑 (2026-11-20 주인 결정: 앱 정식판에 넣는다).
 * 주인 PC의 예약 작업(Claude)이 매주 월요일 새벽 원고를 쓰고 사내 코드로 잠가, 저장소의 fert-report 가지에 report.enc.json으로 올린다.
 * 수집기는 그것을 받아 원고(briefing.json)에 잠긴 채 싣고, 정식판 앱만 사내 코드로 풀어 해외 뉴스 앞에서 읽는다.
 * 꼴: { date: 'YYYY-MM-DD'(월요일), box: lessons-crypt의 잠긴 꼴 } — 원고 내용은 잠겨 있고 날짜만 보인다.
 *
 * 올리기 (주인 PC): node tools/report.js upload <원고 json> --code-file _keys/lesson-code.txt
 *   원고 json 꼴: { title, date, segments: [{ name, text }], facts?: [...] } (facts는 올리지 않는다)
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const lock = require('./lessons-crypt.js');

const REPO = 'kimyh1981/newsclipping';
const BRANCH = 'fert-report';
const FILE = 'report.enc.json';
const URL = `https://raw.githubusercontent.com/${REPO}/${BRANCH}/${FILE}`;
const FRESH_DAYS = 6; // 월요일 원고를 그 주 일요일까지 싣는다 (앱은 한 주에 한 번만 읽는다)

/** 서울 날짜 YYYY-MM-DD */
function seoulDate(now) {
  return new Date(now + 9 * 3600e3).toISOString().slice(0, 10);
}

/** 이번 주 원고인가: 날짜가 오늘(서울) 이전이고 FRESH_DAYS 안 */
function fresh(report, now) {
  if (!report || !/^\d{4}-\d{2}-\d{2}$/.test(report.date || '') || !report.box || !report.box.data) return false;
  const days = (Date.parse(seoulDate(now)) - Date.parse(report.date)) / 864e5;
  return days >= 0 && days <= FRESH_DAYS;
}

/** fert-report 가지의 잠긴 원고를 받아, 이번 주 것이면 돌려준다 (없거나 오래됐으면 null) */
async function fetchReport(get, now, log = []) {
  try {
    const r = JSON.parse(await get(URL));
    if (fresh(r, now)) { log.push(`비료시장 주간 브리핑: ${r.date}분 실음`); return { date: r.date, box: r.box }; }
    log.push(`비료시장 주간 브리핑: ${r.date || '날짜 없음'}분이라 싣지 않음`);
  } catch (err) {
    log.push(`비료시장 주간 브리핑 없음 (${err.status || err.message})`);
  }
  return null;
}

/** 원고 json → 올릴 꼴 (잠금). 근거 표(facts)와 메모는 싣지 않는다 */
function sealReport(doc, code, opts) {
  const plain = { title: doc.title, date: doc.date, segments: (doc.segments || []).map((s) => ({ name: s.name, text: s.text })) };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(plain.date || '') || !plain.segments.length) throw new Error('원고에 date와 segments가 있어야 합니다');
  return { date: plain.date, box: lock.seal(plain, code, opts) };
}

/** gh(깃허브 명령줄)로 fert-report 가지에 올린다. 가지가 없으면 main에서 만든다 */
function upload(sealed) {
  const gh = (args) => execFileSync('gh', args, { encoding: 'utf8' }).trim();
  try { gh(['api', `repos/${REPO}/git/ref/heads/${BRANCH}`]); } catch {
    const sha = gh(['api', `repos/${REPO}/git/ref/heads/main`, '--jq', '.object.sha']);
    gh(['api', '-X', 'POST', `repos/${REPO}/git/refs`, '-f', `ref=refs/heads/${BRANCH}`, '-f', `sha=${sha}`]);
  }
  let sha = '';
  try { sha = gh(['api', `repos/${REPO}/contents/${FILE}?ref=${BRANCH}`, '--jq', '.sha']); } catch { /* 처음 올림 */ }
  const args = ['api', '-X', 'PUT', `repos/${REPO}/contents/${FILE}`, '-f', `message=비료시장 주간 브리핑 ${sealed.date}분 (잠금)`,
    '-f', `content=${Buffer.from(JSON.stringify(sealed) + '\n').toString('base64')}`, '-f', `branch=${BRANCH}`];
  if (sha) args.push('-f', `sha=${sha}`);
  gh(args);
  return `${BRANCH}/${FILE} (${sealed.date})`;
}

if (require.main === module) {
  const [cmd, file, ...rest] = process.argv.slice(2);
  if (cmd !== 'upload' || !file) { console.log('사용: node tools/report.js upload <원고 json> --code-file _keys/lesson-code.txt'); process.exit(1); }
  const i = rest.indexOf('--code-file');
  const code = i >= 0 ? fs.readFileSync(path.resolve(rest[i + 1]), 'utf8').trim() : (process.env.LESSON_CODE || '').trim();
  const sealed = sealReport(JSON.parse(fs.readFileSync(path.resolve(file), 'utf8')), code);
  console.log('올림:', upload(sealed));
}

module.exports = { URL, fresh, fetchReport, sealReport, seoulDate };
