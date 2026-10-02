package org.lmthermal.core

/** Standard read-only Zoom Absolute requests; neither selector nor arbitrary request is exposed. */
enum class ZoomQuery(val code: Int, val length: Int) {
    INFO(0x86, 1), LENGTH(0x85, 2), MINIMUM(0x82, 2), MAXIMUM(0x83, 2),
    RESOLUTION(0x84, 2), DEFAULT(0x87, 2), CURRENT(0x81, 2)
}

/** Parsed descriptors from the already-open libuvc device, without serials or scene data. */
data class ZoomDescriptor(
    val uvcVersion: Int, val terminalId: Int, val controlInterface: Int,
    val controls: Long, val terminalType: Int,
    val focalMinimum: Int, val focalMaximum: Int, val ocularFocalLength: Int,
) {
    companion object {
        const val SELECTOR = 0x0b
        const val ZOOM_BITMAP_BIT = 9 // UVC camera-terminal bitmap is not selector-minus-one.
        const val GET_REQUEST_TYPE = 0xa1
    }
    val zoomAdvertised: Boolean get() = controls and (1L shl ZOOM_BITMAP_BIT) != 0L
    val index: Int get() = (terminalId shl 8) or controlInterface
    /** Numeric descriptor evidence without private device identifiers. */
    fun fields(): Map<String, Any> = mapOf("uvc_version" to uvcVersion,
        "terminal_id" to terminalId, "control_interface" to controlInterface,
        "bmControls" to controls, "zoom_advertised" to zoomAdvertised,
        "terminal_type" to terminalType, "focal_minimum" to focalMinimum,
        "focal_maximum" to focalMaximum, "ocular_focal_length" to ocularFocalLength)
}

/** A failed/short response is preserved as evidence, never decoded as a successful zero. */
data class ZoomTransfer(val actualLength: Int, val bytes: List<Int>) {
    /** Exact-length unsigned little-endian values only; errors never become a successful zero. */
    fun decoded(query: ZoomQuery): Int? {
        if (actualLength != query.length || bytes.size != query.length || bytes.any { it !in 0..255 }) return null
        return bytes[0] or (if (query.length == 2) bytes[1] shl 8 else 0)
    }
}

/** Deliberately no SET method: inventory cannot request modifying commands through this interface. */
interface ReadOnlyZoomControl {
    fun descriptor(): ZoomDescriptor
    fun query(query: ZoomQuery): ZoomTransfer
}

/** Runs on the source worker; inventory errors are observations, not grounds to issue writes. */
class ZoomInventory(private val control: ReadOnlyZoomControl, private val ownsConnection: () -> Boolean,
                    private val clockMs: () -> Long, private val event: (Map<String, Any?>) -> Unit) {
    fun run() {
        check(ownsConnection()) { "Connection cancelled" }
        val descriptor = control.descriptor()
        event(mapOf("event" to "zoom_descriptor", "monotonic_ms" to clockMs()) + descriptor.fields())
        // Observe CURRENT before and after each capability/range query: query order is evidence.
        val orderedQueries = listOf(ZoomQuery.CURRENT) + ZoomQuery.entries.flatMap {
            if (it == ZoomQuery.CURRENT) listOf(it) else listOf(it, ZoomQuery.CURRENT)
        }
        for ((ordinal, query) in orderedQueries.withIndex()) {
            check(ownsConnection()) { "Connection cancelled" }
            val response = control.query(query)
            check(ownsConnection()) { "Connection cancelled" }
            event(mapOf("event" to "zoom_query", "monotonic_ms" to clockMs(), "query" to query.name, "ordinal" to ordinal,
                "bmRequestType" to ZoomDescriptor.GET_REQUEST_TYPE, "bRequest" to query.code,
                "selector" to ZoomDescriptor.SELECTOR, "terminal_id" to descriptor.terminalId,
                "interface" to descriptor.controlInterface, "wValue" to (ZoomDescriptor.SELECTOR shl 8),
                "wIndex" to descriptor.index, "requested_length" to query.length,
                "actual_length" to response.actualLength, "bytes" to response.bytes,
                "decoded" to response.decoded(query), "exact_length" to (response.decoded(query) != null)))
        }
        event(mapOf("event" to "zoom_inventory_complete", "monotonic_ms" to clockMs()))
    }
}
