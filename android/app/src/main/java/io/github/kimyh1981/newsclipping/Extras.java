package io.github.kimyh1981.newsclipping;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 폰이 직접 가져오는 기사: 사람이 주소를 넣은 언론사, 농업 키워드 기사.
 * 서버(briefing.json)에 없는 것이라 녹음 없이 폰 음성으로 읽는다. 주소가 RSS가 아니면 페이지의 RSS 링크를,
 * 그것도 없으면 구글 뉴스에서 그 사이트 기사를 찾는다.
 */
final class Extras {
    private Extras() {}

    static final int TAKE = 2;
    static final long MAX_AGE = 36L * 3600 * 1000;
    static final String FARM = "farm";
    /** 해외 농업·비료 시장 뉴스 섹션 (feeds.json) */
    static final String FERT_WORLD = "fert-world";

    /** 사람이 넣은 언론사. feed는 확인해 둔 RSS 주소(없으면 매번 찾는다) */
    static final class Custom {
        final String id, name, url, feed, section;
        Custom(String id, String name, String url, String feed, String section) {
            this.id = id; this.name = name; this.url = url; this.feed = feed; this.section = section;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("url", url).put("feed", feed).put("section", section);
        }
    }

    /** 'https://www.nongmin.com/rss' → 'nongmin.com' (로고를 찾을 누리집 주소) */
    static String siteOf(String url) {
        try {
            String host = new java.net.URI(url.contains("://") ? url.trim() : "https://" + url.trim()).getHost();
            return host == null ? "" : host.replaceFirst("^(www|rss|feeds?|m)\\.", "");
        } catch (Exception e) {
            return "";
        }
    }

