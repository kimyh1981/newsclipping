package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/** 섹션 순서, 직접 넣은 언론사, 농업 키워드 */
public class ExtrasTest {
    private static String read(String name) throws Exception {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path f = p.resolve("tests/fixtures").resolve(name);
            if (Files.exists(f)) return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(name);
    }

    private static final long NOW = Extras.time("Tue, 13 Oct 2026 22:00:00 GMT");

    private static final String RSS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss><channel><title>농민신문</title>"
            + "<item><title><![CDATA[[단독] 쌀값 반등 &amp; 비료 지원]]></title><link>https://a/1</link><pubDate>Tue, 13 Oct 2026 20:00:00 GMT</pubDate>"
            + "<description>&lt;p&gt;정부가 비료 지원을 늘린다.&lt;/p&gt;</description></item>"
            + "<item><title>사흘 지난 기사</title><link>https://a/2</link><pubDate>Fri, 09 Oct 2026 20:00:00 GMT</pubDate></item>"
            + "<item><title>날짜 없는 기사</title><link>https://a/3</link></item></channel></rss>";

    @Test public void readsRssAndDropsOldItems() {
        assertTrue(Extras.isFeed(RSS));
        assertEquals("농민신문", Extras.feedTitle(RSS));
        List<Brief.Item> items = Extras.items(Extras.entries(RSS), "농민신문", false, NOW, 5, new HashSet<>());
        assertEquals(2, items.size());
        assertEquals("쌀값 반등 & 비료 지원", items.get(0).spoken);
        assertEquals("정부가 비료 지원을 늘린다.", items.get(0).summary);
        assertEquals("농민신문", items.get(0).source);
        assertTrue(items.get(0).local);
        assertEquals("날짜 없는 기사", items.get(1).spoken);
    }

    @Test public void googleNewsTitlesLoseTheOutletSuffix() {
        String g = "<rss><channel><title>google</title><item><title>퇴비 값 오른다 - 농민신문</title><link>https://n/1</link>"
                + "<pubDate>Tue, 13 Oct 2026 21:00:00 GMT</pubDate><description>&lt;a href=\"x\"&gt;퇴비 값 오른다&lt;/a&gt;</description>"
                + "<source url=\"https://www.nongmin.com\">농민신문</source></item></channel></rss>";
        List<Brief.Item> items = Extras.items(Extras.entries(g), "", true, NOW, 1, new HashSet<>());
        assertEquals("퇴비 값 오른다", items.get(0).spoken);
        assertEquals("농민신문", items.get(0).source);
        assertEquals("", items.get(0).summary); // 제목 되풀이뿐인 설명은 버린다
    }

    @Test public void cleansDoubleEscapedTextAndBylines() {
        assertEquals("a b & c", Extras.clean("a&amp;nbsp;b &amp;amp; c"));
        String rss = "<rss><channel><item><title>농지 조사</title><description>[한국농어민신문 김기자 기자] 정부가 농지를 본다.</description></item></channel></rss>";
        assertEquals("정부가 농지를 본다.", Extras.items(Extras.entries(rss), "x", false, NOW, 1, new HashSet<>()).get(0).summary);
    }

    @Test public void keywordArticlesMustMentionTheKeyword() {
        List<Extras.Entry> e = Arrays.asList(new Extras.Entry("SK텔레콤 감축사업", "", "", "", 0), new Extras.Entry("바이오플랜 미생물 비료", "", "", "", 0));
        assertEquals(1, Extras.withKeyword(e, "바이오플랜").size());
    }

    @Test public void findsRssLinkInPage() {
        String html = "<html><head><title>농축유통신문 | 홈</title><link rel=\"alternate\" type=\"application/rss+xml\" href=\"/rss/allArticle.xml\"></head></html>";
        assertEquals("https://www.amnews.co.kr/rss/allArticle.xml", Extras.discover(html, "https://www.amnews.co.kr/"));
        assertEquals("농축유통신문", Extras.pageTitle(html));
        assertEquals("amnews.co.kr", Extras.host("www.amnews.co.kr/news"));
    }

    @Test public void decodesEucKrFeeds() throws Exception {
        byte[] body = "<?xml version=\"1.0\" encoding=\"euc-kr\"?><rss><channel><title>한국농어민신문</title></channel></rss>".getBytes("EUC-KR");
        assertEquals("한국농어민신문", Extras.feedTitle(Extras.decode(body, "text/xml")));
    }

