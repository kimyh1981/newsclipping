package io.github.kimyh1981.newsclipping;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 폰에 든 한국어 음성 고르기: 설정에서 고른 음성, 없으면 가장 자연스러운(품질 높은) 음성. */
final class Voices {
    static final String SAMPLE = "좋은 아침입니다. 이 목소리로 오늘의 출근길 뉴스를 읽어 드릴게요.";

    private Voices() {}

    static boolean installed(Voice v) {
        Set<String> f = v.getFeatures();
        return f == null || !f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
    }

    /** 받아 둔 한국어 음성, 이름순 (목록 번호가 날마다 바뀌지 않게) */
    static List<Voice> korean(TextToSpeech tts) {
        List<Voice> out = new ArrayList<>();
        Set<Voice> all;
        try { all = tts.getVoices(); } catch (RuntimeException e) { all = null; }
        if (all != null) for (Voice v : all) {
            if (v.getLocale() != null && "ko".equals(v.getLocale().getLanguage()) && installed(v)) out.add(v);
        }
        out.sort(Comparator.comparing(Voice::getName));
        return out;
    }

    static Voice best(List<Voice> voices) {
        Voice best = null;
        int top = Integer.MIN_VALUE;
        for (Voice v : voices) {
            int s = Rules.voiceScore(v.getQuality(), v.isNetworkConnectionRequired(), !installed(v));
            if (best == null || s > top) { best = v; top = s; }
        }
        return best;
    }

    /** 고른 음성(없으면 가장 좋은 음성)과 말 빠르기를 엔진에 건다 */
    static void apply(TextToSpeech tts, Prefs prefs) {
        List<Voice> voices = korean(tts);
        Voice pick = null;
        for (Voice v : voices) if (v.getName().equals(prefs.voice())) pick = v;
        if (pick == null) pick = best(voices);
        if (pick != null) tts.setVoice(pick);
        tts.setSpeechRate(prefs.rate() / 100f);
    }

    static String label(Voice v, int i) {
        String s = "음성 " + (i + 1);
        if (v.getQuality() >= Voice.QUALITY_VERY_HIGH) s += " · 고음질";
        if (v.isNetworkConnectionRequired()) s += " · 인터넷 필요";
        return s;
    }
}
