package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 설정: 차 블루투스, 재생 시간대, 평일만, 고른 언론사, 목소리·빠르기, 오늘 이미 읽었는지. */
final class Prefs {
    static final int DEFAULT_START = 6 * 60;
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

    /** 고른 음성 이름. 비어 있으면 자동(가장 자연스러운 음성) */
    String voice() { return p.getString("voice", ""); }
    void setVoice(String name) { p.edit().putString("voice", name).apply(); }

    /** 말 빠르기, 보통 = 100 */
    int rate() { return p.getInt("rate", 100); }
    void setRate(int rate) { p.edit().putInt("rate", rate).apply(); }

    /** 차 블루투스에서 'ㅅ·ㅆ' 소리(치찰음)가 날카롭지 않게 고음을 줄인다 */
    boolean soften() { return p.getBoolean("soften", true); }
    void setSoften(boolean v) { p.edit().putBoolean("soften", v).apply(); }

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    boolean playedToday() { return today().equals(p.getString("lastPlayed", "")); }
    void markPlayed() { p.edit().putString("lastPlayed", today()).apply(); }
    String lastPlayed() { return p.getString("lastPlayed", ""); }
}
