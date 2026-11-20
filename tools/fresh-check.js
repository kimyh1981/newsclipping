#!/usr/bin/env node
// GitHub 예약 실행은 몇 시간씩 늦게 온다 (2026-11 실측: 새벽 4시 30분 예약이 오전 10~11시에, 오후 5시 예약이 새벽 2시에).
// 아침판은 'Morning alarm'이 4시 28분에, 오후판은 'Evening alarm'이 4시 27분에 직접 만든다. 늦게 온 예약 실행이
// 같은 판을 또 만들면 번역기(구글)를 쓸데없이 불러 429로 막히고, 다 들은 원고가 바뀐다 → 그날 그 판이 이미 있으면 건너뛴다.
// 이미 있는 판이 없으면(알람이 실패한 날) 늦게라도 만든다. 결과는 GITHUB_OUTPUT의 skip=true|false
const fs = require('fs');

const SITE = 'https://kimyh1981.github.io/newsclipping/';

/** 서울 시각 기준 { date: 'YYYY-MM-DD', hour } */
function seoul(ms) {
  const d = new Date(ms + 9 * 3600e3);
  return { date: d.toISOString().slice(0, 10), hour: d.getUTCHours() };
}

/** 건너뛸지와 까닭. latest = 사이트의 latest.json ({ generatedAt, edition }) 또는 null */
function decide(latest, now) {
  const at = seoul(now);
  if (at.hour < 4) return { skip: true, why: `새벽 ${at.hour}시: 아침판은 4시 28분 알람이 만든다` };
  const edition = at.hour >= 12 ? 'evening' : 'morning';
  if (!latest || !latest.generatedAt) return { skip: false, why: '사이트 원고 시각을 모름' };
  const made = seoul(Date.parse(latest.generatedAt));
  if (latest.edition === edition && made.date === at.date) return { skip: true, why: `오늘(${at.date}) ${edition === 'morning' ? '아침' : '오후'}판이 이미 있음 (${latest.generatedAt})` };
  return { skip: false, why: `오늘 ${edition === 'morning' ? '아침' : '오후'}판이 아직 없음` };
}

async function main() {
  let latest = null;
  try {
    const res = await fetch(`${SITE}latest.json`, { signal: AbortSignal.timeout(15000), cache: 'no-store' });
    if (res.ok) latest = await res.json();
  } catch { /* 모르면 만든다 */ }
  const r = decide(latest, Date.now());
  console.log(`${r.skip ? '건너뜀' : '만듦'}: ${r.why}`);
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `skip=${r.skip}\n`);
}

if (require.main === module) main();

module.exports = { decide, seoul };
