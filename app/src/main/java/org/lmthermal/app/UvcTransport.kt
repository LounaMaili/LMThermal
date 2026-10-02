package org.lmthermal.app

import android.hardware.usb.UsbDeviceConnection

/** Exact unconverted payload source, independent of parsing, session decisions and UI.
 * Calls are serialized on one worker dispatcher. Android owns the connection through native close.
 */
interface UvcTransport : AutoCloseable {
    fun read(): ByteArray?
    fun statistics(): LongArray
}

/** JNI boundary deliberately has no camera control write methods in this read-only milestone. */
internal object NativeUvc {
    init { System.loadLibrary("lmthermal") }
    external fun open(descriptor: Int): Long
    external fun read(handle: Long): ByteArray?
    external fun stats(handle: Long): LongArray
    external fun close(handle: Long)
}

/** Keep UsbManager's authorized descriptor alive until UVC transfers and callbacks have stopped. */
class NativeUvcTransport(private val connection: UsbDeviceConnection) : UvcTransport {
    private var handle: Long = try { NativeUvc.open(connection.fileDescriptor) }
        catch (error: Exception) { connection.close(); throw error }
    /** JNI waits at most 250 ms so cancellation/normal backgrounding can release the camera. */
    override fun read(): ByteArray? = NativeUvc.read(handle)
    /** Counters are native acquisition counts, not Compose-rendered frame counts. */
    override fun statistics(): LongArray = NativeUvc.stats(handle)
    /** Idempotent normal release; close native before Android closes the borrowed descriptor. */
    override fun close() {
        if (handle != 0L) { NativeUvc.close(handle); handle = 0L; connection.close() }
    }
}
