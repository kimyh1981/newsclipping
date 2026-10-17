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
import android.media.AudioPlaybackConfiguration;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;

/** 오늘의 브리핑(briefing.json)을 받아 고른 언론사로 원고를 만들어 미디어 음량으로 읽는다. 차 스피커로 나가도록 포그라운드 서비스로 돈다. */
public class NewsService extends Service {
    static final String ACTION_PLAY = "play";
    static final String ACTION_STOP = "stop";
    static final String ACTION_MORE = "more"; // 전체 듣기 (예전 알림의 '자세히'도 같은 동작)
    static final String ACTION_PREV = "prev";
    static final String ACTION_NEXT = "next";
    /** 제품 공부 중이면 같은 회의 농업영어로, 농업영어 중이면 그 회를 마치고 맺음말로 건너뛴다 */
    static final String ACTION_SKIP_LESSON = "skip-lesson";
    static final String ACTION_PAUSE = "pause";
    static final String ACTION_RESUME = "resume";
    /** 멈춘 채로 이만큼 지나면 끝낸다 */
    private static final long PAUSE_LIMIT_MS = 30 * 60 * 1000L;
    static final String EXTRA_MANUAL = "manual";
    /** 이 제목의 기사부터 읽는다 (웹 기사 목록의 ▶) */
    static final String EXTRA_FROM = "from";
    /** 제품 공부 한 회만 듣기 (진도 화면에서) */
    static final String EXTRA_LESSON = "lesson";
    private static final String TAG = "newsclipping";
    static final String CHANNEL = "news";
    private static final int NOTIFY_ID = 1;
    private static final int RETRY_NOTIFY_ID = 2;

    private static NewsService running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private volatile boolean stopped;
    private MediaSession session;
    private List<Brief.Line> lines = new ArrayList<>();
    /** 서버가 녹음해 둔 줄 글자 → 받아 둔 파일. 읽는 중에도 계속 받아 채운다. 있으면 앱이 직접 틀고, 없으면 폰 음성.
     *  (음성 엔진의 addSpeech는 엔진 앱이 이 앱의 파일을 읽지 못해 소리 없이 건너뛴다) */
    private final Map<String, File> clips = new ConcurrentHashMap<>();
    private ExecutorService pool;
    /** F1 목소리(폰에서 만듦)로 읽는다: 모델을 받아 두고 설정에서 켰을 때. 다음 차례 몇 개를 미리 만들어 둔다 (주인 요청 2026-10-16) */
    private volatile boolean useF1;
    private ExecutorService f1Pool;
    /** 차례 글자(+언어) → 만들고 있는/만든 소리 파일 */
    private final Map<String, Future<File>> f1Jobs = new ConcurrentHashMap<>();
    private File f1Dir;
    private static final int F1_AHEAD = 3;
    /** 다음 차례가 이만큼 안에 안 만들어지면 그 차례만 폰 음성으로 */
    private static final long F1_WAIT_MS = 20000;
    /** 읽을 차례들과 지금 차례. 한 차례가 끝나면 다음 차례를 튼다 */
    private List<Step> queue = new ArrayList<>();
    private int pos;
    /** 멈추거나 건너뛸 때마다 올린다. 예전 차례의 '끝남' 알림은 버린다 */
    private int gen;
    private MediaPlayer player;
    private AudioAttributes attrs;
    private final Runnable afterSilence = this::advance;
    /** 차 연결로 저절로 시작했다 (자막 화면을 띄운다) */
    private boolean autoStart;
    /** 읽기 시작한 때. 차 연결 직후 저절로 켜진 음악 앱이 소리를 가져가면 이 안에서는 되찾는다 */
    private long startedAt;
    private int retakes;
    private static final long RETAKE_WINDOW_MS = 2 * 60 * 1000L;
    /** 내비 안내 음성이 나오는 동안 멈췄다 (끝나면 이어 읽는다) */
    private boolean navPaused;
    private AudioManager.AudioPlaybackCallback playbackWatch;
    private final Runnable navResume = () -> { if (this.paused && this.navPaused) { this.navPaused = false; resume(); } };
    /** 지금 읽는 원고 줄 번호 */
    private volatile int current;
    /** 통화·다른 앱 소리로 멈춘 상태. 다시 들으면 멈춘 기사 처음부터 이어 읽는다 */
    private boolean paused;
    /** 잠깐 빼앗긴 소리(통화·내비 안내)라 돌려받으면 저절로 이어 읽는다 */
    private boolean resumeOnGain;
    /** 통화·안내로 잠깐 멈춘 때. 이보다 오래 멈췄으면 끝나도 저절로 이어 읽지 않는다 (긴 통화 뒤 갑자기 소리가 나지 않게) */
    private long pausedAt;
    private static final long AUTO_RESUME_MS = 5 * 60 * 1000L;
    /** 통화가 끝난 때. 차는 통화가 끝나면 '재생' 버튼 신호를 보내기도 해서, 직후의 재생 신호는 사람이 누른 것으로 보지 않는다 */
    private long callEndedAt;
    private boolean inCall;
    private Object modeWatch; // AudioManager.OnModeChangedListener (안드로이드 12부터)
    private static final long AFTER_CALL_MS = 5000;
    /** 멈춘 뒤 통화가 끼었다 (그러면 음성 비서 뒤처럼 저절로 이어 읽지 않는다) */
    private boolean callSincePause;
    /** 제미나이 같은 음성 비서가 소리를 가져갔을 때: 마이크·다른 소리가 멎기를 지켜본다 */
    private boolean assistWatch;
    private boolean sawMic;
    private int quietTicks;
    private final Runnable assistCheck = this::checkAssistant;
    private final Bundle speakParams = new Bundle();
    private AudioTrack hold;
    private final Runnable pauseLimit = this::finish;
    /** 이 제목의 기사부터 읽기 시작한다. 없으면 오늘 멈춘 기사부터 */
    private String from;
    /** 원고를 끝까지 다 읽었다: 다음에는 처음부터 */
    private boolean completed;
    /** 진도 화면에서 고른 제품 공부 회 (0이면 보통 브리핑) */
    private int lessonOnly;

