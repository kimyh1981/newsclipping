package io.github.kimyh1981.newsclipping;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 앱이 갑자기 꺼지면 다음에 열 때 그 이유를 보여 준다. 주인 폰에서 F1을 켜자 앱이 꺼졌는데
 * 폰 기록을 볼 방법이 없어서 넣었다 (2026-10-17). 자바 오류는 직접 적어 두고,
 * 엔진(네이티브) 오류·메모리 부족은 안드로이드 11부터 시스템이 남기는 '지난번 종료 이유'를 읽는다.
 */
final class Crash {
    private Crash() {}

    private static final String FILE = "crash.txt";
    private static boolean installed;

    /** 자바 오류로 꺼질 때 내용을 파일에 적고, 원래 처리(앱 닫기)로 넘긴다 */
    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            EventLog.note(c, "앱 오류로 꺼짐: " + e); // 사건 기록에도 남긴다
            try (FileOutputStream o = new FileOutputStream(f)) {
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                o.write((System.currentTimeMillis() + "\n" + t.getName() + "\n" + w).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** 지난번 앱이 꺼진 까닭(시스템 기록)을 사건 기록에 한 번 남긴다: 오류·메모리 부족·사용자 종료·시스템 종료 등 (안드로이드 11부터) */
    static void noteLastExit(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            android.content.SharedPreferences p = c.getSharedPreferences("newsclipping", Context.MODE_PRIVATE);
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 1);
            if (list.isEmpty()) return;
            ApplicationExitInfo x = list.get(0);
            if (x.getTimestamp() <= p.getLong("exitLogged", 0)) return;
            p.edit().putLong("exitLogged", x.getTimestamp()).apply();
            String why = reason(x.getReason());
            EventLog.note(c, "지난번 종료(" + new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date(x.getTimestamp())) + "): "
                    + (why != null ? why : "보통 종료 " + x.getReason()) + (x.getDescription() != null ? " · " + x.getDescription() : "")
                    + String.format(Locale.ROOT, " · 메모리 %,dMB", x.getPss() / 1024));
        } catch (Exception ignored) { }
    }

    /**
     * 지난번 F1 엔진을 띄우거나 소리를 만들다 앱이 비정상으로 꺼졌으면 F1을 끄고 true (화면 알림은 한 번).
     * 앱이 새로 뜬 뒤 F1을 쓰기 전에만 판단한다. 그냥 닫혀서 남은 표시면 지우기만 한다
     */
    static synchronized boolean f1Crashed(Context c, Prefs p) {
        if (!F1Voice.touched && p.f1Busy()) {
            p.setF1Busy(false);
            if (abnormalLastExit(c)) { p.setF1On(false); p.setF1AutoOff(true); }
        }
        if (!p.f1AutoOff()) return false;
        if (c instanceof android.app.Activity) p.setF1AutoOff(false); // 화면에서 알렸다
        return true;
    }

    /** 지난번 종료가 오류·메모리 부족이었나 (안드로이드 10 이하는 자바 오류만 안다) */
    static boolean abnormalLastExit(Context c) {
        if (new File(c.getFilesDir(), FILE).exists()) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 1);
            return !list.isEmpty() && reason(list.get(0).getReason()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 알릴 만한 종료 이유 (ApplicationExitInfo.REASON_*). 정상 종료면 null */
    static String reason(int r) {
        switch (r) {
            case 2: return "시스템이 강제로 닫음";             // REASON_SIGNALED
            case 3: return "메모리가 모자라 시스템이 닫음";    // REASON_LOW_MEMORY
            case 4: return "앱 오류로 꺼짐 (자바)";            // REASON_CRASH
            case 5: return "엔진 오류로 꺼짐 (네이티브)";      // REASON_CRASH_NATIVE
            case 6: return "응답이 없어 닫힘";                 // REASON_ANR
            case 9: return "자원을 너무 많이 써서 닫힘";       // REASON_EXCESSIVE_RESOURCE_USAGE
            default: return null;
        }
    }

    private static final Pattern CLUE = Pattern.compile("(?i).*(\\bsig[a-z]{2,}|abort|\\.so\\b|^#\\d\\d|exception|error|outofmemory|onnx|fatal|cause).*");

    /** 오류 기록(바이너리일 수도 있다)에서 읽을 수 있는 단서 줄만 골라 max줄까지 */
    static List<String> hints(byte[] trace, int max) {
        Set<String> out = new LinkedHashSet<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= trace.length && out.size() < max; i++) {
            int ch = i < trace.length ? trace[i] & 0xff : 0;
            if (ch >= 0x20 && ch < 0x7f) { run.append((char) ch); continue; }
            String s = run.toString().trim();
            run.setLength(0);
            if (s.length() >= 4 && CLUE.matcher(s).matches()) out.add(s.length() > 160 ? s.substring(0, 160) : s);
        }
        return new ArrayList<>(out);
    }

    /** 지난번에 비정상으로 꺼졌으면 그 이유(처음 한 번만), 아니면 null. seen: 이미 보여 준 시각 */
    static String lastReport(Context c, Prefs prefs) {
        long seen = prefs.crashSeen();
        StringBuilder msg = new StringBuilder();
        long when = 0;
        File f = new File(c.getFilesDir(), FILE);
        if (f.exists()) {
            try {
                String[] t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", 3);
                when = Long.parseLong(t[0].trim());
                if (when > seen) msg.append("앱 오류로 꺼짐 (자바, ").append(t.length > 1 ? t[1] : "").append(")\n\n")
                        .append(String.join("\n", stackLines(t.length > 2 ? t[2] : "")));
            } catch (Exception ignored) { }
            f.delete();
        }
        if (msg.length() == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 1);
                if (!list.isEmpty()) {
                    ApplicationExitInfo x = list.get(0);
                    String why = reason(x.getReason());
                    if (why != null && x.getTimestamp() > seen) {
                        when = x.getTimestamp();
                        msg.append(why);
                        if (x.getDescription() != null) msg.append("\n").append(x.getDescription());
                        msg.append(String.format(Locale.ROOT, "\n메모리 %,dMB", x.getPss() / 1024));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try (InputStream in = x.getTraceInputStream()) {
                                if (in != null) {
                                    byte[] all = readUpTo(in, 2_000_000);
                                    List<String> h = hints(all, 12);
                                    if (!h.isEmpty()) msg.append("\n\n").append(String.join("\n", h));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        if (msg.length() == 0) return null;
        prefs.setCrashSeen(Math.max(when, seen));
        String at = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(new Date(when));
        return at + "\n" + msg + "\n\n버전 " + BuildConfig.VERSION_NAME;
    }

    /** 오류 기록에서 보여 줄 줄: 맨 앞 4줄과, 진짜 원인('Caused by')부터 6줄 (예전엔 앞 12줄만이라 원인이 잘렸다, 2026-10-18) */
    static List<String> stackLines(String s) {
        List<String> all = firstLines(s, 400), out = new ArrayList<>(all.subList(0, Math.min(4, all.size())));
        int cause = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).startsWith("Caused by")) cause = i; // 가장 안쪽 원인
        if (cause >= 0) { out.add("…"); out.addAll(all.subList(cause, Math.min(all.size(), cause + 6))); }
        return out;
    }

    private static List<String> firstLines(String s, int n) {
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) { if (out.size() >= n) break; out.add(l.trim()); }
        return out;
    }

    private static byte[] readUpTo(InputStream in, int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; o.size() < max && (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        return o.toByteArray();
    }
}
