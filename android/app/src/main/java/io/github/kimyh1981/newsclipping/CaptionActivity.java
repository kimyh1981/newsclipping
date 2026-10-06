package io.github.kimyh1981.newsclipping;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 자막 화면: 지금 읽는 헤드라인(전체 듣기 중이면 지금 읽는 문장)을 큰 글씨로 보여 준다. 화면 아무 데나 누르면 그 기사 전체를 읽고,
 * 다 읽거나 왼쪽으로 쓸어 넘기면 다음 헤드라인으로 넘어간다.
 * 차 연결로 자동 재생이 시작되면 잠금 화면 위에도 뜨고, 읽는 동안 화면을 켜 둔다. 읽기가 끝나면 저절로 닫힌다.
 */
public class CaptionActivity extends Activity {
    private TextView head, body, hint;
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

        head = text(20, 0xFF8E8E93);
        col.addView(head);
        body = text(36, 0xFFFFFFFF);
        body.setTypeface(Typeface.DEFAULT_BOLD);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setLineSpacing(0, 1.15f);
        body.setAutoSizeTextTypeUniformWithConfiguration(18, 40, 1, TypedValue.COMPLEX_UNIT_SP);
        col.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        hint = text(17, 0xFF8E8E93);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint);

        // 누르면 전체 듣기, 오른쪽에서 왼쪽으로 쓸면 다음 기사(전체 듣기 중이면 끝내고 다음 헤드라인), 반대로 쓸면 이전 기사
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (NewsService.isRunning()) NewsService.control(NewsService.ACTION_MORE);
                else NewsService.start(CaptionActivity.this, true);
                return true;
            }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || !NewsService.isRunning()) return false;
                float dx = b.getX() - a.getX();
                if (Math.abs(dx) < dp(60) || Math.abs(dx) < Math.abs(b.getY() - a.getY())) return false;
                NewsService.control(dx < 0 ? NewsService.ACTION_NEXT : NewsService.ACTION_PREV);
                return true;
            }
        });
        col.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));
        setContentView(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NewsService.onCaption = redraw;
        show();
    }

    @Override
    protected void onPause() {
        if (NewsService.onCaption == redraw) NewsService.onCaption = null;
        super.onPause();
    }

    private void show() {
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
        head.setText(c.full ? c.source + " · 전체 듣기 · " + c.title : c.source);
        // 헤드라인은 읽는 글 그대로, 전체 듣기는 지금 읽는 문장. 너무 긴 글(옛 원고의 본문 통째)은 기사 제목
        body.setText(c.text.length() > 240 ? c.title : c.text);
        hint.setText(paused ? "멈춤" : c.title.isEmpty() ? "" : c.full ? "왼쪽으로 넘기면 다음 헤드라인으로 갑니다" : "누르면 전체 듣기 · 왼쪽으로 넘기면 다음 기사");
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
}
