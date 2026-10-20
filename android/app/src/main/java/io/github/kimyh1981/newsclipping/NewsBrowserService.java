package io.github.kimyh1981.newsclipping;

import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.os.Process;
import android.service.media.MediaBrowserService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 안드로이드 오토의 미디어 앱 목록에 '뉴스클리핑'을 올린다. 오토는 이 앱의 세션(CarMedia)으로 핸들 버튼·재생을 보낸다.
 * 목록에는 '오늘의 뉴스' 하나: 누르면 읽기 시작. 오토·시스템·이 앱만 연결을 받는다
 */
public class NewsBrowserService extends MediaBrowserService {
    private static final String ROOT = "root", TODAY = "today", AUTO = "com.google.android.projection.gearhead";
    private static final Set<String> CALLERS = new HashSet<>(Arrays.asList(
            "com.google.android.projection.gearhead", // 안드로이드 오토
            "com.google.android.carassistant",
            "com.google.android.googlequicksearchbox", // 구글 어시스턴트·제미나이
            "com.android.bluetooth",
            "com.google.android.bluetooth")); // 안드로이드 13부터 구글 폰의 블루투스

    @Override public void onCreate() {
        super.onCreate();
        setSessionToken(CarMedia.session(this).getSessionToken());
    }

    /** 안드로이드 오토가 이 앱에 붙어 있는지 (차 모드 표시가 없는 오토도 차로 알아보려고) */
    static volatile boolean autoConnected;

    @Override public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        if (AUTO.equals(clientPackageName)) { autoConnected = true; new Prefs(this).noteAudio("안드로이드 오토 연결"); }
        boolean ok = clientUid == Process.myUid() || clientUid == Process.SYSTEM_UID || CALLERS.contains(clientPackageName);
        return ok ? new BrowserRoot(ROOT, null) : null;
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        autoConnected = false; // 모두 떨어짐 (오토 연결 끝)
        return super.onUnbind(intent);
    }

    @Override public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        if (!ROOT.equals(parentId)) { result.sendResult(Collections.emptyList()); return; }
        MediaDescription d = new MediaDescription.Builder().setMediaId(TODAY).setTitle("오늘의 뉴스").setSubtitle("헤드라인 듣기").build();
        result.sendResult(Collections.singletonList(new MediaBrowser.MediaItem(d, MediaBrowser.MediaItem.FLAG_PLAYABLE)));
    }
}
