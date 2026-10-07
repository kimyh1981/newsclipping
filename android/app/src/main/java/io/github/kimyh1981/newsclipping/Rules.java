package io.github.kimyh1981.newsclipping;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 안드로이드에 기대지 않는 판단 규칙 (단위 테스트 대상). */
public final class Rules {
    private Rules() {}

    /** 자동 재생 시간대인가: dayOfWeek는 Calendar.SUNDAY~SATURDAY, 분은 하루 중 분(0~1439). 끝은 포함하지 않는다. */
    public static boolean inWindow(int dayOfWeek, int minuteOfDay, int startMinute, int endMinute, boolean weekdaysOnly) {
        if (weekdaysOnly && (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)) return false;
        if (startMinute == endMinute) return true; // 같은 시각이면 하루 종일
        if (startMinute < endMinute) return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        return minuteOfDay >= startMinute || minuteOfDay < endMinute; // 자정을 넘기는 구간
    }

    /** "06:00" */
    public static String hhmm(int minuteOfDay) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /**
     * 원고를 음성 엔진에 넘길 조각으로 나눈다. 빈 줄은 ""(잠깐 쉼)으로 남기고,
     * 엔진 한도(max자)를 넘는 줄은 문장 끝(". ")이나 쉼표에서 자른다.
     */
    public static List<String> chunks(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        boolean lastBlank = true;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (!lastBlank) out.add("");
                lastBlank = true;
                continue;
            }
            lastBlank = false;
            while (line.length() > max) {
                int cut = Math.max(line.lastIndexOf(". ", max), line.lastIndexOf(", ", max));
                if (cut <= 0) cut = max - 1;
                out.add(line.substring(0, cut + 1).trim());
                line = line.substring(cut + 1).trim();
            }
            if (!line.isEmpty()) out.add(line);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /**
     * 자동으로 고를 음성의 점수: 받지 않은(설치 안 된) 음성은 고르지 않고, 품질이 높을수록,
     * 품질이 같으면 인터넷이 필요 없는 음성(터널·지하에서도 끊기지 않음)을 먼저 고른다.
     */
    public static int voiceScore(int quality, boolean needsNetwork, boolean notInstalled) {
        if (notInstalled) return Integer.MIN_VALUE;
        return quality * 2 - (needsNetwork ? 1 : 0);
    }

    /** 다음 기사 줄: cur 뒤의 첫 기사 줄, 없으면 lines(끝: 맺음말로) */
    public static int nextItem(boolean[] isItem, int cur) {
        for (int i = cur + 1; i < isItem.length; i++) if (isItem[i]) return i;
        return isItem.length;
    }

    /** 이전 기사 줄: cur 앞의 마지막 기사 줄, 없으면 0(처음부터) */
    public static int prevItem(boolean[] isItem, int cur) {
        for (int i = Math.min(cur, isItem.length) - 1; i >= 0; i--) if (isItem[i]) return i;
        return 0;
    }

    /** 말 빠르기(보통 = 100) */
    public static final int[] RATES = {85, 100, 115, 130};
    public static final String[] RATE_LABELS = {"조금 느리게", "보통", "조금 빠르게", "빠르게"};

    public static String rateLabel(int rate) {
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rate) return RATE_LABELS[i];
        return rate + "%";
    }
}
