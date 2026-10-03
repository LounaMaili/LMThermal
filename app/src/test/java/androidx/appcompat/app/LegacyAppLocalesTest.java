package androidx.appcompat.app;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Looper;
import org.robolectric.Shadows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lmthermal.app.R;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import androidx.core.os.LocaleListCompat;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Test-only AndroidX package access uses its reset hook to model cold-start static state, never product storage. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 32}, qualifiers = "de-rDE")
@LooperMode(LooperMode.Mode.PAUSED)
public class LegacyAppLocalesTest {
    private ActivityController<LocaleProbeActivity> controller;
    private static final String STORAGE = "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file";

    /** A minimal AppCompat host exercises the actual library/theme without loading USB or Compose. */
    public static class LocaleProbeActivity extends AppCompatActivity {
        @Override protected void onCreate(Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
        }
    }

    @Before public void resetProcessState() {
        AppCompatDelegate.resetStaticRequestedAndStoredLocales();
        new File(RuntimeEnvironment.getApplication().getFilesDir(), STORAGE).delete();
    }

    @After public void releaseHost() {
        if (controller != null) controller.pause().stop().destroy();
        AppCompatDelegate.resetStaticRequestedAndStoredLocales();
    }

    private void openHost() {
        controller = Robolectric.buildActivity(LocaleProbeActivity.class).setup();
    }

    /** Locale storage runs on AndroidX's serial executor; wait for its actual file rather than assuming completion. */
    private String awaitStored(String language) throws Exception {
        File file = new File(RuntimeEnvironment.getApplication().getFilesDir(), STORAGE);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (file.exists()) {
                String text = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (text.contains("application_locales=\"" + language + "\"")) return text;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("AndroidX locale storage did not contain " + language);
    }

    /** Drain ActivityCompat's posted recreation before destroying/replacing a test controller. */
    private void selectLocales(LocaleListCompat locales) {
        AppCompatDelegate.setApplicationLocales(locales);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void manifestOptsIntoAndroidXStorageWithoutAnExportedService() throws Exception {
        ServiceInfo service = RuntimeEnvironment.getApplication().getPackageManager().getServiceInfo(
            new ComponentName(RuntimeEnvironment.getApplication(), AppLocalesMetadataHolderService.class),
            PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS);
        assertFalse(service.enabled); assertFalse(service.exported);
        assertTrue(service.metaData.getBoolean("autoStoreLocales"));
    }

    @Test public void emptyAppLocaleListFollowsUnsupportedSystemLocaleAndEnglishFallback() {
        openHost();
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty());
        assertEquals("de", controller.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
        assertEquals("Connect / Open", controller.get().getString(R.string.camera_connect));
    }

    @Test public void explicitFrenchAndEnglishUseTheAppCompatActivityResourceConfiguration() {
        openHost();
        selectLocales(LocaleListCompat.forLanguageTags("fr"));
        controller.pause().stop().destroy();
        openHost();
        assertEquals("fr", controller.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
        assertEquals("Connecter / Ouvrir", controller.get().getString(R.string.camera_connect));
        selectLocales(LocaleListCompat.forLanguageTags("en"));
        controller.pause().stop().destroy();
        openHost();
        assertEquals("en", AppCompatDelegate.getApplicationLocales().get(0).getLanguage());
        assertEquals("Connect / Open", controller.get().getString(R.string.camera_connect));
    }

    @Test public void persistedFrenchIsRestoredAfterAndroidXProcessStateReset() throws Exception {
        openHost();
        selectLocales(LocaleListCompat.forLanguageTags("fr"));
        awaitStored("fr");
        controller.pause().stop().destroy();
        // AndroidX supplies this package-private reset specifically for tests. The disk record stays intact.
        AppCompatDelegate.resetStaticRequestedAndStoredLocales();
        openHost();
        assertEquals("fr", AppCompatDelegate.getApplicationLocales().get(0).getLanguage());
        assertEquals("Connecter / Ouvrir", controller.get().getString(R.string.camera_connect));
        selectLocales(LocaleListCompat.getEmptyLocaleList());
        // Robolectric shares application Resources with its test host; restore the unchanged simulated OS input.
        RuntimeEnvironment.setQualifiers("de-rDE");
        controller.pause().stop().destroy();
        openHost();
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty());
        assertEquals("Connect / Open", controller.get().getString(R.string.camera_connect));
    }
}
