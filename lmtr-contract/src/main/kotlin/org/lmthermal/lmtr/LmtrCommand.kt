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
                "seek" -> {
                    demand(args.size==3 || (args.size==5 && args[3]=="--stream"),"invalid_metadata")
                    val time=args[2].toLongOrNull() ?: throw LmtrFailure("invalid_metadata")
                    val selected=LmtrReader(File(args[1])).use { it.seek(time,args.getOrNull(4)) }
                    println(LmtrJson.encode(selected).toString(Charsets.UTF_8))
                }
                "scan" -> {
                    demand(args.size==2,"invalid_metadata")
                    println(LmtrJson.encode(LmtrReader(File(args[1])).use { it.salvage() }).toString(Charsets.UTF_8))
                }
                "check" -> {
                    // Unsupported future seek/filter options must not silently
                    // produce a narrower read than the operator requested.
                    var cursor=2
                    while(cursor<args.size) {
                        when(args[cursor]) {
                            "--full","--recover" -> cursor++
                            "--entry" -> { demand(cursor+1<args.size,"invalid_metadata");cursor+=2 }
                            else -> throw LmtrFailure("invalid_metadata")
                        }
                    }
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
