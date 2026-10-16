package io.github.kimyh1981.newsclipping;

import java.io.File;

/**
 * 앱의 F1 엔진(F1Engine·F1Text·Pron)을 PC용 ONNX Runtime으로 그대로 돌려 소리를 만든다 (단위 테스트가 아니라 android.yml의 f1-live가 돌린다).
 * java F1Live <모델 폴더> <저장할 wav> <글>...
 */
public final class F1Live {
    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        try (F1Engine e = new F1Engine(new File(args[0]))) {
            System.out.println("엔진 준비 " + (System.currentTimeMillis() - t0) + "ms");
            StringBuilder all = new StringBuilder();
            for (int i = 2; i < args.length; i++) all.append(i > 2 ? " " : "").append(args[i]);
            String said = Pron.say(all.toString());
            System.out.println("읽을 글: " + said);
            long t = System.currentTimeMillis();
            float[] pcm = e.synth(said, "ko", 1.05f);
            double sec = pcm.length / (double) e.sampleRate, took = (System.currentTimeMillis() - t) / 1000.0;
            double rms = 0;
            for (float x : pcm) rms += x * x;
            rms = Math.sqrt(rms / Math.max(1, pcm.length));
            System.out.printf("소리 %.1f초 · 만든 시간 %.1f초 · 실시간의 %.1f배 · 크기(RMS) %.3f%n", sec, took, sec / took, rms);
            F1Engine.writeWav(pcm, e.sampleRate, new File(args[1]));
            if (sec < 2 || rms < 0.01) throw new IllegalStateException("소리가 너무 짧거나 작다");
        }
    }
}
