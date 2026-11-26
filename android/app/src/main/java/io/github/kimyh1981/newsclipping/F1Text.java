package io.github.kimyh1981.newsclipping;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** F1(Supertonic-3)에 넣을 글 다듬기와 덩이 나누기. 개발사 예제(Helper.java UnicodeProcessor·chunkText)와 같은 규칙 */
final class F1Text {
    private F1Text() {}

    /**
     * F1이 만든 소리 다듬기 (벤치마킹 2026-11-21: 줄마다 앞뒤 무음 길이와 크기가 달라 속도·쉼이 들쭉날쭉하게 들렸다).
     * 앞뒤 무음을 잘라 앞은 leadSec, 뒤는 0.12초로 고정하고, 말소리 크기(RMS)를 0.1에 맞춘다(0.5~2배, 찌그러지지 않게).
     * 말소리가 없으면 그대로 돌려준다
     */
    static float[] tidyAudio(float[] pcm, int sampleRate, float leadSec) {
        final float quiet = 0.005f;
        int first = -1, last = -1;
        for (int i = 0; i < pcm.length; i++) if (Math.abs(pcm[i]) > quiet) { if (first < 0) first = i; last = i; }
        if (first < 0) return pcm;
        int start = Math.max(0, first - (int) (0.03f * sampleRate)), end = Math.min(pcm.length, last + 1 + (int) (0.12f * sampleRate));
        double sum = 0;
        int n = 0;
        float peak = 0;
        for (int i = start; i < end; i++) {
            float a = Math.abs(pcm[i]);
            if (a > 0.02f) { sum += (double) a * a; n++; }
            if (a > peak) peak = a;
        }
        float gain = 1f;
        if (n > 0) {
            float rms = (float) Math.sqrt(sum / n);
            gain = Math.max(0.5f, Math.min(2f, 0.1f / rms));
            if (peak * gain > 0.97f) gain = 0.97f / peak;
        }
        int lead = (int) (leadSec * sampleRate);
        float[] out = new float[lead + end - start];
        for (int i = start; i < end; i++) out[lead + i - start] = pcm[i] * gain;
        return out;
    }

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

    private static final Pattern NUMBER_UNIT = Pattern.compile("(?<=[일이삼사오육칠팔구])([천백])(?=[일이삼사오육칠팔구십백])|(?<=천)(백)(?=[일이삼사오육칠팔구십])");
    /** 육십육억 → 육십 육억: '66억 달러'를 '유억 달러'처럼 얼버무렸다 (주인 2026-11-23). 앞에 숫자가 있는 십 뒤, 숫자+억·만·조 앞만 */
    private static final Pattern TEN_UNIT = Pattern.compile("(?<=[이삼사오육칠팔구])십(?=[일이삼사오육칠팔구][억만조])");
    /**
     * F1이 또렷하게 읽는 가장 빠른 빠르기(초당 음절). 오늘 기사 본문 3건을 받아쓰기로 잰 값 (2026-11-26, _f1bench):
     * 보통(100) 초당 5.0음절 → 빠진 음절 1.6%·끝 잘림 0/45, 빠르게(130) 초당 6.6음절 → 빠진 음절 4.0%·끝 잘림 2/45('덧붙였다'가 사라짐),
     * 빠르게 + 상한 6.0 → 빠진 음절 1.9%·끝 잘림 0/45. 이보다 빠르면 모델이 낱말·끝 글자를 삼킨다
     */
    // 2026-11-27 다시 잼: 본문은 6.0까지 버텼지만 제목처럼 고유명사가 몰린 줄('서울경기양돈농협 송산농협 도농상생')은
    // 초당 5.0음절부터 '송산농협 도농상생'이 통째로 사라졌다(3번 중 3번). 4.5에서는 3번 모두 온전했다. 모델의 제 빠르기는 초당 4.1~4.4음절.
    // 그래서 F1은 늘 이 빠르기 안에서 만들고, 빠르게 듣기는 재생 빠르기(높낮이 그대로 늘이고 줄이기)로 한다: 1.15·1.3배로 틀어도 12번 모두 온전했다
    static final float MAX_SPS = 4.5f;

    private static final Pattern EN_VOWELS = Pattern.compile("[aeiouy]+", Pattern.CASE_INSENSITIVE);

