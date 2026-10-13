package org.lmthermal.field

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
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
    private val route=FieldSourceRoute<Bitmap>(observer={ source ->
        metrics.observation(source)
        runCatching { clock?.source(source.identity.receiptClockId,source.receivedMonotonicNs) }
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
    val presenter=CelsiusPresenter(application,camera.state,camera::measurementEvidence,camera::cursorEvidence,camera::presentationGeneration,camera::publishPresentation)
    val lifetime=CameraUiLifetime(camera::leaveForeground,camera::recordLifecycle)
    init {
        camera.screenOff=lifetime::screenOff
        scope.launch { presenter.state.collect { it.measurement?.let { m -> metrics.preview(m.receivedMonotonicMs) } } }
        scope.launch { camera.state.collect { if(it.measurement==null)it.preview?.let { p -> metrics.preview(p.receivedMonotonicMs) } } }
    }
    /** UI actions launch only admission/recorder work. Unsupported/absent source cannot create a file. */
    fun start(profile:RecordingProfile?,seconds:Int) {
        if(busy.value)return
        busy.value=true
        scope.launch {
            try {
                val adapter=checkNotNull(route.adapter){"Connect/Open first; no authoritative source"}
                val preflight=FieldPreflight(profile?:RecordingProfile.FULL,seconds,
                    runCatching { StatFs(getApplication<Application>().filesDir.path).availableBytes }.getOrNull())
                check(preflight.permitted){"Known insufficient storage: ${preflight.requiredFreeBytes} bytes required"}
                val id=UUID.randomUUID().toString()
                val root=File(getApplication<Application>().filesDir,"r4-field/$id");check(root.mkdirs())
                directory=root;storageCap=preflight.storageCapBytes;oracleBytes=java.util.concurrent.atomic.AtomicLong();traceBytes=java.util.concurrent.atomic.AtomicLong();stopRequestNs.set(0);stopRequestWallNs.set(0);closeWallNs=null;metrics=FieldMetrics();runStart=SystemClock.elapsedRealtimeNanos();initialStatistics=camera.state.value.statistics;latestStatistics=initialStatistics;measured=seconds<=20
                // Both intake and timeline origin use the exact API used by HT receipt, including suspend.
                val selectedClock=FieldClock(SystemClock::elapsedRealtimeNanos);clock=selectedClock;header=selectedClock.header()
                File(root,"preflight.json").writeBytes(LmtrJson.encode(preflight.report()+mapOf("run_id" to id,
                    "baseline" to (profile==null),"private_wall_clock_label" to java.time.Instant.now().toString(),
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
                    val trace=File(root,"samples.jsonl").bufferedWriter()
                    try {
                        while(isActive && SystemClock.elapsedRealtimeNanos()-runStart<seconds*1000000000L) {
                            val snapshot=engine?.snapshot()
                            val state=camera.state.value
                            if(state.statistics.received>=latestStatistics.received)latestStatistics=state.statistics
                            if(!measured && SystemClock.elapsedRealtimeNanos()-runStart>=20000000000L) {
                                metrics=FieldMetrics();measured=true;initialStatistics=latestStatistics
                                File(root,"measured-window.json").writeBytes(LmtrJson.encode(mapOf("warmup_elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-runStart).toString(),"planned_measure_seconds" to (seconds-20))))
                            }
                            val sample=metrics.sample()+mapOf("elapsed_ns" to (SystemClock.elapsedRealtimeNanos()-runStart).toString(),
                                "session_state" to state.status.detail?.machineCode,"callback_fps" to state.statistics.callbackFps,
                                "received" to state.statistics.received.toString(),"replaced" to state.statistics.replaced.toString(),
                                "malformed" to state.statistics.malformed.toString(),"recorder" to snapshot?.let(::fieldSnapshot),
                                "thermal" to runCatching { File("/sys/class/thermal/thermal_zone0/temp").readText().trim() }.getOrNull())
                            val sampleBytes=LmtrJson.encode(sample);check(sampleBytes.size<=65536)
                            trace.write(sampleBytes.toString(Charsets.UTF_8));trace.newLine();trace.flush();traceBytes.addAndGet(sampleBytes.size+1L)
                            if(snapshot?.state in listOf("finalized","failed"))break
                            delay(1000)
                        }
                    } finally {trace.close()}
                    finishSafely()
                }
            } catch(error:Exception) {
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
    fun stop() { if(!busy.value)return;requestStop();sampler?.cancel();scope.launch { sampler?.join();finishSafely() } }
    /** Reserve final diagnostics until all worker/file ownership is resolved; no overlapping Start. */
    @Synchronized private fun claimFinish():Boolean { if(!busy.value || finishing)return false;finishing=true;return true }
    /** Bounds the actual nonblocking Stop request; it is not a fabricated source-failure cutoff. */
    private fun requestStop() {
        val start=SystemClock.elapsedRealtimeNanos();route.stop()
        if(stopRequestNs.compareAndSet(0,start))stopRequestWallNs.set(SystemClock.elapsedRealtimeNanos()-start)
    }
    /** Terminal result is not permission to reuse an oracle while a failed materializer unwinds. */
    private suspend fun waitWorkers(current:RecorderEngine):Boolean {
        val deadline=SystemClock.elapsedRealtimeNanos()+30000000000L
        while(!current.workersTerminated() && SystemClock.elapsedRealtimeNanos()<deadline)delay(10)
        return current.workersTerminated()
    }
    private suspend fun finishSafely() {
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
            "termination_reason" to result?.reason,"reopen" to reopened,"source_sha256" to before,"source_unchanged" to (before==after),
            "clock_correlation" to "source_receipt_and_recorder_intake_use_Android_elapsedRealtimeNanos_same_boot",
            "utc_sensor_resolution" to "unknown","warning" to NativeEquivalentThermometry.WARNING)
        File(root,"private-report.json").writeBytes(LmtrJson.encode(report))
        val inventory=root.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map {
            mapOf("file" to it.name,"bytes" to it.length().toString(),"sha256" to fileHash(it)) }
        File(root,"inventory.json").writeBytes(LmtrJson.encode(mapOf("files" to inventory)))
        engine=null;clock=null;finishing=false;busy.value=false;status.value="Stopped and read-only checked: ${root.name}; private files retained; inspect before longer run"
    }
    fun memoryPressure() { engine?.memoryPressure() }
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
        setContent {
            val state by model.status.collectAsState();val busy by model.busy.collectAsState()
            MaterialTheme { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("R4 test only — $state",Modifier.padding(6.dp))
                Row {
                    Button(enabled=!busy,onClick={model.start(null,146)}){Text("Baseline")}
                    Button(enabled=!busy,onClick={model.start(RecordingProfile.FULL,10)}){Text("Smoke 10s")}
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
    override fun onStart(){super.onStart();model.camera.enterForeground()}
    override fun onStop(){model.lifetime.stopped(token,isChangingConfigurations,isFinishing);super.onStop()}
    override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level==TRIM_MEMORY_RUNNING_CRITICAL || level>=TRIM_MEMORY_COMPLETE)model.memoryPressure()}
}
