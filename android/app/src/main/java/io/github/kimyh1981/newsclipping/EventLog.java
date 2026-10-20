package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 차에서 무슨 일이 있었는지(소리 차례·내비·음성 비서·멈춤·이어 읽기·재생 시작/오류·버튼) 시각과 함께 남긴다.
 * 기기에서만 알 수 있는 일을 짐작 대신 기록으로 고치려고 (주인 요청 2026-10-21). 최근 400줄, 앱 밖으로는 주인이 '기록 보내기'를 눌러야만 나간다
 */
final class EventLog {
    private static final String FILE = "events.log";
    private static final int MAX = 400;
    private static final List<String> lines = new ArrayList<>();
    private static boolean loaded;

    private EventLog() { }

    static synchronized void note(Context c, String event) {
        load(c);
        String line = new SimpleDateFormat("M/d HH:mm:ss.SSS", Locale.KOREA).format(new Date()) + " " + event;
        Log.i("newsclipping", "기록: " + event);
        lines.add(line);
        while (lines.size() > MAX) lines.remove(0);
        try {
            Files.write(new File(c.getFilesDir(), FILE).toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    /** 새것이 위로 n줄 */
    static synchronized List<String> recent(Context c, int n) {
        load(c);
        List<String> out = new ArrayList<>(lines.subList(Math.max(0, lines.size() - n), lines.size()));
        Collections.reverse(out);
        return out;
    }

    /** 전체 (오래된 것이 위, 보내기용) */
    static synchronized String all(Context c) {
        load(c);
        return String.join("\n", lines);
    }

    private static void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE);
            if (f.exists()) lines.addAll(Arrays.asList(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")));
        } catch (Exception ignored) { }
    }
}