    @Test public void sectionOrderAndCustomSource() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        List<String> ids = new ArrayList<>();
        for (Brief.Section s : b.sections) ids.add(s.id);
        List<String> reversed = new ArrayList<>(ids);
        Collections.reverse(reversed);
        List<Brief.Picked> picked = b.select(null, reversed);
        assertEquals(reversed.get(reversed.size() - 1), picked.get(picked.size() - 1).id);
        // 순서에 없는 섹션은 뒤에 원래 순서대로
        assertEquals(ids, idsOf(b.ordered(Collections.singletonList("없는 섹션"))));

        Brief.Item mine = new Brief.Item("내 기사", "내 기사", "내 언론사", "x-1", "", "", Collections.<String>emptyList(), true);
        assertTrue(b.addSource(ids.get(0), new Brief.Source("custom-1", "내 언론사", "ko", true, 2, new ArrayList<>(Collections.singletonList(mine)))));
        assertFalse(b.addSource(ids.get(0), new Brief.Source("custom-1", "내 언론사", "ko", true, 2, new ArrayList<>())));
        boolean found = false;
        for (Brief.Line l : b.lines(null)) if (l.item == mine) { found = true; assertTrue(l.local); }
        assertTrue(found);
        // 체크를 끄면 읽지 않는다
        for (Brief.Line l : b.lines(new HashSet<>(Arrays.asList("a")))) assertFalse(l.item == mine);
    }

    @Test public void logoSites() throws Exception {
        assertEquals("nongmin.com", Extras.siteOf("https://www.nongmin.com/rss/all.xml"));
        assertEquals("donga.com", Extras.siteOf("https://rss.donga.com/total.xml"));
        assertEquals("news.kbs.co.kr", Extras.siteOf("news.kbs.co.kr"));
        assertEquals("", Extras.siteOf("주소 아님 ::"));
        Brief b = Brief.parse(read("briefing.json"));
        String sec = b.ordered(null).get(0).id;
        b.addSource(sec, new Brief.Source("c-1", "가", "ko", true, 2, new ArrayList<>()).site("a.com"));
        b.addSource(sec, new Brief.Source("c-2", "나", "ko", true, 2, new ArrayList<>()).site("a.com"));
        b.addSource(sec, new Brief.Source("c-3", "다", "ko", true, 2, new ArrayList<>()).site("c.com"));
        assertEquals(Arrays.asList("a.com", "c.com"), b.sites(null, null)); // 같은 누리집은 한 번, 주소 없는 곳은 뺀다
        assertEquals(Collections.singletonList("c.com"), b.sites(new HashSet<>(Arrays.asList("c-3")), null));
    }

    @Test public void keywordMentionLooksAtHeadlinesAndSummaries() {
        Brief.Picked farm = new Brief.Picked(Extras.FARM, "농업 신문 헤드라인", true);
        farm.items.add(new Brief.Item("벼 수확 한창", "벼 수확 한창", "농민신문", "g", "미생물 비료를 쓴 논", ""));
        assertTrue(Extras.mentions(farm, "미생물"));
        assertFalse(Extras.mentions(farm, "바이오플랜"));
    }

    private static List<String> idsOf(List<Brief.Section> secs) {
        List<String> out = new ArrayList<>();
        for (Brief.Section s : secs) out.add(s.id);
        return out;
    }

    @Test public void fertWorldCountAndLatest() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        Brief.Section sec = b.ordered(null).get(0);
        int before = 0;
        for (Brief.Picked p : b.select(null)) if (p.id.equals(sec.id)) before = p.items.size();
        b.setLimit(sec.id, 1);
        for (Brief.Picked p : b.select(null)) if (p.id.equals(sec.id)) assertTrue(p.items.size() <= Math.max(1, before));
        assertTrue(b.sourcesOf("없는 섹션").isEmpty());
        List<Extras.Entry> e = Extras.latestFirst(Arrays.asList(
                new Extras.Entry("옛", "", "", "", 1000), new Extras.Entry("모름", "", "", "", 0), new Extras.Entry("새", "", "", "", 5000)));
        assertEquals("새", e.get(0).title);
        assertEquals("모름", e.get(2).title);
        assertTrue(Extras.google("요소수", "2d").contains("when%3A2d"));
    }

    @Test public void keywordArticlesComeFirst() throws Exception {
        Brief b = Brief.parse(read("briefing.json"));
        Brief.Section sec = b.ordered(null).get(0);
        Brief.Source src = sec.sources.get(0);
        Brief.Item last = src.items.get(src.items.size() - 1);
        String word = last.spoken.split("\\s+")[0];
        b.prefer((s, x) -> s == sec && x == src, Collections.singletonList(word));
        assertTrue(Brief.mentions(src.items.get(0), Collections.singletonList(word)));
        assertTrue(src.items.contains(last));
        b.prefer((s, x) -> true, Collections.<String>emptyList()); // 키워드가 없으면 그대로
    }

    @Test public void abroadCountriesAndLines() throws Exception {
        assertEquals(100, Countries.ALL.size());
        java.util.Set<String> names = new HashSet<>();
        for (Countries.Country c : Countries.ALL) {
            assertTrue(c.ko, names.add(c.ko));
            assertTrue(c.ko, Arrays.asList(Countries.CONTINENTS).contains(c.continent));
            assertEquals(c.ko, c.gl.isEmpty(), c.hl.isEmpty());
        }
        int total = 0;
        for (List<Countries.Country> l : Countries.byContinent().values()) total += l.size();
        assertEquals(100, total);
        String us = Countries.searchUrl(Countries.byName("미국"), "biostimulant", "1d");
        assertTrue(us, us.endsWith("&hl=en-US&gl=US&ceid=US:en"));
        String dk = Countries.searchUrl(Countries.byName("덴마크"), "biostimulant", "1d");
        assertTrue(dk, dk.contains("Denmark") && dk.contains("gl=US"));
        assertEquals("en", Countries.byName("덴마크").searchLang());
        assertEquals("미국은 오늘자 '비료' 검색된 기사가 없습니다", Abroad.noArticle("미국", "비료"));
        assertEquals("중국은 오늘자 '비료' 검색된 기사가 없습니다", Abroad.noArticle("중국", "비료"));
        assertEquals("인도는 오늘자 '비료' 검색된 기사가 없습니다", Abroad.noArticle("인도", "비료"));
        assertEquals("미생물 비료", Abroad.parseTranslation("[[[\"미생물 \",\"microbial \",null,null,1],[\"비료\",\"fertilizer\",null,null,1]],null,\"en\"]"));
        // 나라를 고르지 않았거나 키워드가 없으면 아무것도 하지 않는다 (인터넷도 안 쓴다)
        Brief b = Brief.parse(read("briefing.json"));
        List<Brief.Picked> picked = b.select(null);
        int n = picked.size();
        Abroad.addCountryNews(b, picked, Collections.<String>emptyList(), Collections.singletonList("비료"), 0);
        Abroad.addCountryNews(b, picked, Collections.singletonList("미국"), Collections.<String>emptyList(), 0);
        assertEquals(n, picked.size());
    }

    @Test public void googleSourceUrlIsRead() {
        String xml = "<rss><channel><item><title>Urea prices climb - The Star</title><link>https://news.google.com/a</link>"
                + "<source url=\"https://www.thestar.com.my\">The Star</source></item></channel></rss>";
        assertEquals("https://www.thestar.com.my", Extras.entries(xml).get(0).sourceUrl);
    }

    @Test public void abroadPrefersLocalArticles() {
        Countries.Country my = Countries.byName("말레이시아");
        Extras.Entry wire = new Extras.Entry("Fertilizer prices jump on supply worries", "l1", "Reuters", "", 0, "https://www.reuters.com");
        Extras.Entry star = new Extras.Entry("Padi farmers get fertiliser aid", "l2", "The Star", "", 0, "https://www.thestar.com.my");
        Extras.Entry named = new Extras.Entry("Malaysia to cut urea imports", "l3", "Bloomberg", "", 0, "https://www.bloomberg.com");
        assertFalse(Abroad.local(my, wire, "공급 우려에 비료 가격 급등"));
        assertTrue(Abroad.local(my, star, "논 농가에 비료 지원"));
        assertTrue(Abroad.local(my, named, "말레이시아, 요소 수입 줄인다"));
        // 나라 이름을 붙인 것과 안 붙인 것, 띄어쓰기가 달라도 같은 기사
        assertEquals(Abroad.sameKey("공급 우려에 비료 가격 급등"), Abroad.sameKey("미국, 공급 우려에 비료가격 급등!"));
    }
}
