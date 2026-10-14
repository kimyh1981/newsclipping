package io.github.kimyh1981.newsclipping;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * 설정 화면과 같은 모양(아이폰 설정 앱 색, 둥근 흰 묶음, 둥근 버튼)의 팝업. AlertDialog.Builder에서 쓰던 만큼만 같은 이름으로 둔다:
 * 제목, 설명, 직접 만든 화면, 목록(누르면 닫힘), 한 개 고르기(✓), 여러 개 고르기(✓), 버튼 셋(저장 같은 주 버튼은 파란 바탕).
 */
final class Sheet {
    /** 설정 화면의 색 (밝은 화면 / 어두운 화면) */
    static final class Look {
        final boolean dark;
        final int bg, card, label, secondary, separator, tint, green, red;
        Look(Activity a) {
            dark = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            bg = dark ? 0xFF000000 : 0xFFF2F2F7;
            card = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
            label = dark ? 0xFFFFFFFF : 0xFF000000;
            secondary = dark ? 0xFF8E8E93 : 0xFF8A8A8E;
            separator = dark ? 0xFF38383A : 0xFFC6C6C8;
            tint = dark ? 0xFF0A84FF : 0xFF007AFF;
            green = dark ? 0xFF30D158 : 0xFF34C759;
            red = dark ? 0xFFFF453A : 0xFFFF3B30;
        }
    }

    private final Activity a;
    private final Look k;
    private final Dialog dialog;
    private String title, message;
    private View view;
    private String[] items;
    private boolean[] checked;
    private int single = -2; // -2: 고르기 목록 아님
    private DialogInterface.OnClickListener onItem;
    private DialogInterface.OnMultiChoiceClickListener onMulti;
    private final List<Object[]> buttons = new ArrayList<>(); // {글, 리스너, 종류}
    private static final int POSITIVE = 0, NEUTRAL = 1, NEGATIVE = 2;

    Sheet(Activity a) {
        this.a = a;
        this.k = new Look(a);
        // 입력칸 밖을 누르거나 팝업을 닫으면 키보드를 내린다
        this.dialog = new Dialog(a) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                View f = getCurrentFocus();
                if (e.getAction() == MotionEvent.ACTION_DOWN && f instanceof EditText) {
                    int[] at = new int[2];
                    f.getLocationOnScreen(at);
                    Rect r = new Rect(at[0], at[1], at[0] + f.getWidth(), at[1] + f.getHeight());
                    if (!r.contains((int) e.getRawX(), (int) e.getRawY())) hideKeyboard(f);
                }
                return super.dispatchTouchEvent(e);
            }
            @Override public void dismiss() {
                View f = getCurrentFocus();
                if (f != null) hideKeyboard(f);
                super.dismiss();
            }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    Sheet setTitle(String t) { title = t; return this; }
    Sheet setMessage(String m) { message = m; return this; }
    Sheet setView(View v) { view = v; return this; }
    Sheet setItems(String[] labels, DialogInterface.OnClickListener l) { items = labels; onItem = l; return this; }
    Sheet setSingleChoiceItems(String[] labels, int at, DialogInterface.OnClickListener l) { items = labels; single = at; onItem = l; return this; }
    Sheet setMultiChoiceItems(String[] labels, boolean[] on, DialogInterface.OnMultiChoiceClickListener l) { items = labels; checked = on; onMulti = l; return this; }
    Sheet setPositiveButton(String t, DialogInterface.OnClickListener l) { buttons.add(new Object[] {t, l, POSITIVE}); return this; }
    Sheet setNeutralButton(String t, DialogInterface.OnClickListener l) { buttons.add(new Object[] {t, l, NEUTRAL}); return this; }
    Sheet setNegativeButton(String t, DialogInterface.OnClickListener l) { buttons.add(new Object[] {t, l, NEGATIVE}); return this; }

    void dismiss() { dialog.dismiss(); }