    /** 읽는 음절 수: 한글 음절, 숫자 하나씩, 영어 낱말은 모음 덩이 수(최소 1). 영어 줄을 글자 수로 세면 터무니없이 느려진다 */
    static int syllables(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '가' && c <= '힣') || (c >= '0' && c <= '9')) n++;
        }
        Matcher w = Pattern.compile("[A-Za-z]+").matcher(s);
        while (w.find()) {
            int v = 0;
            Matcher m = EN_VOWELS.matcher(w.group());
            while (m.find()) v++;
            n += Math.max(1, v);
        }
        return n;
    }

    /**
     * 한 덩이의 소리 길이(초): 모델이 예측한 길이를 빠르기로 나누되, 초당 MAX_SPS음절보다 빠르지 않게.
     * 예전에는 줄마다 빠르기를 바꿨다(숫자 줄 12% 느리게, 짧은 줄 상한): 한 기사 안에서 빨라졌다 느려졌다 했다 (주인 2026-11-26 '고무줄').
     * 앱은 늘 같은 빠르기(F1Voice.speed(100))로 만들고, 사용자의 빠르기는 재생 빠르기로 준다 (NewsService)
     */
    static float duration(float predicted, String chunk, float speed) {
        return Math.max(predicted / speed, syllables(chunk) / MAX_SPS);
    }

    /**
     * 우리말 숫자를 천·백 단위마다 띄운다: '삼천사백오십육억' → '삼천 사백 오십육억'.
     * F1이 띄어쓰기 없이 긴 숫자 말을 읽다 가끔 한 글자를 건너뛰었다 (2026-10-17). 앞에 숫자가 있는 천·백 뒤만 띄워서 '천사'·'백화점'·'삼천만'은 그대로
     */
    static String spaceNumbers(String text) {
        // 팔천백이만 → 팔천 백 이만: F1이 '백' 뒤 글자를 빠뜨렸다 (16억 8102만원 → 8,100만, 10/18 받아쓰기)
        Matcher m = NUMBER_UNIT.matcher(text);
        StringBuffer b = new StringBuffer();
        while (m.find()) m.appendReplacement(b, (m.group(1) != null ? m.group(1) : m.group(2)) + " ");
        m.appendTail(b);
        return TEN_UNIT.matcher(b.toString()).replaceAll("십 ");
    }

    // ---- 영문 낱말을 한글 소리로 (F1 전용) ----
    // F1은 우리말 문장 속 영문을 건너뛰거나 뭉갠다: 'FinCEN'을 '핀 씨'로, 'Kh-101'을 잡음으로 (2026-11-26 받아쓰기).
    // <en> 태그로 섞으면 소리가 통째로 무너졌고, 한글로 옮겨 쓰면 바르게 읽었다. 그래서 Pron 사전(1,300여 개)을 거치고도 남은 영문을
    // 자주 쓰는 낱말 표 → 대문자 약어는 낱자 → 나머지는 영어 철자 규칙으로 한글로 옮긴다. 서버 녹음·폰 음성은 영문을 잘 읽어 손대지 않는다.
    // tools·tests/fixtures/hangulize.json(_f1bench/hangulize.js가 만든 기대값)으로 확인한다

    private static final String[] ABC = {"에이", "비", "씨", "디", "이", "에프", "지", "에이치", "아이", "제이", "케이", "엘", "엠", "엔", "오", "피", "큐", "알", "에스", "티", "유", "브이", "더블유", "엑스", "와이", "제트"};
    private static final java.util.Map<String, String> EN_WORDS = new java.util.HashMap<>();
    static {
        String[] w = {
            "ag", "애그", "agri", "애그리", "agro", "애그로", "bio", "바이오", "tech", "테크", "crop", "크롭", "life", "라이프", "stack", "스택", "more", "모어", "people", "피플",
            "stone", "스톤", "global", "글로벌", "money", "머니", "club", "클럽", "shorts", "쇼츠", "news", "뉴스", "farm", "팜", "farms", "팜스", "group", "그룹",
            "india", "인디아", "solutions", "솔루션스", "solution", "솔루션", "market", "마켓", "markets", "마켓츠", "capital", "캐피털", "partners", "파트너스",
            "international", "인터내셔널", "holdings", "홀딩스", "energy", "에너지", "power", "파워", "bank", "뱅크", "food", "푸드", "foods", "푸즈", "science", "사이언스",
            "sciences", "사이언시스", "health", "헬스", "care", "케어", "data", "데이터", "cloud", "클라우드", "smart", "스마트", "green", "그린", "world", "월드",
            "company", "컴퍼니", "corp", "코프", "inc", "잉크", "ltd", "리미티드", "co", "코", "the", "더", "and", "앤드", "of", "오브", "for", "포", "new", "뉴", "one", "원",
            "plus", "플러스", "pro", "프로", "max", "맥스", "air", "에어", "street", "스트리트", "journal", "저널", "times", "타임스", "post", "포스트", "today", "투데이",
            "daily", "데일리", "weekly", "위클리", "review", "리뷰", "report", "리포트", "research", "리서치", "network", "네트워크", "system", "시스템",
            "systems", "시스템스", "service", "서비스", "services", "서비시스", "insight", "인사이트", "insights", "인사이츠", "plant", "플랜트", "plantation", "플랜테이션",
            "plantations", "플랜테이션스", "fertilizer", "퍼틸라이저", "fertiliser", "퍼틸라이저", "nutrition", "뉴트리션", "seed", "시드", "seeds", "시즈",
            "soil", "소일", "water", "워터", "rice", "라이스", "field", "필드", "fresh", "프레시", "organic", "오가닉", "natural", "내추럴", "chemical", "케미컬",
            "chemicals", "케미컬스", "industries", "인더스트리스", "industry", "인더스트리", "trade", "트레이드", "exchange", "익스체인지", "fund", "펀드",
            "digital", "디지털", "mobile", "모바일", "online", "온라인", "live", "라이브", "show", "쇼", "day", "데이", "week", "위크", "summit", "서밋",
            "forum", "포럼", "expo", "엑스포", "award", "어워드", "awards", "어워즈", "center", "센터", "centre", "센터", "university", "유니버시티",
            "institute", "인스티튜트", "council", "카운슬", "association", "어소시에이션", "federation", "페더레이션", "union", "유니언", "alliance", "얼라이언스",
            "foundation", "파운데이션", "ministry", "미니스트리", "agency", "에이전시", "federal", "페더럴", "national", "내셔널", "royal", "로열",
            "general", "제너럴", "mil", "백만", "cr", "크로르", "bn", "십억", "mn", "백만", "vs", "대", "dr", "닥터", "mr", "미스터", "ms", "미즈", "st", "세인트",
            "kh", "케이에이치", "mig", "미그", "su", "수호이", "fincen", "핀센", "acid", "애시드", "urea", "요소",
        };
        for (int i = 0; i < w.length; i += 2) EN_WORDS.put(w[i], w[i + 1]);
    }
    private static final String ONSET = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String VOWEL = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String CODA = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"; // 맨 앞 칸은 받침 없음
    /** 자음 → {첫소리, 받침(될 수 있으면), 홀로 설 때 붙는 모음} */
    private static final java.util.Map<String, String[]> CONS = new java.util.HashMap<>();
    static {
        String[][] c = {{"b", "ㅂ", "ㅂ", "ㅡ"}, {"d", "ㄷ", "", "ㅡ"}, {"f", "ㅍ", "", "ㅡ"}, {"g", "ㄱ", "", "ㅡ"}, {"h", "ㅎ", "", "ㅡ"}, {"j", "ㅈ", "", "ㅣ"}, {"k", "ㅋ", "ㄱ", "ㅡ"},
            {"l", "ㄹ", "ㄹ", "ㅡ"}, {"m", "ㅁ", "ㅁ", "ㅡ"}, {"n", "ㄴ", "ㄴ", "ㅡ"}, {"p", "ㅍ", "ㅂ", "ㅡ"}, {"r", "ㄹ", "", "ㅡ"}, {"s", "ㅅ", "", "ㅡ"}, {"t", "ㅌ", "ㅅ", "ㅡ"},
            {"v", "ㅂ", "", "ㅡ"}, {"w", "ㅇ", "", "ㅜ"}, {"y", "ㅇ", "", "ㅣ"}, {"z", "ㅈ", "", "ㅡ"}, {"CH", "ㅊ", "", "ㅣ"}, {"SH", "ㅅ", "", "ㅣ"}, {"TH", "ㅅ", "", "ㅡ"}, {"NG", "ㅇ", "ㅇ", "ㅡ"}};
        for (String[] x : c) CONS.put(x[0], new String[] {x[1], x[2], x[3]});
    }
    /** 영어 철자 → 소리 조각 (이 차례로 찾는다). 모음은 "V:모음", 끝의 L은 긴 모음 */
    private static final String[][][] RULES = {
        {{"tion"}, {"SH", "V:ㅓ", "n"}}, {{"sion"}, {"j", "V:ㅓ", "n"}}, {{"ture"}, {"CH", "V:ㅓ"}}, {{"igh"}, {"V:ㅏ", "V:ㅣ"}}, {{"ough"}, {"V:ㅗ"}},
        {{"sch"}, {"s", "k"}}, {{"tch"}, {"CH"}}, {{"ch"}, {"CH"}}, {{"sh"}, {"SH"}}, {{"th"}, {"TH"}}, {{"ph"}, {"f"}}, {{"ck"}, {"k"}}, {{"qu"}, {"k", "w"}}, {{"x"}, {"k", "s"}},
        {{"ee"}, {"V:ㅣL"}}, {{"ea"}, {"V:ㅣL"}}, {{"oo"}, {"V:ㅜL"}}, {{"ou"}, {"V:ㅏ", "V:ㅜ"}}, {{"ow"}, {"V:ㅏ", "V:ㅜ"}}, {{"ai"}, {"V:ㅔ", "V:ㅣ"}},
        {{"ei"}, {"V:ㅔ", "V:ㅣ"}}, {{"oa"}, {"V:ㅗ"}}, {{"au"}, {"V:ㅗ"}}, {{"aw"}, {"V:ㅗ"}}, {{"oi"}, {"V:ㅗ", "V:ㅣ"}}, {{"ie"}, {"V:ㅣL"}}, {{"ue"}, {"V:ㅜL"}},
    };
    private static final Pattern MAGIC_E = Pattern.compile("[^aeiou][aeiou][^aeiouwxyr]e$");
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z][A-Za-z'’.-]*[A-Za-z]|[A-Za-z]");
    private static final Pattern STOP = Pattern.compile("t|k|p|b");
    private static final Pattern CLUSTER_HEAD = Pattern.compile("b|p|t|d|k|g|f|v|TH");
    private static final Pattern SHORT_A_CODA = Pattern.compile("k|t|p|b|n|m|NG");

    private static boolean isV(char c) { return "aeiouy".indexOf(c) >= 0; }
    private static boolean isVow(String p) { return p != null && p.startsWith("V:"); }
    private static String syl(char o, char v, char c) {
        return String.valueOf((char) (0xAC00 + (ONSET.indexOf(o) * 21 + VOWEL.indexOf(v)) * 28 + Math.max(0, CODA.indexOf(c))));
    }

    static List<String> sounds(String w) {
        w = w.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        boolean magic = false; // 끝의 묵음 e (stone, life, phosphate): 앞 모음을 길게
        if (w.length() > 3 && MAGIC_E.matcher(w).find()) { magic = true; w = w.substring(0, w.length() - 1); }
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < w.length()) {
            char c = w.charAt(i);
            char nx = i + 1 < w.length() ? w.charAt(i + 1) : ' ', n2 = i + 2 < w.length() ? w.charAt(i + 2) : ' ';
            // ay·ey·oy는 뒤에 모음이 없을 때만 이중모음 (NAVODAYA 나보다야)
            if ((c == 'a' || c == 'e' || c == 'o') && nx == 'y' && !isV(n2)) { out.addAll(c == 'o' ? Arrays.asList("V:ㅗ", "V:ㅣ") : Arrays.asList("V:ㅔ", "V:ㅣ")); i += 2; continue; }
            // ng는 뒤에 모음이 없을 때만 받침 ㅇ (Syngenta 신젠타)
            if (c == 'n' && nx == 'g' && !isV(n2)) { out.add("NG"); i += 2; continue; }
            // er·ar·or·ir·ur: 뒤에 모음이 없으면 r 모음 (Koppert 코퍼트)
            if ("aeiou".indexOf(c) >= 0 && nx == 'r' && !isV(n2)) { out.add("V:" + "ㅏㅓㅓㅗㅓ".charAt("aeiou".indexOf(c)) + "R"); i += 2; continue; }
            String[][] hit = null;
            for (String[][] r : RULES) if (w.startsWith(r[0][0], i)) { hit = r; break; }
            if (hit != null) { out.addAll(Arrays.asList(hit[1])); i += hit[0][0].length(); continue; }
            if (c == 'c') { out.add("eiy".indexOf(nx) >= 0 ? "s" : "k"); i++; continue; }
            if (c == 'g' && "eiy".indexOf(nx) >= 0 && i > 0) { out.add("j"); i++; continue; }
            if (c == 'q') { out.add("k"); i++; continue; }
            if (c == 'y' && (i == 0 || isV(nx))) { out.add("y"); i++; continue; } // 첫소리 y
            if (c == 'w' && isV(nx)) { out.add("w"); i++; continue; }
            int vi = "aeiouy".indexOf(c);
            if (vi >= 0) { out.add("V:" + "ㅏㅔㅣㅗㅓㅣ".charAt(vi)); i++; continue; }
            if (i > 0 && w.charAt(i - 1) == c) { i++; continue; } // 겹자음은 하나로
            String k = String.valueOf(c);
            out.add(CONS.containsKey(k) ? k : "k");
            i++;
        }
        if (magic) for (int k = out.size() - 1; k >= 0; k--) if (out.get(k).startsWith("V:")) {
            String v = out.get(k).substring(2);
            out.remove(k);
            if (v.equals("ㅏ")) out.addAll(k, Arrays.asList("V:ㅔ", "V:ㅣ"));
            else if (v.equals("ㅣ")) out.addAll(k, Arrays.asList("V:ㅏ", "V:ㅣ"));
            else if (v.equals("ㅓ")) out.add(k, "V:ㅠ");
            else out.add(k, "V:" + v);
            break;
        }
        return out;
    }

    private static String mapFirst(String v, String from, String to) {
        int at = from.indexOf(v.charAt(0));
        return at < 0 ? v : to.charAt(at) + v.substring(1);
    }

    static String assemble(List<String> ph) {
        List<char[]> out = new ArrayList<>(); // {첫소리, 모음, 받침}
        for (int i = 0; i < ph.size(); i++) {
            String p = ph.get(i);
            char o = 'ㅇ';
            String v;
            if (isVow(p)) v = p.substring(2);
            else if (i + 1 < ph.size() && isVow(ph.get(i + 1))) {
                v = ph.get(i + 1).substring(2);
                if (p.equals("y")) v = mapFirst(v, "ㅏㅓㅗㅜㅔㅐ", "ㅑㅕㅛㅠㅖㅒ");
                else if (p.equals("w")) v = mapFirst(v, "ㅏㅓㅔㅣㅐ", "ㅘㅝㅞㅟㅙ");
                else o = CONS.get(p)[0].charAt(0);
                if (p.equals("SH")) v = mapFirst(v, "ㅓㅗㅜㅏㅔ", "ㅕㅛㅠㅑㅖ"); // 쇼·셔·슈·샤·셰 (ㅈ·ㅊ 뒤는 그냥 저·조)
                // ㄹ 첫소리 앞 음절에 받침이 없으면 ㄹ을 덧붙인다 (Valagro 발라그로, Plant 플랜트)
                if (p.equals("l") && !out.isEmpty() && out.get(out.size() - 1)[2] == ' ') out.get(out.size() - 1)[2] = 'ㄹ';
                i++;
            } else { // 홀로 선 자음: 스·트·치…
                String[] c = CONS.get(p);
                out.add(new char[] {c[0].charAt(0), c[2].charAt(0), ' '});
                continue;
            }
            boolean rc = v.endsWith("R"), lng = v.endsWith("L");
            v = v.replaceAll("[RL]$", "");
            String n1 = i + 1 < ph.size() ? ph.get(i + 1) : null, n2 = i + 2 < ph.size() ? ph.get(i + 2) : null, n3 = i + 3 < ph.size() ? ph.get(i + 3) : null;
            // 긴 모음·이중모음 뒤 t·k·p·b는 받침 대신 트·크·프·브 (sheet 시트, phosphate 포스페이트)
            boolean diph = (lng || (i > 0 && isVow(ph.get(i - 1)) && isVow(ph.get(i)))) && n1 != null && STOP.matcher(n1).matches();
            char c = ' ';
            char vc = v.charAt(0);
            // 받침: 다음이 자음이고 그다음이 모음이 아니고(닫힌 음절), r 모음·이중모음 뒤가 아니고, 자음+r/l+모음(트로, 플라)이 아닐 때
            boolean cluster = n1 != null && CLUSTER_HEAD.matcher(n1).matches() && ("r".equals(n2) || "l".equals(n2)) && isVow(n3);
            if (n1 != null && !isVow(n1) && !isVow(n2) && !rc && !diph && !cluster) {
                String cc = n1.equals("n") && ("k".equals(n2) || "g".equals(n2)) ? "ㅇ" : CONS.get(n1)[1];
                if (!cc.isEmpty()) { c = cc.charAt(0); if (vc == 'ㅏ' && SHORT_A_CODA.matcher(n1).matches()) vc = 'ㅐ'; i++; }
            }
            out.add(new char[] {o, vc, c});
        }
        StringBuilder b = new StringBuilder();
        for (char[] x : out) b.append(syl(x[0], x[1], x[2]));
        return b.toString();
    }

    static String enWord(String w) {
        String lw = w.toLowerCase(java.util.Locale.ROOT);
        if (EN_WORDS.containsKey(lw)) return EN_WORDS.get(lw);
        if (w.matches("[A-Z]{1,5}")) { // 대문자 약어는 낱자로
            StringBuilder b = new StringBuilder();
            for (char c : w.toCharArray()) b.append(ABC[c - 'A']);
            return b.toString();
        }
        if (w.length() == 1) return ABC[Character.toUpperCase(w.charAt(0)) - 'A'];
        return assemble(sounds(w));
    }

    /** 낱말 안의 대소문자 경계(CropLife, StoneX)를 나눈다. 표에 통째로 있으면(FinCEN) 나누지 않는다 */
    static List<String> splitWord(String w) {
        if (EN_WORDS.containsKey(w.toLowerCase(java.util.Locale.ROOT))) return Arrays.asList(w);
        String s = w.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        List<String> out = new ArrayList<>();
        for (String x : s.split("[\\s'’.-]+")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    /** 글 속 영문 낱말을 한글 소리로 (F1이 읽기 전에) */
    static String hangulize(String text) {
        Matcher m = LATIN_WORD.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder r = new StringBuilder();
            for (String part : splitWord(m.group())) r.append(r.length() > 0 ? " " : "").append(enWord(part));
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean emoji(int cp) {
        return (cp >= 0x1F600 && cp <= 0x1F64F) || (cp >= 0x1F300 && cp <= 0x1F5FF) || (cp >= 0x1F680 && cp <= 0x1F6FF)
            || (cp >= 0x1F700 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF) || (cp >= 0x1F1E6 && cp <= 0x1F1FF);
    }

    /** 긴 문장을 자를 때 양쪽 조각이 이보다 짧지 않게 */
    private static final int MIN_PART = 25;

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
                    cutLong(s, max, out);
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

    /**
     * max자 넘는 한 문장을 고른 길이로 자른다: 조각 수 n = ceil(길이/max), 목표 길이 = 길이/n에 가장 가까운 쉼표 뒤(없으면 빈칸).
     * 마지막이 아닌 조각은 쉼표로 끝낸다: 마침표 없이 잘린 덩이를 F1이 문장 끝처럼 내려 읽어 한 문장이 두 문장으로 들렸다 (2026-10-27)
     */
    static void cutLong(String s, int max, List<String> out) {
        while (s.length() > max) {
            int n = (s.length() + max - 1) / max;
            double target = (double) s.length() / n;
            int best = -1;
            double bd = 1e9;
            for (int i = s.indexOf(", "); i >= 0; i = s.indexOf(", ", i + 1)) {
                int len = i + 1;
                if (len <= max && len >= MIN_PART && s.length() - len - 1 >= MIN_PART && Math.abs(len - target) < bd) { bd = Math.abs(len - target); best = len; }
            }
            if (best < 0) for (int i = s.indexOf(' '); i >= 0; i = s.indexOf(' ', i + 1)) {
                if (i <= max && i >= MIN_PART && s.length() - i - 1 >= MIN_PART && Math.abs(i - target) < bd) { bd = Math.abs(i - target); best = i; }
            }
            if (best < 0) { best = s.lastIndexOf(' ', max); if (best <= 0) best = max; }
            String left = s.substring(0, best).trim();
            if (!left.matches("(?s).*[,.!?]")) left += ",";
            out.add(left);
            s = s.substring(best).replaceAll("^[,\\s]+", "");
        }
        if (!s.isEmpty()) out.add(s);
    }

    private static void flush(List<String> out, StringBuilder b) {
        if (b.length() > 0) out.add(b.toString().trim());
        b.setLength(0);
    }
}
