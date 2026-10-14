package io.github.kimyh1981.newsclipping;

import android.Manifest;
import android.app.Activity;
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
    private TextView footer, permVal, batteryVal, overlayVal, carVal, timeVal, sourcesVal, orderVal, customVal, keywordsVal, lessonVal, voiceVal, rateVal;
    private Switch enabledSw, weekdaysSw, captionsSw;
    private ImageView playBtn;
    private TextToSpeech preview; // 목소리·빠르기를 고를 때 미리 들려준다
    private boolean previewReady;
    private Runnable afterPreview;
    private boolean loadingSources;
    /** 새 버전 안내 (맨 위 파란 띠). 있으면 누르면 받아서 설치 */
    private TextView updateBar;
    /** 탭 화면들과 아래 탭 버튼, 지금 탭 */
    private ScrollView[] pages;
    private TextView[] tabs;
    private int tab;
    private TextView nowPlaying, lessonTile;
    private Updater.Release update;
    private boolean updating;
    private long updateChecked;

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

        // ── 뉴스클리핑 탭: 재생 버튼과 바로 가기 버튼 ──
        LinearLayout col = page("뉴스클리핑");
        nowPlaying = text("", 15, secondary);
        nowPlaying.setPadding(dp(4), 0, dp(4), dp(16));
        nowPlaying.setMaxLines(2);
        nowPlaying.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(nowPlaying);
        updateBar = text("", 16, 0xFFFFFFFF);
        updateBar.setTypeface(Typeface.DEFAULT_BOLD);
        updateBar.setGravity(Gravity.CENTER);
        updateBar.setPadding(dp(16), dp(14), dp(16), dp(14));
        updateBar.setBackground(pressable(rounded(green, dp(14))));
        updateBar.setVisibility(View.GONE);
        updateBar.setOnClickListener(v -> installUpdate());
        LinearLayout.LayoutParams ub = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ub.bottomMargin = dp(14);
        col.addView(updateBar, ub);

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
        play.addView(playBtn, new LinearLayout.LayoutParams(0, dp(64), 1f));
        play.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        play.addView(iconPill(Glyph.STOP, "멈춤", card, red, v -> NewsService.stopIfRunning()), new LinearLayout.LayoutParams(0, dp(64), 1f));
        col.addView(play);

        // 이전 기사 / 듣던 기사 전체 듣기 / 다음 기사 (읽는 중에만 동작)
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(10), 0, 0);
        nav.addView(iconPill(Glyph.PREV, "이전 기사", card, tint, v -> NewsService.control(NewsService.ACTION_PREV)), new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        TextView fullBtn = text("전체 듣기", 16, tint);
        fullBtn.setTypeface(Typeface.DEFAULT_BOLD);
        fullBtn.setGravity(Gravity.CENTER);
        fullBtn.setBackground(pressable(rounded(card, dp(14))));
        // 전체 듣기: 듣던 기사(멈췄으면 멈춘 곳, 안 읽는 중이면 지난번 듣던 기사)부터 이어서, 자막 화면으로 넘어간다
        fullBtn.setOnClickListener(v -> {
            if (NewsService.isRunning()) NewsService.control(NewsService.ACTION_MORE);
            else NewsService.start(this, true);
            startActivity(new Intent(this, CaptionActivity.class));
        });
        nav.addView(fullBtn, new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        nav.addView(iconPill(Glyph.NEXT, "다음 기사", card, tint, v -> NewsService.control(NewsService.ACTION_NEXT)), new LinearLayout.LayoutParams(0, dp(52), 1f));
        col.addView(nav);

        // 바로 가기 버튼 (두 줄 두 칸)
        LinearLayout tiles1 = tileRow(col);
        tile(tiles1, "💬", "자막 화면", "듣던 곳에서 이어 보기", v -> startActivity(new Intent(this, CaptionActivity.class)));
        tile(tiles1, "📰", "오늘 기사 목록", "웹에서 보기", v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SITE + "?app"))));
        LinearLayout tiles2 = tileRow(col);
        lessonTile = tile(tiles2, "🌱", "제품 공부·영어", "", v -> withBrief(this::showLessons));
        tile(tiles2, "🗞", "뉴스 설정", "언론사·순서·키워드", v -> showTab(1));

        // ── 뉴스설정 탭 ──
        LinearLayout newsPage = page("뉴스 설정");
        LinearLayout today = section(newsPage, "오늘 뉴스", null);
        sourcesVal = row(today, "들을 언론사", v -> withBrief(this::showSources), true);
        orderVal = row(today, "섹션 순서", v -> withBrief(this::showOrder), true);
        customVal = row(today, "언론사 직접 넣기 (주소)", v -> withBrief(this::showCustom), true);
        keywordsVal = row(today, "농업 키워드", v -> showKeywords(), false);
        LinearLayout study = section(newsPage, "뉴스 뒤에 듣기", "평일 아침 뉴스가 끝나면 제품 공부 한 회와 같은 회의 농업영어를 읽습니다.");
        lessonVal = row(study, "제품 공부·농업영어 진도", v -> withBrief(this::showLessons), false);

        // ── 설정 탭: 처음 한 번만 · 자동 재생 · 듣기 ──
        LinearLayout setPage = page("설정");
        LinearLayout setup = section(setPage, "처음 한 번만", "두 가지를 허용해야 차에 탔을 때 앱을 열지 않아도 자동으로 읽습니다.");
        permVal = row(setup, "블루투스·알림 권한", v -> askPermissions(), true);
        batteryVal = row(setup, "배터리 사용 제한 없음", v -> askBattery(), false);

        LinearLayout auto = section(setPage, "자동 재생", "공휴일은 서버가 알아서 건너뜁니다. 자막 화면은 '다른 앱 위에 표시'를 허용해야 차에 탔을 때 저절로 뜹니다.");
        enabledSw = switchRow(auto, "차에 타면 자동으로 읽기", on -> prefs.setEnabled(on), true);
        carVal = row(auto, "차 블루투스", v -> pickCar(), true);
        timeVal = row(auto, "재생 시간", v -> pickTime(), true);
        weekdaysSw = switchRow(auto, "평일에만", on -> prefs.setWeekdaysOnly(on), true);
        captionsSw = switchRow(auto, "자막 화면 띄우기", on -> prefs.setCaptions(on), true);
        overlayVal = row(auto, "다른 앱 위에 표시 (자막 화면용)", v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))), false);

        LinearLayout listen = section(setPage, "듣기", "목소리와 빠르기는 고르는 동안 미리 들려 드립니다.");
        voiceVal = row(listen, "목소리", v -> withPreview(this::pickVoice), true);
        rateVal = row(listen, "말 빠르기", v -> withPreview(this::pickRate), false);

        footer = text("", 13, secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        setPage.addView(footer);

        // 화면 = 지금 탭 + 아래 아이콘 탭 막대
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        pages = new ScrollView[] {scrollOf(col), scrollOf(newsPage), scrollOf(setPage)};
        for (ScrollView sv : pages) body.addView(sv);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        View line = new View(this);
        line.setBackgroundColor(separator);
        root.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(dark ? 0xFF121212 : 0xFFF9F9F9);
        bar.setPadding(0, dp(6), 0, dp(6));
        String[] names = {"뉴스클리핑", "뉴스설정", "설정"};
        tabs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int at = i;
            TextView t = text(names[i], 11, secondary);
            t.setGravity(Gravity.CENTER);
            t.setCompoundDrawablePadding(dp(3));
            t.setPadding(0, dp(2), 0, dp(2));
            t.setBackground(pressable(new ColorDrawable(0)));
            t.setOnClickListener(v -> showTab(at));
            tabs[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        root.addView(bar);
        setContentView(root);
        showTab(b == null ? 0 : b.getInt("tab", 0));
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
        checkUpdate();
    }

    /** 깃허브에 새 버전이 있으면 맨 위에 '업데이트' 띠를 띄운다 (30분에 한 번만 확인) */
    private void checkUpdate() {
        if (updating || System.currentTimeMillis() - updateChecked < 30 * 60_000L) return;
        updateChecked = System.currentTimeMillis();
        new Thread(() -> {
            Updater.Release r = Updater.newer();
            runOnUiThread(() -> {
                update = r;
                if (r == null) { updateBar.setVisibility(View.GONE); return; }
                updateBar.setText("새 버전 " + r.name + "이 있습니다 · 눌러서 업데이트");
                updateBar.setVisibility(View.VISIBLE);
            });
        }, "news-update").start();
    }

    private void installUpdate() {
        if (update == null || updating) return;
        if (!Updater.canInstall(this)) {
            new Sheet(this)
                    .setTitle("설치 허용이 한 번 필요합니다")
                    .setMessage("방금 열린 화면에서 '이 출처 허용'을 켜고 돌아와서 업데이트를 다시 눌러 주세요. 처음 한 번만 하면 됩니다.")
                    .setPositiveButton("확인", null)
                    .show();
            updateChecked = 0;
            return;
        }
        updating = true;
        Updater.Release r = update;
        updateBar.setText("새 버전 받는 중…");
        new Thread(() -> {
            try {
                Updater.download(this, r, pct -> runOnUiThread(() -> updateBar.setText(pct < 0 ? "새 버전 받는 중…" : "새 버전 받는 중 " + pct + "%")));
                runOnUiThread(() -> { updating = false; updateBar.setText("설치 화면에서 '업데이트'를 눌러 주세요"); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updating = false;
                    updateBar.setText("새 버전 " + r.name + "이 있습니다 · 눌러서 업데이트");
                    new Sheet(this).setMessage("새 버전을 받지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요.").setPositiveButton("확인", null).show();
                });
            }
        }, "news-update-get").start();
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
        NewsService.Caption c = NewsService.caption();
        String what = c == null ? "" : c.lesson > 0 ? c.source + " " + c.title : c.source;
        nowPlaying.setText(playing ? "읽는 중" + (what.isEmpty() ? "" : " · " + what)
                : NewsService.state() == NewsService.PAUSED ? "멈춤" + (what.isEmpty() ? "" : " · " + what) + " · 재생을 누르면 이어 듣습니다"
                : "재생을 누르면 오늘 뉴스를 읽습니다.");
    }

    private void refresh() {
        status(permVal, hasBluetoothPermission() && hasNotifyPermission());
        status(batteryVal, batteryOk());
        carVal.setText(prefs.carAddress().isEmpty() ? "모든 오디오 기기" : prefs.carName());
        timeVal.setText(Rules.hhmm(prefs.start()) + " – " + Rules.hhmm(prefs.end()));
        Set<String> chosen = prefs.sources();
        sourcesVal.setText(chosen == null ? "기본" : chosen.size() + "곳");
        orderVal.setText(prefs.order().isEmpty() ? "기본" : "직접 정함");
        customVal.setText(prefs.custom().isEmpty() ? "없음" : prefs.custom().size() + "곳");
        keywordsVal.setText(prefs.keywords().isEmpty() ? "없음" : String.join(", ", prefs.keywords()));
        lessonVal.setText(!prefs.lessonOn() && !prefs.englishOn() ? "끔" : (prefs.lessonRound() > 1 ? prefs.lessonRound() + "바퀴 · " : "") + prefs.lessonDone().size() + "회 완료");
        lessonTile.setText(lessonVal.getText() + (prefs.lessonOn() || prefs.englishOn() ? " · 진도 보기" : ""));
        voiceVal.setText(prefs.voice().isEmpty() ? "자동" : "직접 고름");
        rateVal.setText(Rules.rateLabel(prefs.rate()));
        enabledSw.setChecked(prefs.enabled());
        weekdaysSw.setChecked(prefs.weekdaysOnly());
        captionsSw.setChecked(prefs.captions());
        status(overlayVal, Settings.canDrawOverlays(this));
        String last = prefs.lastPlayed();
        String button = prefs.lastButton();
        footer.setText("버전 " + BuildConfig.VERSION_NAME + (last.isEmpty() ? "" : " · 마지막 자동 재생 " + last) + (button.isEmpty() ? "" : "\n마지막 차 버튼 " + button));
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

    /** 탭 한 장: 큰 제목이 맨 위에 있는 세로 화면 */
    private LinearLayout page(String name) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(24), dp(16), dp(32));
        TextView title = text(name, 34, label);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(8), 0, dp(8));
        col.addView(title);
        return col;
    }

    private ScrollView scrollOf(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        sv.addView(col);
        return sv;
    }

    private void showTab(int i) {
        tab = i;
        for (int k = 0; k < pages.length; k++) {
            pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            int color = k == i ? tint : secondary;
            tabs[k].setTextColor(color);
            tabs[k].setCompoundDrawablesWithIntrinsicBounds(null, new TabIcon(k, color, dp(24)), null, null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    private LinearLayout tileRow(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(r, lp);
        return r;
    }

    /** 바로 가기 버튼: 그림 · 굵은 이름 · 회색 설명. 설명 TextView를 돌려준다 */
    private TextView tile(LinearLayout row, String icon, String name, String sub, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(12), dp(14));
        t.setMinimumHeight(dp(108));
        t.setBackground(pressable(rounded(card, dp(16))));
        t.setOnClickListener(l);
        t.addView(text(icon, 26, label));
        TextView n = text(name, 16, label);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setPadding(0, dp(8), 0, dp(2));
        t.addView(n);
        TextView s2 = text(sub, 13, secondary);
        s2.setSingleLine(true);
        s2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(s2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = dp(12);
        row.addView(t, lp);
        return s2;
    }

    /** 아래 탭 막대의 그림: 0 뉴스클리핑(재생 원), 1 뉴스설정(신문), 2 설정(톱니) */
    private static final class TabIcon extends Drawable {
        private final int kind, size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TabIcon(int kind, int color, int size) {
            this.kind = kind;
            this.size = size;
            paint.setColor(color);
            setBounds(0, 0, size, size);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = size, x = b.left, y = b.top, cx = x + s / 2f, cy = y + s / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.09f);
            if (kind == 0) {
                c.drawCircle(cx, cy, s * 0.42f, paint);
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(cx - s * 0.12f, cy - s * 0.18f);
                p.lineTo(cx + s * 0.2f, cy);
                p.lineTo(cx - s * 0.12f, cy + s * 0.18f);
                p.close();
                c.drawPath(p, paint);
            } else if (kind == 1) {
                c.drawRoundRect(new RectF(x + s * 0.1f, y + s * 0.14f, x + s * 0.9f, y + s * 0.86f), s * 0.1f, s * 0.1f, paint);
                paint.setStrokeWidth(s * 0.08f);
                c.drawLine(x + s * 0.26f, y + s * 0.36f, x + s * 0.74f, y + s * 0.36f, paint);
                c.drawLine(x + s * 0.26f, y + s * 0.52f, x + s * 0.74f, y + s * 0.52f, paint);
                c.drawLine(x + s * 0.26f, y + s * 0.68f, x + s * 0.56f, y + s * 0.68f, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45f, cx, cy);
                    c.drawRoundRect(new RectF(cx - s * 0.07f, y + s * 0.04f, cx + s * 0.07f, y + s * 0.24f), s * 0.03f, s * 0.03f, paint);
                    c.restore();
                }
                paint.setStyle(Paint.Style.STROKE); // 가운데가 빈 고리
                paint.setStrokeWidth(s * 0.13f);
                c.drawCircle(cx, cy, s * 0.26f, paint);
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
        new Sheet(this)
                .setTitle("차 블루투스를 고르세요")
                .setItems(names.toArray(new String[0]), (d, i) -> { prefs.setCar(addrs.get(i), i == 0 ? "" : names.get(i)); refresh(); })
                .show();
    }

    private void pickTime() {
        int theme = dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
        new TimePickerDialog(this, theme, (v1, h1, m1) ->
                new TimePickerDialog(this, theme, (v2, h2, m2) -> { prefs.setWindow(h1 * 60 + m1, h2 * 60 + m2); refresh(); },
                        prefs.end() / 60, prefs.end() % 60, true) {{ setTitle("끝나는 시각"); }}.show(),
                prefs.start() / 60, prefs.start() % 60, true) {{ setTitle("시작 시각"); }}.show();
    }

    /** 언론사 체크 목록: 오늘 브리핑에 든 언론사 전체를 섹션별로 보여 주고, 고른 것만 읽는다 */
    /** 오늘 브리핑(섹션·언론사 목록)을 받아 next를 화면에서 실행한다. 직접 넣은 언론사도 목록에 넣는다 */
    private void withBrief(java.util.function.Consumer<Brief> next) {
        if (loadingSources) return;
        loadingSources = true;
        sourcesVal.setText("불러오는 중…");
        android.widget.Toast.makeText(this, "오늘 뉴스 목록을 불러오는 중…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Brief b;
            try {
                b = Brief.parse(NewsService.fetch(BuildConfig.NEWS_URL));
                for (Extras.Custom c : prefs.custom()) b.addSource(c.section, new Brief.Source(c.id, c.name, "ko", true, Extras.TAKE, new ArrayList<>()));
            } catch (Exception e) {
                b = null;
            }
            final Brief brief = b;
            runOnUiThread(() -> {
                loadingSources = false;
                refresh();
                if (brief == null) {
                    new Sheet(this).setMessage("언론사 목록을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.").setPositiveButton("확인", null).show();
                    return;
                }
                next.accept(brief);
            });
        }, "news-sources").start();
    }

    private void showSources(Brief b) {
        List<String[]> rows = b.catalog(prefs.order());
        Set<String> chosen = prefs.sources() == null ? b.defaults() : prefs.sources();
        String[] labels = new String[rows.size()];
        boolean[] checked = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            labels[i] = r[0] + " · " + r[1] + (r[3].isEmpty() ? "" : " (" + r[3] + ")");
            checked[i] = chosen.contains(r[2]);
        }
        new Sheet(this)
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

    /** 섹션 재생 순서: ▲▼로 옮기고 저장한다 */
    private void showOrder(Brief b) {
        List<Brief.Section> secs = new ArrayList<>(b.ordered(prefs.order()));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(8), dp(4));
        list.setBackground(rounded(card, dp(12)));
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            list.removeAllViews();
            for (int i = 0; i < secs.size(); i++) {
                final int at = i;
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setMinimumHeight(dp(44));
                r.addView(text((i + 1) + ". " + secs.get(i).title, 16, label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                TextView up = text("▲", 20, i == 0 ? separator : tint);
                TextView down = text("▼", 20, i == secs.size() - 1 ? separator : tint);
                up.setPadding(dp(14), dp(6), dp(14), dp(6));
                down.setPadding(dp(14), dp(6), dp(14), dp(6));
                up.setOnClickListener(v -> { if (at > 0) { secs.add(at - 1, secs.remove(at)); draw[0].run(); } });
                down.setOnClickListener(v -> { if (at < secs.size() - 1) { secs.add(at + 1, secs.remove(at)); draw[0].run(); } });
                r.addView(up);
                r.addView(down);
                list.addView(r);
            }
        };
        draw[0].run();
        new Sheet(this)
                .setTitle("섹션 순서 (위에서부터 읽습니다)")
                .setView(list)
                .setPositiveButton("저장", (d, w) -> {
                    List<String> ids = new ArrayList<>();
                    for (Brief.Section x : secs) ids.add(x.id);
                    prefs.setOrder(ids);
                    refresh();
                })
                .setNeutralButton("기본 순서", (d, w) -> { prefs.setOrder(new ArrayList<>()); refresh(); })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 주소를 직접 넣은 언론사 목록: 누르면 지우고, '추가'로 새로 넣는다 */
    private void showCustom(Brief b) {
        List<Extras.Custom> list = prefs.custom();
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = list.get(i).name + " · " + sectionTitle(b, list.get(i).section);
        Sheet d = new Sheet(this)
                .setTitle(list.isEmpty() ? "직접 넣은 언론사가 없습니다" : "직접 넣은 언론사 (누르면 지웁니다)")
                .setPositiveButton("추가", (x, w) -> addCustom(b))
                .setNegativeButton("닫기", null);
        if (!list.isEmpty()) d.setItems(labels, (x, i) -> new Sheet(this)
                .setMessage(list.get(i).name + "을(를) 지울까요?")
                .setPositiveButton("지우기", (y, w) -> {
                    Extras.Custom gone = list.remove(i);
                    prefs.setCustom(list);
                    Set<String> on = prefs.sources();
                    if (on != null && on.remove(gone.id)) prefs.setSources(on);
                    refresh();
                })
                .setNegativeButton("취소", null).show());
        else d.setMessage("언론사 홈페이지나 RSS 주소를 넣으면 고른 섹션에 들어가고, 들을 언론사 목록에 체크됩니다. 폰이 아침마다 직접 가져와 폰 음성으로 읽습니다.");
        d.show();
    }

    private static String sectionTitle(Brief b, String id) {
        for (Brief.Section s : b.sections) if (s.id.equals(id)) return s.title;
        return id;
    }

    private void addCustom(Brief b) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        form.setBackground(rounded(card, dp(12)));
        android.widget.EditText url = new android.widget.EditText(this);
        url.setHint("주소 (예: www.nongmin.com 또는 RSS 주소)");
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_CLASS_TEXT);
        android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("이름 (비우면 자동)");
        name.setSingleLine(true);
        form.addView(url);
        form.addView(name);
        TextView pick = text("넣을 섹션", 14, secondary);
        pick.setPadding(0, dp(12), 0, dp(4));
        form.addView(pick);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        List<Brief.Section> secs = b.ordered(prefs.order());
        for (int i = 0; i < secs.size(); i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(secs.get(i).title);
            rb.setId(1000 + i);
            group.addView(rb);
            if (Extras.FARM.equals(secs.get(i).id)) group.check(rb.getId());
        }
        if (group.getCheckedRadioButtonId() == -1 && !secs.isEmpty()) group.check(1000);
        form.addView(group);
        new Sheet(this)
                .setTitle("언론사 직접 넣기")
                .setView(form)
                .setPositiveButton("확인", (d, w) -> {
                    String u = Extras.normalize(url.getText().toString());
                    int idx = group.getCheckedRadioButtonId() - 1000;
                    if (u.isEmpty() || idx < 0 || idx >= secs.size()) return;
                    checkCustom(u, name.getText().toString().trim(), secs.get(idx));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 주소에서 기사를 실제로 찾아 보고 저장한다 */
    private void checkCustom(String url, String name, Brief.Section sec) {
        customVal.setText("확인 중…");
        new Thread(() -> {
            Extras.Found f = Extras.find(url, "");
            runOnUiThread(() -> {
                if (f == null) {
                    refresh();
                    new Sheet(this).setMessage("이 주소에서 기사를 찾지 못했습니다. 주소를 확인하거나 RSS 주소를 넣어 주세요.").setPositiveButton("확인", null).show();
                    return;
                }
                String n = !name.isEmpty() ? name : !f.name.isEmpty() && !f.viaGoogle ? f.name : Extras.host(url);
                List<Extras.Custom> list = prefs.custom();
                String id = Extras.customId(url);
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
                list.add(new Extras.Custom(id, n, url, f.feed, sec.id));
                prefs.setCustom(list);
                Set<String> on = prefs.sources();
                if (on != null) { on.add(id); prefs.setSources(on); } // 기본값을 쓰면 새 언론사는 저절로 켜진다
                refresh();
                new Sheet(this)
                        .setMessage(n + "을(를) '" + sec.title + "'에 넣었습니다. " + (f.viaGoogle ? "RSS가 없어 구글 뉴스에서 이 사이트 기사를 찾아 읽습니다. " : "")
                                + "지금 기사 " + f.entries.size() + "건이 보입니다. 다음 재생부터 언론사마다 " + Extras.TAKE + "건씩 폰 음성으로 읽습니다.")
                        .setPositiveButton("확인", null).show();
            });
        }, "news-custom-check").start();
    }

    /** 제품 공부 진도: 오늘 회, 남은 회(예정 날짜), 완료한 회(누르면 다시 듣기·다시 듣기 표시) */
    private void showLessons(Brief b) {
        if (b.lessons.isEmpty()) {
            new Sheet(this).setMessage("제품 공부 원고가 아직 서버에 없습니다. 내일 아침 원고부터 들어옵니다.").setPositiveButton("확인", null).show();
            return;
        }
        List<Integer> all = Lessons.numbers(b.lessons);
        java.util.Map<Integer, String> done = prefs.lessonDone();
        List<Integer> stars = prefs.lessonStars();
        int round = prefs.lessonRound();
        int todayN = prefs.lessonForToday(all, false);
        boolean nextRound = done.size() >= all.size();
        List<Integer> order = Lessons.order(all, stars, nextRound ? round + 1 : round);
        java.util.Map<Integer, String> left = Lessons.schedule(order, nextRound ? new java.util.HashMap<>() : done, Prefs.today());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Sheet[] dlg = new Sheet[1];
        LinearLayout onOff = group(list, null);
        switchRow(onOff, "평일 뉴스 뒤에 제품 공부", prefs::setLessonOn, true).setChecked(prefs.lessonOn());
        switchRow(onOff, "이어서 같은 회의 농업영어", prefs::setEnglishOn, false).setChecked(prefs.englishOn());

        Brief.Lesson t = b.lesson(todayN);
        if (t != null) {
            LinearLayout g = group(list, (done.containsKey(todayN) && !nextRound ? "오늘 (들음)" : "오늘") + " · " + Lessons.label(left.containsKey(todayN) ? left.get(todayN) : Prefs.today()));
            lessonRow(g, todayN + "회 " + t.title, "▶", label, false, () -> { dlg[0].dismiss(); NewsService.playLesson(this, todayN); });
        }
        List<java.util.Map.Entry<Integer, String>> rest = new ArrayList<>();
        for (java.util.Map.Entry<Integer, String> e : left.entrySet()) if (e.getKey() != todayN && b.lesson(e.getKey()) != null) rest.add(e);
        if (!rest.isEmpty()) {
            LinearLayout g = group(list, "남은 회 · 평일 하루 한 회");
            for (int i = 0; i < rest.size(); i++) {
                Brief.Lesson l = b.lesson(rest.get(i).getKey());
                lessonRow(g, l.n + "회 " + l.title, Lessons.label(rest.get(i).getValue()), secondary, i < rest.size() - 1, () -> { dlg[0].dismiss(); NewsService.playLesson(this, l.n); });
            }
        }
        List<java.util.Map.Entry<Integer, String>> fin = new ArrayList<>();
        for (java.util.Map.Entry<Integer, String> e : done.entrySet()) if (b.lesson(e.getKey()) != null) fin.add(e);
        if (!fin.isEmpty()) {
            LinearLayout g = group(list, "완료 · 누르면 다시 듣기, 길게 누르면 ★ 다시 듣기 표시");
            for (int i = 0; i < fin.size(); i++) {
                Brief.Lesson l = b.lesson(fin.get(i).getKey());
                String day = Lessons.label(fin.get(i).getValue());
                TextView v = lessonRow(g, "✓ " + l.n + "회 " + l.title, (stars.contains(l.n) ? "★ " : "") + day, label, i < fin.size() - 1, () -> { dlg[0].dismiss(); NewsService.playLesson(this, l.n); });
                ((View) v.getParent()).setOnLongClickListener(x -> {
                    prefs.toggleStar(l.n);
                    v.setText((prefs.lessonStars().contains(l.n) ? "★ " : "") + day);
                    return true;
                });
            }
        }
        dlg[0] = new Sheet(this)
                .setTitle("제품 공부·농업영어" + (round > 1 ? " · " + round + "바퀴째" : "") + " · " + done.size() + "/" + all.size())
                .setView(list)
                .setPositiveButton("닫기", null)
                .show();
    }

    /** 팝업 안의 회색 소제목 + 흰 둥근 묶음 */
    private LinearLayout group(LinearLayout parent, String header) {
        if (header != null) {
            TextView h = text(header, 13, secondary);
            h.setPadding(dp(16), dp(16), dp(16), dp(6));
            parent.addView(h);
        }
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(rounded(card, dp(12)));
        g.setClipToOutline(true);
        parent.addView(g);
        return g;
    }

    /** 회차 한 줄: 이름 · 회색 값(날짜). 값 TextView를 돌려준다 */
    private TextView lessonRow(LinearLayout g, String name, String value, int color, boolean dividerAfter, Runnable onTap) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(48));
        r.setPadding(dp(16), dp(8), dp(14), dp(8));
        r.addView(text(name, 16, color), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 15, secondary);
        v.setPadding(dp(8), 0, 0, 0);
        r.addView(v);
        r.setBackground(pressable(new ColorDrawable(card)));
        r.setOnClickListener(x -> onTap.run());
        g.addView(r);
        if (dividerAfter) divider(g);
        return v;
    }

    /** 농업 키워드: 쉼표로 여러 개 */
    private void showKeywords() {
        android.widget.EditText words = new android.widget.EditText(this);
        words.setText(String.join(", ", prefs.keywords()));
        words.setHint("예: 비료, 미생물, 바이오플랜");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        box.setBackground(rounded(card, dp(12)));
        box.addView(words, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new Sheet(this)
                .setTitle("농업 키워드")
                .setMessage("농업 신문 헤드라인에 키워드 기사가 없으면, 그 키워드의 오늘 기사를 한 건씩 더 찾아 읽습니다. 쉼표로 여러 개 넣을 수 있습니다.")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    List<String> out = new ArrayList<>();
                    for (String k : words.getText().toString().split("[,，]")) if (!k.trim().isEmpty() && !out.contains(k.trim())) out.add(k.trim());
                    prefs.setKeywords(out);
                    refresh();
                })
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
                new Sheet(this).setMessage("음성 엔진을 시작하지 못했습니다.").setPositiveButton("확인", null).show();
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
            new Sheet(this)
                    .setMessage("고를 수 있는 한국어 음성이 없습니다. 설정의 '텍스트 음성 변환'에서 Google 음성 서비스와 한국어 음성 데이터를 받아 주세요.")
                    .setPositiveButton("설정 열기", (d, w) -> openTtsSettings())
                    .setNegativeButton("닫기", null).show();
            return;
        }
        String[] labels = new String[voices.size() + 1];
        labels[0] = "자동 (서버 녹음 음성, 다 녹음되지 않은 날은 폰 음성)";
        int[] choice = {0};
        for (int i = 0; i < voices.size(); i++) {
            labels[i + 1] = Voices.label(voices.get(i), i);
            if (voices.get(i).getName().equals(prefs.voice())) choice[0] = i + 1;
        }
        new Sheet(this)
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
        new Sheet(this)
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
