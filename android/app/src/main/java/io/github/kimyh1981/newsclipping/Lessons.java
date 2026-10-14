package io.github.kimyh1981.newsclipping;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 제품 공부 진도: 평일 뉴스 뒤에 한 회씩, 끝까지 들은 회는 완료.
 * 첫 바퀴는 1회부터 차례로, 다 들으면 두 번째 바퀴는 '다시 듣기'로 표시한 회부터.
 * 같은 날 다시 들으면 그날 회를 다시 읽는다 (다음 회로 넘어가지 않는다).
 */
final class Lessons {
    private Lessons() {}

    /** 이번 바퀴의 순서: 첫 바퀴는 번호 순, 다음 바퀴부터는 다시 듣기 표시한 회를 앞으로 */
    static List<Integer> order(List<Integer> all, List<Integer> starred, int round) {
        List<Integer> out = new ArrayList<>();
        if (round > 1) for (int n : all) if (starred.contains(n)) out.add(n);
        for (int n : all) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** 이번 바퀴에서 아직 안 들은 첫 회, 다 들었으면 -1 */
    static int next(List<Integer> order, Map<Integer, String> done) {
        for (int n : order) if (!done.containsKey(n)) return n;
        return -1;
    }

    /** 오늘(ymd)부터 평일마다 하나씩, 남은 회의 예정 날짜. first는 오늘 회(이미 정해졌으면) */
    static Map<Integer, String> schedule(List<Integer> order, Map<Integer, String> done, String today) {
        Map<Integer, String> out = new LinkedHashMap<>();
        String day = weekday(today, 0);
        for (int n : order) {
            if (done.containsKey(n)) continue;
            out.put(n, day);
            day = weekday(day, 1);
        }
        return out;
    }

    /** ymd에서 k번째 다음 평일 (k=0이면 ymd가 평일이면 그날, 주말이면 다음 월요일) */
    static String weekday(String ymd, int k) {
        Calendar c = Calendar.getInstance(Locale.ROOT);
        try { c.setTime(fmt().parse(ymd)); } catch (ParseException e) { return ymd; }
        while (isWeekend(c)) c.add(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < k; i++) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            while (isWeekend(c)) c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return fmt().format(c.getTime());
    }

    private static boolean isWeekend(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    private static SimpleDateFormat fmt() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); }

    /** '2026-10-15' → '10/15 (목)' */
    static String label(String ymd) {
        Calendar c = Calendar.getInstance(Locale.ROOT);
        try { c.setTime(fmt().parse(ymd)); } catch (ParseException e) { return ymd; }
        String[] days = {"일", "월", "화", "수", "목", "금", "토"};
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH) + " (" + days[c.get(Calendar.DAY_OF_WEEK) - 1] + ")";
    }

    // 저장 모양: "3=2026-10-15,4=2026-10-16"
    static Map<Integer, String> parseDone(String s) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (String x : s.split(",")) {
            int eq = x.indexOf('=');
            if (eq <= 0) continue;
            try { out.put(Integer.parseInt(x.substring(0, eq).trim()), x.substring(eq + 1).trim()); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    static String doneString(Map<Integer, String> done) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : done.entrySet()) sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append('=').append(e.getValue());
        return sb.toString();
    }

    static List<Integer> numbers(List<Brief.Lesson> lessons) {
        List<Integer> out = new ArrayList<>();
        for (Brief.Lesson l : lessons) out.add(l.n);
        return out;
    }
}
