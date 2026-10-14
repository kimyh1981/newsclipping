package io.github.kimyh1981.newsclipping;

import android.util.Log;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/**
 * 해외농업 키워드 (바이오플랜 해외 영업용). 영어나 우리말로 넣은 키워드를 고른 나라의 말로 옮겨 그 나라 구글 뉴스에서 찾고,
 * 찾은 기사 제목을 우리말로 옮겨 '나라 이름, 제목'으로 읽는다.
 * 그 나라 언론사를 이미 듣고 있으면 키워드 기사를 그 언론사에서 먼저 고르고, 거기 키워드 기사가 없을 때만 나라별로 한 건씩 찾는다.
 */
final class Abroad {
    private Abroad() {}

    private static final String TAG = "newsclipping";
    /** 해외농업 키워드 기사를 모아 읽는 섹션 (폰에서 만든다) */
    static final String SECTION = "abroad-kw";
    static final String TITLE = "해외농업 키워드 뉴스";
    /** 키워드가 해외 언론사 기사 고르기에 작용하는 섹션 (나라와 상관없이) */
    static final Set<String> FOREIGN_FARM = new HashSet<>(java.util.Arrays.asList(Extras.FERT_WORLD, "global-agri", "vietnam"));

    // ---- 번역 (키 없는 구글 번역, 서버 collect.js와 같은 주소) ----

    private static final Map<String, String> cache = Collections.synchronizedMap(new HashMap<>());

