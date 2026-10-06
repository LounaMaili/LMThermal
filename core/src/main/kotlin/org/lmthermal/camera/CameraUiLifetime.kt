package org.lmthermal.camera

/** Retained UI lifetime, separate from the sole session owner. No Activity, driver or timer is retained.
 * A stopped configuration instance and an outstanding destination transaction keep ownership. Every other
 * stop releases. Screen-off, locale change and final disposal override the destination exception.
 * A picker transaction deliberately lasts until its result (including cancellation), even if Home is used
 * inside that external picker; screen-off/detach/disposal still release. No background service is implied.
 * Methods run serially with the UI/receiver callbacks; session release is serialized separately by its owner.
 */
class CameraUiLifetime(private val release: () -> Unit,
    private val event: (String) -> Unit = {}) {
    private var uiGeneration = 0L
    private var localeSignature: String? = null
    private var picker = false
    private var disposed = false

    /** Bind only the newest UI instance. Locale changes retain the conservative explicit-reopen policy. */
    fun started(locales: String): Long {
        check(!disposed)
        if (localeSignature != null && localeSignature != locales) {
            release(); event("locale_release")
        }
        localeSignature = locales
        uiGeneration++
        event("ui_started")
        return uiGeneration
    }
    /** A replaced Activity must not close a session now being displayed by its successor. */
    fun stopped(ui: Long, changingConfiguration: Boolean, finishing: Boolean) {
        if (!current(ui)) return
        when {
            finishing -> { release(); event("finish_release") }
            changingConfiguration -> event("configuration_retained")
            picker -> event("picker_retained")
            else -> { release(); event("background_release") }
        }
    }
    /** Register before launching SAF; launch failure must resolve the transaction as well. */
    fun beginPicker(ui: Long): Boolean {
        if (!current(ui) || picker) return false
        picker = true; event("picker_started")
        return true
    }
    /** ActivityResultRegistry redelivers to the current instance after recreation, never reopens a source. */
    fun endPicker(ui: Long): Boolean {
        if (!current(ui) || !picker) return false
        picker = false; event("picker_returned")
        return true
    }
    /** Gate callbacks before they can act on a successor UI or disposed composition root. */
    fun current(ui: Long): Boolean = !disposed && ui == uiGeneration
    /** A locked/covered phone must not leave an outstanding picker acquiring indefinitely. */
    fun screenOff() { if (!disposed) { release(); event("screen_off_release") } }
    /** Final UI-owner disposal overrides all temporary retention exceptions. */
    fun dispose() { if (!disposed) { disposed = true; picker = false; release(); event("disposed") } }
}
