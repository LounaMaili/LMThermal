package org.lmthermal.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A conflated latest snapshot, used by the Android controller: never queues presentation frames. */
class LatestFrameState<T>(initial: T) {
    private val latest = MutableStateFlow(initial)
    private val publicationLock = Any()
    /** Publishing replaces the current value; slow consumers observe the newest available snapshot. */
    var value: T
        get() = latest.value
        set(value) { synchronized(publicationLock) { latest.value = value } }
    /** Serialize the ownership check and publication with close/reset, preventing a stale final frame. */
    fun updateIf(ownsSource: () -> Boolean, transform: (T) -> T) {
        synchronized(publicationLock) {
            if (ownsSource()) latest.value = transform(latest.value)
        }
    }
    /** Consumers cannot mutate the producer's state or ask it to replay an obsolete queue. */
    fun asStateFlow(): StateFlow<T> = latest.asStateFlow()
}
