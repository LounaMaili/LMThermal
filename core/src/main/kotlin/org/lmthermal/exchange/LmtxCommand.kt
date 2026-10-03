package org.lmthermal.exchange

import java.io.File

/** Camera-free developer checker. Does not extract, open a module, reprocess temperatures or edit files. */
object LmtxCommand {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Provide one .lmtx path" }
        val manifest = LmtxChecker.check(File(args.single()))
        println(LmtxJson.encode(manifest).toString(Charsets.UTF_8))
    }
}
