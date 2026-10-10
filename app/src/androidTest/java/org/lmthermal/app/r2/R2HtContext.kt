package org.lmthermal.app.r2

import org.lmthermal.core.RadiometricMeasurement

/** Retained test adapter; the prototype producer owns the exact shared mapping. */
object R2HtContext {
    fun metadata(measurement: RadiometricMeasurement) = org.lmthermal.r2.R2HtContext.metadata(measurement)
}
