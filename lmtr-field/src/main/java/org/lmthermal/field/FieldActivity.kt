package org.lmthermal.field

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.lmthermal.app.*
import org.lmthermal.camera.*
import org.lmthermal.recording.*
import org.lmthermal.recorder.*
import org.lmthermal.lmtr.*
import org.lmthermal.core.NativeEquivalentThermometry
import java.io.File
import java.io.BufferedWriter
import java.util.UUID
import java.security.MessageDigest

/** Streaming private inventory; never allocate a whole recording to hash it. */
internal fun fileHash(file:File):String {
    val digest=MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> val buffer=ByteArray(65536);while(true){val size=input.read(buffer);if(size<0)break;digest.update(buffer,0,size)} }
    return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
}

/** Sole test composition root. Existing owner/acquisition/UI are reused; no automatic Connect/init/Start.
 * ViewModel keeps rotation; CameraUiLifetime enforces ordinary background/screen-off cutoff.
 * Only app-private files, never SAF/share or production recording services.
 */
class FieldModel(application:Application):AndroidViewModel(application) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var metrics=FieldMetrics()
    @Volatile private var clock:FieldClock?=null
    @Volatile private var engine:RecorderEngine?=null
    private var sampler:Job?=null
    private var oracle:BufferedWriter?=null
    private var directory:File?=null
    private var header:Obj?=null
    private var finishing=false
    private var diagnosticFailure:String?=null
    @Volatile private var safetyFailure:String?=null
    @Volatile private var foreground=false
    @Volatile private var lastValidNs:Long?=null
    private val stopRequestNs=java.util.concurrent.atomic.AtomicLong()
    private val stopRequestWallNs=java.util.concurrent.atomic.AtomicLong()
    private var closeWallNs:Long?=null
    private var storageCap=0L
    private var oracleBytes=java.util.concurrent.atomic.AtomicLong()
    private var traceBytes=java.util.concurrent.atomic.AtomicLong()
    private var runStart=0L
    private var initialStatistics=CameraStatistics()
    private var latestStatistics=CameraStatistics()
    @Volatile private var measured=false
    private val writeLock=Any()
    val status=MutableStateFlow("No test started; Connect and initialization remain explicit")
    val busy=MutableStateFlow(false)
    val running=MutableStateFlow(false)
    private var healthSampler:Job?=null
    private var presentationSampler:Job?=null
    private val suppressPresentation=java.util.concurrent.atomic.AtomicBoolean()
    @Volatile private var presentationCounters=PresentationCounters()
    /** One bounded normal-preview memory window. No recorder/admission attempt, GC, hidden
     * preview, source controls or UI-ownership changes. Counts describe only sampled roles.
     */
    fun sampleLiveMemory() {
        if(busy.value || healthSampler?.isActive==true || presentationSampler?.isActive==true)return
        if(camera.state.value.measurement==null){status.value="Live memory refused: current ready measurement required";return}
        busy.value=true;running.value=true
        presentationSampler=scope.launch {
            val root=File(getApplication<Application>().filesDir,"r4-live-memory/${UUID.randomUUID()}")
            val settings=presenter.settings.value;val selected=presenter.roi.selection.value.rect
            var completed=0;var failure:String?=null
            try {
                check(root.mkdirs())
                check(!suppressPresentation.get() && engine==null){"live_diagnostic_requires_normal_preview_no_recorder"}
                presenter.setAutomatic(true)
                camera.state.value.geometry?.let {presenter.roi.select(org.lmthermal.core.NativeRect(0,0,it.width,it.height))}
                delay(3000)
                metrics=FieldMetrics();presentationCounters=PresentationCounters()
                val start=SystemClock.elapsedRealtimeNanos()
                File(root,"samples.jsonl").bufferedWriter().use {out ->
                    repeat(20) {index ->
                        val health=fieldHealth(getApplication(),foreground)
                        check(health.foregroundUnlocked && health.cameraPermission && !health.systemLowMemory &&
                            (health.batteryPercent ?: -1)>=30 && (health.thermalStatus ?: 99)<3 &&
                            (health.freeBytes ?: -1)>=268435456 && health.javaHeadroomBytes>=33554432){"live_diagnostic_safety_stop"}
                        val current=camera.state.value
                        check(current.measurement!=null && !suppressPresentation.get()){"live_source_or_preview_unavailable"}
                        val visible=VisibleMeasurementOwners.report(mapOf("camera" to current.measurement,
                            "rendered" to presenter.state.value.measurement,"roi" to presenter.roi.state.value.measurement))
                        val plan=FieldPreflight(RecordingProfile.FULL,20,null)
                        val row=mapOf("sample" to index,"elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-start).toString(),
                            "health" to health.report(),"memory" to metrics.sample(),"metrics" to metrics.report(),
                            "presentation" to presentationCounters.report(),"visible_owners" to visible,
                            "session_state" to current.status.detail?.machineCode,
                            "policy_observation_refusal" to FieldSafety.refusal(health,plan,true),
                            "policy_scope" to "observation_not_Start_or_safe_admission_proof",
                            "recorder_created" to false,"render_suppressed" to false,"forced_gc" to false)
                        out.write(LmtrJson.encode(row).toString(Charsets.UTF_8));out.newLine();out.flush()
                        completed++;delay(1000)
                    }
                }
                File(root,"metrics.json").writeBytes(LmtrJson.encode(mapOf("elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-start).toString(),
                    "metrics" to metrics.report(),"presentation" to presentationCounters.report())))
            } catch(cancelled:CancellationException){failure="cancelled";throw cancelled}
            catch(error:Exception){failure=error.javaClass.simpleName+":"+error.message}
            finally {
                presenter.setPalette(settings.palette)
                if(settings.automatic)presenter.setAutomatic(true)else presenter.setLocked(settings.locked)
                presenter.roi.clear();selected?.let {presenter.roi.select(it)}
                if(root.isDirectory)File(root,"result.json").writeBytes(LmtrJson.encode(mapOf("completed_samples" to completed,
                    "planned_samples" to 20,"complete" to (completed==20 && failure==null),"failure" to failure,
                    "recorder_created" to false,"render_suppressed" to false,"forced_gc" to false)))
                status.value="Live memory ${if(failure==null)"completed" else "stopped: $failure"}: ${root.name}"
                busy.value=false;running.value=false
            }
        }
    }
    /** Read-only diagnostic using the existing health witness, even before a USB source exists.
     * A phase label is not authority: each sample also retains actual lifecycle/readiness facts.
     * No recorder, source connection, controls, GC or device-setting changes occur here.
     */
    fun sampleHealth(label:String) {
        if(busy.value || healthSampler?.isActive==true)return
        require(label in setOf("fresh_process","foreground_idle","connected","ready","before_admission"))
        healthSampler=scope.launch {
            val root=File(getApplication<Application>().filesDir,"r4-memory/${UUID.randomUUID()}")
            check(root.mkdirs())
            var completed=0;var failure:String?=null
            try { File(root,"health.jsonl").bufferedWriter().use { output ->
                repeat(20) { index ->
                    val state=camera.state.value
                    val plan=FieldPreflight(RecordingProfile.FULL,20,null)
                    val health=fieldHealth(getApplication(),foreground)
                    val row=health.report()+mapOf("sample" to index,"phase_label" to label,
                        "session_state" to state.status.detail?.machineCode,"lifecycle" to state.lifecycle.name,
                        "measurement_available" to (state.measurement!=null),"recorder_created" to false,
                        "admission_refusal" to FieldSafety.refusal(health,plan,true),
                        "memory" to FieldMetrics().sample(),"heap_semantics" to "occupied_includes_uncollected_garbage_not_reachable_live_heap")
                    output.write(LmtrJson.encode(row).toString(Charsets.UTF_8));output.newLine();output.flush()
                    completed++
                    delay(100)
                }
            } } catch(cancelled:CancellationException) {failure="cancelled";throw cancelled}
            catch(error:Exception) {failure=error.javaClass.simpleName}
            finally {
                // A partial diagnostic window is evidence, not an implicitly successful 20-sample run.
                File(root,"sampling.json").writeBytes(LmtrJson.encode(mapOf("planned_samples" to 20,
                    "completed_samples" to completed,"complete" to (completed==20 && failure==null),
                    "failure_class" to failure,"phase_label" to label,"recorder_created" to false)))
                status.value="Read-only health ${if(completed==20 && failure==null)"sampled" else "incomplete"}: ${root.name}; no source/recorder action"
            }
        }
    }
    private val route=FieldSourceRoute<Bitmap>(observer={ source ->
        metrics.observation(source)
        if(source.measurement!=null)lastValidNs=source.receivedMonotonicNs
        // A new baseline-only source after cutoff must not upgrade the old interrupted run.
        runCatching { if(engine?.snapshot()?.state=="recording")clock?.source(source.identity.receiptClockId,source.receivedMonotonicNs) }
            .onFailure { engine?.failSource("field_source_clock_changed_or_regressed") }
    },lossObserver={metrics.loss(it)},adapterFactory={ identity ->
        val native=HtFieldRoute.native(identity)
        if(native==null)null else object:CameraRecordingAdapter by native {
            override fun offer(source:CameraAcquiredObservation):RecordingOffer {
                // Bind the run now, not when an older failed preparation worker eventually executes.
                val boundClock=checkNotNull(clock);val boundOracle=checkNotNull(oracle);val boundDirectory=checkNotNull(directory)
                val boundOracleBytes=oracleBytes;val boundTraceBytes=traceBytes;val boundCap=storageCap
                return FieldOracleAdapter(native,HtFieldOracle::capture,{ row ->
                    synchronized(writeLock) {
                        check(oracle===boundOracle && directory===boundDirectory){"field_oracle_run_replaced"}
                        val bytes=LmtrJson.encode(row);check(bytes.size<=65536)
                        check(File(boundDirectory,"capture.lmtr").length()+boundOracleBytes.get()+boundTraceBytes.get()+bytes.size+1048576<=boundCap){"field_total_storage_cap"}
                        boundOracle.write(bytes.toString(Charsets.UTF_8));boundOracle.newLine();boundOracle.flush();boundOracleBytes.addAndGet(bytes.size+1L)
                    }
                },boundClock::now).offer(source)
            }
        }
    })
    val camera=AndroidCameraCoordinator(application,recordingLifetime=route)
    val presenter=CelsiusPresenter(application,camera.state,camera::measurementEvidence,camera::cursorEvidence,camera::presentationGeneration,camera::publishPresentation,
        {event,m,elapsed->presentationCounters.event(event,m,elapsed)}, { !suppressPresentation.get() })
    val lifetime=CameraUiLifetime(camera::leaveForeground,camera::recordLifecycle)
    init {
        camera.screenOff=lifetime::screenOff
        scope.launch { presenter.state.collect { it.measurement?.let { m -> metrics.preview(m.receivedMonotonicMs) } } }
        scope.launch { camera.state.collect { if(it.measurement==null)it.preview?.let { p -> metrics.preview(p.receivedMonotonicMs) } } }
    }
    /** Finite no-recorder Compose/USB probe. Connect/init remain separate explicit actions.
     * Uses the original source owner and guards; no GC, source controls or timing changes.
     * Configuration changes are display/ROI-only and are restored even on cancellation.
     */
    fun profilePresentation() {
        if(busy.value || healthSampler?.isActive==true || presentationSampler?.isActive==true)return
        if(camera.state.value.measurement==null){status.value="Presentation probe refused: current ready measurement required";return}
        busy.value=true;running.value=true
        presentationSampler=scope.launch {
            val root=File(getApplication<Application>().filesDir,"r4-presentation/${UUID.randomUUID()}")
            val settings=presenter.settings.value;val previousRoi=presenter.roi.selection.value.rect
            var failure:String?=null
            try {
                check(root.mkdirs())
                for(phase in listOf("locked_no_roi","auto_no_roi","auto_full_roi","suppressed")) {
                    val pre=fieldHealth(getApplication(),foreground)
                    check(pre.foregroundUnlocked && pre.cameraPermission && (pre.batteryPercent ?: -1)>=30 &&
                        (pre.thermalStatus ?: 99)<3 && (pre.freeBytes ?: -1)>=268435456 && !pre.systemLowMemory){"diagnostic_prerequisite"}
                    presenter.roi.clear();presenter.setAutomatic(phase!="locked_no_roi")
                    if(phase=="auto_full_roi")camera.state.value.geometry?.let { presenter.roi.select(org.lmthermal.core.NativeRect(0,0,it.width,it.height)) }
                    suppressPresentation.set(phase=="suppressed")
                    delay(3000) // Exclude transition/warmup; never qualifies session readiness.
                    metrics=FieldMetrics();presentationCounters=PresentationCounters()
                    val start=SystemClock.elapsedRealtimeNanos();val before=camera.state.value.statistics
                    File(root,"$phase.jsonl").bufferedWriter().use { out ->
                        repeat(10) {
                            val state=camera.state.value;check(state.measurement!=null && foreground){"source_or_foreground_lost"}
                            val health=fieldHealth(getApplication(),foreground)
                            check(health.foregroundUnlocked && (health.batteryPercent ?: -1)>=30 &&
                                (health.thermalStatus ?: 99)<3 && !health.systemLowMemory && health.javaHeadroomBytes>=33554432){"diagnostic_safety_stop"}
                            val row=mapOf("phase" to phase,"elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-start).toString(),
                                "health" to health.report(),"memory" to metrics.sample(),"presentation" to presentationCounters.report(),
                                "session_state" to state.status.detail?.machineCode,"recorder_created" to false)
                            out.write(LmtrJson.encode(row).toString(Charsets.UTF_8));out.newLine();out.flush();delay(1000)
                        }
                    }
                    File(root,"$phase-summary.json").writeBytes(LmtrJson.encode(mapOf("phase" to phase,
                        "elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-start).toString(),"metrics" to metrics.report(),
                        "presentation" to presentationCounters.report(),"received_delta" to (camera.state.value.statistics.received-before.received).toString(),
                        "recorder_created" to false,"render_suppressed" to suppressPresentation.get())))
                }
            } catch(error:Exception){failure=error.javaClass.simpleName+":"+error.message}
            finally {
                suppressPresentation.set(false);presenter.setPalette(settings.palette)
                if(settings.automatic)presenter.setAutomatic(true)else presenter.setLocked(settings.locked)
                presenter.roi.clear();previousRoi?.let { presenter.roi.select(it) }
                if(root.isDirectory)File(root,"result.json").writeBytes(LmtrJson.encode(mapOf("failure" to failure,"complete" to (failure==null),"recorder_created" to false,"forced_gc" to false)))
                status.value="Presentation probe ${if(failure==null)"completed" else "stopped: $failure"}: ${root.name}"
                busy.value=false;running.value=false
            }
        }
    }
    /** UI actions launch only admission/recorder work. Unsupported/absent source cannot create a file. */
    fun start(profile:RecordingProfile?,seconds:Int) {
        // Diagnostic sampling declares no recorder and must not overlap a later admission window.
        if(busy.value || healthSampler?.isActive==true || presentationSampler?.isActive==true)return
        busy.value=true
        running.value=true
        scope.launch {
            try {
                val adapter=checkNotNull(route.adapter){"Connect/Open first; no authoritative source"}
                val preflight=FieldPreflight(profile?:RecordingProfile.FULL,seconds,
                    runCatching { StatFs(getApplication<Application>().filesDir.path).availableBytes }.getOrNull())
                check(preflight.permitted){"Known insufficient storage: ${preflight.requiredFreeBytes} bytes required"}
                val health=fieldHealth(getApplication(),foreground)
                val safetyRefusal=FieldSafety.refusal(health,preflight,true)
                // Persist actual admission-time quantities even if no recording can safely be created.
                // A later dumpsys/GC snapshot is not a substitute for the refusing Runtime witness.
                val admissionDirectory=File(getApplication<Application>().filesDir,"r4-admission")
                check(admissionDirectory.isDirectory || admissionDirectory.mkdirs())
                File(admissionDirectory,"${UUID.randomUUID()}.json").writeBytes(LmtrJson.encode(mapOf(
                    "health" to health.report(),"plan" to preflight.report(),"safety_refusal" to safetyRefusal,
                    "recording_created" to false,"scope" to "admission_witness_not_run_completion")))
                check(safetyRefusal==null){"Safety preflight refused: $safetyRefusal"}
                check(camera.state.value.status.code==CameraStatusCode.MEASUREMENT_READY && camera.state.value.measurement!=null){"Explicit initialization and current valid measurement required before field Start"}
                val safety=FieldSafety(preflight,checkNotNull(health.rssBytes),SystemClock.elapsedRealtimeNanos(),camera.state.value.statistics.received)
                val id=UUID.randomUUID().toString()
                val root=File(getApplication<Application>().filesDir,"r4-field/$id");check(root.mkdirs())
                directory=root;diagnosticFailure=null;safetyFailure=null;storageCap=preflight.storageCapBytes;oracleBytes=java.util.concurrent.atomic.AtomicLong();traceBytes=java.util.concurrent.atomic.AtomicLong();stopRequestNs.set(0);stopRequestWallNs.set(0);closeWallNs=null;metrics=FieldMetrics();runStart=SystemClock.elapsedRealtimeNanos();initialStatistics=camera.state.value.statistics;latestStatistics=initialStatistics;measured=seconds<=20
                // Both intake and timeline origin use the exact API used by HT receipt, including suspend.
                val selectedClock=FieldClock(SystemClock::elapsedRealtimeNanos);clock=selectedClock;header=selectedClock.header()
                File(root,"preflight.json").writeBytes(LmtrJson.encode(preflight.report()+mapOf("run_id" to id,
                    "baseline" to (profile==null),"private_wall_clock_label" to java.time.Instant.now().toString(),
                    "health" to health.report(),"safety_policy" to "sampled_1hz_battery30_thermal3_storage256MiB_heap32MiB_rss128MiB_stale3s_half_cadence5samples",
                    "wall_clock_quality" to "unqualified_not_lmtr_utc_anchor")))
                if(profile!=null) {
                    synchronized(writeLock){oracle=File(root,"oracle.jsonl").bufferedWriter()}
                    val local=LocalRecorderBackend(File(root,"capture.lmtr"))
                    val backend=object:RecorderBackend {
                        override val destination=local.destination
                        override fun append(type:Int,ordinal:Long,previous:Long,body:ByteArray):Record {
                            check(destination.length()+body.size+112+oracleBytes.get()+traceBytes.get()+1048576<=preflight.storageCapBytes){"field_storage_cap"}
                            val result=local.append(type,ordinal,previous,body)
                            if(type==LmtrFraming.CHECKPOINT)metrics.checkpoint()
                            return result
                        }
                        override fun close() { val start=SystemClock.elapsedRealtimeNanos();try {local.close()} finally {closeWallNs=SystemClock.elapsedRealtimeNanos()-start} }
                    }
                    val current=RecorderEngine(header!!,listOf(adapter.stream.value()),profile,preflight.limits,backend,preflight.codec,
                        now=selectedClock::now,sealMetric={a,b,c,d -> metrics.sealed(a,b,c,d)},codecMetric={a,b,c -> metrics.codec(a,b,c)})
                    engine=current
                    check(route.start(current,adapter.identity)){"Source replaced or recorder still draining; explicit Start refused"}
                }
                status.value="${profile?.wire ?: "Baseline"}: $id; bounded $seconds s, Stop is always available"
                sampler=scope.launch {
                    var trace:BufferedWriter?=null
                    try {
                        val localTrace=File(root,"samples.jsonl").bufferedWriter();trace=localTrace
                        while(isActive && SystemClock.elapsedRealtimeNanos()-runStart<seconds*1000000000L) {
                            val snapshot=engine?.snapshot()
                            val state=camera.state.value
                            val currentHealth=fieldHealth(getApplication(),foreground)
                            val withinBounds=snapshot==null || (snapshot.preparationQueuedBytes<=preflight.limits.preparationBytes &&
                                snapshot.writerQueuedBytes<=preflight.limits.writerBytes && snapshot.controlBytes<=preflight.limits.controlBytes &&
                                snapshot.preparationInFlightBytes<=preflight.limits.maxObservationBytes && snapshot.writerInFlightBytes<=preflight.limits.maxObservationBytes &&
                                snapshot.pendingBytes<=preflight.limits.preferredChunkBytes+preflight.limits.maxObservationBytes)
                            val safetyReason=safety.check(currentHealth,SystemClock.elapsedRealtimeNanos(),state.statistics.received,lastValidNs,withinBounds)
                            if(safetyReason!=null)stopForSafety(safetyReason)
                            if(state.statistics.received>=latestStatistics.received)latestStatistics=state.statistics
                            if(!measured && SystemClock.elapsedRealtimeNanos()-runStart>=20000000000L) {
                                metrics=FieldMetrics();measured=true;initialStatistics=latestStatistics
                                File(root,"measured-window.json").writeBytes(LmtrJson.encode(mapOf("warmup_elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-runStart).toString(),"planned_measure_seconds" to (seconds-20))))
                            }
                            val sample=metrics.sample()+mapOf("elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-runStart).toString(),
                                "session_state" to state.status.detail?.machineCode,"callback_fps" to state.statistics.callbackFps,
                                "received" to state.statistics.received.toString(),"replaced" to state.statistics.replaced.toString(),
                                "malformed" to state.statistics.malformed.toString(),"recorder" to snapshot?.let(::fieldSnapshot),
                                "health" to currentHealth.report(),"safety_stop_reason" to safetyFailure,
                                "thermal" to runCatching { File("/sys/class/thermal/thermal_zone0/temp").readText().trim() }.getOrNull())
                            val sampleBytes=LmtrJson.encode(sample);check(sampleBytes.size<=65536)
                            localTrace.write(sampleBytes.toString(Charsets.UTF_8));localTrace.newLine();localTrace.flush();traceBytes.addAndGet(sampleBytes.size+1L)
                            if(safetyFailure!=null)break
                            if(snapshot?.state in listOf("finalized","failed"))break
                            delay(1000)
                        }
                    } catch(cancelled:CancellationException) { throw cancelled }
                    catch(error:Exception) {
                        diagnosticFailure=error.javaClass.simpleName
                        engine?.stop("field_metrics_failed",true);requestStop()
                    } finally {
                        runCatching {trace?.close()}.onFailure {
                            diagnosticFailure=it.javaClass.simpleName;engine?.stop("field_metrics_failed",true);requestStop()
                        }
                    }
                    finishSafely()
                }
            } catch(error:Exception) {
                running.value=false
                status.value="Test refused/failed: ${error.message}; originals retained"
                engine?.failSource("field_setup_failed")
                val stopped=engine?.awaitStop(10000)
                val released=engine?.let { waitWorkers(it) } ?: true
                if(engine==null || (stopped!=null && released)) {
                    synchronized(writeLock){oracle?.close();oracle=null};engine=null;clock=null;busy.value=false
                } else {status.value="Setup/Stop timeout; originals and worker ownership retained; no next run"}
            }
        }
    }
    /** Stop cuts source intake synchronously; drain/reopen/hash/inventory remain off UI. */
    fun stop() { if(presentationSampler?.isActive==true){presentationSampler?.cancel();return};if(!busy.value)return;requestStop();sampler?.cancel();scope.launch { sampler?.join();finishSafely() } }
    /** Unhealthy runs cannot enable a later run; retain honest interrupted termination and originals. */
    private fun stopForSafety(reason:String) {
        if(safetyFailure==null)safetyFailure=reason
        engine?.stop("field_safety_$reason",true);requestStop()
    }
    /** Foreground activity only: lifecycle loss stops intake, even during a recorder-free baseline. */
    fun foreground(value:Boolean) {
        foreground=value
        if(!value && busy.value){stopForSafety("foreground_lost");stop()}
    }
    /** Reserve final diagnostics until all worker/file ownership is resolved; no overlapping Start. */
    @Synchronized private fun claimFinish():Boolean { if(!busy.value || finishing)return false;finishing=true;return true }
    /** Bounds the actual nonblocking Stop request; it is not a fabricated source-failure cutoff. */
    private fun requestStop() {
        running.value=false
        val start=SystemClock.elapsedRealtimeNanos();metrics.closeSourceWindow();route.stop()
        if(stopRequestNs.compareAndSet(0,start))stopRequestWallNs.set(SystemClock.elapsedRealtimeNanos()-start)
    }
    /** Terminal result is not permission to reuse an oracle while a failed materializer unwinds. */
    private suspend fun waitWorkers(current:RecorderEngine):Boolean {
        val deadline=SystemClock.elapsedRealtimeNanos()+30000000000L
        while(!current.workersTerminated() && SystemClock.elapsedRealtimeNanos()<deadline)delay(10)
        return current.workersTerminated()
    }
    private suspend fun finishSafely() {
        running.value=false
        try { finish() } catch(error:Exception) {
            engine?.failSource("field_diagnostic_failure")
            status.value="Stop/readback diagnostic failed: ${error.javaClass.simpleName}; all files retained; inspect locally"
            // Refuse another run when the prior worker/diagnostic ownership is uncertain.
            finishing=false;busy.value=true
        }
    }
    private suspend fun finish() {
        if(!claimFinish())return
        val root=directory ?: return
        val stopStart=SystemClock.elapsedRealtimeNanos()
        requestStop()
        val current=engine
        val result=current?.awaitStop(30000)
        if(current!=null && (result==null || !waitWorkers(current))) {
            // Do not close an oracle still used by admitted work or certify a hung footer.
            finishing=false;busy.value=true;status.value="Stop timed out; keep app open, no success claimed; originals retained"
            return
        }
        synchronized(writeLock){oracle?.flush();oracle?.close();oracle=null}
        val capture=File(root,"capture.lmtr")
        val before=if(capture.exists())fileHash(capture) else null
        val reopenStart=SystemClock.elapsedRealtimeNanos()
        val reopened=if(capture.exists())runCatching { LmtrReader(capture).use { it.check(full=true,recover=result?.finalized!=true).filterKeys { key -> key!="logical_hashes" } } }
            .fold({it},{mapOf("classification" to "readback_failed","reason" to it.javaClass.simpleName)}) else null
        val reopenNs=SystemClock.elapsedRealtimeNanos()-reopenStart
        val after=if(capture.exists())fileHash(capture) else null
        check(before==after){"Source changed during read-only check"}
        val stats=latestStatistics
        val report=metrics.report()+mapOf("status" to "executed_not_automatically_accepted","physical" to true,
            "duration_ns" to (SystemClock.elapsedRealtimeNanos()-runStart).toString(),"warmup_seconds" to if(File(root,"measured-window.json").exists())20 else 0,"camera_received_delta" to (stats.received-initialStatistics.received).toString(),
            "callback_replaced_delta" to (stats.replaced-initialStatistics.replaced).toString(),"callback_malformed_delta" to (stats.malformed-initialStatistics.malformed).toString(),
            "stop_cutoff_drain_close_reopen_ns" to (SystemClock.elapsedRealtimeNanos()-stopStart).toString(),"full_reopen_ns" to reopenNs.toString(),
            "observed_stop_request_elapsed_ns" to (stopRequestNs.get()-runStart).toString(),"stop_request_call_wall_ns" to stopRequestWallNs.get().toString(),
            "backend_close_wall_ns" to closeWallNs?.toString(),"stop_cutoff_authority" to "engine_serialized_witness_request_window_not_assumed_first_on_source_failure",
            "recorder_snapshot" to current?.snapshot()?.let(::fieldSnapshot),"finalized" to result?.finalized,"termination_status" to result?.status,
            "termination_reason" to result?.reason,"reopen" to reopened,"source_sha256" to before,"source_unchanged" to if(before==null)null else (before==after),"diagnostic_failure" to diagnosticFailure,
            "safety_stop_reason" to safetyFailure,
            "clock_correlation" to "source_receipt_and_recorder_intake_use_Android_elapsedRealtimeNanos_same_boot",
            "utc_sensor_resolution" to "unknown","warning" to NativeEquivalentThermometry.WARNING)
        File(root,"private-report.json").writeBytes(LmtrJson.encode(report))
        val inventory=root.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map {
            mapOf("file" to it.name,"bytes" to it.length().toString(),"sha256" to fileHash(it)) }
        File(root,"inventory.json").writeBytes(LmtrJson.encode(mapOf("files" to inventory)))
        engine=null;clock=null;finishing=false
        val refused=safetyFailure!=null || diagnosticFailure!=null || fieldReopenFailed(reopened,capture.exists())
        busy.value=refused
        status.value=if(refused)"Diagnostic/read-only check failed: ${root.name}; originals retained; no longer run before inspection"
            else "Stopped and read-only checked: ${root.name}; private files retained; inspect before longer run"
    }
    fun memoryPressure() { if(busy.value){stopForSafety("android_memory_pressure");stop()} }
    override fun onCleared() { route.stop();lifetime.dispose();presenter.dispose();camera.dispose();scope.launch { sampler?.cancelAndJoin();finishSafely();scope.cancel() } }
}

/** Deliberate field buttons plus the unchanged product camera/measurement screen, in a separate APK. */
class FieldActivity:AppCompatActivity() {
    private val model:FieldModel by viewModels()
    private var token=-1L
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(model.lifetime.current(token))model.camera.cameraPermissionResult(it) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        token=model.lifetime.started(resources.configuration.locales.toLanguageTags())
        if(intent.action=="org.lmthermal.field.SAMPLE_HEALTH")model.sampleHealth(intent.getStringExtra("phase") ?: "foreground_idle")
        if(intent.action=="org.lmthermal.field.PROFILE_PRESENTATION")model.profilePresentation()
        if(intent.action=="org.lmthermal.field.SAMPLE_LIVE_MEMORY")model.sampleLiveMemory()
        setContent {
            val state by model.status.collectAsState();val busy by model.busy.collectAsState()
            val running by model.running.collectAsState()
            // Only this bounded foreground experiment may keep the screen awake; never a setting.
            DisposableEffect(running) {
                if(running)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose {window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
            }
            MaterialTheme { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("R4 test only — $state",Modifier.padding(6.dp))
                Row {
                    Button(enabled=!busy,onClick={model.start(null,146)}){Text("Baseline")}
                    Button(enabled=!busy,onClick={model.start(RecordingProfile.FULL,20)}){Text("Smoke 20s")}
                    Button(enabled=busy,onClick=model::stop){Text("Stop test")}
                }
                Row {
                    for(p in listOf(RecordingProfile.ANALYSIS,RecordingProfile.NATIVE,RecordingProfile.FULL))
                        Button(enabled=!busy,onClick={model.start(p,146)}){Text(p.wire)}
                }
                Box(Modifier.weight(1f)) { ThermalScreen(model.camera,model.presenter,{
                    if(model.lifetime.current(token) && model.camera.connect())permission.launch(Manifest.permission.CAMERA)
                }) }
            } }
        }
    }
    override fun onNewIntent(intent:android.content.Intent) {
        super.onNewIntent(intent)
        if(intent.action=="org.lmthermal.field.SAMPLE_HEALTH")model.sampleHealth(intent.getStringExtra("phase") ?: "foreground_idle")
        if(intent.action=="org.lmthermal.field.PROFILE_PRESENTATION")model.profilePresentation()
        if(intent.action=="org.lmthermal.field.SAMPLE_LIVE_MEMORY")model.sampleLiveMemory()
    }
    override fun onStart(){super.onStart();model.foreground(true);model.camera.enterForeground()}
    override fun onStop(){window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(!isChangingConfigurations)model.foreground(false);model.lifetime.stopped(token,isChangingConfigurations,isFinishing);super.onStop()}
    override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level==TRIM_MEMORY_RUNNING_CRITICAL || level>=TRIM_MEMORY_COMPLETE)model.memoryPressure()}
}
