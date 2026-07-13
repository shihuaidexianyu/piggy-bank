package com.shihuaidexianyu.money.ui.stats

import java.math.BigInteger

data class NetWorthGoalProgressPresentation(
    val geometryPercent: Int,
    val percentageText: String,
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
    return NetWorthGoalProgressPresentation(
        geometryPercent = geometryPercent,
        percentageText = "$percentage%",
    )
}
