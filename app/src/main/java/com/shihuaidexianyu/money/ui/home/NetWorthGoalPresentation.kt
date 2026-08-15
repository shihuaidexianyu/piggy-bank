package com.shihuaidexianyu.money.ui.home

import java.math.BigInteger

data class NetWorthGoalProgressPresentation(
    val geometryPercent: Int,
    val percentageText: String,
    /** targetAmount - currentAmount clamped at zero; zero means the goal is reached. */
    val remainingAmount: Long,
)

fun netWorthGoalProgressPresentation(
    currentAmount: Long,
    targetAmount: Long,
): NetWorthGoalProgressPresentation {
    require(targetAmount > 0L) { "净资产目标必须大于 0" }
    val percentage = BigInteger.valueOf(currentAmount)
        .multiply(BigInteger.valueOf(100L))
        .divide(BigInteger.valueOf(targetAmount))
    val geometryPercent = when {
        percentage.signum() <= 0 -> 0
        percentage >= BigInteger.valueOf(100L) -> 100
        else -> percentage.toInt()
    }
    // BigInteger like the percentage above: Long.MIN_VALUE net worth would overflow a plain
    // subtraction. Saturate at Long.MAX_VALUE — such a gap is not meaningfully displayable anyway.
    val remaining = BigInteger.valueOf(targetAmount).subtract(BigInteger.valueOf(currentAmount))
    val remainingAmount = when {
        remaining.signum() <= 0 -> 0L
        remaining > BigInteger.valueOf(Long.MAX_VALUE) -> Long.MAX_VALUE
        else -> remaining.toLong()
    }
    return NetWorthGoalProgressPresentation(
        geometryPercent = geometryPercent,
        percentageText = "$percentage%",
        remainingAmount = remainingAmount,
    )
}
