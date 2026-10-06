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
import android.media.audiofx.Equalizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
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
    static final String ACTION_MORE = "more"; // 전체 듣기 (예전 알림의 '자세히'도 같은 동작)
    static final String ACTION_PREV = "prev";
    static final String ACTION_NEXT = "next";
    static final String ACTION_PAUSE = "pause";
    static final String ACTION_RESUME = "resume";
    /** 멈춘 채로 이만큼 지나면 끝낸다 */
    private static final long PAUSE_LIMIT_MS = 30 * 60 * 1000L;
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
    /** 통화·다른 앱 소리로 멈춘 상태. 다시 들으면 멈춘 기사 처음부터 이어 읽는다 */
    private boolean paused;
    /** 잠깐 빼앗긴 소리(통화·내비 안내)라 돌려받으면 저절로 이어 읽는다 */
    private boolean resumeOnGain;
    private final Bundle speakParams = new Bundle();
    private Equalizer eq;
    private final Runnable pauseLimit = this::finish;

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

    static final int IDLE = 0, PLAYING = 1, PAUSED = 2;
    /** 앱 화면의 재생 버튼 모양(재생 ↔ 일시정지)을 맞추려고 상태가 바뀔 때마다 부른다 */
    static Runnable onStateChange;

    static int state() {
        NewsService s = running;
        return s == null ? IDLE : s.paused ? PAUSED : PLAYING;
    }

    private static void stateChanged() {
        Runnable r = onStateChange;
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }

    /** 앱 화면의 이전·전체 듣기·다음 버튼 */
    static void control(String action) {
        NewsService s = running;
        if (s != null) s.main.post(() -> s.handle(action));
    }

    private void handle(String action) {
        if (running != this || tts == null) return;
        switch (action) {
            case ACTION_PREV: jump(prevTarget()); break;
            case ACTION_NEXT: jump(nextTarget()); break;
            case ACTION_MORE: full(); break;
            case ACTION_PAUSE: pause(false); break;
            case ACTION_RESUME: resume(); break;
            default: break;
        }
    }

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
        String action = intent == null ? null : intent.getAction();
        if (ACTION_MORE.equals(action) || ACTION_PREV.equals(action) || ACTION_NEXT.equals(action) || ACTION_PAUSE.equals(action) || ACTION_RESUME.equals(action)) {
            handle(action);
            return START_NOT_STICKY;
        }
        if (running == this) { // 이미 가져오거나 읽는 중: 멈춰 있었으면 이어 읽는다
            if (paused) resume();
            else startInForeground(tts == null ? "오늘의 뉴스를 가져오는 중" : PLAYING_TEXT); // startForegroundService마다 필요
            return START_NOT_STICKY;
        }
        startInForeground("오늘의 뉴스를 가져오는 중");
        running = this;
        stateChanged();
        boolean manual = intent != null && intent.getBooleanExtra(EXTRA_MANUAL, false);
        new Thread(() -> prepare(manual), "news-fetch").start();
        return START_NOT_STICKY;
    }

    private PendingIntent act(int code, String action) {
        return PendingIntent.getService(this, code, new Intent(this, NewsService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE);
    }

    private void startInForeground(String text) {
        ensureChannel(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("뉴스클리핑")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true);
        if (tts != null) { // 읽는 중: 잠금 화면에서도 이전 · 전체 듣기 · 다음 · 멈춤/이어 듣기
            b.addAction(new Notification.Action.Builder(null, "이전", act(3, ACTION_PREV)).build())
             .addAction(new Notification.Action.Builder(null, "전체 듣기", act(2, ACTION_MORE)).build())
             .addAction(new Notification.Action.Builder(null, "다음", act(4, ACTION_NEXT)).build())
             .addAction(paused ? new Notification.Action.Builder(null, "이어 듣기", act(5, ACTION_RESUME)).build()
                               : new Notification.Action.Builder(null, "멈춤", act(1, ACTION_STOP)).build());
            Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
            if (session != null) style.setMediaSession(session.getSessionToken());
            b.setStyle(style);
        } else {
            b.addAction(new Notification.Action.Builder(null, "멈춤", act(1, ACTION_STOP)).build());
        }
        Notification n = b.build();
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
            Voices.apply(tts, new Prefs(this));
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            tts.setAudioAttributes(attrs);
            audio = getSystemService(AudioManager.class);
            // 통화·내비 안내·다른 앱 소리가 끼어들면 멈추고, 끝나면 멈춘 기사부터 이어 읽는다
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this::onFocus, main).build();
            audio.requestAudioFocus(focus); // 라디오·음악은 잠시 멈췄다가 끝나면 다시 나온다
            soften(audio.generateAudioSessionId());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {
                    if (id.startsWith("L")) {
                        int i = Integer.parseInt(id.substring(1, id.indexOf('.')));
                        current = i;
                        main.post(() -> showLine(i));
                    }
                }
                @Override public void onDone(String id) { if (id.equals(last)) main.post(() -> { if (!paused) finish(); }); }
                @Override public void onError(String id) { if (id.equals(last)) main.post(() -> { if (!paused) finish(); }); }
            });
            startSession();
            startInForeground(PLAYING_TEXT);
            queueFrom(0, TextToSpeech.QUEUE_ADD);
        });
    }

    private static final String PLAYING_TEXT = "읽는 중 · 이전 / 전체 듣기 / 다음";

    /** start번째 줄부터 끝까지 읽기 예약. 긴 줄은 음성 엔진 한도에 맞춰 나누고, 기사 사이는 잠깐 쉰다 */
    private void queueFrom(int start, int mode) {
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        last = null;
        for (int i = start; i < lines.size(); i++) {
            Brief.Line line = lines.get(i);
            if (line.text.isEmpty()) {
                last = "P" + i;
                tts.playSilentUtterance(700, mode, last);
            } else {
                List<String> parts = Rules.chunks(line.text, max);
                for (int k = 0; k < parts.size(); k++) {
                    last = "L" + i + "." + k;
                    tts.speak(parts.get(k), mode, speakParams, last);
                    mode = TextToSpeech.QUEUE_ADD;
                }
                if (line.item != null) { // 헤드라인 뒤에 숨 한 번: 기사끼리 붙어 들리지 않게
                    last = "P" + i + ".h";
                    tts.playSilentUtterance(450, TextToSpeech.QUEUE_ADD, last);
                }
            }
            mode = TextToSpeech.QUEUE_ADD;
        }
        if (last == null) finish();
    }

    private boolean[] itemFlags() {
        boolean[] f = new boolean[lines.size()];
        for (int i = 0; i < f.length; i++) f[i] = lines.get(i).item != null;
        return f;
    }

    /** 이전 기사 (멈춤 중이면 멈춘 기사 기준) */
    private int prevTarget() {
        return Rules.prevItem(itemFlags(), current);
    }

    /** 다음 기사. 'OO 소식입니다.'를 읽는 중이면 바로 뒤 기사가 지금 기사이므로 그다음 */
    private int nextTarget() {
        boolean[] f = itemFlags();
        int i = Rules.nextItem(f, current);
        if (i == current + 1 && current < lines.size() && lines.get(current).text.endsWith("소식입니다.")) i = Rules.nextItem(f, i);
        return i;
    }

    /** 기사 줄 i부터 다시 읽는다. 앞줄이 'OO 소식입니다.'면 그 줄부터 */
    private void jump(int i) {
        if (i >= lines.size()) i = Math.max(0, lines.size() - 1); // 다음 기사가 없으면 맺음말
        int start = i;
        if (i > 0 && lines.get(i).item != null && lines.get(i - 1).item == null && lines.get(i - 1).text.endsWith("소식입니다.")) start = i - 1;
        current = i;
        if (paused) { showLine(i); return; } // 멈춤 중에는 위치만 옮기고, 이어 듣기로 거기서 시작
        tts.stop();
        queueFrom(start, TextToSpeech.QUEUE_FLUSH);
    }

    /** '전체 듣기': 지금(또는 방금) 읽은 기사의 본문 앞부분을 읽고, 그다음 줄부터 이어 읽는다 */
    private void full() {
        if (tts == null || stopped || lines.isEmpty()) return;
        int i = Math.min(current, lines.size() - 1);
        while (i > 0 && lines.get(i).item == null) i--;
        Brief.Item it = lines.get(i).item;
        String text = it == null ? "" : it.full();
        if (text.isEmpty()) text = it == null ? "전체로 들을 기사가 아직 없습니다." : "이 기사는 본문을 가져오지 못했습니다.";
        if (paused) { paused = false; stateChanged(); main.removeCallbacks(pauseLimit); audio.requestAudioFocus(focus); setState(PlaybackState.STATE_PLAYING); startInForeground(PLAYING_TEXT); }
        tts.stop();
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        List<String> parts = Rules.chunks(text, max);
        for (int k = 0; k < parts.size(); k++) tts.speak(parts.get(k), k == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, speakParams, "S" + i + "." + k);
        tts.playSilentUtterance(600, TextToSpeech.QUEUE_ADD, "S" + i + ".p");
        queueFrom(i + 1, TextToSpeech.QUEUE_ADD);
        if (last == null) last = "S" + i + ".p";
    }

    private void onFocus(int change) {
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (paused && resumeOnGain) resume();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause(false); // 다른 앱이 소리를 가져감: 이어 듣기(앱·알림·핸들 재생 버튼)를 기다린다
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            pause(true); // 통화·내비 안내: 끝나면 저절로 이어 읽는다
        }
    }

    private void pause(boolean autoResume) {
        if (tts == null || stopped) return;
        resumeOnGain = autoResume;
        if (paused) return;
        paused = true;
        stateChanged();
        tts.stop();
        setState(PlaybackState.STATE_PAUSED);
        startInForeground(autoResume ? "잠깐 멈춤 · 통화나 안내가 끝나면 이어 읽습니다" : "멈춤 · '이어 듣기'를 누르면 멈춘 기사부터 읽습니다");
        main.removeCallbacks(pauseLimit);
        main.postDelayed(pauseLimit, PAUSE_LIMIT_MS);
    }

    private void resume() {
        if (tts == null || stopped || !paused) return;
        paused = false;
        stateChanged();
        main.removeCallbacks(pauseLimit);
        audio.requestAudioFocus(focus);
        setState(PlaybackState.STATE_PLAYING);
        startInForeground(PLAYING_TEXT);
        jump(Math.min(current, lines.size() - 1));
    }

    /** 치찰음 줄이기: 이 서비스의 음성만 이퀄라이저로 고음을 낮춘다. 기기가 지원하지 않으면 그냥 읽는다 */
    private void soften(int sessionId) {
        if (sessionId <= 0) return;
        speakParams.putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, sessionId);
        if (!new Prefs(this).soften()) return;
        try {
            eq = new Equalizer(0, sessionId);
            short[] range = eq.getBandLevelRange();
            for (short band = 0; band < eq.getNumberOfBands(); band++) {
                eq.setBandLevel(band, Rules.softenLevel(eq.getCenterFreq(band) / 1000, range[0], range[1]));
            }
            eq.setEnabled(true);
        } catch (RuntimeException e) {
            Log.w(TAG, "이퀄라이저를 쓸 수 없음: " + e);
            eq = null;
        }
    }

    private void setState(int state) {
        if (session == null) return;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_FAST_FORWARD)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state == PlaybackState.STATE_PLAYING ? 1f : 0f).build());
    }

    /** 차의 미디어 버튼(핸들 리모컨): 다음·이전 = 다음·이전 기사, 일시정지·재생 = 멈춤·이어 듣기, 빨리 감기 = 전체 듣기. 차 화면에는 지금 기사 제목이 뜬다 */
    private void startSession() {
        session = new MediaSession(this, "newsclipping");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onSkipToNext() { handle(ACTION_NEXT); }
            @Override public void onSkipToPrevious() { handle(ACTION_PREV); }
            @Override public void onFastForward() { full(); }
            @Override public void onPause() { pause(false); }
            @Override public void onPlay() { resume(); }
            @Override public void onStop() { finish(); }
        }, main);
        setState(PlaybackState.STATE_PLAYING);
        showLine(0);
        session.setActive(true);
    }

    private void showLine(int i) {
        if (session == null || i >= lines.size()) return;
        Brief.Line l = lines.get(i);
        if (l.item == null && i > 0) return; // 기사 제목만 차 화면에 띄운다
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, l.item == null ? "오늘의 뉴스" : l.item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, l.item == null ? "뉴스클리핑" : l.item.source)
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
        main.removeCallbacks(pauseLimit);
        if (eq != null) { eq.release(); eq = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (session != null) { session.setActive(false); session.release(); session = null; }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        if (running == this) running = null;
        stateChanged();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** 최근 앱 화면에서 뉴스클리핑을 쓸어 올려 닫으면 읽기도 멈춘다 (다음 날 자동 재생은 그대로) */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        finish();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (!stopped) finish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
