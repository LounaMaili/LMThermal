package org.lmthermal.field

import org.lmthermal.lmtr.LmtrJson
import org.lmthermal.lmtr.Obj
import java.io.File
import java.security.MessageDigest

/** Read a whole-file digest using a fixed-size buffer, including for large LMTR archives. */
fun fieldFileHash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65_536)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Write closing evidence from the sole close attempt that owns this run's directory. */
fun writeFieldClosingEvidence(root: File, report: Obj) {
    File(root, "private-report.json").writeBytes(LmtrJson.encode(report))
    val inventory = root.listFiles().orEmpty()
        .filter { it.isFile && it.name != "inventory.json" }
        .sortedBy { it.name }
        .map { mapOf("file" to it.name, "bytes" to it.length().toString(), "sha256" to fieldFileHash(it)) }
    File(root, "inventory.json").writeBytes(LmtrJson.encode(mapOf("files" to inventory)))
}
