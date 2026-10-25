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
    // 최근 10년 신문·방송에 자주 나온 영문 약어·이름·단위 (js/pron.js와 같은 사전)
    // <사전: data/pron-dict.json에서 tools/pron-dict.js가 만든다. 손으로 고치지 말 것>
    private static final String[] DICT_WORDS_SRC = {
        "Champions League\t챔피언스리그\nDutch Fertilizer\t더치 퍼틸라이저\nKyiv Independent\t키이우 인디펜던트\nGlobalFoundries\t글로벌파운드리스\nMassey Ferguson\t매시 퍼거슨\nKVK-Sepahijala\t케이브이케이 세파히잘라\nPremier League\t프리미어리그\nSilicon Valley\t실리콘밸리\nAgriSolutions\t애그리솔루션스\nArcelorMittal\t아르셀로미탈\nBankman-Fried\t뱅크먼프리드\nBiostimulants\t바이오스티뮬런트\nBreakingviews\t브레이킹뷰스\nDSM-Firmenich\t디에스엠 피르메니히\nDruckenmiller\t드러켄밀러\nLouis Dreyfus\t루이 드레퓌스\nMercedes-Benz\t메르세데스벤츠\nMicroStrategy\t마이크로스트래티지\nbiostimulants\t바이오스티뮬런트\nvon der Leyen\t폰데어라이엔\nAbhiyan-Rabi\t아비얀 라비\nAgribusiness\t애그리비즈니스\nAgricultural\t애그리컬처럴\nBiostimulant\t바이오스티뮬런트\nCharacter.AI\t캐릭터에이아이\nGLOBALG.A.P.\t글로벌갭\nGlobalG.A.P.\t글로벌갭\nGolden Globe\t골든글로브\nHugging Face\t허깅페이스\nNippon Steel\t일본제철\nNovo Nordisk\t노보노디스크\nPetroVietnam\t페트로베트남\nSaudi Arabia\t사우디아라비아\nTechnologies\t테크놀로지스\nTokio Marine\t도쿄해상\nWestinghouse\t웨스팅하우스\nWorld Series\t월드시리즈\nbiostimulant\t바이오스티뮬런트\nAgriculture\t애그리컬처\nAntigravity\t안티그래비티\nAstraZeneca\t아스트라제네카\nBABYMONSTER\t베이비몬스터\nBelaruskali\t벨라루스칼리\nBeyond Meat\t비욘드미트\nBlue Origin\t블루오리진\nBridgestone\t브리지스톤\nBridgewater\t브리지워터\nBurevestnik\t부레베스트니크\nCapital One\t캐피털원\nCaterpillar\t캐터필러\nChina Daily\t차이나데일리\nCorporation\t코퍼레이션\nElectronics\t일렉트로닉스\nFERTILIZERS\t퍼틸라이저스\nFertilisers\t퍼틸라이저스\nFertilizers\t퍼틸라이저스\nGolden Dome\t골든돔\nGroundswell\t그라운드스웰\nHo Chi Minh\t호찌민\nHyperliquid\t하이퍼리퀴드\nKim Jong Un\t김정은\nKim Jong-un\t김정은\nKim Yo Jong\t김여정\nKim Yo-jong\t김여정\nKraft Heinz\t크래프트 하인즈\nLE SSERAFIM\t르세라핌\nLamborghini\t람보르기니\nNetherlands\t네덜란드\nNew Holland\t뉴홀랜드\nPaetongtarn\t패통탄\nParalympics\t패럴림픽\nPhilippines\t필리핀\nPlayStation\t플레이스테이션\nReal Madrid\t레알 마드리드\nSuper Micro\t슈퍼마이크로\nTrichoderma\t트리코더마\nWall Street\t월스트리트\nWhite House\t백악관\nXiaohongshu\t샤오훙수\nAl Jazeera\t알자지라\nAliExpress\t알리익스프레스\nBangladesh\t방글라데시\nBlackstone\t블랙스톤\nBundesliga\t분데스리가\nCalifornia\t캘리포니아\nCloudFlare\t클라우드플레어\nCloudflare\t클라우드플레어\nCoromandel\t코로만델\nCybertruck\t사이버트럭\nDatabricks\t데이터브릭스\nElevenLabs\t일레븐랩스\nElevenlabs\t일레븐랩스\nEnza Zaden\t엔자 자덴\nExxonMobil\t엑손모빌\nFertiglobe\t퍼티글로브\nFertilizer\t퍼틸라이저\nGreenpeace\t그린피스\nHyperCLOVA\t하이퍼클로바\nIvy League\t아이비리그\nJohn Deere\t존디어\nMastercard\t마스터카드\nMcDonald's\t맥도날드\nMidjourney\t미드저니\nNew Mexico\t뉴멕시코\nNotebookLM\t노트북엘엠\nPerplexity\t퍼플렉시티\nPezeshkian\t페제시키안\nPhosphates\t포스페이츠\nPrudential\t푸르덴셜\nRebellions\t리벨리온\nRijk Zwaan\t레이크 즈반\nRocket Lab\t로켓랩\nSalesforce\t세일즈포스\nServiceNow\t서비스나우\nSharkNinja\t샤크닌자\nSkullcandy\t스컬캔디\nSmithfield\t스미스필드\nSnapdragon\t스냅드래곤\nSquid Game\t오징어 게임\nStablecoin\t스테이블코인\nStarshield\t스타실드\nStellantis\t스텔란티스\nStray Kids\t스트레이 키즈\nSuper Bowl\t슈퍼볼\nSupermicro\t슈퍼마이크로\nTechCrunch\t테크크런치\nTerraPower\t테라파워\nVolkswagen\t폭스바겐\nWashington\t워싱턴\nXi Jinping\t시진핑\nZuckerberg\t저커버그\nstablecoin\t스테이블코인\nAbenomics\t아베노믹스\nAccenture\t액센츄어\nAjinomoto\t아지노모토\nAlphaFold\t알파폴드\nAnthropic\t앤트로픽\nArgentina\t아르헨티나\nAustralia\t호주\nAvalanche\t아발란체\nBLACKPINK\t블랙핑크\nBarcelona\t바르셀로나\nBerkshire\t버크셔\nBillboard\t빌보드\nBlackRock\t블랙록\nBlackpink\t블랙핑크\nBlackrock\t블랙록\nBlackwell\t블랙웰\nBloomberg\t블룸버그\nBluetooth\t블루투스",
        "ByteDance\t바이트댄스\nCambridge\t케임브리지\nCavendish\t캐번디시\nCelltrion\t셀트리온\nChainlink\t체인링크\nChangpeng\t창펑\nCitigroup\t씨티그룹\nCoca-Cola\t코카콜라\nCoreWeave\t코어위브\nEconomist\t이코노미스트\nEurogroup\t유로그룹\nFairtrade\t페어트레이드\nFuriosaAI\t퓨리오사에이아이\nGLOBALGAP\t글로벌갭\nGelsinger\t겔싱어\nGeorgieva\t게오르기에바\nGlobalGAP\t글로벌갭\nGreenland\t그린란드\nHang Seng\t항셍\nHezbollah\t헤즈볼라\nHong Kong\t홍콩\nINDOPACOM\t인도페이콤\nIndonesia\t인도네시아\nInstacart\t인스타카트\nInstagram\t인스타그램\nIron Dome\t아이언돔\nJerusalem\t예루살렘\nK-content\t케이콘텐츠\nK-culture\t케이컬처\nKOSDAQ150\t코스닥백오십\nKasetsart\t까셋삿\nKellanova\t켈라노바\nLimagrain\t리마그레인\nLiverpool\t리버풀\nLululemon\t룰루레몬\nMasayoshi\t마사요시\nMicrosoft\t마이크로소프트\nNASDAQ100\t나스닥백\nNIKKEI225\t닛케이이백이십오\nNasdaq100\t나스닥백\nNetanyahu\t네타냐후\nNeuralink\t뉴럴링크\nNew Delhi\t뉴델리\nNew Glenn\t뉴글렌\nNew START\t뉴스타트\nNikkei225\t닛케이이백이십오\nNovonesis\t노보네시스\nNovozymes\t노보자임스\nObamacare\t오바마케어\nPanasonic\t파나소닉\nParamount\t파라마운트\nPertamina\t페르타미나\nPinduoduo\t핀둬둬\nPinterest\t핀터레스트\nPivot Bio\t피벗바이오\nPrinceton\t프린스턴\nPyongyang\t평양\nRamaphosa\t라마포사\nRamaswamy\t라마스와미\nRobinhood\t로빈후드\nRoscosmos\t로스코스모스\nSEVENTEEN\t세븐틴\nSheinbaum\t셰인바움\nSingapore\t싱가포르\nSnowflake\t스노플레이크\nStarbucks\t스타벅스\nSutskever\t수츠케버\nSynaptics\t시냅틱스\nTottenham\t토트넘\nVenezuela\t베네수엘라\nVnExpress\t브이엔익스프레스\nWikipedia\t위키피디아\nWimbledon\t윔블던\nWorldcoin\t월드코인\nZelenskyy\t젤렌스키\nAbhiyaan\t아비얀\nAfrikelp\t아프리켈프\nAgriFood\t애그리푸드\nAgriTech\t애그리테크\nAgritech\t애그리테크\nAgrotech\t애그로테크\nAirlines\t에어라인스\nAl-Qaeda\t알카에다\nAlbanese\t앨버니지\nBacillus\t바실러스\nBarclays\t바클레이스\nBarron's\t배런스\nBessemer\t베세머\nBioPrime\t바이오프라임\nBioworks\t바이오웍스\nBroadcom\t브로드컴\nBrussels\t브뤼셀\nCambodia\t캄보디아\nCerebras\t세레브라스\nCoinbase\t코인베이스\nColorado\t콜로라도\nColumbia\t컬럼비아\nCovid-19\t코비드 일구\nDeSantis\t디샌티스\nDeepMind\t딥마인드\nDeepSeek\t딥시크\nDeepmind\t딥마인드\nDeepseek\t딥시크\nDeloitte\t딜로이트\nDeutsche\t도이체\nDogecoin\t도지코인\nDoorDash\t도어대시\nEricsson\t에릭슨\nEthereum\t이더리움\nEuroChem\t유로켐\nEuronext\t유로넥스트\nEurostat\t유로스타트\nEurozone\t유로존\nFacebook\t페이스북\nFidelity\t피델리티\nFonterra\t폰테라\nFoodTech\t푸드테크\nFoodtech\t푸드테크\nGlencore\t글렌코어\nGoolsbee\t굴스비\nGopinath\t고피나스\nGuardian\t가디언\nGuterres\t구테흐스\nHassabis\t허사비스\nHathaway\t해서웨이\nHoldings\t홀딩스\nHolstein\t홀스타인\nIBOVESPA\t보베스파\nICAR-KVK\t아이씨에이알 케이브이케이\nINTERPOL\t인터폴\nInterpol\t인터폴\nJPMorgan\t제이피모건\nJen-Hsun\t젠슨\nK-beauty\t케이뷰티\nKOSPI200\t코스피이백\nKashkari\t카시카리\nKeytruda\t키트루다\nKhamenei\t하메네이\nKiyosaki\t기요사키\nKuaishou\t콰이쇼우\nLactalis\t락탈리스\nLe Monde\t르몽드\nLi Qiang\t리창\nLinkedIn\t링크드인\nLitecoin\t라이트코인\nLockheed\t록히드\nMERCOSUR\t메르코수르\nMahindra\t마힌드라\nMainichi\t마이니치\nMalaysia\t말레이시아",
        "McDonald\t맥도날드\nMcKinsey\t맥킨지\nMedicaid\t메디케이드\nMedicare\t메디케어\nMedvedev\t메드베데프\nMercedes\t메르세데스\nMercosur\t메르코수르\nMichelin\t미쉐린\nMondelez\t몬델리즈\nMonsanto\t몬산토\nMoonshot\t문샷\nMounjaro\t마운자로\nNakamoto\t나카모토\nNeocloud\t네오클라우드\nNet-Zero\t넷제로\nNew York\t뉴욕\nNewJeans\t뉴진스\nNewsweek\t뉴스위크\nNintendo\t닌텐도\nNorthrop\t노스롭\nNovartis\t노바티스\nOlympics\t올림픽\nOreshnik\t오레시니크\nPM-KISAN\t피엠키산\nPM-Kisan\t피엠키산\nPakistan\t파키스탄\nPalantir\t팔란티어\nParadeep\t파라딥\nPaxlovid\t팍스로비드\nPentagon\t펜타곤\nPershing\t퍼싱\nPetronas\t페트로나스\nPhosAgro\t포스아그로\nPolestar\t폴스타\nPolitico\t폴리티코\nPolkadot\t폴카닷\nQualcomm\t퀄컴\nRaytheon\t레이시온\nReliance\t릴라이언스\nRobotaxi\t로보택시\nRoullier\t룰리에\nSKUAST-K\t스쿠아스트 케이\nSYNGENTA\t신젠타\nSarandos\t서랜도스\nScotland\t스코틀랜드\nSeedance\t시댄스\nShandong\t산둥\nShanghai\t상하이\nShenzhou\t선저우\nSnapchat\t스냅챗\nSoftBank\t소프트뱅크\nSoftbank\t소프트뱅크\nSolidigm\t솔리다임\nStanford\t스탠퍼드\nStarlink\t스타링크\nStarship\t스타십\nSuleyman\t술레이만\nSyngenta\t신젠타\nTakaichi\t다카이치\nTelegram\t텔레그램\nThailand\t태국\nTiangong\t톈궁\nTomahawk\t토마호크\nTuoi Tre\t뚜오이째\nUnilever\t유니레버\nUralkali\t우랄칼리\nVN-Index\t브이엔지수\nVanguard\t뱅가드\nVentures\t벤처스\nViet Nam\t베트남\nWhatsApp\t왓츠앱\nZelensky\t젤렌스키\nZepbound\t젭바운드\nnet-zero\t넷제로\nAFRICOM\t아프리콤\nAPR1400\t에이피알천사백\nARIRANG\t아리랑\nAbhiyan\t아비얀\nAcadian\t아카디안\nAirPods\t에어팟\nAlibaba\t알리바바\nAllianz\t알리안츠\nAlphaGo\t알파고\nAmerica\t아메리카\nAndroid\t안드로이드\nApplied\t어플라이드\nArabica\t아라비카\nArnault\t아르노\nArsenal\t아스널\nArtemis\t아르테미스\nBangkok\t방콕\nBeijing\t베이징\nBenioff\t베니오프\nBessent\t베선트\nBinance\t바이낸스\nBiobest\t바이오베스트\nBiochar\t바이오차\nBitcoin\t비트코인\nBithumb\t빗썸\nBovespa\t보베스파\nBritain\t영국\nBuffett\t버핏\nCARICOM\t카리콤\nCENTCOM\t센트콤\nCapital\t캐피털\nCardano\t카르다노\nCargill\t카길\nCarlyle\t칼라일\nCartier\t까르띠에\nCase IH\t케이스 아이에이치\nChambal\t참발\nChelsea\t첼시\nChevron\t셰브론\nChouhan\t차우한\nCitadel\t시타델\nCoinone\t코인원\nComcast\t컴캐스트\nCopilot\t코파일럿\nCorteva\t코르테바\nCoupang\t쿠팡\nDaimler\t다임러\nDeckers\t데커스\nDiscord\t디스코드\nDisney+\t디즈니플러스\nDodgers\t다저스\nDonbass\t돈바스\nDropbox\t드롭박스\nENHYPEN\t엔하이픈\nEURATOM\t유라톰\nEUROPOL\t유로폴\nEl Nino\t엘니뇨\nEl Niño\t엘니뇨\nEllison\t엘리슨\nEmbraer\t엠브라에르\nEmbrapa\t엠브라파\nEngland\t잉글랜드\nEpstein\t엡스타인\nErdogan\t에르도안\nErdoğan\t에르도안\nEuropol\t유로폴\nFerrari\t페라리\nFortune\t포천\nFoxconn\t폭스콘\nFreddie\t프레디\nFrontex\t프론텍스\nFuriosa\t퓨리오사\nGLONASS\t글로나스\nGabbard\t개버드\nGartner\t가트너\nGazprom\t가스프롬",
        "General\t제너럴\nGenesis\t제네시스\nGermany\t독일\nGoldman\t골드만\nGrammys\t그래미\nGrumman\t그러먼\nGuizhou\t구이저우\nHankook\t한국\nHarvard\t하버드\nHaryana\t하리아나\nHassett\t해싯\nHegseth\t헤그세스\nHershey\t허쉬\nHitachi\t히타치\nHouthis\t후티\nHugging\t허깅\nHwasong\t화성\nHyundai\t현대\nICRISAT\t이크리샛\nIberico\t이베리코\nInfosys\t인포시스\nInvesco\t인베스코\nJakarta\t자카르타\nJavelin\t재블린\nK-drama\t케이드라마\nKATSEYE\t캣츠아이\nKadyrov\t카디로프\nKellogg\t켈로그\nKennedy\t케네디\nKinzhal\t킨잘\nKishida\t기시다\nKomatsu\t고마쓰\nKoppert\t코퍼트\nKremlin\t크렘린\nKrugman\t크루그먼\nLPDDR5X\t엘피디디알파이브엑스\nLa Nina\t라니냐\nLa Niña\t라니냐\nLagarde\t라가르드\nLeavitt\t레빗\nLeqembi\t레켐비\nLutnick\t러트닉\nMANPADS\t맨패즈\nMa'aden\t마덴\nMacBook\t맥북\nMachado\t마차도\nMamdani\t맘다니\nMarvell\t마벨\nMasters\t마스터스\nMeituan\t메이퇀\nMetLife\t메트라이프\nMiniMax\t미니맥스\nMistral\t미스트랄\nModerna\t모더나\nMoody's\t무디스\nMustafa\t무스타파\nMyanmar\t미얀마\nNadella\t나델라\nNavalny\t나발니\nNavarro\t나바로\nNetZero\t넷제로\nNetflix\t넷플릭스\nNigeria\t나이지리아\nNon-GMO\t논 지엠오\nNordisk\t노디스크\nNuScale\t뉴스케일\nNutrien\t뉴트리엔\nOmicron\t오미크론\nOptimus\t옵티머스\nOzempic\t오젬픽\nPatriot\t패트리엇\nPepsiCo\t펩시코\nPhilips\t필립스\nPolygon\t폴리곤\nPony.ai\t포니에이아이\nPorsche\t포르쉐\nPrabowo\t프라보워\nRakuten\t라쿠텐\nRapidus\t라피더스\nRenault\t르노\nReuters\t로이터\nRevolut\t레볼루트\nRobusta\t로부스타\nRonaldo\t호날두\nRosatom\t로사톰\nRosneft\t로스네프트\nRoubini\t루비니\nRoundup\t라운드업\nSamsung\t삼성\nSanDisk\t샌디스크\nSandisk\t샌디스크\nSankalp\t산칼프\nSchmidt\t슈미트\nSchumer\t슈머\nSemafor\t세마포\nSephora\t세포라\nSequoia\t세쿼이아\nSerie A\t세리에 아\nShivraj\t시브라지\nShopify\t쇼피파이\nSiemens\t지멘스\nSpotify\t스포티파이\nSputnik\t스푸트니크\nStarmer\t스타머\nSummers\t서머스\nTaliban\t탈레반\nTamiflu\t타미플루\nTencent\t텐센트\nThaiGAP\t타이갭\nTiffany\t티파니\nToshiba\t도시바\nTrudeau\t트뤼도\nTürkiye\t튀르키예\nUS Open\t유에스오픈\nUkraine\t우크라이나\nUnitree\t유니트리\nUpstage\t업스테이지\nVNINDEX\t브이엔지수\nValagro\t발라그로\nVariety\t버라이어티\nVietGAP\t비엣갭\nVietnam\t베트남\nVinFast\t빈패스트\nViterra\t비테라\nVuitton\t비통\nWalmart\t월마트\nWang Yi\t왕이\nWindows\t윈도\nWitkoff\t위트코프\nWorkday\t워크데이\nWozniak\t워즈니악\nYankees\t양키스\nYomiuri\t요미우리\nYouTube\t유튜브\nYoutube\t유튜브\narabica\t아라비카\nbiochar\t바이오차\nbitcoin\t비트코인\niShares\t아이셰어즈\nnon-GMO\t논 지엠오\nrobusta\t로부스타\nATACMS\t에이태큼스\nAbbott\t애보트\nAckman\t애크먼\nAdidas\t아디다스\nAfrica\t아프리카\nAgTech\t애그테크\nAgriSA\t애그리에스에이\nAgtech\t애그테크\nAirbnb\t에어비앤비",
        "Airbus\t에어버스\nAltman\t올트먼\nAmazon\t아마존\nAmodei\t아모데이\nAnutin\t아누틴\nApollo\t아폴로\nAramco\t아람코\nBRANDT\t브랜트\nBayern\t바이에른\nBerlin\t베를린\nBoeing\t보잉\nBostic\t보스틱\nBowman\t보먼\nBrazil\t브라질\nBrexit\t브렉시트\nC.Vale\t세발레\nCIMMYT\t심밋\nCRISPR\t크리스퍼\nCaixin\t차이신\nCanada\t캐나다\nCannes\t칸\nCarney\t카니\nCathie\t캐시\nChanel\t샤넬\nCircle\t서클\nClaude\t클로드\nCostco\t코스트코\nCramer\t크레이머\nCrimea\t크림반도\nCursor\t커서\nD-Wave\t디웨이브\nDALL-E\t달리\nDEFCON\t데프콘\nDanone\t다논\nDarfur\t다르푸르\nDisney\t디즈니\nDonald\t도널드\nDonbas\t돈바스\nDoosan\t두산\nDorsey\t도시\nDuPont\t듀폰\nEBITDA\t에비타\nECOWAS\t에코와스\nEXAONE\t엑사원\nEagles\t이글스\nEurope\t유럽\nEvonik\t에보닉\nExaone\t엑사원\nExynos\t엑시노스\nFalcon\t팰컨\nFannie\t패니\nFarage\t패라지\nForbes\t포브스\nFrance\t프랑스\nFulvic\t풀빅\nGPT-4o\t지피티포오\nGSOMIA\t지소미아\nGalaxy\t갤럭시\nGallup\t갤럽\nGemini\t제미나이\nGeneva\t제네바\nGiants\t자이언츠\nGilead\t길리어드\nGitHub\t깃허브\nGoogle\t구글\nGrammy\t그래미\nGrossi\t그로시\nHANARO\t하나로\nHIMARS\t하이마스\nHanwha\t한화\nHanwoo\t한우\nHarris\t해리스\nHermes\t에르메스\nHermès\t에르메스\nHinton\t힌턴\nHopper\t호퍼\nHouthi\t후티\nHuawei\t화웨이\nHubble\t허블\nIshiba\t이시바\nIsrael\t이스라엘\nJaguar\t재규어\nJensen\t젠슨\nJerome\t제롬\nK-FOOD\t케이푸드\nK-Food\t케이푸드\nK-food\t케이푸드\nKRX300\t케이알엑스삼백\nKUBOTA\t구보타\nKanana\t카나나\nKelpak\t켈팍\nKharif\t카리프\nKioxia\t키옥시아\nKlarna\t클라르나\nKorbit\t코빗\nKraken\t크라켄\nKrishi\t크리시\nKubota\t구보타\nKugler\t쿠글러\nKuiper\t카이퍼\nKuroda\t구로다\nLGBTQ+\t엘지비티큐플러스\nLPDDR5\t엘피디디알파이브\nLPDDR6\t엘피디디알식스\nLaLiga\t라리가\nLabubu\t라부부\nLavrov\t라브로프\nLe Pen\t르펜\nLeBron\t르브론\nLenovo\t레노버\nLondon\t런던\nLuckin\t루이싱\nMaaden\t마덴\nMacron\t마크롱\nMaduro\t마두로\nManila\t마닐라\nMarcos\t마르코스\nMartin\t마틴\nMekong\t메콩\nMeloni\t멜로니\nMexico\t멕시코\nMiG-29\t미그이십구\nMicron\t마이크론\nMizuho\t미즈호\nMorgan\t모건\nMosaic\t모자이크\nMoscow\t모스크바\nMotoGP\t모토지피\nMotors\t모터스\nMoutai\t마오타이\nMumbai\t뭄바이\nMurati\t무라티\nNASCAR\t나스카\nNIKKEI\t닛케이\nNapoli\t나폴리\nNestle\t네슬레\nNestlé\t네슬레\nNewsom\t뉴섬\nNikkei\t닛케이\nNippon\t닛폰\nNissan\t닛산\nNomura\t노무라\nNscale\t엔스케일\nNufarm\t누팜\nNvidia\t엔비디아\nOdisha\t오디샤\nOhtani\t오타니\nOneWeb\t원웹\nOracle\t오라클\nOscars\t오스카",
        "Oxford\t옥스퍼드\nPadres\t파드리스\nPayPal\t페이팔\nPayPay\t페이페이\nPelosi\t펠로시\nPeskov\t페스코프\nPfizer\t화이자\nPichai\t피차이\nPowell\t파월\nPunjab\t펀자브\nRIMPAC\t림팩\nReddit\t레딧\nRipple\t리플\nRivian\t리비안\nRussia\t러시아\nS&P500\t에스앤피오백\nSENSEX\t센섹스\nSKUAST\t스쿠아스트\nSaigon\t사이공\nSakata\t사카타\nSanofi\t사노피\nSarmat\t사르마트\nSaylor\t세일러\nScholz\t숄츠\nSchwab\t슈왑\nSensex\t센섹스\nShahed\t샤헤드\nSinwar\t신와르\nSolana\t솔라나\nSonnet\t소네트\nSpaceX\t스페이스엑스\nStripe\t스트라이프\nSubaru\t스바루\nSundar\t순다르\nSuzuki\t스즈키\nTaipei\t타이베이\nTaiwan\t대만\nTarget\t타깃\nTaurus\t타우러스\nTedros\t테드로스\nTehran\t테헤란\nTether\t테더\nTikTok\t틱톡\nTiktok\t틱톡\nTinubu\t티누부\nTo Lam\t또 럼\nToyota\t도요타\nTurkey\t튀르키예\nTô Lâm\t또 럼\nUNAIDS\t유엔에이즈\nUNFCCC\t유엔에프씨씨씨\nUNIFIL\t유니필\nUniqlo\t유니클로\nUnited\t유나이티드\nV-NAND\t브이낸드\nViksit\t빅싯\nWagner\t바그너\nWaller\t월러\nWarner\t워너\nWarren\t워런\nWegovy\t위고비\nWilmar\t윌마\nXiaomi\t샤오미\nXinhua\t신화\nYANMAR\t얀마\nYanmar\t얀마\nYellen\t옐런\nYonhap\t연합\nYouGov\t유고브\nfulvic\t풀빅\niPhone\t아이폰\nADAMA\t아다마\nAIPAC\t에이팩\nANZUS\t앤저스\nATEEZ\t에이티즈\nAWACS\t에이왁스\nAbbas\t아바스\nAcron\t아크론\nAdobe\t어도비\nAlexa\t알렉사\nAngus\t앵거스\nAnwar\t안와르\nApple\t애플\nAsahi\t아사히\nAxios\t악시오스\nBAYER\t바이엘\nBaidu\t바이두\nBanga\t방가\nBarra\t바라\nBayer\t바이엘\nBezos\t베이조스\nBiden\t바이든\nBixby\t빅스비\nBondi\t본디\nBosch\t보쉬\nBunge\t번기\nBurry\t버리\nBusan\t부산\nBybit\t바이비트\nCADIZ\t차디즈\nCAR-T\t카티\nCELAC\t셀락\nCF100\t씨에프백\nCHIPS\t칩스\nCLAAS\t클라스\nCODEX\t코덱스\nCOFCO\t코프코\nCOFIX\t코픽스\nCOMEX\t코멕스\nCONAB\t코나브\nCOP16\t콥십육\nCOP26\t콥이십육\nCOP27\t콥이십칠\nCOP28\t콥이십팔\nCOP29\t콥이십구\nCOP30\t콥삼십\nCOP31\t콥삼십일\nCOP32\t콥삼십이\nCOVAX\t코백스\nCanva\t캔바\nChase\t체이스\nChery\t체리\nChina\t중국\nCisco\t시스코\nClaas\t클라스\nCodex\t코덱스\nConab\t코나브\nCovid\t코비드\nDARPA\t다르파\nDINKs\t딩크\nDaiso\t다이소\nDalio\t달리오\nDario\t다리오\nDavos\t다보스\nDeepX\t딥엑스\nDeere\t디어\nDelhi\t델리\nDemis\t데미스\nDepot\t디포\nDimon\t다이먼\nDutch\t네덜란드\nEbola\t에볼라\nEgypt\t이집트\nEmart\t이마트\nEmmys\t에미\nExxon\t엑손\nF-15K\t에프십오케이\nF-35A\t에프삼십오에이\nFA-50\t에프에이오십\nFAANG\t팡",
        "Fargo\t파고\nFendt\t펜트\nFigma\t피그마\nFitch\t피치\nGB200\t지비이백\nGB300\t지비삼백\nGDDR6\t지디디알식스\nGDDR7\t지디디알세븐\nGLP-1\t지엘피원\nGPT-4\t지피티포\nGPT-5\t지피티파이브\nGPT-6\t지피티식스\nGates\t게이츠\nGeely\t지리\nGemma\t젬마\nGen Z\t젠지\nGenAI\t젠에이아이\nGmail\t지메일\nGopax\t고팍스\nGreer\t그리어\nGroup\t그룹\nGucci\t구찌\nHACCP\t해썹\nHAMAS\t하마스\nHBM3E\t에이치비엠쓰리이\nHBM4E\t에이치비엠포이\nHaier\t하이얼\nHaifa\t하이파\nHaiku\t하이쿠\nHamas\t하마스\nHanoi\t하노이\nHonda\t혼다\nHuang\t황\nHumic\t휴믹\nHynix\t하이닉스\nICEYE\t아이스아이\nIELTS\t아이엘츠\nIFFCO\t이프코\nILLIT\t아일릿\nIRENA\t이레나\nIndia\t인도\nIntel\t인텔\nIpsos\t입소스\nIsaan\t이산\nItaly\t이탈리아\nJADIZ\t자디즈\nJamie\t제이미\nJapan\t일본\nJassy\t재시\nJones\t존스\nK-ETS\t케이 이티에스\nK-GAP\t케이갭\nK-Pop\t케이팝\nKADIZ\t카디즈\nKAMAZ\t카마즈\nKF-16\t케이에프십육\nKF-21\t케이에프이십일\nKODEX\t코덱스\nKOSEF\t코세프\nKSTAR\t케이스타\nKakao\t카카오\nKenya\t케냐\nKisan\t키산\nKling\t클링\nKorea\t코리아\nKurly\t컬리\nKursk\t쿠르스크\nKyodo\t교도\nL-SAM\t엘샘\nLASER\t레이저\nLIBOR\t리보\nLIDAR\t라이다\nLarry\t래리\nLeCun\t르쿤\nLexus\t렉서스\nLibor\t리보\nLidar\t라이다\nLilly\t릴리\nLlama\t라마\nLotte\t롯데\nLouis\t루이\nLucid\t루시드\nM-SAM\t엠샘\nMATIF\t마티프\nManus\t마누스\nMazda\t마쓰다\nMerck\t머크\nMessi\t메시\nMicro\t마이크로\nMidea\t메이디\nMilei\t밀레이\nMiran\t마이런\nMixue\t미쉐\nNIFTY\t니프티\nNK603\t엔케이육공삼\nNORAD\t노라드\nNYMEX\t나이멕스\nNaver\t네이버\nNifty\t니프티\nNokia\t노키아\nOatly\t오틀리\nObama\t오바마\nOrban\t오르반\nOrbán\t오르반\nOscar\t오스카\nPAC-2\t팩투\nPAC-3\t팩쓰리\nPM2.5\t피엠이쩜오\nParis\t파리\nPatel\t파텔\nPepsi\t펩시\nPosco\t포스코\nPrada\t프라다\nPupuk\t푸푹\nPutin\t푸틴\nQAFCO\t카프코\nQatar\t카타르\nRADAR\t레이더\nRE100\t알이백\nREDD+\t레드플러스\nRadar\t레이더\nRoche\t로슈\nRolex\t롤렉스\nRubin\t루빈\nRubio\t루비오\nRutte\t뤼터\nS-400\t에스사백\nSAARC\t사크\nSABIC\t사빅\nSIPRI\t시프리\nSONAR\t소나\nSTART\t스타트\nSTOXX\t스톡스\nSachs\t삭스\nSatya\t사티아\nSaudi\t사우디\nSeoul\t서울\nShein\t쉬인\nShell\t셸\nSlack\t슬랙\nSoros\t소로스\nSpain\t스페인\nSteel\t스틸\nSteve\t스티브\nStoxx\t스톡스\nSudan\t수단\nSuper\t슈퍼\nTIGER\t타이거\nTIMAC\t티막\nTOEFL\t토플",
        "TOEIC\t토익\nTOPIX\t토픽스\nTWICE\t트와이스\nTakii\t다키이\nTerra\t테라\nTesla\t테슬라\nTexas\t텍사스\nThiel\t틸\nTinto\t틴토\nTokio\t도쿄\nTokyo\t도쿄\nTopix\t토픽스\nTrump\t트럼프\nTyson\t타이슨\nUAN28\t유에이엔 이십팔\nUAN30\t유에이엔 삼십\nUAN32\t유에이엔 삼십이\nUNIDO\t유니도\nUSB-C\t유에스비씨\nUpbit\t업비트\nVance\t밴스\nVolvo\t볼보\nWASDE\t와스디\nWagyu\t와규\nWarsh\t워시\nWaymo\t웨이모\nWells\t웰스\nXPeng\t샤오펑\nXi'an\t시안\nXpeng\t샤오펑\nYIMBY\t임비\nYahoo\t야후\nZeekr\t지커\naespa\t에스파\neVTOL\t이브이톨\nhumic\t휴믹\nhynix\t하이닉스\ni-SMR\t아이에스엠알\nmacOS\t맥오에스\nA100\t에이백\nADIZ\t아디즈\nAESA\t에이사\nAGCO\t아그코\nAGRA\t아그라\nAGRI\t아그리\nAGRO\t아그로\nAIoT\t에이아이오티\nAMEX\t아멕스\nASEM\t아셈\nASIC\t에이식\nAgro\t아그로\nAmEx\t아멕스\nAmex\t아멕스\nArla\t알라\nAsia\t아시아\nB-1B\t비원비\nB-52\t비오십이\nB200\t비이백\nB300\t비삼백\nBASF\t바스프\nBali\t발리\nBank\t뱅크\nBenz\t벤츠\nBild\t빌트\nBill\t빌\nBose\t보스\nBrin\t브린\nCBAM\t씨밤\nCBOT\t시봇\nCOAT\t코트\nCUDA\t쿠다\nCiti\t씨티\nCook\t쿡\nCorp\t코프\nD.C.\t디씨\nDART\t다트\nDDR4\t디디알포\nDDR5\t디디알파이브\nDINK\t딩크\nDOGE\t도지\nDeFi\t디파이\nDefi\t디파이\nDell\t델\nDoge\t도지\nEBay\t이베이\nEFTA\t에프타\nENSO\t엔소\nElon\t일론\nEmmy\t에미\nEric\t에릭\nF-16\t에프십육\nF-22\t에프이십이\nF-35\t에프삼십오\nFANG\t팡\nFEMA\t피마\nFIBA\t피바\nFIRE\t파이어\nFLUX\t플럭스\nFTSE\t풋시\nFace\t페이스\nFink\t핑크\nFord\t포드\nFund\t펀드\nGMAT\t지맷\nGOAT\t고트\nGaza\t가자\nGenZ\t젠지\nGrab\t그랩\nGrok\t그록\nH100\t에이치백\nH1N1\t에이치일엔일\nH200\t에이치이백\nH3N2\t에이치삼엔이\nH5N1\t에이치오엔일\nH5N6\t에이치오엔육\nH5N8\t에이치오엔팔\nH7N9\t에이치칠엔구\nH9N2\t에이치구엔이\nHBM3\t에이치비엠쓰리\nHBM4\t에이치비엠포\nHang\t항\nHome\t홈\nHuma\t휴마\nICAO\t이카오\nISIS\t아이시스\nITER\t이터\nIger\t아이거\nIkea\t이케아\nIonQ\t아이온큐\nIran\t이란\nJAXA\t작사\nJDAM\t제이댐\nJGAP\t제이갭\nJack\t잭\nJeff\t제프\nJobs\t잡스\nKASA\t카사\nKIKO\t키코\nKOSA\t코사\nKOVO\t코보\nKiev\t키이우\nKimi\t키미\nKoch\t코크\nKyiv\t키이우\nLIFE\t라이프\nLUNA\t루나\nLaos\t라오스\nLego\t레고\nLula\t룰라\nLuna\t루나",
        "Lyft\t리프트\nMAGA\t마가\nMOOC\t무크\nMQ-9\t엠큐나인\nMark\t마크\nMerz\t메르츠\nMeta\t메타\nMets\t메츠\nModi\t모디\nMpox\t엠폭스\nMuse\t뮤즈\nMusk\t머스크\nNAMA\t나마\nNEIS\t나이스\nNICE\t나이스\nNOAA\t노아\nNike\t나이키\nNoem\t놈\nNovo\t노보\nOlam\t올람\nOpus\t오퍼스\nP2O5\t인산\nPISA\t피사\nPM10\t피엠십\nPadi\t파디\nQwen\t큐원\nRAND\t랜드\nRISE\t라이즈\nRQ-4\t알큐포\nRabi\t라비\nSAGA\t사가\nSBTi\t에스비티아이\nSM-3\t에스엠쓰리\nSM-6\t에스엠식스\nSOFA\t소파\nSOFR\t소파\nSPAM\t스팸\nSTEM\t스템\nSaaS\t사스\nSeng\t셍\nSiri\t시리\nSony\t소니\nSora\t소라\nSpam\t스팸\nT-50\t티오십\nTASS\t타스\nTEPS\t텝스\nTIME\t타임\nTemu\t테무\nToss\t토스\nTron\t트론\nUNEP\t유넵\nUSDA\t유에스디에이\nUSIM\t유심\nUber\t우버\nUeda\t우에다\nVale\t발레\nVera\t베라\nVisa\t비자\nWADA\t와다\nWeb3\t웹쓰리\nXBOX\t엑스박스\nXbox\t엑스박스\nYARA\t야라\nYale\t예일\nYara\t야라\nYelp\t옐프\nZara\t자라\nZika\t지카\nZoom\t줌\na16z\t에이식스틴지\naT센터\t에이티센터\ndLLM\t디엘엘엠\neBay\t이베이\neSIM\t이심\niMac\t아이맥\niPad\t아이패드\nmRNA\t엠알엔에이\nmpox\t엠폭스\nsLLM\t에스엘엘엠\nA.X\t에이닷엑스\nACE\t에이스\nArm\t암\nB-2\t비투\nBYD\t비야디\nCH4\t씨에이치포\nCO2\t씨오투\nCO₂\t씨오투\nD.C\t디씨\nDAX\t닥스\nDow\t다우\nEXO\t엑소\nFED\t연준\nFed\t연준\nH20\t에이치이십\nICE\t아이스\nIVE\t아이브\nInc\t인크\nIoT\t아이오티\nJio\t지오\nK+S\t케이플러스에스\nK2O\t케이투오\nKCl\t케이씨엘\nKia\t기아\nLIV\t리브\nLtd\t리미티드\nN2O\t아산화질소\nNH3\t암모니아\nNIO\t니오\nNO2\t이산화질소\nNOx\t녹스\nNio\t니오\nPIM\t핌\nPS5\t피에스파이브\nPS6\t피에스식스\nPSY\t싸이\nPhD\t피에이치디\nPwC\t피더블유씨\nSKY\t스카이\nSO2\t이산화황\nSOx\t속스\nSam\t샘\nSoC\t에스오씨\nTim\t팀\nVIX\t빅스\nVeo\t비오\nWSJ\t월스트리트저널\nWiz\t위즈\niOS\t아이오에스\niPS\t아이피에스\nsLM\t에스엘엠\nxAI\t엑스에이아이\nF1\t에프원\nK2\t케이투\nK9\t케이나인\nXi\t시진핑\naT\t에이티\npH\t피에이치",
    };
    /** 단위: 쓴 글, 숫자 뒤에서 읽는 말, 숫자 없이도 바꿀지("1"), 숫자 앞에 붙는 말(시속·리터당) */
    private static final String[] DICT_UNITS_SRC = {
        "bushels\t부셸\t0\t\nEFLOPS\t엑사플롭스\t0\t\nPFLOPS\t페타플롭스\t0\t\nTFLOPS\t테라플롭스\t0\t\nbushel\t부셸\t0\t\nkg/10a\t킬로그램\t0\t십 아르당\nkm/kWh\t킬로미터\t0\t킬로와트시당\ntCO2eq\t이산화탄소환산톤\t0\t\nBq/kg\t베크렐\t0\t킬로그램당\nCO2eq\t이산화탄소 환산\t0\t\nFLOPS\t플롭스\t1\t\nMMBTU\t백만 비티유\t0\t\nMMBtu\t백만 비티유\t1\t\nWh/kg\t와트시\t0\t킬로그램당\nacres\t에이커\t0\t\nbbl/d\t배럴\t0\t하루\nbu/ac\t부셸\t0\t에이커당\nkg/ha\t킬로그램\t0\t헥타르당\nkgf·m\t킬로그램포스미터\t0\t\nknots\t노트\t0\t\nmS/cm\t밀리지멘스퍼센티미터\t0\t\nmSv/h\t밀리시버트\t0\t시간당\nmg/dL\t밀리그램\t0\t데시리터당\nmg/kg\t밀리그램\t0\t킬로그램당\nmm/hr\t밀리미터\t0\t시간당\nmmBtu\t백만 비티유\t0\t\ntCO2e\t이산화탄소환산톤\t0\t\n°Brix\t브릭스\t0\t\nµSv/h\t마이크로시버트\t0\t시간당\nµg/m³\t마이크로그램\t0\t세제곱미터당\nμSv/h\t마이크로시버트\t0\t시간당\nμg/m3\t마이크로그램\t0\t세제곱미터당\nμg/m³\t마이크로그램\t0\t세제곱미터당\nBq/L\t베크렐\t0\t리터당\nBrix\t브릭스\t0\t\nGB/s\t기가바이트\t0\t초당\nGbps\t기가비피에스\t0\t\nKbps\t킬로비피에스\t0\t\nKcal\t킬로칼로리\t0\t\nMB/s\t메가바이트\t0\t초당\nMbps\t메가비피에스\t0\t\nMtoe\t백만 티오이\t0\t\nTB/s\t테라바이트\t0\t초당\nTbps\t테라비피에스\t0\t\nWh/L\t와트시\t0\t리터당\nacre\t에이커\t0\t\nbrix\t브릭스\t0\t\nbu/a\t부셸\t0\t에이커당\ndS/m\t데시지멘스퍼미터\t0\t\ng/km\t그램\t0\t킬로미터당\ninch\t인치\t0\t\nkbps\t킬로비피에스\t0\t\nkcal\t킬로칼로리\t1\t\nkg/a\t킬로그램\t0\t아르당\nkg·m\t킬로그램미터\t0\t\nkm/L\t킬로미터\t0\t리터당\nkm/s\t킬로미터\t0\t초속\nm3/s\t세제곱미터\t0\t초당\nmbar\t밀리바\t0\t\nmg/L\t밀리그램\t0\t리터당\nmg/l\t밀리그램\t0\t리터당\nmm/h\t밀리미터\t0\t시간당\nmmHg\t수은주밀리미터\t0\t\nm³/s\t세제곱미터\t0\t초당\nnits\t니트\t0\t\nsqft\t제곱피트\t0\t\nt/ha\t톤\t0\t헥타르당\ntons\t톤\t0\t\n㎍/m³\t마이크로그램\t0\t세제곱미터당\nBtu\t비티유\t1\t\nGHz\t기가헤르츠\t0\t\nGPa\t기가파스칼\t0\t\nGWp\t기가와트피크\t0\t\nKWh\t킬로와트시\t0\t\nKwh\t킬로와트시\t0\t\nMBq\t메가베크렐\t0\t\nMHz\t메가헤르츠\t0\t\nMPa\t메가파스칼\t0\t\nMVA\t메가볼트암페어\t0\t\nMWp\t메가와트피크\t0\t\nN·m\t뉴턴미터\t0\t\nTHz\t테라헤르츠\t0\t\nTon\t톤\t0\t\natm\t기압\t0\t\nb/d\t배럴\t0\t하루\nbar\t바\t0\t\nbbl\t배럴\t1\t\nbpd\t배럴\t0\t하루\nbps\t베이시스포인트\t0\t\ncal\t칼로리\t0\t\ncm2\t제곱센티미터\t0\t\ncm²\t제곱센티미터\t0\t\ncm³\t세제곱센티미터\t0\t\ndBA\t데시벨\t0\t\ndpi\t디피아이\t0\t\nfps\t에프피에스\t0\t\nft²\t제곱피트\t0\t\ng/L\t그램\t0\t리터당\ngal\t갤런\t0\t\nhPa\t헥토파스칼\t0\t\nhrs\t시간\t0\t\nkBq\t킬로베크렐\t0\t\nkHz\t킬로헤르츠\t0\t\nkPa\t킬로파스칼\t0\t\nkVA\t킬로볼트암페어\t0\t\nkWh\t킬로와트시\t1\t\nkWp\t킬로와트피크\t0\t\nkgf\t킬로그램포스\t0\t\nkwh\t킬로와트시\t0\t\nlbs\t파운드\t0\t\nmAh\t밀리암페어시\t0\t\nmSv\t밀리시버트\t0\t\nmcg\t마이크로그램\t0\t\nmin\t분\t0\t\nmm2\t제곱밀리미터\t0\t\nmm²\t제곱밀리미터\t0\t\nmph\t마일\t0\t시속\nnit\t니트\t0\t\nnmi\t해리\t0\t\nozt\t트로이온스\t0\t\nppb\t피피비\t0\t\nppi\t피피아이\t0\t\nppm\t피피엠\t1\t\nppt\t피피티\t0\t\npsi\t피에스아이\t0\t\nrpm\t알피엠\t0\t\nsec\t초\t0\t\ntoe\t티오이\t0\t\nton\t톤\t0\t\n°Bx\t브릭스\t0\t\nµSv\t마이크로시버트\t0\t\nμSv\t마이크로시버트\t0\t\n㎍/㎥\t마이크로그램\t0\t세제곱미터당\n㎥/s\t세제곱미터\t0\t초당\n%P\t퍼센트포인트\t0\t\nAh\t암페어시\t0\t\nBP\t베이시스포인트\t0\t\nBq\t베크렐\t0\t\nEB\t엑사바이트\t0\t\nGJ\t기가줄\t0\t\nGb\t기가비트\t0\t\nGt\t기가톤\t0\t\nGy\t그레이\t0\t\nHP\t마력\t0\t\nHz\t헤르츠\t0\t\nKM\t킬로미터\t0\t\nKW\t킬로와트\t0\t\nKg\t킬로그램\t0\t\nKm\t킬로미터\t0\t\nKw\t킬로와트\t0\t",
        "MJ\t메가줄\t0\t\nMM\t밀리미터\t0\t\nMT\t톤\t0\t\nMb\t메가비트\t0\t\nMt\t메가톤\t0\t\nNm\t뉴턴미터\t0\t\nPB\t페타바이트\t0\t\nPa\t파스칼\t0\t\nSv\t시버트\t0\t\nTW\t테라와트\t0\t\nTb\t테라비트\t0\t\nWh\t와트시\t0\t\nZB\t제타바이트\t0\t\nac\t에이커\t0\t\nbu\t부셸\t0\t\ncc\t시시\t0\t\ncm\t센티미터\t1\t\ndB\t데시벨\t0\t\ndL\t데시리터\t0\t\nft\t피트\t0\t\nha\t헥타르\t1\t\nhp\t마력\t0\t\nhr\t시간\t0\t\nkA\t킬로암페어\t0\t\nkB\t킬로바이트\t0\t\nkJ\t킬로줄\t0\t\nkL\t킬로리터\t0\t\nkN\t킬로뉴턴\t0\t\nkV\t킬로볼트\t0\t\nkg\t킬로그램\t1\t\nkm\t킬로미터\t1\t\nkΩ\t킬로옴\t0\t\nlb\t파운드\t0\t\nm3\t세제곱미터\t0\t\nmA\t밀리암페어\t0\t\nmV\t밀리볼트\t0\t\nmW\t밀리와트\t0\t\nmi\t마일\t0\t\nmm\t밀리미터\t1\t\nms\t밀리초\t0\t\nm³\t세제곱미터\t0\t\nng\t나노그램\t0\t\nnm\t나노미터\t0\t\nns\t나노초\t0\t\noz\t온스\t1\t\ntf\t톤포스\t0\t\nyd\t야드\t0\t\n°F\t도\t0\t화씨\nµg\t마이크로그램\t0\t\nµm\t마이크로미터\t0\t\nµs\t마이크로초\t0\t\nμL\t마이크로리터\t0\t\nμm\t마이크로미터\t0\t\nμs\t마이크로초\t0\t\nV\t볼트\t0\t\nW\t와트\t0\t\nÅ\t옹스트롬\t0\t\nΩ\t옴\t0\t\n℉\t도\t0\t화씨\n㎃\t밀리암페어\t0\t\n㎅\t킬로바이트\t0\t\n㎆\t메가바이트\t0\t\n㎇\t기가바이트\t0\t\n㎈\t칼로리\t0\t\n㎉\t킬로칼로리\t0\t\n㎎\t밀리그램\t0\t\n㎐\t헤르츠\t0\t\n㎑\t킬로헤르츠\t0\t\n㎒\t메가헤르츠\t0\t\n㎓\t기가헤르츠\t0\t\n㎖\t밀리리터\t0\t\n㎗\t데시리터\t0\t\n㎘\t킬로리터\t0\t\n㎚\t나노미터\t0\t\n㎛\t마이크로미터\t0\t\n㎟\t제곱밀리미터\t0\t\n㎠\t제곱센티미터\t0\t\n㎤\t세제곱센티미터\t0\t\n㎥\t세제곱미터\t0\t\n㎧\t미터\t0\t초속\n㎩\t파스칼\t0\t\n㎪\t킬로파스칼\t0\t\n㎫\t메가파스칼\t0\t\n㎱\t나노초\t0\t\n㎲\t마이크로초\t0\t\n㎳\t밀리초\t0\t\n㎸\t킬로볼트\t0\t\n㎽\t밀리와트\t0\t\n㏃\t베크렐\t0\t\n㏄\t시시\t0\t\n㏈\t데시벨\t0\t\n㏋\t마력\t0\t\n㏙\t피피엠\t0\t\n㏜\t시버트\t0\t",
    };
    private static final String[][] DICT_WORDS = rows(DICT_WORDS_SRC, 2), DICT_UNITS = rows(DICT_UNITS_SRC, 4);
    // </사전>
    /** 사전 글 덩이('쓴 글\t소리' 줄들)를 표로 */
    private static String[][] rows(String[] src, int n) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String chunk : src) for (String line : chunk.split("\n")) out.add(java.util.Arrays.copyOf(line.split("\t", -1), n));
        return out.toArray(new String[0][]);
    }

    /** LATIN + 사전 낱말 + 숫자 없이도 바꾸는 단위, 긴 것부터 */
    private static final String[][] LATIN_ALL;
    static {
        java.util.LinkedHashMap<String, String> all = new java.util.LinkedHashMap<>();
        for (String[] x : LATIN) all.putIfAbsent(x[0], x[1]);
        for (String[] x : DICT_WORDS) all.putIfAbsent(x[0], x[1]);
        for (String[] x : DICT_UNITS) if ("1".equals(x[2])) all.putIfAbsent(x[0], x[1]);
        java.util.List<String[]> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : all.entrySet()) list.add(new String[] {e.getKey(), e.getValue()});
        list.sort((a, b) -> b[0].length() - a[0].length()); // 같은 길이는 넣은 순서 그대로 (js의 sort도 안정 정렬)
        LATIN_ALL = list.toArray(new String[0][]);
    }
    private static final Pattern[] LATIN_RES = new Pattern[LATIN_ALL.length];
    static {
        for (int i = 0; i < LATIN_ALL.length; i++) LATIN_RES[i] = Pattern.compile("(?<![A-Za-z0-9À-ɏ])" + Pattern.quote(LATIN_ALL[i][0]) + "(?![A-Za-z0-9À-ɏ]|[.-]\\d)" + Pron.JOSA); // JOSA는 아래에 있어 이름을 앞에 붙인다(앞 참조 오류)
    }
    private static final Pattern DOTTED = Pattern.compile("(?<![A-Za-z0-9])((?:[A-Z]\\.){2,}[A-Z]?)(?![A-Za-z])");
    /** 한글 바로 뒤 괄호 속 영문(엡스타인(Epstein), 이산화탄소(CO2))은 한 번 더 읽지 않는다 */
    private static final Pattern LATIN_PAREN = Pattern.compile("([가-힣])\\s?\\(([A-Za-z][A-Za-z0-9 .,'&+-]*)\\)");
    private static final Pattern AMP = Pattern.compile("(?<![A-Za-z0-9])([A-Z]{1,3})\\s?&\\s?([A-Z]{1,3})(?![A-Za-z])");
    private static final Pattern CAPS = Pattern.compile("(?<![A-Za-z0-9À-ɏ])[A-Z]{2,5}(?![A-Za-z0-9À-ɏ])(?!-\\d)");
    private static final Pattern CAP_HANGUL = Pattern.compile("(?<![A-Za-z0-9À-ɏ])[A-Z](?=-?[가-힣]|[^A-Za-z0-9À-ɏ]|$)"); // 홀로 선 X·J도 낱자로

    private static String spell(String w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) b.append(ABC[w.charAt(i) - 'A']);
        return b.toString();
    }

    static String latin(String s) {
        for (int i = 0; i < LATIN_ALL.length; i++) {
            Matcher lm = LATIN_RES[i].matcher(s);
            if (!lm.find()) continue;
            String sound = LATIN_ALL[i][1];
            StringBuffer lb = new StringBuffer();
            do lm.appendReplacement(lb, Matcher.quoteReplacement(josa(sound, lm.group(1)))); while (lm.find());
            lm.appendTail(lb);
            s = lb.toString();
        }
        Matcher dm = DOTTED.matcher(s); // B.C., A.W. → 비씨, 에이더블유
        StringBuffer db = new StringBuffer();
        while (dm.find()) dm.appendReplacement(db, Matcher.quoteReplacement(spell(dm.group(1).replace(".", ""))));
        dm.appendTail(db);
        s = db.toString();
        Matcher m = AMP.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(spell(m.group(1)) + "앤" + spell(m.group(2))));
        m.appendTail(sb);
        m = CAPS.matcher(sb.toString());
        sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(spell(m.group()))); // 사전에 없는 대문자 약어는 낱자로 (USDA, 2026-10-26)
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
        String s = symbols(latin(numbers(LATIN_PAREN.matcher(text).replaceAll("$1"))));
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
    /** 숫자 앞에 붙여 읽는 말: 리터당 오백 밀리그램, 화씨 백 도 (js/pron.js의 PRE) */
    private static final java.util.Map<String, String> PRE = new java.util.HashMap<>();
    static {
        String[][] u = {{"%p", "퍼센트포인트"}, {"%포인트", "퍼센트포인트"}, {"%", "퍼센트"}, {"퍼센트포인트", "퍼센트포인트"}, {"퍼센트", "퍼센트"},
            {"km/h", "킬로미터"}, {"kWh", "킬로와트시"}, {"MWh", "메가와트시"}, {"GWh", "기가와트시"}, {"TWh", "테라와트시"}, {"kW", "킬로와트"}, {"MW", "메가와트"}, {"GW", "기가와트"},
            {"km²", "제곱킬로미터"}, {"km2", "제곱킬로미터"}, {"㎢", "제곱킬로미터"}, {"km", "킬로미터"}, {"kg", "킬로그램"}, {"mg", "밀리그램"}, {"μg", "마이크로그램"}, {"㎍", "마이크로그램"},
            {"cm", "센티미터"}, {"mm", "밀리미터"}, {"ml", "밀리리터"}, {"mL", "밀리리터"}, {"㎡", "제곱미터"}, {"m²", "제곱미터"}, {"m2", "제곱미터"}, {"m", "미터"}, {"g", "그램"}, {"t", "톤"},
            {"ℓ", "리터"}, {"L", "리터"}, {"ha", "헥타르"}, {"ppm", "피피엠"}, {"bp", "베이시스포인트"}, {"GB", "기가바이트"}, {"TB", "테라바이트"}, {"MB", "메가바이트"}, {"℃", "도"}, {"°C", "도"}, {"°", "도"}};
        for (String[] x : u) UNITS.put(x[0], x[1]);
        UNITS.put("m/s", "미터");
        for (String[] x : DICT_UNITS) if (!UNITS.containsKey(x[0])) { UNITS.put(x[0], x[1]); if (!x[3].isEmpty()) PRE.put(x[0], x[3]); }
    }
    /** 숫자 뒤 단위: 긴 것부터 (km/h가 km보다 먼저) */
    private static final String UNIT;
    static {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(java.util.Arrays.asList("%p", "%포인트", "%", "퍼센트포인트", "퍼센트", "km/h", "m/s", "kWh", "MWh", "GWh", "TWh", "kW", "MW", "GW",
            "km²", "km2", "㎢", "km", "kg", "mg", "μg", "㎍", "cm", "mm", "ml", "mL", "㎡", "m²", "m2", "m", "g", "t", "ℓ", "L", "ha", "ppm", "bp", "GB", "TB", "MB", "℃", "°C", "°"));
        for (String[] x : DICT_UNITS) set.add(x[0]);
        java.util.List<String> list = new java.util.ArrayList<>(set);
        list.sort((a, b) -> b.length() - a.length());
        StringBuilder b = new StringBuilder();
        for (String x : list) b.append(b.length() == 0 ? "" : "|").append(Pattern.quote(x));
        UNIT = b.toString();
    }
    private static final Pattern NUM = Pattern.compile("(?<![\\w.-])(?:(?<![^\\s(])(-))?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(?:\\s*(" + UNIT + ")(?![A-Za-z])|(?!\\.?\\d)(?![A-Za-z]))");
    private static final String COUNTER = "번째|시간|군데|차례|켤레|송이|그루|마리|가지|사람|그릇|자루|봉지|포기|마디|끼(?!리)|개(?!월|국|사|년|소|항|교|층|조)|명|살|곳|달(?!러)|가구|바퀴|병(?!원|사|력|역|합|행|충|실|무|상|동|과|장|균)"
        + "|권(?!리|한|역|고|자|위)|잔(?!액|고|여)|벌(?!금|써|어)|채(?!권)|척|통(?!화|신|계|합|과|령)|장(?!관|기|애|면)|배(?!럴)"
        + "|번(?!째|지|호|길)(?!\\s?(?:출구|국도|지방도|도로|버스|타자|홀|게이트|트랙|채널|레인|환자|확진자|후보))";
    private static final Pattern SPACE_BEFORE = Pattern.compile("(?:" + COUNTER + "|원|건|톤|명|곳|회)"); // 한자어로 읽은 숫자 뒤에 띄울 세는 말
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
    private static final Pattern SIGN_RE, CODE_AFTER, CODE_BEFORE, PER, PRE_BIG;
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
        java.util.List<String> pk = new java.util.ArrayList<>(PRE.keySet());
        pk.sort((a, b) -> b.length() != a.length() ? b.length() - a.length() : a.compareTo(b));
        StringBuilder pre = new StringBuilder();
        for (String k : pk) pre.append(pre.length() == 0 ? "" : "|").append(Pattern.quote(k));
        PRE_BIG = Pattern.compile("((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?:\\s?[조억만천])+)\\s?(" + pre + ")(?![A-Za-z])");
        // 'A/B' 꼴의 '당': 3만 동/kg, $100/t, 500mg/L (js/pron.js의 PER과 같게)
        PER = Pattern.compile("((?:(?:US\\$|\\$|€|£|¥|₩)\\s?)?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?:\\s?[조억만천])*\\s?(?:" + UNIT
                + "|원|달러|동|위안|엔|유로|루피아|루피|바트|헤알|링깃|페소|파운드|" + codes + ")?)\\s?/\\s?(" + UNIT + ")(?![A-Za-z])");
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
        e = PRE_BIG.matcher(s); // 하루 200만 배럴: 앞에 붙는 말은 숫자 전체 앞에 (js/pron.js와 같게)
        eb = new StringBuffer();
        while (e.find()) e.appendReplacement(eb, Matcher.quoteReplacement(PRE.get(e.group(2)) + " " + e.group(1) + " " + UNITS.get(e.group(2))));
        e.appendTail(eb);
        s = eb.toString();
        // 480만 t, 132만ha처럼 만·억 뒤의 단위
        e = BIG_UNIT.matcher(s);
        eb = new StringBuffer();
        while (e.find()) e.appendReplacement(eb, Matcher.quoteReplacement(e.group(1) + " " + UNITS.get(e.group(2))));
        e.appendTail(eb);
        s = eb.toString();
        Matcher per = PER.matcher(s);
        StringBuffer pb = new StringBuffer();
        while (per.find()) per.appendReplacement(pb, Matcher.quoteReplacement(UNITS.get(per.group(2)) + "당 " + per.group(1).trim()));
        per.appendTail(pb);
        s = pb.toString();
        s = s.replaceAll("(\\d)([만억조])(?=\\d)", "$1$2 "); // 2만6224 → 2만 6224: 붙여 넘기면 F1이 단위 글자를 빼먹었다 (2026-10-21)
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
                if ("m/s".equals(unit)) out = "초속 " + out;
                if (unit != null && PRE.containsKey(unit)) out = PRE.get(unit) + " " + out;
                if (unit != null) out += " " + UNITS.get(unit);
                else if (SPACE_BEFORE.matcher(rest).lookingAt()) out += " "; // 백 명, 육천이백이십사 명: 숫자 말과 세는 말을 띄운다
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
