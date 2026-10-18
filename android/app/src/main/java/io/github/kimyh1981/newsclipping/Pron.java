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
        {"산업용", "산업뇽", ""}, {"가정용", "가정뇽", ""}, {"상업용", "상업뇽", ""}, {"몇 월", "며둴", ""}, {"맛없", "마덥", ""}, {"겉옷", "거돋", ""}, {"헛웃음", "허두슴", ""}, {"값어치", "가버치", ""},
    };
    /** '세 대가 부딪쳤다'의 대가는 값 가(價)가 아니다: 고유어 수 바로 뒤의 '대'로 시작하는 말은 두지 않는다 */
    private static final String AFTER_COUNT = "(?<!(?:한|두|세|네|섯|곱|덟|홉|열|무|물|른|흔|쉰|순|든))";
    private static final Pattern[] WORD_RES = new Pattern[WORDS.length];
    static {
        for (int i = 0; i < WORDS.length; i++) {
            WORD_RES[i] = Pattern.compile((WORDS[i][0].charAt(0) == '대' ? AFTER_COUNT : "") + "(^|[^가-힣])" + Pattern.quote(WORDS[i][0]) + (WORDS[i][2].isEmpty() ? "" : "(?!" + WORDS[i][2] + ")"));
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

    /** js/pron.js의 LATIN과 같은 표: 낱말처럼 읽는 로마자 약어와 상표·영어 낱말 {쓴 글, 소리} */
    private static final String[][] LATIN = {
        {"COVID-19", "코비드 일구"}, {"OPEC+", "오펙 플러스"}, {"K-POP", "케이팝"}, {"K-pop", "케이팝"}, {"Wi-Fi", "와이파이"}, {"WiFi", "와이파이"},
        {"ChatGPT", "챗지피티"}, {"OpenAI", "오픈에이아이"}, {"LiDAR", "라이다"}, {"REITs", "리츠"}, {"Nasdaq", "나스닥"}, {"NASA", "나사"},
        {"NATO", "나토"}, {"FIFA", "피파"}, {"UEFA", "유에파"}, {"UNESCO", "유네스코"}, {"UNICEF", "유니세프"}, {"OPEC", "오펙"},
        {"APEC", "에이펙"}, {"ASEAN", "아세안"}, {"NAFTA", "나프타"}, {"BRICS", "브릭스"}, {"QUAD", "쿼드"}, {"AUKUS", "오커스"},
        {"IPEF", "아이펙"}, {"RCEP", "알셉"}, {"THAAD", "사드"}, {"COP", "콥"}, {"CITES", "사이테스"}, {"NEOM", "네옴"},
        {"NIMBY", "님비"}, {"PIMFY", "핌피"}, {"AIDS", "에이즈"}, {"SARS", "사스"}, {"MERS", "메르스"}, {"COVID", "코비드"},
        {"NASDAQ", "나스닥"}, {"KOSPI", "코스피"}, {"KOSDAQ", "코스닥"}, {"KOTRA", "코트라"}, {"KOICA", "코이카"}, {"KORAIL", "코레일"},
        {"KEPCO", "켑코"}, {"KOGAS", "코가스"}, {"POSCO", "포스코"}, {"KAIST", "카이스트"}, {"POSTECH", "포스텍"}, {"UNIST", "유니스트"},
        {"DGIST", "디지스트"}, {"GIST", "지스트"}, {"KIST", "키스트"}, {"KAI", "카이"}, {"HYBE", "하이브"},
        // 낱말로 읽는 회사·브랜드 이름 (KIA는 기아, 2026-10-18)
        {"KIA", "기아"}, {"HYUNDAI", "현대"}, {"SAMSUNG", "삼성"}, {"LOTTE", "롯데"}, {"HANWHA", "한화"}, {"DAEWOO", "대우"}, {"KOREA", "코리아"}, {"NVIDIA", "엔비디아"}, {"TESLA", "테슬라"}, {"INTEL", "인텔"}, {"IKEA", "이케아"}, {"LEGO", "레고"}, {"META", "메타"}, {"UBER", "우버"}, {"NIKE", "나이키"}, {"ADIDAS", "아디다스"}, {"ZARA", "자라"}, {"DISNEY", "디즈니"}, {"TOYOTA", "도요타"}, {"HONDA", "혼다"}, {"NISSAN", "닛산"}, {"AUDI", "아우디"}, {"VOLVO", "볼보"}, {"FORD", "포드"}, {"JEEP", "지프"}, {"SONY", "소니"}, {"NAVER", "네이버"},
        {"KAKAO", "카카오"}, {"OLED", "올레드"}, {"AMOLED", "아몰레드"}, {"DRAM", "디램"}, {"SRAM", "에스램"}, {"NAND", "낸드"},
        {"RAM", "램"}, {"ROM", "롬"}, {"LAN", "랜"}, {"PIN", "핀"}, {"ARM", "암"}, {"SPAC", "스팩"},
        {"REIT", "리츠"}, {"ADAS", "에이다스"}, {"SWIFT", "스위프트"}, {"NEET", "니트"}, {"EZ", "이지"}, {"YOLO", "욜로"}, {"FOMO", "포모"},
        {"VS", "대"}, {"POP", "팝"}, {"NEWS", "뉴스"}, {"SHOW", "쇼"}, {"LIVE", "라이브"}, {"BEST", "베스트"},
        {"NEW", "뉴"}, {"TOP", "톱"}, {"PLUS", "플러스"}, {"PRO", "프로"}, {"MAX", "맥스"}, {"ULTRA", "울트라"},
        {"FOLD", "폴드"}, {"FLIP", "플립"}, {"MINI", "미니"}, {"AIR", "에어"}, {"PAY", "페이"}, {"NO", "노"},
        {"ON", "온"}, {"OFF", "오프"}, {"KING", "킹"}, {"STAR", "스타"}, {"BANK", "뱅크"}, {"ONE", "원"},
        {"GO", "고"}, {"UP", "업"}, {"YES", "예스"}, {"HOT", "핫"}, {"BIG", "빅"}, {"EDGE", "엣지"},
        {"2D", "투디"}, {"3D", "쓰리디"}, {"4D", "포디"}, {"3G", "쓰리지"}, {"4G", "포지"}, {"5G", "파이브지"},
        {"6G", "식스지"}, {"4K", "포케이"}, {"8K", "에잇케이"}, {"G2", "지투"}, {"G7", "지세븐"}, {"G8", "지에잇"},
        {"G20", "지이십"},
    };
    /** 알파벳 이름 (C 씨, R 알, Z 제트) */
    private static final String[] ABC = {"에이", "비", "씨", "디", "이", "에프", "지", "에이치", "아이", "제이", "케이", "엘", "엠", "엔", "오", "피", "큐", "알", "에스", "티", "유", "브이", "더블유", "엑스", "와이", "제트"};
    private static final Pattern[] LATIN_RES = new Pattern[LATIN.length];
    static {
        for (int i = 0; i < LATIN.length; i++) LATIN_RES[i] = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(LATIN[i][0]) + "(?![A-Za-z0-9])");
    }
    private static final Pattern AMP = Pattern.compile("(?<![A-Za-z0-9])([A-Z]{1,3})\\s?&\\s?([A-Z]{1,3})(?![A-Za-z])");
    private static final Pattern CAPS = Pattern.compile("(?<![A-Za-z0-9])[A-Z]{2,5}(?![A-Za-z0-9])(?!-\\d)");
    /** 네 글자 이상인데 낱말처럼 읽히는 것(AGRI, COAT)은 낱자로 끊지 않는다 */
    private static final Pattern WORDLIKE = Pattern.compile("^[^AEIOU]?[AEIOU][^AEIOU]{1,2}[AEIOU]|[^AEIOU][AEIOU]{2}[^AEIOU]");
    private static final Pattern CAP_HANGUL = Pattern.compile("(?<![A-Za-z0-9])[A-Z](?=-?[가-힣])");

    private static String spell(String w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) b.append(ABC[w.charAt(i) - 'A']);
        return b.toString();
    }

    static String latin(String s) {
        for (int i = 0; i < LATIN.length; i++) s = LATIN_RES[i].matcher(s).replaceAll(Matcher.quoteReplacement(LATIN[i][1]));
        Matcher m = AMP.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(spell(m.group(1)) + "앤" + spell(m.group(2))));
        m.appendTail(sb);
        m = CAPS.matcher(sb.toString());
        sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(m.group().length() > 3 && WORDLIKE.matcher(m.group()).find() ? m.group() : spell(m.group())));
        m.appendTail(sb);
        s = sb.toString();
        return spellAll(CAP_HANGUL, s);
    }

    private static String spellAll(Pattern p, String s) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(spell(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    static String say(String text) {
        if (text == null || text.isEmpty()) return text;
        String s = symbols(latin(numbers(text)));
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

    // 숫자·기호 읽기: js/pron.js의 numbers·symbols와 같은 규칙
    private static final String[] DIG = {"영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] N1 = {"", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"};
    private static final String[] N10 = {"", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"};
    private static final java.util.Map<String, String> UNITS = new java.util.HashMap<>();
    static {
        String[][] u = {{"%p", "퍼센트포인트"}, {"%포인트", "퍼센트포인트"}, {"%", "퍼센트"}, {"퍼센트포인트", "퍼센트포인트"}, {"퍼센트", "퍼센트"},
            {"km/h", "킬로미터"}, {"kWh", "킬로와트시"}, {"MWh", "메가와트시"}, {"GWh", "기가와트시"}, {"TWh", "테라와트시"}, {"kW", "킬로와트"}, {"MW", "메가와트"}, {"GW", "기가와트"},
            {"km²", "제곱킬로미터"}, {"km2", "제곱킬로미터"}, {"㎢", "제곱킬로미터"}, {"km", "킬로미터"}, {"kg", "킬로그램"}, {"mg", "밀리그램"}, {"μg", "마이크로그램"}, {"㎍", "마이크로그램"},
            {"cm", "센티미터"}, {"mm", "밀리미터"}, {"ml", "밀리리터"}, {"mL", "밀리리터"}, {"㎡", "제곱미터"}, {"m²", "제곱미터"}, {"m2", "제곱미터"}, {"m", "미터"}, {"g", "그램"}, {"t", "톤"},
            {"ℓ", "리터"}, {"L", "리터"}, {"ha", "헥타르"}, {"ppm", "피피엠"}, {"bp", "베이시스포인트"}, {"GB", "기가바이트"}, {"TB", "테라바이트"}, {"MB", "메가바이트"}, {"℃", "도"}, {"°C", "도"}, {"°", "도"}};
        for (String[] x : u) UNITS.put(x[0], x[1]);
    }
    private static final String UNIT = "%p|%포인트|%|퍼센트포인트|퍼센트|km/h|kWh|MWh|GWh|TWh|kW|MW|GW|km²|km2|㎢|km|kg|mg|μg|㎍|cm|mm|ml|mL|㎡|m²|m2|m|g|t|ℓ|L|ha|ppm|bp|GB|TB|MB|℃|°C|°";
    private static final Pattern NUM = Pattern.compile("(?<![\\w.-])(?:(?<![^\\s(])(-))?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(?:\\s*(" + UNIT + ")(?![A-Za-z])|(?!\\.?\\d)(?![A-Za-z]))");
    private static final String COUNTER = "번째|시간|군데|차례|켤레|송이|그루|마리|가지|사람|그릇|자루|봉지|포기|마디|끼(?!리)|개(?!월|국|사|년|소|항|교|층|조)|명|살|곳|달(?!러)"
        + "|권(?!리|한|역|고|자|위)|잔(?!액|고|여)|벌(?!금|써|어)|채(?!권)|척|통(?!화|신|계|합|과|령)|장(?!관|기|애|면)|배(?!럴)"
        + "|번(?!째|지|호|길)(?!\\s?(?:출구|국도|지방도|도로|버스|타자|홀|게이트|트랙|채널|레인|환자|확진자|후보))";
    private static final Pattern NATIVE = Pattern.compile("^\\s?(?:" + COUNTER + ")");
    private static final Pattern DAE = Pattern.compile("^\\s?대(?=[가를의는도만와씩로]|\\s(?:이상|이하|가량|정도|씩|분)|[,.?!]|$)");
    private static final Pattern CLOCK = Pattern.compile("^\\s?시(?!간|장|즌|청|민|군|의회|의원|도|정|각|점|대|공|설|리즈)");
    private static final String[] ROUGH = {"", "한두", "두세", "서너", "네다섯", "대여섯", "예닐곱", "일고여덟"};
    private static final Pattern RANGE = Pattern.compile("(?<![\\d.,])(\\d{1,2})\\s*(?:~|-|에서\\s?)(\\d{1,2})(\\s?)(" + COUNTER + "|대(?=[가를의는도만와씩로\\s,.]|$))");
    private static final Pattern TIME = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-4]):([0-5]\\d)(?![\\d:])");
    // 나라마다 통화 이름 (js/pron.js의 MONEY·CODES와 같게). ¥는 중국 얘기면 위안, 아니면 엔
    private static final String[][] MONEY_SIGNS = {
        {"US$", "달러"}, {"A$", "호주달러"}, {"AU$", "호주달러"}, {"C$", "캐나다달러"}, {"CA$", "캐나다달러"}, {"HK$", "홍콩달러"}, {"S$", "싱가포르달러"},
        {"NT$", "대만달러"}, {"NZ$", "뉴질랜드달러"}, {"R$", "헤알"}, {"Rp", "루피아"}, {"Rs.", "루피"}, {"Rs", "루피"}, {"RM", "링깃"},
        {"$", "달러"}, {"€", "유로"}, {"£", "파운드"}, {"₩", "원"}, {"₹", "루피"}, {"₫", "동"}, {"₽", "루블"}, {"฿", "바트"}, {"₱", "페소"}, {"₺", "리라"}, {"₦", "나이라"}, {"₴", "흐리브냐"},
    };
    private static final String[][] MONEY_CODES = {
        {"USD", "달러"}, {"EUR", "유로"}, {"JPY", "엔"}, {"CNY", "위안"}, {"RMB", "위안"}, {"GBP", "파운드"}, {"KRW", "원"}, {"VND", "동"}, {"INR", "루피"}, {"BRL", "헤알"}, {"RUB", "루블"},
        {"IDR", "루피아"}, {"THB", "바트"}, {"AUD", "호주달러"}, {"CAD", "캐나다달러"}, {"CHF", "스위스프랑"}, {"HKD", "홍콩달러"}, {"SGD", "싱가포르달러"}, {"TWD", "대만달러"},
        {"NZD", "뉴질랜드달러"}, {"MXN", "멕시코페소"}, {"MYR", "링깃"}, {"SAR", "리얄"}, {"AED", "디르함"}, {"ZAR", "랜드"}, {"NGN", "나이라"}, {"EGP", "이집트파운드"},
        {"PKR", "파키스탄루피"}, {"BDT", "타카"}, {"PLN", "즐로티"}, {"SEK", "크로나"}, {"NOK", "크로네"}, {"DKK", "크로네"}, {"ARS", "아르헨티나페소"}, {"CLP", "칠레페소"}, {"COP", "콜롬비아페소"},
    };
    private static final java.util.Map<String, String> MONEY_NAME = new java.util.HashMap<>();
    private static final String AMOUNT = "((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)(\\s?[조억만천])?";
    private static final String JOSA = "(으로|로|을|를|은|는|과|와|이(?![가-힣])|가(?![가-힣]))?";
    private static final Pattern SIGN_RE, CODE_AFTER, CODE_BEFORE;
    private static final Pattern CHINA = Pattern.compile("중국|위안|인민폐|China|Chinese|CNY|RMB");
    static {
        StringBuilder signs = new StringBuilder();
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String[] m : MONEY_SIGNS) { MONEY_NAME.put(m[0], m[1]); keys.add(m[0]); }
        keys.sort((a, b) -> b.length() - a.length());
        for (String k : keys) signs.append(Pattern.quote(k)).append('|');
        StringBuilder codes = new StringBuilder();
        for (String[] m : MONEY_CODES) { MONEY_NAME.put(m[0], m[1]); codes.append(codes.length() > 0 ? "|" : "").append(m[0]); }
        SIGN_RE = Pattern.compile("(?<![A-Za-z])(" + signs + "¥)\\s?" + AMOUNT + JOSA);
        CODE_AFTER = Pattern.compile(AMOUNT + "\\s?(" + codes + "|元|円|đ)(?![A-Za-z])" + JOSA);
        CODE_BEFORE = Pattern.compile("(?<![A-Za-z])(" + codes + ")\\s?" + AMOUNT + JOSA);
    }

    private static String josa(String word, String p) {
        if (p == null) return word;
        int j = jong(word.charAt(word.length() - 1));
        boolean has = j != 0;
        if (p.equals("으로") || p.equals("로")) return word + (has && j != 8 ? "으로" : "로");
        if (p.equals("을") || p.equals("를")) return word + (has ? "을" : "를");
        if (p.equals("은") || p.equals("는")) return word + (has ? "은" : "는");
        if (p.equals("과") || p.equals("와")) return word + (has ? "과" : "와");
        return word + (has ? "이" : "가");
    }

    static String money(String s) {
        String yen = CHINA.matcher(s).find() ? "위안" : "엔";
        s = moneyPass(s, SIGN_RE, 1, 2, 3, 4, yen);
        s = moneyPass(s, CODE_BEFORE, 1, 2, 3, 4, yen);
        return moneyPass(s, CODE_AFTER, 3, 1, 2, 4, yen);
    }

    private static String moneyPass(String s, Pattern re, int ci, int ni, int ui, int pi, String yen) {
        Matcher m = re.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String c = m.group(ci);
            String w = c.equals("¥") ? yen : c.equals("元") ? "위안" : c.equals("円") ? "엔" : c.equals("đ") ? "동" : MONEY_NAME.get(c);
            if (w.matches("^.{2,}(달러|페소|프랑|파운드|루피)$")) w = " " + w; // 삼억 호주달러
            String u = m.group(ui) == null ? "" : m.group(ui);
            m.appendReplacement(sb, Matcher.quoteReplacement(josa(m.group(ni) + u + w, m.group(pi))));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    private static final Pattern EVENT = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s?·\\s?(\\d{1,2})(?![\\d.])");
    private static final Pattern BIG_UNIT = Pattern.compile("(\\d[만억조천])\\s?(" + UNIT + ")(?![A-Za-z])");
    private static final Pattern FRACTION = Pattern.compile("(?<![\\d/.])(\\d{1,3})/(\\d{1,3})(?![\\d/])");

    private static String sino4(int n) {
        String d = String.format(java.util.Locale.ROOT, "%04d", n);
        StringBuilder out = new StringBuilder();
        String[] u = {"천", "백", "십", ""};
        for (int i = 0; i < 4; i++) {
            int k = d.charAt(i) - '0';
            if (k > 0) out.append(k == 1 && i < 3 ? "" : DIG[k]).append(u[i]);
        }
        return out.toString();
    }

    static String sino(String digits) {
        if (digits.matches("0\\d.*") || digits.length() > 16) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) b.append(i > 0 ? " " : "").append(DIG[digits.charAt(i) - '0']);
            return b.toString();
        }
        if (digits.matches("0+")) return "영";
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        String n = digits;
        String[] units = {"", "만", "억", "조"};
        for (int i = 0; !n.isEmpty(); i++) {
            int cut = Math.max(0, n.length() - 4);
            int g = Integer.parseInt(n.substring(cut));
            n = n.substring(0, cut);
            if (g > 0) parts.addFirst((g == 1 && i == 1 ? "" : sino4(g)) + units[i]);
        }
        return String.join(" ", parts);
    }

    private static String nativeNum(int n) {
        if (n == 20) return "스무";
        return N10[n / 10] + N1[n % 10];
    }

    /** 전각 숫자·기호와 한 글자 단위(㎞·㎏)를 보통 글자로 */
    private static String plain(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '０' && c <= '９' || "％～：＋－．".indexOf(c) >= 0 ? (char) (c - 0xfee0) : c);
        }
        return b.toString().replaceAll("[∼〜]", "~").replace("㎞", "km").replace("㎏", "kg").replace("㎜", "mm").replace("㎝", "cm").replace("㏊", "ha").replace("㎾", "kW").replace("㎿", "MW").replaceAll("[ㆍ・•∙]", "·");
    }

    static String numbers(String s) {
        s = plain(s).replaceAll("코로나\\s?-?19", "코로나 일구").replaceAll("영하\\s?-(?=\\d)", "영하 ").replaceAll("(\\d)\\s?a당", "$1아르당").replaceAll("시속\\s?(\\d[\\d,.]*)\\s?km/h", "$1km/h");
        // 5·18, 6·25, 10·15 대책처럼 날짜로 이름 붙인 일은 오일팔, 육이오, 십일오로
        Matcher e = EVENT.matcher(s);
        StringBuffer eb = new StringBuffer();
        while (e.find()) {
            StringBuilder r = new StringBuilder(sino(e.group(1)));
            for (char c : e.group(2).toCharArray()) r.append(DIG[c - '0']);
            e.appendReplacement(eb, r.toString());
        }
        e.appendTail(eb);
        s = eb.toString();
        // 480만 t, 132만ha처럼 만·억 뒤의 단위
        e = BIG_UNIT.matcher(s);
        eb = new StringBuffer();
        while (e.find()) e.appendReplacement(eb, Matcher.quoteReplacement(e.group(1) + " " + UNITS.get(e.group(2))));
        e.appendTail(eb);
        s = eb.toString();
        s = s.replaceAll("(\\d{4})\\s?[.\\-/]\\s?(\\d{1,2})\\s?[.\\-/]\\s?(\\d{1,2})(?!\\d)\\.?", "$1년 $2월 $3일");
        Matcher m = TIME.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int mm = Integer.parseInt(m.group(2));
            m.appendReplacement(sb, Integer.parseInt(m.group(1)) + "시" + (mm > 0 ? " " + mm + "분" : ""));
        }
        m.appendTail(sb);
        s = sb.toString();
        s = money(s);
        s = s.replaceAll("(?<![^\\s(])\\+(?=\\d)", "플러스 ").replaceAll("±\\s?(?=\\d)", "플러스마이너스 ");
        s = s.replaceAll("([1-4])/4\\s?분기", "$1분기");
        s = FRACTION.matcher(s).replaceAll("$2분의 $1");
        m = RANGE.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
            String sp = m.group(3), c = m.group(4);
            String r = b == a + 1 && a >= 1 && a < ROUGH.length ? ROUGH[a] + sp + c : m.group(1) + sp + c + "에서 " + m.group(2) + sp + c;
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        s = sb.toString();
        s = s.replaceAll("(\\d)\\s*~\\s*(?=\\d)", "$1에서 ").replaceAll("(\\d)-(?=\\d)", "$1에서 ").replaceAll("(\\d)\\s?:\\s?(?=\\d)", "$1 대 ");
        m = NUM.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String minus = m.group(1), num = m.group(2), frac = m.group(3), unit = m.group(4);
            String digits = num.replace(",", "");
            String rest = s.substring(m.end());
            String before = s.substring(Math.max(0, m.start() - 2), m.start());
            boolean bare = minus == null && frac == null && unit == null && !num.contains(",") && !before.matches("(?s).*제\\s?");
            int n = digits.length() <= 9 ? Integer.parseInt(digits) : Integer.MAX_VALUE;
            String out = null;
            if (bare && n >= 1 && n <= 99) {
                if (rest.matches("(?s)^\\s?번째.*")) out = n == 1 ? "첫 " : nativeNum(n) + " ";
                else if (rest.matches("(?s)^\\s?달(?!러).*") && (n == 3 || n == 4)) out = n == 3 ? "석 " : "넉 ";
                else if (NATIVE.matcher(rest).find() && !(n > 20 && rest.matches("(?s)^\\s?시간.*")) && !(n > 10 && rest.matches("(?s)^\\s?배.*"))) out = nativeNum(n) + " ";
                else if (n % 10 != 0 && DAE.matcher(rest).find()) out = nativeNum(n) + " ";
                else if (n <= 12 && CLOCK.matcher(rest).find()) out = nativeNum(n) + " ";
            }
            if (out == null && bare && rest.startsWith("월") && (n == 6 || n == 10)) out = n == 6 ? "유" : "시";
            if (out == null && bare && n == 1 && rest.matches("(?s)^[십백천만].*")) out = "";
            if (out == null) {
                out = sino(digits);
                if (frac != null) {
                    StringBuilder f = new StringBuilder();
                    for (int i = 0; i < frac.length(); i++) f.append(DIG[frac.charAt(i) - '0']);
                    out += "쩜" + f;
                }
                if (minus != null) out = (unit != null && unit.matches(".*[℃°].*") ? "영하 " : "마이너스 ") + out;
                if ("km/h".equals(unit)) out = "시속 " + out;
                if (unit != null) out += " " + UNITS.get(unit);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(out));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String symbols(String s) {
        s = s.replaceAll("[“”\"‘’'`「」『』《》〈〉<>\\[\\]【】※\\u1100-\\u11ff]", "").replaceAll("[()]", ", ").replaceAll("…|\\.{2,}", ", ");
        s = s.replaceAll("[ \\t]*↑", " 상승").replaceAll("[ \\t]*↓", " 하락").replaceAll("[▲▼△▽▶▷►◀◁◆◇■□●○◎★☆→←↔⇒⇨▸▪]", ", ").replaceAll("\\b[vV][sS]\\b\\.?", " 대 ");
        s = s.replaceAll("[ \\t]*[|/·][ \\t]*", ", ").replace("&", " 앤 ").replace("%", " 퍼센트").replaceAll("[#*=^_~+@]", " ").replaceAll("(?<=\\S)[ \\t]*-[ \\t]*(?=\\S)", " ");
        s = s.replaceAll("[ \\t]*,(?:[ \\t]*,)*[ \\t]*", ", ").replaceAll(",[ \\t]*([.?!])", "$1").replaceAll("[ \\t]{2,}", " ").replaceAll("(?m)^[ \\t,]+|[ \\t,]+$", "");
        return s;
    }
}
