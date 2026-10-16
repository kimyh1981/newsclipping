package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** F1에 넣는 글 다듬기·덩이 나누기 (개발사 예제와 같은 규칙) */
public class F1TextTest {
    @Test public void koreanIsSplitIntoJamoAndWrapped() {
        // 한글은 자모(ㄱ U+1100 + ㅏ U+1161)로 풀고(NFKD), 끝에 마침표, 언어 표시로 감싼다
        assertEquals("<ko>가.</ko>", F1Text.prepare("가", "ko"));
        assertEquals("<ko>가?</ko>", F1Text.prepare("가?", "ko"));
        assertEquals("<en>A - B, c.</en>", F1Text.prepare("A — B ,  c", "en"));
    }

    @Test public void longTextIsChunkedBySentenceThenComma() {
        String s = "가".repeat(80) + ". " + "나".repeat(80) + ". " + "다".repeat(30);
        List<String> c = F1Text.chunks(s, 120);
        assertEquals(3, c.size());
        for (String x : c) assertTrue(x, x.length() <= 120);
        assertEquals(1, F1Text.chunks("짧은 문장입니다.", 120).size());
        List<String> commas = F1Text.chunks("가".repeat(70) + ", " + "나".repeat(70) + ", " + "다".repeat(70), 120);
        for (String x : commas) assertTrue(x, x.length() <= 120);
        assertTrue(commas.size() >= 2);
    }
}
