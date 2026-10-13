package org.lmthermal.lmtr

import java.io.File
import kotlin.system.exitProcess

/** Camera-free contract diagnostic; structured scopes never claim production calibration. */
object LmtrCommand {
    @JvmStatic fun main(args: Array<String>) {
        try {
            demand(args.size>=2,"invalid_metadata")
            when(args[0]) {
                "canon" -> { System.out.write(LmtrJson.encode(LmtrJson.parse(File(args[1]).readBytes())));return }
                "write-plan" -> { demand(args.size==3,"invalid_metadata");println(LmtrJson.encode(LmtrWriter.writePlan(LmtrJson.parse(File(args[1]).readBytes()),File(args[2]))).toString(Charsets.UTF_8)) }
                "check" -> {
                    val full="--full" in args;val recover="--recover" in args
                    val index=args.indexOf("--entry");val entry=if(index>=0)args.getOrNull(index+1)?.toLongOrNull()?:throw LmtrFailure("invalid_metadata")else null
                    val result=LmtrReader(File(args[1])).use { it.check(full,entry,recover) }
                    println(LmtrJson.encode(result).toString(Charsets.UTF_8))
                }
                else -> throw LmtrFailure("invalid_metadata")
            }
        } catch(e:LmtrFailure) {
            println(LmtrJson.encode(mapOf("status" to "error","error" to e.code,"integrity_scope" to "none")).toString(Charsets.UTF_8));exitProcess(2)
        } catch(e:java.io.IOException) {
            println(LmtrJson.encode(mapOf("status" to "error","error" to "storage_failure","integrity_scope" to "none")).toString(Charsets.UTF_8));exitProcess(2)
        }
    }
}
