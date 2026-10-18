package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** '전체 듣기': 폰이 원문 페이지에서 본문 문장만 뽑는다. 화면의 군글은 읽지 않는다 (시험용으로 지어낸 페이지) */
public class ArticleReaderTest {
    private static final String P1 = "정부가 내년 비료 가격 안정을 위해 무기질비료 구입비 보조 예산을 늘리기로 했다고 밝혔다.";
    private static final String P2 = "농가는 지난해보다 3.5% 낮은 값에 비료를 살 수 있을 것으로 보인다고 관계자는 설명했다.";

    private static final String PAGE = "<html><head><meta property=\"og:description\" content=\"요약 문장입니다 요약 문장입니다 요약 문장입니다 요약 문장입니다.\">"
            + "<script>var x = '스크립트는 읽지 않는다 스크립트는 읽지 않는다 스크립트는 읽지 않는다';</script></head><body>"
            + "<nav><p>홈 정치 경제 사회 국제 농업 비료 오피니언 많이 본 뉴스 전체 메뉴 보기 로그인 회원가입</p></nav>"
            + "<article><p>[서울=뉴스통신] 홍길동 기자 = " + P1 + "</p>"
            + "<figure><img src=x><figcaption>사진 설명은 읽지 않는다 사진 설명은 읽지 않는다 사진 설명은 읽지 않는다</figcaption></figure>"
            + "<p>" + P2 + " hong@example.com</p>"
            + "<p>▶ 관련 기사: 비료값 오른다는데 농가는 어떻게 대비해야 하나 지금 바로 확인하세요</p>"
            + "<p>&lt;저작권자 ⓒ 뉴스통신, 무단 전재 및 재배포 금지&gt; 이 기사를 공유하기 카카오톡 페이스북</p>"
            + "<p>짧은 줄</p></article>"
            + "<aside><p>많이 본 뉴스 1. 다른 기사 제목이 여기에 들어갑니다 2. 또 다른 기사 제목입니다 3. 세 번째</p></aside>"
            + "<footer><p>회사 소개 개인정보처리방침 청소년보호정책 고충처리인 광고 문의 Copyright 2026 All rights reserved.</p></footer>"
            + "</body></html>";

    @Test public void readsOnlyArticleSentences() {
        List<String> s = ArticleReader.sentences(PAGE);
        assertEquals(2, s.size());
        assertEquals(P1, s.get(0));
        assertEquals(P2, s.get(1)); // 이메일은 지우고, 3.5%의 점에서 자르지 않는다
        String all = String.join(" ", s);
        for (String junk : new String[] {"서울=", "홍길동 기자", "@", "사진 설명", "관련 기사", "저작권", "공유", "많이 본", "로그인", "Copyright", "스크립트"}) {
            assertFalse(junk, all.contains(junk));
        }
    }

    @Test public void naverBodyUsesLineBreaksAndSkipsPhotoCaptions() {
        String page = "<article id=\"dic_area\" class=\"go_trans\">" + P1 + "<br><br><em class=\"img_desc\">사진 설명 사진 설명 사진 설명 사진 설명 사진 설명</em><br>" + P2 + "<br></article>";
        List<String> s = ArticleReader.sentences(page);
        assertEquals(2, s.size());
        assertTrue(s.get(1).startsWith("농가는"));
    }

    @Test public void fallsBackToDescriptionThenNothing() {
        String onlyMeta = "<html><head><meta name=\"description\" content=\"" + P1 + "\"></head><body><p>메뉴</p></body></html>";
        assertEquals(1, ArticleReader.sentences(onlyMeta).size());
        assertTrue(ArticleReader.sentences("<html><body><p>로그인이 필요합니다</p></body></html>").isEmpty()); // 유료·로그인 페이지: 엉뚱한 것을 읽지 않는다
    }

    // 10/18 실제 기사 점검에서 나온 군글
    @Test public void dropsJunkFoundOnRealSites() {
        String page = "<article>"
                + "<p>서울특별시 서대문구 독립문로 59 농민신문사 사업자등록번호 102-82-05549 " + P1 + "</p>"
                + "<p>&lt;img id=img1 data-src= class=_LAZY_LOADING alt=사진 설명이 여기에 길게 붙어 나오는 경우가 있다 정말로 길게</p>"
                + "<p>[이 기사에 나온 스타트업에 대한 보다 다양한 기업정보는 빅데이터 플랫폼에서 볼 수 있습니다.] " + P2 + "</p>"
                + "<p>더 궁금한 점이 있으신가요? 읽으면서 머릿속에 맴돌던 질문들, 앨리스가 대답해 드릴게요.</p>"
                + "<p>Subscribe to our newsletter for the biggest tech news every single day of the week.</p>"
                + "<p>&lsquo;생활인구&rsquo;는 특정 지역에 거주하거나 체류하면서 생활을 영위하는 사람을 말하는 새로운 개념이다.</p>"
                + "<p>박시몬 기자 새 가족을 만나 행복한 견생 2회차를 살고 있는 반려견들이 남다른 참을성을 뽐냈다고 한다.</p>"
                + "<p>박시몬 기자 새 가족을 만나 행복한 견생 2회차를 살고 있는 반려견들이 남다른 참을성을 뽐냈다고 한다.</p>"
                + "</article>";
        List<String> s = ArticleReader.sentences(page);
        assertEquals(P1, s.get(0)); // 바닥글은 지우고 본문 첫 문장
        assertEquals(P2, s.get(1)); // 홍보 대괄호는 지움
        String all = String.join(" ", s);
        for (String junk : new String[] {"사업자", "img", "data-src", "스타트업", "앨리스", "Subscribe", "&lsquo;", "박시몬 기자"}) assertFalse(junk, all.contains(junk));
        assertTrue(all.contains("생활인구는"));
        assertTrue(ArticleReader.keep("봉준호 감독의 영화 <기생충>은 칸 영화제에서 황금종려상을 받으며 큰 화제가 되었다."));
        assertEquals(4, s.size()); // 같은 문장 반복은 한 번만
    }

    @Test public void foreignArticlesAreDetected() {
        assertTrue(ArticleReader.foreign("After each harvest, straw, crop stalks and leaves still contain a large amount of nutrients."));
        assertTrue(ArticleReader.foreign("Ngày 14/10, Cơ quan Cảnh sát điều tra Công an tỉnh Cà Mau đã khởi tố một người sản xuất phân bón giả."));
        assertFalse(ArticleReader.foreign(P1 + " KIA와 LG가 맞붙었다."));
    }
}
