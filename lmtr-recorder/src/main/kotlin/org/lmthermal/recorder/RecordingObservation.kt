package org.lmthermal.recorder

import org.lmthermal.lmtr.*

/** Canonical owned JSON; callers cannot mutate maps after publication. Numeric semantics stay exact. */
class FrozenJson(value: Obj) {
    private val bytes = LmtrJson.encode(value)
    val byteSize: Int get() = bytes.size
    fun value(): Obj = LmtrJson.parse(bytes.copyOf())
    internal fun same(other: FrozenJson): Boolean = bytes.contentEquals(other.bytes)
}

/** Module-owned exact evidence. Descriptor excludes storage placement/hash; bytes are never borrowed.
 * The module freezes while it owns the source buffer. Copy access cannot mutate retained evidence.
 */
class RecordingPayload(descriptor: Obj, bytes: ByteArray, val acquisitionViewOffset: Long? = null) {
    val descriptor = FrozenJson(descriptor)
    private val owned = bytes.copyOf()
    val size: Int get() = owned.size
    val role: String get() = descriptor.value()["role"].text()
    fun bytes(): ByteArray = owned.copyOf()
    internal fun matchesSlice(parent: RecordingPayload, offset: Long): Boolean =
        offset >= 0 && offset <= parent.size.toLong() - size && owned.indices.all { owned[it] == parent.owned[offset.toInt()+it] }
}

/** A source module's immutable observation, separate from UI snapshots and rendered/latest-frame state.
 * Stream/session, original sequences/clocks, context and geometry are module evidence, not inferred.
 * Intake time is assigned only by the recorder. No thermometry computation occurs here.
 * `facts` carries receipt/acquisition/scheduled/device_sequence/measurement; storage fields are forbidden.
 */
class RecordingObservation(val streamId: String, val sequence: Long, stream: Obj, context: Obj,
    facts: Obj, payloads: List<RecordingPayload>) {
    val stream = FrozenJson(stream)
    val context = FrozenJson(context)
    val facts = FrozenJson(facts)
    val payloads: List<RecordingPayload> = java.util.Collections.unmodifiableList(payloads.toList())
    val ownedBytes: Long = this.payloads.sumOf { it.size.toLong() } + this.payloads.sumOf { it.descriptor.byteSize.toLong() } +
        this.stream.byteSize + this.context.byteSize + this.facts.byteSize
    init {
        require(sequence >= 0 && stream["stream_id"] == streamId)
        require(facts.keys.containsAll(listOf("receipt","acquisition","scheduled","device_sequence","measurement")))
        require(facts.keys.none { it in listOf("entry_id","timeline_ns","stream_id","segment_id","context_id","payloads","retention","observation_sequence","kind") })
        require(this.payloads.map { it.descriptor.value()["id"].text() }.distinct().size == this.payloads.size)
        require(this.payloads.filter { it.role!="module_extension" }.map { it.role }.distinct().size == this.payloads.count { it.role!="module_extension" })
    }
}

enum class RecordingProfile(val wire: String) { ANALYSIS("analysis"), NATIVE("native"), FULL("full"), VISUAL("visual") }

/** Resolve advertised capabilities explicitly; available requested evidence may never be silently omitted. */
object RecordingProfiles {
    fun requested(profile: RecordingProfile, capabilities: Obj): List<String> {
        fun supported(role: String) = capabilities[role] in listOf("supported","partial")
        require(profile != RecordingProfile.VISUAL || !supported("temperature")) { "Visual cannot discard available radiometry" }
        val roles = mutableSetOf<String>()
        if (supported("temperature")) roles.add("temperature")
        if (profile in listOf(RecordingProfile.NATIVE,RecordingProfile.FULL) && supported("native_samples")) roles.add("native_samples")
        if (profile == RecordingProfile.FULL && supported("acquisition")) roles.add("acquisition")
        if (profile in listOf(RecordingProfile.FULL,RecordingProfile.VISUAL)) {
            for (role in listOf("preview","visible")) if (supported(role)) roles.add(role)
        }
        require(roles.any { it in listOf("temperature","native_samples","acquisition","preview","visible") }) { "No recordable capability" }
        return roles.sorted()
    }

    /** Retention describes absence separately from profile omission; masks follow temperatures. */
    fun select(observation: RecordingObservation, profile: RecordingProfile): PreparedObservation {
        val stream = observation.stream.value();val capabilities=stream["capabilities"].obj()
        val requested=requested(profile,capabilities)
        val facts=observation.facts.value()
        val roles=requested.toMutableSet().apply { if("temperature" in this)add("validity");add("module_extension") }
        val payloads=observation.payloads.filter { it.role in roles }.sortedWith(compareBy<RecordingPayload> { LmtrChunk.roles.indexOf(it.role) }.thenBy { it.descriptor.value()["id"].text() })
        val availability=facts["availability"]?.obj() ?: emptyMap()
        val retention=linkedMapOf<String,Any?>()
        for(role in listOf("temperature","native_samples","acquisition","preview","visible","calibration_settings")) {
            val present=if(role=="calibration_settings") listOf("calibration","settings","environment").any {
                observation.context.value()[it].obj()["status"] in listOf("known","partial")
            } else payloads.any { it.role==role }
            retention[role]=when {
                present && (role in requested || role=="calibration_settings") -> mapOf("status" to "present")
                role !in requested && capabilities[role] in listOf("supported","partial") -> mapOf("status" to "omitted","reason" to "explicit_profile")
                availability.containsKey(role) -> availability[role].obj().also { require(it["status"] != "present" && it.containsKey("reason")) }
                capabilities[role]=="unsupported" -> mapOf("status" to "unsupported","reason" to "module_unsupported")
                else -> throw IllegalArgumentException("Missing explicit availability for $role")
            }
        }
        // An advertised/requested present plane must be retained; absence needs module evidence.
        val selectedStream=stream+("policy" to mapOf("level" to profile.wire,"choice" to "explicit","requested_roles" to requested))
        return PreparedObservation(observation.streamId,observation.sequence,FrozenJson(selectedStream),observation.context,
            FrozenJson(facts.filterKeys { it != "availability" }+("retention" to retention)),payloads)
    }
}

/** Internal worker-owned prepared observation; no UI or camera lifetime retained. */
class PreparedObservation internal constructor(val streamId: String,val sequence: Long,val stream: FrozenJson,
    val context: FrozenJson,val facts: FrozenJson,val payloads: List<RecordingPayload>)

/** Qualified module-reported loss. Counts require original ranges or an explicit signal;
 * unknown losses retain null count/ranges and their reason, never inferred camera FPS.
 */
class RecordingLoss(val streamId:String,stream:Obj,facts:Obj) {
    val stream=FrozenJson(stream)
    val facts=FrozenJson(facts)
    init {
        require(stream["stream_id"]==streamId)
        require(facts.keys.none { it in listOf("entry_id","timeline_ns","segment_id","kind","stream_id") })
        LmtrMetadata.local("Gap",facts+mapOf("kind" to "gap","entry_id" to "0","timeline_ns" to "0",
            "segment_id" to "0".repeat(64),"stream_id" to streamId))
        require(this.stream.byteSize+this.facts.byteSize<=16384)
    }
}
