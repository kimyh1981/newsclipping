package io.github.kimyh1981.newsclipping;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 안드로이드에 기대지 않는 판단 규칙 (단위 테스트 대상). */
public final class Rules {
    private Rules() {}

    /** 자동 재생 시간대인가: dayOfWeek는 Calendar.SUNDAY~SATURDAY, 분은 하루 중 분(0~1439). 끝은 포함하지 않는다. */
    /** 퇴근길 자동 재생 시간: 오후 6시~8시 (주인 요청 2026-10-17: 아침은 그날 1면 헤드라인, 퇴근길은 그날 많이 본 기사) */
    public static final int EVENING_START = 18 * 60, EVENING_END = 20 * 60;
    public static final int NONE = 0, MORNING = 1, EVENING = 2;

    /** 차에 탔을 때 자동으로 읽을지: 아침 창이면 MORNING, 퇴근길 창이면 EVENING, 아니면 NONE. 판마다 하루 한 번 */
    public static int autoPlay(int dayOfWeek, int minuteOfDay, int startMinute, int endMinute, boolean weekdaysOnly,
                               boolean eveningOn, boolean playedMorning, boolean playedEvening) {
        if (!playedMorning && inWindow(dayOfWeek, minuteOfDay, startMinute, endMinute, weekdaysOnly)) return MORNING;
        if (eveningOn && !playedEvening && inWindow(dayOfWeek, minuteOfDay, EVENING_START, EVENING_END, weekdaysOnly)) return EVENING;
        return NONE;
    }

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

    /** 직접 넣은 기사 수: 1~50건. 비었거나 숫자가 아니면 그전 값 */
    static int clampCount(String typed, int before) {
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(typed.trim())));
        } catch (NumberFormatException e) {
            return before;
        }
    }

    /** 음성 비서(제미나이 등)가 소리를 가져간 뒤 1초마다 보는 판단 */
    static final int ASSIST_WAIT = 0, ASSIST_QUIET = 1, ASSIST_GIVE_UP = 2;
    /** 마이크를 쓰는 앱이 이 안에 안 보이면 음성 비서가 아니라 음악 앱 같은 것: 이어 듣기를 누를 때까지 기다린다 */
    static final long ASSIST_MIC_MS = 15000;

    /**
     * 소리 차례를 돌려받았을 때(포커스 GAIN) 저절로 이어 읽을지.
     * 차에서(블루투스·안드로이드 오토) 듣던 중이거나, 음성 비서가 잠깐 가져갔거나, 내비 안내로 멈췄으면 이어 읽는다.
     * 예전엔 '블루투스로 소리가 나가는지'만 봐서 안드로이드 오토(USB·무선)에서는 내비 안내 뒤 멈춘 채로 남았다 (2026-10-20 퇴근길)
     */
    static boolean resumeOnGain(boolean paused, boolean autoResume, long pausedForMs, long limitMs, boolean inCar, boolean assistant, boolean navPaused) {
        return paused && autoResume && pausedForMs < limitMs && (inCar || assistant || navPaused);
    }

    /**
     * 멈춘 뒤 마이크를 쓰는 앱이 보였고(음성 비서), 이제 마이크도 다른 소리도 멎었으면 QUIET (두 번 이어지면 이어 읽는다).
     * 통화가 끼었거나, 오래 지났거나, 마이크를 쓰는 앱이 끝내 안 보이면 GIVE_UP
     */
    static int afterAssistant(boolean sawMic, boolean micNow, boolean otherSound, boolean call, long sincePauseMs, long limitMs) {
        if (call || sincePauseMs > limitMs) return ASSIST_GIVE_UP;
        if (!sawMic && !micNow && sincePauseMs > ASSIST_MIC_MS) return ASSIST_GIVE_UP;
        if (micNow || otherSound || !sawMic) return ASSIST_WAIT;
        return ASSIST_QUIET;
    }

    /** 원고를 만든 시각(ISO, briefing.json의 generatedAt) → '오늘 오후 2:56' · '어제 오전 5:10' · '10월 15일 오후 4:22' (서울 시간). 못 읽으면 "" */
    public static String briefTime(String iso, long now) {
        try {
            java.time.ZoneId seoul = java.time.ZoneId.of("Asia/Seoul");
            java.time.ZonedDateTime t = java.time.Instant.parse(iso).atZone(seoul);
            java.time.LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(seoul).toLocalDate();
            String day = t.toLocalDate().equals(today) ? "오늘" : t.toLocalDate().equals(today.minusDays(1)) ? "어제" : t.getMonthValue() + "월 " + t.getDayOfMonth() + "일";
            int h = t.getHour();
            return day + " " + (h < 12 ? "오전 " : "오후 ") + (h % 12 == 0 ? 12 : h % 12) + ":" + String.format(java.util.Locale.ROOT, "%02d", t.getMinute());
        } catch (Exception e) {
            return "";
        }
    }

    /** 초 → 동영상처럼 '6:12', 한 시간 넘으면 '1:02:05' (홈 화면 진행바 아래) */
    public static String clock(float seconds) {
        int t = Math.max(0, (int) seconds), h = t / 3600, m = t % 3600 / 60, s = t % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.ROOT, "%d:%02d", m, s);
    }
}
