package org.lmthermal.app

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lmthermal.camera.*
import org.lmthermal.camera.ht301.*
import org.lmthermal.camera.simulated.SimulatedCameraIdentity
import org.lmthermal.core.CelsiusPalette
import java.util.Locale

/** Resource/configuration tests never open USB or initialize radiometry. Device parity tests remain independent. */
@RunWith(AndroidJUnit4::class)
class LocalizationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    /** Configuration contexts are test inputs, not a custom product language/storage implementation. */
    private fun localized(tag: String, base: Context = context): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })

    /** Platform locale delivery is asynchronous; an idle main queue alone does not await its configuration. */
    private fun awaitLanguage(scenario: ActivityScenario<MainActivity>, language: String, previous: MainActivity? = null) = runBlocking {
        withTimeout(5000) {
            var applied = false
            while (!applied) {
                scenario.onActivity { activity ->
                    applied = activity.resources.configuration.locales[0].language == language && activity !== previous
                }
                if (!applied) delay(50)
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun allCommonStatusesErrorsAndPalettesResolveInBothLanguages() {
        for (tag in listOf("en", "fr")) {
            val resources = localized(tag)
            for (status in CameraStatusCode.entries) assertTrue(resources.getString(cameraStatusResource(status)).isNotBlank())
            for (error in CameraErrorCode.entries) assertTrue(resources.getString(cameraErrorResource(error)).isNotBlank())
            for (palette in CelsiusPalette.entries) assertTrue(resources.getString(PaletteResources.label(palette)).isNotBlank())
        }
        assertEquals("Connecter / Ouvrir", localized("fr").getString(R.string.camera_connect))
        assertEquals("Camera error", localized("en").getString(cameraStatusResource(CameraStatusCode.ERROR)))
    }

    @Test fun moduleBindingsAndHt301DebugFactsResolveThroughTheirOwnNamespaces() {
        val modules = defaultCameraModules(context)
        assertEquals(setOf("ht301", "simulated-preview"), modules.map { it.module.metadata.id.value }.toSet())
        for (tag in listOf("en", "fr")) {
            val resources = localized(tag)
            for (module in modules) {
                val name = resources.resources.getResourceEntryName(module.ui.modelLabel)
                assertTrue(name.startsWith(if (module.module.metadata.id.value == "ht301") "camera_ht301_" else "camera_simulated_"))
                assertTrue(resources.getString(module.ui.modelLabel).isNotBlank())
            }
            for (status in Ht301SessionStatus.entries) {
                val state = CameraStatus(CameraStatusCode.MEASUREMENT_UNAVAILABLE, status)
                val id = Ht301UiBindings.status(state) ?: cameraStatusResource(state.code)
                assertTrue(resources.getString(id).isNotBlank())
            }
            for (permission in Ht301Permission.entries) assertTrue(resources.getString(ht301PermissionResource(permission)).isNotBlank())
            for (inventory in Ht301InventoryStatus.entries) assertTrue(resources.getString(ht301InventoryResource(inventory)).isNotBlank())
        }
        assertEquals("Aperçu simulé (test)", localized("fr").getString(modules.last().ui.modelLabel))
    }

    @Test fun frenchDecimalsCoordinatesCountsAndSpecialCharactersAreLocalizedWithoutChangingValues() {
        val fr = localized("fr")
        val temperature = 37.25f
        assertEquals("(210, 145) · 37,25 °C", fr.getString(R.string.measurement_cursor_temperature, 210, 145, temperature))
        assertEquals("(210, 145) · 37.25 °C", localized("en").getString(R.string.measurement_cursor_temperature, 210, 145, temperature))
        assertEquals("25,0", fr.getString(R.string.measurement_legend_tick, 25.0))
        assertEquals("Reçues 12 · remplacées 2 · malformées 0", fr.getString(R.string.camera_diagnostic_counts, 12L, 2L, 0L))
        assertTrue(fr.getString(R.string.measurement_range_label, "Inferno", 25.0, 45.0).contains("25,00 à 45,00 °C"))
        assertEquals(37.25f, temperature, 0f)
        assertEquals("Turbo", PaletteResources.evidenceName(CelsiusPalette.TURBO))
        assertEquals("MEASUREMENT_READY", CameraStatusCode.MEASUREMENT_READY.name)
    }

    @Test fun editableBoundsKeepTheirRoundTripValueWithFrenchDecimalSeparator() {
        val value = 25.123456789012345
        val fr = editableCelsius(value, Locale.FRENCH)
        assertTrue(fr.contains(',')); assertFalse(fr.contains('.'))
        assertEquals(value, parseEditableCelsius(fr, Locale.FRENCH)!!, 0.0)
        assertEquals(value.toString(), editableCelsius(value, Locale.ENGLISH))
        val rtl = Locale.forLanguageTag("ar-XB")
        assertEquals(value, parseEditableCelsius(editableCelsius(value, rtl), rtl)!!, 0.0)
        assertNull(parseEditableCelsius("25,0 invalid", Locale.FRENCH))
    }

    @Test fun unsupportedLocaleFallsBackToCompleteEnglishResources() {
        val de = localized("de-DE")
        assertEquals("Connect / Open", de.getString(R.string.camera_connect))
        assertEquals("Temperatures unavailable", de.getString(R.string.measurement_unavailable))
        for (error in CameraErrorCode.entries) assertTrue(de.getString(cameraErrorResource(error)).isNotBlank())
        assertTrue(AppLanguage.SYSTEM.locales().isEmpty)
    }

    @Test fun missingTranslationFallsBackAndTestOnlyPluralsUseAndroidRules() {
        val fr = localized("fr", instrumentation.context)
        val en = localized("en", instrumentation.context)
        assertEquals("Test fallback · °C", fr.getString(org.lmthermal.app.test.R.string.fallback_probe))
        assertEquals("1 trame", fr.resources.getQuantityString(org.lmthermal.app.test.R.plurals.count_probe, 1, 1))
        assertEquals("2 trames", fr.resources.getQuantityString(org.lmthermal.app.test.R.plurals.count_probe, 2, 2))
        assertEquals("1 sample", en.resources.getQuantityString(org.lmthermal.app.test.R.plurals.count_probe, 1, 1))
        assertEquals("2 samples", en.resources.getQuantityString(org.lmthermal.app.test.R.plurals.count_probe, 2, 2))
        val productFr = localized("fr")
        val productEn = localized("en")
        assertEquals("1 pixel valide sur 10", productFr.resources.getQuantityString(R.plurals.measurement_roi_valid_pixels, 1, 1, 10))
        assertEquals("2 pixels valides sur 10", productFr.resources.getQuantityString(R.plurals.measurement_roi_valid_pixels, 2, 2, 10))
        assertEquals("1000000 pixels valides sur 1000000", productFr.resources.getQuantityString(
            R.plurals.measurement_roi_valid_pixels, 1_000_000, 1_000_000, 1_000_000))
        assertEquals("1 valid pixel out of 10", productEn.resources.getQuantityString(R.plurals.measurement_roi_valid_pixels, 1, 1, 10))
    }

    @Test fun systemFrenchEnglishRoundTripThroughRealLocaleLists() {
        for (choice in AppLanguage.entries) assertEquals(choice, AppLanguage.selected(choice.locales()))
        assertEquals(AppLanguage.FRENCH, AppLanguage.selected(LocaleListCompat.forLanguageTags("fr-CA")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.selected(LocaleListCompat.forLanguageTags("en-GB")))
        assertNull(AppLanguage.selected(LocaleListCompat.forLanguageTags("de-DE")))
        assertNull(AppLanguage.selected(LocaleListCompat.forLanguageTags("en-XA")))
        assertEquals(3, AppLanguage.entries.size)
    }

    @Test fun generatedLocaleConfigExposesProductLanguagesAndDebugPseudolocales() {
        if (Build.VERSION.SDK_INT < 33) return
        val config = LocaleConfig(context)
        assertEquals(LocaleConfig.STATUS_SUCCESS, config.status)
        val locales = config.supportedLocales!!
        val tags = (0 until locales.size()).map { locales[it].toLanguageTag() }.toSet()
        assertEquals(setOf("en", "fr", "en-XA", "ar-XB"), tags)
    }

    @Test fun packagedPseudolocalesExpandTextAndProvideRtlResources() {
        val original = localized("en").getString(R.string.camera_connect)
        assertTrue(localized("en-XA").getString(R.string.camera_connect).length > original.length)
        assertNotEquals(original, localized("ar-XB").getString(R.string.camera_connect))
        assertEquals(1, localized("ar-XB").resources.configuration.layoutDirection)
    }

    @Test fun languageChangeRecreatesAppCompatActivityAndDoesNotAutoOpen() {
        var initial: MainActivity? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var original = LocaleListCompat.getEmptyLocaleList()
        lateinit var originalLanguage: String
        lateinit var target: AppLanguage
        try {
            scenario.onActivity { activity ->
                original = AppCompatDelegate.getApplicationLocales()
                originalLanguage = activity.resources.configuration.locales[0].language
                initial = activity
                assertTrue(activity is AppCompatActivity)
                assertNull(ViewModelProvider(activity)[CameraViewModel::class.java].camera.state.value.preview)
                // Request a different resolved language; System and explicit French may otherwise share resources.
                target = if (originalLanguage == "fr") AppLanguage.ENGLISH else AppLanguage.FRENCH
                AppCompatDelegate.setApplicationLocales(target.locales())
            }
            awaitLanguage(scenario, target.tag, initial)
            scenario.onActivity { activity ->
                assertNotSame(initial, activity)
                val camera = ViewModelProvider(activity)[CameraViewModel::class.java].camera
                assertNull(camera.state.value.preview); assertNull(camera.state.value.measurement)
                assertNotEquals(CameraLifecycle.STREAMING, camera.state.value.lifecycle)
                assertEquals(AppCompatDelegate.getApplicationLocales()[0]!!.language, activity.resources.configuration.locales[0].language)
            }
        } finally {
            scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
            awaitLanguage(scenario, originalLanguage)
            scenario.close()
        }
    }

    @Test fun externalOsLanguageStateIsThePickerAuthority() {
        if (Build.VERSION.SDK_INT < 33) return
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        try {
            scenario.onActivity { manager.applicationLocales = LocaleList.forLanguageTags("fr") }
            awaitLanguage(scenario, "fr")
            scenario.onActivity {
                assertEquals(AppLanguage.FRENCH, AppLanguage.selected(AppCompatDelegate.getApplicationLocales()))
                assertEquals("Français", it.getString(R.string.language_french))
                manager.applicationLocales = LocaleList.getEmptyLocaleList()
            }
            awaitLanguage(scenario, manager.systemLocales[0].language)
            scenario.onActivity {
                assertEquals(AppLanguage.SYSTEM, AppLanguage.selected(AppCompatDelegate.getApplicationLocales()))
                assertEquals(manager.systemLocales[0].language, it.resources.configuration.locales[0].language)
            }
        } finally {
            scenario.onActivity { manager.applicationLocales = original }
            awaitLanguage(scenario, if (original.isEmpty) manager.systemLocales[0].language else original[0].language)
            scenario.close()
        }
    }

    @Test fun simulatedModuleUsesLocalizedUiWhileMachineStateAndGeometryStayUnchanged() = runBlocking {
        for (tag in listOf("en", "fr")) {
            val coordinator = AndroidCameraCoordinator(localized(tag), devices = { listOf(SimulatedCameraIdentity()) })
            try {
                withContext(Dispatchers.Main) { coordinator.enterForeground(); coordinator.connect() }
                withTimeout(5000) { while (coordinator.state.value.preview == null) delay(10) }
                val state = coordinator.state.value
                assertEquals(160, state.geometry!!.width); assertEquals(120, state.geometry!!.height)
                assertEquals("simulated-preview", state.module!!.id.value)
                assertEquals(CameraStatusCode.PREVIEW_ONLY, state.status.code)
                assertNull(state.measurement)
                assertFalse(CameraUiPolicy.showInitialize(state))
                assertTrue(localized(tag).getString(coordinator.uiFor(state.module!!.id).modelLabel).isNotBlank())
            } finally { withContext(Dispatchers.Main) { coordinator.dispose() } }
        }
    }
}
