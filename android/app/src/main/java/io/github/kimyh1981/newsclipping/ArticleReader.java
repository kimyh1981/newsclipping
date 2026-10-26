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
    static final int MAX_CHARS = 20000; // 6,000자에서 긴 분석 기사 뒷부분이 잘렸다 (팜인사이트 8천 자, 2026-10-27). 넘으면 문단 경계에서 자른다

    private static final Pattern DROP_BLOCKS = Pattern.compile("(?is)<(script|style|noscript|svg|iframe|form|nav|footer|header|aside|figcaption|button|select)[\\s>].*?</\\1>");
    private static final Pattern PARA = Pattern.compile("(?is)<p[\\s>].*?</p>");
    private static final Pattern NAVER_BODY = Pattern.compile("(?is)<article[^>]*id=\"dic_area\"[^>]*>(.*?)</article>");
    private static final Pattern ARTICLE = Pattern.compile("(?is)<article[\\s>].*?</article>");
    private static final Pattern META_DESC = Pattern.compile("(?i)<meta[^>]+(?:property=\"og:description\"|name=\"description\")[^>]+content=\"([^\"]+)\"");

    /** 화면 군글: 이런 말이 든 문단은 본문이 아니다 (10/18 실제 기사 32곳 점검에서 나온 것 포함: 챗봇 위젯, 영어 사이트 홍보, 연락처) */
    private static final Pattern JUNK = Pattern.compile("(?i)(무단\\s*전재|재배포\\s*금지|저작권자|copyright|ⓒ|©|all rights reserved"
            + "|관련\\s*기사|많이\\s*본|인기\\s*기사|추천\\s*기사|이\\s*시각|구독|좋아요|댓글|기사\\s*제보|제보하기|광고\\s*문의|글자\\s*크기|공유하기"
            + "|카카오톡|페이스북|트위터|URL\\s*복사|링크\\s*복사|앱\\s*다운|로그인|회원가입|▶|☞|△|▲\\s*사진|사진\\s*=|사진\\s*제공|그래픽\\s*=)"
            + "|궁금한\\s*점|대답해\\s*드릴게요|손실\\s*책임|투자\\s*판단|AI\\s*요약|요약\\s*정보"
            + "|subscribe|newsletter|sign\\s*up|save\\s*up\\s*to|advertisement|cookie|follow\\s*us|share\\s*this|read\\s*more|related\\s*:|getty\\s*images"
            + "|e-?mail\\s*:|\\bfax\\b|\\btel\\.|điện\\s*thoại|ghi\\s*rõ\\s*nguồn|<\\s*/?[a-zA-Z]|data-src|class=|src="); // 태그 흔적만 (영화 <기생충> 같은 꺾쇠는 본문)

    /** 원문 주소 → 읽을 문장들 (네트워크를 쓰므로 화면 밖 스레드에서). 외국어 기사는 우리말로 옮겨 읽는다 (제목처럼) */
    static List<List<String>> read(String url) throws Exception {
        String text = text(Extras.get(url, 15000)); // 늦는 사이트도 기다린다 (YTN 8초 초과, 10/18 점검)
        if (foreign(text)) text = translate(text);
        return paragraphs(text);
    }

    /** 문단별 문장들 (자막 화면에서 위로 쓸면 다음 문단, 아래로 쓸면 이전 문단, 주인 요청 2026-10-18). 빈 문단은 뺀다 */
    static List<List<String>> paragraphs(String text) {
        List<List<String>> out = new ArrayList<>();
        String last = "";
        if (text == null) return out;
        for (String p : text.split("\n")) {
            if (!keep(p, true)) continue;
            List<String> s = new ArrayList<>();
            for (String x : split(p)) {
                if (x.equals(last)) continue; // 문단을 넘어 같은 문장이 되풀이되면 한 번만
                s.add(x);
                last = x;
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** 한글이 거의 없으면 외국어 기사 */
    static boolean foreign(String text) {
        int hangul = 0, letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) { letters++; if (c >= 0xAC00 && c <= 0xD7A3) hangul++; }
        }
        return letters >= 40 && hangul * 10 < letters * 3;
    }

    /** 문단을 모아 1,500자씩 무료 구글 번역(gtx)으로 옮긴다. 못 옮긴 덩어리는 버린다 (외국어를 한국어 목소리로 읽지 않게) */
    static String translate(String text) {
        StringBuilder out = new StringBuilder(), chunk = new StringBuilder();
        for (String p : (text + "\n").split("\n")) {
            if (chunk.length() > 0 && chunk.length() + p.length() > 1500) { out.append(translateChunk(chunk.toString())); chunk.setLength(0); }
            if (!p.trim().isEmpty()) chunk.append(p).append('\n');
        }
        if (chunk.length() > 0) out.append(translateChunk(chunk.toString()));
        return out.toString();
    }

    private static String translateChunk(String text) {
        try {
            String json = Extras.get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ko&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8"), 15000);
            org.json.JSONArray parts = new org.json.JSONArray(json).optJSONArray(0);
            StringBuilder b = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++) {
                org.json.JSONArray x = parts.optJSONArray(i);
                if (x != null && !x.isNull(0)) b.append(x.optString(0));
            }
            return b.toString().trim().isEmpty() ? "" : b.append('\n').toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 페이지 HTML → 읽을 문장들. 본문을 못 찾으면 빈 목록 (그러면 앱이 '본문을 가져오지 못했습니다'라고 하고 넘어간다) */
    static List<String> sentences(String html) {
        List<String> all = new ArrayList<>();
        for (List<String> p : paragraphs(text(html))) all.addAll(p);
        return all;
    }

    /** 본문 문단 (줄마다 한 문단). 서버 articleText와 같은 순서: 네이버 본문 → article 안 p → 페이지 p → 기사 요약 */
    static String text(String html) {
        String clean = DROP_BLOCKS.matcher(entities(html)).replaceAll(" ");
        Matcher dic = NAVER_BODY.matcher(clean);
        if (dic.find()) {
            String lines = dic.group(1).replaceAll("(?is)<em class=\"img_desc\".*?</em>", " ").replaceAll("(?i)<br\\s*/?>|</(?:p|div)>", "\n");
            StringBuilder out = new StringBuilder();
            for (String t : lines.split("\n")) {
                String p = Extras.clean(t);
                if (keep(p, true)) out.append(p).append('\n');
            }
            if (out.length() >= 60) return cap(out.toString());
        }
        Matcher art = ARTICLE.matcher(clean);
        String text = art.find() ? paras(art.group(), true) : "";
        if (text.length() < 80) text = paras(clean, false); // 짧은 기사(두세 문단)도 본문으로 읽는다
        if (text.length() < 80) {
            Matcher m = META_DESC.matcher(clean);
            text = m.find() ? Extras.clean(m.group(1)) : "";
            if (!keep(text)) text = "";
        }
        return cap(text);
    }

    private static String paras(String scope, boolean body) {
        StringBuilder out = new StringBuilder();
        Matcher m = PARA.matcher(scope);
        while (m.find()) {
            String p = Extras.clean(m.group().replaceAll("(?i)<br\\s*/?>", " "));
            if (keep(p, body)) out.append(p).append('\n');
        }
        return out.toString();
    }

    /** 본문 문단인가: 너무 짧거나(메뉴·단추), 군글이면 버린다. 머리말·기자 이름·이메일·주소는 지운 뒤 판단 */
    static boolean keep(String p) {
        return keep(p, false);
    }

    /** 본문 안(body)에서는 40자보다 짧아도 '…다.'처럼 문장으로 끝나면 읽는다 ('고랭지 무는 … 왔다.'가 빠졌다, 2026-10-27) */
    static boolean keep(String p, boolean body) {
        String t = tidy(p);
        if (JUNK.matcher(t).find()) return false;
        return t.length() >= 40 || body && t.length() >= 10 && SENTENCE_END.matcher(t).find();
    }

    private static final Pattern SENTENCE_END = Pattern.compile("[가-힣][.?!]['\"’”]?$");

    /** 문단 앞뒤 군말을 지운다: [서울=뉴시스], (사진=연합뉴스), 홍길동 기자 =, 이메일, 주소 (서버 cleanParas와 같은 규칙) */
    static String tidy(String p) {
        return p.replaceAll("^.*?사업자\\s*등록\\s*번호\\s*[\\d-]+\\s*", "") // 사이트 바닥글(주소·사업자번호)이 첫 문단에 붙은 것
                .replaceAll("^\\s*[\\[(【][^\\])】]{1,200}[\\])】]\\s*", "") // [서울=뉴시스], [이 기사에 나온 스타트업에 대한 … 볼 수 있습니다]
                .replaceAll("^[가-힣]{2,4}\\s*(?:기자|특파원|객원기자)\\s*=?\\s*", "")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "")
                .replaceAll("\\([^)]{0,30}(?:사진|제공|=)[^)]{0,30}\\)", "")
                .replaceAll("https?://\\S+", "")
                .trim();
    }

    /** Extras.clean이 모르는 이름 붙은 기호: 따옴표·가운뎃점·말줄임 */
    static String entities(String s) {
        return s.replace("&lsquo;", "'").replace("&rsquo;", "'").replace("&ldquo;", "\"").replace("&rdquo;", "\"")
                .replace("&middot;", "·").replace("&hellip;", "…").replace("&ndash;", "-").replace("&mdash;", "-").replace("&bull;", " ");
    }

    private static String cap(String text) {
        if (text.length() <= MAX_CHARS) return text;
        int cut = text.lastIndexOf('\n', MAX_CHARS);
        return text.substring(0, cut > 0 ? cut + 1 : MAX_CHARS);
    }

    /** 문단들 → 문장들. 소리 내어 읽기 좋게 다듬는다 (서버 speechSentences의 주요 규칙) */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        StringBuilder all = new StringBuilder();
        for (String p : text.split("\n")) if (keep(p, true)) all.append(tidy(p)).append(' ');
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
            x = x.replaceAll("^[가-힣]{2,4}\\s*(?:기자|특파원)\\s+(?=\\S)", ""); // 문장 앞 '고봉준 기자 프로야구…'
            if (!x.matches(".*[가-힣a-zA-Z0-9]{2}.*")) continue;
            x = x.matches(".*[.?!]$") ? x : x + ".";
            if (!out.isEmpty() && out.get(out.size() - 1).equals(x)) continue; // 같은 문장이 잇따르면 한 번만 (요약문 반복)
            out.add(x);
        }
        return out;
    }
}