    /** text를 to 말로. 실패하면 null */
    static String translate(String text, String to) {
        String key = to + "|" + text;
        String hit = cache.get(key);
        if (hit != null) return hit;
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + to + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
            String out = parseTranslation(Extras.get(url));
            if (out.isEmpty()) return null;
            cache.put(key, out);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "번역 실패 " + to + ": " + e);
            return null;
        }
    }

    /** 구글 번역 응답 [[["번역","원문",…],…],…] → 번역문 */
    static String parseTranslation(String json) throws Exception {
        JSONArray parts = new JSONArray(json).optJSONArray(0);
        if (parts == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray p = parts.optJSONArray(i);
            if (p != null && !p.isNull(0)) b.append(p.optString(0));
        }
        return b.toString().trim();
    }

    /** 키워드를 찾을 모양들: 넣은 그대로 + 우리말 번역 (번역 기사 제목은 우리말이라) */
    static List<String> forms(String keyword) {
        List<String> out = new ArrayList<>();
        out.add(keyword);
        String ko = translate(keyword, "ko");
        if (ko != null && !ko.equalsIgnoreCase(keyword)) out.add(ko);
        return out;
    }

    // ---- 나라별 검색 ----

    /** 받침이 있으면 '은', 없으면 '는' */
    static String topic(String word) {
        char c = word.isEmpty() ? 'a' : word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return word + "는";
        return word + ((c - 0xAC00) % 28 == 0 ? "는" : "은");
    }

    /** 검색 결과가 없을 때 읽는 줄 */
    static String noArticle(String country, String keyword) {
        return topic(country) + " 오늘자 '" + keyword + "' 검색된 기사가 없습니다";
    }

    /** 같은 기사인지 볼 열쇠: 나라 이름을 떼고 띄어쓰기·문장부호를 뺀 제목 */
    static String sameKey(String title) {
        return title.replaceFirst("^[가-힣]{1,10}, ", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    /**
     * 그 나라 기사인지: 언론사 주소가 그 나라 도메인(.my, .com.my …)이거나 제목에 그 나라 이름이 있다.
     * 영어판 구글 뉴스(말레이시아·싱가포르·인도 …)에는 로이터 같은 국제 기사가 미국판과 똑같이 올라와서, 그 나라 기사를 먼저 고른다
     */
    static boolean local(Countries.Country c, Extras.Entry e, String ko) {
        String host = Extras.host(e.sourceUrl);
        if (!c.gl.isEmpty() && !c.gl.equals("US") && host.endsWith("." + c.gl.toLowerCase(java.util.Locale.ROOT))) return true;
        if (c.gl.equals("US") && (host.endsWith(".us") || host.endsWith(".gov"))) return true;
        String t = e.title.toLowerCase(java.util.Locale.ROOT);
        return t.contains(c.en.toLowerCase(java.util.Locale.ROOT)) || (ko != null && ko.contains(c.ko));
    }

    /**
     * 한 나라에서 키워드 기사 한 건. 검색은 됐는데 기사가 없으면 빈 목록, 검색이 안 되면 예외.
     * 그 나라 기사를 먼저 고르고, 없으면 다른 나라에서 아직 안 고른 기사. 같은 기사를 두 나라에서 읽지 않는다 (seen은 나라들이 함께 쓴다)
     */
    static List<Brief.Item> search(Countries.Country c, String keyword, long now, Set<String> seen) throws Exception {
        String word = translate(keyword, c.searchLang());
        if (word == null) word = keyword;
        List<Extras.Entry> entries = Extras.latestFirst(Extras.entries(Extras.get(Countries.searchUrl(c, word, "1d"))));
        Extras.Entry pick = null;
        String pickKo = null;
        int checked = 0;
        for (Extras.Entry e : entries) {
            if (e.time > 0 && now - e.time > Extras.MAX_AGE) continue;
            String title = Extras.spoken(e.title, e.source);
            if (title.isEmpty()) continue;
            if (seen.contains(sameKey(title)) || seen.contains(e.link)) continue;
            if (++checked > 8) break; // 번역을 너무 많이 부르지 않게
            String ko = c.searchLang().startsWith("ko") ? title : translate(title, "ko");
            if (ko == null) continue;
            if (seen.contains(sameKey(Extras.spoken(ko, "")))) continue;
            boolean here = local(c, e, ko);
            if (pick == null || here) { pick = e; pickKo = ko; }
            if (here) break;
        }
        if (pick == null) return Collections.emptyList();
        String bare = Extras.spoken(pickKo, "");
        String spoken = c.ko + ", " + bare;
        synchronized (seen) {
            String raw = sameKey(Extras.spoken(pick.title, pick.source));
            if (seen.contains(raw) || seen.contains(sameKey(bare))) return Collections.emptyList(); // 다른 나라가 방금 같은 기사를 골랐다
            seen.add(raw);
            seen.add(sameKey(bare));
            if (!pick.link.isEmpty()) seen.add(pick.link);
        }
        String key = spoken.replaceAll("\\s+", "");
        return Collections.singletonList(new Brief.Item(spoken, spoken, pick.source.isEmpty() ? c.ko + " 뉴스" : pick.source, "x-" + key, "", "", Collections.<String>emptyList(), true));
    }

    /**
     * 고른 나라마다 키워드마다: 그 나라 언론사 기사(이미 고른 것)에 키워드가 있으면 그대로 두고, 없으면 그 나라에서 한 건 찾는다.
     * 찾았는데 없으면 '오늘자 검색된 기사가 없습니다'. 모은 것은 '해외농업 키워드 뉴스' 섹션으로 해외 비료 시장 뉴스 뒤에 넣는다
     */
    static void addCountryNews(Brief b, List<Brief.Picked> picked, List<String> countries, List<String> keywords, long now) {
        if (countries.isEmpty() || keywords.isEmpty()) return;
        Map<Brief.Item, String> countryOf = new IdentityHashMap<>();
        for (Brief.Section sec : b.sections) for (Brief.Source src : sec.sources) for (Brief.Item it : src.items) if (!src.country.isEmpty()) countryOf.put(it, src.country);
        Set<String> seen = Collections.synchronizedSet(new HashSet<String>());
        for (String t : Extras.titles(b)) seen.add(sameKey(t));
        for (Brief.Picked p : picked) for (Brief.Item it : p.items) seen.add(sameKey(it.spoken));
        Map<String, List<String>> formsOf = new HashMap<>();
        for (String k : keywords) formsOf.put(k, forms(k));

        List<String[]> todo = new ArrayList<>(); // {나라, 키워드}
        for (String name : countries) {
            if (Countries.byName(name) == null) continue;
            for (String k : keywords) {
                boolean covered = false;
                for (Brief.Picked p : picked) for (Brief.Item it : p.items) covered |= name.equals(countryOf.get(it)) && Brief.mentions(it, formsOf.get(k));
                if (!covered) todo.add(new String[] {name, k});
            }
        }
        if (todo.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<List<Brief.Item>>> results = new ArrayList<>();
        for (String[] t : todo) results.add(pool.submit(() -> search(Countries.byName(t[0]), t[1], now, seen)));
        pool.shutdown();
        long until = System.currentTimeMillis() + 20000;
        Brief.Picked out = new Brief.Picked(SECTION, TITLE, false);
        for (int i = 0; i < todo.size(); i++) {
            try {
                List<Brief.Item> got = results.get(i).get(Math.max(1, until - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (got.isEmpty()) {
                    String line = noArticle(todo.get(i)[0], todo.get(i)[1]);
                    out.items.add(new Brief.Item(line, line, todo.get(i)[0] + " 뉴스", "none-" + i, "", "", Collections.<String>emptyList(), true));
                } else {
                    out.items.addAll(got);
                }
            } catch (Exception e) {
                Log.w(TAG, "해외 키워드 검색 실패 " + todo.get(i)[0] + " " + todo.get(i)[1] + ": " + e); // 인터넷 문제: 없다고 말하지 않고 넘어간다
            }
        }
        pool.shutdownNow();
        if (out.items.isEmpty()) return;
        int at = picked.size();
        for (int i = 0; i < picked.size(); i++) if (picked.get(i).id.equals(Extras.FERT_WORLD)) at = i + 1;
        picked.add(at, out);
    }
}
