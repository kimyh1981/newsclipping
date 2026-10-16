package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * F1 목소리 모델(약 400MB)을 폰에 받아 두고, 엔진을 한 번만 띄워 함께 쓴다.
 * 받는 곳은 개발사가 보관해 둔 허깅페이스 저장소의 고정 판(바뀌지 않게). 끊겨도 받던 곳부터 이어 받는다.
 */
final class F1Voice {
    private static final String TAG = "F1Voice";
    static final String BASE = "https://huggingface.co/supertone-oss-archive/supertonic-3/resolve/aafc6e32416a594460b32413efc49d7fe4ce6d46/";
    /** 받을 파일 (작은 것부터) */
    static final String[] FILES = {
        "onnx/tts.json", "onnx/unicode_indexer.json", "voice_styles/" + F1Engine.STYLE + ".json",
        "onnx/duration_predictor.onnx", "onnx/text_encoder.onnx", "onnx/vocoder.onnx", "onnx/vector_estimator.onnx",
    };
    static final long TOTAL_MB = 400;

    /** 받는 중이면 0~100, 아니면 -1. 실패하면 error에 까닭 */
    static volatile int progress = -1;
    static volatile String error = "";
    private static F1Engine engine;

    private F1Voice() {}

    static File dir(Context c) { return new File(c.getFilesDir(), "f1"); }

    /** 다 받았나: 파일마다 '다 받음' 표시(.ok)가 있어야 한다 */
    static boolean ready(Context c) {
        File d = dir(c);
        for (String f : FILES) if (!new File(d, f + ".ok").exists()) return false;
        return true;
    }

    /** 모델을 받는다 (오래 걸린다: 화면 밖 스레드에서 부른다). 이미 받은 파일은 건너뛴다 */
    static synchronized boolean download(Context c) {
        File d = dir(c);
        error = "";
        progress = 0;
        try {
            long done = 0, total = TOTAL_MB * 1_000_000L;
            for (String f : FILES) {
                File to = new File(d, f);
                File ok = new File(to.getPath() + ".ok");
                if (ok.exists()) { done += to.length(); continue; }
                to.getParentFile().mkdirs();
                done = fetch(BASE + f, to, done, total);
                if (!ok.createNewFile() && !ok.exists()) throw new IllegalStateException("표시 저장 실패");
            }
            progress = -1;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "모델 받기 실패: " + e);
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            progress = -1;
            return false;
        }
    }

    /** url을 to에 받는다. 받다 만 .part가 있으면 그 뒤부터 (Range) */
    private static long fetch(String url, File to, long before, long total) throws Exception {
        File part = new File(to.getPath() + ".part");
        long have = part.exists() ? part.length() : 0;
        for (int hop = 0; hop < 5; hop++) { // 허깅페이스는 실제 파일 저장소로 넘겨 준다 (다른 주소로 이동)
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            if (have > 0) h.setRequestProperty("Range", "bytes=" + have + "-");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400) { url = new URL(new URL(url), h.getHeaderField("Location")).toString(); h.disconnect(); continue; }
            if (code == 200) have = 0; // 이어 받기를 안 받아 주면 처음부터
            else if (code != 206) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = h.getInputStream(); OutputStream o = new FileOutputStream(part, have > 0)) {
                byte[] buf = new byte[1 << 16];
                long got = have;
                for (int n; (n = in.read(buf)) > 0; ) {
                    o.write(buf, 0, n);
                    got += n;
                    progress = (int) Math.min(99, (before + got) * 100 / total);
                }
                have = got;
            } finally {
                h.disconnect();
            }
            to.delete();
            if (!part.renameTo(to)) throw new IllegalStateException("저장 실패");
            return before + have;
        }
        throw new IllegalStateException("이동이 너무 많음");
    }

    /** 엔진 (처음 부를 때 띄운다, 몇 초 걸린다). 모델이 없거나 못 띄우면 null */
    static synchronized F1Engine engine(Context c) {
        if (engine != null) return engine;
        if (!ready(c)) return null;
        try {
            long t = System.currentTimeMillis();
            engine = new F1Engine(dir(c));
            Log.i(TAG, "F1 엔진 준비 " + (System.currentTimeMillis() - t) + "ms");
        } catch (Throwable e) {
            Log.w(TAG, "F1 엔진을 띄우지 못함: " + e);
            error = String.valueOf(e.getMessage());
        }
        return engine;
    }

    /** 받은 모델을 지운다 (끄고 공간을 돌려받을 때) */
    static synchronized void delete(Context c) {
        if (engine != null) { engine.close(); engine = null; }
        deleteTree(dir(c));
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteTree(x);
        f.delete();
    }

    /** 앱 말 빠르기(보통 100)를 F1 빠르기로: 개발사 기본 1.05가 보통 */
    static float speed(int rate) { return 1.05f * rate / 100f; }
}
