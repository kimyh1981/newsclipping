package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.Normalizer;
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
        assertEquals(2, c.size()); // 81자 문장 둘은 나뉘고, 뒤의 30자는 120자 안이라 둘째 덩이에 붙는다
        for (String x : c) assertTrue(x, x.length() <= 120);
        assertEquals(1, F1Text.chunks("짧은 문장입니다.", 120).size());
        List<String> commas = F1Text.chunks("가".repeat(70) + ", " + "나".repeat(70) + ", " + "다".repeat(70), 120);
        for (String x : commas) assertTrue(x, x.length() <= 120);
        assertTrue(commas.size() >= 2);
    }

    // 120자 넘는 문장을 잘라 넘기면 F1이 덩이 끝을 문장 끝처럼 내려 읽었다: 고르게 자르고 앞 덩이는 쉼표로 끝낸다 (2026-10-27)
    @Test public void longSentenceIsCutEvenlyAndEndsWithComma() {
        List<String> c = F1Text.chunks("농협이 감당하는 공적 업무를 제대로 수행했는지를 감시하는 것이 국정감사의 합법적 영역이지, 의원 개인이 마음에 들지 않는다고 민간 협동조합의 고유한 인사권과 경영 판단까지 미주알고주알 간섭하고 호통치는 것은 명백한 월권이다.", 120);
        assertEquals(2, c.size());
        assertEquals("농협이 감당하는 공적 업무를 제대로 수행했는지를 감시하는 것이 국정감사의 합법적 영역이지,", c.get(0));
        assertEquals("의원 개인이 마음에 들지 않는다고 민간 협동조합의 고유한 인사권과 경영 판단까지 미주알고주알 간섭하고 호통치는 것은 명백한 월권이다.", c.get(1));

        List<String> three = F1Text.chunks("가".repeat(70) + ", " + "나".repeat(70) + ", " + "다".repeat(70), 120);
        assertEquals(3, three.size());
        assertEquals("가".repeat(70) + ",", three.get(0));
        assertEquals("나".repeat(70) + ",", three.get(1));
        assertEquals("다".repeat(70), three.get(2));

        List<String> words = F1Text.chunks(("가나다 ").repeat(40).trim() + ".", 120); // 쉼표 없는 160자
        assertEquals(2, words.size());
        assertEquals(80, words.get(0).length());
        assertTrue(words.get(0).endsWith(","));
        assertEquals(80, words.get(1).length());
        assertTrue(words.get(1).endsWith("."));

        // 바뀌면 안 됨: 120자 이하 문장들은 지금처럼 마침표로 끝난다
        List<String> sentences = F1Text.chunks("가".repeat(80) + ". " + "나".repeat(80) + ". " + "다".repeat(30), 120);
        assertEquals("가".repeat(80) + ".", sentences.get(0));
        assertTrue(sentences.get(1).startsWith("나".repeat(80) + ". "));
        assertEquals(1, F1Text.chunks("짧은 문장입니다. 둘째 문장입니다.", 120).size());
    }

    // F1이 '삼천사백오십육억'을 '삼천 사 오십육'처럼 한 글자 건너뛰어 읽었다 (주인 폰, 2026-10-17): 단위마다 띄워 넘긴다
    @Test public void longKoreanNumbersAreSpacedByUnit() {
        assertEquals("삼천 사백 오십육억 원", F1Text.spaceNumbers("삼천사백오십육억 원"));
        assertEquals("이천 이십육년", F1Text.spaceNumbers("이천이십육년"));
        assertEquals("만 이천 삼백 사십오", F1Text.spaceNumbers("만 이천삼백사십오"));
        assertEquals("천이백 삼십사명", F1Text.spaceNumbers("천이백삼십사명"));
        assertEquals("십육억 팔천 백 이만원", F1Text.spaceNumbers("십육억 팔천백이만원"));
        assertEquals("천백 원", F1Text.spaceNumbers("천백 원")); // 맨 앞 천은 그대로
        // 숫자가 아닌 낱말과 단위가 붙은 말은 그대로
        assertEquals("천사가 백화점에서 삼천만 원과 오백억", F1Text.spaceNumbers("천사가 백화점에서 삼천만 원과 오백억"));
        assertEquals("<ko>삼천 사백 오십육억.</ko>", Normalizer.normalize(F1Text.prepare("삼천사백오십육억", "ko"), Normalizer.Form.NFC));
    }
}
