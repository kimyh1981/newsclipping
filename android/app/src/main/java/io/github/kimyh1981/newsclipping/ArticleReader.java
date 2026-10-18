package io.github.kimyh1981.newsclipping;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * '전체 듣기': 폰이 그 순간 원문 기사 페이지를 열어 본문 문장만 뽑는다 (주인 결정 2026-10-18).
 * 서버는 본문을 모으거나 녹음하지 않는다 (제목·언론사·원문 링크만). 여기서 뽑은 글은 폰에서 읽고 버린다.
 * 규칙은 서버가 쓰던 본문 추출(tools/summarize.js articleText·cleanParas)을 옮기고, 화면의 군글(관련 기사·구독·공유·광고·저작권 문구·
 * 기자 이메일·사진 설명)을 더 거른다. 안드로이드에 기대지 않아 단위 테스트로 확인한다.
 */
final class ArticleReader {
    private ArticleReader() {}

    /** 이보다 길면 본문이 아니라 목록·댓글이 섞였다고 보고 자른다 */
    static final int MAX_CHARS = 6000;

    private static final Pattern DROP_BLOCKS = Pattern.compile("(?is)<(script|style|noscript|svg|iframe|form|nav|footer|header|aside|figcaption|button|select)[\\s>].*?</\\1>");
    private static final Pattern PARA = Pattern.compile("(?is)<p[\\s>].*?</p>");
    private static final Pattern NAVER_BODY = Pattern.compile("(?is)<article[^>]*id=\"dic_area\"[^>]*>(.*?)</article>");
    private static final Pattern ARTICLE = Pattern.compile("(?is)<article[\\s>].*?</article>");
    private static final Pattern META_DESC = Pattern.compile("(?i)<meta[^>]+(?:property=\"og:description\"|name=\"description\")[^>]+content=\"([^\"]+)\"");

    /** 화면 군글: 이런 말이 든 문단은 본문이 아니다 */
    private static final Pattern JUNK = Pattern.compile("(?i)(무단\\s*전재|재배포\\s*금지|저작권자|copyright|ⓒ|©|all rights reserved"
            + "|관련\\s*기사|많이\\s*본|인기\\s*기사|추천\\s*기사|이\\s*시각|구독|좋아요|댓글|기사\\s*제보|제보하기|광고\\s*문의|글자\\s*크기|공유하기"
            + "|카카오톡|페이스북|트위터|URL\\s*복사|링크\\s*복사|앱\\s*다운|로그인|회원가입|▶|☞|△|▲\\s*사진|사진\\s*=|사진\\s*제공|그래픽\\s*=)");

    /** 페이지 HTML → 읽을 문장들. 본문을 못 찾으면 빈 목록 (그러면 앱이 '본문을 가져오지 못했습니다'라고 하고 넘어간다) */
    static List<String> sentences(String html) {
        return split(text(html));
    }

    /** 본문 문단 (줄마다 한 문단). 서버 articleText와 같은 순서: 네이버 본문 → article 안 p → 페이지 p → 기사 요약 */
    static String text(String html) {
        String clean = DROP_BLOCKS.matcher(html).replaceAll(" ");
        Matcher dic = NAVER_BODY.matcher(clean);
        if (dic.find()) {
            String lines = dic.group(1).replaceAll("(?is)<em class=\"img_desc\".*?</em>", " ").replaceAll("(?i)<br\\s*/?>|</(?:p|div)>", "\n");
            StringBuilder out = new StringBuilder();
            for (String t : lines.split("\n")) {
                String p = Extras.clean(t);
                if (keep(p)) out.append(p).append('\n');
            }
            if (out.length() >= 60) return cap(out.toString());
        }
        Matcher art = ARTICLE.matcher(clean);
        String text = art.find() ? paras(art.group()) : "";
        if (text.length() < 80) text = paras(clean); // 짧은 기사(두세 문단)도 본문으로 읽는다
        if (text.length() < 80) {
            Matcher m = META_DESC.matcher(clean);
            text = m.find() ? Extras.clean(m.group(1)) : "";
            if (!keep(text)) text = "";
        }
        return cap(text);
    }

    private static String paras(String scope) {
        StringBuilder out = new StringBuilder();
        Matcher m = PARA.matcher(scope);
        while (m.find()) {
            String p = Extras.clean(m.group().replaceAll("(?i)<br\\s*/?>", " "));
            if (keep(p)) out.append(p).append('\n');
        }
        return out.toString();
    }

    /** 본문 문단인가: 너무 짧거나(메뉴·단추), 군글이면 버린다. 머리말·기자 이름·이메일·주소는 지운 뒤 판단 */
    static boolean keep(String p) {
        String t = tidy(p);
        return t.length() >= 40 && !JUNK.matcher(t).find();
    }

    /** 문단 앞뒤 군말을 지운다: [서울=뉴시스], (사진=연합뉴스), 홍길동 기자 =, 이메일, 주소 (서버 cleanParas와 같은 규칙) */
    static String tidy(String p) {
        return p.replaceAll("^\\s*[\\[(【][^\\])】]{1,40}[\\])】]\\s*", "")
                .replaceAll("^[가-힣]{2,4}\\s*(?:기자|특파원|객원기자)\\s*=?\\s*", "")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "")
                .replaceAll("\\([^)]{0,30}(?:사진|제공|=)[^)]{0,30}\\)", "")
                .replaceAll("https?://\\S+", "")
                .trim();
    }

    private static String cap(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    /** 문단들 → 문장들. 소리 내어 읽기 좋게 다듬는다 (서버 speechSentences의 주요 규칙) */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        StringBuilder all = new StringBuilder();
        for (String p : text.split("\n")) if (keep(p)) all.append(tidy(p)).append(' ');
        String s = Normalizer.normalize(all.toString(), Normalizer.Form.NFKC);
        s = s.replaceAll("\\s*(?:[가-힣]{3}\\s+){0,2}[가-힣]{2,4}\\s*(?:기자|특파원|객원기자)\\s*=\\s*", ". "); // 부제 … 홍길동 기자 = 본문 → 부제. 본문
        s = s.replaceAll("\\(\\s*[^()]{1,20}\\s*\\)", " "); // 비무장지대(DMZ) → 비무장지대
        s = s.replaceAll("[\\u4e00-\\u9fff]+", ""); // 한자는 읽지 않는다
        s = s.replaceAll("[\"“”‘’'`]", "");
        s = s.replaceAll("…|\\.{3,}", ", ").replaceAll("(\\d)\\s*~\\s*(?=\\d)", "$1에서 ").replace("~", ", ");
        s = s.replaceAll("\\s+", " ");
        s = s.replaceAll("(\\d)\\.(?=\\d)", "$1․"); // 3.5% 의 점에서 자르지 않는다
        Matcher m = Pattern.compile("[^.?!。]+(?:[.?!。]+|$)").matcher(s);
        while (m.find()) {
            String x = m.group().replace('․', '.').replaceAll("^[\\s,]+|[\\s,]+$", "");
            if (!x.matches(".*[가-힣a-zA-Z0-9]{2}.*")) continue;
            out.add(x.matches(".*[.?!]$") ? x : x + ".");
        }
        return out;
    }
}
