package org.lmthermal.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lmthermal.camera.*
import org.lmthermal.core.CelsiusPalette

/** Resource mapping is Android presentation policy; core enums and numerical evidence remain untranslated. */
object PaletteResources {
    fun label(palette: CelsiusPalette): Int = when (palette) {
        CelsiusPalette.WHITE_HOT -> R.string.palette_white_hot
        CelsiusPalette.BLACK_HOT -> R.string.palette_black_hot
        CelsiusPalette.INFERNO -> R.string.palette_inferno
        CelsiusPalette.IRON_LIKE -> R.string.palette_iron_like
        CelsiusPalette.TURBO -> R.string.palette_turbo
    }
    /** Compatibility names for existing machine reports; never used as UI/module identities. */
    fun evidenceName(palette: CelsiusPalette): String = when (palette) {
        CelsiusPalette.WHITE_HOT -> "White hot"
        CelsiusPalette.BLACK_HOT -> "Black hot"
        CelsiusPalette.INFERNO -> "Inferno"
        CelsiusPalette.IRON_LIKE -> "Iron-like"
        CelsiusPalette.TURBO -> "Turbo"
    }
}

@Composable
fun cameraStatusText(state: CameraSessionState<*>, ui: CameraUiBindings): String {
    val moduleLabel = ui.status(state.status)
    val resource = moduleLabel ?: when (state.status.code) {
            CameraStatusCode.NO_CAMERA -> R.string.camera_status_no_camera
            CameraStatusCode.DETECTED -> R.string.camera_status_detected
            CameraStatusCode.PERMISSION_REQUIRED -> R.string.camera_status_permission_required
            CameraStatusCode.OPENING -> R.string.camera_status_opening
            CameraStatusCode.PREVIEW_ONLY -> R.string.camera_status_preview_only
            CameraStatusCode.INITIALIZING -> R.string.camera_status_initializing
            CameraStatusCode.MEASUREMENT_READY -> R.string.camera_status_measurement_ready
            CameraStatusCode.MEASUREMENT_UNAVAILABLE -> R.string.camera_status_measurement_unavailable
            CameraStatusCode.CLOSED -> R.string.camera_status_closed
            CameraStatusCode.ERROR -> R.string.camera_status_error
        }
    return stringResource(resource)
}
fun cameraErrorResource(code: CameraErrorCode): Int = when (code) {
    CameraErrorCode.CAMERA_NOT_FOUND -> R.string.camera_error_not_found
    CameraErrorCode.CAMERA_UNSUPPORTED -> R.string.camera_error_unsupported
    CameraErrorCode.CAMERA_MATCH_AMBIGUOUS -> R.string.camera_error_ambiguous
    CameraErrorCode.PERMISSION_REQUIRED -> R.string.camera_error_permission_required
    CameraErrorCode.PERMISSION_DENIED -> R.string.camera_error_permission_denied
    CameraErrorCode.STREAM_OPEN_FAILED -> R.string.camera_error_open_failed
    CameraErrorCode.STREAM_FAILED -> R.string.camera_error_stream_failed
    CameraErrorCode.MODULE_DATA_INVALID -> R.string.camera_error_module_data_invalid
    CameraErrorCode.ACTION_UNAVAILABLE -> R.string.camera_error_action_unavailable
}
