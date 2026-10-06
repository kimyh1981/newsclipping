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
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 오늘의 브리핑(briefing.json)을 받아 고른 언론사로 원고를 만들어 미디어 음량으로 읽는다. 차 스피커로 나가도록 포그라운드 서비스로 돈다. */
public class NewsService extends Service {
    static final String ACTION_PLAY = "play";
    static final String ACTION_STOP = "stop";
    static final String ACTION_MORE = "more";
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
    private MediaSession session;
    private List<Brief.Line> lines = new ArrayList<>();
    /** 지금 읽는 원고 줄 번호 */
    private volatile int current;

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
        if (intent != null && ACTION_MORE.equals(intent.getAction())) {
            if (running == this) more();
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
                .addAction(new Notification.Action.Builder(null, "자세히", PendingIntent.getService(this, 2, new Intent(this, NewsService.class).setAction(ACTION_MORE), PendingIntent.FLAG_IMMUTABLE)).build())
                .addAction(new Notification.Action.Builder(null, "멈춤", stop).build())
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFY_ID, n);
    }

    private void prepare(boolean manual) {
        if (!manual) sleep(5000); // 차 오디오(A2DP)가 이어질 시간
        Brief brief = null;
        for (int k = 0; k < 3 && brief == null && !stopped; k++) {
            try {
                brief = Brief.parse(fetch(BuildConfig.NEWS_URL));
            } catch (Exception e) {
                Log.w(TAG, "브리핑 받기 실패 " + (k + 1) + "회: " + e);
                sleep(4000L * (k + 1)); // 시동 직후 데이터가 늦게 잡히는 경우
            }
        }
        if (stopped) return;
        List<Brief.Line> script;
        if (brief == null) {
            if (!manual) { main.post(this::finish); return; }
            script = new ArrayList<>();
            script.add(new Brief.Line("뉴스를 가져오지 못했습니다. 인터넷 연결을 확인해 주세요.", null));
        } else if (!brief.playToday && !manual) {
            main.post(this::finish); // 주말·공휴일: 자동으로는 읽지 않는다
            return;
        } else {
            script = brief.lines(new Prefs(this).sources());
        }
        if (!manual) new Prefs(this).markPlayed();
        main.post(() -> speak(script));
    }

    private void speak(List<Brief.Line> script) {
        if (stopped) return;
        lines = script;
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
                @Override public void onStart(String id) {
                    if (id.startsWith("L")) {
                        int i = Integer.parseInt(id.substring(1, id.indexOf('.')));
                        current = i;
                        main.post(() -> showLine(i));
                    }
                }
                @Override public void onDone(String id) { if (id.equals(last)) main.post(NewsService.this::finish); }
                @Override public void onError(String id) { if (id.equals(last)) main.post(NewsService.this::finish); }
            });
            startSession();
            startInForeground("읽는 중 · 핸들의 다음(▶▶) 버튼이나 '자세히'로 기사 요약을 듣습니다");
            queueFrom(0, TextToSpeech.QUEUE_ADD);
        });
    }

    /** start번째 줄부터 끝까지 읽기 예약. 긴 줄은 음성 엔진 한도에 맞춰 나눈다 */
    private void queueFrom(int start, int mode) {
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        last = null;
        for (int i = start; i < lines.size(); i++) {
            String text = lines.get(i).text;
            if (text.isEmpty()) {
                last = "P" + i;
                tts.playSilentUtterance(700, mode, last);
            } else {
                List<String> parts = Rules.chunks(text, max);
                for (int k = 0; k < parts.size(); k++) {
                    last = "L" + i + "." + k;
                    tts.speak(parts.get(k), mode, null, last);
                    mode = TextToSpeech.QUEUE_ADD;
                }
            }
            mode = TextToSpeech.QUEUE_ADD;
        }
        if (last == null) finish();
    }

    /** '자세히': 지금(또는 방금) 읽은 기사의 요약을 읽고, 그다음 줄부터 이어 읽는다 */
    private void more() {
        if (tts == null || stopped || lines.isEmpty()) return;
        int i = Math.min(current, lines.size() - 1);
        while (i > 0 && lines.get(i).item == null) i--;
        Brief.Item it = lines.get(i).item;
        String text = it == null ? "" : it.summary;
        if (text.isEmpty()) text = it == null ? "자세히 들을 기사가 아직 없습니다." : "이 기사는 자세한 내용이 없습니다.";
        tts.stop();
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        List<String> parts = Rules.chunks(text, max);
        for (int k = 0; k < parts.size(); k++) tts.speak(parts.get(k), k == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "S" + i + "." + k);
        tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "S" + i + ".p");
        queueFrom(i + 1, TextToSpeech.QUEUE_ADD);
        if (last == null) last = "S" + i + ".p";
    }

    /** 차의 미디어 버튼(핸들 리모컨)을 받는다: 다음 = 자세히, 일시정지·정지 = 멈춤. 차 화면에는 지금 기사 제목이 뜬다 */
    private void startSession() {
        session = new MediaSession(this, "newsclipping");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onSkipToNext() { more(); }
            @Override public void onPause() { finish(); }
            @Override public void onStop() { finish(); }
        }, main);
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
        showLine(0);
        session.setActive(true);
    }

    private void showLine(int i) {
        if (session == null || i >= lines.size()) return;
        Brief.Line l = lines.get(i);
        if (l.item == null && i > 0) return; // 기사 제목만 차 화면에 띄운다
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, l.item == null ? "오늘의 뉴스" : l.item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, l.item == null ? "뉴스클리핑" : l.item.source + " · 다음 버튼: 자세히")
                .build());
    }

    static String fetch(String url) throws Exception {
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
        if (session != null) { session.setActive(false); session.release(); session = null; }
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
