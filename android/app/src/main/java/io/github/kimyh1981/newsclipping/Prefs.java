package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 설정: 차 블루투스, 재생 시간대, 평일만, 고른 언론사·섹션 순서·직접 넣은 언론사·농업 키워드, 목소리·빠르기, 오늘 이미 읽었는지. */
final class Prefs {
    static final int DEFAULT_START = 5 * 60; // 6시 전에 차에 타는 날도 읽게 (2026-10-13, 원고는 4시 40분에 만든다)
    static final int DEFAULT_END = 8 * 60;

    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("newsclipping", Context.MODE_PRIVATE);
    }

    boolean enabled() { return p.getBoolean("enabled", true); }
    void setEnabled(boolean v) { p.edit().putBoolean("enabled", v).apply(); }

    /** 비어 있으면 '오디오 기기면 모두'(차 오디오·헤드셋 등) */
    String carAddress() { return p.getString("carAddress", ""); }
    String carName() { return p.getString("carName", ""); }
    void setCar(String address, String name) { p.edit().putString("carAddress", address).putString("carName", name).apply(); }

    int start() { return p.getInt("start", DEFAULT_START); }
    int end() { return p.getInt("end", DEFAULT_END); }
    void setWindow(int start, int end) { p.edit().putInt("start", start).putInt("end", end).apply(); }

    boolean weekdaysOnly() { return p.getBoolean("weekdaysOnly", true); }
    void setWeekdaysOnly(boolean v) { p.edit().putBoolean("weekdaysOnly", v).apply(); }

    /** 고른 언론사 id. 한 번도 고르지 않았으면 null: 서버가 정한 기본 언론사를 쓴다 */
    Set<String> sources() { return p.contains("sources") ? new HashSet<>(p.getStringSet("sources", new HashSet<>())) : null; }
    void setSources(Set<String> ids) { p.edit().putStringSet("sources", new HashSet<>(ids)).apply(); }
    void resetSources() { p.edit().remove("sources").apply(); }

    /** 섹션 재생 순서(섹션 id). 비어 있으면 서버 순서 */
    List<String> order() { return split(p.getString("order", "")); }

    /** 홈 화면 로고로 지난번에 보인 언론사 누리집 주소들 (앱을 열자마자 보여 주려고) */
    List<String> logoSites() { return split(p.getString("logoSites", "")); }
    void setLogoSites(List<String> sites) { p.edit().putString("logoSites", String.join(",", sites)).apply(); }
    void setOrder(List<String> ids) { p.edit().putString("order", String.join(",", ids)).apply(); }

    /** 주소를 직접 넣은 언론사 (Extras.Custom 목록 JSON) */
    List<Extras.Custom> custom() { return Extras.parseCustom(p.getString("custom", "")); }
    void setCustom(List<Extras.Custom> list) { p.edit().putString("custom", Extras.customJson(list)).apply(); }

    /** 농업 키워드: 농업 헤드라인에 그 낱말 기사가 없으면 한 건 더 찾아 읽는다 */
    List<String> keywords() { return split(p.getString("keywords", "")); }
    void setKeywords(List<String> words) { p.edit().putString("keywords", String.join(",", words)).apply(); }

    // ---- 제품 공부 진도 ----

    /** 평일 뉴스 뒤에 제품 공부 한 회를 읽는다 */
    boolean lessonOn() { return p.getBoolean("lessonOn", true); }
    void setLessonOn(boolean v) { p.edit().putBoolean("lessonOn", v).apply(); }
    /** 제품 공부 뒤에 같은 회의 농업영어 */
    boolean englishOn() { return p.getBoolean("englishOn", true); }
    void setEnglishOn(boolean v) { p.edit().putBoolean("englishOn", v).apply(); }

    /** 이번 바퀴에서 끝까지 들은 회 → 들은 날 */
    java.util.Map<Integer, String> lessonDone() { return Lessons.parseDone(p.getString("lessonDone", "")); }
    int lessonRound() { return p.getInt("lessonRound", 1); }

    void markLessonDone(int n) {
        java.util.Map<Integer, String> done = lessonDone();
        if (!done.containsKey(n)) done.put(n, today());
        p.edit().putString("lessonDone", Lessons.doneString(done)).apply();
    }

    /** 다시 듣기로 표시한 회 (다음 바퀴에서 먼저) */
    List<Integer> lessonStars() {
        List<Integer> out = new ArrayList<>();
        for (String x : split(p.getString("lessonStars", ""))) { try { out.add(Integer.parseInt(x)); } catch (NumberFormatException ignored) { } }
        return out;
    }

    void toggleStar(int n) {
        List<Integer> stars = lessonStars();
        if (!stars.remove(Integer.valueOf(n))) stars.add(n);
        List<String> s = new ArrayList<>();
        for (int x : stars) s.add(String.valueOf(x));
        p.edit().putString("lessonStars", String.join(",", s)).apply();
    }

    /** 오늘 들을 회: 오늘 이미 정했으면 그 회, 아니면 이번 바퀴의 다음 회(다 들었으면 다음 바퀴의 첫 회).
     *  assign이면 오늘 회로 정해 저장한다 (바퀴가 끝났으면 다음 바퀴로 넘긴다). 회차가 없으면 -1 */
    int lessonForToday(List<Integer> all, boolean assign) {
        if (all.isEmpty()) return -1;
        String t = p.getString("lessonToday", "");
        if (t.startsWith(today() + ":")) {
            try { int n = Integer.parseInt(t.substring(11)); if (all.contains(n)) return n; } catch (NumberFormatException ignored) { }
        }
        int round = lessonRound();
        int n = Lessons.next(Lessons.order(all, lessonStars(), round), lessonDone());
        if (n < 0) {
            n = Lessons.order(all, lessonStars(), round + 1).get(0);
            if (assign) p.edit().putInt("lessonRound", round + 1).putString("lessonDone", "").apply();
        }
        if (assign) p.edit().putString("lessonToday", today() + ":" + n).apply();
        return n;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String x : s.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    /** 고른 음성 이름. 비어 있으면 자동(가장 자연스러운 음성) */
    String voice() { return p.getString("voice", ""); }
    void setVoice(String name) { p.edit().putString("voice", name).apply(); }

    /** 말 빠르기, 보통 = 100 */
    int rate() { return p.getInt("rate", 100); }
    void setRate(int rate) { p.edit().putInt("rate", rate).apply(); }

    /** 차에서 자동으로 읽기 시작하면 자막 화면(지금 읽는 헤드라인, 터치하면 전체 듣기)을 띄운다 */
    boolean captions() { return p.getBoolean("captions", true); }
    /** 새 버전 알림을 띄운 빌드 번호 (같은 버전은 한 번만 알린다) */
    int updateNotified() { return p.getInt("updateNotified", 0); }
    void setUpdateNotified(int code) { p.edit().putInt("updateNotified", code).apply(); }
    void setCaptions(boolean v) { p.edit().putBoolean("captions", v).apply(); }

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    boolean playedToday() { return today().equals(p.getString("lastPlayed", "")); }
    void markPlayed() { p.edit().putString("lastPlayed", today()).apply(); }
    String lastPlayed() { return p.getString("lastPlayed", ""); }

    /** 마지막으로 받은 차·이어폰 버튼과 시각 (앱 화면 맨 아래에 보인다) */
    String lastButton() { return p.getString("lastButton", ""); }
    void noteButton(String key) { p.edit().putString("lastButton", new SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(new Date()) + " " + key).apply(); }

    /** 오늘 멈추거나 끈 기사 제목: 다시 들으면 거기서 시작한다. 날이 바뀌었거나 끝까지 들었으면 "" */
    String resumeTitle() { return today().equals(p.getString("resumeDay", "")) ? p.getString("resumeTitle", "") : ""; }
    void setResumeTitle(String title) { p.edit().putString("resumeTitle", title).putString("resumeDay", today()).apply(); }
}
