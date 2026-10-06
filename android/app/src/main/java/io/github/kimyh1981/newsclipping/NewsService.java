package io.github.kimyh1981.newsclipping;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** 오늘의 원고(briefing.txt)를 받아 미디어 음량으로 읽는다. 차 스피커로 나가도록 포그라운드 서비스로 돈다. */
public class NewsService extends Service {
    static final String ACTION_PLAY = "play";
    static final String ACTION_STOP = "stop";
    static final String EXTRA_MANUAL = "manual";
    private static final String TAG = "newsclipping";
    private static final String CHANNEL = "news";
    private static final int NOTIFY_ID = 1;
    private static final int RETRY_NOTIFY_ID = 2;

    private static NewsService running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private volatile boolean stopped;
    private String last;

    /** 자동(차 연결) 또는 수동(앱의 '지금 듣기')으로 읽기 시작. 백그라운드 시작이 막히면 탭해서 듣는 알림을 띄운다. */
    static void start(Context c, boolean manual) {
        Intent i = new Intent(c, NewsService.class).setAction(ACTION_PLAY).putExtra(EXTRA_MANUAL, manual);
        try {
            c.startForegroundService(i);
        } catch (RuntimeException e) { // Android 12+: ForegroundServiceStartNotAllowedException (배터리 제한 앱)
            Log.w(TAG, "백그라운드 시작 거부: " + e);
            ensureChannel(c);
            PendingIntent pi = PendingIntent.getForegroundService(c, 0, i.putExtra(EXTRA_MANUAL, true), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("오늘의 뉴스 듣기")
                    .setContentText("탭하면 읽어 드립니다. 앱에서 '배터리 제한 없음'을 켜면 자동으로 시작합니다.")
                    .setContentIntent(pi).setAutoCancel(true).build();
            c.getSystemService(NotificationManager.class).notify(RETRY_NOTIFY_ID, n);
        }
    }

    static void stopIfRunning() {
        NewsService s = running;
        if (s != null) s.main.post(s::finish);
    }

    static boolean isRunning() { return running != null; }

    static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "뉴스 읽기", NotificationManager.IMPORTANCE_LOW);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        startInForeground("오늘의 뉴스를 가져오는 중");
        if (running == this) return START_NOT_STICKY; // 이미 가져오거나 읽는 중
        running = this;
        boolean manual = intent != null && intent.getBooleanExtra(EXTRA_MANUAL, false);
        new Thread(() -> prepare(manual), "news-fetch").start();
        return START_NOT_STICKY;
    }

    private void startInForeground(String text) {
        ensureChannel(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, NewsService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("뉴스클리핑")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "멈춤", stop).build())
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFY_ID, n);
    }

    private void prepare(boolean manual) {
        if (!manual) sleep(5000); // 차 오디오(A2DP)가 이어질 시간
        String text = null;
        for (int k = 0; k < 3 && text == null && !stopped; k++) {
            try {
                text = fetch(BuildConfig.NEWS_URL);
            } catch (Exception e) {
                Log.w(TAG, "원고 받기 실패 " + (k + 1) + "회: " + e);
                sleep(4000L * (k + 1)); // 시동 직후 데이터가 늦게 잡히는 경우
            }
        }
        if (stopped) return;
        if (text == null) text = manual ? "뉴스를 가져오지 못했습니다. 인터넷 연결을 확인해 주세요." : "";
        if (text.trim().isEmpty()) {
            if (!manual) { main.post(this::finish); return; } // 주말·공휴일: 서버가 원고를 비워 둔다
            text = "오늘은 쉬는 날이라 자동 브리핑이 없습니다. 뉴스 화면에서 오늘 기사를 볼 수 있습니다.";
        }
        if (!manual) new Prefs(this).markPlayed();
        final String script = text;
        main.post(() -> speak(script));
    }

    private void speak(String script) {
        if (stopped) return;
        tts = new TextToSpeech(this, status -> {
            if (stopped) return;
            if (status != TextToSpeech.SUCCESS) { Log.w(TAG, "음성 엔진 시작 실패"); finish(); return; }
            int lang = tts.setLanguage(Locale.KOREAN);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) Log.w(TAG, "한국어 음성 데이터 없음");
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            tts.setAudioAttributes(attrs);
            audio = getSystemService(AudioManager.class);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build();
            audio.requestAudioFocus(focus); // 라디오·음악은 잠시 멈췄다가 끝나면 다시 나온다
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { if (id.equals(last)) main.post(NewsService.this::finish); }
                @Override public void onError(String id) { if (id.equals(last)) main.post(NewsService.this::finish); }
            });
            List<String> parts = Rules.chunks(script, Math.min(3900, TextToSpeech.getMaxSpeechInputLength()));
            startInForeground("읽는 중 · 알림의 '멈춤'으로 멈춥니다");
            for (int i = 0; i < parts.size(); i++) {
                last = "u" + i;
                if (parts.get(i).isEmpty()) tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, last);
                else tts.speak(parts.get(i), TextToSpeech.QUEUE_ADD, null, last);
            }
            if (parts.isEmpty()) finish();
        });
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void finish() {
        stopped = true;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        if (running == this) running = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (!stopped) finish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
