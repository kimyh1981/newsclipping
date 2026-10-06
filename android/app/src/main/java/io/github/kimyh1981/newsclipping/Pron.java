package io.github.kimyh1981.newsclipping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 소리 내어 읽기 전에 글을 '들리는 대로' 고친다 (표준 발음법의 경음화·ㄴ첨가와 띄어 읽기 쉼).
 * js/pron.js와 같은 규칙이고, tests/fixtures/pron.json으로 두 쪽 결과를 맞춰 본다. 자막은 그대로, 소리에만 쓴다.
 */
final class Pron {
    private Pron() {}

    /** js/pron.js의 WORDS와 같은 표: {쓴 글, 소리, 뒤에 오면 바꾸지 않는 말} */
    private static final String[][] WORDS = {
        {"대가", "대까", "의|들"}, {"주가", "주까", ""}, {"물가", "물까", ""}, {"유가", "유까", "족|증"}, {"원가", "원까", ""}, {"단가", "단까", ""},
        {"정가", "정까", ""}, {"평가", "평까", ""}, {"저가", "저까", ""}, {"고가", "고까", "도로|차도"}, {"시가총액", "시까총액", ""}, {"분양가", "분양까", ""},
        {"공시가", "공시까", ""}, {"매매가", "매매까", ""}, {"거래가", "거래까", "격"}, {"실거래가", "실거래까", "격"}, {"판매가", "판매까", "격"}, {"공급가", "공급까", "격"},
        {"낙찰가", "낙찰까", ""}, {"사건", "사껀", ""}, {"조건", "조껀", ""}, {"요건", "요껀", ""}, {"안건", "안껀", ""}, {"여건", "여껀", ""},
        {"인건비", "인껀비", ""}, {"건수", "건쑤", ""}, {"점수", "점쑤", ""}, {"개수", "개쑤", ""}, {"성과", "성꽈", ""}, {"내과", "내꽈", ""},
        {"외과", "외꽈", ""}, {"안과", "안꽈", "\\s*밖"}, {"치과", "치꽈", ""}, {"장점", "장쩜", ""}, {"단점", "단쩜", ""}, {"초점", "초쩜", ""},
        {"쟁점", "쟁쩜", ""}, {"관점", "관쩜", ""}, {"요점", "요쩜", ""}, {"허점", "허쩜", ""}, {"시점", "시쩜", ""}, {"맹점", "맹쩜", ""},
        {"강점", "강쩜", "기"}, {"논점", "논쩜", ""}, {"결점", "결쩜", ""}, {"정점", "정쩜", ""}, {"기점", "기쩜", ""}, {"문제점", "문제쩜", ""},
        {"공통점", "공통쩜", ""}, {"차이점", "차이쩜", ""}, {"개선점", "개선쩜", ""}, {"한계점", "한계쩜", ""}, {"출발점", "출발쩜", ""}, {"교차점", "교차쩜", ""},
        {"글자", "글짜", ""}, {"문자", "문짜", ""}, {"한자", "한짜", "리"}, {"인기", "인끼", "척"}, {"일자리", "일짜리", ""}, {"갈등", "갈뜽", ""},
        {"발전", "발쩐", ""}, {"발생", "발쌩", ""}, {"발동", "발똥", ""}, {"출동", "출똥", ""}, {"출시", "출씨", ""}, {"출석", "출썩", ""},
        {"결정", "결쩡", ""}, {"결제", "결쩨", ""}, {"결산", "결싼", ""}, {"실적", "실쩍", ""}, {"실제", "실쩨", ""}, {"실시", "실씨", ""},
        {"실장", "실짱", ""}, {"일정", "일쩡", ""}, {"일시", "일씨", ""}, {"활동", "활똥", ""}, {"절실", "절씰", ""}, {"물질", "물찔", ""},
        {"열대", "열때", ""}, {"몰수", "몰쑤", ""}, {"절도", "절또", ""}, {"말살", "말쌀", ""}, {"열정", "열쩡", ""}, {"헌법", "헌뻡", ""},
        {"불법", "불뻡", ""}, {"위법", "위뻡", ""}, {"형법", "형뻡", ""}, {"민법", "민뻡", ""}, {"편법", "편뻡", ""}, {"탈법", "탈뻡", ""},
        {"상법", "상뻡", ""}, {"세법", "세뻡", ""}, {"문법", "문뻡", ""}, {"입법", "입뻡", ""}, {"수법", "수뻡", ""}, {"특별법", "특별뻡", ""},
        {"특검법", "특검뻡", ""}, {"재산세", "재산쎄", ""}, {"부가세", "부가쎄", ""}, {"종부세", "종부쎄", ""}, {"양도세", "양도쎄", ""}, {"법인세", "법인쎄", ""},
        {"증여세", "증여쎄", ""}, {"주민세", "주민쎄", ""}, {"신분증", "신분쯩", ""}, {"후유증", "후유쯩", ""}, {"우울증", "우울쯩", ""}, {"면허증", "면허쯩", ""},
        {"합병증", "합뼝쯩", ""}, {"감염증", "감염쯩", ""}, {"휘발유", "휘발류", ""}, {"식용유", "식용뉴", ""}, {"국민연금", "국민년금", ""}, {"색연필", "생년필", ""},
        {"한여름", "한녀름", ""}, {"담요", "담뇨", ""}, {"영업용", "영업뇽", ""}, {"일일이", "일리리", ""},
    };
    private static final Pattern[] WORD_RES = new Pattern[WORDS.length];
    static {
        for (int i = 0; i < WORDS.length; i++) {
            WORD_RES[i] = Pattern.compile("(^|[^가-힣])" + Pattern.quote(WORDS[i][0]) + (WORDS[i][2].isEmpty() ? "" : "(?!" + WORDS[i][2] + ")"));
        }
    }
    private static final Pattern ADNOMINAL = Pattern.compile("([가-힣]) (수|것|줄|바)(?=[^가-힣]|$|[가도는밖조만을이은으에])");
    private static final Pattern STATION = Pattern.compile("([가-힣]{2,})역(?=[^가-힣]|$|에|으로|까지|앞|광장|사거리|인근|일대|부근|과|의|은|을|이|도)");
    private static final Pattern NOT_STATION = Pattern.compile("(지|영|구|권|해|수|무|방|검|통|번|징|용|병|공|노|배|광|현|반)$");
    private static final Pattern LEAD = Pattern.compile("^(그러나|하지만|그런데|그러면서|또한|한편|특히|다만|따라서|아울러|반면|게다가|결국|실제로|앞서|이에 따라|이와 함께|이에 대해)\\s+(?!,)");
    private static final Pattern LINK = Pattern.compile("(했고|됐고|였고|었고|았고|겠고|있고|없고|이고|하며|되며|이며|으며|었으며|였으며|했으며|면서|지만|는데|은데|으나|었으나|했으나|라며|다며)\\s");
    private static final Pattern SENTENCE = Pattern.compile("[^.?!]+[.?!]*");
    private static final Pattern ACCORDING = Pattern.compile("(에 따르면)\\s(?!,)");

