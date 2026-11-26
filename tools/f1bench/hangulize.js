// F1Text.hangulize의 자바스크립트 쌍둥이 (측정·기대값 만들기용). 자바와 같은 규칙: tests/fixtures/hangulize.json으로 맞춘다
'use strict';
const ABC = ['에이', '비', '씨', '디', '이', '에프', '지', '에이치', '아이', '제이', '케이', '엘', '엠', '엔', '오', '피', '큐', '알', '에스', '티', '유', '브이', '더블유', '엑스', '와이', '제트'];
// 자주 나오는 영어 낱말 (외래어 표기). 규칙으로는 틀리기 쉬운 것 위주
const WORDS = {
  agri: '애그리', agro: '애그로', bio: '바이오', tech: '테크', crop: '크롭', life: '라이프', stack: '스택', more: '모어', people: '피플',
  stone: '스톤', global: '글로벌', money: '머니', club: '클럽', shorts: '쇼츠', news: '뉴스', farm: '팜', farms: '팜스', group: '그룹',
  india: '인디아', solutions: '솔루션스', solution: '솔루션', market: '마켓', markets: '마켓츠', capital: '캐피털', partners: '파트너스',
  international: '인터내셔널', holdings: '홀딩스', energy: '에너지', power: '파워', bank: '뱅크', food: '푸드', foods: '푸즈', science: '사이언스',
  sciences: '사이언시스', health: '헬스', care: '케어', data: '데이터', cloud: '클라우드', smart: '스마트', green: '그린', world: '월드',
  company: '컴퍼니', corp: '코프', inc: '잉크', ltd: '리미티드', co: '코', the: '더', and: '앤드', of: '오브', for: '포', new: '뉴', one: '원',
  plus: '플러스', pro: '프로', max: '맥스', air: '에어', street: '스트리트', journal: '저널', times: '타임스', post: '포스트', today: '투데이',
  daily: '데일리', weekly: '위클리', review: '리뷰', report: '리포트', research: '리서치', network: '네트워크', system: '시스템',
  systems: '시스템스', service: '서비스', services: '서비시스', insight: '인사이트', insights: '인사이츠', plant: '플랜트', plantation: '플랜테이션',
  plantations: '플랜테이션스', fertilizer: '퍼틸라이저', fertiliser: '퍼틸라이저', nutrition: '뉴트리션', seed: '시드', seeds: '시즈',
  soil: '소일', water: '워터', rice: '라이스', field: '필드', fresh: '프레시', organic: '오가닉', natural: '내추럴', chemical: '케미컬',
  chemicals: '케미컬스', industries: '인더스트리스', industry: '인더스트리', trade: '트레이드', exchange: '익스체인지', fund: '펀드',
  digital: '디지털', mobile: '모바일', online: '온라인', live: '라이브', show: '쇼', day: '데이', week: '위크', summit: '서밋',
  forum: '포럼', expo: '엑스포', award: '어워드', awards: '어워즈', center: '센터', centre: '센터', university: '유니버시티',
  institute: '인스티튜트', council: '카운슬', association: '어소시에이션', federation: '페더레이션', union: '유니언', alliance: '얼라이언스',
  foundation: '파운데이션', ministry: '미니스트리', agency: '에이전시', federal: '페더럴', national: '내셔널', royal: '로열',
  general: '제너럴', mil: '백만', cr: '크로르', bn: '십억', mn: '백만', vs: '대', dr: '닥터', mr: '미스터', ms: '미즈', st: '세인트',
  kh: '케이에이치', mig: '미그', su: '수호이', fincen: '핀센', acid: '애시드', urea: '요소', tech: '테크',
};
const ONSET = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
const VOWEL = 'ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ';
const CODA = ['', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'];
const syl = (o, v, c) => String.fromCharCode(0xac00 + (ONSET.indexOf(o) * 21 + VOWEL.indexOf(v)) * 28 + CODA.indexOf(c || ''));
// 소리 조각: 자음은 이름, 모음은 'V:모음' (R이 붙으면 r 모음: 뒤 자음을 받침으로 쓰지 않는다)
// 자음 → [첫소리, 받침(될 수 있으면), 홀로 설 때]
const CONS = {
  b: ['ㅂ', 'ㅂ', 'ㅡ'], d: ['ㄷ', '', 'ㅡ'], f: ['ㅍ', '', 'ㅡ'], g: ['ㄱ', '', 'ㅡ'], h: ['ㅎ', '', 'ㅡ'], j: ['ㅈ', '', 'ㅣ'], k: ['ㅋ', 'ㄱ', 'ㅡ'],
  l: ['ㄹ', 'ㄹ', 'ㅡ'], m: ['ㅁ', 'ㅁ', 'ㅡ'], n: ['ㄴ', 'ㄴ', 'ㅡ'], p: ['ㅍ', 'ㅂ', 'ㅡ'], r: ['ㄹ', '', 'ㅡ'], s: ['ㅅ', '', 'ㅡ'], t: ['ㅌ', 'ㅅ', 'ㅡ'],
  v: ['ㅂ', '', 'ㅡ'], w: ['ㅇ', '', 'ㅜ'], y: ['ㅇ', '', 'ㅣ'], z: ['ㅈ', '', 'ㅡ'], CH: ['ㅊ', '', 'ㅣ'], SH: ['ㅅ', '', 'ㅣ'], TH: ['ㅅ', '', 'ㅡ'], NG: ['ㅇ', 'ㅇ', 'ㅡ'],
};
const RULES = [
  ['tion', ['SH', 'V:ㅓ', 'n']], ['sion', ['j', 'V:ㅓ', 'n']], ['ture', ['CH', 'V:ㅓ']], ['igh', ['V:ㅏ', 'V:ㅣ']], ['ough', ['V:ㅗ']],
  ['sch', ['s', 'k']], ['tch', ['CH']], ['ch', ['CH']], ['sh', ['SH']], ['th', ['TH']], ['ph', ['f']], ['ck', ['k']], ['qu', ['k', 'w']], ['x', ['k', 's']],
  ['ee', ['V:ㅣL']], ['ea', ['V:ㅣL']], ['oo', ['V:ㅜL']], ['ou', ['V:ㅏ', 'V:ㅜ']], ['ow', ['V:ㅏ', 'V:ㅜ']], ['ai', ['V:ㅔ', 'V:ㅣ']],
  ['ei', ['V:ㅔ', 'V:ㅣ']], ['oa', ['V:ㅗ']], ['au', ['V:ㅗ']], ['aw', ['V:ㅗ']], ['oi', ['V:ㅗ', 'V:ㅣ']], ['ie', ['V:ㅣL']], ['ue', ['V:ㅜL']],
];
const VOW = { a: 'ㅏ', e: 'ㅔ', i: 'ㅣ', o: 'ㅗ', u: 'ㅓ', y: 'ㅣ' };
const isV = (c) => /[aeiouy]/.test(c || '');
function sounds(w) {
  w = w.toLowerCase().replace(/[^a-z]/g, '');
  let magic = false; // 끝의 묵음 e (stone, life, phosphate): 앞 모음을 길게
  if (w.length > 3 && /[^aeiou][aeiou][^aeiouwxyr]e$/.test(w)) { magic = true; w = w.slice(0, -1); }
  const out = [];
  for (let i = 0; i < w.length;) {
    const c = w[i], nx = w[i + 1];
    // ay·ey·oy는 뒤에 모음이 없을 때만 이중모음 (NAVODAYA 나보다야)
    if (/^[aeo]y$/.test(c + (nx || '')) && !isV(w[i + 2])) { out.push(...(c === 'o' ? ['V:ㅗ', 'V:ㅣ'] : ['V:ㅔ', 'V:ㅣ'])); i += 2; continue; }
    // ng는 뒤에 모음이 없을 때만 받침 ㅇ (Syngenta 신젠타)
    if (c === 'n' && nx === 'g' && !isV(w[i + 2])) { out.push('NG'); i += 2; continue; }
    // er·ar·or·ir·ur: 뒤에 모음이 없으면 r 모음 (Koppert 코퍼트)
    if (/[aeiou]/.test(c) && nx === 'r' && !isV(w[i + 2])) { out.push('V:' + { a: 'ㅏ', e: 'ㅓ', i: 'ㅓ', o: 'ㅗ', u: 'ㅓ' }[c] + 'R'); i += 2; continue; }
    const r = RULES.find(([g]) => w.startsWith(g, i));
    if (r) { out.push(...r[1]); i += r[0].length; continue; }
    if (c === 'c') { out.push(/[eiy]/.test(nx || '') ? 's' : 'k'); i++; continue; }
    if (c === 'g' && /[eiy]/.test(nx || '') && i > 0) { out.push('j'); i++; continue; }
    if (c === 'q') { out.push('k'); i++; continue; }
    if (c === 'y' && (i === 0 || isV(nx))) { out.push('y'); i++; continue; } // 첫소리 y
    if (c === 'w' && isV(nx)) { out.push('w'); i++; continue; }
    if (VOW[c]) { out.push('V:' + VOW[c]); i++; continue; }
    if (i > 0 && w[i - 1] === c) { i++; continue; } // 겹자음은 하나로
    out.push(CONS[c] ? c : 'k'); i++;
  }
  if (magic) for (let k = out.length - 1; k >= 0; k--) if (out[k].startsWith('V:')) {
    const v = out[k].slice(2);
    out.splice(k, 1, ...(v === 'ㅏ' ? ['V:ㅔ', 'V:ㅣ'] : v === 'ㅣ' ? ['V:ㅏ', 'V:ㅣ'] : v === 'ㅓ' ? ['V:ㅠ'] : ['V:' + v]));
    break;
  }
  return out;
}
const isVow = (p) => p && p.startsWith('V:');
function assemble(ph) {
  const out = []; // {o, v, c}
  for (let i = 0; i < ph.length; i++) {
    const p = ph[i];
    let o = 'ㅇ', v;
    if (isVow(p)) v = p.slice(2);
    else if (isVow(ph[i + 1])) {
      v = ph[i + 1].slice(2);
      if (p === 'y') v = ({ ㅏ: 'ㅑ', ㅓ: 'ㅕ', ㅗ: 'ㅛ', ㅜ: 'ㅠ', ㅔ: 'ㅖ', ㅐ: 'ㅒ' }[v[0]] || v[0]) + v.slice(1);
      else if (p === 'w') v = ({ ㅏ: 'ㅘ', ㅓ: 'ㅝ', ㅔ: 'ㅞ', ㅣ: 'ㅟ', ㅐ: 'ㅙ' }[v[0]] || v[0]) + v.slice(1);
      else o = CONS[p][0];
      if (p === 'SH') v = ({ ㅓ: 'ㅕ', ㅗ: 'ㅛ', ㅜ: 'ㅠ', ㅏ: 'ㅑ', ㅔ: 'ㅖ' }[v[0]] || v[0]) + v.slice(1); // 쇼·셔·슈·샤·셰 (ㅈ·ㅊ 뒤는 그냥 저·조)
      // ㄹ 첫소리 앞 음절에 받침이 없으면 ㄹ을 덧붙인다 (Valagro 발라그로, Plant 플랜트)
      if (p === 'l' && out.length && !out[out.length - 1].c) out[out.length - 1].c = 'ㄹ';
      i++;
    } else { // 홀로 선 자음: 스·트·치…
      out.push({ o: CONS[p][0], v: CONS[p][2], c: '' });
      continue;
    }
    const rc = v.endsWith('R'), long = v.endsWith('L'); v = v.replace(/[RL]$/, '');
    // 받침: 다음이 자음이고 그다음이 모음이 아니고(닫힌 음절), r 모음·이중모음 뒤가 아니고, 자음+r/l+모음(트로, 플라)이 아닐 때
    const n1 = ph[i + 1], n2 = ph[i + 2], n3 = ph[i + 3];
    const diph = (long || (isVow(ph[i - 1]) && isVow(ph[i]))) && /^(t|k|p|b)$/.test(n1 || ''); // 긴 모음·이중모음 뒤 t·k·p는 트·크·프 (sheet 시트)
    let c = '';
    if (n1 && !isVow(n1) && !isVow(n2) && !rc && !diph && !(/^(b|p|t|d|k|g|f|v|TH)$/.test(n1) && (n2 === 'r' || n2 === 'l') && isVow(n3))) {
      c = n1 === 'n' && (n2 === 'k' || n2 === 'g') ? 'ㅇ' : CONS[n1][1];
      if (c) { if (v === 'ㅏ' && /^(k|t|p|b|n|m|NG)$/.test(n1)) v = 'ㅐ'; i++; }
    } else if (n1 && !isVow(n1) && !n2 && !rc && !diph && CONS[n1][1]) { // 낱말 끝 자음
      c = CONS[n1][1]; if (v === 'ㅏ' && /^(k|t|p|b|n|m|NG)$/.test(n1)) v = 'ㅐ'; i++;
    }
    out.push({ o, v, c });
  }
  return out.map((x) => syl(x.o, x.v, x.c)).join('');
}
function word(w) {
  const lw = w.toLowerCase();
  if (WORDS[lw]) return WORDS[lw];
  if (/^[A-Z]{1,5}$/.test(w)) return w.split('').map((c) => ABC[c.charCodeAt(0) - 65]).join(''); // 대문자 약어는 낱자로
  if (w.length === 1) return ABC[w.toUpperCase().charCodeAt(0) - 65];
  return assemble(sounds(w));
}
// 낱말 안의 대소문자 경계(CropLife, StoneX)를 나눈다. 사전에 통째로 있으면(FinCEN) 나누지 않는다
function split(w) {
  if (WORDS[w.toLowerCase()]) return [w];
  return w.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/([A-Z])([A-Z][a-z])/g, '$1 $2').split(/[\s'’.-]+/).filter(Boolean);
}
function hangulize(text) {
  return text.replace(/[A-Za-z][A-Za-z'’.-]*[A-Za-z]|[A-Za-z]/g, (m) => split(m).map(word).join(' '));
}
module.exports = { hangulize, word, split, sounds };
if (require.main === module) for (const w of process.argv.slice(2)) console.log(w, '→', hangulize(w));