    private void hideKeyboard(View f) {
        InputMethodManager im = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    Sheet show() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(k.bg, dp(18)));
        box.setPadding(dp(16), dp(20), dp(16), dp(16));

        if (title != null) {
            TextView t = text(title, 19, k.label);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(4), 0, dp(4), dp(8));
            box.addView(t);
        }
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        if (message != null) {
            TextView m = text(message, 15, k.secondary);
            m.setPadding(dp(4), 0, dp(4), dp(12));
            m.setLineSpacing(0, 1.15f);
            body.addView(m);
        }
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            body.addView(view);
        }
        if (items != null) body.addView(list());
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        box.addView(new FrameLayout(a) {
            { addView(sv); }
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        });

        // 버튼은 아래에 넓게 세로로: 주 버튼(파란 바탕) → 그 밖 → 취소·닫기
        for (int kind : new int[] {POSITIVE, NEUTRAL, NEGATIVE}) {
            for (Object[] b : buttons) {
                if ((int) b[2] != kind) continue;
                TextView btn = text((String) b[0], 17, kind == POSITIVE ? 0xFFFFFFFF : kind == NEUTRAL ? k.tint : k.secondary);
                btn.setTypeface(kind == POSITIVE ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                btn.setGravity(Gravity.CENTER);
                btn.setBackground(pressable(rounded(kind == POSITIVE ? k.tint : k.card, dp(14))));
                DialogInterface.OnClickListener l = (DialogInterface.OnClickListener) b[1];
                int which = kind == POSITIVE ? DialogInterface.BUTTON_POSITIVE : kind == NEUTRAL ? DialogInterface.BUTTON_NEUTRAL : DialogInterface.BUTTON_NEGATIVE;
                btn.setOnClickListener(v -> { dialog.dismiss(); if (l != null) l.onClick(dialog, which); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
                lp.topMargin = dp(10);
                box.addView(btn, lp);
            }
        }

        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(Math.min(dp(440), Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.92f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        return this;
    }

    /** 둥근 흰 묶음 안의 목록. 고르기 목록이면 오른쪽에 파란 ✓ */
    private View list() {
        LinearLayout group = new LinearLayout(a);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(rounded(k.card, dp(12)));
        group.setClipToOutline(true);
        List<TextView> marks = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            final int at = i;
            if (i > 0) {
                View d = new View(a);
                d.setBackgroundColor(k.separator);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
                lp.leftMargin = dp(16);
                group.addView(d, lp);
            }
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(dp(48));
            r.setPadding(dp(16), dp(8), dp(14), dp(8));
            r.addView(text(items[i], 16, k.label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView mark = text("✓", 18, k.tint);
            mark.setTypeface(Typeface.DEFAULT_BOLD);
            mark.setPadding(dp(10), 0, 0, 0);
            boolean on = checked != null ? checked[i] : single == i;
            mark.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            if (checked == null && single == -2) mark.setVisibility(View.GONE);
            marks.add(mark);
            r.addView(mark);
            r.setBackground(pressable(new ColorDrawable(k.card)));
            r.setOnClickListener(v -> {
                if (checked != null) {
                    checked[at] = !checked[at];
                    mark.setVisibility(checked[at] ? View.VISIBLE : View.INVISIBLE);
                    if (onMulti != null) onMulti.onClick(dialog, at, checked[at]);
                } else if (single != -2) {
                    single = at;
                    for (int j = 0; j < marks.size(); j++) marks.get(j).setVisibility(j == at ? View.VISIBLE : View.INVISIBLE);
                    if (onItem != null) onItem.onClick(dialog, at);
                } else {
                    dialog.dismiss();
                    if (onItem != null) onItem.onClick(dialog, at);
                }
            });
            group.addView(r);
        }
        return group;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private Drawable pressable(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(k.dark ? 0x33FFFFFF : 0x1F000000), content, null);
    }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
