package io.github.kimyh1981.newsclipping;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 홈 화면 아래쪽: 고른 언론사의 로고만 같은 크기 흰 칸에 꽉 채워 늘어놓는다. 많으면 칸 크기를 줄여 모두 들어가게 한다.
 * 로고는 언론사 누리집의 apple-touch-icon(크고 선명함)을 먼저, 없으면 구글 파비콘을 받아 휴대폰에 저장해 둔다.
 */
final class LogoWall extends View {
    private static final String TAG = "newsclipping";
    private final List<String> sites = new ArrayList<>();
    private final Map<String, Bitmap> bitmaps = new HashMap<>();
    /** 로고를 끝내 못 찾은 곳 (빈 칸으로 두지 않고 뺀다) */
    private final java.util.Set<String> failed = new java.util.HashSet<>();
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float density;

    LogoWall(Context c, boolean dark, int separator) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        tile.setColor(0xFFFFFFFF); // 로고는 대개 흰 바탕용이라 어두운 화면에서도 흰 칸
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, density / 2f));
        edge.setColor(dark ? 0x00000000 : separator);
    }

    /** 보여 줄 누리집 주소들 (순서대로). 같으면 그대로 둔다 */
    void setSites(List<String> list) {
        if (list.equals(sites)) return;
        sites.clear();
        sites.addAll(list);
        for (String s : list) if (!bitmaps.containsKey(s)) load(s);
        invalidate();
    }

    private void load(String site) {
        bitmaps.put(site, null);
        File file = new File(new File(getContext().getCacheDir(), "logos"), site.replaceAll("[^a-zA-Z0-9.-]", "_") + ".png");
        new Thread(() -> {
            Bitmap b = file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
            if (b == null) {
                b = fetch("https://" + site + "/apple-touch-icon.png", 96);
                if (b == null && site.split("\\.").length == 2) b = fetch("https://www." + site + "/apple-touch-icon.png", 96);
                if (b == null) b = fetch("https://www.google.com/s2/favicons?domain=" + site + "&sz=128", 1);
                if (b != null) {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) { b.compress(Bitmap.CompressFormat.PNG, 100, out); } catch (Exception e) { Log.w(TAG, "로고 저장 실패: " + e); }
                }
            }
            final Bitmap got = b;
            ui.post(() -> {
                if (got != null) bitmaps.put(site, got);
                else failed.add(site);
                invalidate();
            });
        }, "logo").start();
    }

    /** 그림을 받아 minSize 이상이면 돌려준다 (작으면 흐려서 다른 곳을 찾는다) */
    private static Bitmap fetch(String url, int minSize) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36");
            try (InputStream in = c.getInputStream()) {
                if (c.getResponseCode() != 200) return null;
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0 && buf.size() < 2_000_000; ) buf.write(b, 0, n);
                byte[] data = buf.toByteArray();
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                return bmp != null && Math.min(bmp.getWidth(), bmp.getHeight()) >= minSize ? bmp : null;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        List<String> shown = new ArrayList<>();
        for (String s : sites) if (!failed.contains(s)) shown.add(s);
        int n = shown.size();
        int pad = Math.round(8 * density), gap = Math.round(12 * density);
        int w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
        int[] g = Rules.logoGrid(n, w, h, gap, Math.round(96 * density), Math.round(24 * density));
        int s = g[0], cols = g[1];
        if (s <= 0) return;
        int rows = (n + cols - 1) / cols;
        float top = pad + (h - (rows * s + (rows - 1) * gap)) / 2f;
        float radius = s * 0.22f, inset = s * 0.14f;
        for (int i = 0; i < n; i++) {
            int row = i / cols, inRow = Math.min(cols, n - row * cols), col = i % cols;
            float left = pad + (w - (inRow * s + (inRow - 1) * gap)) / 2f + col * (s + gap);
            float y = top + row * (s + gap);
            RectF r = new RectF(left, y, left + s, y + s);
            c.drawRoundRect(r, radius, radius, tile);
            c.drawRoundRect(r, radius, radius, edge);
            Bitmap b = bitmaps.get(shown.get(i));
            if (b == null) continue;
            // 로고 비율은 그대로, 칸 안쪽에 맞춰 가운데
            float box = s - 2 * inset, k = Math.min(box / b.getWidth(), box / b.getHeight());
            float bw = b.getWidth() * k, bh = b.getHeight() * k;
            RectF dst = new RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2);
            c.drawBitmap(b, new Rect(0, 0, b.getWidth(), b.getHeight()), dst, pic);
        }
    }
}
