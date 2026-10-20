package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import org.junit.Test;

public class RulesTest {
    @Test public void morningAndEveningCommuteEachOncePerDay() {
        int fiveAm = 5 * 60, eightAm = 8 * 60;
        assertEquals(Rules.MORNING, Rules.autoPlay(Calendar.TUESDAY, 7 * 60, fiveAm, eightAm, true, true, false, false));
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.TUESDAY, 7 * 60, fiveAm, eightAm, true, true, true, false)); // 아침에 이미 읽음
        assertEquals(Rules.EVENING, Rules.autoPlay(Calendar.TUESDAY, 18 * 60 + 30, fiveAm, eightAm, true, true, true, false)); // 아침에 읽었어도 퇴근길엔 또
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.TUESDAY, 18 * 60 + 30, fiveAm, eightAm, true, true, true, true));
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.TUESDAY, 18 * 60 + 30, fiveAm, eightAm, true, false, false, false)); // 퇴근길 끔
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.TUESDAY, 20 * 60, fiveAm, eightAm, true, true, false, false)); // 8시부터는 아님
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.SATURDAY, 18 * 60 + 30, fiveAm, eightAm, true, true, false, false)); // 평일에만
        assertEquals(Rules.NONE, Rules.autoPlay(Calendar.TUESDAY, 12 * 60, fiveAm, eightAm, true, true, false, false));
    }

    // 홈 화면 맨 위 '원고 시각' (당겨서 새로 받기, 주인 요청 2026-10-17). 시각은 서울 시간
    @Test public void clockLikeAVideoPlayer() {
        assertEquals("0:00", Rules.clock(0));
        assertEquals("0:09", Rules.clock(9.9f));
        assertEquals("6:12", Rules.clock(372));
        assertEquals("1:02:05", Rules.clock(3725));
        assertEquals("0:00", Rules.clock(-3));
    }

    @Test public void briefTimeInPlainKorean() {
        long now = java.time.Instant.parse("2026-10-17T06:30:00Z").toEpochMilli(); // 10/17 15:30 KST
        assertEquals("오늘 오후 2:56", Rules.briefTime("2026-10-17T05:56:35.143Z", now));
        assertEquals("오늘 오전 5:10", Rules.briefTime("2026-10-16T20:10:00Z", now));
        assertEquals("어제 오후 4:22", Rules.briefTime("2026-10-16T07:22:00Z", now));
        assertEquals("10월 15일 오후 12:05", Rules.briefTime("2026-10-15T03:05:00Z", now));
        assertEquals("", Rules.briefTime("", now));
        assertEquals("", Rules.briefTime("nonsense", now));
    }

    private static final int SIX = 6 * 60, EIGHT = 8 * 60;

    @Test public void weekdayMorningWindow() {
        assertTrue(Rules.inWindow(Calendar.TUESDAY, 6 * 60, SIX, EIGHT, true));
        assertTrue(Rules.inWindow(Calendar.FRIDAY, 7 * 60 + 59, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 8 * 60, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 5 * 60 + 59, SIX, EIGHT, true));
    }

    @Test public void weekendsOnlyWhenAllowed() {
        assertFalse(Rules.inWindow(Calendar.SATURDAY, 7 * 60, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.SUNDAY, 7 * 60, SIX, EIGHT, true));
        assertTrue(Rules.inWindow(Calendar.SATURDAY, 7 * 60, SIX, EIGHT, false));
    }

    @Test public void overnightAndAllDay() {
        assertTrue(Rules.inWindow(Calendar.MONDAY, 23 * 60, 22 * 60, 2 * 60, false));
        assertTrue(Rules.inWindow(Calendar.MONDAY, 60, 22 * 60, 2 * 60, false));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 12 * 60, 22 * 60, 2 * 60, false));
        assertTrue(Rules.inWindow(Calendar.MONDAY, 12 * 60, SIX, SIX, false));
        assertEquals("06:00", Rules.hhmm(SIX));
    }

    @Test public void chunksKeepPausesAndRespectLimit() {
        assertEquals(Arrays.asList("좋은 아침입니다.", "", "먼저 주요 신문.", "기사."),
                Rules.chunks("좋은 아침입니다.\n\n\n먼저 주요 신문.\n기사.\n\n", 4000));
        assertEquals(Arrays.asList("가나다라.", "마바사."), Rules.chunks("가나다라. 마바사.", 8));
        assertTrue(Rules.chunks("", 100).isEmpty());
        for (String c : Rules.chunks("아주긴문장없이계속되는텍스트입니다", 5)) assertTrue(c.length() <= 5);
    }

    @Test public void picksBestInstalledVoicePreferringOffline() {
        assertTrue(Rules.voiceScore(500, false, false) > Rules.voiceScore(500, true, false));
        assertTrue(Rules.voiceScore(500, true, false) > Rules.voiceScore(400, false, false));
        assertTrue(Rules.voiceScore(300, false, false) > Rules.voiceScore(500, false, true));
    }

    @Test public void rateLabels() {
        assertEquals("보통", Rules.rateLabel(100));
        assertEquals("조금 느리게", Rules.rateLabel(85));
        assertEquals("90%", Rules.rateLabel(90));
    }

    @Test public void previousAndNextArticle() {
        boolean[] f = {false, false, true, false, true, true, false};
        assertEquals(2, Rules.nextItem(f, 0));
        assertEquals(4, Rules.nextItem(f, 2));
        assertEquals(7, Rules.nextItem(f, 5)); // 마지막 기사 다음은 맺음말 쪽으로
        assertEquals(2, Rules.prevItem(f, 4));
        assertEquals(4, Rules.prevItem(f, 5));
        assertEquals(0, Rules.prevItem(f, 2)); // 첫 기사 앞은 처음부터
        assertEquals(5, Rules.prevItem(f, 9));
    }


    @Test public void lessonParagraphsAndSkipToEnglish() {
        // 뉴스(0) 빈줄 제품(1)x3 빈줄 영어(2)x2 빈줄 맺음말(0)
        int[] k = {0, -1, 1, 1, 1, -1, 2, 2, -1, 0};
        assertEquals(3, Rules.nextParagraph(k, 2));
        assertEquals(6, Rules.nextParagraph(k, 4)); // 제품 끝 → 영어 첫 줄
        assertEquals(2, Rules.prevParagraph(k, 2)); // 코너 첫 줄이면 그대로
        assertEquals(3, Rules.prevParagraph(k, 4));
        assertEquals(6, Rules.skipLesson(k, 3)); // 제품 → 영어
        assertEquals(9, Rules.skipLesson(k, 6)); // 영어 → 맺음말
        assertEquals(5, Rules.skipLesson(new int[] {0, -1, 1, 1, -1, 0}, 2)); // 영어가 없으면 맺음말
    }

    @Test public void typedCountIsClamped() {
        assertEquals(12, Rules.clampCount(" 12 ", 5));
        assertEquals(1, Rules.clampCount("0", 5));
        assertEquals(50, Rules.clampCount("99", 5));
        assertEquals(5, Rules.clampCount("", 5));
    }

    @Test public void assistantResumeWaitsForQuiet() {
        long lim = 300000;
        // 제미나이가 듣는 중(마이크): 기다린다. 닫히고 소리도 없으면 이어 읽어도 된다
        assertEquals(Rules.ASSIST_WAIT, Rules.afterAssistant(true, true, false, false, 3000, lim));
        assertEquals(Rules.ASSIST_WAIT, Rules.afterAssistant(true, false, true, false, 4000, lim)); // 대답을 말하는 중
        assertEquals(Rules.ASSIST_QUIET, Rules.afterAssistant(true, false, false, false, 6000, lim));
        // 마이크를 쓰는 앱이 끝내 없으면 음악 앱 같은 것: 이어 듣기를 기다린다
        assertEquals(Rules.ASSIST_WAIT, Rules.afterAssistant(false, false, true, false, 5000, lim));
        assertEquals(Rules.ASSIST_GIVE_UP, Rules.afterAssistant(false, false, false, false, 16000, lim));
        // 통화가 끼었거나 너무 오래 지났으면 저절로 켜지지 않는다
        assertEquals(Rules.ASSIST_GIVE_UP, Rules.afterAssistant(true, false, false, true, 6000, lim));
        assertEquals(Rules.ASSIST_GIVE_UP, Rules.afterAssistant(true, false, false, false, lim + 1, lim));
    }

    @Test public void logoGridShrinksToFit() {
        // 넓은 칸에 몇 개면 가장 큰 크기
        assertArrayEquals(new int[] {90, 3}, Rules.logoGrid(3, 400, 400, 10, 90, 30));
        // 많아지면 줄어서 모두 들어간다
        int[] g = Rules.logoGrid(30, 330, 400, 10, 90, 30);
        int rows = (30 + g[1] - 1) / g[1];
        assertTrue(g[0] < 90);
        assertTrue(g[1] * g[0] + (g[1] - 1) * 10 <= 330);
        assertTrue(rows * g[0] + (rows - 1) * 10 <= 400);
        assertArrayEquals(new int[] {0, 0}, Rules.logoGrid(0, 300, 300, 10, 90, 30));
    }

    // 안드로이드 오토(USB·무선)는 블루투스 출력이 아니어도 차다: 내비 안내 뒤 멈춘 채 남았다 (2026-10-20 퇴근길)
    @Test public void resumesAfterFocusReturnsInCar() {
        long lim = 10 * 60_000L;
        assertTrue(Rules.resumeOnGain(true, true, 5000, lim, true, false, false));   // 오토·블루투스에서 내비 안내 뒤
        assertTrue(Rules.resumeOnGain(true, true, 5000, lim, false, false, true));   // 내비 소리를 보고 멈춘 것
        assertTrue(Rules.resumeOnGain(true, true, 5000, lim, false, true, false));   // 폰 스피커에서도 음성 비서 뒤에는
        assertFalse(Rules.resumeOnGain(true, true, 5000, lim, false, false, false)); // 폰 스피커로 듣다 통화 뒤에는 기다린다
        assertFalse(Rules.resumeOnGain(true, false, 5000, lim, true, false, false)); // 손으로 멈춘 것
        assertFalse(Rules.resumeOnGain(true, true, lim + 1, lim, true, false, false)); // 너무 오래 멈춤
        assertFalse(Rules.resumeOnGain(false, true, 5000, lim, true, false, false)); // 읽는 중
    }
}
