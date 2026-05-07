package com.shihuaidexianyu.money.util

object RecordValidator {
    fun requireAmount(amountText: String): Long {
        val amount = AmountInputParser.parseToMinor(amountText)
            ?: throw ValidationException(invalidAmountMessage(amountText))
        if (amount <= 0) {
            throw ValidationException("金额必须大于 0")
        }
        return amount
    }

    fun requireNonNegativeAmount(amountText: String): Long {
        return AmountInputParser.parseToMinor(amountText)
            ?: throw ValidationException(invalidAmountMessage(amountText))
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
        if (from == to) {
            throw ValidationException("请选择不同的转出和转入账户")
        }
        return from to to
    }

    fun requireReminderName(name: String) {
        if (name.isBlank()) {
            throw ValidationException("请输入名称")
        }
    }

    class ValidationException(override val message: String) : Exception(message)

    private fun invalidAmountMessage(amountText: String): String {
        return if (amountText.isBlank()) {
            "金额不能为空"
        } else {
            "请输入有效金额，最多保留两位小数"
        }
    }
}
