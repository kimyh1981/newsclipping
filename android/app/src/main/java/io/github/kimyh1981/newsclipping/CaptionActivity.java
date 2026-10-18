package io.github.kimyh1981.newsclipping;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 자막 화면: 지금 읽는 헤드라인(전체 듣기 중이면 지금 읽는 문장)을 큰 글씨로 보여 준다. 화면 아무 데나 누르면 그 기사 전체를 읽고,
 * 다 읽거나 왼쪽으로 쓸어 넘기면 다음 헤드라인으로 넘어간다.
 * 차 연결로 자동 재생이 시작되면 잠금 화면 위에도 뜨고, 읽는 동안 화면을 켜 둔다. 읽기가 끝나면 저절로 닫힌다.
 * 웹 기사 목록의 ▶(newsclipping://play?t=기사 제목)로 열리면 그 기사부터 읽는다.
 */
public class CaptionActivity extends Activity {
    private TextView head, body, hint, origin, time;
    private ChapterBar bar;
    /** 읽는 동안 0.5초마다 진행바와 시간 */
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            Chapters c = NewsService.chapters();
            int line = NewsService.progressLine();
            float[] t = NewsService.times(CaptionActivity.this);
            if (!bar.dragging()) bar.set(c, line, t != null && t[1] > 0 && line >= 0 ? t[0] / t[1] : -1);
            time.setText(NewsService.buffering() ? "기사를 불러오는 중…" : t != null && t[1] > 0 ? Rules.clock(t[0]) + " / " + Rules.clock(t[1]) : "");
            bar.removeCallbacks(this);
            if (NewsService.state() == NewsService.PLAYING) bar.postDelayed(this, 500);
        }
    };
    /** 빨리 듣기: 누른 채 0.25초면 2배, 두 번째로 누른 채면 3배 */
    private View content;
    private float holdSpeed, downX, downY;
    private boolean boosted;
    private final Runnable boostOn = () -> { boosted = true; NewsService.setBoost(holdSpeed >= 3f ? 3f : 2f); };

    private boolean moved(MotionEvent e) { return Math.hypot(e.getX() - downX, e.getY() - downY) > dp(12); }

    /**
     * 쓸기는 손을 뗄 때 움직인 거리로 판단한다 (2026-10-18 주인: 위아래 문단 넘기기가 끊긴다).
     * 예전엔 '빠르게 튕기기'만 쳐서 천천히 쓸면 무시됐고, 손가락이 0.25초 머물면 2배속이 켜져 쓸기가 사라졌다.
     * 위로 쓸면 다음 문단, 아래로 이전 문단 / 왼쪽으로 다음 기사, 오른쪽으로 이전 기사
     */
    private void swipe(float dx, float dy) {
        if (!NewsService.isRunning() || Math.max(Math.abs(dx), Math.abs(dy)) < dp(60)) return;
        if (Math.abs(dy) > Math.abs(dx)) NewsService.control(dy < 0 ? NewsService.ACTION_PARA_NEXT : NewsService.ACTION_PARA_PREV);
        else NewsService.control(dx < 0 ? NewsService.ACTION_NEXT : NewsService.ACTION_PREV);
    }
    /** 제품 공부 중: 농업영어로 넘기기, 농업영어 중: 이 회 마치기 */
    private Button skip;
    private final Runnable redraw = this::show;
    /** 읽는 중인 것을 한 번이라도 보여 줬다: 읽기가 끝나면 닫는다 */
    private boolean seen;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF000000);
        col.setPadding(dp(24), dp(32), dp(24), dp(24));

        // 맨 위 줄: 언론사(왼쪽) · 앱 홈으로 가는 버튼(오른쪽). 홈으로 가도 읽기는 이어진다
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        head = text(20, 0xFF8E8E93);
        top.addView(head, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.ImageButton home = new android.widget.ImageButton(this);
        home.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        home.setImageDrawable(new HomeIcon(0xFFFFFFFF, dp(26)));
        home.setContentDescription("홈");
        android.graphics.drawable.GradientDrawable round = new android.graphics.drawable.GradientDrawable();
        round.setColor(0xFF2C2C2E);
        round.setCornerRadius(dp(14));
        home.setBackground(round);
        home.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("tab", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        top.addView(home, new LinearLayout.LayoutParams(dp(52), dp(52)));
        col.addView(top);
        body = text(36, 0xFFFFFFFF);
        body.setTypeface(Typeface.DEFAULT_BOLD);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setLineSpacing(0, 1.15f);
        body.setAutoSizeTextTypeUniformWithConfiguration(18, 40, 1, TypedValue.COMPLEX_UNIT_SP);
        col.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        skip = new Button(this);
        skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        skip.setTextColor(0xFFFFFFFF);
        skip.setAllCaps(false);
        skip.setBackgroundColor(0xFF0A84FF);
        skip.setVisibility(View.GONE);
        skip.setOnClickListener(v -> NewsService.control(NewsService.ACTION_SKIP_LESSON));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        sp.bottomMargin = dp(12);
        col.addView(skip, sp);
        // 홈 화면과 같은 진행바: 얼마나 남았는지 보고, 끌어서 그 언론사로 넘어간다 (주인 요청 2026-10-18)
        bar = new ChapterBar(this, 0xFF0A84FF, 0xFF3A3A3C);
        bar.setListener(new ChapterBar.Listener() {
            @Override public void onScrub(int line) { }
            @Override public void onSeek(int line) { NewsService.seek(line); }
        });
        col.addView(bar);
        time = text(15, 0xFF8E8E93);
        time.setGravity(Gravity.CENTER);
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = dp(12);
        col.addView(time, tp);
        hint = text(17, 0xFF8E8E93);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint);
        // 원문 보기: 차를 세웠을 때 기사 페이지를 연다 (읽는 동안 화면은 자막 그대로, 주인 결정 2026-10-18)
        origin = text(15, 0xFF0A84FF);
        origin.setText("원문 보기 ↗");
        origin.setGravity(Gravity.CENTER);
        origin.setPadding(0, dp(10), 0, dp(4));
        origin.setVisibility(View.GONE);
        origin.setOnClickListener(v -> {
            String link = NewsService.currentLink();
            if (!link.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)));
        });
        col.addView(origin);

        // 누르면 전체 듣기, 오른쪽에서 왼쪽으로 쓸면 다음 기사, 반대로 쓸면 이전 기사. 전체 듣기 중에는 어느 쪽이든 본문을 끝내고 헤드라인으로 돌아온다
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTapEvent(MotionEvent e) {
                // 두 번째로 누른 채 0.25초: 3배속
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { holdSpeed = 3f; content.postDelayed(boostOn, 250); }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (boosted) return true; // 빨리 듣기였다: 누르기로 치지 않는다
                NewsService.Caption c = NewsService.caption();
                if (c != null && c.lesson > 0) NewsService.control(NewsService.state() == NewsService.PAUSED ? NewsService.ACTION_RESUME : NewsService.ACTION_PAUSE);
                else if (NewsService.isRunning()) NewsService.control(NewsService.ACTION_MORE);
                else NewsService.start(CaptionActivity.this, true);
                return true;
            }
        });
        content = col;
        col.setOnTouchListener((v, e) -> {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN && holdSpeed < 2f) { holdSpeed = 2f; content.postDelayed(boostOn, 250); } // 누른 채 0.25초: 2배속
            if (act == MotionEvent.ACTION_MOVE && !boosted && moved(e)) content.removeCallbacks(boostOn); // 쓸기면 빨리 듣기가 아니다
            if (act == MotionEvent.ACTION_DOWN) downX = e.getX();
            if (act == MotionEvent.ACTION_DOWN) downY = e.getY();
            boolean r = gestures.onTouchEvent(e);
            if (act == MotionEvent.ACTION_UP && !boosted) swipe(e.getX() - downX, e.getY() - downY);
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                content.removeCallbacks(boostOn);
                if (boosted) NewsService.setBoost(1f);
                holdSpeed = 0f;
                content.postDelayed(() -> boosted = false, 50); // 손을 뗀 직후의 '누르기'를 막고 풀어 준다
            }
            return r;
        });
        setContentView(col);
        if (b == null) playLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        playLink(intent);
    }

    /** 웹 기사 목록의 ▶: 그 기사부터 (제목이 없으면 듣던 곳부터) */
    private void playLink(Intent intent) {
        Uri u = intent == null ? null : intent.getData();
        if (u == null || !"play".equals(u.getHost())) return;
        String t = u.getQueryParameter("t");
        if (t != null && !t.isEmpty()) NewsService.playFrom(this, t);
        else if (NewsService.state() == NewsService.PAUSED) NewsService.control(NewsService.ACTION_RESUME);
        else if (!NewsService.isRunning()) NewsService.start(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NewsService.onCaption = redraw;
        NewsService.onProgress = progress;
        show();
        progress.run();
    }

    @Override
    protected void onPause() {
        if (NewsService.onCaption == redraw) NewsService.onCaption = null;
        if (NewsService.onProgress == progress) NewsService.onProgress = null;
        bar.removeCallbacks(progress);
        super.onPause();
    }

    private void show() {
        skip.setVisibility(View.GONE);
        origin.setVisibility(NewsService.isRunning() && !NewsService.currentLink().isEmpty() ? View.VISIBLE : View.GONE);
        if (!NewsService.isRunning()) {
            if (seen) { finish(); return; }
            head.setText("뉴스클리핑");
            body.setText("화면을 누르면 오늘의 뉴스를 읽기 시작합니다");
            hint.setText("");
            return;
        }
        seen = true;
        NewsService.Caption c = NewsService.caption();
        if (c == null) {
            head.setText("뉴스클리핑");
            body.setText("오늘의 뉴스를 가져오는 중입니다");
            hint.setText("");
            return;
        }
        boolean paused = NewsService.state() == NewsService.PAUSED;
        if (c.lesson > 0) { // 제품 공부·농업영어: 쓸어서 문단을 빠르게 넘기고, 버튼으로 다음 코너로
            head.setText(c.source + " · " + c.title);
            body.setText(c.text);
            skip.setText(c.english ? "이 회 마치기 ⏭" : "농업영어로 넘어가기 ⏭");
            skip.setVisibility(View.VISIBLE);
            hint.setText(paused ? "멈춤 · 누르면 이어 듣기" : "왼쪽으로 넘기면 다음 문단 · 오른쪽은 이전 문단 · 누르면 멈춤");
            return;
        }
        String fast = NewsService.boost() > 1f ? (NewsService.boost() >= 3f ? "▶▶▶ 3배 · " : "▶▶ 2배 · ") : "";
        head.setText(fast + (c.full ? c.source + " · 전체 듣기 · " + c.title : c.source));
        // 헤드라인은 읽는 글 그대로, 전체 듣기는 지금 읽는 문장. 너무 긴 글(옛 원고의 본문 통째)은 기사 제목
        body.setText(c.text.length() > 240 ? c.title : c.text);
        hint.setText(paused ? "멈춤" : c.title.isEmpty() ? "" : c.full ? "위·아래로 넘기면 문단 · 오른쪽은 헤드라인으로 · 누르고 있으면 빠르게" : "누르면 전체 듣기 · 왼쪽으로 넘기면 다음 기사 · 누르고 있으면 빠르게");
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 홈 버튼 그림: 집 모양 실루엣 (문은 비워 둔다) */
    private static final class HomeIcon extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int size;
        HomeIcon(int color, int size) {
            this.size = size;
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            setBounds(0, 0, size, size);
        }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = size, x = b.left, y = b.top;
            android.graphics.Path p = new android.graphics.Path();
            p.setFillType(android.graphics.Path.FillType.EVEN_ODD);
            p.moveTo(x + s * 0.5f, y + s * 0.08f);
            p.lineTo(x + s * 0.96f, y + s * 0.5f);
            p.lineTo(x + s * 0.82f, y + s * 0.5f);
            p.lineTo(x + s * 0.82f, y + s * 0.92f);
            p.lineTo(x + s * 0.18f, y + s * 0.92f);
            p.lineTo(x + s * 0.18f, y + s * 0.5f);
            p.lineTo(x + s * 0.04f, y + s * 0.5f);
            p.close();
            p.addRect(x + s * 0.41f, y + s * 0.64f, x + s * 0.59f, y + s * 0.92f, android.graphics.Path.Direction.CW);
            c.drawPath(p, paint);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
