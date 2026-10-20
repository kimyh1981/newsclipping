package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;

/**
 * 차 화면·핸들 버튼과 이어지는 미디어 세션 (앱 전체에서 하나).
 * 안드로이드 오토는 '오토용 미디어 앱'(NewsBrowserService)의 세션에만 핸들의 다음·이전·재생 버튼을 보낸다 (2026-10-20 퇴근길: 핸들 버튼이 안 먹음).
 * 읽는 동안에는 NewsService가 이 세션에 자기 처리기를 붙이고, 끝나면 '재생을 누르면 읽기 시작'으로 돌려놓는다.
 */
final class CarMedia {
    private static MediaSession session;

    private CarMedia() { }

    static synchronized MediaSession session(Context c) {
        if (session == null) {
            session = new MediaSession(c.getApplicationContext(), "newsclipping");
            idle(c);
        }
        return session;
    }

    /** 읽지 않을 때: 오토 화면에서 '오늘의 뉴스'를 누르거나 재생을 누르면 읽기 시작. 버튼을 가로채지 않게 비활성 */
    static synchronized void idle(Context c) {
        if (session == null) return;
        Context app = c.getApplicationContext();
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { NewsService.start(app, true); }
            @Override public void onPlayFromMediaId(String id, Bundle extras) { NewsService.start(app, true); }
            @Override public void onPlayFromSearch(String query, Bundle extras) { NewsService.start(app, true); }
            @Override public boolean onMediaButtonEvent(Intent i) { return false; } // 차가 연결될 때 저절로 보내는 '재생'으로 시작하지 않는다
        });
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY_FROM_SEARCH)
                .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build());
        // 차 화면에 마지막 기사 제목이 남지 않게
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "오늘의 뉴스").build());
        session.setActive(false);
    }
}
