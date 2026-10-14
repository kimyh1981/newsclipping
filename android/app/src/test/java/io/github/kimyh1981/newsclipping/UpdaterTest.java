package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;

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
        Updater.Release r = Updater.parse("{\"tag_name\":\"v1.37-r70\",\"assets\":[{\"name\":\"newsclipping.apk\",\"browser_download_url\":\"https://github.com/x/newsclipping.apk\"}]}");
        assertEquals(70, r.code);
        assertEquals("1.37", r.name);
        assertEquals("https://github.com/x/newsclipping.apk", r.apk);
    }

    @Test public void installHelpByStatus() {
        assertEquals(Updater.HELP_NONE, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_SUCCESS));
        assertEquals(Updater.HELP_SECURITY, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED));
        assertEquals(Updater.HELP_SECURITY, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED));
        assertEquals(Updater.HELP_REINSTALL, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT));
        assertEquals(Updater.HELP_OTHER, Updater.helpFor(android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE));
    }
}
