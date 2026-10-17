package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** 홈 화면 진행바: 원고 줄을 섹션 토막과 언론사로 나누고, 바 위치 ↔ 줄 (주인 요청 2026-10-17) */
public class ChaptersTest {
    private static Brief.Item item(String source, String title) {
        return new Brief.Item(title, title, source, "g-" + title, "", "");
    }

    /** 인사 · [주요 신문: 가신문 2건, 나신문 1건] · [비료: 다신문 1건] · 맺음말 */
    private static List<Brief.Line> script() {
        List<Brief.Line> l = new ArrayList<>();
        l.add(new Brief.Line("좋은 아침입니다. 10월 6일 화요일, 출근길 뉴스 브리핑입니다.", null));
        l.add(new Brief.Line("", null));
        l.add(new Brief.Line("먼저 주요 신문 헤드라인부터 전해 드립니다.", null));
        l.add(new Brief.Line("가신문 소식입니다.", null));
        l.add(new Brief.Line("가 기사 하나.", item("가신문", "가 기사 하나")));
        l.add(new Brief.Line("가 기사 둘.", item("가신문", "가 기사 둘")));
        l.add(new Brief.Line("나신문 소식입니다.", null));
        l.add(new Brief.Line("나 기사.", item("나신문", "나 기사")));
        l.add(new Brief.Line("", null));
        l.add(new Brief.Line("다음은 비료 관련 뉴스입니다.", null));
        l.add(new Brief.Line("다 기사.", item("다신문", "다 기사")));
        l.add(new Brief.Line("", null));
        l.add(new Brief.Line("지금까지 아침 뉴스였습니다.", null));
        return l;
    }

    private static final Map<String, String> SITES = new HashMap<>();
    static {
        SITES.put("가신문", "ga.kr");
        SITES.put("나신문", "na.kr");
        SITES.put("다신문", "da.kr");
    }

    private static Chapters chapters() { return Chapters.of(script(), it -> SITES.getOrDefault(it.source, "")); }

    @Test public void linesAreGroupedIntoSectionChapters() {
        Chapters c = chapters();
        // 인사(0) · 주요 신문(1) · 비료(2) · 맺음말(3): 빈 줄에서 새 토막
        assertEquals(4, c.count());
        assertEquals(Arrays.asList(0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 3, 3), c.chapterList());
        float[] b = c.bounds();
        assertEquals(0f, b[0], 1e-6);
        assertEquals(1f, b[b.length - 1], 1e-6);
        for (int i = 1; i < b.length; i++) assertTrue(b[i] > b[i - 1]);
    }

    @Test public void eachLineKnowsWhichOutletItBelongsTo() {
        Chapters c = chapters();
        // 'OO 소식입니다.'와 섹션 소개 줄은 바로 뒤 기사의 언론사로 친다 (그 자리에서 로고가 커지게)
        assertEquals("ga.kr", c.site(2));
        assertEquals("ga.kr", c.site(3));
        assertEquals("ga.kr", c.site(5));
        assertEquals("na.kr", c.site(6));
        assertEquals("da.kr", c.site(9));
        assertEquals("", c.site(0)); // 인사
        assertEquals("", c.site(12)); // 맺음말
    }

    @Test public void barPositionMapsToLineAndSnapsToOutletStart() {
        Chapters c = chapters();
        for (int i = 0; i < 13; i++) assertEquals(i, c.lineAt(c.fractionOf(i) + 1e-4f));
        assertEquals(0, c.lineAt(-1f));
        assertEquals(12, c.lineAt(2f));
        // 손을 떼면 그 언론사가 시작하는 줄부터 (섹션 첫 언론사면 섹션 소개부터)
        assertEquals(2, c.seekTarget(5));
        assertEquals(6, c.seekTarget(7));
        assertEquals(9, c.seekTarget(10)); // 섹션 소개부터 (다신문이 그 섹션 첫 언론사)
        assertEquals(12, c.seekTarget(12));
    }
}
