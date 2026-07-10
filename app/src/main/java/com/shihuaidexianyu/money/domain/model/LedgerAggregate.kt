package com.shihuaidexianyu.money.domain.model

data class AccountLedgerAggregate(
    val accountId: Long,
    val inflow: Long = 0L,
    val outflow: Long = 0L,
    val transferIn: Long = 0L,
    val transferOut: Long = 0L,
    val manualAdjustment: Long = 0L,
    val reconciliation: Long = 0L,
)

data class AccountActivityMaxima(
    val maxActiveOccurredAt: Long?,
    val maxBalanceUpdateOccurredAt: Long?,
)

class LedgerOverflowException(cause: Throwable? = null) :
    ArithmeticException("账本金额超出 Long 范围") {
    init {
        if (cause != null) initCause(cause)
    }
}

fun ledgerAddExact(left: Long, right: Long): Long = ledgerExact {
    Math.addExact(left, right)
}

fun ledgerSubtractExact(left: Long, right: Long): Long = ledgerExact {
    Math.subtractExact(left, right)
}

fun Iterable<Long>.ledgerSumExact(): Long {
    var total = 0L
    for (value in this) total = ledgerAddExact(total, value)
    return total
}

private inline fun ledgerExact(block: () -> Long): Long {
    return try {
        block()
    } catch (error: ArithmeticException) {
        if (error is LedgerOverflowException) throw error
        throw LedgerOverflowException(error)
    }
}
