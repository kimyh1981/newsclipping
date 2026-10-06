package io.github.kimyh1981.newsclipping;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 설정 화면 (아이폰 설정 앱처럼 묶음 목록): 지금 듣기 → 처음 한 번만(권한) → 자동 재생 → 듣기 → 더 보기.
 * 설정을 마치면 앱을 닫아도 차에 타면 자동으로 읽는다.
 */
public class MainActivity extends Activity {
    private static final String SITE = "https://kimyh1981.github.io/newsclipping/";
    private Prefs prefs;
    private TextView footer, permVal, batteryVal, carVal, timeVal, sourcesVal, voiceVal, rateVal;
    private Switch enabledSw, weekdaysSw, softenSw;
    private ImageView playBtn;
    private TextToSpeech preview; // 목소리·빠르기를 고를 때 미리 들려준다
    private boolean previewReady;
    private Runnable afterPreview;
    private boolean loadingSources;

    // 아이폰 설정 앱의 색 (밝은 화면 / 어두운 화면)
    private boolean dark;
    private int bg, card, label, secondary, separator, tint, green, red;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        NewsService.ensureChannel(this);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = dark ? 0xFF000000 : 0xFFF2F2F7;
        card = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        label = dark ? 0xFFFFFFFF : 0xFF000000;
        secondary = dark ? 0xFF8E8E93 : 0xFF8A8A8E;
        separator = dark ? 0xFF38383A : 0xFFC6C6C8;
        tint = dark ? 0xFF0A84FF : 0xFF007AFF;
        green = dark ? 0xFF30D158 : 0xFF34C759;
        red = dark ? 0xFFFF453A : 0xFFFF3B30;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(24), dp(16), dp(32));

        TextView title = text("뉴스클리핑", 34, label);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(8), 0, dp(4));
        col.addView(title);
        TextView intro = text("차 블루투스가 연결되면 평일 아침에 고른 언론사의 헤드라인을 읽어 드립니다.", 15, secondary);
        intro.setPadding(dp(4), 0, dp(4), dp(16));
        col.addView(intro);

        // 지금 듣기 / 멈춤
        LinearLayout play = new LinearLayout(this);
        play.setOrientation(LinearLayout.HORIZONTAL);
        // 재생 버튼은 누를 때마다 재생 ↔ 일시정지 (일시정지하면 멈춘 기사부터 이어 읽는다)
        playBtn = iconPill(Glyph.PLAY, "지금 듣기", tint, 0xFFFFFFFF, v -> {
            int st = NewsService.state();
            if (st == NewsService.PLAYING) NewsService.control(NewsService.ACTION_PAUSE);
            else if (st == NewsService.PAUSED) NewsService.control(NewsService.ACTION_RESUME);
            else NewsService.start(this, true);
        });
        play.addView(playBtn, new LinearLayout.LayoutParams(0, dp(50), 1f));
        play.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        play.addView(iconPill(Glyph.STOP, "멈춤", card, red, v -> NewsService.stopIfRunning()), new LinearLayout.LayoutParams(0, dp(50), 1f));
        col.addView(play);

        // 이전 기사 / 듣던 기사 전체 듣기 / 다음 기사 (읽는 중에만 동작)
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(10), 0, 0);
        nav.addView(iconPill(Glyph.PREV, "이전 기사", card, tint, v -> NewsService.control(NewsService.ACTION_PREV)), new LinearLayout.LayoutParams(0, dp(46), 1f));
        nav.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        TextView fullBtn = text("전체 듣기", 16, tint);
        fullBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fullBtn.setGravity(Gravity.CENTER);
        fullBtn.setBackground(pressable(rounded(card, dp(14))));
        fullBtn.setOnClickListener(v -> NewsService.control(NewsService.ACTION_MORE));
        nav.addView(fullBtn, new LinearLayout.LayoutParams(0, dp(46), 1f));
        nav.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        nav.addView(iconPill(Glyph.NEXT, "다음 기사", card, tint, v -> NewsService.control(NewsService.ACTION_NEXT)), new LinearLayout.LayoutParams(0, dp(46), 1f));
        col.addView(nav);

        LinearLayout today = section(col, "오늘 뉴스", null);
        sourcesVal = row(today, "들을 언론사", v -> pickSources(), true);
        row(today, "오늘 기사 목록 (웹)", v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SITE))), false).setText("");

        LinearLayout setup = section(col, "처음 한 번만", "두 가지를 허용해야 차에 탔을 때 앱을 열지 않아도 자동으로 읽습니다.");
        permVal = row(setup, "블루투스·알림 권한", v -> askPermissions(), true);
        batteryVal = row(setup, "배터리 사용 제한 없음", v -> askBattery(), false);

        LinearLayout auto = section(col, "자동 재생", "공휴일은 서버가 알아서 건너뜁니다.");
        enabledSw = switchRow(auto, "차에 타면 자동으로 읽기", on -> prefs.setEnabled(on), true);
        carVal = row(auto, "차 블루투스", v -> pickCar(), true);
        timeVal = row(auto, "재생 시간", v -> pickTime(), true);
        weekdaysSw = switchRow(auto, "평일에만", on -> prefs.setWeekdaysOnly(on), false);

        LinearLayout listen = section(col, "듣기", "목소리와 빠르기는 고르는 동안 미리 들려 드립니다.");
        voiceVal = row(listen, "목소리", v -> withPreview(this::pickVoice), true);
        rateVal = row(listen, "말 빠르기", v -> withPreview(this::pickRate), true);
        softenSw = switchRow(listen, "치찰음 줄이기 (차 블루투스)", on -> prefs.setSoften(on), false);

        footer = text("", 13, secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        col.addView(footer);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        if (preview != null) preview.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        NewsService.onStateChange = this::showPlayState;
        showPlayState();
        refresh();
    }

    @Override
    protected void onPause() {
        NewsService.onStateChange = null;
        super.onPause();
    }

    private void showPlayState() {
        boolean playing = NewsService.state() == NewsService.PLAYING;
        playBtn.setImageDrawable(new Glyph(playing ? Glyph.PAUSE : Glyph.PLAY, 0xFFFFFFFF, dp(22)));
        playBtn.setContentDescription(playing ? "일시정지" : NewsService.state() == NewsService.PAUSED ? "이어 듣기" : "지금 듣기");
    }

    private void refresh() {
        status(permVal, hasBluetoothPermission() && hasNotifyPermission());
        status(batteryVal, batteryOk());
        carVal.setText(prefs.carAddress().isEmpty() ? "모든 오디오 기기" : prefs.carName());
        timeVal.setText(Rules.hhmm(prefs.start()) + " – " + Rules.hhmm(prefs.end()));
        Set<String> chosen = prefs.sources();
        sourcesVal.setText(chosen == null ? "기본" : chosen.size() + "곳");
        voiceVal.setText(prefs.voice().isEmpty() ? "자동" : "직접 고름");
        rateVal.setText(Rules.rateLabel(prefs.rate()));
        enabledSw.setChecked(prefs.enabled());
        weekdaysSw.setChecked(prefs.weekdaysOnly());
        softenSw.setChecked(prefs.soften());
        String last = prefs.lastPlayed();
        footer.setText("버전 " + BuildConfig.VERSION_NAME + (last.isEmpty() ? "" : " · 마지막 자동 재생 " + last));
    }

    private void status(TextView v, boolean ok) {
        v.setText(ok ? "허용됨" : "허용 필요");
        v.setTextColor(ok ? green : red);
    }

    // ── 아이폰 설정 앱 모양의 화면 조각 ──

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private Drawable pressable(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1F000000), content, null);
    }

    /** 글자 없이 기호(재생·멈춤·이전·다음)만 그린 버튼. 글꼴마다 ▶와 ■ 크기가 달라서 직접 같은 크기로 그린다 */
    private ImageView iconPill(int kind, String label, int fill, int color, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new Glyph(kind, color, dp(22)));
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setContentDescription(label);
        v.setBackground(pressable(rounded(fill, dp(14))));
        v.setOnClickListener(l);
        return v;
    }

    /** size×size 칸에 그리는 기호: 재생 삼각형, 같은 칸에서 같은 크기로 보이는 정사각형, 이전·다음(막대 + 삼각형) */
    private static final class Glyph extends Drawable {
        static final int PLAY = 0, STOP = 1, PREV = 2, NEXT = 3, PAUSE = 4;
        private final int kind;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int kind, int color, int size) {
            this.kind = kind;
            this.size = size;
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = size, x = b.left, y = b.top;
            if (kind == PLAY) {
                Path p = new Path();
                p.moveTo(x + s * 0.12f, y);
                p.lineTo(x + s, y + s / 2f);
                p.lineTo(x + s * 0.12f, y + s);
                p.close();
                c.drawPath(p, paint);
            } else if (kind == PAUSE) {
                float w = s * 0.3f;
                c.drawRoundRect(new RectF(x + s * 0.14f, y + s * 0.06f, x + s * 0.14f + w, y + s * 0.94f), s * 0.06f, s * 0.06f, paint);
                c.drawRoundRect(new RectF(x + s * 0.86f - w, y + s * 0.06f, x + s * 0.86f, y + s * 0.94f), s * 0.06f, s * 0.06f, paint);
            } else if (kind == PREV || kind == NEXT) {
                float bar = s * 0.16f, top = y + s * 0.1f, bottom = y + s * 0.9f;
                Path p = new Path();
                if (kind == NEXT) {
                    p.moveTo(x + s * 0.08f, top);
                    p.lineTo(x + s * 0.76f, y + s / 2f);
                    p.lineTo(x + s * 0.08f, bottom);
                    c.drawRect(x + s * 0.8f, top, x + s * 0.8f + bar, bottom, paint);
                } else {
                    p.moveTo(x + s * 0.92f, top);
                    p.lineTo(x + s * 0.24f, y + s / 2f);
                    p.lineTo(x + s * 0.92f, bottom);
                    c.drawRect(x + s * 0.2f - bar, top, x + s * 0.2f, bottom, paint);
                }
                p.close();
                c.drawPath(p, paint);
            } else {
                float in = s * 0.1f;
                c.drawRoundRect(new RectF(x + in, y + in, x + s - in, y + s - in), s * 0.08f, s * 0.08f, paint);
            }
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** 회색 소제목 + 흰 둥근 묶음 + 회색 설명. 묶음(행을 담는 곳)을 돌려준다 */
    private LinearLayout section(LinearLayout parent, String header, String note) {
        TextView h = text(header, 13, secondary);
        h.setPadding(dp(16), dp(28), dp(16), dp(6));
        parent.addView(h);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(rounded(card, dp(12)));
        group.setClipToOutline(true);
        parent.addView(group);
        if (note != null) {
            TextView n = text(note, 13, secondary);
            n.setPadding(dp(16), dp(6), dp(16), 0);
            parent.addView(n);
        }
        return group;
    }

    private LinearLayout line(LinearLayout group, String name) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(48));
        r.setPadding(dp(16), dp(6), dp(12), dp(6));
        r.addView(text(name, 17, label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        group.addView(r);
        return r;
    }

    private void divider(LinearLayout group) {
        View d = new View(this);
        d.setBackgroundColor(separator);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        lp.leftMargin = dp(16);
        group.addView(d, lp);
    }

    /** 누르면 고르는 행: 이름 · 회색 값 · ›. 값 TextView를 돌려준다 */
    private TextView row(LinearLayout group, String name, View.OnClickListener l, boolean dividerAfter) {
        LinearLayout r = line(group, name);
        TextView value = text("", 17, secondary);
        value.setPadding(dp(8), 0, dp(6), 0);
        value.setSingleLine(true);
        value.setMaxWidth(dp(170));
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(value);
        r.addView(text("›", 22, separator));
        r.setBackground(pressable(new ColorDrawable(card)));
        r.setOnClickListener(l);
        if (dividerAfter) divider(group);
        return value;
    }

    private Switch switchRow(LinearLayout group, String name, java.util.function.Consumer<Boolean> onChange, boolean dividerAfter) {
        LinearLayout r = line(group, name);
        Switch sw = new Switch(this);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[] {0xFFFFFFFF, 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[] {green, dark ? 0xFF39393D : 0xFFE9E9EA}));
        sw.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
        sw.setOnCheckedChangeListener((x, on) -> { onChange.accept(on); refresh(); });
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        if (dividerAfter) divider(group);
        return sw;
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotifyPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryOk() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askPermissions() {
        List<String> want = new ArrayList<>();
        if (!hasBluetoothPermission()) want.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!hasNotifyPermission()) want.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!want.isEmpty()) requestPermissions(want.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refresh();
    }

    @android.annotation.SuppressLint("BatteryLife")
    private void askBattery() {
        if (batteryOk()) return;
        // 배터리 최적화 예외여야 Android 12 이상에서 차 연결 순간 백그라운드에서 읽기를 시작할 수 있다
        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
    }

    private void pickCar() {
        if (!hasBluetoothPermission()) { askPermissions(); return; }
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        List<String> names = new ArrayList<>();
        List<String> addrs = new ArrayList<>();
        names.add("자동 (오디오 블루투스 기기 모두)");
        addrs.add("");
        try {
            Set<BluetoothDevice> bonded = adapter == null ? null : adapter.getBondedDevices();
            if (bonded != null) for (BluetoothDevice d : bonded) {
                names.add(d.getName() == null ? d.getAddress() : d.getName());
                addrs.add(d.getAddress());
            }
        } catch (SecurityException e) {
            askPermissions();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("차 블루투스를 고르세요")
                .setItems(names.toArray(new String[0]), (d, i) -> { prefs.setCar(addrs.get(i), i == 0 ? "" : names.get(i)); refresh(); })
                .show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (v1, h1, m1) ->
                new TimePickerDialog(this, (v2, h2, m2) -> { prefs.setWindow(h1 * 60 + m1, h2 * 60 + m2); refresh(); },
                        prefs.end() / 60, prefs.end() % 60, true) {{ setTitle("끝나는 시각"); }}.show(),
                prefs.start() / 60, prefs.start() % 60, true) {{ setTitle("시작 시각"); }}.show();
    }

    /** 언론사 체크 목록: 오늘 브리핑에 든 언론사 전체를 섹션별로 보여 주고, 고른 것만 읽는다 */
    private void pickSources() {
        if (loadingSources) return;
        loadingSources = true;
        sourcesVal.setText("불러오는 중…");
        new Thread(() -> {
            Brief b;
            try {
                b = Brief.parse(NewsService.fetch(BuildConfig.NEWS_URL));
            } catch (Exception e) {
                b = null;
            }
            final Brief brief = b;
            runOnUiThread(() -> {
                loadingSources = false;
                refresh();
                if (brief == null) {
                    new AlertDialog.Builder(this).setMessage("언론사 목록을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.").setPositiveButton("확인", null).show();
                    return;
                }
                showSources(brief);
            });
        }, "news-sources").start();
    }

    private void showSources(Brief b) {
        List<String[]> rows = b.catalog();
        Set<String> chosen = prefs.sources() == null ? b.defaults() : prefs.sources();
        String[] labels = new String[rows.size()];
        boolean[] checked = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            labels[i] = r[0] + " · " + r[1] + (r[3].isEmpty() ? "" : " (" + r[3] + ")");
            checked[i] = chosen.contains(r[2]);
        }
        new AlertDialog.Builder(this)
                .setTitle("들을 언론사를 고르세요")
                .setMultiChoiceItems(labels, checked, (d, i, on) -> checked[i] = on)
                .setPositiveButton("저장", (d, w) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < rows.size(); i++) if (checked[i]) ids.add(rows.get(i)[2]);
                    prefs.setSources(ids);
                    refresh();
                })
                .setNeutralButton("기본값으로", (d, w) -> { prefs.resetSources(); refresh(); })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 미리 듣기용 음성 엔진을 한 번만 띄우고, 준비되면 next를 실행한다 */
    private void withPreview(Runnable next) {
        if (previewReady) { next.run(); return; }
        afterPreview = next;
        if (preview != null) return; // 준비 중
        preview = new TextToSpeech(this, st -> runOnUiThread(() -> {
            if (st != TextToSpeech.SUCCESS) {
                preview = null;
                new AlertDialog.Builder(this).setMessage("음성 엔진을 시작하지 못했습니다.").setPositiveButton("확인", null).show();
                return;
            }
            preview.setLanguage(Locale.KOREAN);
            previewReady = true;
            if (afterPreview != null) afterPreview.run();
            afterPreview = null;
        }));
    }

    private void say(Voice v, int rate) {
        if (v != null) preview.setVoice(v);
        preview.setSpeechRate(rate / 100f);
        preview.speak(Voices.SAMPLE, TextToSpeech.QUEUE_FLUSH, null, "preview");
    }

    private void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    /** 폰에 든 한국어 음성 목록: 누르면 미리 들려주고, 저장하면 차에서 그 음성으로 읽는다 */
    private void pickVoice() {
        List<Voice> voices = Voices.korean(preview);
        if (voices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("고를 수 있는 한국어 음성이 없습니다. 설정의 '텍스트 음성 변환'에서 Google 음성 서비스와 한국어 음성 데이터를 받아 주세요.")
                    .setPositiveButton("설정 열기", (d, w) -> openTtsSettings())
                    .setNegativeButton("닫기", null).show();
            return;
        }
        String[] labels = new String[voices.size() + 1];
        labels[0] = "자동 (가장 자연스러운 음성)";
        int[] choice = {0};
        for (int i = 0; i < voices.size(); i++) {
            labels[i + 1] = Voices.label(voices.get(i), i);
            if (voices.get(i).getName().equals(prefs.voice())) choice[0] = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle("목소리를 고르세요 (누르면 들려 드립니다)")
                .setSingleChoiceItems(labels, choice[0], (d, i) -> {
                    choice[0] = i;
                    say(i == 0 ? Voices.best(voices) : voices.get(i - 1), prefs.rate());
                })
                .setPositiveButton("저장", (d, w) -> {
                    preview.stop();
                    prefs.setVoice(choice[0] == 0 ? "" : voices.get(choice[0] - 1).getName());
                    refresh();
                })
                .setNeutralButton("음성 더 받기", (d, w) -> { preview.stop(); openTtsSettings(); })
                .setNegativeButton("취소", (d, w) -> preview.stop())
                .show();
    }

    private void pickRate() {
        int[] choice = {1};
        for (int i = 0; i < Rules.RATES.length; i++) if (Rules.RATES[i] == prefs.rate()) choice[0] = i;
        List<Voice> voices = Voices.korean(preview);
        Voice current = null;
        for (Voice v : voices) if (v.getName().equals(prefs.voice())) current = v;
        if (current == null) current = Voices.best(voices);
        final Voice voice = current;
        new AlertDialog.Builder(this)
                .setTitle("말 빠르기 (누르면 들려 드립니다)")
                .setSingleChoiceItems(Rules.RATE_LABELS, choice[0], (d, i) -> { choice[0] = i; say(voice, Rules.RATES[i]); })
                .setPositiveButton("저장", (d, w) -> { preview.stop(); prefs.setRate(Rules.RATES[choice[0]]); refresh(); })
                .setNegativeButton("취소", (d, w) -> preview.stop())
                .show();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
