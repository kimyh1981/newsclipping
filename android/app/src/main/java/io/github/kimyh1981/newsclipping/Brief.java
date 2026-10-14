package io.github.kimyh1981.newsclipping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * briefing.json(언론사마다 기사)에서 고른 언론사로 원고를 만든다.
 * js/brief.js와 같은 규칙이고, tests/fixtures의 같은 파일로 두 쪽 결과를 맞춰 본다.
 */
final class Brief {
    static final class Item {
        /** summary: 첫 두세 문장('자세히'), body: 본문 앞부분('전체 듣기'). 없으면 "" */
        final String title, spoken, source, group, summary, body;
        /** 서버가 소리 내어 읽기 좋게 다듬어 문장마다 나눈 '전체 듣기' 글 (문장마다 녹음·자막). 옛 briefing.json이면 비어 있다 */
        final List<String> say;
        /** 폰이 직접 가져온 기사(직접 넣은 언론사, 농업 키워드): 서버 녹음이 없어 폰 음성으로 읽는다 */
        final boolean local;
        Item(String title, String spoken, String source, String group, String summary, String body) {
            this(title, spoken, source, group, summary, body, Collections.<String>emptyList());
        }
        Item(String title, String spoken, String source, String group, String summary, String body, List<String> say) {
            this(title, spoken, source, group, summary, body, say, false);
        }
        Item(String title, String spoken, String source, String group, String summary, String body, List<String> say, boolean local) {
            this.title = title; this.spoken = spoken; this.source = source; this.group = group; this.summary = summary; this.body = body; this.say = say; this.local = local;
        }

        /** '전체 듣기'로 읽을 글: 본문이 없으면 요약 */
        String full() { return body.isEmpty() ? summary : body; }

        /** '전체 듣기'로 읽을 문장들: 서버가 나눠 둔 것, 없으면 본문(요약) 통째로 한 덩어리 */
        List<String> fullSentences() {
            if (!say.isEmpty()) return say;
            String f = full();
            return f.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(f);
        }
    }

    /** 원고 한 줄. 기사 헤드라인 줄이면 item이 있다 ('자세히'에서 요약을 읽는다). 빈 줄은 잠깐 쉰다 */
    static final class Line {
        final String text;
        final Item item;
        /** 폰이 직접 가져온 기사의 줄(헤드라인과 그 언론사 소개): 서버 녹음을 찾지 않는다 */
        final boolean local;
        /** 제품 공부·농업영어 줄이면 그 회 번호, 아니면 0 */
        final int lesson;
        /** 농업영어 코너의 줄 (한국어 풀이 줄 포함) */
        final boolean english;
        /** 영어 목소리로 읽는 줄: 한국어 읽기 규칙을 거치지 않는다 */
        final boolean en;
        /** 이 줄 뒤에 쉬는 시간(밀리초): 따라 말할 틈 */
        final int pause;
        Line(String text, Item item) { this(text, item, item != null && item.local); }
        Line(String text, Item item, boolean local) { this(text, item, local, 0); }
        Line(String text, Item item, boolean local, int lesson) { this(text, item, local, lesson, false, false, 0); }
        Line(String text, Item item, boolean local, int lesson, boolean english, boolean en, int pause) {
            this.text = text; this.item = item; this.local = local; this.lesson = lesson; this.english = english; this.en = en; this.pause = pause;
        }
    }

    /** 제품 공부 한 회 (서버 lessons/product.json)와 같은 회의 농업영어 (lessons/english.json, 없으면 빈 목록) */
    static final class Lesson {
        final int n;
        final String title;
        final List<String> lines;
        final List<Line> english = new ArrayList<>();
        Lesson(int n, String title, List<String> lines) { this.n = n; this.title = title; this.lines = lines; }
    }

    static final class Source {
        final String id, name, lang;
        final boolean byDefault;
        final int take;
        final List<Item> items;
        /** 언론사 누리집 주소 (홈 화면 로고용, 모를 때 빈칸) */
        String site = "";
        /** 외국 언론사의 나라 (미국, 중국 …). 모르거나 국내면 빈칸 */
        String country = "";
        Source(String id, String name, String lang, boolean byDefault, int take, List<Item> items) {
            this.id = id; this.name = name; this.lang = lang; this.byDefault = byDefault; this.take = take; this.items = items;
        }
        Source site(String s) { site = s == null ? "" : s; return this; }
        Source country(String c) { country = c == null ? "" : c; return this; }
    }

