package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

/** 웹(js/brief.js)과 같은 브리핑에서 같은 원고가 나와야 한다: tests/fixtures를 같이 쓴다. */
public class BriefTest {
    private static Path fixtures() {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path f = p.resolve("tests/fixtures");
            if (Files.isDirectory(f)) return f;
        }
        throw new IllegalStateException("tests/fixtures를 찾지 못함");
    }

    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(fixtures().resolve(name)), StandardCharsets.UTF_8);
    }

    @Test public void sameScriptAsWeb() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        assertTrue(b.playToday);
        assertEquals(new HashSet<>(Arrays.asList("a", "c", "d")), b.defaults());
        assertEquals(read("script-default.txt"), b.script(null));
        assertEquals(read("script-b-c-d.txt"), b.script(new HashSet<>(Arrays.asList("b", "c", "d"))));
        assertEquals(read("script-none.txt"), b.script(Collections.emptySet()));
    }

    @Test public void headlineLinesCarryTheirSummary() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        Brief.Line withSummary = null;
        for (Brief.Line l : b.lines(null)) if (l.item != null && !l.item.summary.isEmpty()) withSummary = l;
        assertEquals("가 기사.", withSummary.text);
        assertTrue(withSummary.item.summary.startsWith("가 기사의 자세한 내용"));
    }

    @Test public void sentenceKeepsEndingMarks() {
        assertEquals("쌀값 급등.", Brief.sentence("쌀값 급등"));
        assertEquals("어떻습니까?", Brief.sentence("어떻습니까?"));
        assertFalse(Brief.sentence("끝.").endsWith(".."));
    }

    @Test public void catalogMarksTranslatedSources() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        assertEquals(4, b.catalog().size());
        assertEquals("번역", b.catalog().get(3)[3]);
    }

    @Test public void greetingFollowsTheWeekday() {
        assertTrue(Brief.opening("10월 5일 월요일").endsWith("새로운 한 주, 힘차게 시작해 보시죠."));
        assertTrue(Brief.opening("10월 9일 금요일").endsWith("한 주의 마무리, 오늘도 힘내세요."));
        assertEquals("좋은 아침입니다. 10월 6일 화요일, 출근길 뉴스 브리핑입니다.", Brief.opening("10월 6일 화요일"));
        assertEquals("다음은 가입니다.", Brief.intro("가", 1, 4));
        assertEquals("이어서 가입니다.", Brief.intro("가", 2, 4));
        assertEquals("끝으로 가입니다.", Brief.intro("가", 3, 4));
    }
}
