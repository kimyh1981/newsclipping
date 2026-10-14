package io.github.kimyh1981.newsclipping;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 해외농업 키워드로 검색할 나라: 국내총생산(GDP) 상위 100개국 (우리나라 뺌, 대략의 순위), 대륙별로 고른다.
 * 나라마다 키워드를 옮길 말(구글 번역 코드)과 구글 뉴스 나라판(hl·gl·ceid). 나라판이 없는 나라는 영어판에서 '키워드 + 나라 이름'으로 찾는다.
 */
final class Countries {
    private Countries() {}

    static final String[] CONTINENTS = {"아시아", "중동", "유럽", "북아메리카", "남아메리카", "아프리카", "오세아니아"};

    static final class Country {
        final String ko, en, continent, lang, gl, hl, ceid;
        Country(String[] r) { ko = r[0]; en = r[1]; continent = r[2]; lang = r[3]; gl = r[4]; hl = r[5]; ceid = r[6]; }
        boolean hasEdition() { return !gl.isEmpty(); }
        /** 키워드를 옮길 말: 나라판이 없으면 영어로 찾는다 */
        String searchLang() { return hasEdition() ? lang : "en"; }
    }

    /** 이름(우리말) · 영어 이름 · 대륙 · 말 · 구글 뉴스 gl · hl · ceid */
    private static final String[][] ROWS = {
            {"미국", "United States", "북아메리카", "en", "US", "en-US", "US:en"},
            {"중국", "China", "아시아", "zh-CN", "CN", "zh-CN", "CN:zh-Hans"},
            {"독일", "Germany", "유럽", "de", "DE", "de", "DE:de"},
            {"일본", "Japan", "아시아", "ja", "JP", "ja", "JP:ja"},
            {"인도", "India", "아시아", "en", "IN", "en-IN", "IN:en"},
            {"영국", "United Kingdom", "유럽", "en", "GB", "en-GB", "GB:en"},
            {"프랑스", "France", "유럽", "fr", "FR", "fr", "FR:fr"},
            {"이탈리아", "Italy", "유럽", "it", "IT", "it", "IT:it"},
            {"캐나다", "Canada", "북아메리카", "en", "CA", "en-CA", "CA:en"},
            {"브라질", "Brazil", "남아메리카", "pt", "BR", "pt-BR", "BR:pt-419"},
            {"러시아", "Russia", "유럽", "ru", "RU", "ru", "RU:ru"},
            {"스페인", "Spain", "유럽", "es", "ES", "es", "ES:es"},
            {"멕시코", "Mexico", "북아메리카", "es", "MX", "es-419", "MX:es-419"},
            {"호주", "Australia", "오세아니아", "en", "AU", "en-AU", "AU:en"},
            {"인도네시아", "Indonesia", "아시아", "id", "ID", "id", "ID:id"},
            {"튀르키예", "Turkey", "중동", "tr", "TR", "tr", "TR:tr"},
            {"네덜란드", "Netherlands", "유럽", "nl", "NL", "nl", "NL:nl"},
            {"사우디아라비아", "Saudi Arabia", "중동", "ar", "SA", "ar", "SA:ar"},
            {"스위스", "Switzerland", "유럽", "de", "CH", "de", "CH:de"},
            {"폴란드", "Poland", "유럽", "pl", "PL", "pl", "PL:pl"},
            {"대만", "Taiwan", "아시아", "zh-TW", "TW", "zh-TW", "TW:zh-Hant"},
            {"벨기에", "Belgium", "유럽", "fr", "BE", "fr", "BE:fr"},
            {"아르헨티나", "Argentina", "남아메리카", "es", "AR", "es-419", "AR:es-419"},
            {"스웨덴", "Sweden", "유럽", "sv", "SE", "sv", "SE:sv"},
            {"아일랜드", "Ireland", "유럽", "en", "IE", "en-IE", "IE:en"},
            {"이스라엘", "Israel", "중동", "iw", "IL", "he", "IL:he"},
            {"싱가포르", "Singapore", "아시아", "en", "SG", "en-SG", "SG:en"},
            {"아랍에미리트", "United Arab Emirates", "중동", "ar", "AE", "ar", "AE:ar"},
            {"태국", "Thailand", "아시아", "th", "TH", "th", "TH:th"},
            {"오스트리아", "Austria", "유럽", "de", "AT", "de", "AT:de"},
            {"노르웨이", "Norway", "유럽", "no", "NO", "no", "NO:no"},
            {"필리핀", "Philippines", "아시아", "en", "PH", "en-PH", "PH:en"},
            {"베트남", "Vietnam", "아시아", "vi", "VN", "vi", "VN:vi"},
            {"방글라데시", "Bangladesh", "아시아", "bn", "BD", "bn", "BD:bn"},
            {"덴마크", "Denmark", "유럽", "da", "", "", ""},
            {"말레이시아", "Malaysia", "아시아", "en", "MY", "en-MY", "MY:en"},
            {"콜롬비아", "Colombia", "남아메리카", "es", "CO", "es-419", "CO:es-419"},
            {"홍콩", "Hong Kong", "아시아", "zh-TW", "HK", "zh-HK", "HK:zh-Hant"},
            {"남아프리카공화국", "South Africa", "아프리카", "en", "ZA", "en-ZA", "ZA:en"},
            {"루마니아", "Romania", "유럽", "ro", "RO", "ro", "RO:ro"},
            {"파키스탄", "Pakistan", "아시아", "en", "PK", "en-PK", "PK:en"},
            {"칠레", "Chile", "남아메리카", "es", "CL", "es-419", "CL:es-419"},
            {"체코", "Czechia", "유럽", "cs", "CZ", "cs", "CZ:cs"},
            {"이집트", "Egypt", "아프리카", "ar", "EG", "ar", "EG:ar"},
            {"이란", "Iran", "중동", "fa", "", "", ""},
            {"포르투갈", "Portugal", "유럽", "pt", "PT", "pt-PT", "PT:pt-150"},
            {"핀란드", "Finland", "유럽", "fi", "", "", ""},
            {"페루", "Peru", "남아메리카", "es", "PE", "es-419", "PE:es-419"},
            {"카자흐스탄", "Kazakhstan", "아시아", "ru", "", "", ""},
            {"이라크", "Iraq", "중동", "ar", "", "", ""},
            {"알제리", "Algeria", "아프리카", "fr", "", "", ""},
            {"그리스", "Greece", "유럽", "el", "GR", "el", "GR:el"},
            {"뉴질랜드", "New Zealand", "오세아니아", "en", "NZ", "en-NZ", "NZ:en"},
            {"헝가리", "Hungary", "유럽", "hu", "HU", "hu", "HU:hu"},
            {"카타르", "Qatar", "중동", "ar", "", "", ""},
            {"우크라이나", "Ukraine", "유럽", "uk", "UA", "uk", "UA:uk"},
            {"나이지리아", "Nigeria", "아프리카", "en", "NG", "en-NG", "NG:en"},
            {"쿠웨이트", "Kuwait", "중동", "ar", "", "", ""},
            {"모로코", "Morocco", "아프리카", "fr", "MA", "fr", "MA:fr"},
            {"슬로바키아", "Slovakia", "유럽", "sk", "SK", "sk", "SK:sk"},
            {"에콰도르", "Ecuador", "남아메리카", "es", "", "", ""},
            {"도미니카공화국", "Dominican Republic", "북아메리카", "es", "", "", ""},
            {"에티오피아", "Ethiopia", "아프리카", "en", "ET", "en", "ET:en"},
            {"케냐", "Kenya", "아프리카", "en", "KE", "en-KE", "KE:en"},
            {"우즈베키스탄", "Uzbekistan", "아시아", "ru", "", "", ""},
            {"과테말라", "Guatemala", "북아메리카", "es", "", "", ""},
            {"오만", "Oman", "중동", "ar", "", "", ""},
            {"불가리아", "Bulgaria", "유럽", "bg", "BG", "bg", "BG:bg"},
            {"앙골라", "Angola", "아프리카", "pt", "", "", ""},
            {"코스타리카", "Costa Rica", "북아메리카", "es", "", "", ""},
            {"룩셈부르크", "Luxembourg", "유럽", "fr", "", "", ""},
            {"스리랑카", "Sri Lanka", "아시아", "en", "", "", ""},
            {"파나마", "Panama", "북아메리카", "es", "", "", ""},
            {"크로아티아", "Croatia", "유럽", "hr", "", "", ""},
            {"우루과이", "Uruguay", "남아메리카", "es", "", "", ""},
            {"리투아니아", "Lithuania", "유럽", "lt", "LT", "lt", "LT:lt"},
            {"세르비아", "Serbia", "유럽", "sr", "RS", "sr", "RS:sr"},
            {"탄자니아", "Tanzania", "아프리카", "en", "TZ", "en-TZ", "TZ:en"},
            {"미얀마", "Myanmar", "아시아", "my", "", "", ""},
            {"코트디부아르", "Ivory Coast", "아프리카", "fr", "", "", ""},
            {"가나", "Ghana", "아프리카", "en", "GH", "en-GH", "GH:en"},
            {"아제르바이잔", "Azerbaijan", "아시아", "az", "", "", ""},
            {"슬로베니아", "Slovenia", "유럽", "sl", "SI", "sl", "SI:sl"},
            {"벨라루스", "Belarus", "유럽", "ru", "", "", ""},
            {"콩고민주공화국", "DR Congo", "아프리카", "fr", "", "", ""},
            {"투르크메니스탄", "Turkmenistan", "아시아", "ru", "", "", ""},
            {"요르단", "Jordan", "중동", "ar", "", "", ""},
            {"튀니지", "Tunisia", "아프리카", "fr", "", "", ""},
            {"카메룬", "Cameroon", "아프리카", "fr", "", "", ""},
            {"우간다", "Uganda", "아프리카", "en", "UG", "en-UG", "UG:en"},
            {"볼리비아", "Bolivia", "남아메리카", "es", "", "", ""},
            {"바레인", "Bahrain", "중동", "ar", "", "", ""},
            {"라트비아", "Latvia", "유럽", "lv", "LV", "lv", "LV:lv"},
            {"파라과이", "Paraguay", "남아메리카", "es", "", "", ""},
            {"에스토니아", "Estonia", "유럽", "et", "", "", ""},
            {"네팔", "Nepal", "아시아", "ne", "", "", ""},
            {"캄보디아", "Cambodia", "아시아", "km", "", "", ""},
            {"짐바브웨", "Zimbabwe", "아프리카", "en", "ZW", "en-ZW", "ZW:en"},
            {"잠비아", "Zambia", "아프리카", "en", "", "", ""},
            {"온두라스", "Honduras", "북아메리카", "es", "", "", ""},
    };

