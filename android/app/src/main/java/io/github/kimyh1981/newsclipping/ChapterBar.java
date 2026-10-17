package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 홈 화면 진행바: 원고 전체를 섹션 토막으로 끊어 그리고(유튜브 진행바의 챕터처럼), 지금 읽는 곳까지 칠한다.
 * 끄는 동안 손가락 자리의 줄을 알려 주고(로고 벽이 그 언론사 로고를 키운다), 놓으면 그 언론사부터 읽게 한다 (주인 요청 2026-10-17).
 */
final class ChapterBar extends View {
    interface Listener {
        /** 끄는 중: 손가락 자리의 줄. 끄기를 그만두면 -1 */
        void onScrub(int line);
        /** 놓음: 읽기 시작할 줄 */
        void onSeek(int line);
    }

    private final float density;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Chapters chapters;
    private int line = -1;
    /** 줄 안에서도 부드럽게 차오르게: 지난 시간 ÷ 전체 시간 (모르면 -1) */
    private float frac = -1;
    private boolean dragging;
    private float dragAt;
    private Listener listener;

    ChapterBar(Context c, int tint, int separator) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        track.setColor(separator);
        fill.setColor(tint);
        thumb.setColor(tint);
        setContentDescription("뉴스 진행바: 끌어서 언론사를 고르고 놓으면 거기부터 읽습니다");
    }

    void setListener(Listener l) { listener = l; }

    boolean dragging() { return dragging; }

    /** 지금 원고(읽는 중이 아니면 null)와 읽는 줄 */
    void set(Chapters c, int at) { set(c, at, -1); }

    void set(Chapters c, int at, float fraction) {
        chapters = c;
        line = at;
        frac = fraction;
        if (c == null) dragging = false;
        invalidate();
    }

    private float left() { return getPaddingLeft() + 10 * density; }

    private float right() { return getWidth() - getPaddingRight() - 10 * density; }

    private float toFraction(float x) { return Math.max(0f, Math.min(1f, (x - left()) / Math.max(1f, right() - left()))); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        Chapters c = chapters;
        if (c == null || c.size() == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true); // 화면 스크롤이 끌기를 가로채지 않게
                dragging = true;
                // fall through
            case MotionEvent.ACTION_MOVE:
                dragAt = toFraction(e.getX());
                if (listener != null) listener.onScrub(c.lineAt(dragAt));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                int target = c.seekTarget(c.lineAt(toFraction(e.getX())));
                line = target;
                frac = -1;
                if (listener != null) listener.onSeek(target);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (listener != null) listener.onScrub(-1);
                invalidate();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float l = left(), r = right(), w = r - l, cy = getHeight() / 2f;
        float h = (dragging ? 6 : 4) * density, gap = 3 * density, round = h / 2;
        Chapters c = chapters;
        if (c == null || c.size() == 0) { // 읽는 중이 아니면 흐린 빈 바만
            canvas.drawRoundRect(new RectF(l, cy - h / 2, r, cy + h / 2), round, round, track);
            return;
        }
        float p = dragging ? dragAt : frac >= 0 ? Math.min(1f, frac) : line < 0 ? 0 : c.fractionOf(line);
        float[] b = c.bounds();
        for (int k = 0; k + 1 < b.length; k++) {
            float x0 = l + b[k] * w + (k == 0 ? 0 : gap / 2), x1 = l + b[k + 1] * w - (k + 2 == b.length ? 0 : gap / 2);
            if (x1 <= x0) continue;
            canvas.drawRoundRect(new RectF(x0, cy - h / 2, x1, cy + h / 2), round, round, track);
            float fx = Math.min(x1, l + p * w);
            if (fx > x0) canvas.drawRoundRect(new RectF(x0, cy - h / 2, fx, cy + h / 2), round, round, fill);
        }
        canvas.drawCircle(l + p * w, cy, (dragging ? 10 : 7) * density, thumb);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Math.round(40 * density)); // 손가락으로 잡기 좋은 높이
    }
}
