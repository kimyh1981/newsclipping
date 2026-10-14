package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

/** 제품 공부: 진도, 예정 날짜, 뉴스 뒤에 붙이기 */
public class LessonsTest {
    private static String read(String rel) throws Exception {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path f = p.resolve(rel);
            if (Files.exists(f)) return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(rel);
    }

    @Test public void starredComeFirstFromTheSecondRound() {
        List<Integer> all = Arrays.asList(1, 2, 3, 4);
        assertEquals(all, Lessons.order(all, Arrays.asList(3), 1));
        assertEquals(Arrays.asList(3, 1, 2, 4), Lessons.order(all, Arrays.asList(3), 2));
    }

    @Test public void nextSkipsDoneAndEndsAtMinusOne() {
        List<Integer> all = Arrays.asList(1, 2, 3);
        assertEquals(2, Lessons.next(all, Lessons.parseDone("1=2026-10-14")));
        assertEquals(-1, Lessons.next(all, Lessons.parseDone("1=a,2=b,3=c")));
    }

    @Test public void scheduleSkipsWeekends() {
        // 2026-10-16은 금요일
        Map<Integer, String> s = Lessons.schedule(Arrays.asList(1, 2, 3), Lessons.parseDone("1=2026-10-15"), "2026-10-16");
        assertEquals("2026-10-16", s.get(2));
        assertEquals("2026-10-19", s.get(3));
        assertEquals("2026-10-19", Lessons.weekday("2026-10-17", 0));
        assertEquals("10/16 (금)", Lessons.label("2026-10-16"));
    }

    @Test public void doneRoundTrips() {
        String s = "3=2026-10-15,4=2026-10-16";
        assertEquals(s, Lessons.doneString(Lessons.parseDone(s)));
        assertTrue(Lessons.parseDone("").isEmpty());
    }

    @Test public void lessonGoesBetweenNewsAndClosing() throws Exception {
        JSONObject j = new JSONObject(read("tests/fixtures/briefing.json"));
        j.put("lessons", new JSONObject().put("product", new JSONObject(read("lessons/product.json"))));
        Brief b = Brief.parse(j.toString());
        assertEquals(20, b.lessons.size());
        Brief.Lesson l = b.lesson(1);
        List<Brief.Line> news = b.lines(null);
        List<Brief.Line> all = Brief.withLesson(news, l);
        assertEquals(news.size() + 1 + l.lines.size() + 1, all.size());
        assertEquals(news.get(news.size() - 1).text, all.get(all.size() - 1).text);
        int first = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).lesson == 1) { first = i; break; }
        assertEquals(Brief.lessonIntro(1, l.title), all.get(first).text);
        assertEquals("", all.get(first - 1).text);
        assertEquals(0, all.get(all.size() - 1).lesson);
        // 원고 없는 브리핑(예전 서버)은 그대로
        assertTrue(Brief.parse(read("tests/fixtures/briefing.json")).lessons.isEmpty());
    }
}