    static final List<Country> ALL = new ArrayList<>();
    static { for (String[] r : ROWS) ALL.add(new Country(r)); }

    static Country byName(String ko) {
        for (Country c : ALL) if (c.ko.equals(ko)) return c;
        return null;
    }

    /** 대륙별로 (CONTINENTS 순서, 대륙 안은 GDP 순) */
    static Map<String, List<Country>> byContinent() {
        Map<String, List<Country>> out = new LinkedHashMap<>();
        for (String k : CONTINENTS) out.put(k, new ArrayList<>());
        for (Country c : ALL) out.get(c.continent).add(c);
        return out;
    }

    /** 그 나라 구글 뉴스 검색 주소. word는 이미 그 나라 말(나라판이 없으면 영어)로 옮긴 키워드 */
    static String searchUrl(Country c, String word, String when) {
        try {
            String q = "\"" + word + "\"" + (c.hasEdition() ? "" : " \"" + c.en + "\"") + " when:" + when;
            String hl = c.hasEdition() ? c.hl : "en-US", gl = c.hasEdition() ? c.gl : "US", ceid = c.hasEdition() ? c.ceid : "US:en";
            return "https://news.google.com/rss/search?q=" + URLEncoder.encode(q, "UTF-8") + "&hl=" + hl + "&gl=" + gl + "&ceid=" + ceid;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