    private static boolean syl(char c) { return c >= 0xac00 && c <= 0xd7a3; }
    private static int jong(char c) { return (c - 0xac00) % 28; }

    /** 첫소리 ㄱㄷㅂㅅㅈ을 ㄲㄸㅃㅆㅉ으로 */
    static String tense(String ch) {
        char c = ch.charAt(0);
        if (!syl(c)) return ch;
        int cho = (c - 0xac00) / 588;
        int to = cho == 0 ? 1 : cho == 3 ? 4 : cho == 7 ? 8 : cho == 9 ? 10 : cho == 12 ? 13 : -1;
        return to < 0 ? ch : String.valueOf((char) (c + (to - cho) * 588));
    }

    static String say(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = text;
        for (int i = 0; i < WORDS.length; i++) s = WORD_RES[i].matcher(s).replaceAll("$1" + Matcher.quoteReplacement(WORDS[i][1]));
        s = adnominal(s);
        s = stations(s);
        return pauses(s);
    }

    private static String adnominal(String s) {
        Matcher m = ADNOMINAL.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String a = m.group(1);
            String r = jong(a.charAt(0)) == 8 ? a + " " + tense(m.group(2)) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String stations(String s) {
        Matcher m = STATION.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            int j = jong(name.charAt(name.length() - 1));
            String r = NOT_STATION.matcher(name).find() || j == 0 ? m.group() : name + (j == 8 ? "력" : "녁");
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String pauses(String s) {
        Matcher m = SENTENCE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String sent = m.group();
            int k = 0;
            while (k < sent.length() && Character.isWhitespace(sent.charAt(k))) k++;
            String t = LEAD.matcher(sent.substring(k)).replaceFirst("$1, ");
            t = ACCORDING.matcher(t).replaceAll("$1, ");
            if (t.length() >= 30) t = links(t);
            m.appendReplacement(sb, Matcher.quoteReplacement(sent.substring(0, k) + t));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String links(String t) {
        Matcher m = LINK.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String before = t.substring(0, m.start());
            before = before.substring(before.lastIndexOf(',') + 1);
            String after = t.substring(m.end()).split("[,.?!]", -1)[0];
            String r = before.length() >= 12 && after.length() >= 10 ? m.group(1) + ", " : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
