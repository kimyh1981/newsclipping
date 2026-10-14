package io.github.kimyh1981.newsclipping;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 앱 안에서 업데이트: 깃허브 최신 릴리스(빌드 번호 rNN)가 지금 앱보다 새것이면 알려 주고, 누르면 받아서 설치 화면을 띄운다.
 * 같은 키로 서명한 APK라 기존 앱 위에 그대로 설치된다. 처음 한 번은 '이 출처의 앱 설치 허용'이 필요하다.
 */
final class Updater {
    private Updater() {}

    private static final String TAG = "newsclipping";
    static final String LATEST = "https://api.github.com/repos/kimyh1981/newsclipping/releases/latest";
    private static final int NOTIFY_ID = 7;

    /** 최신 릴리스: 보이는 버전(1.37), 빌드 번호(앱 versionCode와 같은 수), APK 주소 */
    static final class Release {
        final String name;
        final int code;
        final String apk;
        Release(String name, int code, String apk) { this.name = name; this.code = code; this.apk = apk; }
    }

    /** 릴리스 태그 'v1.37-r70' → 70, 예전 태그 'v1.0.62' → 62. 모르는 모양이면 -1 */
    static int buildOf(String tag) {
        if (tag == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-r(\\d+)$").matcher(tag);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = java.util.regex.Pattern.compile("^v1\\.0\\.(\\d+)$").matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 'v1.37-r70' → '1.37' */
    static String nameOf(String tag) {
        String t = tag == null ? "" : tag.replaceFirst("^v", "");
        int r = t.indexOf("-r");
        return r > 0 ? t.substring(0, r) : t;
    }

    static Release parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name");
        String apk = "";
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url");
        }
        return new Release(nameOf(tag), buildOf(tag), apk);
    }

    /** 지금 앱보다 새 릴리스가 있으면 그것, 없거나 못 알아보면 null (네트워크를 쓰므로 화면 스레드 밖에서) */
    static Release newer() {
        try {
            Release r = parse(NewsService.fetch(LATEST));
            return r.code > BuildConfig.VERSION_CODE && !r.apk.isEmpty() ? r : null;
        } catch (Exception e) {
            Log.w(TAG, "업데이트 확인 실패: " + e);
            return null;
        }
    }

    /** 새 버전이 있으면 알림을 한 번 띄운다 (버전마다 한 번). 누르면 앱 설정 화면이 열리고 거기서 업데이트한다 */
    static void notifyIfNew(Context c) {
        Release r = newer();
        if (r == null) return;
        Prefs prefs = new Prefs(c);
        if (prefs.updateNotified() == r.code) return;
        prefs.setUpdateNotified(r.code);
        NewsService.ensureChannel(c);
        PendingIntent open = PendingIntent.getActivity(c, 9, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, NewsService.CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("뉴스클리핑 새 버전 " + r.name)
                .setContentText("눌러서 앱을 열고 '업데이트'를 누르면 설치됩니다.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(NOTIFY_ID, n);
    }

    /** 이 앱이 APK를 설치해도 되는지 ('출처를 알 수 없는 앱' 허용). 안 되면 그 설정 화면을 연다 */
    static boolean canInstall(Activity a) {
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
        return false;
    }

    interface Progress { void at(int percent); }

    /** APK를 받아 설치 세션에 넣고 확정한다. 이어서 시스템 설치 확인 화면이 뜬다 (InstallResult가 띄움). 화면 스레드 밖에서 */
    static void download(Context c, Release r, Progress progress) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            HttpURLConnection conn = (HttpURLConnection) new URL(r.apk).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int total = conn.getContentLength();
            try (InputStream in = conn.getInputStream(); OutputStream out = s.openWrite("app.apk", 0, total > 0 ? total : -1)) {
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                int last = -1;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    got += n;
                    int pct = total > 0 ? (int) (got * 100 / total) : -1;
                    if (pct != last) { last = pct; progress.at(pct); }
                }
                s.fsync(out);
            } finally {
                conn.disconnect();
            }
            Intent done = new Intent(c, InstallResult.class);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, done, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pending.getIntentSender());
        } catch (Exception e) {
            pi.abandonSession(id);
            throw e;
        }
    }

    /** 설치 세션의 결과. 사용자 확인이 필요하면 시스템 설치 화면을 띄운다 */
    public static final class InstallResult extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Log.w(TAG, "업데이트 설치 실패: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }
}
