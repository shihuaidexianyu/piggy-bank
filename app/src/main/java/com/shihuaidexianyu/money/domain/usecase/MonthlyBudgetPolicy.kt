package com.shihuaidexianyu.money.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

data class MonthlyBudgetStatus(
    val targetAmount: Long,
    val spentAmount: Long,
    val progressFraction: Float,
    val percentageText: String,
    val overBudgetAmount: Long?,
    val overBudgetPercentageText: String?,
)

fun calculateMonthlyBudgetStatus(
    targetAmount: Long?,
    spentAmount: Long,
): MonthlyBudgetStatus? {
    require(spentAmount >= 0L) { "月支出不能为负数" }
    if (targetAmount == null) return null
    require(targetAmount > 0L) { "月预算必须大于 0" }

    val spent = BigDecimal.valueOf(spentAmount)
    val target = BigDecimal.valueOf(targetAmount)
    val ratio = spent.divide(target, 12, RoundingMode.HALF_UP)
    val percentage = spent
        .multiply(BigDecimal.valueOf(100L))
        .divide(target, 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    val overBudgetAmount = if (spentAmount > targetAmount) spentAmount - targetAmount else null
    val overBudgetPercentageText = overBudgetAmount?.let { over ->
        val percentageOver = BigDecimal.valueOf(over)
            .multiply(BigDecimal.valueOf(100L))
            .divide(target, 2, RoundingMode.HALF_UP)
        if (percentageOver.signum() == 0) {
            "<0.01%"
        } else {
            "${percentageOver.stripTrailingZeros().toPlainString()}%"
        }
    }
    return MonthlyBudgetStatus(
        targetAmount = targetAmount,
        spentAmount = spentAmount,
        progressFraction = ratio.coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toFloat(),
        percentageText = "$percentage%",
        overBudgetAmount = overBudgetAmount,
        overBudgetPercentageText = overBudgetPercentageText,
    )
}
