# Android localization

## Product language policy

The application follows the system language by default. Its in-app **Language**
selector offers **System**, **French** and **English**. System clears the
application-specific locale list; it does not install an English override.

- `app/src/main/res/values/strings.xml`: complete English fallback, 110 keys (including ROI plurals, drag hint and 15 export resources).
- `app/src/main/res/values-fr/strings.xml`: all 110 French translations.
- No redundant `values-en` directory.
- Unsupported locales resolve through Android's normal resource fallback. Machine
  identifiers are never a replacement for missing product prose.

The display language and Celsius units are separate. No Fahrenheit option,
thermometry change or numerical approximation is introduced.

## Supported Android infrastructure

AGP 8.9.2 generates LocaleConfig with `androidResources.generateLocaleConfig = true`.
`res/resources.properties` declares `unqualifiedResLocale=en`. The generated manifest
references the generated XML; neither is duplicated in source. Resource configurations
`en` and `fr` also filter dependency translations so other library languages do not
become unsupported product choices. The release configuration lists exactly `en, fr`.

`MainActivity` extends **AppCompatActivity** with AppCompat 1.7.1. Its XML theme uses
`Theme.AppCompat.Light.NoActionBar`; the existing Compose Material3 screen stays intact.
`AppCompatDelegate.getApplicationLocales/setApplicationLocales` and `LocaleListCompat`
are the sole language preference API. No custom locale mutation, database or preference
file is implemented.

| Android version | Storage and application |
|---|---|
| API 26–32 | AndroidX AppCompat auto-storage via disabled, non-exported `AppLocalesMetadataHolderService`, `autoStoreLocales=true` |
| API 33+ | AppCompat delegates to Android's LocaleManager; OS per-app language state/persistence is authoritative |

AndroidX auto-storage can perform a small synchronous locale-record read during startup;
this is the documented library behavior, not camera I/O. No application migration from
an earlier custom language store is needed.

`AppLanguage` belongs to Android presentation, not the JVM camera contract. System maps
to an empty list, French to `fr`, English to `en`. The selector reads the actual list
on configuration/menu/lifecycle changes. Returning from OS Settings refreshes it even
if an explicit language and System resolve to the same language. Unknown explicit
locales are shown as their actual tags, never falsely as System. Pseudolocales are
excluded from the normal three-choice menu.

