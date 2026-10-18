package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/** 앱이 꺼진 이유 알려 주기 (F1을 켜자 앱이 꺼졌다, 2026-10-16 주인 폰) */
public class CrashTest {
    @Test public void onlyAbnormalExitsAreReported() {
        assertEquals("엔진 오류로 꺼짐 (네이티브)", Crash.reason(5));
        assertEquals("앱 오류로 꺼짐 (자바)", Crash.reason(4));
        assertEquals("메모리가 모자라 시스템이 닫음", Crash.reason(3));
        assertNull(Crash.reason(1)); // 스스로 끝냄
        assertNull(Crash.reason(10)); // 사용자가 닫음
    }

    @Test public void hintsPickReadableClueLinesFromBinaryTrace() throws Exception {
        // 네이티브 오류 기록(tombstone)은 바이너리라 글자 덩이만 골라낸다
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(new byte[] {0, 1, 2, (byte) 0x9f});
        b.write("SIGSEGV".getBytes(StandardCharsets.US_ASCII));
        b.write(new byte[] {0, 7});
        b.write("zzz".getBytes(StandardCharsets.US_ASCII));
        b.write(new byte[] {0});
        b.write("/data/app/lib/arm64/libonnxruntime.so".getBytes(StandardCharsets.US_ASCII));
        b.write(new byte[] {0, 0});
        b.write("/data/app/lib/arm64/libonnxruntime.so".getBytes(StandardCharsets.US_ASCII)); // 같은 줄은 한 번만
        b.write(new byte[] {3});
        b.write("plain words here".getBytes(StandardCharsets.US_ASCII));
        List<String> h = Crash.hints(b.toByteArray(), 10);
        assertEquals(2, h.size());
        assertEquals("SIGSEGV", h.get(0));
        assertTrue(h.get(1).endsWith("libonnxruntime.so"));
    }

    @Test public void hintsAreCapped() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 50; i++) s.append("#").append(i < 10 ? "0" : "").append(i).append(" pc libx.so\n");
        assertEquals(5, Crash.hints(s.toString().getBytes(StandardCharsets.US_ASCII), 5).size());
    }

    // '앱이 꺼진 이유' 창: 앞 12줄만 보여 줘서 진짜 원인(Caused by)이 잘렸다 (1.76, 2026-10-18)
    @Test public void showsInnermostCause() {
        StringBuilder s = new StringBuilder("java.lang.RuntimeException: Unable to start service\n");
        for (int i = 0; i < 30; i++) s.append("\tat a.B.c").append(i).append("(B.java:1)\n");
        s.append("Caused by: java.lang.IllegalStateException: outer\n\tat x.Y.z(Y.java:2)\n");
        s.append("Caused by: java.lang.NullPointerException: inner\n\tat io.github.Real.spot(Real.java:42)\n");
        List<String> l = Crash.stackLines(s.toString());
        assertEquals("java.lang.RuntimeException: Unable to start service", l.get(0));
        assertTrue(String.join("\n", l).contains("NullPointerException: inner"));
        assertTrue(String.join("\n", l).contains("Real.java:42"));
        assertEquals(4, Crash.stackLines("a\nb\nc\nd\ne\nf").size()); // 원인 줄이 없으면 앞 4줄
    }
}
