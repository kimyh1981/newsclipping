package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.Normalizer;
import java.util.List;
import org.junit.Test;

/** F1에 넣는 글 다듬기·덩이 나누기 (개발사 예제와 같은 규칙) */
public class F1TextTest {
    // 영문을 한글 소리로: tests/fixtures/hangulize.json은 _f1bench/hangulize.js(같은 규칙의 자바스크립트판)가 만든 기대값 (2026-11-26)
    @Test public void englishWordsBecomeHangulForF1() throws Exception {
        java.nio.file.Path f = null;
        for (java.nio.file.Path p = java.nio.file.Paths.get("").toAbsolutePath(); p != null && f == null; p = p.getParent())
            if (java.nio.file.Files.exists(p.resolve("tests/fixtures/hangulize.json"))) f = p.resolve("tests/fixtures/hangulize.json");
        org.json.JSONArray cases = new org.json.JSONArray(new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length(); i++) {
            org.json.JSONArray c = cases.getJSONArray(i);
            assertEquals(c.getString(0), c.getString(1), F1Text.hangulize(c.getString(0)));
        }
        // 오늘(11/26) 원고에서 F1이 건너뛰거나 뭉갠 낱말
        assertEquals("핀센", F1Text.hangulize("FinCEN"));
        assertEquals("크롭 라이프", F1Text.hangulize("CropLife"));
        assertEquals("페트로나스", F1Text.hangulize("PETRONAS"));
    }

    @Test public void koreanIsSplitIntoJamoAndWrapped() {
        // 한글은 자모(ㄱ U+1100 + ㅏ U+1161)로 풀고(NFKD), 끝에 마침표, 언어 표시로 감싼다
        assertEquals("<ko>가.</ko>", F1Text.prepare("가", "ko"));
        assertEquals("<ko>가?</ko>", F1Text.prepare("가?", "ko"));
        assertEquals("<en>A - B, c.</en>", F1Text.prepare("A — B ,  c", "en"));
    }

    // F1 소리 다듬기: 앞뒤 무음 길이를 고정하고 크기를 맞춘다 (줄마다 쉼·크기가 달랐다, 2026-11-21)
    @Test public void audioIsTrimmedAndLevelled() {
        int rate = 1000;
        float[] pcm = new float[3000]; // 0.9초 무음 + 1초 말소리(크기 0.02~0.04) + 1.1초 무음
        for (int i = 900; i < 1900; i++) pcm[i] = (i % 2 == 0 ? 0.04f : -0.04f);
        float[] out = F1Text.tidyAudio(pcm, rate, 0.2f);
        assertEquals(200 + 30 + 1000 + 120, out.length); // 앞 0.2초 + 여유 0.03초 + 말소리 + 뒤 0.12초
        assertEquals(0f, out[0], 0f);
        assertEquals(0.08f, Math.abs(out[200 + 30 + 500]), 0.001f); // 0.04 → 두 배(위 끝)로 키움
        float[] loud = new float[1000];
        for (int i = 0; i < 1000; i++) loud[i] = (i % 2 == 0 ? 0.9f : -0.9f);
        float[] quieter = F1Text.tidyAudio(loud, rate, 0f);
        assertEquals(0.45f, Math.abs(quieter[500]), 0.001f); // 너무 크면 절반(아래 끝)까지 줄임
        float[] silent = new float[500];
        assertTrue(F1Text.tidyAudio(silent, rate, 0.2f) == silent); // 말소리가 없으면 그대로
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

    // 빠르기 상한: 초당 6음절보다 빠르게 만들지 않는다 (빠르게 130에서 '덧붙였다'가 사라졌다, 2026-11-26 받아쓰기)
    @Test public void neverFasterThanSixSyllablesPerSecond() {
        assertEquals(13, F1Text.syllables("연합뉴스 소식입니다, A1 하나."));
        // 모델 예측 3초, 30음절: 보통 빠르기(1.05)면 예측대로 2.86초, 빠르게(1.365)면 2.2초가 아니라 상한 5초
        String thirty = "가".repeat(30);
        assertEquals(3f / 1.05f, F1Text.duration(3f, "가".repeat(15), 1.05f), 0.001f);
        assertEquals(5f, F1Text.duration(3f, thirty, 1.365f), 0.001f);
        // 말 빠르기를 줄이면 상한과 상관없이 예측보다 길어진다
        assertEquals(3f / 0.89f, F1Text.duration(3f, "가".repeat(15), 0.89f), 0.001f);
    }

    // F1이 '삼천사백오십육억'을 '삼천 사 오십육'처럼 한 글자 건너뛰어 읽었다 (주인 폰, 2026-10-17): 단위마다 띄워 넘긴다
    @Test public void longKoreanNumbersAreSpacedByUnit() {
        assertEquals("삼천 사백 오십 육억 원", F1Text.spaceNumbers("삼천사백오십육억 원"));
        assertEquals("육십 육억 달러", F1Text.spaceNumbers("육십육억 달러")); // '유억 달러'로 얼버무렸다 (2026-11-23)
        assertEquals("이십삼명, 오십 대", F1Text.spaceNumbers("이십삼명, 오십 대")); // 억·만·조 앞이 아니면 그대로
        assertEquals("이천 이십육년", F1Text.spaceNumbers("이천이십육년"));
        assertEquals("만 이천 삼백 사십오", F1Text.spaceNumbers("만 이천삼백사십오"));
        assertEquals("천이백 삼십사명", F1Text.spaceNumbers("천이백삼십사명"));
        assertEquals("십육억 팔천 백 이만원", F1Text.spaceNumbers("십육억 팔천백이만원"));
        assertEquals("천백 원", F1Text.spaceNumbers("천백 원")); // 맨 앞 천은 그대로
        // 숫자가 아닌 낱말과 단위가 붙은 말은 그대로
        assertEquals("천사가 백화점에서 삼천만 원과 오백억", F1Text.spaceNumbers("천사가 백화점에서 삼천만 원과 오백억"));
        assertEquals("<ko>삼천 사백 오십 육억.</ko>", Normalizer.normalize(F1Text.prepare("삼천사백오십육억", "ko"), Normalizer.Form.NFC));
    }
}