    static final class Section {
        final String id, title;
        final boolean perSourceLabel;
        int limit; // 해외 농업·비료 시장 뉴스는 앱에서 기사 수를 고른다
        final List<Source> sources;
        Section(String id, String title, boolean perSourceLabel, int limit, List<Source> sources) {
            this.id = id; this.title = title; this.perSourceLabel = perSourceLabel; this.limit = limit; this.sources = sources;
        }
    }

    /** 고른 언론사로 추린 섹션 */
    static final class Picked {
        final String id, title;
        final boolean perSourceLabel;
        final List<Item> items = new ArrayList<>();
        Picked(String id, String title, boolean perSourceLabel) { this.id = id; this.title = title; this.perSourceLabel = perSourceLabel; }
    }

    // 아나운서처럼: 요일에 맞춘 인사, 섹션마다 다른 연결 말, 언론사 소개, 맺음말 (js/brief.js와 같은 문장)
    static String opening(String date) {
        String extra = date.endsWith("월요일") ? " 새로운 한 주, 힘차게 시작해 보시죠." : date.endsWith("금요일") ? " 한 주의 마무리, 오늘도 힘내세요." : "";
        return "좋은 아침입니다. " + date + ", 출근길 뉴스 브리핑입니다." + extra;
    }

    static String intro(String title, int i, int n) {
        if (i == 0) return "먼저 " + title + "부터 전해 드립니다.";
        if (i == n - 1) return "끝으로 " + title + "입니다.";
        return (i % 2 == 1 ? "다음은 " : "이어서 ") + title + "입니다.";
    }

    /** 뉴스 뒤 제품 공부의 첫 줄 (js/brief.js lessonIntro와 같은 문장) */
    static String lessonIntro(int n, String title) {
        return "이어서 바이오플랜 제품 공부 시간입니다. 오늘은 " + n + "회, " + sentence(title);
    }

    static String closing(String date) {
        return "지금까지 " + date + " 아침 뉴스였습니다. 오늘도 안전 운전하시고, 좋은 하루 보내세요.";
    }

    /** 서버가 오늘 원고를 아직 못 만들어 어제 원고를 읽을 때 인사 뒤에 붙인다 (js/brief.js STALE과 같은 문장) */
    static final String STALE = "오늘 아침 소식은 아직 정리되지 않아, 어제 모은 뉴스로 전해 드립니다.";
    private static final String[] DAYS = {"일", "월", "화", "수", "목", "금", "토"};

