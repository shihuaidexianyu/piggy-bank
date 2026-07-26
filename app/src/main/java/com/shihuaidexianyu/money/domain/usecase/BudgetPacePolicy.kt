package com.shihuaidexianyu.money.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Budget burn-down: turns "spent X of Y" into "at this pace you will overspend by Z".
 *
 * The projection is a straight-line extrapolation of the average daily spend so far. It is a
 * pace signal, not a forecast — it deliberately ignores the shape of the month (rent on the 1st,
 * payday spikes), because a simple rule the user can verify by eye is more trustworthy on a
 * dashboard than a model they cannot.
 */
data class BudgetPace(
    val daysElapsed: Int,
    val daysTotal: Int,
    val daysRemaining: Int,
    /** Average spend per elapsed day. */
    val dailyAverageSpent: Long,
    /** Straight-line projection of the full period's spend at the current pace. */
    val projectedTotalSpend: Long,
    /** Projected spend beyond the target, or null when the projection stays within budget. */
    val projectedOverspend: Long?,
    /** Budget still unspent; negative once the target is exceeded. */
    val remainingBudget: Long,
    /** What the user may spend per remaining day to land exactly on target, or null if none is left. */
    val safeDailySpend: Long?,
)

fun calculateBudgetPace(
    targetAmount: Long,
    spentAmount: Long,
    daysElapsed: Int,
    daysTotal: Int,
): BudgetPace? {
    if (targetAmount <= 0L) return null
    if (daysTotal <= 0) return null
    val elapsed = daysElapsed.coerceIn(1, daysTotal)
    val remainingDays = daysTotal - elapsed
    val spent = spentAmount.coerceAtLeast(0L)

    val dailyAverage = BigDecimal.valueOf(spent)
        .divide(BigDecimal.valueOf(elapsed.toLong()), 0, RoundingMode.HALF_UP)
    val projectedTotal = dailyAverage.multiply(BigDecimal.valueOf(daysTotal.toLong()))
    // A projection can never be below what is already spent — per-day rounding otherwise lets
    // the card claim "within budget at this pace" on a budget that is already exceeded.
    val projectedTotalLong = projectedTotal.toClampedLong().coerceAtLeast(spent)
    val overspend = (projectedTotalLong - targetAmount).takeIf { it > 0L }
    val remainingBudget = targetAmount - spent
    val safeDailySpend = if (remainingDays > 0 && remainingBudget > 0L) {
        BigDecimal.valueOf(remainingBudget)
            .divide(BigDecimal.valueOf(remainingDays.toLong()), 0, RoundingMode.DOWN)
            .toClampedLong()
    } else {
        null
    }

    return BudgetPace(
        daysElapsed = elapsed,
        daysTotal = daysTotal,
        daysRemaining = remainingDays,
        dailyAverageSpent = dailyAverage.toClampedLong(),
        projectedTotalSpend = projectedTotalLong,
        projectedOverspend = overspend,
        remainingBudget = remainingBudget,
        safeDailySpend = safeDailySpend,
    )
}

/**
 * Clamps instead of throwing: these are derived display values, and a saturated number on the
 * dashboard is a better outcome than an arithmetic crash on the home screen.
 */
private fun BigDecimal.toClampedLong(): Long = this
    .coerceIn(BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE))
    .setScale(0, RoundingMode.HALF_UP)
    .toLong()
