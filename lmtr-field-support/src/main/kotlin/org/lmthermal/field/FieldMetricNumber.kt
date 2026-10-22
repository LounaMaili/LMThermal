package org.lmthermal.field

/** Diagnostic rates/ratios are not wire numbers. Convert their finite observed value explicitly
 * before passing a report to the strict metadata encoder; never teach that encoder to accept Double.
 */
fun fieldMetricNumber(value:Double):java.math.BigDecimal {
    require(value.isFinite());return java.math.BigDecimal.valueOf(value)
}
