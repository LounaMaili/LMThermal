package org.lmthermal.app

import android.hardware.usb.UsbDeviceConnection
import org.lmthermal.core.RadiometricCommand
import org.lmthermal.core.RadiometricControl

/** Exact unconverted payload source, independent of parsing, session decisions and UI.
 * Calls are serialized on one worker dispatcher. Android owns the connection through native close.
 */
interface UvcTransport : AutoCloseable {
    fun read(): ByteArray?
    fun statistics(): LongArray
}

/** JNI exposes only current zoom readback (-1) and the three semantic operation ordinals. */
internal object NativeUvc {
    init { System.loadLibrary("lmthermal") }
    external fun open(descriptor: Int): Long
    external fun read(handle: Long): ByteArray?
    external fun stats(handle: Long): LongArray
    external fun close(handle: Long)
    external fun zoom(handle: Long, operation: Int): Int
}

/** Keep UsbManager's authorized descriptor alive until UVC transfers and callbacks have stopped. */
class NativeUvcTransport(private val connection: UsbDeviceConnection) : UvcTransport, RadiometricControl {
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
    /** Idempotent normal release; close native before Android closes the borrowed descriptor. */
    override fun close() {
        if (handle != 0L) { NativeUvc.close(handle); handle = 0L; connection.close() }
    }
}
