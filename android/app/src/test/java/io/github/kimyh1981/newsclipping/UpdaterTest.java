package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 깃허브 릴리스 태그에서 빌드 번호(앱 versionCode)와 보이는 버전 */
public class UpdaterTest {
    @Test public void readsBuildNumberFromTags() {
        assertEquals(70, Updater.buildOf("v1.37-r70"));
        assertEquals(62, Updater.buildOf("v1.0.62"));
        assertEquals(-1, Updater.buildOf("nightly"));
        assertEquals("1.37", Updater.nameOf("v1.37-r70"));
    }

    @Test public void findsApkInRelease() throws Exception {
        Updater.Release r = Updater.parse("{\"tag_name\":\"v1.37-r70\",\"assets\":[{\"name\":\"newsclipping.apk\",\"browser_download_url\":\"https://github.com/x/newsclipping.apk\"}]}", "newsclipping.apk");
        assertEquals(70, r.code);
        assertEquals("1.37", r.name);
        assertEquals("https://github.com/x/newsclipping.apk", r.apk);
    }

    private static final String BOTH = "{\"tag_name\":\"v1.57-r101\",\"assets\":["
            + "{\"name\":\"newsclipping-lite.apk\",\"label\":\"v1.56\",\"browser_download_url\":\"https://github.com/x/newsclipping-lite.apk\"},"
            + "{\"name\":\"newsclipping.apk\",\"label\":\"\",\"browser_download_url\":\"https://github.com/x/newsclipping.apk\"}]}";

    @Test public void eachEditionPicksItsOwnApk() throws Exception {
        Updater.Release full = Updater.parse(BOTH, "newsclipping.apk");
        assertEquals("1.57", full.name);
        assertEquals("https://github.com/x/newsclipping.apk", full.apk);
        // 라이트판은 라이트 APK와 그 표시 이름(라이트 버전)을 쓴다
        Updater.Release lite = Updater.parse(BOTH, "newsclipping-lite.apk");
        assertEquals("1.56", lite.name);
        assertEquals(101, lite.code);
        assertEquals("https://github.com/x/newsclipping-lite.apk", lite.apk);
    }

    @Test public void liteSkipsReleasesThatOnlyChangedLessons() throws Exception {
        Updater.Release lite = Updater.parse(BOTH, "newsclipping-lite.apk");
        // 제품 공부만 바뀐 1.57: 라이트 버전은 그대로 1.56이라 1.56을 깐 라이트에는 뜨지 않는다
        assertFalse(Updater.isNewer(lite, 99, "1.56", true));
        assertTrue(Updater.isNewer(lite, 99, "1.55", true));
        // 정식판은 빌드 번호만 본다
        assertTrue(Updater.isNewer(Updater.parse(BOTH, "newsclipping.apk"), 99, "1.56", false));
        assertFalse(Updater.isNewer(Updater.parse(BOTH, "newsclipping.apk"), 101, "1.57", false));
        // 라이트 APK가 없는 예전 릴리스는 라이트에 뜨지 않는다
        Updater.Release old = Updater.parse("{\"tag_name\":\"v1.53-r98\",\"assets\":[{\"name\":\"newsclipping.apk\",\"browser_download_url\":\"u\"}]}", "newsclipping-lite.apk");
        assertFalse(Updater.isNewer(old, 1, "1.56", true));
    }

    @Test public void installHelpByStatus() {
        assertEquals(Updater.HELP_NONE, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_SUCCESS));
        assertEquals(Updater.HELP_SECURITY, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED));
        assertEquals(Updater.HELP_SECURITY, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED));
        assertEquals(Updater.HELP_REINSTALL, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT));
        assertEquals(Updater.HELP_OTHER, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE));
    }
}