    /** 자동(차 연결) 또는 수동(앱의 '지금 듣기')으로 읽기 시작. 백그라운드 시작이 막히면 탭해서 듣는 알림을 띄운다. */
    static void start(Context c, boolean manual) {
        start(c, manual, null);
    }

    /** title 기사부터 듣는다. 읽는 중이면 그 기사로 건너뛰고, 멈춰 있었으면 거기서 이어 읽는다 */
    static void playFrom(Context c, String title) {
        NewsService s = running;
        if (s != null) s.main.post(() -> s.playTitle(title));
        else start(c, true, title);
    }

    private static void start(Context c, boolean manual, String from) {
        Intent i = new Intent(c, NewsService.class).setAction(ACTION_PLAY).putExtra(EXTRA_MANUAL, manual);
        if (from != null) i.putExtra(EXTRA_FROM, from);
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

    /** 제품 공부 n회만 듣는다. 읽는 중이면 끄고 잠시 뒤 시작한다 */
    static void playLesson(Context c, int n) {
        Context app = c.getApplicationContext();
        Runnable go = () -> {
            Intent i = new Intent(app, NewsService.class).setAction(ACTION_PLAY).putExtra(EXTRA_MANUAL, true).putExtra(EXTRA_LESSON, n);
            app.startForegroundService(i);
        };
        NewsService s = running;
        if (s == null) { go.run(); return; }
        s.main.post(s::finish);
        s.main.postDelayed(go, 800);
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
        Runnable c = onCaption;
        if (c != null) new Handler(Looper.getMainLooper()).post(c);
    }

    /** 자막 화면에 띄울 것: 언론사, 기사 제목, 지금 읽는 글, 전체 듣기 중인지 */
    static final class Caption {
        final String source, title, text;
        final boolean full;
        /** 제품 공부·농업영어 회 번호 (뉴스면 0)와 농업영어인지 */
        final int lesson;
        final boolean english;
        Caption(String source, String title, String text, boolean full) { this(source, title, text, full, 0, false); }
        Caption(String source, String title, String text, boolean full, int lesson, boolean english) {
            this.source = source; this.title = title; this.text = text; this.full = full; this.lesson = lesson; this.english = english;
        }
    }
    private static volatile Caption caption;
    /** 자막 화면이 지금 읽는 글이 바뀔 때마다(그리고 멈추거나 끝날 때) 다시 그리려고 단다 */
    static Runnable onCaption;

    /** 읽는 중이 아니면 null */
    static Caption caption() { return running == null ? null : caption; }

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
            case ACTION_SKIP_LESSON: skipLesson(); break;
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
        Crash.install(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        if (ACTION_MORE.equals(action) || ACTION_SKIP_LESSON.equals(action) || ACTION_PREV.equals(action) || ACTION_NEXT.equals(action) || ACTION_PAUSE.equals(action) || ACTION_RESUME.equals(action)) {
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
        autoStart = !manual;
        from = intent == null ? null : intent.getStringExtra(EXTRA_FROM);
        lessonOnly = intent == null ? 0 : intent.getIntExtra(EXTRA_LESSON, 0);
        new Thread(() -> prepare(manual), "news-fetch").start();
        new Thread(() -> Updater.notifyIfNew(this), "news-update-check").start(); // 새 앱 버전이 있으면 알림 한 번
        return START_NOT_STICKY;
    }

    private PendingIntent act(int code, String action) {
        return PendingIntent.getService(this, code, new Intent(this, NewsService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE);
    }

    private void startInForeground(String text) {
        ensureChannel(this);
        // 읽는 중에 알림을 누르면 자막 화면, 가져오는 중이면 앱 화면
        PendingIntent open = PendingIntent.getActivity(this, tts != null ? 6 : 0, new Intent(this, tts != null ? CaptionActivity.class : MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true);
        if (tts != null) { // 읽는 중: 잠금 화면에서도 이전 · 전체 듣기 · 다음 · 멈춤/이어 듣기
            b.addAction(new Notification.Action.Builder(null, "이전", act(3, ACTION_PREV)).build())
             .addAction(new Notification.Action.Builder(null, "전체 듣기", act(2, ACTION_MORE)).build())
             .addAction(new Notification.Action.Builder(null, "다음", act(4, ACTION_NEXT)).build())
             .addAction(paused ? new Notification.Action.Builder(null, "이어 듣기", act(5, ACTION_RESUME)).build()
                               : new Notification.Action.Builder(null, "멈춤", act(1, ACTION_PAUSE)).build());
            if (paused) b.addAction(new Notification.Action.Builder(null, "끄기", act(7, ACTION_STOP)).build());
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
        } else if (lessonOnly > 0) {
            Brief.Lesson l = brief.lesson(lessonOnly);
            script = l == null ? new ArrayList<>() : Brief.lessonLines(l, true, true);
            if (script.isEmpty()) script.add(new Brief.Line("이 회차 원고를 찾지 못했습니다.", null));
        } else if (!brief.playToday && !manual) {
            main.post(this::finish); // 주말·공휴일: 자동으로는 읽지 않는다
            return;
        } else {
            // 직접 넣은 언론사·섹션 순서·농업 키워드: 폰이 그 자리에서 가져와 원고에 넣는다 (녹음 없이 폰 음성)
            Prefs prefs = new Prefs(this);
            long now = System.currentTimeMillis();
            Extras.addCustom(brief, prefs.custom(), now);
            // 해외 농업·비료 시장 뉴스 기사 수
            brief.setLimit(Extras.FERT_WORLD, prefs.fertCount());
            // 국내농업 키워드: 국내 농업 언론사에서 키워드 기사를 먼저 고른다
            brief.prefer((sec, src) -> Extras.FARM.equals(sec.id), prefs.keywords());
            // 해외농업 키워드: 고른 나라의 언론사와 해외 농업·비료 섹션에서 키워드 기사를 먼저 고른다
            List<String> abroad = prefs.abroadKeywords(), countries = prefs.abroadCountries();
            List<String> forms = new ArrayList<>();
            for (String k : abroad) forms.addAll(Abroad.forms(k));
            brief.prefer((sec, src) -> Abroad.FOREIGN_FARM.contains(sec.id) || (!src.country.isEmpty() && countries.contains(src.country)), forms);
            List<Brief.Picked> picked = brief.select(prefs.sources(), prefs.order());
            Extras.addKeywords(brief, picked, prefs.keywords(), now);
            Abroad.addCountryNews(brief, picked, countries, abroad, now);
            boolean evening = Brief.evening(now);
            script = brief.linesOf(picked, Brief.koreanDate(now), evening);
            // 평일 아침이면 뉴스 뒤에 제품 공부 한 회 (주말·공휴일은 진도를 넘기지 않는다. 오후 원고에는 붙이지 않는다, 주인 요청 2026-10-14)
            if (BuildConfig.LESSONS && brief.playToday && !evening && (prefs.lessonOn() || prefs.englishOn()) && !brief.lessons.isEmpty()) {
                script = Brief.withLesson(script, brief.lesson(prefs.lessonForToday(Lessons.numbers(brief.lessons), true)), prefs.lessonOn(), prefs.englishOn());
            }
        }
        if (!manual && lessonOnly == 0) new Prefs(this).markPlayed();
        List<Brief.Line> ready = script;
        // F1을 켰으면 서버 녹음은 받지 않고 폰에서 만든다. 엔진은 여기서(화면 밖 스레드) 미리 띄운다. 못 띄우면 예전처럼
        Crash.f1Crashed(this, new Prefs(this)); // 지난번 F1을 쓰다 앱이 꺼졌으면 F1을 끈다 (차에서 매번 꺼지지 않게)
        useF1 = new Prefs(this).f1On() && F1Voice.ready(this) && F1Voice.engine(this) != null;
        if (useF1) startF1();
        else if (brief != null) fetchClips(brief, ready);
        main.post(() -> speak(ready));
    }

    /** 원고 줄(과 요약·본문) 가운데 서버가 녹음해 둔 것을 원고 순서대로 받는다. 앞 몇 줄만 기다리고(최대 8초) 나머지는 읽는 동안 받는다.
     *  아직 못 받은 줄은 폰 음성으로 읽는다 */
    private void fetchClips(Brief brief, List<Brief.Line> script) {
        if (brief.audioIds.isEmpty()) return;
        // 설정에서 폰 음성을 직접 골랐으면 처음부터 끝까지 그 음성으로 읽는다 (주인 요청 2026-10-13: 본문도 설정한 같은 음성으로)
        if (!new Prefs(this).voice().isEmpty()) { Log.i(TAG, "고른 폰 음성으로 모두 읽음"); return; }
        Set<String> texts = new LinkedHashSet<>();
        for (Brief.Line l : script) { // 원고 줄을 먼저, 전체 듣기 문장은 그 뒤에. 폰이 직접 가져온 기사 줄은 녹음이 없으니 뺀다
            if (!l.text.isEmpty() && !l.local) texts.add(l.text);
        }
        for (Brief.Line l : script) {
            if (l.item != null && !l.local) texts.addAll(l.item.fullSentences());
        }
        // 원고 줄과 본문이 다 녹음돼 있을 때만 녹음 음성으로 읽는다. 하나라도 빠지면 모두 폰 음성: 읽다가 목소리가 바뀌지 않게
        // 제품 공부 줄은 따로 센다: 녹음이 덜 됐어도 뉴스는 녹음 음성으로 읽고, 제품 공부만 폰 음성으로 읽는다
        Set<String> lessonTexts = new HashSet<>();
        for (Brief.Line l : script) if (l.lesson > 0) lessonTexts.add(l.text);
        int missing = 0;
        for (String t : texts) if (!t.isEmpty() && !lessonTexts.contains(t) && !brief.audioIds.contains(Brief.audioId(t))) missing++;
        if (missing > 0) { Log.i(TAG, "녹음 안 된 줄 " + missing + "개: 오늘은 폰 음성으로 모두 읽음"); return; }
        File dir = new File(getCacheDir(), "audio2"); // 2: 서버가 스테레오로 다시 녹음한 파일 (예전 폴더는 아래에서 지운다)
        deleteTree(new File(getCacheDir(), "audio"));
        dir.mkdirs();
        String base = BuildConfig.NEWS_URL.substring(0, BuildConfig.NEWS_URL.lastIndexOf('/') + 1) + brief.audioBase;
        Set<String> keep = new HashSet<>();
        List<Future<?>> first = new ArrayList<>();
        pool = Executors.newFixedThreadPool(4);
        for (String t : texts) {
            if (t.isEmpty()) continue;
            String id = Brief.audioId(t);
            if (!brief.audioIds.contains(id)) continue;
            File f = new File(dir, id + ".mp3");
            keep.add(f.getName());
            Future<?> job = pool.submit(() -> {
                if (stopped) return;
                try {
                    if (!f.exists() || f.length() == 0) download(base + id + ".mp3", f);
                    clips.put(t, f);
                } catch (Exception e) {
                    Log.w(TAG, "녹음 파일 받기 실패 " + id + ": " + e);
                }
            });
            if (first.size() < 4) first.add(job);
        }
        pool.shutdown();
        long end = System.currentTimeMillis() + 8000;
        for (Future<?> job : first) {
            try { job.get(Math.max(1, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS); } catch (Exception ignored) { }
        }
        File[] old = dir.listFiles(); // 어제 받은 파일은 지운다
        if (old != null) for (File f : old) if (!keep.contains(f.getName()) && !f.getName().endsWith(".part")) f.delete();
        Log.i(TAG, "녹음 " + keep.size() + "줄 중 " + clips.size() + "줄 받고 시작");
    }

    private static void deleteTree(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir.delete();
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        File part = new File(to.getPath() + ".part");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(part)) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            }
            if (!part.renameTo(to)) throw new IllegalStateException("저장 실패");
        } finally {
            c.disconnect();
            part.delete();
        }
    }

    /** 읽을 차례 하나: 글자(녹음이 있으면 그 파일, 없으면 폰 음성) 또는 silence 밀리초 쉼. id가 L로 시작하면 원고 줄 번호 */
    private static final class Step {
        final String id, text;
        final int silence;
        Step(String id, String text, int silence) { this.id = id; this.text = text; this.silence = silence; }
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
            attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            tts.setAudioAttributes(attrs);
            audio = getSystemService(AudioManager.class);
            // 통화·내비 안내·다른 앱 소리가 끼어들면 멈추고, 끝나면 멈춘 기사부터 이어 읽는다
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this::onFocus, main).build();
            audio.requestAudioFocus(focus); // 라디오·음악은 잠시 멈췄다가 끝나면 다시 나온다
            startedAt = System.currentTimeMillis();
            watchNavigation();
            watchCalls();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(() -> done(id)); }
                @Override public void onError(String id) { main.post(() -> done(id)); }
            });
            startSession();
            startInForeground(PLAYING_TEXT);
            openCaptions();
            int first = lineOf(from != null ? from : new Prefs(this).resumeTitle());
            from = null;
            if (first > 0) jump(first); else play(stepsFrom(0));
        });
    }

    private static final String PLAYING_TEXT = "읽는 중 · 이전 / 전체 듣기 / 다음";

    /** 지금 읽던 것을 끊고 steps를 처음부터 읽는다 */
    private void play(List<Step> steps) {
        halt();
        // 건너뛰면 아직 시작 안 한 F1 만들기는 버린다: 새 자리의 차례부터 만들게
        for (Map.Entry<String, Future<File>> e : f1Jobs.entrySet()) if (!e.getValue().isDone() && e.getValue().cancel(false)) f1Jobs.remove(e.getKey());
        queue = steps;
        pos = 0;
        advance();
    }

    /** 지금 차례(폰 음성·녹음·쉼)를 끊는다. 끊긴 차례의 '끝남'은 gen이 달라 무시된다 */
    private void halt() {
        gen++;
        main.removeCallbacks(afterSilence);
        releasePlayer();
        if (tts != null) tts.stop();
    }

    private void releasePlayer() {
        if (player != null) { player.release(); player = null; }
    }

    /** 다음 차례를 튼다. 다 읽었으면 끝낸다 */
    private void advance() {
        if (stopped || paused || tts == null) return;
        if (pos >= queue.size()) { completed = true; finish(); return; }
        Step s = queue.get(pos++);
        holdButtons(true); // 차례마다 다시 틀어 '가장 최근에 소리 낸 앱'이 되게
        if (s.id.startsWith("L")) {
            int i = Integer.parseInt(s.id.substring(1, s.id.indexOf('.')));
            current = i;
            showLine(i);
        }
        if (s.id.startsWith("Q")) new Prefs(this).markLessonDone(Integer.parseInt(s.id.substring(1, s.id.indexOf('.'))));
        if (s.silence > 0) { main.postDelayed(afterSilence, s.silence); return; }
        showCaption(s);
        if (useF1) {
            for (int k = pos - 1; k < Math.min(queue.size(), pos - 1 + F1_AHEAD); k++) f1Job(queue.get(k));
            if (playF1(s, gen, System.currentTimeMillis())) return;
        }
        File f = clips.get(s.text);
        if (f != null && playClip(f)) return;
        speakPhone(s);
    }

    /** 폰 음성으로 한 차례 */
    private void speakPhone(Step s) {
        boolean en = isEnglish(s);
        if (en != phoneEnglish) { // 농업영어의 영어 줄은 폰 음성도 영어로 (녹음이 없을 때)
            phoneEnglish = en;
            if (en) tts.setLanguage(Locale.US);
            else { tts.setLanguage(Locale.KOREAN); Voices.apply(tts, new Prefs(this)); }
        }
        tts.speak(en ? s.text : Pron.say(s.text), TextToSpeech.QUEUE_FLUSH, speakParams, gen + ":" + s.id); // 자막은 쓴 글, 소리는 들리는 대로
    }

    private void startF1() {
        f1Dir = new File(getCacheDir(), "f1clips");
        deleteTree(f1Dir); // 지난번에 만든 소리는 지운다
        f1Dir.mkdirs();
        f1Pool = Executors.newSingleThreadExecutor(); // 엔진이 안에서 여러 코어를 쓴다: 한 번에 하나씩
    }

    /** 이 차례의 F1 소리 만들기 (이미 맡겼으면 그것). 쉼 차례는 null */
    private Future<File> f1Job(Step s) {
        if (s.silence > 0 || s.text.isEmpty() || f1Pool == null || f1Pool.isShutdown()) return null;
        boolean en = isEnglish(s);
        String key = (en ? "en|" : "ko|") + s.text;
        return f1Jobs.computeIfAbsent(key, k -> f1Pool.submit(() -> {
            F1Engine e = F1Voice.engine(this);
            if (e == null || stopped) return null;
            File out = new File(f1Dir, Brief.audioId(k) + ".wav");
            if (out.exists()) return out;
            long t = System.currentTimeMillis();
            float[] pcm = F1Voice.synth(this, e, en ? s.text : Pron.say(s.text), en ? "en" : "ko", F1Voice.speed(new Prefs(this).rate()));
            F1Engine.writeWav(pcm, e.sampleRate, out);
            float x = pcm.length / (float) e.sampleRate / Math.max(0.001f, (System.currentTimeMillis() - t) / 1000f);
            new Prefs(this).setF1Speed(x);
            Log.i(TAG, "F1 " + s.text.length() + "자 · 실시간의 " + String.format(Locale.ROOT, "%.1f", x) + "배");
            return out;
        }));
    }

    /** F1 소리로 튼다. 아직 만드는 중이면 기다렸다가(F1_WAIT_MS까지) 틀고, 못 만들면 false (폰 음성으로) */
    private boolean playF1(Step s, int g, long since) {
        Future<File> job = f1Job(s);
        if (job == null) return false;
        if (!job.isDone()) {
            if (System.currentTimeMillis() - since > F1_WAIT_MS) { Log.w(TAG, "F1이 늦어 이 차례는 폰 음성으로"); return false; }
            main.postDelayed(() -> { if (g == gen && !stopped && !paused && !playF1(s, g, since)) speakPhone(s); }, 80);
            return true;
        }
        try {
            File f = job.get();
            return f != null && playClip(f);
        } catch (Exception e) {
            Log.w(TAG, "F1 만들기 실패, 폰 음성으로: " + e);
            return false;
        }
    }

    /** 폰 음성이 지금 영어로 맞춰져 있나 */
    private boolean phoneEnglish;

    private boolean isEnglish(Step s) {
        if (!s.id.startsWith("L")) return false;
        int i = Integer.parseInt(s.id.substring(1, s.id.indexOf('.')));
        return i < lines.size() && lines.get(i).en;
    }

    private void showCaption(Step s) {
        boolean full = s.id.startsWith("S");
        int i = Integer.parseInt(s.id.substring(1, s.id.indexOf('.')));
        Brief.Item it = i < lines.size() ? lines.get(i).item : null;
        int lesson = i < lines.size() ? lines.get(i).lesson : 0;
        boolean english = i < lines.size() && lines.get(i).english;
        caption = lesson > 0 ? new Caption(english ? "농업영어" : "바이오플랜 제품 공부", lesson + "회", s.text, false, lesson, english)
                : it == null ? new Caption("뉴스클리핑", "", s.text, false) : new Caption(it.source, it.title, s.text, full);
        Runnable c = onCaption;
        if (c != null) c.run();
    }

    /** 차에서 자동으로 읽기 시작하면 자막 화면을 띄운다. 화면 밖(백그라운드)에서 띄우려면 '다른 앱 위에 표시' 허용이 필요하다 */
    private void openCaptions() {
        if (!autoStart || !new Prefs(this).captions() || !Settings.canDrawOverlays(this)) return;
        try {
            startActivity(new Intent(this, CaptionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.w(TAG, "자막 화면을 띄우지 못함: " + e);
        }
    }

    /** 녹음 파일을 앱에서 직접 튼다 (치찰음 줄이기와 스테레오는 서버가 녹음할 때 해 둔다) */
    private boolean playClip(File f) {
        int g = gen;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(attrs);
            mp.setDataSource(f.getPath());
            mp.setOnCompletionListener(p -> done(g + ":"));
            mp.setOnErrorListener((p, what, extra) -> { done(g + ":"); return true; });
            mp.prepare();
            mp.start();
            player = mp;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "녹음 재생 실패, 폰 음성으로: " + e);
            mp.release();
            f.delete();
            clips.values().remove(f);
            return false;
        }
    }

    /** 한 차례가 끝남 ("세대:id"). 지금 세대의 것만 다음 차례로 넘어간다 */
    private void done(String id) {
        if (!id.startsWith(gen + ":")) return;
        releasePlayer();
        advance();
    }

    /** start번째 줄부터 끝까지의 차례. 긴 줄은 음성 엔진 한도에 맞춰 나누고, 기사 사이는 잠깐 쉰다 */
    private List<Step> stepsFrom(int start) {
        List<Step> out = new ArrayList<>();
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        for (int i = start; i < lines.size(); i++) {
            Brief.Line line = lines.get(i);
            if (line.text.isEmpty()) {
                out.add(new Step("P" + i, "", 700));
            } else {
                List<String> parts = Rules.chunks(line.text, max);
                for (int k = 0; k < parts.size(); k++) out.add(new Step("L" + i + "." + k, parts.get(k), 0));
                if (line.item != null) out.add(new Step("P" + i + ".h", "", 450)); // 헤드라인 뒤에 숨 한 번: 기사끼리 붙어 들리지 않게
                if (line.pause > 0) out.add(new Step("P" + i + ".r", "", line.pause)); // 농업영어: 따라 말할 틈
                // 제품 공부 회의 마지막 줄까지 읽으면 완료 (중간에 끄면 완료가 아니다)
                if (line.lesson > 0 && (i + 1 >= lines.size() || lines.get(i + 1).lesson != line.lesson)) out.add(new Step("Q" + line.lesson + ".", "", 300));
            }
        }
        return out;
    }

    private boolean[] itemFlags() {
        boolean[] f = new boolean[lines.size()];
        for (int i = 0; i < f.length; i++) f[i] = lines.get(i).item != null;
        return f;
    }

    /** 이전 기사 (멈춤 중이면 멈춘 기사 기준). 제품 공부·농업영어 중이면 이전 문단 */
    private int prevTarget() {
        if (inLesson()) return Rules.prevParagraph(lessonOf(), current);
        return Rules.prevItem(itemFlags(), current);
    }

    private boolean inLesson() { return current < lines.size() && lines.get(current).lesson > 0; }

    /** 줄마다 제품 공부(1)·농업영어(2)·그 밖(0), 빈 줄은 -1 */
    private int[] lessonOf() {
        int[] f = new int[lines.size()];
        for (int i = 0; i < f.length; i++) {
            Brief.Line l = lines.get(i);
            f[i] = l.text.isEmpty() ? -1 : l.lesson == 0 ? 0 : l.english ? 2 : 1;
        }
        return f;
    }

    /** 제품 공부 중이면 같은 회의 농업영어 첫 줄로, 농업영어 중(또는 영어가 없으면)이면 그 회 다음 줄(맺음말)로 */
    private void skipLesson() {
        if (!inLesson()) return;
        jump(Rules.skipLesson(lessonOf(), current));
    }

    /** 다음 기사. 'OO 소식입니다.'를 읽는 중이면 바로 뒤 기사가 지금 기사이므로 그다음. 제품 공부·농업영어 중이면 다음 문단 */
    private int nextTarget() {
        if (inLesson()) return Rules.nextParagraph(lessonOf(), current);
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
        play(stepsFrom(start));
    }

    /** '전체 듣기': 지금(또는 방금) 읽은 기사의 본문 앞부분을 문장마다(자막도 문장마다) 읽고, 그다음 줄부터 이어 읽는다.
     *  읽는 중에 '다음'(자막 화면을 왼쪽으로 쓸기·핸들 다음 버튼)을 누르면 전체 듣기를 끝내고 다음 기사 헤드라인으로 */
    private void full() {
        if (tts == null || stopped || lines.isEmpty()) return;
        if (inLesson()) { if (paused) resume(); return; } // 제품 공부·농업영어에는 펼칠 본문이 없다: 멈춰 있었으면 이어 듣기
        int i = Math.min(current, lines.size() - 1);
        while (i > 0 && lines.get(i).item == null) i--;
        Brief.Item it = lines.get(i).item;
        List<String> sentences = it == null ? new ArrayList<>() : new ArrayList<>(it.fullSentences());
        if (sentences.isEmpty()) sentences.add(it == null ? "전체로 들을 기사가 아직 없습니다." : "이 기사는 본문을 가져오지 못했습니다.");
        current = i;
        if (paused) unpause();
        int max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength());
        List<Step> steps = new ArrayList<>();
        int k = 0;
        for (String sen : sentences) for (String part : Rules.chunks(sen, max)) steps.add(new Step("S" + i + "." + k++, part, 0));
        steps.add(new Step("S" + i + ".p", "", 600));
        steps.addAll(stepsFrom(i + 1));
        play(steps);
    }

    private void onFocus(int change) {
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            // 통화가 끝나면 이어 읽는 건 차(블루투스 오디오)에서만: 폰 스피커로 듣다 받은 통화 뒤에는 멈춘 채 기다린다
            // 음성 비서(마이크를 쓴 앱)가 잠깐 가져간 것이면 스피커로 듣고 있었어도 이어 읽는다
            boolean assistant = sawMic && !callSincePause && !callNow();
            if (paused && resumeOnGain && System.currentTimeMillis() - pausedAt < AUTO_RESUME_MS && (bluetoothOut() || assistant)) { stopAssistantWatch(); resume(); }
            else if (paused) keepPaused(); // 손으로 멈췄거나 오래 멈춤: 이어 듣기를 누를 때까지 기다린다
        } else if (change == AudioManager.AUDIOFOCUS_LOSS && autoStart && retakes < 3 && System.currentTimeMillis() - startedAt < RETAKE_WINDOW_MS && !micBusy()) {
            // 차에 연결되자마자 애플 뮤직·삼성 뮤직 같은 앱이 저절로 재생을 시작함: 소리를 되찾고 자막 화면을 다시 맨 위로
            retakes++;
            Log.i(TAG, "다른 앱이 소리를 가져가서 되찾음 " + retakes);
            main.postDelayed(() -> {
                if (stopped || audio == null) return;
                audio.requestAudioFocus(focus);
                if (session != null) session.setActive(true);
                openCaptions();
            }, 700);
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            // 다른 앱이 소리를 가져감: 음악 앱이면 이어 듣기(앱·알림·핸들 재생 버튼)를 기다리고,
            // 제미나이 같은 음성 비서(마이크를 씀)면 그것이 닫힌 뒤 저절로 이어 읽는다
            boolean was = paused;
            pause(false);
            if (!was && paused) { watchAssistant(); startInForeground("잠깐 멈춤 · 음성 비서가 끝나면 이어 읽습니다"); }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            boolean was = paused;
            pause(true); // 통화·내비 안내·음성 비서: 끝나면 저절로 이어 읽는다
            if (!was && paused) watchAssistant();
        }
    }

    /** 내비 앱 가운데에는 안내 음성을 낼 때 소리 차례(오디오 포커스)를 요청하지 않고 그냥 섞어 내는 것이 있다.
     *  재생 중인 소리 목록에서 '내비 안내' 용도의 소리가 보이면 멈추고, 사라지면 1초 뒤 이어 읽는다 */
    private void watchNavigation() {
        playbackWatch = new AudioManager.AudioPlaybackCallback() {
            @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                boolean nav = false;
                for (AudioPlaybackConfiguration c : configs) {
                    AudioAttributes a = c.getAudioAttributes();
                    if (a != null && a.getUsage() == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) nav = true;
                }
                if (nav) {
                    main.removeCallbacks(navResume);
                    if (!paused) { pause(true); navPaused = true; }
                } else if (navPaused) {
                    main.removeCallbacks(navResume);
                    main.postDelayed(navResume, 1000);
                }
            }
        };
        audio.registerAudioPlaybackCallback(playbackWatch, main);
    }

    private void pause(boolean autoResume) {
        if (tts == null || stopped) return;
        stopAssistantWatch();
        if (paused) { // 이미 멈춤: 손으로 멈춘 것을 통화가 '잠깐 멈춤'으로 바꾸면 통화 뒤 저절로 켜진다
            if (!autoResume) keepPaused();
            return;
        }
        resumeOnGain = autoResume;
        pausedAt = System.currentTimeMillis();
        callSincePause = callNow();
        sawMic = false;
        if (!autoResume && audio != null && focus != null) audio.abandonAudioFocusRequest(focus); // 손으로 멈추면 소리 차례를 내준다: 통화·음악이 끝나도 다시 켜지지 않게
        paused = true;
        stateChanged();
        halt();
        holdButtons(false);
        setState(PlaybackState.STATE_PAUSED);
        startInForeground(autoResume ? "잠깐 멈춤 · 통화나 안내가 끝나면 이어 읽습니다" : "멈춤 · '이어 듣기'를 누르면 멈춘 기사부터 읽습니다");
        main.removeCallbacks(pauseLimit);
        main.postDelayed(pauseLimit, PAUSE_LIMIT_MS);
    }

    /** 소리가 블루투스(차·이어폰)로 나가는지 */
    private boolean bluetoothOut() {
        if (audio == null) return false;
        for (android.media.AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true;
        }
        return false;
    }

    /** 마이크를 쓰는 앱(음성 비서·통화)이 있는지 */
    private boolean micBusy() {
        return audio != null && !audio.getActiveRecordingConfigurations().isEmpty();
    }

    /** 전화 통화 중인지 (음성 비서가 쓸 수도 있는 인터넷 통화 모드는 빼고 본다) */
    private boolean callNow() {
        int mode = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING;
    }

    /** 소리를 빼앗긴 직후부터 1초마다: 음성 비서가 듣고 말하는 동안 기다렸다가, 닫히면 멈춘 기사부터 이어 읽는다 */
    private void watchAssistant() {
        stopAssistantWatch();
        assistWatch = true;
        sawMic = micBusy();
        quietTicks = 0;
        main.postDelayed(assistCheck, 1000);
    }

    private void stopAssistantWatch() {
        assistWatch = false;
        main.removeCallbacks(assistCheck);
    }

    private void checkAssistant() {
        if (!assistWatch || stopped || !paused || audio == null) { stopAssistantWatch(); return; }
        boolean mic = micBusy();
        sawMic |= mic;
        boolean call = callNow();
        callSincePause |= call;
        int r = Rules.afterAssistant(sawMic, mic, audio.isMusicActive(), callSincePause, System.currentTimeMillis() - pausedAt, AUTO_RESUME_MS);
        if (r == Rules.ASSIST_GIVE_UP) {
            stopAssistantWatch();
            if (!resumeOnGain) keepPaused(); // 소리를 아주 가져간 앱(음악 등): 이어 듣기를 기다린다. 잠깐 가져간 것은 돌려받을 때 판단
            return;
        }
        quietTicks = r == Rules.ASSIST_QUIET ? quietTicks + 1 : 0;
        if (quietTicks >= 2) {
            stopAssistantWatch();
            Log.i(TAG, "음성 비서가 끝나 이어 읽음");
            resume();
            if (session != null) session.setActive(true); // 핸들·알림의 재생 버튼이 다시 이 앱으로 오게
            return;
        }
        main.postDelayed(assistCheck, 1000);
    }

    /** 저절로 이어 읽지 않는 멈춤으로 바꾼다 */
    private void keepPaused() {
        stopAssistantWatch();
        resumeOnGain = false;
        navPaused = false;
        main.removeCallbacks(navResume);
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        startInForeground("멈춤 · '이어 듣기'를 누르면 멈춘 기사부터 읽습니다");
    }

    /** 통화 중인지, 통화가 언제 끝났는지 지켜본다 (안드로이드 12부터) */
    private void watchCalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AudioManager.OnModeChangedListener w = mode -> {
            boolean call = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING;
            if (inCall && !call) callEndedAt = System.currentTimeMillis();
            inCall = call;
            if (paused && (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE)) callSincePause = true;
        };
        audio.addOnModeChangedListener(getMainExecutor(), w);
        modeWatch = w;
    }

    /** 차·이어폰의 '재생' 버튼: 통화 중이거나 통화가 막 끝났을 때 온 신호는 차가 저절로 보낸 것이라 무시한다 */
    private void playButton() {
        int mode = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
        if (inCall || mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
                || System.currentTimeMillis() - callEndedAt < AFTER_CALL_MS) {
            Log.i(TAG, "통화 직후 재생 신호는 무시");
            return;
        }
        resume();
    }

    private void resume() {
        if (tts == null || stopped || !paused) return;
        stopAssistantWatch();
        unpause();
        jump(Math.min(current, lines.size() - 1));
    }

    private void unpause() {
        paused = false;
        stateChanged();
        main.removeCallbacks(pauseLimit);
        audio.requestAudioFocus(focus);
        setState(PlaybackState.STATE_PLAYING);
        startInForeground(PLAYING_TEXT);
    }

    /** 제목이 title인 기사 줄. 없으면 -1 */
    private int lineOf(String title) {
        if (title == null || title.isEmpty()) return -1;
        for (int i = 0; i < lines.size(); i++) {
            Brief.Item it = lines.get(i).item;
            if (it != null && it.title.equals(title)) return i;
        }
        return -1;
    }

    /** 웹 기사 목록의 ▶: 그 기사부터 읽는다 (원고에 없는 기사면 멈춘 곳부터 이어 읽기만) */
    private void playTitle(String title) {
        if (stopped) return;
        if (tts == null || session == null) { from = title; return; } // 아직 원고를 가져오는 중: 시작할 때 그 기사부터
        int i = lineOf(title);
        if (paused) unpause();
        else if (i < 0) return;
        jump(i >= 0 ? i : Math.min(current, lines.size() - 1));
    }

    /** 멈춘(끈) 기사: 기사 줄이면 그 기사, 섹션·언론사 소개를 읽던 중이면 바로 뒤 기사 (첫인사·맺음말이면 없음) */
    private Brief.Item stoppedAt() {
        if (current <= 0) return null; // 첫인사 중이면 처음부터
        for (int i = current; i < lines.size(); i++) {
            if (lines.get(i).item != null) return lines.get(i).item;
        }
        return null;
    }

    /**
     * 차 핸들의 다음·이전 버튼은 안드로이드가 '가장 최근에 소리를 내기 시작한 앱'에 보낸다. 폰 음성은 음성 엔진 앱이 소리를 내고,
     * 차에 연결되면 음악 앱이 먼저 재생을 시작하므로, 그대로 두면 버튼이 음악 앱으로 간다.
     * 읽는 동안 이 앱이 소리 없는 소리를 함께 틀고 차례마다 다시 시작해 버튼을 이 앱이 받는다.
     */
    private void holdButtons(boolean on) {
        try {
            if (!on) { if (hold != null) hold.pause(); return; }
            if (hold == null) {
                int rate = 8000, frames = rate / 2;
                hold = new AudioTrack.Builder().setAudioAttributes(attrs)
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(frames * 4).build();
                hold.write(new short[frames * 2], 0, frames * 2);
                hold.setLoopPoints(0, frames, -1);
            } else {
                hold.pause();
            }
            hold.play();
        } catch (RuntimeException e) {
            Log.w(TAG, "버튼 받기용 소리를 못 틈: " + e);
            if (hold != null) { hold.release(); hold = null; }
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
            // 차 버튼이 앱까지 오는지 앱 화면 맨 아래에 남긴다 (다음 버튼이 안 먹힐 때 원인 찾기)
            @Override public boolean onMediaButtonEvent(Intent i) {
                android.view.KeyEvent k = i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (k != null && k.getAction() == android.view.KeyEvent.ACTION_DOWN) new Prefs(NewsService.this).noteButton(android.view.KeyEvent.keyCodeToString(k.getKeyCode()).replace("KEYCODE_MEDIA_", ""));
                return super.onMediaButtonEvent(i);
            }
            @Override public void onSkipToNext() { handle(ACTION_NEXT); }
            @Override public void onSkipToPrevious() { handle(ACTION_PREV); }
            @Override public void onFastForward() { full(); }
            @Override public void onPause() { pause(false); }
            @Override public void onPlay() { playButton(); }
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
        // 멈춤·끄기로 끝나면 그 기사를 기억해 오늘 다시 들을 때 거기서 시작하고, 끝까지 다 들었으면 다음엔 처음부터
        if (tts != null && !stopped) {
            Brief.Item at = completed ? null : stoppedAt();
            new Prefs(this).setResumeTitle(at == null ? "" : at.title);
        }
        stopped = true;
        main.removeCallbacks(pauseLimit);
        main.removeCallbacks(afterSilence);
        releasePlayer();
        if (pool != null) pool.shutdownNow();
        if (f1Pool != null) f1Pool.shutdownNow();
        if (hold != null) { hold.release(); hold = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (session != null) { session.setActive(false); session.release(); session = null; }
        main.removeCallbacks(navResume);
        stopAssistantWatch();
        if (audio != null && playbackWatch != null) audio.unregisterAudioPlaybackCallback(playbackWatch);
        if (audio != null && modeWatch != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.removeOnModeChangedListener((AudioManager.OnModeChangedListener) modeWatch);
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        if (running == this) running = null;
        caption = null;
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
