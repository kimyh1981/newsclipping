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

    // 제품 공부·농업영어 안에서 넘기기. kind[i]: 1 제품 공부, 2 농업영어, 0 그 밖, -1 빈 줄

    /** 다음 문단(빈 줄이 아닌 줄). 코너 끝이면 코너 다음 줄 */
    public static int nextParagraph(int[] kind, int cur) {
        for (int i = cur + 1; i < kind.length; i++) if (kind[i] != -1) return i;
        return Math.max(0, kind.length - 1);
    }

    /** 같은 코너 안의 이전 문단. 코너 첫 줄이면 그 자리 */
    public static int prevParagraph(int[] kind, int cur) {
        int k = kind[cur];
        for (int i = cur - 1; i >= 0; i--) {
            if (kind[i] == -1) continue;
            return kind[i] == k ? i : cur;
        }
        return cur;
    }

    /** 제품 공부 중이면 이어지는 농업영어 첫 줄, 농업영어 중이거나 영어가 없으면 코너가 끝난 뒤 첫 줄 */
    public static int skipLesson(int[] kind, int cur) {
        int i = cur + 1;
        if (kind[cur] == 1) {
            while (i < kind.length && (kind[i] == 1 || kind[i] == -1)) i++;
            if (i < kind.length && kind[i] == 2) return i;
        }
        while (i < kind.length && (kind[i] == 2 || kind[i] == -1)) i++;
        return Math.min(i, Math.max(0, kind.length - 1));
    }

    /** 말 빠르기(보통 = 100) */
    public static final int[] RATES = {85, 100, 115, 130};
    public static final String[] RATE_LABELS = {"조금 느리게", "보통", "조금 빠르게", "빠르게"};

    public static String rateLabel(int rate) {
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rate) return RATE_LABELS[i];
        return rate + "%";
    }

    /**
     * 홈 화면 로고 n개를 w×h 칸에 같은 크기 정사각형으로 늘어놓을 때의 {한 칸 크기, 한 줄 개수}.
     * 가장 큰 크기(max)부터 줄여 가며 모두 들어가는 첫 크기를 고른다. min까지 줄여도 넘치면 min으로 (가장 작은 크기)
     */
    public static int[] logoGrid(int n, int w, int h, int gap, int max, int min) {
        if (n <= 0 || w <= 0 || h <= 0) return new int[] {0, 0};
        for (int s = max; s >= min; s--) {
            int cols = Math.max(1, Math.min(n, (w + gap) / (s + gap)));
            if (cols * s + (cols - 1) * gap > w) continue;
            int rows = (n + cols - 1) / cols;
            if (rows * s + (rows - 1) * gap <= h) return new int[] {s, cols};
        }
        return new int[] {min, Math.max(1, Math.min(n, (w + gap) / (min + gap)))};
    }
}
