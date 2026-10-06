const test = require('node:test');
const assert = require('node:assert/strict');
const naver = require('../tools/naver.js');

// 네이버 '신문보기' 페이지 모양 (2026-10-06 조선일보에서 줄인 것)
const paper = (name = '조선일보') => `<html><head><title id="browserTitleArea"> ${name} :: 네이버뉴스 </title></head><body>
<div class="newspaper_brick_item _start_page"><div class="newspaper_wrp type_main"><div class="newspaper_inner">
<h3><span class="page_notation"><em>A1</em>면</span></h3> <ul class="newspaper_article_lst">
<li> <a href="https://n.news.naver.com/article/newspaper/023/0004002259?date=20261006" onclick="x"> <div class="newspaper_img_frame"> <img src="a.jpg" alt="섬네일 이미지"> </div> <div class="newspaper_txt_box"> <strong>상응 조치는 없었다, 지뢰 제거만 촉구한 軍</strong> <p></p> </div> </a> </li>
<li> <a class="article_lst--title_only" href="https://n.news.naver.com/article/newspaper/023/0004002258?date=20261006"> <strong>무죄 부른 &#039;위법 증거&#039;, 70%가 경찰 실수</strong> </a> </li>
</ul></div></div></div>
<div class="newspaper_inner"><h3><span class="page_notation"><em>A2</em>면</span></h3> <ul class="newspaper_article_lst">
<li> <a href="https://n.news.naver.com/article/newspaper/023/0004002300?date=20261006"> <strong>2면 기사</strong> </a> </li></ul></div>
</body></html>`;

const home = `<html><head><title> 연합뉴스 :: 네이버뉴스 </title></head><body>
<a href="https://n.news.naver.com/article/001/0000000001?type=main"><strong>메뉴에 걸린 링크</strong></a>
<div class="press_main_news as_representation _CURATION_CARD" id="Representation"> <div class="press_main_news_inner"> <ul class="press_news_list">
<li class="press_news_item"> <a href="https://n.news.naver.com/article/001/0016360965?type=main" class="press_news_link"> <span class="press_news_text"><strong>北 &#034;韓, 자작극으로 책임전가&#034; 거듭발뺌</strong></span> </a> </li>
<li class="press_news_item"> <a href="https://n.news.naver.com/article/001/0016360719?type=main" class="press_news_link"> <span class="press_news_text"><strong>남극얼음으로 우주 본다</strong></span> </a> </li>
</ul></div></div>
<div class="press_ranking"><a href="https://n.news.naver.com/article/001/0016360000?type=main"><strong>많이 본 기사</strong></a></div>
</body></html>`;

test('신문보기에서 1면 기사만 고른다', () => {
  assert.deepEqual(naver.frontPage(paper()), [
    { title: '상응 조치는 없었다, 지뢰 제거만 촉구한 軍', link: 'https://n.news.naver.com/article/newspaper/023/0004002259?date=20261006' },
    { title: "무죄 부른 '위법 증거', 70%가 경찰 실수", link: 'https://n.news.naver.com/article/newspaper/023/0004002258?date=20261006' },
  ]);
  assert.deepEqual(naver.frontPage('<html>면 표시 없음</html>'), []);
});

test('언론사 홈에서는 맨 위 주요 뉴스 묶음만 고른다 (많이 본 기사·메뉴 링크는 뺀다)', () => {
  assert.deepEqual(naver.mainNews(home).map((x) => x.title), ['北 "韓, 자작극으로 책임전가" 거듭발뺌', '남극얼음으로 우주 본다']);
});

test('언론사 이름이 다르면 쓰지 않고, 오늘 지면이 없으면 최신 지면을 본다', async () => {
  const NOW = Date.parse('2026-10-06T20:00:00Z'); // 서울 10월 7일 새벽 5시
  assert.equal(naver.seoulDate(NOW), '20261007');
  const seen = [];
  const get = async (url) => { seen.push(url); return url.includes('date=') ? '<title>조선일보 :: 네이버뉴스</title>' : paper(); };
  const items = await naver.read({ naver: '023', front: true, name: '조선일보' }, get, NOW);
  assert.equal(items.length, 2);
  assert.deepEqual(seen, ['https://media.naver.com/press/023/newspaper?date=20261007', 'https://media.naver.com/press/023/newspaper']);
  await assert.rejects(naver.read({ naver: '023', front: true, name: '중앙일보' }, async () => paper(), NOW), /언론사 이름이 다름 \(조선일보\)/);
  assert.equal((await naver.read({ naver: '001', name: '연합뉴스' }, async () => home, NOW)).length, 2);
});
