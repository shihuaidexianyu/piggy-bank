package com.shihuaidexianyu.money.util

object RecordValidator {
    fun requireAmount(amountText: String): Long {
        val amount = AmountInputParser.parseToMinor(amountText)
            ?: throw ValidationException("金额不能为空")
        if (amount <= 0) {
            throw ValidationException("金额必须大于 0")
        }
        return amount
    }

    fun requireNonNegativeAmount(amountText: String): Long {
        return AmountInputParser.parseToMinor(amountText)
            ?: throw ValidationException("金额不能为空")
    }

    fun requireOccurredAt(occurredAt: Long) {
        if (occurredAt > System.currentTimeMillis()) {
            throw ValidationException("时间不能晚于当前时间")
        }
    }

    fun requireAccountId(accountId: Long?): Long {
        return accountId ?: throw ValidationException("请选择账户")
    }

    fun requireTransferAccounts(fromId: Long?, toId: Long?): Pair<Long, Long> {
        val from = fromId ?: throw ValidationException("请选择账户")
        val to = toId ?: throw ValidationException("请选择账户")
        return from to to
    }

    fun requireReminderName(name: String) {
        if (name.isBlank()) {
            throw ValidationException("请输入名称")
        }
    }

    class ValidationException(override val message: String) : Exception(message)
}
