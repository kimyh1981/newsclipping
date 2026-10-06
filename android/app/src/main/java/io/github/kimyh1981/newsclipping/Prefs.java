package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 설정: 차 블루투스, 재생 시간대, 평일만, 오늘 이미 읽었는지. */
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

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }
    boolean playedToday() { return today().equals(p.getString("lastPlayed", "")); }
    void markPlayed() { p.edit().putString("lastPlayed", today()).apply(); }
    String lastPlayed() { return p.getString("lastPlayed", ""); }
}
