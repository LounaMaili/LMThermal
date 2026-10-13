package org.lmthermal.lmtr

/** Bounded current-segment journal validation; no camera timestamp is inferred. */
class JournalState(private val clockBudget: Int = 4096) {
    var nextEntry = 0L; private set
    var lastTimeline = -1L; private set
    var activeSegment: String? = null; private set
    private val nextObservation = mutableMapOf<String, Long>()
    private val deviceSequence = mutableMapOf<String, Long>()
    private val clockEvidence = mutableMapOf<String, ByteArray>()

    /** Validate one committed chunk in original append order, not playback display order. */
    fun accept(chunk: Obj) {
        val segments = chunk["segments"].arr().map { it.obj() }.associateBy { it["id"].text() }
        for (segment in segments.values) for (stream in segment["streams"].arr().map { it.obj() }) {
            for (clock in stream["clocks"].arr().map { it.obj() }) {
                val id = clock["id"].text(); val bytes = LmtrJson.encode(clock)
                val prior = clockEvidence[id]
                if (prior != null) demand(prior.contentEquals(bytes), "invalid_metadata")
                else { demand(clockEvidence.size < clockBudget, "local_resource_limit"); clockEvidence[id] = bytes }
            }
        }
        for (entry in chunk["entries"].arr().map { it.obj() }) {
            demand(entry["entry_id"].d() == nextEntry, "invalid_metadata")
            val timeline = entry["timeline_ns"].d(); demand(timeline >= lastTimeline, "invalid_metadata")
            nextEntry = LmtrChunk.add(nextEntry, 1); lastTimeline = timeline
            val segmentId = entry["segment_id"].text(); val segment = segments.getValue(segmentId)
            when (entry["kind"]) {
                "segment_start" -> {
                    demand(activeSegment == null && segment["start_entry"].d() == nextEntry-1 && segment["start_timeline_ns"].d() == timeline, "invalid_metadata")
                    activeSegment = segmentId; nextObservation.clear(); deviceSequence.clear()
                }
                "segment_end" -> { demand(activeSegment == segmentId, "invalid_metadata"); activeSegment = null }
                "observation" -> {
                    demand(activeSegment == segmentId, "invalid_metadata")
                    val stream = entry["stream_id"].text(); val sequence = entry["observation_sequence"].d()
                    nextObservation[stream]?.let { demand(sequence == it, "invalid_metadata") }
                    nextObservation[stream] = LmtrChunk.add(sequence, 1)
                    entry["device_sequence"]?.let { device ->
                        val actual = device.d(); deviceSequence[stream]?.let { demand(actual > it, "invalid_metadata") }
                        deviceSequence[stream] = actual
                    }
                }
                "gap" -> {
                    demand(activeSegment == segmentId, "invalid_metadata")
                    entry["observation_first"]?.let { first ->
                        val stream = entry["stream_id"].text()
                        nextObservation[stream]?.let { demand(first.d() == it, "invalid_metadata") }
                        nextObservation[stream] = LmtrChunk.add(entry["observation_last"].d(), 1)
                    }
                }
            }
        }
    }
}

/** Acknowledged counts retain at most 64 stream accumulators and role counters. */
class SummaryAccumulator(private val header: Obj) {
    private val aggregate = mutableMapOf<String, Long>()
    private val streams = linkedMapOf<String, MutableMap<String, Long>>()
    private val streamBytes = linkedMapOf<String, MutableMap<String, Long>>()
    private val bytes = LmtrChunk.roles.associateWith { 0L }.toMutableMap()
    private var firstEntry: Long? = null; private var lastEntry: Long? = null
    private var firstTime: Long? = null; private var lastTime: Long? = null
    private val counters = listOf("observations","temperature_planes","measurable_planes","gap_events","known_missing_observations","unknown_gap_events")

    init { for (stream in header["streams"].arr().map { it.obj() }) {
        val id = stream["id"].text(); streams[id] = counters.associateWith { 0L }.toMutableMap()
        streamBytes[id] = LmtrChunk.roles.associateWith { 0L }.toMutableMap()
    } }

    private fun increment(map: MutableMap<String, Long>, key: String, value: Long = 1) { map[key] = LmtrChunk.add(map[key] ?: 0, value) }

    /** Count only complete acknowledged chunks, not preparation/queued work. */
    fun accept(chunk: Obj) {
        increment(aggregate,"chunks")
        val entries = chunk["entries"].arr().map { it.obj() }; increment(aggregate,"entries",entries.size.toLong())
        if (firstEntry == null) { firstEntry=entries.first()["entry_id"].d();firstTime=entries.first()["timeline_ns"].d() }
        lastEntry=entries.last()["entry_id"].d();lastTime=entries.last()["timeline_ns"].d()
        for (block in chunk["blocks"].arr().map { it.obj() }) increment(aggregate,"physical_payload_bytes",block["decoded"].d())
        for (entry in entries) {
            if (entry["kind"] !in listOf("observation","gap")) continue
            val streamId=entry["stream_id"].text(); val s=streams.getValue(streamId)
            if (entry["kind"] == "gap") {
                for (target in listOf(aggregate,s)) {
                    increment(target,"gap_events")
                    if (entry["count"] == null) increment(target,"unknown_gap_events") else increment(target,"known_missing_observations",entry["count"].d())
                }
            } else {
                val measurement=entry["measurement"].obj()
                for (target in listOf(aggregate,s)) {
                    increment(target,"observations")
                    if (measurement["status"] == "available") {
                        increment(target,"temperature_planes")
                        if (measurement["validity"] != "no_valid_pixels") increment(target,"measurable_planes")
                    }
                }
                for (payload in entry["payloads"].arr().map { it.obj() }) {
                    val role=payload["role"].text();val length=payload["length"].d()
                    increment(bytes,role,length);increment(streamBytes.getValue(streamId),role,length)
                }
            }
        }
    }

    /** Deterministic core summary object for checkpoint/END validation and publication. */
    fun json(): Obj {
        val result=linkedMapOf<String,Any?>()
        for (key in listOf("chunks","entries")+counters+"physical_payload_bytes") result[key]=(aggregate[key]?:0).toString()
        result["logical_bytes"]=bytes.mapValues { it.value.toString() }
        result["streams"]=streams.map { (id,values) -> linkedMapOf<String,Any?>("stream_id" to id).apply {
            for (key in counters) put(key,values.getValue(key).toString())
            put("logical_bytes",streamBytes.getValue(id).mapValues { it.value.toString() })
        } }
        result["first_entry"]=firstEntry?.toString();result["last_entry"]=lastEntry?.toString()
        result["first_timeline_ns"]=firstTime?.toString();result["last_timeline_ns"]=lastTime?.toString()
        return result
    }
}
