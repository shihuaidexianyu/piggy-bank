package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A signed change measured against a baseline, for "up ¥3,200 (2.6%) since the start of the month"
 * style comparisons.
 *
 * [percentageText] is null when the baseline is not positive: a percentage change against zero is
 * undefined, and against a negative net worth it reads backwards (a baseline of -100 growing to
 * -50 is an improvement, but -50% suggests a loss). The amount is always meaningful, so callers
 * show the amount and simply omit the percentage.
 */
data class PeriodDelta(
    val currentAmount: Long,
    val baselineAmount: Long,
    val deltaAmount: Long,
    val percentageText: String?,
) {
    val isIncrease: Boolean get() = deltaAmount > 0L
    val isDecrease: Boolean get() = deltaAmount < 0L
    val isUnchanged: Boolean get() = deltaAmount == 0L
}

fun calculatePeriodDelta(
    currentAmount: Long,
    baselineAmount: Long,
): PeriodDelta {
    val delta = ledgerSubtractExact(currentAmount, baselineAmount)
    return PeriodDelta(
        currentAmount = currentAmount,
        baselineAmount = baselineAmount,
        deltaAmount = delta,
        percentageText = percentageText(delta, baselineAmount),
    )
}

private fun percentageText(deltaAmount: Long, baselineAmount: Long): String? {
    if (baselineAmount <= 0L) return null
    if (deltaAmount == 0L) return "0%"
    val percentage = BigDecimal.valueOf(deltaAmount)
        .abs()
        .multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(baselineAmount), 1, RoundingMode.HALF_UP)
    // A change small enough to round to zero is still a change; saying "0%" would misreport it.
    if (percentage.signum() == 0) return "<0.1%"
    return "${percentage.stripTrailingZeros().toPlainString()}%"
}
