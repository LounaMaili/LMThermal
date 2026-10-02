package org.lmthermal.app

import android.hardware.usb.UsbDeviceConnection
import org.lmthermal.core.RadiometricCommand
import org.lmthermal.core.RadiometricControl
import org.lmthermal.core.ReadOnlyZoomControl
import org.lmthermal.core.ZoomDescriptor
import org.lmthermal.core.ZoomQuery
import org.lmthermal.core.ZoomTransfer

/** Exact unconverted payload source, independent of parsing, session decisions and UI.
 * Calls are serialized on one worker dispatcher. Android owns the connection through native close.
 */
interface UvcTransport : AutoCloseable {
    fun read(): ByteArray?
    fun statistics(): LongArray
}

/** Restricted Zoom semantic operations and fixed GET inventory; no selector/value API. */
internal object NativeUvc {
    init { System.loadLibrary("lmthermal") }
    external fun open(descriptor: Int): Long
    external fun read(handle: Long): ByteArray?
    external fun stats(handle: Long): LongArray
    external fun close(handle: Long)
    external fun zoom(handle: Long, operation: Int): Int
    external fun zoomDescriptor(handle: Long): LongArray
    external fun zoomQuery(handle: Long, query: Int): IntArray
}

/** Keep UsbManager's authorized descriptor alive until UVC transfers and callbacks have stopped. */
class NativeUvcTransport(private val connection: UsbDeviceConnection) : UvcTransport, RadiometricControl, ReadOnlyZoomControl {
    private var handle: Long = try { NativeUvc.open(connection.fileDescriptor) }
        catch (error: Exception) { connection.close(); throw error }
    /** JNI waits at most 250 ms so cancellation/normal backgrounding can release the camera. */
    override fun read(): ByteArray? = NativeUvc.read(handle)
    /** Counters are native acquisition counts, not Compose-rendered frame counts. */
    override fun statistics(): LongArray = NativeUvc.stats(handle)
    /** Unsigned two-byte UVC GET_CUR, sharing this stream's authorized handle. */
    override fun readZoom(): Int = NativeUvc.zoom(handle, -1)
    /** No arbitrary selector/value API. Called only by the worker-owned explicit session. */
    override fun execute(command: RadiometricCommand) { NativeUvc.zoom(handle, command.ordinal) }
    /** Descriptor values remain bound to this exact open device, never guessed by the UI. */
    override fun descriptor(): ZoomDescriptor {
        val d = NativeUvc.zoomDescriptor(handle)
        check(d.size == 8)
        return ZoomDescriptor(d[0].toInt(), d[1].toInt(), d[2].toInt(), d[3],
            d[4].toInt(), d[5].toInt(), d[6].toInt(), d[7].toInt())
    }
    /** Native inventory boundary accepts query ordinals only, always on the fixed zoom selector. */
    override fun query(query: ZoomQuery): ZoomTransfer {
        val response = NativeUvc.zoomQuery(handle, query.ordinal)
        return ZoomTransfer(response[0], response.drop(1))
    }
    /** Idempotent normal release; close native before Android closes the borrowed descriptor. */
    override fun close() {
        if (handle != 0L) { NativeUvc.close(handle); handle = 0L; connection.close() }
    }
}
