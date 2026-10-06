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
        final String title, spoken, source, group, summary;
        Item(String title, String spoken, String source, String group, String summary) {
            this.title = title; this.spoken = spoken; this.source = source; this.group = group; this.summary = summary;
        }
    }

    /** 원고 한 줄. 기사 헤드라인 줄이면 item이 있다 ('자세히'에서 요약을 읽는다). 빈 줄은 잠깐 쉰다 */
    static final class Line {
        final String text;
        final Item item;
        Line(String text, Item item) { this.text = text; this.item = item; }
    }

    static final class Source {
        final String id, name, lang;
        final boolean byDefault;
        final int take;
        final List<Item> items;
        Source(String id, String name, String lang, boolean byDefault, int take, List<Item> items) {
            this.id = id; this.name = name; this.lang = lang; this.byDefault = byDefault; this.take = take; this.items = items;
        }
    }

    static final class Section {
        final String id, title;
        final boolean perSourceLabel;
        final int limit;
        final List<Source> sources;
        Section(String id, String title, boolean perSourceLabel, int limit, List<Source> sources) {
            this.id = id; this.title = title; this.perSourceLabel = perSourceLabel; this.limit = limit; this.sources = sources;
        }
    }

    /** 고른 언론사로 추린 섹션 */
    static final class Picked {
        final String title;
        final boolean perSourceLabel;
        final List<Item> items = new ArrayList<>();
        Picked(String title, boolean perSourceLabel) { this.title = title; this.perSourceLabel = perSourceLabel; }
    }

    private static final String[] ORDINAL = {"먼저", "다음은", "이어서", "마지막으로"};

    final String dateLabel;
    /** 서버가 정한 오늘 자동 재생 여부: 주말·공휴일이면 false */
    final boolean playToday;
    final List<Section> sections;

    private Brief(String dateLabel, boolean playToday, List<Section> sections) {
        this.dateLabel = dateLabel; this.playToday = playToday; this.sections = sections;
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
                            it.optString("group", "i" + i + "-" + j + "-" + k), it.optString("summary", "")));
                }
                sources.add(new Source(src.getString("id"), src.getString("name"), src.optString("lang", "ko"), src.optBoolean("default", true), src.optInt("take", 3), items));
            }
            sections.add(new Section(s.getString("id"), s.getString("title"), s.optBoolean("perSourceLabel", false), s.optInt("limit", 99), sources));
        }
        JSONObject auto = b.optJSONObject("autoPlay");
        return new Brief(b.getString("dateLabel"), auto == null || auto.optBoolean("play", true), sections);
    }

    Set<String> defaults() {
        Set<String> ids = new LinkedHashSet<>();
        for (Section s : sections) for (Source src : s.sources) if (src.byDefault) ids.add(src.id);
        return ids;
    }

    /** enabled가 null이면 기본 언론사. 같은 사건(group)은 처음 나온 곳에서 한 번만, 언론사마다 take건, 섹션마다 limit건까지 */
    List<Picked> select(Set<String> enabled) {
        Set<String> on = enabled == null ? defaults() : enabled;
        Set<String> groups = new HashSet<>();
        List<Picked> out = new ArrayList<>();
        for (Section sec : sections) {
            Picked p = null;
            for (Source src : sec.sources) {
                if (!on.contains(src.id)) continue;
                if (p == null) p = new Picked(sec.title, sec.perSourceLabel);
                int taken = 0;
                for (Item it : src.items) {
                    if (p.items.size() >= sec.limit || taken >= src.take) break;
                    if (!groups.add(it.group)) continue;
                    p.items.add(it);
                    taken++;
                }
            }
            if (p != null) out.add(p);
        }
        return out;
    }

    /** 원고를 줄 단위로: 앱은 줄마다 읽고, 기사 줄에서 '자세히'를 받으면 그 기사 요약을 읽는다 */
    List<Line> lines(Set<String> enabled) {
        List<Picked> secs = select(enabled);
        boolean any = false;
        for (Picked p : secs) any |= !p.items.isEmpty();
        List<Line> lines = new ArrayList<>();
        if (!any) {
            lines.add(new Line("좋은 아침입니다. " + dateLabel + "입니다. 오늘은 뉴스를 가져오지 못했습니다. 안전 운전하세요.", null));
            return lines;
        }
        lines.add(new Line("좋은 아침입니다. " + dateLabel + " 아침 뉴스 브리핑입니다.", null));
        for (int i = 0; i < secs.size(); i++) {
            Picked sec = secs.get(i);
            String lead = i == secs.size() - 1 && secs.size() > 1 ? ORDINAL[3] : ORDINAL[Math.min(i, 2)];
            lines.add(new Line("", null));
            lines.add(new Line(lead + " " + sec.title + "입니다.", null));
            if (sec.items.isEmpty()) { lines.add(new Line("오늘은 새 소식이 없습니다.", null)); continue; }
            String last = null;
            for (Item it : sec.items) {
                if (sec.perSourceLabel && !it.source.equals(last)) lines.add(new Line(it.source + ".", null));
                last = it.source;
                lines.add(new Line(sentence(it.spoken), it));
            }
        }
        lines.add(new Line("", null));
        lines.add(new Line("이상으로 오늘 아침 브리핑을 마칩니다. 오늘도 안전 운전하세요.", null));
        return lines;
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
    List<String[]> catalog() {
        List<String[]> rows = new ArrayList<>();
        for (Section s : sections) for (Source src : s.sources) rows.add(new String[] {s.title, src.name, src.id, "ko".equals(src.lang) ? "" : "번역"});
        return Collections.unmodifiableList(rows);
    }
}
