package org.lmthermal.app.r2

import org.lmthermal.r2.BlockCodec

/** Test APK only, reference 1.5.7 BSD-3-Clause, independent frames/window<=8MiB/no dictionaries. */
object NativeZstd : BlockCodec {
    init { System.loadLibrary("r2_zstd") }
    override val id = 2
    override val name = "zstd-3"
    external override fun encode(input: ByteArray): ByteArray
    external override fun decode(input: ByteArray, size: Int): ByteArray
}