See [Android per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages)
and [AppCompat 1.7.1](https://developer.android.com/jetpack/androidx/releases/appcompat#1.7.1).

## Camera lifecycle on language changes

A locale change can recreate the Activity. The retained UI policy compares the app preference
and effective resource locale when the replacement UI binds, immediately clears the
visible preview/measurement and releases the module/session before composing that UI.
This intentional release differs from ordinary rotation, which now retains the source.
The returning/new Activity only detects devices read-only. The operator must explicitly **Connect / Open** again.
Existing raw14 is still **RAW14_UNSETTLED**; no readiness or old Celsius value survives.
Radiometric initialization remains a separate explicit action.

Language selection cannot send 32772/32800/32768, retain a USB handle across locale recreation,
change registry selection, or change raw words, trailer/calibration, native coordinates,
LUT arithmetic, palette tables or session gates. Protocol/state enums and JSON evidence
remain language-independent. Real locale-release/reopen evidence is recorded in
[ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).

## Resource and module boundary

| Namespace | Purpose |
|---|---|
| `app_*` | Application labels |
| `language_*` | Selector labels |
| `camera_*` | Shared camera actions, structured status/error mappings, accessibility |
| `measurement_*`, `palette_*` | Celsius controls, readings, descriptions, warnings |
| `camera_ht301_*` | HT-301 model, initialization/status/debug labels |
| `camera_simulated_*` | Simulated preview module |
| `camera_<module>_*` | Future module-specific labels |

`CameraText.kt` maps every common status/error/palette to resources. Module-specific
UI bindings supply their own IDs. Core codes, stable module/model IDs, frame-mode and
session-state identifiers, fixture names, logcat developer messages and JSON keys/values
are unchanged. Hardware-supplied manufacturer/product names and numeric protocol facts
are data, not translated sentences.

HT-301 debug permission/inventory results now use typed presentation facts with exhaustive
resource mappings. Technical failure details remain in logs; exception prose is not a
normal user message. Debug session/transition stage IDs remain machine identifiers with
localized surrounding labels. The normal status text is human-readable in either language.

Meaningful thermal image/legend content descriptions are localized. Text-labelled
Material controls provide their accessible names through their translated labels.
There are no new icon-only controls.

## Formatting and layout

All parameterized product strings use positional Java-format arguments. English/French
argument positions and types must match; translated order may differ. `stringResource`
and Android resource formatting use the configured display locale, including legend ticks,
Celsius readings, coordinates and counters. French shows decimal commas.

Editable Celsius bounds preserve the round-trip Double text with the display decimal
separator; parsing accepts that separator and existing comma/dot input. Bounds still pass
the same finite/minimum-less-than-maximum validation. No measurement or JSON number is
localized/mutated. There are currently no product dates to format.

Current count labels are grammatical for any count (for example “Received 1” / “Reçues 1”),
so those counter labels do not need plurals. ROI valid-pixel counts use product `plurals`
selected by valid count, with matching English/French positional arguments. Point/ROI,
Clear ROI, size, Min/Max/Mean and unavailable readings share the measurement namespace.
Test-only quantity resources additionally verify Android singular/plural behavior.

Connect/Close and Auto/Locked use wrapping FlowRows. Portrait content and landscape
controls remain scrollable. Long French or pseudolocalized text can wrap without losing
access to initialization, range, palette, diagnostics or language actions. Native image
coordinates and mapping do not change with text direction or orientation.

## Checks and evidence

```bash
python3 tools/check_android_localization.py --self-test
python3 tools/check_camera_module_boundaries.py
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:generateReleaseLocaleConfig :app:processReleaseManifestForPackage
git diff --check
```

The resource checker detects missing/extra French keys, wrong namespaces, translated
nontranslatable/machine-only keys, unpositioned/conflicting or incompatible parameters,
and plural quantity/type differences. Ten resource-contract regression tests protect
its failure behavior. Future locale files can be checked with `--locale <qualifier>`.
Additional locale-specific plural forms (French `many`) use the default `other` form's
argument contract; missing default forms and mismatched extra-form arguments still fail.

Pixel tests cover exhaustive common/module/debug mappings, language-list conversion,
French decimals/coordinates/counts/accents/special characters, unsupported `de-DE`
resource contexts, and a deliberately missing French key **only in the test APK**.
Production French remains complete. AppCompat Activity recreation and external platform
locale changes are tested without opening USB. Both real HT bindings and the actual
160×120 preview-only simulator are covered.

Debug enables AGP pseudolocales **en-XA** (expanded) and **ar-XB** (RTL). They are present
in the debug LocaleConfig for validation only; the release configuration and normal
language menu expose product languages only. Layout instrumentation mounts the existing
screen with clearly synthetic, camera-free finite measurements, checks controls/dialogs
in both orientations, and retains screenshots privately for visual review. It makes no
hardware/physical-accuracy claim. No synthetic test host ships in production.

Robolectric 4.16.1 tests exercise the actual AppCompat library/theme, unsupported simulated
system language and file-backed auto-storage/restoration on API 26 and 32. Its AndroidX
package-private test reset hook models process-static reset; no product code accesses it.
This is simulated older-OS coverage, not a physical API 26/32 device claim. Pixel force-stop
checks validate actual platform persistence separately.

Compose UI tests use test-only Espresso 3.7.0, whose input service implementation works
on the test Pixel's OS; older Espresso used a removed reflective InputManager API.
[Robolectric setup](https://robolectric.org/getting-started/) and
[AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test)
document the test infrastructure. Build prerequisites remain in CONFIGURATION.md.

## Adding a language or camera module

1. Add `values-<locale>/strings.xml` with every translatable common **and module** key.
2. Preserve positional argument types and meaningful image/control descriptions; add
   appropriate plural forms only where needed.
3. Add the locale to `resourceConfigurations`; AGP regenerates LocaleConfig automatically.
   An optional in-app menu entry and its `language_*` label are presentation-only changes.
4. Run the parity checker for that locale, lint, fallback/formatting and both-orientation
   layout tests; inspect the packaged LocaleConfig and OS Settings choices.
5. Document support/evidence and update the changelog. JVM business/camera code needs no change.

A new camera keeps stable core IDs/capabilities/structured states and adds its Android
resource binding with a unique `camera_<module>_*` namespace. Supply all supported-language
translations and map every status/error/native sample label. Test module labels/capability
visibility and numerical invariants. Do not copy shared UI or modify HT-301 protocol to
translate another module. See CAMERA_MODULE_ARCHITECTURE.md for real-model validation.

## Still-export controls

`export_*` owns complete EN/FR capture/destination/cancel/share, availability, progress,
completion, storage/resource/partial-cleanup error and richer-evidence privacy prose.
Machine schema/error identifiers stay untranslated. Fixed font-scaled status slots
and wrapping action rows prevent progress from shifting the image while ROI is held.
The real export controls are exercised in English/French/expanded/RTL pseudolocales
in portrait and landscape using a synthetic source, without USB writes. An immutable
prepared capture survives app-language Activity recreation; the camera still releases
and requires explicit reopen. See [ANDROID_LMTX_EXPORT.md](ANDROID_LMTX_EXPORT.md).
