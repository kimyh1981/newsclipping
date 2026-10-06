package io.github.kimyh1981.newsclipping;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 자막 화면: 지금 읽는 헤드라인을 큰 글씨로 보여 준다. 화면 아무 데나 누르면 그 기사 전체를 읽고, 다 읽으면 다음 헤드라인으로 넘어간다.
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

        col.setOnClickListener(v -> {
            if (NewsService.isRunning()) NewsService.control(NewsService.ACTION_MORE);
            else NewsService.start(this, true);
        });
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
        head.setText(c.title.isEmpty() ? c.source : c.source + (c.full ? " · 전체 듣는 중" : ""));
        // 헤드라인은 읽는 글 그대로, 본문(전체 듣기)처럼 긴 글은 기사 제목을 띄운다
        // '또, '는 같은 언론사 두 번째 기사를 소리로 잇는 말이라 화면에는 띄우지 않는다
        body.setText(c.full || c.text.length() > 140 ? c.title : c.text.replaceFirst("^또, ", ""));
        hint.setText(paused ? "멈춤" : c.title.isEmpty() ? "" : c.full ? "다 읽으면 다음 헤드라인으로 넘어갑니다" : "화면을 누르면 이 기사 전체를 읽어 드립니다");
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
