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
            String said = F1Text.hangulize(Pron.say(all.toString())); // 앱과 같이: 남은 영문은 한글 소리로
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
            // 앱은 늘 보통 빠르기(1.05)로 만들고 말 빠르기는 재생 빠르기로 준다 (2026-11-27). 여기서는 만든 소리가 상한(초당 MAX_SPS음절)을 넘지 않는지 본다
            int syl = F1Text.syllables(said);
            double sps = syl / sec;
            // 앞뒤 무음(덩이 사이 쉼 포함)이 들어 있어 실제 말하는 빠르기는 이보다 조금 빠르다
            System.out.printf("만든 빠르기: 초당 %.1f음절 (상한 %.1f) · 앱의 '빠르게'(130)는 이 소리를 1.3배로 튼다%n", sps, F1Text.MAX_SPS);
            if (sps > F1Text.MAX_SPS + 0.3) throw new IllegalStateException("초당 음절 상한을 넘었다");
            if (sps < 3.0) throw new IllegalStateException("너무 느리다 (상한 계산이 영문 글자 수를 세는 것 같은)");
        }
    }
}
