package io.github.kimyh1981.newsclipping;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** F1(Supertonic-3)에 넣을 글 다듬기와 덩이 나누기. 개발사 예제(Helper.java UnicodeProcessor·chunkText)와 같은 규칙 */
final class F1Text {
    private F1Text() {}

    private static final String[][] SYMBOLS = {
        {"–", "-"}, {"‑", "-"}, {"—", "-"}, {"_", " "}, {"“", "\""}, {"”", "\""}, {"‘", "'"}, {"’", "'"},
        {"´", "'"}, {"`", "'"}, {"[", " "}, {"]", " "}, {"|", " "}, {"/", " "}, {"#", " "}, {"→", " "}, {"←", " "},
        {"@", " at "}, {"e.g.,", "for example, "}, {"i.e.,", "that is, "},
    };
    private static final Pattern ENDS = Pattern.compile(".*[.!?;:,'\"\\u201C\\u201D\\u2018\\u2019)\\]}…。」』】〉》›»]$");

    /** 모델이 읽는 꼴: 한글은 자모로 풀고(NFKD), 기호를 정리하고, 끝에 마침표, <ko>…</ko>로 감싼다 */
    static String prepare(String text, String lang) {
        if ("ko".equals(lang)) text = spaceNumbers(text);
        text = Normalizer.normalize(text, Normalizer.Form.NFKD);
        StringBuilder b = new StringBuilder();
        text.codePoints().filter(cp -> !emoji(cp)).forEach(b::appendCodePoint);
        text = b.toString();
        for (String[] r : SYMBOLS) text = text.replace(r[0], r[1]);
        text = text.replaceAll("[♥☆♡©\\\\]", "");
        text = text.replace(" ,", ",").replace(" .", ".").replace(" !", "!").replace(" ?", "?").replace(" ;", ";").replace(" :", ":").replace(" '", "'");
        while (text.contains("\"\"")) text = text.replace("\"\"", "\"");
        while (text.contains("''")) text = text.replace("''", "'");
        text = text.replaceAll("\\s+", " ").trim();
        if (!ENDS.matcher(text).matches()) text += ".";
        return "<" + lang + ">" + text + "</" + lang + ">";
    }

    private static final Pattern NUMBER_UNIT = Pattern.compile("(?<=[일이삼사오육칠팔구])([천백])(?=[일이삼사오육칠팔구십백])");

    /**
     * 우리말 숫자를 천·백 단위마다 띄운다: '삼천사백오십육억' → '삼천 사백 오십육억'.
     * F1이 띄어쓰기 없이 긴 숫자 말을 읽다 가끔 한 글자를 건너뛰었다 (2026-10-17). 앞에 숫자가 있는 천·백 뒤만 띄워서 '천사'·'백화점'·'삼천만'은 그대로
     */
    static String spaceNumbers(String text) {
        return NUMBER_UNIT.matcher(text).replaceAll("$1 ");
    }

    private static boolean emoji(int cp) {
        return (cp >= 0x1F600 && cp <= 0x1F64F) || (cp >= 0x1F300 && cp <= 0x1F5FF) || (cp >= 0x1F680 && cp <= 0x1F6FF)
            || (cp >= 0x1F700 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x1F1E6 && cp <= 0x1F1FF);
    }

    /** 긴 글을 max자 이하 덩이로: 문장 → 쉼표 → 띄어쓰기 순으로 자른다 */
    static List<String> chunks(String text, int max) {
        text = text.trim();
        if (text.isEmpty()) return Arrays.asList("");
        List<String> out = new ArrayList<>();
        for (String para : text.split("\\n\\s*\\n")) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (para.length() <= max) { out.add(para); continue; }
            StringBuilder cur = new StringBuilder();
            for (String s : para.split("(?<=[.!?])\\s+")) {
                s = s.trim();
                if (s.isEmpty()) continue;
                if (s.length() > max) {
                    flush(out, cur);
                    for (String part : s.split(",")) {
                        part = part.trim();
                        if (part.isEmpty()) continue;
                        if (part.length() > max) {
                            flush(out, cur);
                            StringBuilder w = new StringBuilder();
                            for (String word : part.split("\\s+")) {
                                if (w.length() + word.length() + 1 > max && w.length() > 0) flush(out, w);
                                if (w.length() > 0) w.append(' ');
                                w.append(word);
                            }
                            flush(out, w);
                        } else {
                            if (cur.length() + part.length() + 1 > max && cur.length() > 0) flush(out, cur);
                            if (cur.length() > 0) cur.append(", ");
                            cur.append(part);
                        }
                    }
                    continue;
                }
                if (cur.length() + s.length() + 1 > max && cur.length() > 0) flush(out, cur);
                if (cur.length() > 0) cur.append(' ');
                cur.append(s);
            }
            flush(out, cur);
        }
        return out.isEmpty() ? Arrays.asList("") : out;
    }

    private static void flush(List<String> out, StringBuilder b) {
        if (b.length() > 0) out.add(b.toString().trim());
        b.setLength(0);
    }
}
