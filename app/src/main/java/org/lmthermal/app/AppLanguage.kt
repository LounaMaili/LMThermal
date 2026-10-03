package org.lmthermal.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Only presentation choices live here. The empty list follows the OS; AndroidX/Android owns persistence. */
enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("", R.string.language_system), FRENCH("fr", R.string.language_french), ENGLISH("en", R.string.language_english);

    fun locales(): LocaleListCompat = LocaleListCompat.forLanguageTags(tag)

    companion object {
        /** An unknown explicit locale (e.g. a test pseudolocale) must never be misrepresented as System. */
        fun selected(locales: LocaleListCompat): AppLanguage? = when {
            locales.isEmpty -> SYSTEM
            locales[0]?.country in setOf("XA", "XB") -> null
            else -> entries.firstOrNull { it != SYSTEM && it.tag == locales[0]?.language }
        }
    }
}

/** Read the authoritative app-specific list again on configuration changes, including changes in OS Settings. */
@Composable
fun LanguageSelector() {
    // Reading LocalConfiguration subscribes Compose to locale changes without storing a second preference.
    val configuration = LocalConfiguration.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    // Returning from OS Settings must refresh even when System and an explicit locale resolve identically.
    val locales = remember(configuration, lifecycleState, expanded) { AppCompatDelegate.getApplicationLocales() }
    val choice = AppLanguage.selected(locales)
    val label = choice?.let { stringResource(it.label) }
        ?: stringResource(R.string.language_other, locales.toLanguageTags())
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.language_label, label)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(text = { Text(stringResource(language.label)) }, onClick = {
                    expanded = false
                    // Activity recreation invokes onStop: release the camera and require explicit reopen.
                    AppCompatDelegate.setApplicationLocales(language.locales())
                })
            }
        }
    }
}

/** Preserve the round-trip Double representation while using the display locale's decimal separator. */
fun editableCelsius(value: Double, locale: Locale): String =
    value.toString().replace('.', DecimalFormatSymbols(locale).decimalSeparator)

/** Inputs accept the displayed separator and existing comma/dot syntax, with strict whole-value parsing. */
fun parseEditableCelsius(text: String, locale: Locale): Double? =
    text.replace(DecimalFormatSymbols(locale).decimalSeparator, '.').replace(',', '.').toDoubleOrNull()