    /** 서울 날짜 '10월 7일 수요일' (서버의 dateLabel과 같은 모양) */
    static String koreanDate(long now) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        c.setTimeInMillis(now);
        return (c.get(java.util.Calendar.MONTH) + 1) + "월 " + c.get(java.util.Calendar.DAY_OF_MONTH) + "일 " + DAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] + "요일";
    }

    final String dateLabel;
    /** 서버가 정한 오늘 자동 재생 여부: 주말·공휴일이면 false */
    final boolean playToday;
    final List<Section> sections;
    /** 서버가 구글 음성으로 녹음해 둔 줄(audioId)과 그 파일이 있는 곳(briefing.json 기준 상대 경로). 없으면 빈 목록 */
    final Set<String> audioIds;
    final String audioBase;
    /** 제품 공부 회차들 (옛 briefing.json이면 비어 있다) */
    final List<Lesson> lessons;

    private Brief(String dateLabel, boolean playToday, List<Section> sections, Set<String> audioIds, String audioBase, List<Lesson> lessons) {
        this.dateLabel = dateLabel; this.playToday = playToday; this.sections = sections; this.audioIds = audioIds; this.audioBase = audioBase; this.lessons = lessons;
    }

    /** 녹음 파일 이름: 줄 글자(앞뒤 공백 뺌)의 SHA-1 앞 16자리. 서버 tools/tts.js의 audioId와 같은 규칙 */
    static String audioId(String text) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest(text.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i] & 0xff));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Brief parse(String json) throws JSONException {
        JSONObject b = new JSONObject(json);
        List<Section> sections = new ArrayList<>();
        JSONArray secs = b.getJSONArray("sections");
        for (int i = 0; i < secs.length(); i++) {
            JSONObject s = secs.getJSONObject(i);
            List<Source> sources = new ArrayList<>();
            JSONArray srcs = s.getJSONArray("sources");
            for (int j = 0; j < srcs.length(); j++) {
                JSONObject src = srcs.getJSONObject(j);
                List<Item> items = new ArrayList<>();
                JSONArray its = src.getJSONArray("items");
                for (int k = 0; k < its.length(); k++) {
                    JSONObject it = its.getJSONObject(k);
                    items.add(new Item(it.optString("title", it.getString("spoken")), it.getString("spoken"), it.optString("source", src.getString("name")),
                            it.optString("group", "i" + i + "-" + j + "-" + k), it.optString("summary", ""), it.optString("body", ""), strings(it.optJSONArray("say"))));
                }
                sources.add(new Source(src.getString("id"), src.getString("name"), src.optString("lang", "ko"), src.optBoolean("default", true), src.optInt("take", 3), items).site(src.optString("site", "")).country(src.optString("country", "")));
            }
            sections.add(new Section(s.getString("id"), s.getString("title"), s.optBoolean("perSourceLabel", false), s.optInt("limit", 99), sources));
        }
        JSONObject auto = b.optJSONObject("autoPlay");
        Set<String> audioIds = new HashSet<>();
        JSONObject audio = b.optJSONObject("audio");
        JSONArray ids = audio == null ? null : audio.optJSONArray("ids");
        if (ids != null) for (int i = 0; i < ids.length(); i++) audioIds.add(ids.getString(i));
        List<Lesson> lessons = new ArrayList<>();
        JSONObject ls = b.optJSONObject("lessons");
        JSONObject product = ls == null ? null : ls.optJSONObject("product");
        JSONArray eps = product == null ? null : product.optJSONArray("episodes");
        if (eps != null) for (int i = 0; i < eps.length(); i++) {
            JSONObject e = eps.getJSONObject(i);
            lessons.add(new Lesson(e.getInt("n"), e.getString("title"), strings(e.optJSONArray("lines"))));
        }
        JSONObject english = ls == null ? null : ls.optJSONObject("english");
        JSONArray eng = english == null ? null : english.optJSONArray("episodes");
        if (eng != null) for (int i = 0; i < eng.length(); i++) {
            JSONObject e = eng.getJSONObject(i);
            int n = e.getInt("n");
            Lesson l = null;
            for (Lesson x : lessons) if (x.n == n) l = x;
            JSONArray ls2 = e.optJSONArray("lines");
            if (l == null || ls2 == null) continue;
            for (int k = 0; k < ls2.length(); k++) {
                JSONObject o = ls2.getJSONObject(k);
                boolean en = o.has("en");
                String t = (en ? o.optString("en") : o.optString("ko")).trim();
                if (!t.isEmpty()) l.english.add(new Line(t, null, false, n, true, en, (int) Math.round(o.optDouble("pause", 0) * 1000)));
            }
        }
        return new Brief(b.getString("dateLabel"), auto == null || auto.optBoolean("play", true), sections, audioIds, audio == null ? "audio/" : audio.optString("base", "audio/"), lessons);
    }

    Set<String> defaults() {
        Set<String> ids = new LinkedHashSet<>();
        for (Section s : sections) for (Source src : s.sources) if (src.byDefault) ids.add(src.id);
        return ids;
    }

    /** enabled가 null이면 기본 언론사. 같은 사건(group)은 처음 나온 곳에서 한 번만, 언론사마다 take건, 섹션마다 limit건까지.
     *  언론사를 돌아가며 한 건씩 고르므로 고른 언론사는 기사가 있으면 모두 한 건 이상 나오고,
     *  고른 언론사가 limit보다 많으면 섹션은 고른 언론사 수만큼 늘어난다 (원고는 언론사 순서대로). js/brief.js와 같은 규칙 */
    List<Picked> select(Set<String> enabled) { return select(enabled, null); }

    /** order: 사람이 정한 섹션 순서(섹션 id). 거기 없는 섹션은 원래 순서대로 그 뒤에 */
    List<Picked> select(Set<String> enabled, List<String> order) {
        Set<String> on = enabled == null ? defaults() : enabled;
        Set<String> groups = new HashSet<>();
        List<Picked> out = new ArrayList<>();
        for (Section sec : ordered(order)) {
            List<Source> srcs = new ArrayList<>();
            for (Source src : sec.sources) if (on.contains(src.id)) srcs.add(src);
            if (srcs.isEmpty()) continue;
            int cap = Math.max(sec.limit, srcs.size());
            List<List<Item>> per = new ArrayList<>();
            int[] at = new int[srcs.size()];
            for (int k = 0; k < srcs.size(); k++) per.add(new ArrayList<>());
            int total = 0;
            for (boolean more = true; more && total < cap; ) {
                more = false;
                for (int k = 0; k < srcs.size() && total < cap; k++) {
                    Source src = srcs.get(k);
                    if (per.get(k).size() >= src.take) continue;
                    while (at[k] < src.items.size()) {
                        Item it = src.items.get(at[k]++);
                        if (!groups.add(it.group)) continue;
                        per.get(k).add(it);
                        total++;
                        more = true;
                        break;
                    }
                }
            }
            Picked p = new Picked(sec.id, sec.title, sec.perSourceLabel);
            for (List<Item> l : per) p.items.addAll(l);
            out.add(p);
        }
        return out;
    }

    /** 섹션을 사람이 정한 순서로. order가 비었으면 서버 순서 그대로 */
    List<Section> ordered(List<String> order) {
        if (order == null || order.isEmpty()) return sections;
        List<Section> out = new ArrayList<>();
        for (String id : order) for (Section s : sections) if (s.id.equals(id) && !out.contains(s)) out.add(s);
        for (Section s : sections) if (!out.contains(s)) out.add(s);
        return out;
    }

    /** 섹션 기사 수를 바꾼다 (그 섹션이 없으면 그대로) */
    void setLimit(String sectionId, int n) {
        for (Section s : sections) if (s.id.equals(sectionId) && n > 0) s.limit = n;
    }

    /** 섹션의 언론사 목록 (없으면 빈 목록) */
    List<Source> sourcesOf(String sectionId) {
        for (Section s : sections) if (s.id.equals(sectionId)) return s.sources;
        return Collections.emptyList();
    }

    /**
     * 키워드가 든 기사를 그 언론사의 앞으로 (기사 고르기 전에). where가 고른 섹션·언론사만.
     * 언론사마다 앞쪽 몇 건만 읽으므로, 키워드 기사가 뒤에 있어도 읽히게 된다
     */
    void prefer(java.util.function.BiPredicate<Section, Source> where, List<String> words) {
        if (words.isEmpty()) return;
        for (Section sec : sections) for (Source src : sec.sources) {
            if (!where.test(sec, src)) continue;
            List<Item> hit = new ArrayList<>(), rest = new ArrayList<>();
            for (Item it : src.items) (mentions(it, words) ? hit : rest).add(it);
            if (hit.isEmpty() || rest.isEmpty()) continue;
            src.items.clear();
            src.items.addAll(hit);
            src.items.addAll(rest);
        }
    }

    /** 제목·읽을 제목·요약에 낱말 가운데 하나라도 있는지 (대소문자 무시) */
    static boolean mentions(Item it, List<String> words) {
        String t = (it.title + " " + it.spoken + " " + it.summary).toLowerCase(java.util.Locale.ROOT);
        for (String w : words) if (!w.trim().isEmpty() && t.contains(w.trim().toLowerCase(java.util.Locale.ROOT))) return true;
        return false;
    }

    /** 폰이 직접 가져온 언론사를 그 섹션 끝에 붙인다 (없는 섹션이면 버린다) */
    boolean addSource(String sectionId, Source src) {
        for (Section s : sections) {
            if (!s.id.equals(sectionId)) continue;
            for (Source x : s.sources) if (x.id.equals(src.id)) return false;
            s.sources.add(src);
            return true;
        }
        return false;
    }

    /** 원고를 줄 단위로: 앱은 줄마다 읽고, 기사 줄에서 '자세히'를 받으면 그 기사 요약을 읽는다 */
    List<Line> lines(Set<String> enabled) { return lines(enabled, null); }

    List<Line> lines(Set<String> enabled, String today) { return linesOf(select(enabled), today); }

    /** picked: select로 고른 섹션(폰이 붙인 키워드 기사가 들어 있을 수 있다).
     *  today: 오늘 서울 날짜. 원고 날짜와 다르면(서버가 오늘 원고를 아직 못 만듦) 인사·맺음말은 오늘 날짜로, 어제 뉴스라고 알린다 */
    List<Line> linesOf(List<Picked> picked, String today) {
        boolean stale = today != null && !today.equals(dateLabel);
        String date = stale ? today : dateLabel;
        List<Picked> secs = picked;
        boolean any = false;
        for (Picked p : secs) any |= !p.items.isEmpty();
        List<Line> lines = new ArrayList<>();
        if (!any) {
            lines.add(new Line("좋은 아침입니다. " + date + "입니다. 오늘은 뉴스를 가져오지 못했습니다. 안전 운전하세요.", null));
            return lines;
        }
        lines.add(new Line(opening(date), null));
        if (stale) lines.add(new Line(STALE, null));
        for (int i = 0; i < secs.size(); i++) {
            Picked sec = secs.get(i);
            lines.add(new Line("", null));
            lines.add(new Line(intro(sec.title, i, secs.size()), null));
            if (sec.items.isEmpty()) { lines.add(new Line("오늘은 새로 들어온 소식이 없습니다.", null)); continue; }
            String last = null;
            for (Item it : sec.items) {
                if (sec.perSourceLabel && !it.source.equals(last)) lines.add(new Line(it.source + " 소식입니다.", null, it.local));
                last = it.source;
                lines.add(new Line(sentence(it.spoken), it));
            }
        }
        lines.add(new Line("", null));
        lines.add(new Line(closing(date), null));
        return lines;
    }

    Lesson lesson(int n) {
        for (Lesson l : lessons) if (l.n == n) return l;
        return null;
    }

    /** 제품 공부 한 회의 줄들 (첫 줄은 소개) */
    static List<Line> lessonLines(Lesson l) { return lessonLines(l, true, true); }

    /** 제품 공부(product)와 이어지는 농업영어(english) 가운데 켠 것만 */
    static List<Line> lessonLines(Lesson l, boolean product, boolean english) {
        List<Line> out = new ArrayList<>();
        if (product) {
            out.add(new Line(lessonIntro(l.n, l.title), null, false, l.n));
            for (String t : l.lines) out.add(new Line(t, null, false, l.n));
        }
        if (english && !l.english.isEmpty()) {
            if (!out.isEmpty()) out.add(new Line("", null, false, l.n));
            out.addAll(l.english);
        }
        return out;
    }

    static List<Line> withLesson(List<Line> news, Lesson l) { return withLesson(news, l, true, true); }

    /** 뉴스 원고의 맺음말 앞에 제품 공부 한 회(와 농업영어)를 넣는다 */
    static List<Line> withLesson(List<Line> news, Lesson l, boolean product, boolean english) {
        if (l == null || news.size() < 3) return news;
        List<Line> lesson = lessonLines(l, product, english);
        if (lesson.isEmpty()) return news;
        List<Line> out = new ArrayList<>(news.subList(0, news.size() - 2)); // 맨 끝은 빈 줄과 맺음말
        out.add(new Line("", null));
        out.addAll(lesson);
        out.addAll(news.subList(news.size() - 2, news.size()));
        return out;
    }

    String script(Set<String> enabled) {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines(enabled)) sb.append(l.text).append('\n');
        return sb.toString();
    }

    static String sentence(String s) {
        return s.matches(".*[.?!]$") ? s : s + ".";
    }

    /** 체크 목록에 쓰는 언론사 표: [섹션 제목, 언론사 이름, id, 번역 여부] */
    List<String[]> catalog() { return catalog(null); }

    /** 홈 화면 로고: 고른 언론사(enabled가 null이면 기본)의 누리집 주소, 섹션 순서대로 한 번씩. 주소를 모르는 곳(검색 모음)은 뺀다 */
    List<String> sites(Set<String> enabled, List<String> order) {
        Set<String> on = enabled == null ? defaults() : enabled;
        Set<String> out = new LinkedHashSet<>();
        for (Section s : ordered(order)) for (Source src : s.sources) if (on.contains(src.id) && !src.site.isEmpty()) out.add(src.site);
        return new ArrayList<>(out);
    }

    /** order: 사람이 정한 섹션 순서 */
    List<String[]> catalog(List<String> order) {
        List<String[]> rows = new ArrayList<>();
        for (Section s : ordered(order)) for (Source src : s.sources) rows.add(new String[] {s.title, src.name, src.id, "ko".equals(src.lang) ? "" : "번역"});
        return Collections.unmodifiableList(rows);
    }

    private static List<String> strings(JSONArray a) throws JSONException {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) if (!a.getString(i).trim().isEmpty()) out.add(a.getString(i).trim());
        return out;
    }
}