    static List<Custom> parseCustom(String json) {
        List<Custom> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Custom(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("feed", ""), o.getString("section")));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    static String customJson(List<Custom> list) {
        JSONArray a = new JSONArray();
        try {
            for (Custom c : list) a.put(c.json());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    /** 사람이 넣은 주소를 다듬는다: 앞뒤 공백, http(s) 없으면 https */
    static String normalize(String url) {
        String u = url.trim();
        if (u.isEmpty()) return u;
        return u.matches("(?i)^https?://.*") ? u : "https://" + u;
    }

    static String host(String url) {
        try {
            String h = new URL(normalize(url)).getHost().toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }

    static String customId(String url) {
        return "custom-" + Integer.toHexString(normalize(url).toLowerCase(Locale.ROOT).hashCode());
    }

    static String google(String query) { return google(query, "1d"); }

    /** when: 구글 뉴스 검색 기간 (1d, 2d …) */
    static String google(String query, String when) {
        try {
            return "https://news.google.com/rss/search?q=" + URLEncoder.encode(query + " when:" + when, "UTF-8") + "&hl=ko&gl=KR&ceid=KR:ko";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 글 다듬기 ----

    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>");
    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    static String clean(String s) {
        if (s == null) return "";
        s = CDATA.matcher(s).replaceAll("$1");
        s = s.replace("&amp;", "&"); // 두 번 감싼 &amp;nbsp; 도 풀리게 먼저
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&nbsp;", " ");
        Matcher m = NUM_ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int cp;
            try { cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16); } catch (NumberFormatException e) { cp = ' '; }
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(sb);
        s = sb.toString().replace("&amp;", "&").replaceAll("<[^>]*>", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** 읽을 제목: [단독]·(종합) 같은 꼬리표와 ' - 언론사'를 뗀다 */
    static String spoken(String title, String source) {
        String t = title.replaceAll("^\\s*(\\[[^\\]]{1,12}\\]\\s*)+", "").replaceAll("\\s*[(\\[](종합|속보|단독|1보|2보|영상|포토|사진)[)\\]]\\s*", " ").trim();
        if (!source.isEmpty() && t.endsWith(" - " + source)) t = t.substring(0, t.length() - source.length() - 3).trim();
        return t;
    }

    // ---- RSS·Atom 읽기 ----

    static final class Entry {
        final String title, link, source, summary;
        /** 구글 뉴스가 알려 준 언론사 주소 (<source url="…">). 모르면 "" */
        final String sourceUrl;
        final long time; // 모르면 0
        Entry(String title, String link, String source, String summary, long time) {
            this(title, link, source, summary, time, "");
        }
        Entry(String title, String link, String source, String summary, long time, String sourceUrl) {
            this.title = title; this.link = link; this.source = source; this.summary = summary; this.time = time; this.sourceUrl = sourceUrl;
        }
    }

    private static final Pattern ITEM = Pattern.compile("<(item|entry)[\\s>][\\s\\S]*?</\\1>", Pattern.CASE_INSENSITIVE);

    private static String tag(String xml, String names) {
        Matcher m = Pattern.compile("<(" + names + ")(?:\\s[^>]*)?>([\\s\\S]*?)</\\1>", Pattern.CASE_INSENSITIVE).matcher(xml);
        return m.find() ? m.group(2) : "";
    }

    static boolean isFeed(String text) {
        String head = text.substring(0, Math.min(text.length(), 2000)).toLowerCase(Locale.ROOT);
        return head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf");
    }

    /** 피드 이름: channel(또는 feed)의 첫 title */
    static String feedTitle(String xml) {
        int first = xml.toLowerCase(Locale.ROOT).indexOf("<item");
        if (first < 0) first = xml.toLowerCase(Locale.ROOT).indexOf("<entry");
        return clean(tag(first < 0 ? xml : xml.substring(0, first), "title"));
    }

    static List<Entry> entries(String xml) {
        List<Entry> out = new ArrayList<>();
        Matcher m = ITEM.matcher(xml);
        while (m.find()) {
            String it = m.group();
            String title = clean(tag(it, "title"));
            if (title.isEmpty()) continue;
            String link = clean(tag(it, "link"));
            if (link.isEmpty()) {
                Matcher h = Pattern.compile("<link[^>]*href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(it);
                if (h.find()) link = h.group(1);
            }
            String source = clean(tag(it, "source"));
            Matcher su = Pattern.compile("<source[^>]*url=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(it);
            String summary = clean(tag(it, "description|summary|content"));
            out.add(new Entry(title, link, source, summary, time(clean(tag(it, "pubDate|published|updated|dc:date"))), su.find() ? su.group(1) : ""));
        }
        return out;
    }

    static long time(String s) {
        if (s.isEmpty()) return 0;
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        for (String f : new String[] {"EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "d MMM yyyy HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss"}) {
            try {
                SimpleDateFormat p = new SimpleDateFormat(f, Locale.ENGLISH);
                if (f.startsWith("yyyy")) p.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
                return p.parse(s.trim()).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /** 페이지 안의 RSS 링크 (link rel=alternate type=application/rss+xml) */
    static String discover(String html, String base) {
        Matcher m = Pattern.compile("<link[^>]+>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String l = m.group();
            if (!l.matches("(?is).*type=[\"']application/(rss|atom)\\+xml[\"'].*")) continue;
            Matcher h = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(l);
            if (!h.find()) continue;
            try {
                return new URL(new URL(base), clean(h.group(1))).toString();
            } catch (Exception ignored) { }
        }
        return "";
    }

    /** 기사로 만든다: 오래된 것(36시간)과 이미 있는 제목은 빼고 max건 */
    static List<Brief.Item> items(List<Entry> entries, String fallbackSource, boolean useEntrySource, long now, int max, Set<String> seenTitles) {
        List<Brief.Item> out = new ArrayList<>();
        for (Entry e : entries) {
            if (out.size() >= max) break;
            if (e.time > 0 && now - e.time > MAX_AGE) continue;
            String source = useEntrySource && !e.source.isEmpty() ? e.source : fallbackSource;
            String spoken = spoken(e.title, e.source.isEmpty() ? source : e.source);
            String key = spoken.replaceAll("\\s+", "");
            if (spoken.isEmpty() || !seenTitles.add(key)) continue;
            String summary = e.summary.replaceAll("^\\s*\\[[^\\]]*기자\\]\\s*", ""); // [농민신문 홍길동 기자]
            if (summary.replaceAll("\\s+", "").contains(key)) summary = ""; // 구글 뉴스 설명은 제목 되풀이
            if (summary.length() > 300) summary = summary.substring(0, 300).replaceAll("\\s\\S*$", "") + "…";
            out.add(new Brief.Item(spoken, spoken, source, "x-" + key, summary, "", Collections.<String>emptyList(), true));
        }
        return out;
    }

    /** 이미 브리핑에 있는 제목들 (같은 기사를 두 번 읽지 않게) */
    static Set<String> titles(Brief b) {
        Set<String> out = new HashSet<>();
        for (Brief.Section s : b.sections) for (Brief.Source src : s.sources) for (Brief.Item it : src.items) out.add(it.spoken.replaceAll("\\s+", ""));
        return out;
    }

    static final String FARM_WORDS = "농업 OR 농가 OR 농민 OR 농촌 OR 농산물 OR 작물 OR 비료 OR 축산";

    static List<Entry> withKeyword(List<Entry> entries, String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if ((e.title + " " + e.summary).toLowerCase(Locale.ROOT).contains(k)) out.add(e);
        return out;
    }

    /** 키워드 기사: 농업 기사에서 먼저, 없으면 키워드만으로. 제목이나 설명에 키워드가 있는 기사만 */
    static List<Brief.Item> keywordItems(String k, long now, int max, Set<String> seen) throws Exception {
        List<Brief.Item> got = items(withKeyword(entries(get(google("\"" + k + "\" (" + FARM_WORDS + ")"))), k), "", true, now, max, seen);
        if (got.isEmpty()) got = items(withKeyword(entries(get(google("\"" + k + "\""))), k), "", true, now, max, seen);
        return got;
    }

    static boolean mentions(Brief.Picked sec, String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT);
        for (Brief.Item it : sec.items) {
            if ((it.title + " " + it.spoken + " " + it.summary).toLowerCase(Locale.ROOT).contains(k)) return true;
        }
        return false;
    }

    /** 최신 기사부터 (시각을 모르는 기사는 뒤로) */
    static List<Entry> latestFirst(List<Entry> entries) {
        List<Entry> out = new ArrayList<>(entries);
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    // ---- 인터넷 ----

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) newsclipping");
        try {
            int code = c.getResponseCode();
            if (code / 100 == 3) { // http→https처럼 프로토콜이 바뀌는 이동은 직접 따라간다
                String to = c.getHeaderField("Location");
                if (to != null) return get(new URL(new URL(url), to).toString());
            }
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            byte[] body;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0 && out.size() < 3_000_000; ) out.write(buf, 0, n);
                body = out.toByteArray();
            }
            return decode(body, c.getContentType());
        } finally {
            c.disconnect();
        }
    }

    /** 글자 인코딩: 헤더, XML 선언, meta charset 순으로 (EUC-KR 언론사 RSS) */
    static String decode(byte[] body, String contentType) {
        String cs = null;
        Matcher m = Pattern.compile("charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType == null ? "" : contentType);
        if (m.find()) cs = m.group(1);
        String head = new String(body, 0, Math.min(body.length, 2048), StandardCharsets.ISO_8859_1);
        Matcher x = Pattern.compile("(?:encoding|charset)=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
        if (x.find()) cs = x.group(1);
        try {
            return new String(body, cs == null ? StandardCharsets.UTF_8 : Charset.forName(cs));
        } catch (Exception e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** 결과: 찾은 RSS 주소와 이름, 기사 */
    static final class Found {
        final String feed, name;
        final List<Entry> entries;
        final boolean viaGoogle;
        Found(String feed, String name, List<Entry> entries, boolean viaGoogle) {
            this.feed = feed; this.name = name; this.entries = entries; this.viaGoogle = viaGoogle;
        }
    }

    /** 주소에서 기사를 찾는다: RSS면 그대로, 페이지면 RSS 링크, 그래도 없으면 구글 뉴스 site: 검색 */
    static Found find(String url, String knownFeed) {
        String u = normalize(url);
        List<String> tries = new ArrayList<>();
        if (knownFeed != null && !knownFeed.isEmpty()) tries.add(knownFeed);
        tries.add(u);
        for (String t : tries) {
            try {
                String text = get(t);
                if (isFeed(text)) {
                    List<Entry> e = entries(text);
                    if (!e.isEmpty()) return new Found(t, feedTitle(text), e, false);
                    continue;
                }
                String feed = discover(text, t);
                if (!feed.isEmpty()) {
                    String x = get(feed);
                    if (isFeed(x) && !entries(x).isEmpty()) return new Found(feed, pageTitle(text), entries(x), false);
                }
            } catch (Exception ignored) { }
        }
        String host = host(u);
        if (host.isEmpty()) return null;
        String g = google("site:" + host);
        try {
            List<Entry> e = entries(get(g));
            return e.isEmpty() ? null : new Found(g, e.get(0).source, e, true);
        } catch (Exception ex) {
            return null;
        }
    }

    static String pageTitle(String html) {
        String t = clean(tag(html, "title"));
        return t.split("\\s*[|:–—-]\\s*")[0].trim();
    }

    // ---- 오늘 원고에 붙이기 (NewsService가 브리핑을 받은 뒤 부른다) ----

    /** 직접 넣은 언론사의 기사를 받아 그 섹션에 붙인다. 함께 받고, 늦는 곳(12초)은 오늘은 뺀다 */
    static void addCustom(Brief b, List<Custom> list, long now) {
        if (list.isEmpty()) return;
        Set<String> seen = Collections.synchronizedSet(titles(b));
        List<Brief.Source> found = Collections.synchronizedList(new ArrayList<>());
        List<String> sectionOf = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (Custom c : list) {
            Thread t = new Thread(() -> {
                Found f = find(c.url, c.feed);
                List<Brief.Item> items = f == null ? Collections.<Brief.Item>emptyList() : items(f.entries, c.name, false, now, 6, seen);
                synchronized (found) {
                    found.add(new Brief.Source(c.id, c.name, "ko", true, TAKE, items).site(siteOf(c.url)));
                    sectionOf.add(c.section);
                }
            }, "news-custom");
            t.start();
            threads.add(t);
        }
        long until = System.currentTimeMillis() + 12000;
        for (Thread t : threads) {
            try { t.join(Math.max(1, until - System.currentTimeMillis())); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        synchronized (found) {
            for (int i = 0; i < found.size(); i++) b.addSource(sectionOf.get(i), found.get(i));
        }
        // 늦어서 못 붙인 곳도 체크 목록에는 보이게 (오늘 기사 없음)
        for (Custom c : list) b.addSource(c.section, new Brief.Source(c.id, c.name, "ko", true, TAKE, Collections.<Brief.Item>emptyList()).site(siteOf(c.url)));
    }

    /** 농업 헤드라인에 키워드 기사가 없으면 그 키워드 기사를 한 건씩 더 붙인다 (구글 뉴스, 최근 하루) */
    static void addKeywords(Brief b, List<Brief.Picked> picked, List<String> keywords, long now) {
        if (keywords.isEmpty()) return;
        Brief.Picked farm = null;
        for (Brief.Picked p : picked) if (FARM.equals(p.id)) farm = p;
        if (farm == null) return; // 농업 섹션을 듣지 않는다
        Set<String> seen = titles(b);
        for (Brief.Picked p : picked) for (Brief.Item it : p.items) seen.add(it.spoken.replaceAll("\\s+", ""));
        for (String k : keywords) {
            if (mentions(farm, k)) continue;
            try {
                List<Brief.Item> got = keywordItems(k, now, 1, seen);
                for (Brief.Item it : got) {
                    farm.items.add(new Brief.Item(it.title, it.spoken, k + " 관련", it.group, it.summary, "", Collections.<String>emptyList(), true));
                }
            } catch (Exception ignored) { } // 못 찾으면 오늘은 그냥 넘어간다
        }
    }
}
